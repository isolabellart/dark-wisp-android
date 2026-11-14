package com.darkwisp.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.Filter
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.Nip01EventOrder
import com.darkwisp.app.nostr.MarketplaceRelays
import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.relay.RelayConfig
import com.darkwisp.app.relay.RelayPool
import com.darkwisp.app.repo.EventRepository
import com.darkwisp.app.repo.PeerRelayListLookup
import com.darkwisp.app.repo.RelayListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

enum class MarketplaceScope { ALL, FOLLOWING, MINE }

class MarketplaceViewModel(app: Application) : AndroidViewModel(app) {
    private val _listings = MutableStateFlow<List<NostrEvent>>(emptyList())
    val listings: StateFlow<List<NostrEvent>> = _listings

    private val _scope = MutableStateFlow(MarketplaceScope.ALL)
    val scope: StateFlow<MarketplaceScope> = _scope

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories

    private val subscriptionSequence = AtomicLong()
    private val cachedByAddress = linkedMapOf<String, NostrEvent>()
    private val cacheLock = Any()
    private var loadJob: Job? = null
    private var activeSubscriptionId: String? = null
    @Volatile private var activeSellerRelayUrls: List<String> = emptyList()
    private var currentAuthors: Set<String> = emptySet()
    private var currentUserPubkey: String? = null
    private var currentSellerPubkey: String? = null
    private var currentEventRepository: EventRepository? = null

    companion object {
        private const val LOAD_TIMEOUT_MS = 15_000L
        private const val EOSE_DRAIN_GRACE_MS = 6_000L
        private const val MINIMUM_EOSE_COUNT = 6
        private const val MAX_CACHED_LISTINGS = 1000

    }

    fun setScope(scope: MarketplaceScope) {
        _scope.value = scope
    }

    private fun matchesScope(event: NostrEvent, selectedScope: MarketplaceScope, authors: Set<String>, userPubkey: String?, sellerPubkey: String?): Boolean {
        if (sellerPubkey != null) return event.pubkey == sellerPubkey
        return when (selectedScope) {
            MarketplaceScope.ALL -> true
            MarketplaceScope.FOLLOWING -> event.pubkey in authors
            MarketplaceScope.MINE -> event.pubkey == userPubkey
        }
    }

    private fun publishVisibleCache(selectedScope: MarketplaceScope, authors: Set<String>, userPubkey: String?, sellerPubkey: String? = currentSellerPubkey) {
        val snapshot = synchronized(cacheLock) { cachedByAddress.values.toList() }
        val scoped = snapshot.filter { matchesScope(it, selectedScope, authors, userPubkey, sellerPubkey) }
        _availableCategories.value = scoped.flatMap { Nip99.categories(it) }.map { it.trim().lowercase() }
            .filter { it.isNotBlank() }.distinct().sorted()
        val query = _searchQuery.value.trim().lowercase()
        val category = _selectedCategory.value?.lowercase()
        _listings.value = scoped.asSequence()
            .filter { event -> category == null || Nip99.categories(event).any { it.trim().equals(category, ignoreCase = true) } }
            .filter { event ->
                if (query.isBlank()) true else {
                    val maker = currentEventRepository?.getProfileData(event.pubkey)?.displayString.orEmpty()
                    listOf(Nip99.title(event), Nip99.summary(event), Nip99.description(event), Nip99.location(event),
                        event.pubkey, maker, Nip99.categories(event).joinToString(" "))
                        .any { it.contains(query, ignoreCase = true) }
                }
            }
            .sortedByDescending { it.created_at }.take(MAX_CACHED_LISTINGS).toList()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        publishVisibleCache(_scope.value, currentAuthors, currentUserPubkey, currentSellerPubkey)
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
        publishVisibleCache(_scope.value, currentAuthors, currentUserPubkey, currentSellerPubkey)
    }

    fun load(
        selectedScope: MarketplaceScope,
        relayPool: RelayPool,
        eventRepository: EventRepository,
        followedPubkeys: List<String>,
        userPubkey: String?,
        relayListRepository: RelayListRepository,
        sellerPubkey: String? = null
    ) {
        _scope.value = selectedScope
        currentUserPubkey = userPubkey
        currentSellerPubkey = sellerPubkey
        currentEventRepository = eventRepository
        val authors = when {
            sellerPubkey != null -> setOf(sellerPubkey)
            selectedScope == MarketplaceScope.ALL -> emptySet()
            selectedScope == MarketplaceScope.FOLLOWING -> followedPubkeys.toSet()
            else -> setOfNotNull(userPubkey)
        }
        currentAuthors = authors

        // Detach the previous request before cancelling it. Its finally block
        // must not reset the next request's loading indicator or error state.
        val oldSubId = activeSubscriptionId
        val oldSellerRelayUrls = activeSellerRelayUrls
        activeSubscriptionId = null
        activeSellerRelayUrls = emptyList()
        loadJob?.cancel()
        oldSubId?.let { closeSubscription(relayPool, it, oldSellerRelayUrls) }

        if (selectedScope != MarketplaceScope.ALL && authors.isEmpty()) {
            _listings.value = emptyList()
            _loading.value = false
            _error.value = null
            return
        }

        // Keep known listings visible immediately while the relay query refreshes.
        publishVisibleCache(selectedScope, authors, userPubkey, sellerPubkey)
        _loading.value = true
        _error.value = null

        val subId = "marketplace-${subscriptionSequence.incrementAndGet()}"
        activeSubscriptionId = subId

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            var sellerRelayTargets = emptyList<String>()
            try {
                if (sellerPubkey != null) {
                    // Paint the seller storefront from ObjectBox before network discovery;
                    // a previously seen catalogue must not appear empty while relays connect.
                    val storedListings = eventRepository.getCachedEventsByAuthorAndKinds(
                        sellerPubkey,
                        Nip99.MARKETPLACE_KINDS.toSet(),
                        limit = 2_000
                    ).filter {
                        Nip99.dTag(it).isNotBlank() &&
                            Nip99.status(it) != "hidden" &&
                            eventRepository.isEventVisibleToUser(it)
                    }
                    synchronized(cacheLock) {
                        storedListings.forEach { event ->
                            val key = Nip99.addressTag(event)
                            val current = cachedByAddress[key]
                            if (current == null || Nip01EventOrder.isNewer(event, current)) {
                                cachedByAddress[key] = event
                            }
                        }
                    }
                    if (activeSubscriptionId == subId) {
                        publishVisibleCache(selectedScope, authors, userPubkey, sellerPubkey)
                    }

                    // Product listings are published to the seller's NIP-65 write/outbox relays.
                    // Use cached relays first; discover kind 10002 if no usable outbox is known.
                    var knownSellerRelays = relayListRepository.getWriteRelays(sellerPubkey).orEmpty()
                    if (knownSellerRelays.isEmpty()) {
                        PeerRelayListLookup.fetch(
                            sellerPubkey,
                            relayPool,
                            relayListRepository,
                            requireMarketplaceRelayEose = true
                        )
                        knownSellerRelays = relayListRepository.getWriteRelays(sellerPubkey).orEmpty()
                    }
                    sellerRelayTargets = knownSellerRelays.distinct()
                    if (activeSubscriptionId == subId) activeSellerRelayUrls = sellerRelayTargets
                }

                val filter = Filter(
                    kinds = Nip99.MARKETPLACE_KINDS,
                    authors = authors.takeIf { it.isNotEmpty() }?.toList(),
                    limit = 1000
                )
                val request = ClientMessage.req(subId, filter)

                // Search the account's configured relays, public indexers, and
                // the public relay sets used by NIP-99 marketplaces. This avoids
                // relying on a single indexer that may not carry external listings.
                // Subscribe to the result stream before sending REQ. Some relays reply
                // immediately; collecting afterwards loses their EVENT/EOSE frames.
                val remoteListingCount = AtomicLong(0L)
                val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    relayPool.relayEvents
                        .filter { it.subscriptionId == subId }
                        .collect { relayEvent ->
                            val event = relayEvent.event
                            if (!Nip99.isMarketplaceKind(event.kind)) return@collect
                            if (sellerPubkey != null && event.pubkey != sellerPubkey) return@collect
                            if (sellerPubkey == null && selectedScope == MarketplaceScope.FOLLOWING && event.pubkey !in authors) return@collect
                            if (sellerPubkey == null && selectedScope == MarketplaceScope.MINE && event.pubkey != userPubkey) return@collect
                            if (!eventRepository.isEventVisibleToUser(event)) return@collect

                            val dTag = Nip99.dTag(event)
                            // NIP-99 recommends a title tag, but it is not a hard
                            // validity requirement. Do not hide otherwise valid listings.
                            if (dTag.isBlank() || Nip99.status(event) == "hidden") return@collect

                            eventRepository.addEvent(event)
                            eventRepository.requestProfileIfMissing(event.pubkey)

                            val address = Nip99.addressTag(event)
                            val updated = synchronized(cacheLock) {
                                val previous = cachedByAddress[address]
                                if (previous == null || Nip01EventOrder.isNewer(event, previous)) {
                                    cachedByAddress[address] = event
                                    while (cachedByAddress.size > MAX_CACHED_LISTINGS) {
                                        val oldestKey = cachedByAddress.minByOrNull { it.value.created_at }?.key ?: break
                                        cachedByAddress.remove(oldestKey)
                                    }
                                    true
                                } else false
                            }
                            if (updated) {
                                remoteListingCount.incrementAndGet()
                                if (activeSubscriptionId == subId) {
                                    publishVisibleCache(selectedScope, authors, userPubkey)
                                }
                            }
                        }
                }

                val targets = (relayPool.getRelayUrls() + RelayConfig.DEFAULT_INDEXER_RELAYS + MarketplaceRelays.READ + sellerRelayTargets).distinct()
                val eoseTracker = MarketplaceRelayEoseTracker(targets)
                val eoseCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    relayPool.relayEoseSignals
                        .filter { it.subscriptionId == subId }
                        .collect { signal -> eoseTracker.markCompleted(signal.relayUrl) }
                }

                // Keep collecting after the first six distinct relays finish. A slower
                // indexer can hold listings that are absent from the faster relays.
                val reconnectableTargets = (
                    RelayConfig.DEFAULT_INDEXER_RELAYS +
                        MarketplaceRelays.READ +
                        sellerRelayTargets
                    ).toSet()
                targets.forEach { relay ->
                    relayPool.sendToRelayOrEphemeral(
                        relay,
                        request,
                        skipBadCheck = true,
                        autoReconnect = relay in reconnectableTargets
                    )
                }

                var drainStartedAt = 0L
                val completedWithinWindow = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    while (true) {
                        val completedCount = eoseTracker.completedCount()
                        val sellerRelaysCompleted = sellerRelayTargets.all(eoseTracker::hasCompleted)
                        if (completedCount >= eoseTracker.minimumResponseCount() && sellerRelaysCompleted) {
                            if (drainStartedAt == 0L) {
                                drainStartedAt = System.currentTimeMillis()
                            }
                            val graceElapsed =
                                System.currentTimeMillis() - drainStartedAt >= EOSE_DRAIN_GRACE_MS
                            if (eoseTracker.allTargetsCompleted() || graceElapsed) break
                        }
                        delay(100)
                    }
                    true
                } == true

                Log.i(
                    "MarketplaceVM",
                    "Listing fetch finished sub=$subId seller=${sellerPubkey?.take(12) ?: "all"} " +
                        "relays=${eoseTracker.completedCount()}/${eoseTracker.targetCount()} " +
                        "sellerRelays=${sellerRelayTargets.size} remoteUpdates=${remoteListingCount.get()} timeout=${!completedWithinWindow}"
                )
                eoseCollector.cancel()
                eventCollector.cancel()
                eventCollector.join()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (activeSubscriptionId == subId) {
                    _error.value = error.localizedMessage ?: error.message
                }
            } finally {
                closeSubscription(relayPool, subId, sellerRelayTargets)
                if (activeSubscriptionId == subId) {
                    activeSubscriptionId = null
                    activeSellerRelayUrls = emptyList()
                    _loading.value = false
                }
            }
        }
    }

    private fun closeSubscription(
        relayPool: RelayPool,
        subId: String,
        extraRelayUrls: List<String> = emptyList()
    ) {
        val close = ClientMessage.close(subId)
        relayPool.sendToAll(close)
        (RelayConfig.DEFAULT_INDEXER_RELAYS + MarketplaceRelays.READ + extraRelayUrls).distinct().forEach {
            relayPool.sendToRelayOrEphemeral(it, close, skipBadCheck = true)
        }
    }

    fun addOrUpdate(event: NostrEvent) {
        if (!Nip99.isMarketplaceKind(event.kind) || Nip99.dTag(event).isBlank()) return
        val key = Nip99.addressTag(event)
        synchronized(cacheLock) {
            val previous = cachedByAddress[key]
            if (previous == null || Nip01EventOrder.isNewer(event, previous)) {
                cachedByAddress[key] = event
            }
        }
        publishVisibleCache(_scope.value, currentAuthors, currentUserPubkey, currentSellerPubkey)
    }

    fun removeByAddress(pubkey: String, dTag: String) {
        if (dTag.isBlank()) return
        synchronized(cacheLock) {
            cachedByAddress.entries.removeAll { (_, event) -> event.pubkey == pubkey && Nip99.dTag(event) == dTag }
        }
        publishVisibleCache(_scope.value, currentAuthors, currentUserPubkey)
    }

    override fun onCleared() {
        activeSubscriptionId = null
        loadJob?.cancel()
        super.onCleared()
    }
}

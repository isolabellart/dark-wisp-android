package com.darkwisp.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.Filter
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.MarketplaceRelays
import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.relay.RelayConfig
import com.darkwisp.app.relay.RelayPool
import com.darkwisp.app.repo.EventRepository
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
import kotlinx.coroutines.withContext
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
    private var currentAuthors: Set<String> = emptySet()
    private var currentUserPubkey: String? = null
    private var currentSellerPubkey: String? = null
    private var currentEventRepository: EventRepository? = null

    companion object {
        private const val LOAD_TIMEOUT_MS = 12_000L
        private const val EOSE_DRAIN_GRACE_MS = 4_000L
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

    /**
     * Store one NIP-99 revision under its stable author + d-tag address.
     * Returns true when this event becomes the newest locally known revision.
     */
    private fun rememberListing(event: NostrEvent): Boolean {
        if (event.kind != Nip99.KIND_LISTING) return false
        val dTag = Nip99.dTag(event)
        if (dTag.isBlank() || Nip99.title(event).isBlank()) return false

        val address = "${event.pubkey}:$dTag"
        return synchronized(cacheLock) {
            val previous = cachedByAddress[address]
            if (previous == null || event.created_at >= previous.created_at) {
                cachedByAddress[address] = event
                while (cachedByAddress.size > MAX_CACHED_LISTINGS) {
                    val oldestKey = cachedByAddress.minByOrNull { it.value.created_at }?.key ?: break
                    cachedByAddress.remove(oldestKey)
                }
                true
            } else {
                false
            }
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
                    listOf(Nip99.title(event), Nip99.summary(event), event.content, Nip99.location(event),
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
        activeSubscriptionId = null
        loadJob?.cancel()
        oldSubId?.let { closeSubscription(relayPool, it) }

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
            try {
                // A seller storefront should render already-known listings before waiting
                // for network relays. The repository combines in-memory and ObjectBox data.
                var restoredListingCount = 0
                if (sellerPubkey != null) {
                    val locallyKnownListings = withContext(Dispatchers.IO) {
                        eventRepository.getCachedEventsByAuthorAndKinds(
                            pubkey = sellerPubkey,
                            kinds = setOf(Nip99.KIND_LISTING),
                            limit = MAX_CACHED_LISTINGS
                        )
                    }
                    locallyKnownListings.forEach { event ->
                        if (event.kind != Nip99.KIND_LISTING || event.pubkey != sellerPubkey) return@forEach
                        if (!eventRepository.isEventVisibleToUser(event)) return@forEach
                        if (rememberListing(event)) restoredListingCount++
                    }
                    if (activeSubscriptionId == subId) {
                        publishVisibleCache(selectedScope, authors, userPubkey, sellerPubkey)
                    }
                }
                Log.d(
                    "MarketplaceVM",
                    "Storefront cache restored seller=${sellerPubkey?.take(12) ?: "all"} count=$restoredListingCount"
                )

                val filter = Filter(
                    kinds = listOf(Nip99.KIND_LISTING),
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
                            if (event.kind != Nip99.KIND_LISTING) return@collect
                            if (sellerPubkey != null && event.pubkey != sellerPubkey) return@collect
                            if (sellerPubkey == null && selectedScope == MarketplaceScope.FOLLOWING && event.pubkey !in authors) return@collect
                            if (sellerPubkey == null && selectedScope == MarketplaceScope.MINE && event.pubkey != userPubkey) return@collect
                            if (!eventRepository.isEventVisibleToUser(event)) return@collect

                            val dTag = Nip99.dTag(event)
                            if (dTag.isBlank() || Nip99.title(event).isBlank()) return@collect

                            eventRepository.addEvent(event)
                            eventRepository.requestProfileIfMissing(event.pubkey)

                            val updated = rememberListing(event)
                            if (updated) {
                                remoteListingCount.incrementAndGet()
                                if (activeSubscriptionId == subId) {
                                    publishVisibleCache(selectedScope, authors, userPubkey, sellerPubkey)
                                }
                            }
                        }
                }

                val targets = (relayPool.getRelayUrls() + RelayConfig.DEFAULT_INDEXER_RELAYS + MarketplaceRelays.READ).distinct()
                val completionTracker = MarketplaceRelayCompletionTracker()
                val eoseCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    relayPool.relayEoseSignals
                        .filter { it.subscriptionId == subId }
                        .collect { signal -> completionTracker.markCompleted(signal.relayUrl) }
                }

                // Register every unique target before sending: a relay can queue the
                // request while its WebSocket is connecting and still answer later.
                // EOSE can race with the send call, so completion is reconciled by URL.
                targets.forEach { relay ->
                    completionTracker.markRequested(relay)
                    val queuedOrSent = relayPool.sendToRelayOrEphemeral(
                        relay,
                        request,
                        skipBadCheck = true
                    )
                    if (!queuedOrSent) {
                        Log.d("MarketplaceVM", "Listing request deferred or skipped relay=$relay sub=$subId")
                    }
                }

                var minimumEoseReachedAt = 0L
                val completedWithinWindow = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    while (true) {
                        val requestedCount = completionTracker.requestedCount()
                        if (requestedCount == 0) break

                        val completedCount = completionTracker.completedCount()
                        if (completedCount >= minOf(MINIMUM_EOSE_COUNT, requestedCount)) {
                            if (minimumEoseReachedAt == 0L) {
                                minimumEoseReachedAt = System.currentTimeMillis()
                            }
                            val graceElapsed =
                                System.currentTimeMillis() - minimumEoseReachedAt >= EOSE_DRAIN_GRACE_MS
                            if (completionTracker.allRequestedCompleted() || graceElapsed) break
                        }
                        delay(100)
                    }
                    true
                } == true

                Log.i(
                    "MarketplaceVM",
                    "Listing fetch finished sub=$subId seller=${sellerPubkey?.take(12) ?: "all"} " +
                        "relays=${completionTracker.completedCount()}/${completionTracker.requestedCount()} " +
                        "remoteUpdates=${remoteListingCount.get()} cacheRestored=$restoredListingCount " +
                        "timeout=${!completedWithinWindow}"
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
                closeSubscription(relayPool, subId)
                if (activeSubscriptionId == subId) {
                    activeSubscriptionId = null
                    _loading.value = false
                }
            }
        }
    }

    private fun closeSubscription(relayPool: RelayPool, subId: String) {
        val close = ClientMessage.close(subId)
        relayPool.sendToAll(close)
        (RelayConfig.DEFAULT_INDEXER_RELAYS + MarketplaceRelays.READ).distinct().forEach {
            relayPool.sendToRelayOrEphemeral(it, close, skipBadCheck = true)
        }
    }

    fun addOrUpdate(event: NostrEvent) {
        if (event.kind != Nip99.KIND_LISTING || Nip99.dTag(event).isBlank()) return
        val key = event.pubkey + ":" + Nip99.dTag(event)
        synchronized(cacheLock) {
            val previous = cachedByAddress[key]
            if (previous == null || event.created_at >= previous.created_at) {
                cachedByAddress[key] = event
            }
        }
        publishVisibleCache(_scope.value, currentAuthors, currentUserPubkey, currentSellerPubkey)
    }

    fun removeByAddress(pubkey: String, dTag: String) {
        if (dTag.isBlank()) return
        synchronized(cacheLock) {
            cachedByAddress.remove("$pubkey:$dTag")
        }
        publishVisibleCache(_scope.value, currentAuthors, currentUserPubkey)
    }

    override fun onCleared() {
        activeSubscriptionId = null
        loadJob?.cancel()
        super.onCleared()
    }
}

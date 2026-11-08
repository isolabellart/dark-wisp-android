package com.darkwisp.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.Filter
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.relay.RelayConfig
import com.darkwisp.app.relay.RelayPool
import com.darkwisp.app.repo.EventRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
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

    private val subscriptionSequence = AtomicLong()
    private val cachedByAddress = linkedMapOf<String, NostrEvent>()
    private val cacheLock = Any()
    private var loadJob: Job? = null
    private var activeSubscriptionId: String? = null
    private var currentAuthors: Set<String> = emptySet()
    private var currentUserPubkey: String? = null

    companion object {
        private const val LOAD_TIMEOUT_MS = 15_000L
        private const val MAX_CACHED_LISTINGS = 500
        private val MARKETPLACE_RELAYS = listOf(
            // The current Plebeian Market profile advertises these read/write relays.
            "wss://relay.dwadziesciajeden.pl",
            "wss://nostr.einundzwanzig.space",
            "wss://espelho.girino.org",
            "wss://adre.su",
            "wss://nostr.mom",
            "wss://purplepag.es",
            // Barattolo's NIP-99 listings are published on its public relay.
            "wss://relay.barattolo.store"
        )
    }

    fun setScope(scope: MarketplaceScope) {
        _scope.value = scope
    }

    private fun matchesScope(event: NostrEvent, selectedScope: MarketplaceScope, authors: Set<String>, userPubkey: String?): Boolean =
        when (selectedScope) {
            MarketplaceScope.ALL -> true
            MarketplaceScope.FOLLOWING -> event.pubkey in authors
            MarketplaceScope.MINE -> event.pubkey == userPubkey
        }

    private fun publishVisibleCache(selectedScope: MarketplaceScope, authors: Set<String>, userPubkey: String?) {
        val snapshot = synchronized(cacheLock) { cachedByAddress.values.toList() }
        _listings.value = snapshot
            .asSequence()
            .filter { matchesScope(it, selectedScope, authors, userPubkey) }
            .sortedByDescending { it.created_at }
            .take(MAX_CACHED_LISTINGS)
            .toList()
    }

    fun load(
        selectedScope: MarketplaceScope,
        relayPool: RelayPool,
        eventRepository: EventRepository,
        followedPubkeys: List<String>,
        userPubkey: String?
    ) {
        _scope.value = selectedScope
        currentUserPubkey = userPubkey

        val authors = when (selectedScope) {
            MarketplaceScope.ALL -> emptySet()
            MarketplaceScope.FOLLOWING -> followedPubkeys.toSet()
            MarketplaceScope.MINE -> setOfNotNull(userPubkey)
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
        publishVisibleCache(selectedScope, authors, userPubkey)
        _loading.value = true
        _error.value = null

        val subId = "marketplace-${subscriptionSequence.incrementAndGet()}"
        activeSubscriptionId = subId

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val filter = Filter(
                    kinds = listOf(Nip99.KIND_LISTING),
                    authors = authors.takeIf { it.isNotEmpty() }?.toList(),
                    limit = 500
                )
                val request = ClientMessage.req(subId, filter)

                // Search the account's configured relays, public indexers, and
                // the two NIP-99 markets' own relays. This avoids relying on a
                // single indexer that may not carry externally published ads.
                // Subscribe to the result stream before sending REQ. Some relays reply
                // immediately; collecting afterwards loses their EVENT/EOSE frames.
                val eventCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                        relayPool.relayEvents
                            .filter { it.subscriptionId == subId }
                            .collect { relayEvent ->
                                val event = relayEvent.event
                                if (event.kind != Nip99.KIND_LISTING) return@collect
                                if (selectedScope == MarketplaceScope.FOLLOWING && event.pubkey !in authors) return@collect
                                if (selectedScope == MarketplaceScope.MINE && event.pubkey != userPubkey) return@collect
                                if (!eventRepository.isEventVisibleToUser(event)) return@collect

                                val dTag = Nip99.dTag(event)
                                if (dTag.isBlank() || Nip99.title(event).isBlank()) return@collect

                                eventRepository.addEvent(event)
                                eventRepository.requestProfileIfMissing(event.pubkey)

                                val address = event.pubkey + ":" + dTag
                                val updated = synchronized(cacheLock) {
                                    val previous = cachedByAddress[address]
                                    if (previous == null || event.created_at >= previous.created_at) {
                                        cachedByAddress[address] = event
                                        while (cachedByAddress.size > MAX_CACHED_LISTINGS) {
                                            val oldestKey = cachedByAddress.minByOrNull { it.value.created_at }?.key ?: break
                                            cachedByAddress.remove(oldestKey)
                                        }
                                        true
                                    } else false
                                }
                                if (updated && activeSubscriptionId == subId) {
                                    publishVisibleCache(selectedScope, authors, userPubkey)
                                }
                            }
                    }
                }

                val targets = (relayPool.getRelayUrls() + RelayConfig.DEFAULT_INDEXER_RELAYS + MARKETPLACE_RELAYS).distinct()
                targets.forEach { relay ->
                    relayPool.sendToRelayOrEphemeral(relay, request, skipBadCheck = true)
                }
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
        (RelayConfig.DEFAULT_INDEXER_RELAYS + MARKETPLACE_RELAYS).distinct().forEach {
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
        publishVisibleCache(_scope.value, currentAuthors, currentUserPubkey)
    }

    override fun onCleared() {
        activeSubscriptionId = null
        loadJob?.cancel()
        super.onCleared()
    }
}

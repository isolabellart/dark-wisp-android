package com.darkwisp.app.repo

import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.Filter
import com.darkwisp.app.nostr.MarketplaceRelays
import com.darkwisp.app.relay.RelayConfig
import com.darkwisp.app.relay.RelayEvent
import com.darkwisp.app.relay.RelayPool
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * Fetch a peer's kind 10002 relay list (NIP-65) from indexer relays + the connected pool,
 * then populate [RelayListRepository] with the freshest result. Caller can then ask the
 * repository for the peer's read/inbox or write/outbox relays.
 */
object PeerRelayListLookup {
    private val requestSequence = AtomicLong()

    private const val LOOKUP_TIMEOUT_MS = 10_000L
    private const val EOSE_DRAIN_GRACE_MS = 1_500L

    suspend fun fetch(
        pubkey: String,
        relayPool: RelayPool,
        relayListRepo: RelayListRepository,
        extraRelayUrls: List<String> = emptyList()
    ) {
        val subId = "peer-relays-${pubkey.take(8)}-${requestSequence.incrementAndGet()}"
        val filter = Filter(
            kinds = listOf(10002),
            authors = listOf(pubkey),
            limit = 1
        )
        val reqMsg = ClientMessage.req(subId, filter)
        val closeMsg = ClientMessage.close(subId)
        // The peer's kind 10002 may be stored on marketplace-specific relays or
        // on relays explicitly supplied by an NIP-19 reference.
        val discoveryRelayUrls = (
            relayPool.getReadRelayUrls() +
                RelayConfig.DEFAULT_INDEXER_RELAYS +
                MarketplaceRelays.READ +
                extraRelayUrls.filter { RelayConfig.isValidUrl(it) }
            ).filter { RelayConfig.isValidUrl(it) }.distinct()
        val requiredRelayUrls = extraRelayUrls
            .filter { RelayConfig.isValidUrl(it) }
            .distinct()
        val results = Collections.synchronizedList(mutableListOf<RelayEvent>())
        val eoseTracker = AddressableRelayEoseTracker(discoveryRelayUrls)

        coroutineScope {
            // Both collectors start before any REQ is sent: fast relays can return
            // EVENT and EOSE immediately after the WebSocket opens.
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                relayPool.relayEvents
                    .filter { it.subscriptionId == subId }
                    .collect { results.add(it) }
            }
            val eoseCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                relayPool.relayEoseSignals
                    .filter { it.subscriptionId == subId }
                    .collect { signal -> eoseTracker.markCompleted(signal.relayUrl) }
            }

            try {
                for (url in discoveryRelayUrls) {
                    relayPool.sendToRelayOrEphemeral(
                        url,
                        reqMsg,
                        skipBadCheck = true,
                        autoReconnect = true
                    )
                }
                relayPool.sendToAll(reqMsg)

                var drainStartedAt = 0L
                val completedWithinWindow = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                    while (true) {
                        if (eoseTracker.hasSatisfiedRequiredResponses(requiredRelayUrls)) {
                            if (drainStartedAt == 0L) {
                                drainStartedAt = System.currentTimeMillis()
                            }
                            val graceElapsed =
                                System.currentTimeMillis() - drainStartedAt >= EOSE_DRAIN_GRACE_MS
                            if (eoseTracker.allTargetsCompleted() || graceElapsed) break
                        }
                        delay(100L)
                    }
                    true
                } == true

                android.util.Log.i(
                    "PeerRelayListLookup",
                    "NIP-65 lookup pubkey=${pubkey.take(12)} " +
                        "relays=${eoseTracker.completedCount()}/${eoseTracker.targetCount()} " +
                        "required=${requiredRelayUrls.size} timeout=${!completedWithinWindow}"
                )
            } finally {
                eoseCollector.cancelAndJoin()
                collector.cancelAndJoin()
                for (url in discoveryRelayUrls) {
                    relayPool.sendToRelayOrEphemeral(url, closeMsg, skipBadCheck = true)
                }
                relayPool.sendToAll(closeMsg)
            }
        }

        val best = synchronized(results) { results.maxByOrNull { it.event.created_at } }
        if (best != null) {
            relayListRepo.updateFromEvent(best.event)
        }
    }
}

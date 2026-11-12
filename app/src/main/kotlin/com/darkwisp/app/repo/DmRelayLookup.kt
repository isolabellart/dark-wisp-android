package com.darkwisp.app.repo

import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.Filter
import com.darkwisp.app.nostr.Nip51
import com.darkwisp.app.relay.RelayConfig
import com.darkwisp.app.relay.RelayEvent
import com.darkwisp.app.relay.RelayPool
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Fetch a peer's kind 10050 DM relays from indexer relays + the connected pool.
 *
 * The collector is started undispatched BEFORE sending REQ: relayEvents is a
 * non-replaying SharedFlow, so a fast relay response would otherwise be lost.
 * A failed/empty refresh preserves the last known list; an actual newest kind
 * 10050 event with no relay tags is cached as an explicit empty list.
 */
object DmRelayLookup {
    private val requestSequence = AtomicLong()

    suspend fun fetch(
        pubkey: String,
        relayPool: RelayPool,
        dmRepo: DmRepository,
        forceRefresh: Boolean = false
    ): List<String> {
        val cached = dmRepo.getCachedDmRelays(pubkey)
        if (!forceRefresh && cached != null) return cached

        val subId = "dm_relay_${pubkey.take(8)}_${requestSequence.incrementAndGet()}"
        val filter = Filter(
            kinds = listOf(Nip51.KIND_DM_RELAYS),
            authors = listOf(pubkey),
            limit = 1
        )
        val reqMsg = ClientMessage.req(subId, filter)
        val results = mutableListOf<RelayEvent>()

        coroutineScope {
            // SharedFlow has no replay: subscribe before the first REQ can be sent.
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                relayPool.relayEvents
                    .filter { it.subscriptionId == subId }
                    .collect { results.add(it) }
            }
            try {
                for (url in RelayConfig.DEFAULT_INDEXER_RELAYS) {
                    relayPool.sendToRelayOrEphemeral(url, reqMsg, skipBadCheck = true)
                }
                relayPool.sendToAll(reqMsg)

                // Keep the collection window so we can choose the freshest response
                // across indexers and already-connected relays.
                delay(4000L)
            } finally {
                collector.cancelAndJoin()
                val closeMsg = ClientMessage.close(subId)
                for (url in RelayConfig.DEFAULT_INDEXER_RELAYS) {
                    relayPool.sendToRelay(url, closeMsg)
                }
                relayPool.sendToAll(closeMsg)
            }
        }

        val best = results.maxByOrNull { it.event.created_at }
        if (best != null) {
            val urls = Nip51.parseRelaySet(best.event)
            // Cache even an empty set: a received latest kind 10050 with no relay
            // entries is different from a refresh that returned no event at all.
            dmRepo.cacheDmRelays(pubkey, urls)
            return urls
        }

        // A timeout/no response must not erase a previously observed seller list.
        return cached ?: emptyList()
    }
}

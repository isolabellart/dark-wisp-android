package com.darkwisp.app.repo

import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.MarketplaceRelays
import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.relay.RelayPool
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Publish Marketplace mutations to both personal write relays and the public
 * Marketplace relay set. Success is reported only after at least one relay ACK.
 */
internal suspend fun publishMarketplaceEvent(
    relayPool: RelayPool,
    event: NostrEvent,
    failureMessage: String = "Marketplace update was not confirmed by any relay"
) {
    val message = ClientMessage.event(event)

    suspend fun prepareRelays(forceReconnect: Boolean) = coroutineScope {
        // Prepare the two write groups concurrently and bound connection waiting.
        val personal = async {
            if (forceReconnect) relayPool.cycleWriteRelays(timeoutMs = 2_000)
            else relayPool.ensureWriteRelaysConnected(timeoutMs = 2_000)
        }
        val marketplace = async {
            relayPool.ensureMarketplaceWriteRelaysConnected(
                MarketplaceRelays.WRITE,
                timeoutMs = 2_000,
                forceReconnect = forceReconnect
            )
        }
        personal.await()
        marketplace.await()
    }

    prepareRelays(forceReconnect = false)
    var outcome = relayPool.publishWithConfirmation(event.id, timeoutMs = 4_000) {
        relayPool.sendToWriteRelays(message) +
            relayPool.sendToMarketplaceWriteRelays(message, MarketplaceRelays.WRITE)
    }
    if (outcome.sentCount == 0 || !outcome.confirmed) {
        prepareRelays(forceReconnect = true)
        outcome = relayPool.publishWithConfirmation(event.id, timeoutMs = 4_000) {
            relayPool.sendToWriteRelays(message) +
                relayPool.sendToMarketplaceWriteRelays(message, MarketplaceRelays.WRITE)
        }
    }
    if (outcome.sentCount == 0 || !outcome.confirmed) {
        throw IllegalStateException(failureMessage)
    }
}

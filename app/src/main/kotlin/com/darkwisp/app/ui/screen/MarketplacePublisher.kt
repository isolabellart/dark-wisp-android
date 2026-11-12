package com.darkwisp.app.ui.screen

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import android.content.Context
import com.darkwisp.app.R
import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.MarketplaceRelays
import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.relay.RelayPool

/**
 * Publish marketplace events to the account's write relays and the shared
 * marketplace relays, and do not report success until at least one relay ACKs.
 */
internal suspend fun publishMarketplaceEvent(
    relayPool: RelayPool,
    event: NostrEvent,
    context: Context
) {
    val message = ClientMessage.event(event)

    suspend fun prepareRelays(forceReconnect: Boolean) = coroutineScope {
        // The Marketplace action should not wait 8 seconds for every configured relay.
        // Prepare the personal and curated Marketplace write sets concurrently; any
        // connected writable relay can then accept the event.
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
        throw IllegalStateException(context.getString(R.string.marketplace_publish_unconfirmed))
    }
}

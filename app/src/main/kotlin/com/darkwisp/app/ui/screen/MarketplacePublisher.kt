package com.darkwisp.app.ui.screen

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
    relayPool.ensureWriteRelaysConnected(timeoutMs = 8_000)
    relayPool.ensureMarketplaceWriteRelaysConnected(
        MarketplaceRelays.WRITE,
        timeoutMs = 8_000
    )
    var outcome = relayPool.publishWithConfirmation(event.id) {
        relayPool.sendToWriteRelays(message) +
            relayPool.sendToMarketplaceWriteRelays(message, MarketplaceRelays.WRITE)
    }
    if (outcome.sentCount == 0 || !outcome.confirmed) {
        relayPool.cycleWriteRelays(timeoutMs = 8_000)
        relayPool.ensureMarketplaceWriteRelaysConnected(
            MarketplaceRelays.WRITE,
            timeoutMs = 8_000,
            forceReconnect = true
        )
        outcome = relayPool.publishWithConfirmation(event.id) {
            relayPool.sendToWriteRelays(message) +
                relayPool.sendToMarketplaceWriteRelays(message, MarketplaceRelays.WRITE)
        }
    }
    if (outcome.sentCount == 0 || !outcome.confirmed) {
        throw IllegalStateException(context.getString(R.string.marketplace_publish_unconfirmed))
    }
}

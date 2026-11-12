package com.darkwisp.app.ui.screen

import android.content.Context
import com.darkwisp.app.R
import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.relay.RelayPool

/** UI adapter that preserves the localized publishing error for Marketplace actions. */
internal suspend fun publishMarketplaceEvent(
    relayPool: RelayPool,
    event: NostrEvent,
    context: Context
) {
    com.darkwisp.app.repo.publishMarketplaceEvent(
        relayPool = relayPool,
        event = event,
        failureMessage = context.getString(R.string.marketplace_publish_unconfirmed)
    )
}

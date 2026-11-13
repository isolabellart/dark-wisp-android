package com.darkwisp.app.nostr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceRelaysTest {
    @Test
    fun `Plebeian Market relay is used for reads only`() {
        val relay = "wss://relay.plebeian.market"

        assertTrue(MarketplaceRelays.READ.contains(relay))
        assertFalse(MarketplaceRelays.WRITE.contains(relay))
    }
}

package com.darkwisp.app.repo

import com.darkwisp.app.nostr.MarketplaceRelays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerRelayListLookupPlanTest {
    @Test
    fun `npub seller lookup requires marketplace relays when explicitly requested`() {
        val required = buildPeerRelayListRequiredResponses(
            extraRelayUrls = emptyList(),
            requireMarketplaceRelayEose = true
        )

        assertEquals(MarketplaceRelays.READ.distinct(), required)
    }

    @Test
    fun `naddr lookup keeps caller relay hints as its required set`() {
        val hints = listOf("wss://relay.one.example", "wss://relay.two.example")
        val required = buildPeerRelayListRequiredResponses(
            extraRelayUrls = hints,
            requireMarketplaceRelayEose = false
        )

        assertEquals(hints, required)
        assertTrue(required.none { it in MarketplaceRelays.READ })
    }

    @Test
    fun `seller lookup preserves relay hints and adds marketplace read relays`() {
        val hints = listOf("wss://relay.one.example")
        val required = buildPeerRelayListRequiredResponses(
            extraRelayUrls = hints,
            requireMarketplaceRelayEose = true
        )

        assertEquals((hints + MarketplaceRelays.READ).distinct(), required)
    }
}

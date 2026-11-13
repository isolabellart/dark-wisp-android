package com.darkwisp.app.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressableRelayPlanTest {
    @Test
    fun `NIP19 hints and seller NIP65 outbox are priority relay targets`() {
        val nip19Hints = listOf(
            "wss://relay.conduit.market",
            "wss://relay.ditto.pub",
            "wss://nos.lol",
            "wss://relay.nostr.net"
        )
        val sellerWriteRelays = listOf("wss://seller-outbox.example")
        val topRelays = listOf("wss://top-indexer.example", "wss://nos.lol")
        val accountReadRelays = listOf("wss://account-read.example")
        val marketplaceRelays = listOf("wss://relay.conduit.market", "wss://relay.plebeian.market")

        val plan = buildAddressableRelayPlan(
            priorityRelays = nip19Hints + sellerWriteRelays,
            topRelays = topRelays,
            accountReadRelays = accountReadRelays,
            marketplaceRelays = marketplaceRelays
        )

        assertEquals(nip19Hints + sellerWriteRelays, plan.priorityRelayUrls)
        assertTrue(plan.targetUrls.containsAll(nip19Hints + sellerWriteRelays))
        assertEquals(
            listOf(
                "wss://relay.conduit.market",
                "wss://relay.ditto.pub",
                "wss://nos.lol",
                "wss://relay.nostr.net",
                "wss://seller-outbox.example",
                "wss://top-indexer.example",
                "wss://account-read.example",
                "wss://relay.plebeian.market"
            ),
            plan.targetUrls
        )
    }

    @Test
    fun `relay planning removes blank and duplicate destinations while preserving priority order`() {
        val plan = buildAddressableRelayPlan(
            priorityRelays = listOf("wss://priority.example", "", "wss://priority.example"),
            topRelays = listOf("wss://top.example", "wss://priority.example"),
            accountReadRelays = emptyList(),
            marketplaceRelays = listOf("wss://market.example")
        )

        assertEquals(listOf("wss://priority.example"), plan.priorityRelayUrls)
        assertEquals(
            listOf("wss://priority.example", "wss://top.example", "wss://market.example"),
            plan.targetUrls
        )
    }
}

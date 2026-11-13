package com.darkwisp.app.nostr

import org.junit.Assert.assertEquals
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

    @Test
    fun `Conduit naddr relay hints are included in marketplace read discovery`() {
        val hintedRelays = listOf(
            "wss://relay.conduit.market",
            "wss://relay.ditto.pub",
            "wss://nos.lol",
            "wss://relay.nostr.net"
        )

        assertTrue(MarketplaceRelays.READ.containsAll(hintedRelays))
        assertFalse(MarketplaceRelays.WRITE.contains("wss://relay.ditto.pub"))
        assertFalse(MarketplaceRelays.WRITE.contains("wss://relay.nostr.net"))
    }

    @Test
    fun `provided Conduit naddr decodes to the expected NIP99 coordinate and relay hints`() {
        val naddr = "naddr1qvzqqqrkcgpzqd6wayar8uak7yq5xfsps0f40hs3sj446vwxtk4kwayuuumxyv68qyd8wumn8ghj7un9d3shjtnrdahxgatfwshx6ctjddjhgqg4waehxw309aex2mrp0yhxg6t5w3hjuur4vgqs6amnwvaz7tmwdaejumr0dsq32amnwvaz7tmjv4kxz7fwdehhxarj9ehx2aqqysunvvtxvgcrve3d8pjkgwfdxsenje3dvycrqcedx4jxxcek8pnrqdmpxvusks2sqw"
        val decoded = Nip19.naddrDecode(naddr)

        assertEquals("961fb06f-8ed9-439f-a00c-5dcc68f07a39", decoded.dTag)
        assertEquals("374ee93a33f3b6f10143260183d357de1184ab5d31c65dab67749ce736623347", decoded.author)
        assertEquals(30402, decoded.kind)
        assertEquals(
            listOf(
                "wss://relay.conduit.market",
                "wss://relay.ditto.pub",
                "wss://nos.lol",
                "wss://relay.nostr.net"
            ),
            decoded.relays
        )
    }
}

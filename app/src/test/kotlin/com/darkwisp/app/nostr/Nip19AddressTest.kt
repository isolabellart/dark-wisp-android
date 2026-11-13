package com.darkwisp.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Nip19AddressTest {
    @Test
    fun `Conduit NIP99 address decodes author coordinate and every relay hint`() {
        val naddr = "naddr1qvzqqqrkcgpzqd6wayar8uak7yq5xfsps0f40hs3sj446vwxtk4kwayuuumxyv68qyd8wumn8ghj7un9d3shjtnrdahxgatfwshx6ctjddjhgqg4waehxw309aex2mrp0yhxg6t5w3hjuur4vgqs6amnwvaz7tmwdaejumr0dsq32amnwvaz7tmjv4kxz7fwdehhxarj9ehx2aqqysunvvtxvgcrve3d8pjkgwfdxsenje3dvycrqcedx4jxxcek8pnrqdmpxvusks2sqw"

        val decoded = Nip19.naddrDecode(naddr)

        assertEquals("961fb06f-8ed9-439f-a00c-5dcc68f07a39", decoded.dTag)
        assertEquals(
            "374ee93a33f3b6f10143260183d357de1184ab5d31c65dab67749ce736623347",
            decoded.author
        )
        assertEquals(Nip99.KIND_LISTING, decoded.kind)
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

    @Test
    fun `Conduit NIP99 address also decodes from a nostr URI`() {
        val naddr = "nostr:naddr1qvzqqqrkcgpzqd6wayar8uak7yq5xfsps0f40hs3sj446vwxtk4kwayuuumxyv68qyd8wumn8ghj7un9d3shjtnrdahxgatfwshx6ctjddjhgqg4waehxw309aex2mrp0yhxg6t5w3hjuur4vgqs6amnwvaz7tmwdaejumr0dsq32amnwvaz7tmjv4kxz7fwdehhxarj9ehx2aqqysunvvtxvgcrve3d8pjkgwfdxsenje3dvycrqcedx4jxxcek8pnrqdmpxvusks2sqw"

        val decoded = Nip19.decodeNostrUri(naddr)

        assertNotNull(decoded)
        val address = decoded as NostrUriData.AddressRef
        assertEquals(Nip99.KIND_LISTING, address.kind)
        assertEquals(4, address.relays.size)
    }
}

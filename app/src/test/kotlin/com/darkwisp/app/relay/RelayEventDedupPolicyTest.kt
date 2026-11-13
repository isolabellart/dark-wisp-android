package com.darkwisp.app.relay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayEventDedupPolicyTest {
    @Test
    fun `addressable lookup receives events already seen by another subscription`() {
        assertTrue(RelayEventDedupPolicy.shouldBypass("addr-42"))
        assertTrue(RelayEventDedupPolicy.shouldBypass("addr-99"))
    }

    @Test
    fun `peer relay list lookup receives previously seen NIP65 events`() {
        assertTrue(RelayEventDedupPolicy.shouldBypass("peer-relays-a1b2c3d4-7"))
    }

    @Test
    fun `ordinary marketplace and feed subscriptions keep normal event deduplication`() {
        assertFalse(RelayEventDedupPolicy.shouldBypass("marketplace-3"))
        assertFalse(RelayEventDedupPolicy.shouldBypass("feed-main"))
    }
}

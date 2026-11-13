package com.darkwisp.app.relay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayEventDedupPolicyTest {
    private val defaultPrefixes = listOf(
        "thread-", "user", "quote-", "editprofile", "notif", "dms", "search-", "hashtag-",
        "addr-", "peer-relays-"
    )

    @Test
    fun `addressable lookup receives events already seen by another subscription`() {
        assertTrue(shouldBypassRelayEventDedup("addr-42", defaultPrefixes))
        assertTrue(shouldBypassRelayEventDedup("addr-99", defaultPrefixes))
    }

    @Test
    fun `peer relay list lookup receives previously seen NIP65 events`() {
        assertTrue(shouldBypassRelayEventDedup("peer-relays-a1b2c3d4-7", defaultPrefixes))
    }

    @Test
    fun `ordinary marketplace and feed subscriptions keep normal event deduplication`() {
        assertFalse(shouldBypassRelayEventDedup("marketplace-3", defaultPrefixes))
        assertFalse(shouldBypassRelayEventDedup("feed-main", defaultPrefixes))
    }
}

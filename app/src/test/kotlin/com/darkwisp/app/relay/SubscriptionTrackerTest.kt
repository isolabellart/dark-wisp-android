package com.darkwisp.app.relay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTrackerTest {
    @Test
    fun `ordinary subscriptions respect the per relay soft cap`() {
        val tracker = SubscriptionTracker()
        val relay = "wss://relay.example"

        repeat(SubscriptionTracker.SOFT_CAP) { index ->
            tracker.track(relay, "ordinary-$index")
        }

        assertFalse(tracker.hasCapacity(relay, "ordinary-overflow"))
    }

    @Test
    fun `marketplace address and peer relay lookups bypass a saturated relay cap`() {
        val tracker = SubscriptionTracker()
        val relay = "wss://relay.example"

        repeat(SubscriptionTracker.SOFT_CAP) { index ->
            tracker.track(relay, "ordinary-$index")
        }

        assertTrue(tracker.hasCapacity(relay, "marketplace-storefront-1"))
        assertTrue(tracker.hasCapacity(relay, "addr-naddr-1"))
        assertTrue(tracker.hasCapacity(relay, "addr-history-1"))
        assertTrue(tracker.hasCapacity(relay, "peer-relays-seller-1"))
    }
}

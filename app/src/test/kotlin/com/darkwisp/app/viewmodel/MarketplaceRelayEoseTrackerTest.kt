package com.darkwisp.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceRelayEoseTrackerTest {
    @Test
    fun `duplicate EOSE from one relay counts only once`() {
        val tracker = MarketplaceRelayEoseTracker(listOf("wss://one.example", "wss://two.example"))
        tracker.markCompleted("wss://one.example")
        tracker.markCompleted("wss://one.example")

        assertEquals(1, tracker.completedCount())
        assertFalse(tracker.allTargetsCompleted())
    }

    @Test
    fun `unknown relay cannot satisfy the response threshold`() {
        val tracker = MarketplaceRelayEoseTracker(listOf("wss://expected.example"))
        tracker.markCompleted("wss://other.example")

        assertEquals(0, tracker.completedCount())
        assertEquals(1, tracker.minimumResponseCount())
    }

    @Test
    fun `seller relay completion can be checked independently`() {
        val tracker = MarketplaceRelayEoseTracker(listOf("wss://indexer.example", "wss://seller.example"))
        tracker.markCompleted("wss://indexer.example")

        assertFalse(tracker.hasCompleted("wss://seller.example"))
        tracker.markCompleted("wss://seller.example")
        assertTrue(tracker.hasCompleted("wss://seller.example"))
    }

    @Test
    fun `all distinct targets complete the request`() {
        val tracker = MarketplaceRelayEoseTracker(listOf("wss://one.example", "wss://two.example"))
        tracker.markCompleted("wss://one.example")
        tracker.markCompleted("wss://two.example")

        assertEquals(2, tracker.completedCount())
        assertTrue(tracker.allTargetsCompleted())
    }

    @Test
    fun `empty targets are already complete`() {
        val tracker = MarketplaceRelayEoseTracker(emptyList())

        assertEquals(0, tracker.minimumResponseCount())
        assertTrue(tracker.allTargetsCompleted())
    }
}

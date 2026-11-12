package com.darkwisp.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceRelayCompletionTrackerTest {
    @Test
    fun `completion waits for every requested relay`() {
        val tracker = MarketplaceRelayCompletionTracker()
        tracker.markRequested("wss://one.example")
        tracker.markRequested("wss://two.example")
        tracker.markRequested("wss://three.example")

        tracker.markCompleted("wss://one.example")
        tracker.markCompleted("wss://two.example")

        assertEquals(3, tracker.requestedCount())
        assertEquals(2, tracker.completedCount())
        assertFalse(tracker.allRequestedCompleted())

        tracker.markCompleted("wss://three.example")

        assertEquals(3, tracker.completedCount())
        assertTrue(tracker.allRequestedCompleted())
    }

    @Test
    fun `duplicate EOSE from one relay counts only once`() {
        val tracker = MarketplaceRelayCompletionTracker()
        tracker.markRequested("wss://one.example")
        tracker.markRequested("wss://two.example")

        tracker.markCompleted("wss://one.example")
        tracker.markCompleted("wss://one.example")

        assertEquals(1, tracker.completedCount())
        assertFalse(tracker.allRequestedCompleted())
    }

    @Test
    fun `EOSE from an unrequested relay does not complete a requested relay`() {
        val tracker = MarketplaceRelayCompletionTracker()
        tracker.markRequested("wss://expected.example")
        tracker.markCompleted("wss://other.example")

        assertEquals(0, tracker.completedCount())
        assertFalse(tracker.allRequestedCompleted())
    }

    @Test
    fun `EOSE arriving before request registration is retained`() {
        val tracker = MarketplaceRelayCompletionTracker()
        tracker.markCompleted("wss://fast.example")
        tracker.markRequested("wss://fast.example")

        assertEquals(1, tracker.completedCount())
        assertTrue(tracker.allRequestedCompleted())
    }

    @Test
    fun `empty target set is already complete`() {
        val tracker = MarketplaceRelayCompletionTracker()

        assertEquals(0, tracker.requestedCount())
        assertEquals(0, tracker.completedCount())
        assertTrue(tracker.allRequestedCompleted())
    }
}

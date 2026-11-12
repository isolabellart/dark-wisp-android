package com.darkwisp.app.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressableRelayEoseTrackerTest {
    @Test
    fun `counts only distinct expected relays`() {
        val tracker = AddressableRelayEoseTracker(
            listOf("wss://one.example", "wss://two.example", "wss://three.example")
        )

        tracker.markCompleted("wss://one.example")
        tracker.markCompleted("wss://one.example")
        tracker.markCompleted("wss://unknown.example")

        assertEquals(1, tracker.completedCount())
        assertFalse(tracker.allTargetsCompleted())
    }

    @Test
    fun `three responding relays satisfy the minimum threshold`() {
        val tracker = AddressableRelayEoseTracker(
            listOf(
                "wss://one.example",
                "wss://two.example",
                "wss://three.example",
                "wss://four.example"
            )
        )

        tracker.markCompleted("wss://one.example")
        tracker.markCompleted("wss://two.example")
        tracker.markCompleted("wss://three.example")

        assertEquals(3, tracker.minimumResponseCount())
        assertEquals(3, tracker.completedCount())
        assertFalse(tracker.allTargetsCompleted())
    }

    @Test
    fun `all targets are complete when every expected relay responds`() {
        val tracker = AddressableRelayEoseTracker(
            listOf("wss://one.example", "wss://two.example")
        )

        tracker.markCompleted("wss://one.example")
        tracker.markCompleted("wss://two.example")

        assertEquals(2, tracker.minimumResponseCount())
        assertTrue(tracker.allTargetsCompleted())
    }

    @Test
    fun `empty target set has no response requirement`() {
        val tracker = AddressableRelayEoseTracker(emptyList())

        assertEquals(0, tracker.targetCount())
        assertEquals(0, tracker.minimumResponseCount())
        assertTrue(tracker.allTargetsCompleted())
    }
}

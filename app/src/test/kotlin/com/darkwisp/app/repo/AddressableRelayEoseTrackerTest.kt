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
        assertTrue(tracker.hasCompleted("wss://one.example"))
        assertFalse(tracker.hasCompleted("wss://two.example"))
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
    fun `minimum response count does not finish while a required NIP19 relay is pending`() {
        val required = listOf("wss://hint-one.example", "wss://hint-two.example")
        val tracker = AddressableRelayEoseTracker(
            listOf("wss://hint-one.example", "wss://hint-two.example", "wss://indexer.example")
        )

        tracker.markCompleted("wss://hint-one.example")
        tracker.markCompleted("wss://indexer.example")
        tracker.markCompleted("wss://hint-two.example")

        assertTrue(tracker.hasSatisfiedRequiredResponses(required))
    }

    @Test
    fun `minimum response count still waits for every required NIP19 relay`() {
        val required = listOf("wss://hint-one.example", "wss://hint-two.example")
        val tracker = AddressableRelayEoseTracker(
            listOf("wss://hint-one.example", "wss://hint-two.example", "wss://indexer.example")
        )

        tracker.markCompleted("wss://hint-one.example")
        tracker.markCompleted("wss://indexer.example")

        assertEquals(2, tracker.completedCount())
        assertFalse(tracker.hasSatisfiedRequiredResponses(required))
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

package com.darkwisp.app.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class EventRepositoryReactionStateTest {

    @Test
    fun remoteDeletionRemovesOnlyTheExplicitReactionId() {
        val knownIds = setOf("reaction-1", "reaction-2")

        val toRemove = reactionIdsToRemove(
            knownIds = knownIds,
            explicitlyDeletedIds = setOf("reaction-1"),
            latestReactionId = "reaction-2"
        )

        assertEquals(setOf("reaction-1"), toRemove)
        assertEquals(setOf("reaction-2"), knownIds - toRemove)
    }

    @Test
    fun manualUnlikeCanRemoveEveryKnownDuplicate() {
        val knownIds = setOf("reaction-1", "reaction-2")

        val toRemove = reactionIdsToRemove(
            knownIds = knownIds,
            explicitlyDeletedIds = knownIds,
            latestReactionId = "reaction-2"
        )

        assertEquals(knownIds, toRemove)
        assertEquals(emptySet<String>(), knownIds - toRemove)
    }

    @Test
    fun missingExplicitIdsFallBackToLatestKnownReaction() {
        val knownIds = setOf("reaction-1", "reaction-2")

        val toRemove = reactionIdsToRemove(
            knownIds = knownIds,
            explicitlyDeletedIds = emptySet(),
            latestReactionId = "reaction-2"
        )

        assertEquals(setOf("reaction-2"), toRemove)
    }

    @Test
    fun missingLatestReactionFallsBackToAllKnownIds() {
        val knownIds = setOf("reaction-1", "reaction-2")

        val toRemove = reactionIdsToRemove(
            knownIds = knownIds,
            explicitlyDeletedIds = emptySet(),
            latestReactionId = null
        )

        assertEquals(knownIds, toRemove)
    }
}

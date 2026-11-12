package com.darkwisp.app.repo

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks completion for one addressable-event fetch.
 *
 * A response counts once per expected relay URL, never once per EOSE frame.
 */
internal class AddressableRelayEoseTracker(targetRelayUrls: Collection<String>) {
    private val targets = targetRelayUrls.filter { it.isNotBlank() }.toSet()
    private val completed = ConcurrentHashMap.newKeySet<String>()

    fun markCompleted(relayUrl: String) {
        if (relayUrl in targets) completed.add(relayUrl)
    }

    fun targetCount(): Int = targets.size

    fun completedCount(): Int = completed.size

    fun minimumResponseCount(): Int = minOf(MINIMUM_RESPONSES, targets.size)

    fun allTargetsCompleted(): Boolean = completed.containsAll(targets)

    private companion object {
        const val MINIMUM_RESPONSES = 3
    }
}
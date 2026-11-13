package com.darkwisp.app.viewmodel

import java.util.concurrent.ConcurrentHashMap

/**
 * Counts EOSE responses once per expected relay URL.
 * Unknown URLs and duplicate EOSE frames cannot satisfy the response threshold.
 */
internal class MarketplaceRelayEoseTracker(targetRelayUrls: Collection<String>) {
    private val targets = targetRelayUrls.filter { it.isNotBlank() }.toSet()
    private val completed = ConcurrentHashMap.newKeySet<String>()

    fun markCompleted(relayUrl: String) {
        if (relayUrl in targets) completed.add(relayUrl)
    }

    fun targetCount(): Int = targets.size

    fun completedCount(): Int = completed.size

    fun hasCompleted(relayUrl: String): Boolean = relayUrl in completed

    fun minimumResponseCount(): Int = minOf(6, targets.size)

    fun allTargetsCompleted(): Boolean = completed.containsAll(targets)
}

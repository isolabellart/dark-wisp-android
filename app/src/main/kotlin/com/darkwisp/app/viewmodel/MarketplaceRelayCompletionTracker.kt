package com.darkwisp.app.viewmodel

/**
 * Tracks Marketplace REQ completion by relay URL.
 *
 * EOSE can arrive before the request-sending loop records that relay as requested,
 * so completed URLs are retained and reconciled when the request is registered.
 */
internal class MarketplaceRelayCompletionTracker {
    private val requestedRelays = linkedSetOf<String>()
    private val completedRelays = linkedSetOf<String>()

    @Synchronized
    fun markRequested(relayUrl: String) {
        if (relayUrl.isNotBlank()) requestedRelays.add(relayUrl)
    }

    @Synchronized
    fun markCompleted(relayUrl: String) {
        if (relayUrl.isNotBlank()) completedRelays.add(relayUrl)
    }

    @Synchronized
    fun requestedCount(): Int = requestedRelays.size

    @Synchronized
    fun completedCount(): Int = requestedRelays.count { it in completedRelays }

    @Synchronized
    fun allRequestedCompleted(): Boolean = requestedRelays.all { it in completedRelays }
}

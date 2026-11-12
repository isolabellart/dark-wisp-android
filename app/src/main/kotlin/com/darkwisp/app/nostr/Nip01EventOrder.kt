package com.darkwisp.app.nostr

/**
 * Deterministic ordering for replaceable and addressable Nostr events.
 *
 * NIP-01 chooses the event with the lowest ID when two revisions share
 * the same created_at timestamp.
 */
object Nip01EventOrder {
    fun isNewer(candidate: NostrEvent, current: NostrEvent): Boolean {
        if (candidate.created_at != current.created_at) {
            return candidate.created_at > current.created_at
        }
        return candidate.id < current.id
    }

    /**
     * Produces a timestamp that is never older than [now] and is strictly
     * newer than [previousCreatedAt] when editing an existing addressable event.
     */
    fun nextCreatedAt(now: Long, previousCreatedAt: Long?): Long {
        val afterPrevious = previousCreatedAt?.let {
            if (it == Long.MAX_VALUE) it else it + 1L
        } ?: now
        return maxOf(now, afterPrevious)
    }
}

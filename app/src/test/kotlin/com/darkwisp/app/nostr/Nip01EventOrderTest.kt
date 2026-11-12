package com.darkwisp.app.nostr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Nip01EventOrderTest {
    private fun event(id: String, createdAt: Long) = NostrEvent(
        id = id,
        pubkey = "author",
        created_at = createdAt,
        kind = Nip99.KIND_LISTING,
        tags = emptyList(),
        content = "",
        sig = ""
    )

    @Test
    fun `later timestamp wins regardless of event ID`() {
        assertTrue(Nip01EventOrder.isNewer(event("f", 101), event("a", 100)))
        assertFalse(Nip01EventOrder.isNewer(event("a", 100), event("f", 101)))
    }

    @Test
    fun `lowest event ID wins when timestamps are equal`() {
        assertTrue(Nip01EventOrder.isNewer(event("a", 100), event("b", 100)))
        assertFalse(Nip01EventOrder.isNewer(event("b", 100), event("a", 100)))
    }

    @Test
    fun `same event is not newer than itself`() {
        assertFalse(Nip01EventOrder.isNewer(event("a", 100), event("a", 100)))
    }
}

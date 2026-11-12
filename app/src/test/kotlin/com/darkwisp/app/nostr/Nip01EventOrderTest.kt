package com.darkwisp.app.nostr

import org.junit.Assert.assertEquals
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

    @Test
    fun `new listing uses current timestamp when there is no previous revision`() {
        assertEquals(100L, Nip01EventOrder.nextCreatedAt(now = 100L, previousCreatedAt = null))
    }

    @Test
    fun `editing within the same second advances timestamp by one second`() {
        assertEquals(101L, Nip01EventOrder.nextCreatedAt(now = 100L, previousCreatedAt = 100L))
    }

    @Test
    fun `editing an older revision uses current timestamp`() {
        assertEquals(105L, Nip01EventOrder.nextCreatedAt(now = 105L, previousCreatedAt = 100L))
    }

    @Test
    fun `editing after a clock rollback still advances past the previous revision`() {
        assertEquals(201L, Nip01EventOrder.nextCreatedAt(now = 150L, previousCreatedAt = 200L))
    }

    @Test
    fun `newest candidate is independent of arrival order`() {
        val older = event("f", 100)
        val newer = event("a", 101)

        assertEquals(newer, Nip01EventOrder.newest(listOf(older, newer)))
        assertEquals(newer, Nip01EventOrder.newest(listOf(newer, older)))
    }

    @Test
    fun `newest candidate applies the NIP-01 tie break`() {
        val lowerId = event("a", 100)
        val higherId = event("b", 100)

        assertEquals(lowerId, Nip01EventOrder.newest(listOf(higherId, lowerId)))
    }

    @Test
    fun `newest candidate is null for an empty collection`() {
        assertEquals(null, Nip01EventOrder.newest(emptyList()))
    }
}

package com.darkwisp.app.repo

import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.NostrEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressableEventQueryPlanTest {
    private val author = "374ee93a33f3b6f10143260183d357de1184ab5d31c65dab67749ce736623347"
    private val dTag = "961fb06f-8ed9-439f-a00c-5dcc68f07a39"

    @Test
    fun `NIP99 address read uses exact coordinate first and author storefront fallback second`() {
        val stages = buildAddressableEventQueryStages(Nip99.KIND_LISTING, author, dTag)

        assertEquals(2, stages.size)
        assertEquals("exact-coordinate", stages[0].mode)
        assertEquals(listOf(Nip99.KIND_LISTING), stages[0].filter.kinds)
        assertEquals(listOf(author), stages[0].filter.authors)
        assertEquals(listOf(dTag), stages[0].filter.dTags)

        assertEquals("seller-storefront-fallback", stages[1].mode)
        assertEquals(listOf(Nip99.KIND_LISTING), stages[1].filter.kinds)
        assertEquals(listOf(author), stages[1].filter.authors)
        assertNull(stages[1].filter.dTags)
        assertEquals(600, stages[1].filter.limit)
    }

    @Test
    fun `non marketplace addressables do not trigger storefront fallback`() {
        val stages = buildAddressableEventQueryStages(30023, author, dTag)

        assertEquals(1, stages.size)
        assertEquals("exact-coordinate", stages.single().mode)
        assertEquals(listOf(dTag), stages.single().filter.dTags)
    }

    @Test
    fun `broad storefront results are matched by exact author kind and d tag locally`() {
        val wanted = NostrEvent(
            id = "wanted",
            pubkey = author,
            created_at = 1_800_000_000L,
            kind = Nip99.KIND_LISTING,
            tags = listOf(listOf("d", dTag)),
            content = "matching listing",
            sig = "signature"
        )
        val otherDTag = wanted.copy(
            id = "other",
            tags = listOf(listOf("d", "another-product"))
        )
        val otherAuthor = wanted.copy(
            id = "other-author",
            pubkey = "11".repeat(32)
        )
        val wrongKind = wanted.copy(
            id = "wrong-kind",
            kind = 30023
        )
        val duplicateDTag = wanted.copy(
            id = "duplicate-d",
            tags = listOf(listOf("d", dTag), listOf("d", "different-final-coordinate"))
        )

        assertTrue(matchesAddressableEventCoordinate(wanted, Nip99.KIND_LISTING, author, dTag))
        assertFalse(matchesAddressableEventCoordinate(otherDTag, Nip99.KIND_LISTING, author, dTag))
        assertFalse(matchesAddressableEventCoordinate(otherAuthor, Nip99.KIND_LISTING, author, dTag))
        assertFalse(matchesAddressableEventCoordinate(wrongKind, Nip99.KIND_LISTING, author, dTag))
        val duplicateDTag = wanted.copy(
            id = "duplicate-d",
            tags = listOf(listOf("d", dTag), listOf("d", "different-final-coordinate"))
        )
        assertFalse(matchesAddressableEventCoordinate(duplicateDTag, Nip99.KIND_LISTING, author, dTag))
        assertFalse(matchesAddressableEventCoordinate(duplicateDTag, Nip99.KIND_LISTING, author, dTag))
    }
}

package com.darkwisp.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Nip99Test {
    private fun event(tags: List<List<String>>) = NostrEvent(
        id = "event-id",
        pubkey = "11".repeat(32),
        created_at = 1_800_000_000L,
        kind = Nip99.KIND_LISTING,
        tags = tags,
        content = "A detailed description",
        sig = "signature"
    )

    @Test
    fun `reads standard listing fields and price`() {
        val listing = event(
            listOf(
                listOf("d", "painting-01"),
                listOf("title", "Original oil painting"),
                listOf("summary", "A one-of-a-kind artwork"),
                listOf("location", "Italy"),
                listOf("price", "450", "EUR"),
                listOf("status", "active"),
                listOf("image", "https://example.com/painting.jpg"),
                listOf("t", "art")
            )
        )

        assertEquals("painting-01", Nip99.dTag(listing))
        assertEquals("Original oil painting", Nip99.title(listing))
        assertEquals("A one-of-a-kind artwork", Nip99.summary(listing))
        assertEquals("Italy", Nip99.location(listing))
        assertEquals("450 EUR", Nip99.priceLabel(listing))
        assertEquals(listOf("https://example.com/painting.jpg"), Nip99.images(listing))
        assertEquals(listOf("art"), Nip99.categories(listing))
        assertFalse(Nip99.isSold(listing))
    }

    @Test
    fun `buildTags emits marketplace metadata and normalizes category tags`() {
        val tags = Nip99.buildTags(
            dTag = "painting-01",
            title = " Original oil painting ",
            summary = "Unique work",
            publishedAt = "1800000000",
            amount = "450",
            currency = "eur",
            location = "Italy",
            category = "Art, #handmade\npainting",
            images = listOf("https://example.com/one.jpg", "https://example.com/one.jpg"),
            status = "sold"
        )

        assertTrue(tags.contains(listOf("d", "painting-01")))
        assertTrue(tags.contains(listOf("title", "Original oil painting")))
        assertTrue(tags.contains(listOf("price", "450", "EUR")))
        assertTrue(tags.contains(listOf("status", "sold")))
        assertTrue(tags.contains(listOf("t", "art")))
        assertTrue(tags.contains(listOf("t", "handmade")))
        assertTrue(tags.contains(listOf("t", "painting")))
        assertEquals(1, tags.count { it.firstOrNull() == "image" })
    }

    @Test
    fun `stock distinguishes available sold out and unknown listings`() {
        val available = event(listOf(listOf("d", "available"), listOf("stock", "3")))
        val soldOut = event(listOf(listOf("d", "empty"), listOf("stock", "0")))
        val unknown = event(listOf(listOf("d", "unknown")))

        assertEquals(3, Nip99.stock(available))
        assertEquals(Nip99.AvailabilityState.AVAILABLE, Nip99.availability(available).state)
        assertEquals(Nip99.AvailabilityState.SOLD_OUT, Nip99.availability(soldOut).state)
        assertEquals(Nip99.AvailabilityState.UNKNOWN, Nip99.availability(unknown).state)
    }

    @Test
    fun `malformed duplicate negative and overflowing stock are unknown`() {
        val duplicate = event(listOf(listOf("d", "duplicate"), listOf("stock", "4"), listOf("stock", "2")))
        val malformed = event(listOf(listOf("d", "malformed"), listOf("stock", "many")))
        val extraField = event(listOf(listOf("d", "extra"), listOf("stock", "2", "unexpected")))
        val negative = event(listOf(listOf("d", "negative"), listOf("stock", "-1")))
        val overflow = event(listOf(listOf("d", "overflow"), listOf("stock", "2147483648")))

        listOf(duplicate, malformed, extraField, negative, overflow).forEach { listing ->
            assertNull(Nip99.stock(listing))
            assertEquals(Nip99.AvailabilityState.UNKNOWN, Nip99.availability(listing).state)
        }
    }

    @Test
    fun `explicit sold status takes precedence over positive stock`() {
        val listing = event(listOf(listOf("d", "sold"), listOf("status", "sold"), listOf("stock", "5")))
        assertEquals(Nip99.AvailabilityState.SOLD_OUT, Nip99.availability(listing).state)
    }

    @Test
    fun `buildTags publishes stock only when explicitly supplied`() {
        val withStock = Nip99.buildTags(
            dTag = "painting-stock", title = "Painting", summary = "", publishedAt = "1800000000",
            amount = "1", currency = "BTC", location = "", category = "", images = emptyList(),
            status = "active", stock = 2
        )
        val withoutStock = Nip99.buildTags(
            dTag = "painting-unknown", title = "Painting", summary = "", publishedAt = "1800000000",
            amount = "", currency = "", location = "", category = "", images = emptyList(), status = "active"
        )
        assertTrue(withStock.contains(listOf("stock", "2")))
        assertFalse(withoutStock.any { it.firstOrNull() == "stock" })
    }
    @Test
    fun `missing price is represented as null`() {
        assertNull(Nip99.price(event(listOf(listOf("d", "no-price"), listOf("title", "Free")))))
    }

    @Test
    fun `listing address tag uses the NIP-33 coordinate`() {
        val listing = event(listOf(listOf("d", "painting-01")))
        assertEquals("${Nip99.KIND_LISTING}:${listing.pubkey}:painting-01", Nip99.addressTag(listing))
    }

    @Test
    fun `listing reactions include its address coordinate`() {
        val listing = event(listOf(listOf("d", "painting-01")))
        assertTrue(Nip25.buildReactionTags(listing).contains(listOf("a", Nip99.addressTag(listing))))
    }

    @Test
    fun `naddr round trips listing coordinate`() {
        val author = "ab".repeat(32)
        val encoded = Nip19.naddrEncode(
            dTag = "painting-01",
            authorHex = author,
            kind = Nip99.KIND_LISTING,
            relays = listOf("wss://relay.example")
        )
        val decoded = Nip19.naddrDecode(encoded)

        assertEquals("painting-01", decoded.dTag)
        assertEquals(author, decoded.author)
        assertEquals(Nip99.KIND_LISTING, decoded.kind)
        assertEquals(listOf("wss://relay.example"), decoded.relays)
    }
}

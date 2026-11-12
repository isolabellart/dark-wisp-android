package com.darkwisp.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Nip51Test {
    @Test
    fun marketplaceWatchlistRoundTripsPublicListingCoordinates() {
        val dTag = "dark-wisp-marketplace-watchlist"
        val coordinates = setOf(
            "30402:" + "ab".repeat(32) + ":painting-01",
            "30402:" + "ef".repeat(32) + ":painting-02",
            "30402:" + "12".repeat(32) + ":painting-03"
        )
        val tags = Nip51.buildBookmarkSetTags(
            dTag = dTag,
            eventIds = emptySet(),
            coordinates = coordinates,
            title = "Marketplace Watchlist"
        )
        val event = NostrEvent(
            id = "bookmark-set-id",
            pubkey = "cd".repeat(32),
            created_at = 1_800_000_000L,
            kind = Nip51.KIND_BOOKMARK_SET,
            tags = tags,
            content = "",
            sig = "signature"
        )

        val parsed = Nip51.parseBookmarkSet(event)

        assertNotNull(parsed)
        assertEquals(dTag, parsed?.dTag)
        assertEquals(coordinates, parsed?.coordinates)
    }
}

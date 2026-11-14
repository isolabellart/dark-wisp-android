package com.darkwisp.app.repo

import com.darkwisp.app.nostr.Filter
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.NostrEvent

/**
 * Direct NIP-19 resolution has two stages:
 * 1. Exact address query using kind + author + #d.
 * 2. Seller storefront compatibility query using kind + author only, then exact
 *    coordinate matching in the client. Some relay/indexer implementations may
 *    miss or mishandle #d filters even though they serve the seller's listings.
 */
internal data class AddressableEventQueryStage(
    val mode: String,
    val filter: Filter
)

internal data class AddressableFetchOutcome(
    val matched: Boolean,
    val matchingEventCount: Int,
    val respondingRelays: Int,
    val targetRelayCount: Int,
    val timedOut: Boolean,
    val pagesFetched: Int = 0
)

internal const val NIP99_STOREFRONT_PAGE_SIZE = 250
internal const val NIP99_STOREFRONT_MAX_PAGES = 8

internal fun buildAddressableStorefrontPageFilter(author: String, until: Long?): Filter =
    Filter(
        kinds = listOf(Nip99.KIND_LISTING),
        authors = listOf(author),
        until = until,
        limit = NIP99_STOREFRONT_PAGE_SIZE
    )

/**
 * NIP-01 pagination cursor for a newest-first relay page.
 * A cursor is returned only when the relay filled the requested page.
 */
internal fun nextAddressableStorefrontPageUntil(
    receivedCreatedAt: List<Long>,
    requestedLimit: Int = NIP99_STOREFRONT_PAGE_SIZE
): Long? {
    if (receivedCreatedAt.size < requestedLimit) return null
    val oldest = receivedCreatedAt.minOrNull() ?: return null
    return (oldest - 1).takeIf { it >= 0 }
}

internal fun buildAddressableEventQueryStages(
    kind: Int,
    author: String,
    dTag: String
): List<AddressableEventQueryStage> {
    val exact = AddressableEventQueryStage(
        mode = "exact-coordinate",
        filter = Filter(
            kinds = listOf(kind),
            authors = listOf(author),
            dTags = listOf(dTag),
            limit = if (kind == Nip99.KIND_LISTING) 10 else 1
        )
    )
    if (kind != Nip99.KIND_LISTING) return listOf(exact)

    return listOf(
        exact,
        AddressableEventQueryStage(
            mode = "seller-storefront-fallback",
            filter = Filter(
                kinds = listOf(Nip99.KIND_LISTING),
                authors = listOf(author),
                limit = NIP99_STOREFRONT_PAGE_SIZE
            )
        )
    )
}

internal fun matchesAddressableEventCoordinate(
    event: NostrEvent,
    kind: Int,
    author: String,
    dTag: String
): Boolean =
    event.kind == kind &&
        event.pubkey == author &&
        Nip99.dTag(event) == dTag

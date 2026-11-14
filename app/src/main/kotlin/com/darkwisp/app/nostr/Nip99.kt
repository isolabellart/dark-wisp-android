package com.darkwisp.app.nostr

/**
 * NIP-99 classified listing helpers.
 *
 * Listings are addressable events (kind 30402). Updating a listing means publishing
 * a newer event with the same author and d-tag; readers should resolve the newest
 * version of that address.
 */
object Nip99 {
    const val KIND_LISTING = 30402
    const val KIND_DRAFT = 30403

    data class Price(val amount: String, val currency: String, val frequency: String? = null)

    enum class AvailabilityState { AVAILABLE, SOLD_OUT, UNKNOWN }

    /** Missing, malformed or ambiguous stock is unknown, never zero. */
    data class Availability(val state: AvailabilityState, val stock: Int? = null)

    fun tag(event: NostrEvent, name: String): List<String>? =
        event.tags.firstOrNull { it.size >= 2 && it[0] == name }

    fun tags(event: NostrEvent, name: String): List<List<String>> =
        event.tags.filter { it.size >= 2 && it[0] == name }

    fun dTag(event: NostrEvent): String = tag(event, "d")?.getOrNull(1).orEmpty()

    fun addressTag(event: NostrEvent): String = "${event.kind}:${event.pubkey}:${dTag(event)}"

    fun title(event: NostrEvent): String = tag(event, "title")?.getOrNull(1).orEmpty()

    /**
     * Render a usable heading for interoperable NIP-99 listings.
     * The title tag is recommended by NIP-99 but may be absent on external events;
     * in that case use the summary, then the first non-empty description line.
     * This is presentation-only and never changes the signed event.
     */
    fun displayTitle(event: NostrEvent, fallback: String): String {
        title(event).trim().takeIf(String::isNotBlank)?.let { return it }
        summary(event).trim().takeIf(String::isNotBlank)?.let { return it }
        val firstDescriptionLine = event.content.lineSequence()
            .map { line ->
                line.trim()
                    .removePrefix(">").trim()
                    .removePrefix("#").trim()
                    .removePrefix("-").trim()
                    .removePrefix("*").trim()
            }
            .firstOrNull(String::isNotBlank)
            ?.take(120)
        return firstDescriptionLine ?: fallback
    }

    fun summary(event: NostrEvent): String = tag(event, "summary")?.getOrNull(1).orEmpty()
    fun location(event: NostrEvent): String = tag(event, "location")?.getOrNull(1).orEmpty()
    fun status(event: NostrEvent): String = tag(event, "status")?.getOrNull(1)?.lowercase() ?: "active"
    fun isSold(event: NostrEvent): Boolean = status(event) == "sold"

    fun stock(event: NostrEvent): Int? {
        val stockTags = event.tags.filter { it.firstOrNull() == "stock" }
        if (stockTags.size != 1) return null
        val tag = stockTags.single()
        if (tag.size != 2) return null
        return tag[1].trim().toLongOrNull()
            ?.takeIf { it in 0..Int.MAX_VALUE.toLong() }
            ?.toInt()
    }

    fun availability(event: NostrEvent): Availability =
        availability(status = status(event), stock = stock(event))

    fun availability(status: String, stock: Int?): Availability = when {
        status.equals("sold", ignoreCase = true) -> Availability(AvailabilityState.SOLD_OUT, stock)
        stock == null || stock < 0 -> Availability(AvailabilityState.UNKNOWN)
        stock == 0 -> Availability(AvailabilityState.SOLD_OUT, 0)
        else -> Availability(AvailabilityState.AVAILABLE, stock)
    }
    fun publishedAt(event: NostrEvent): String =
        tag(event, "published_at")?.getOrNull(1) ?: event.created_at.toString()

    fun images(event: NostrEvent): List<String> =
        tags(event, "image").mapNotNull { it.getOrNull(1)?.takeIf(String::isNotBlank) }.distinct()

    fun categories(event: NostrEvent): List<String> =
        tags(event, "t").mapNotNull { it.getOrNull(1)?.takeIf(String::isNotBlank) }.distinct()

    fun price(event: NostrEvent): Price? {
        val tag = tag(event, "price") ?: return null
        val amount = tag.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        val currency = tag.getOrNull(2)?.takeIf(String::isNotBlank) ?: return null
        return Price(amount, currency, tag.getOrNull(3)?.takeIf(String::isNotBlank))
    }

    fun priceLabel(event: NostrEvent): String? {
        val value = price(event) ?: return null
        val frequency = value.frequency?.let { "/$it" }.orEmpty()
        return value.amount + " " + value.currency + frequency
    }

    fun buildTags(
        dTag: String,
        title: String,
        summary: String,
        publishedAt: String,
        amount: String,
        currency: String,
        location: String,
        category: String,
        images: List<String>,
        status: String,
        stock: Int? = null
    ): List<List<String>> {
        require(stock == null || stock >= 0) { "Stock cannot be negative" }
        val result = mutableListOf(
            listOf("d", dTag.trim()),
            listOf("title", title.trim()),
            listOf("summary", summary.trim()),
            listOf("published_at", publishedAt),
            listOf("status", if (status.equals("sold", true)) "sold" else "active")
        )
        if (stock != null) result.add(listOf("stock", stock.toString()))
        if (location.isNotBlank()) result.add(listOf("location", location.trim()))
        if (amount.isNotBlank() && currency.isNotBlank()) {
            result.add(listOf("price", amount.trim(), currency.trim().uppercase()))
        }
        category.split(',', '#', '\n')
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { result.add(listOf("t", it)) }
        images.map(String::trim).filter(String::isNotBlank).distinct()
            .forEach { result.add(listOf("image", it)) }
        result.add(listOf("client", "Postilla"))
        return result
    }
}

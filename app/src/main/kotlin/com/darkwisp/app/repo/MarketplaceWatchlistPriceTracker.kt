package com.darkwisp.app.repo

import android.content.Context
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.NostrEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Keeps a per-account price baseline for public Marketplace watchlist entries.
 * A price change remains visible until the user opens/acknowledges that listing.
 */
class MarketplaceWatchlistPriceTracker(
    context: Context,
    pubkey: String?
) {
    data class PriceChange(
        val previousPrice: String,
        val currentPrice: String
    )

    private val prefs = context.applicationContext.getSharedPreferences(
        "marketplace_watchlist_prices_${pubkey ?: "anon"}",
        Context.MODE_PRIVATE
    )

    private val lock = Any()
    private val _priceChanges = MutableStateFlow(loadChanges())
    val priceChanges: StateFlow<Map<String, PriceChange>> = _priceChanges

    fun inspect(event: NostrEvent) = synchronized(lock) {
        if (event.kind != Nip99.KIND_LISTING) return@synchronized
        val address = Nip99.addressTag(event)
        val currentPrice = Nip99.priceLabel(event) ?: return@synchronized
        val prices = readJson("prices")
        val previousPrice = prices.optString(address, "")
        if (previousPrice.isBlank()) {
            prices.put(address, currentPrice)
            prefs.edit().putString("prices", prices.toString()).apply()
            return@synchronized
        }
        if (previousPrice == currentPrice) return@synchronized

        val changes = readJson("changes")
        val alreadyChanged = changes.optJSONObject(address)
        val originalPrice = alreadyChanged?.optString("previousPrice")
            ?.takeIf { !it.isNullOrBlank() } ?: previousPrice
        changes.put(
            address,
            JSONObject()
                .put("previousPrice", originalPrice)
                .put("currentPrice", currentPrice)
        )
        prices.put(address, currentPrice)
        prefs.edit()
            .putString("prices", prices.toString())
            .putString("changes", changes.toString())
            .apply()
        _priceChanges.value = parseChanges(changes)
    }

    fun acknowledge(address: String) = synchronized(lock) {
        val changes = readJson("changes")
        if (changes.has(address)) {
            changes.remove(address)
            prefs.edit().putString("changes", changes.toString()).apply()
            _priceChanges.value = parseChanges(changes)
        }
    }

    fun acknowledgeAll() = synchronized(lock) {
        prefs.edit().putString("changes", "{}").apply()
        _priceChanges.value = emptyMap()
    }

    private fun loadChanges(): Map<String, PriceChange> =
        parseChanges(readJson("changes"))

    private fun readJson(key: String): JSONObject =
        try {
            JSONObject(prefs.getString(key, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }

    private fun parseChanges(json: JSONObject): Map<String, PriceChange> {
        val result = linkedMapOf<String, PriceChange>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val address = keys.next()
            val entry = json.optJSONObject(address) ?: continue
            val previous = entry.optString("previousPrice", "")
            val current = entry.optString("currentPrice", "")
            if (previous.isNotBlank() && current.isNotBlank()) {
                result[address] = PriceChange(previous, current)
            }
        }
        return result
    }
}

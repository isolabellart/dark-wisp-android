package com.darkwisp.app.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

/**
 * Converts a fiat listing price to an indicative sats equivalent using the current
 * public BTC exchange-rate table. This is a reference quote only, not a payment quote:
 * the seller's Lightning invoice determines the final amount.
 */
object MarketplacePriceConverter {
    private const val RATES_URL = "https://api.coinbase.com/v2/exchange-rates?currency=BTC"
    private const val CACHE_DURATION_MS = 60_000L
    private val satsPerBitcoin = BigDecimal("100000000")
    private val cacheMutex = Mutex()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedRates: Map<String, BigDecimal> = emptyMap()
    @Volatile private var cacheExpiresAt = 0L

    suspend fun fiatToSats(amount: String, currency: String): Long? {
        val fiatAmount = amount.trim().replace(',', '.').toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO } ?: return null
        val code = currency.trim().uppercase()
        if (code.isBlank() || code == "BTC" || code == "SATS") return null

        val rate = loadRates()[code] ?: return null
        return convertFiatToSats(fiatAmount, rate)
    }

    private suspend fun loadRates(): Map<String, BigDecimal> = cacheMutex.withLock {
        val now = System.currentTimeMillis()
        if (now < cacheExpiresAt && cachedRates.isNotEmpty()) return@withLock cachedRates

        val freshRates = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(RATES_URL)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val ratesJson = JSONObject(body)
                        .getJSONObject("data")
                        .getJSONObject("rates")
                    val parsed = mutableMapOf<String, BigDecimal>()
                    val keys = ratesJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next().uppercase()
                        ratesJson.optString(key).toBigDecimalOrNull()?.let { value ->
                            if (value > BigDecimal.ZERO) parsed[key] = value
                        }
                    }
                    parsed.takeIf { it.isNotEmpty() }
                }
            }.getOrNull()
        }

        if (freshRates != null) {
            cachedRates = freshRates
            cacheExpiresAt = System.currentTimeMillis() + CACHE_DURATION_MS
        }
        freshRates ?: cachedRates
    }

    internal fun convertFiatToSats(amount: BigDecimal, fiatPerBtc: BigDecimal): Long? {
        if (amount <= BigDecimal.ZERO || fiatPerBtc <= BigDecimal.ZERO) return null
        return runCatching {
            amount
                .divide(fiatPerBtc, 18, RoundingMode.HALF_UP)
                .multiply(satsPerBitcoin)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()
    }
}

package com.darkwisp.app.nostr

/** The short fiat list offered by the marketplace editor, plus Lightning-native units. */
object MarketplaceCurrencies {
    val FIAT = listOf(
        "EUR", "USD", "GBP", "CHF", "CAD", "AUD", "JPY", "CNY", "NZD", "SEK", "NOK"
    )
    val ALL = FIAT + listOf("BTC", "SATS")
}

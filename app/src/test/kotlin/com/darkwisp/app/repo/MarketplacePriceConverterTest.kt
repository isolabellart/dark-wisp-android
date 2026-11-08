package com.darkwisp.app.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MarketplacePriceConverterTest {
    @Test
    fun `fiat amount converts to sats using fiat per bitcoin rate`() {
        val sats = MarketplacePriceConverter.convertFiatToSats(
            amount = BigDecimal("80"),
            fiatPerBtc = BigDecimal("80000")
        )
        assertEquals(100_000L, sats)
    }

    @Test
    fun `fractional sat result rounds to nearest whole sat`() {
        val sats = MarketplacePriceConverter.convertFiatToSats(
            amount = BigDecimal("0.01"),
            fiatPerBtc = BigDecimal("80000")
        )
        assertEquals(13L, sats)
    }

    @Test
    fun `invalid amounts and exchange rates are rejected`() {
        assertNull(MarketplacePriceConverter.convertFiatToSats(BigDecimal.ZERO, BigDecimal("80000")))
        assertNull(MarketplacePriceConverter.convertFiatToSats(BigDecimal("10"), BigDecimal.ZERO))
    }
}

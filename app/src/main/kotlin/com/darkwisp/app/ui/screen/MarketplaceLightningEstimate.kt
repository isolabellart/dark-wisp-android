package com.darkwisp.app.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.darkwisp.app.R
import com.darkwisp.app.repo.MarketplacePriceConverter
import com.darkwisp.app.nostr.MarketplaceCurrencies
import java.text.NumberFormat

/**
 * Shows the current indicative sats equivalent for a fiat listing price.
 * BTC and SATS prices are already denominated in the payment currency.
 */
@Composable
fun MarketplaceLightningEstimate(amount: String, currency: String) {
    if (currency.uppercase() !in MarketplaceCurrencies.FIAT) return

    if (amount.trim().replace(',', '.').toBigDecimalOrNull()?.signum()?.let { it > 0 } != true) {
        return
    }

    var sats by remember(amount, currency) { mutableStateOf<Long?>(null) }
    var completed by remember(amount, currency) { mutableStateOf(false) }

    LaunchedEffect(amount, currency) {
        completed = false
        sats = MarketplacePriceConverter.fiatToSats(amount, currency)
        completed = true
    }

    val label = when {
        !completed -> stringResource(R.string.marketplace_sats_loading)
        sats != null -> stringResource(
            R.string.marketplace_sats_estimate,
            NumberFormat.getIntegerInstance().format(sats)
        )
        else -> stringResource(R.string.marketplace_sats_unavailable)
    }

    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

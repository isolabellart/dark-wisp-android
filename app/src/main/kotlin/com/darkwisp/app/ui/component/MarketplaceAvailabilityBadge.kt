package com.darkwisp.app.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.darkwisp.app.R
import com.darkwisp.app.nostr.Nip99

@Composable
fun MarketplaceAvailabilityBadge(
    availability: Nip99.Availability,
    modifier: Modifier = Modifier
) {
    val (label, containerColor, contentColor) = when (availability.state) {
        Nip99.AvailabilityState.AVAILABLE -> Triple(
            stringResource(
                R.string.marketplace_availability_available_count,
                availability.stock ?: 0
            ),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        Nip99.AvailabilityState.SOLD_OUT -> Triple(
            stringResource(R.string.marketplace_availability_sold_out),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        Nip99.AvailabilityState.UNKNOWN -> Triple(
            stringResource(R.string.marketplace_availability_unknown),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

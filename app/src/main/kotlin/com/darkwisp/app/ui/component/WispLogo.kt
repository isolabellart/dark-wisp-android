package com.darkwisp.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compatibility wrapper for screens that still call WispLogo.
 * The visible identity is now the single Postilla mark and wordmark.
 */
@Composable
fun WispLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showText: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        PostillaBrandMark(size = size)
        if (showText) {
            Spacer(Modifier.height(12.dp))
            PostillaWordmark(fontSize = 36.sp)
        }
    }
}

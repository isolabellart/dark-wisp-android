package com.darkwisp.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkwisp.app.R

@Composable
fun PostillaBrandMark(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.postilla_mark),
        contentDescription = "Postilla",
        modifier = modifier.size(size)
    )
}

/**
 * Uses the exact wordmark shapes extracted from the approved Postilla presentation board.
 * Avoid Text-based reconstruction: platform font metrics change its spacing and silhouette.
 */
@Composable
fun PostillaWordmark(fontSize: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val height = with(density) { fontSize.toDp() }
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wordmark = if (isDarkSurface) {
        R.drawable.postilla_wordmark_light
    } else {
        R.drawable.postilla_wordmark_dark
    }

    Image(
        painter = painterResource(wordmark),
        contentDescription = "Postilla",
        modifier = modifier
            .width(height * (530f / 122f))
            .height(height)
    )
}

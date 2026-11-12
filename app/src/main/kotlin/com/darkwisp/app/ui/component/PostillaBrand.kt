package com.darkwisp.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkwisp.app.R
import com.darkwisp.app.ui.theme.PostillaPalette

@Composable
fun PostillaBrandMark(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.postilla_mark),
        contentDescription = "Postilla",
        modifier = modifier.size(size)
    )
}

@Composable
fun PostillaWordmark(fontSize: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fontSizeDp = with(density) { fontSize.toDp() }
    val lineHeightDp = fontSizeDp * 1.2f
    val textStyle = MaterialTheme.typography.titleLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = fontSize,
        lineHeight = fontSize * 1.2f,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.45).sp
    )
    val ink = MaterialTheme.colorScheme.onBackground

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text("Post", style = textStyle, color = ink)
        Box(
            modifier = Modifier
                .width(fontSizeDp * 0.32f)
                .height(lineHeightDp)
        ) {
            Text(
                text = "ı",
                modifier = Modifier.align(Alignment.BottomCenter),
                style = textStyle,
                color = ink
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = fontSizeDp * 0.13f)
                    .size(fontSizeDp * 0.14f)
                    .clip(CircleShape)
                    .background(PostillaPalette.Orange)
            )
        }
        Text("lla", style = textStyle, color = ink)
    }
}

package com.darkwisp.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

@Composable
fun PostillaWordmark(fontSize: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    val wordmark = buildAnnotatedString {
        append("post")
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
            append("i")
        }
        append("lla")
    }
    Text(
        text = wordmark,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = fontSize,
            lineHeight = fontSize * 1.2f,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.45).sp
        ),
        color = MaterialTheme.colorScheme.onBackground
    )
}

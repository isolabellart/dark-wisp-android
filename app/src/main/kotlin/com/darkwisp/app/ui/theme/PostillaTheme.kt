package com.darkwisp.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Postilla has exactly two appearances. The same small palette is used throughout
 * the application; selection, focus and important actions use orange, never a new hue.
 */
object PostillaPalette {
    val Ebony = Color(0xFF1B1916)
    val EbonyRaised = Color(0xFF24211E)
    val EbonyInset = Color(0xFF302C27)
    val EbonyBorder = Color(0xFF454039)
    val EbonyMuted = Color(0xFFB8AEA1)

    val Ivory = Color(0xFFF4EBDD)
    val IvoryRaised = Color(0xFFFFFAF1)
    val IvoryInset = Color(0xFFEAE1D3)
    val IvoryBorder = Color(0xFFD6CCBD)
    val IvoryMuted = Color(0xFF6C645A)

    val Orange = Color(0xFFF08A32)
    val OrangeDark = Color(0xFFA65318)
    val OrangeSoft = Color(0xFFFFD8B4)
}

private fun postillaTypography(isLargeText: Boolean): Typography {
    val base = Typography()
    val bodySize = if (isLargeText) 17.sp else 15.sp
    val bodyLine = if (isLargeText) 25.sp else 23.sp
    val smallSize = if (isLargeText) 14.sp else 12.sp

    fun style(
        size: androidx.compose.ui.unit.TextUnit,
        weight: FontWeight,
        lineHeight: androidx.compose.ui.unit.TextUnit
    ) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = size,
        fontWeight = weight,
        lineHeight = lineHeight
    )

    return base.copy(
        displayLarge = style(36.sp, FontWeight.Normal, 44.sp),
        displayMedium = style(32.sp, FontWeight.Normal, 40.sp),
        displaySmall = style(28.sp, FontWeight.Normal, 36.sp),
        headlineLarge = style(26.sp, FontWeight.Medium, 34.sp),
        headlineMedium = style(23.sp, FontWeight.Medium, 30.sp),
        headlineSmall = style(21.sp, FontWeight.Medium, 28.sp),
        titleLarge = style(21.sp, FontWeight.Medium, 28.sp),
        titleMedium = style(16.sp, FontWeight.SemiBold, 23.sp),
        titleSmall = style(14.sp, FontWeight.Medium, 20.sp),
        bodyLarge = style(bodySize, FontWeight.Normal, bodyLine),
        bodyMedium = style(if (isLargeText) 16.sp else 14.sp, FontWeight.Normal, 21.sp),
        bodySmall = style(smallSize, FontWeight.Normal, 18.sp),
        labelLarge = style(15.sp, FontWeight.SemiBold, 21.sp),
        labelMedium = style(12.sp, FontWeight.Medium, 16.sp),
        labelSmall = style(if (isLargeText) 12.sp else 11.sp, FontWeight.Medium, 16.sp)
    )
}

@Composable
fun PostillaTheme(
    isDarkTheme: Boolean,
    isLargeText: Boolean = false,
    content: @Composable () -> Unit
) {
    val p = PostillaPalette
    val scheme = if (isDarkTheme) {
        darkColorScheme(
            primary = p.Orange,
            onPrimary = p.Ebony,
            primaryContainer = p.EbonyInset,
            onPrimaryContainer = p.OrangeSoft,
            secondary = p.Orange,
            onSecondary = p.Ebony,
            secondaryContainer = p.EbonyInset,
            onSecondaryContainer = p.Ivory,
            background = p.Ebony,
            onBackground = p.Ivory,
            surface = p.EbonyRaised,
            onSurface = p.Ivory,
            surfaceVariant = p.EbonyInset,
            onSurfaceVariant = p.EbonyMuted,
            outline = p.EbonyBorder,
            outlineVariant = p.EbonyBorder,
            error = p.Orange,
            onError = p.Ebony,
            surfaceTint = Color.Transparent
        )
    } else {
        lightColorScheme(
            primary = p.OrangeDark,
            onPrimary = p.IvoryRaised,
            primaryContainer = p.OrangeSoft,
            onPrimaryContainer = p.Ebony,
            secondary = p.OrangeDark,
            onSecondary = p.IvoryRaised,
            secondaryContainer = p.IvoryInset,
            onSecondaryContainer = p.Ebony,
            background = p.Ivory,
            onBackground = p.Ebony,
            surface = p.IvoryRaised,
            onSurface = p.Ebony,
            surfaceVariant = p.IvoryInset,
            onSurfaceVariant = p.IvoryMuted,
            outline = p.IvoryBorder,
            outlineVariant = p.IvoryBorder,
            error = p.OrangeDark,
            onError = p.IvoryRaised,
            surfaceTint = Color.Transparent
        )
    }

    val muted = if (isDarkTheme) p.EbonyMuted else p.IvoryMuted
    val background = if (isDarkTheme) p.Ebony else p.Ivory
    val accent = if (isDarkTheme) p.Orange else p.OrangeDark

    MaterialTheme(
        colorScheme = scheme,
        typography = postillaTypography(isLargeText),
        content = {
            CompositionLocalProvider(
                LocalWispColors provides WispColors(
                    backgroundColor = background,
                    zapColor = accent,
                    repostColor = muted,
                    bookmarkColor = accent,
                    paidColor = accent
                ),
                content = content
            )
        }
    )
}

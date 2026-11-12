package com.musely.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The single "hero" action colour (play, shuffle, primary CTA).
 * Lime glows on dark surfaces; on light surfaces it lacks contrast, so violet takes over.
 */
@Immutable
data class Spotlight(val container: Color, val content: Color)

val LocalSpotlight = staticCompositionLocalOf { Spotlight(MuselyLime, Color(0xFF23280A)) }

private val base = Typography()

private val MuselyTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.75).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val MuselyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MuselyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val spotlight = if (dark) {
        Spotlight(container = MuselyLime, content = Color(0xFF23280A))
    } else {
        Spotlight(container = MuselyViolet, content = Color.White)
    }
    CompositionLocalProvider(LocalSpotlight provides spotlight) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = MuselyTypography,
            shapes = MuselyShapes,
            content = content,
        )
    }
}

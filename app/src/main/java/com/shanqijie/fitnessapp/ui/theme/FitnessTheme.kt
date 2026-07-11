package com.shanqijie.fitnessapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FitnessColors {
    val Phone = Color(0xFFF4F4EF)
    val Surface = Color(0xFFF8F8F4)
    val SurfaceStrong = Color(0xFFFFFFFF)
    val Ink = Color(0xFF10110F)
    val Muted = Color(0xFF71746E)
    /** Legacy token name retained to keep call sites surgical; this is the single neon accent. */
    val Orange = Color(0xFFEFFF31)
    val Green = Orange
    val Hero = Color(0xFF121310)
    val OnOrange = Ink
    val OnHero = Color.White
}

object FitnessDimensions {
    val MinimumTouchTarget = 48.dp
    val LargeRadius = 34.dp
    val ContainerRadius = 28.dp
    val ControlRadius = 22.dp
}

private val FitnessColorScheme = lightColorScheme(
    primary = FitnessColors.Orange,
    onPrimary = FitnessColors.OnOrange,
    secondary = FitnessColors.Green,
    onSecondary = FitnessColors.Ink,
    background = FitnessColors.Phone,
    onBackground = FitnessColors.Ink,
    surface = FitnessColors.Surface,
    onSurface = FitnessColors.Ink,
    surfaceVariant = FitnessColors.Phone,
    onSurfaceVariant = FitnessColors.Muted,
)

private val FitnessShapes = Shapes(
    extraLarge = RoundedCornerShape(FitnessDimensions.LargeRadius),
    medium = RoundedCornerShape(FitnessDimensions.ContainerRadius),
    small = RoundedCornerShape(FitnessDimensions.ControlRadius),
)

private val FitnessTypography = Typography(
    headlineLarge = TextStyle(
        color = FitnessColors.Ink,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
    headlineSmall = TextStyle(
        color = FitnessColors.Ink,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
    ),
    bodyLarge = TextStyle(
        color = FitnessColors.Ink,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        color = FitnessColors.Muted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
    ),
)

@Composable
fun FitnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FitnessColorScheme,
        typography = FitnessTypography,
        shapes = FitnessShapes,
        content = content,
    )
}

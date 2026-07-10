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
    val Phone = Color(0xFFF5F2EC)
    val Surface = Color(0xFFFFFDFA)
    val Ink = Color(0xFF11151B)
    val Muted = Color(0xFF5F6874)
    val Orange = Color(0xFFFF7426)
    val Green = Color(0xFF24C869)
    val Hero = Color(0xFF151B24)
    val OnOrange = Color.White
    val OnHero = Color.White
}

object FitnessDimensions {
    val MinimumTouchTarget = 48.dp
    val LargeRadius = 24.dp
    val ContainerRadius = 16.dp
    val ControlRadius = 14.dp
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
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = TextStyle(
        color = FitnessColors.Ink,
        fontSize = 22.sp,
        lineHeight = 28.sp,
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

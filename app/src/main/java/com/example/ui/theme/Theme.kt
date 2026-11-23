package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val BansifyDarkColorScheme = darkColorScheme(
    primary = BansifyBlueDark,
    onPrimary = BansifyOnBlueDark,
    primaryContainer = BansifyBlueContainerDark,
    onPrimaryContainer = BansifyBlueContainer,
    secondary = BansifySecondaryDark,
    onSecondary = Color(0xFF1F2F42),
    secondaryContainer = BansifySecondaryContainerDark,
    onSecondaryContainer = BansifySecondaryContainer,
    tertiary = BansifyTertiaryDark,
    onTertiary = Color(0xFF073818),
    tertiaryContainer = BansifyTertiaryContainerDark,
    onTertiaryContainer = BansifyTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF2E3340),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorDark,
    errorContainer = ErrorContainerDark
)

private val BansifyLightColorScheme = lightColorScheme(
    primary = BansifyBlue,
    onPrimary = Color.White,
    primaryContainer = BansifyBlueContainer,
    onPrimaryContainer = BansifyOnBlueContainer,
    secondary = BansifySecondary,
    onSecondary = Color.White,
    secondaryContainer = BansifySecondaryContainer,
    onSecondaryContainer = Color(0xFF1B2B40),
    tertiary = BansifyTertiary,
    onTertiary = Color.White,
    tertiaryContainer = BansifyTertiaryContainer,
    onTertiaryContainer = Color(0xFF072711),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE7EDF7),
    surfaceContainerHighest = Color(0xFFDFE6F2),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorLight,
    errorContainer = ErrorContainerLight
)

val BansifyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BansifyFilesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BansifyDarkColorScheme
        else -> BansifyLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BansifyTypography,
        shapes = BansifyShapes,
        content = content
    )
}

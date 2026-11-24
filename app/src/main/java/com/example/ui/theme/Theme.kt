package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = OnTerracotta,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = BrassSecondary,
    onSecondary = OnBrass,
    secondaryContainer = BrassContainer,
    onSecondaryContainer = OnBrassContainer,
    tertiary = SageTertiary,
    onTertiary = OnSage,
    tertiaryContainer = SageContainer,
    onTertiaryContainer = OnSageContainer,
    background = WarmIvoryBackground,
    onBackground = EspressoOnBackground,
    surface = WarmCreamSurface,
    onSurface = EspressoOnSurface,
    surfaceVariant = WarmLinenSurfaceVariant,
    onSurfaceVariant = WalnutOnSurfaceVariant,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainerRed,
    onErrorContainer = OnErrorContainerRed,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = TerracottaPrimaryDark,
    onPrimary = OnTerracottaDark,
    primaryContainer = TerracottaContainerDark,
    onPrimaryContainer = OnTerracottaContainerDark,
    secondary = BrassSecondaryDark,
    onSecondary = OnBrassDark,
    secondaryContainer = BrassContainerDark,
    onSecondaryContainer = OnBrassContainerDark,
    tertiary = SageTertiaryDark,
    onTertiary = OnSageDark,
    tertiaryContainer = SageContainerDark,
    onTertiaryContainer = OnSageContainerDark,
    background = EspressoBackgroundDark,
    onBackground = IvoryOnBackgroundDark,
    surface = EspressoSurfaceDark,
    onSurface = IvoryOnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,
  )

val GharBazarShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun GharBazarTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = GharBazarTypography,
    shapes = GharBazarShapes,
    content = content,
  )
}

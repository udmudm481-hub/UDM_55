package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun UDMRepairTheme(
    themeName: String = "UDM_CLASSIC",
    customThemeJson: String = "",
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val themeConfig = AppThemeConfig.getTheme(themeName, customThemeJson)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = themeConfig.primaryAccent,
            onPrimary = themeConfig.onPrimaryAccent,
            primaryContainer = themeConfig.cardBackgroundElevated,
            onPrimaryContainer = themeConfig.primaryAccent,
            secondary = themeConfig.secondaryAccent,
            onSecondary = Color.White,
            secondaryContainer = themeConfig.cardBackgroundElevated,
            onSecondaryContainer = themeConfig.textPrimary,
            tertiary = themeConfig.tertiaryAccent,
            onTertiary = Color.White,
            background = themeConfig.background,
            onBackground = themeConfig.textPrimary,
            surface = themeConfig.surface,
            onSurface = themeConfig.textPrimary,
            surfaceVariant = themeConfig.cardBackground,
            onSurfaceVariant = themeConfig.textSecondary,
            outline = themeConfig.border,
            outlineVariant = themeConfig.borderSubtle,
            error = themeConfig.statusCancelled,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = LightNavyPrimary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0F2FE),
            onPrimaryContainer = Color(0xFF0369A1),
            secondary = Color(0xFF0284C7),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF1F5F9),
            onSecondaryContainer = Color(0xFF0F172A),
            tertiary = Color(0xFF059669),
            onTertiary = Color.White,
            background = LightNavyBackground,
            onBackground = Color(0xFF0F172A),
            surface = LightNavySurface,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = LightNavyCard,
            onSurfaceVariant = Color(0xFF475569),
            outline = LightNavyBorder,
            outlineVariant = Color(0xFFE2E8F0),
            error = Color(0xFFDC2626),
            onError = Color.White
        )
    }

    val shapes = androidx.compose.material3.Shapes(
        small = androidx.compose.foundation.shape.RoundedCornerShape(themeConfig.buttonCornerRadiusDp.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(themeConfig.cardCornerRadiusDp.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape((themeConfig.cardCornerRadiusDp + 4).dp)
    )

    CompositionLocalProvider(
        LocalAppTheme provides themeConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = shapes,
            content = content
        )
    }
}

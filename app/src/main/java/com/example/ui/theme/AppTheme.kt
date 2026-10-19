package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

enum class AppThemeMode(val id: String, val displayName: String, val subtitle: String) {
    UDM_CLASSIC(
        id = "UDM_CLASSIC",
        displayName = "UDM Classic",
        subtitle = "Original dark navy, blue & electric cyan design"
    ),
    MIDNIGHT_LUXURY(
        id = "MIDNIGHT_LUXURY",
        displayName = "Midnight Luxury",
        subtitle = "Deep obsidian navy, electric blue & cyan accents"
    ),
    ROYAL_VIOLET(
        id = "ROYAL_VIOLET",
        displayName = "Royal Violet",
        subtitle = "Refined charcoal, deep amethyst & luminous lavender"
    ),
    CUSTOM(
        id = "CUSTOM",
        displayName = "Custom Theme",
        subtitle = "Personalized color scheme, borders & button styles"
    );

    companion object {
        fun fromId(id: String): AppThemeMode {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: UDM_CLASSIC
        }
    }
}

/**
 * Reusable theme configuration model serving as the foundation for built-in themes
 * and extensible for the Custom Theme Editor.
 */
data class AppThemeConfig(
    val themeMode: AppThemeMode = AppThemeMode.UDM_CLASSIC,
    val name: String = themeMode.displayName,

    // Surfaces & Backgrounds
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBackground: Color,
    val cardBackgroundElevated: Color,
    val border: Color,
    val borderSubtle: Color,
    val cardCornerRadiusDp: Int = 12,

    // Brand Highlights & Accents
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val tertiaryAccent: Color,
    val onPrimaryAccent: Color = Color.Black,
    val accentMuted: Color,

    // Text Hierarchy
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,

    // Button Styling
    val primaryButtonBackground: Color,
    val primaryButtonText: Color,
    val primaryButtonGradientColors: List<Color>? = null,
    val secondaryButtonBackground: Color,
    val secondaryButtonText: Color,
    val buttonCornerRadiusDp: Int = 12,

    // Repair Status Semantics
    val statusReceived: Color,
    val statusChecking: Color,
    val statusRepairing: Color,
    val statusReady: Color,
    val statusDelivered: Color,
    val statusCancelled: Color,

    // Payment Status Semantics
    val paymentPaid: Color,
    val paymentPartial: Color,
    val paymentUnpaid: Color,

    // Navigation & Inputs
    val navSelectedIndicator: Color,
    val navSelectedText: Color,
    val inputFocusedBorder: Color,

    val isCustom: Boolean = false
) {
    val cardShape: RoundedCornerShape
        get() = RoundedCornerShape(cardCornerRadiusDp.dp)

    val buttonShape: RoundedCornerShape
        get() = RoundedCornerShape(buttonCornerRadiusDp.dp)

    val primaryGradientBrush: Brush?
        get() = if (!primaryButtonGradientColors.isNullOrEmpty()) {
            Brush.horizontalGradient(primaryButtonGradientColors)
        } else null

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("themeMode", themeMode.id)
        obj.put("name", name)
        obj.put("background", background.toHex())
        obj.put("surface", surface.toHex())
        obj.put("surfaceVariant", surfaceVariant.toHex())
        obj.put("cardBackground", cardBackground.toHex())
        obj.put("cardBackgroundElevated", cardBackgroundElevated.toHex())
        obj.put("border", border.toHex())
        obj.put("borderSubtle", borderSubtle.toHex())
        obj.put("cardCornerRadiusDp", cardCornerRadiusDp)

        obj.put("primaryAccent", primaryAccent.toHex())
        obj.put("secondaryAccent", secondaryAccent.toHex())
        obj.put("tertiaryAccent", tertiaryAccent.toHex())
        obj.put("onPrimaryAccent", onPrimaryAccent.toHex())
        obj.put("accentMuted", accentMuted.toHex())

        obj.put("textPrimary", textPrimary.toHex())
        obj.put("textSecondary", textSecondary.toHex())
        obj.put("textTertiary", textTertiary.toHex())

        obj.put("primaryButtonBackground", primaryButtonBackground.toHex())
        obj.put("primaryButtonText", primaryButtonText.toHex())
        if (primaryButtonGradientColors != null) {
            val gradArr = JSONArray()
            primaryButtonGradientColors.forEach { gradArr.put(it.toHex()) }
            obj.put("primaryButtonGradientColors", gradArr)
        }
        obj.put("secondaryButtonBackground", secondaryButtonBackground.toHex())
        obj.put("secondaryButtonText", secondaryButtonText.toHex())
        obj.put("buttonCornerRadiusDp", buttonCornerRadiusDp)

        obj.put("statusReceived", statusReceived.toHex())
        obj.put("statusChecking", statusChecking.toHex())
        obj.put("statusRepairing", statusRepairing.toHex())
        obj.put("statusReady", statusReady.toHex())
        obj.put("statusDelivered", statusDelivered.toHex())
        obj.put("statusCancelled", statusCancelled.toHex())

        obj.put("paymentPaid", paymentPaid.toHex())
        obj.put("paymentPartial", paymentPartial.toHex())
        obj.put("paymentUnpaid", paymentUnpaid.toHex())

        obj.put("navSelectedIndicator", navSelectedIndicator.toHex())
        obj.put("navSelectedText", navSelectedText.toHex())
        obj.put("inputFocusedBorder", inputFocusedBorder.toHex())
        obj.put("isCustom", isCustom)
        return obj.toString(2)
    }

    companion object {
        fun Color.toHex(): String {
            val a = (alpha * 255).toInt()
            val r = (red * 255).toInt()
            val g = (green * 255).toInt()
            val b = (blue * 255).toInt()
            return String.format("#%02X%02X%02X%02X", a, r, g, b)
        }

        fun colorFromHex(hex: String, default: Color): Color {
            return try {
                Color(android.graphics.Color.parseColor(hex))
            } catch (_: Exception) {
                default
            }
        }

        fun fromJson(json: String): AppThemeConfig {
            return try {
                val obj = JSONObject(json)
                val mode = AppThemeMode.fromId(obj.optString("themeMode", "CUSTOM"))
                val base = getTheme(mode.id)

                val gradColors: List<Color>? = obj.optJSONArray("primaryButtonGradientColors")?.let { arr ->
                    val list = mutableListOf<Color>()
                    for (i in 0 until arr.length()) {
                        list.add(colorFromHex(arr.getString(i), base.primaryAccent))
                    }
                    if (list.isNotEmpty()) list else null
                }

                AppThemeConfig(
                    themeMode = mode,
                    name = obj.optString("name", "Custom Theme"),
                    background = colorFromHex(obj.optString("background"), base.background),
                    surface = colorFromHex(obj.optString("surface"), base.surface),
                    surfaceVariant = colorFromHex(obj.optString("surfaceVariant"), base.surfaceVariant),
                    cardBackground = colorFromHex(obj.optString("cardBackground"), base.cardBackground),
                    cardBackgroundElevated = colorFromHex(obj.optString("cardBackgroundElevated"), base.cardBackgroundElevated),
                    border = colorFromHex(obj.optString("border"), base.border),
                    borderSubtle = colorFromHex(obj.optString("borderSubtle"), base.borderSubtle),
                    cardCornerRadiusDp = obj.optInt("cardCornerRadiusDp", base.cardCornerRadiusDp),
                    primaryAccent = colorFromHex(obj.optString("primaryAccent"), base.primaryAccent),
                    secondaryAccent = colorFromHex(obj.optString("secondaryAccent"), base.secondaryAccent),
                    tertiaryAccent = colorFromHex(obj.optString("tertiaryAccent"), base.tertiaryAccent),
                    onPrimaryAccent = colorFromHex(obj.optString("onPrimaryAccent"), base.onPrimaryAccent),
                    accentMuted = colorFromHex(obj.optString("accentMuted"), base.accentMuted),
                    textPrimary = colorFromHex(obj.optString("textPrimary"), base.textPrimary),
                    textSecondary = colorFromHex(obj.optString("textSecondary"), base.textSecondary),
                    textTertiary = colorFromHex(obj.optString("textTertiary"), base.textTertiary),
                    primaryButtonBackground = colorFromHex(obj.optString("primaryButtonBackground"), base.primaryButtonBackground),
                    primaryButtonText = colorFromHex(obj.optString("primaryButtonText"), base.primaryButtonText),
                    primaryButtonGradientColors = gradColors,
                    secondaryButtonBackground = colorFromHex(obj.optString("secondaryButtonBackground"), base.secondaryButtonBackground),
                    secondaryButtonText = colorFromHex(obj.optString("secondaryButtonText"), base.secondaryButtonText),
                    buttonCornerRadiusDp = obj.optInt("buttonCornerRadiusDp", base.buttonCornerRadiusDp),
                    statusReceived = colorFromHex(obj.optString("statusReceived"), base.statusReceived),
                    statusChecking = colorFromHex(obj.optString("statusChecking"), base.statusChecking),
                    statusRepairing = colorFromHex(obj.optString("statusRepairing"), base.statusRepairing),
                    statusReady = colorFromHex(obj.optString("statusReady"), base.statusReady),
                    statusDelivered = colorFromHex(obj.optString("statusDelivered"), base.statusDelivered),
                    statusCancelled = colorFromHex(obj.optString("statusCancelled"), base.statusCancelled),
                    paymentPaid = colorFromHex(obj.optString("paymentPaid"), base.paymentPaid),
                    paymentPartial = colorFromHex(obj.optString("paymentPartial"), base.paymentPartial),
                    paymentUnpaid = colorFromHex(obj.optString("paymentUnpaid"), base.paymentUnpaid),
                    navSelectedIndicator = colorFromHex(obj.optString("navSelectedIndicator"), base.navSelectedIndicator),
                    navSelectedText = colorFromHex(obj.optString("navSelectedText"), base.navSelectedText),
                    inputFocusedBorder = colorFromHex(obj.optString("inputFocusedBorder"), base.inputFocusedBorder),
                    isCustom = true
                )
            } catch (_: Exception) {
                UDM_CLASSIC_THEME
            }
        }

        // 1. UDM CLASSIC (Default)
        val UDM_CLASSIC_THEME = AppThemeConfig(
            themeMode = AppThemeMode.UDM_CLASSIC,
            name = "UDM Classic",
            background = RawNavyBackground, // 0xFF090E1A
            surface = RawNavySurface,       // 0xFF0F1A2E
            surfaceVariant = RawNavyCard,   // 0xFF152238
            cardBackground = RawNavyCard,
            cardBackgroundElevated = RawNavyCardElevated, // 0xFF1B2C48
            border = RawNavyBorder,         // 0xFF263C60
            borderSubtle = RawNavyBorderSubtle, // 0xFF1E3150
            cardCornerRadiusDp = 12,

            primaryAccent = RawCyanAccent,   // 0xFF00E5FF
            secondaryAccent = RawBluePrimary,// 0xFF2563EB
            tertiaryAccent = RawBlueLight,   // 0xFF38BDF8
            onPrimaryAccent = Color(0xFF090E1A),
            accentMuted = RawCyanAccentMuted, // 0xFF00B4D8

            textPrimary = RawNavyTextPrimary,     // 0xFFF1F5F9
            textSecondary = RawNavyTextSecondary, // 0xFF94A3B8
            textTertiary = RawNavyTextTertiary,   // 0xFF64748B

            primaryButtonBackground = RawCyanAccent,
            primaryButtonText = Color(0xFF090E1A),
            primaryButtonGradientColors = null,
            secondaryButtonBackground = RawBluePrimary,
            secondaryButtonText = Color.White,
            buttonCornerRadiusDp = 12,

            statusReceived = RawStatusReceived,   // 0xFFF59E0B
            statusChecking = RawStatusChecking,   // 0xFFA855F7
            statusRepairing = RawStatusRepairing, // 0xFF3B82F6
            statusReady = RawStatusReady,         // 0xFF10B981
            statusDelivered = RawStatusDelivered, // 0xFF059669
            statusCancelled = RawStatusCancelled, // 0xFFEF4444

            paymentPaid = RawPaymentPaid,
            paymentPartial = RawPaymentPartial,
            paymentUnpaid = RawPaymentUnpaid,

            navSelectedIndicator = RawCyanAccent,
            navSelectedText = RawCyanAccent,
            inputFocusedBorder = RawCyanAccent
        )

        // 2. MIDNIGHT LUXURY
        val MIDNIGHT_LUXURY_THEME = AppThemeConfig(
            themeMode = AppThemeMode.MIDNIGHT_LUXURY,
            name = "Midnight Luxury",
            background = Color(0xFF070B14),
            surface = Color(0xFF0D1527),
            surfaceVariant = Color(0xFF131F37),
            cardBackground = Color(0xFF131F37),
            cardBackgroundElevated = Color(0xFF1B2B4C),
            border = Color(0xFF23385F),
            borderSubtle = Color(0xFF17253F),
            cardCornerRadiusDp = 14,

            primaryAccent = Color(0xFF00D2FF),
            secondaryAccent = Color(0xFF2563EB),
            tertiaryAccent = Color(0xFF60A5FA),
            onPrimaryAccent = Color(0xFF020617),
            accentMuted = Color(0xFF0284C7),

            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8),
            textTertiary = Color(0xFF64748B),

            primaryButtonBackground = Color(0xFF00D2FF),
            primaryButtonText = Color(0xFF020617),
            primaryButtonGradientColors = listOf(Color(0xFF1D4ED8), Color(0xFF00D2FF)),
            secondaryButtonBackground = Color(0xFF1E293B),
            secondaryButtonText = Color(0xFF38BDF8),
            buttonCornerRadiusDp = 14,

            statusReceived = Color(0xFFF59E0B),
            statusChecking = Color(0xFF818CF8),
            statusRepairing = Color(0xFF38BDF8),
            statusReady = Color(0xFF34D399),
            statusDelivered = Color(0xFF10B981),
            statusCancelled = Color(0xFFFB7185),

            paymentPaid = Color(0xFF34D399),
            paymentPartial = Color(0xFFFBBF24),
            paymentUnpaid = Color(0xFFFB7185),

            navSelectedIndicator = Color(0xFF00D2FF),
            navSelectedText = Color(0xFF00D2FF),
            inputFocusedBorder = Color(0xFF00D2FF)
        )

        // 3. ROYAL VIOLET
        val ROYAL_VIOLET_THEME = AppThemeConfig(
            themeMode = AppThemeMode.ROYAL_VIOLET,
            name = "Royal Violet",
            background = Color(0xFF0C0A14),
            surface = Color(0xFF151024),
            surfaceVariant = Color(0xFF1E1733),
            cardBackground = Color(0xFF1E1733),
            cardBackgroundElevated = Color(0xFF291F46),
            border = Color(0xFF3D2F64),
            borderSubtle = Color(0xFF2A2045),
            cardCornerRadiusDp = 16,

            primaryAccent = Color(0xFFC084FC),
            secondaryAccent = Color(0xFFA855F7),
            tertiaryAccent = Color(0xFFE879F9),
            onPrimaryAccent = Color(0xFF0C0A14),
            accentMuted = Color(0xFF9333EA),

            textPrimary = Color(0xFFFAF5FF),
            textSecondary = Color(0xFFA8A29E),
            textTertiary = Color(0xFF78716C),

            primaryButtonBackground = Color(0xFFC084FC),
            primaryButtonText = Color(0xFF0C0A14),
            primaryButtonGradientColors = listOf(Color(0xFF7E22CE), Color(0xFFC084FC)),
            secondaryButtonBackground = Color(0xFF271E42),
            secondaryButtonText = Color(0xFFE879F9),
            buttonCornerRadiusDp = 16,

            statusReceived = Color(0xFFFBBF24),
            statusChecking = Color(0xFFE879F9),
            statusRepairing = Color(0xFFA78BFA),
            statusReady = Color(0xFF34D399),
            statusDelivered = Color(0xFF10B981),
            statusCancelled = Color(0xFFF43F5E),

            paymentPaid = Color(0xFF34D399),
            paymentPartial = Color(0xFFFBBF24),
            paymentUnpaid = Color(0xFFF43F5E),

            navSelectedIndicator = Color(0xFFC084FC),
            navSelectedText = Color(0xFFC084FC),
            inputFocusedBorder = Color(0xFFC084FC)
        )

        fun getTheme(themeName: String, customJson: String = ""): AppThemeConfig {
            return when (themeName.uppercase().trim()) {
                "MIDNIGHT_LUXURY" -> MIDNIGHT_LUXURY_THEME
                "ROYAL_VIOLET" -> ROYAL_VIOLET_THEME
                "CUSTOM" -> if (customJson.isNotBlank()) fromJson(customJson) else UDM_CLASSIC_THEME
                else -> UDM_CLASSIC_THEME
            }
        }
    }
}

val LocalAppTheme = staticCompositionLocalOf { AppThemeConfig.UDM_CLASSIC_THEME }

object AppTheme {
    val colors: AppThemeConfig
        @Composable
        get() = LocalAppTheme.current

    val cardShape: RoundedCornerShape
        @Composable
        get() = LocalAppTheme.current.cardShape

    val buttonShape: RoundedCornerShape
        @Composable
        get() = LocalAppTheme.current.buttonShape

    val cardCornerRadius: Dp
        @Composable
        get() = LocalAppTheme.current.cardCornerRadiusDp.dp

    val buttonCornerRadius: Dp
        @Composable
        get() = LocalAppTheme.current.buttonCornerRadiusDp.dp
}

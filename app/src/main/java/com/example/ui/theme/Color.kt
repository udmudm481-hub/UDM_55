package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Raw Static Colors for UDM Classic Defaults & Fallbacks
val RawNavyBackground = Color(0xFF090E1A)
val RawNavySurface = Color(0xFF0F1A2E)
val RawNavyCard = Color(0xFF152238)
val RawNavyCardElevated = Color(0xFF1B2C48)
val RawNavyBorder = Color(0xFF263C60)
val RawNavyBorderSubtle = Color(0xFF1E3150)

val RawCyanAccent = Color(0xFF00E5FF)
val RawCyanAccentMuted = Color(0xFF00B4D8)
val RawBluePrimary = Color(0xFF2563EB)
val RawBlueLight = Color(0xFF38BDF8)
val RawNavyTextPrimary = Color(0xFFF1F5F9)
val RawNavyTextSecondary = Color(0xFF94A3B8)
val RawNavyTextTertiary = Color(0xFF64748B)

val RawStatusReceived = Color(0xFFF59E0B)
val RawStatusChecking = Color(0xFFA855F7)
val RawStatusRepairing = Color(0xFF3B82F6)
val RawStatusReady = Color(0xFF10B981)
val RawStatusDelivered = Color(0xFF059669)
val RawStatusCancelled = Color(0xFFEF4444)

val RawPaymentPaid = Color(0xFF10B981)
val RawPaymentPartial = Color(0xFFF59E0B)
val RawPaymentUnpaid = Color(0xFFEF4444)

// Light Mode Navy Palette
val LightNavyBackground = Color(0xFFF8FAFC)
val LightNavySurface = Color(0xFFFFFFFF)
val LightNavyCard = Color(0xFFF1F5F9)
val LightNavyBorder = Color(0xFFCBD5E1)
val LightNavyPrimary = Color(0xFF0284C7)

// Dynamic Composable Theme Properties (App-Wide Theme System)
// These dynamically route to LocalAppTheme.current so all existing screens automatically reflect the selected theme!
val CyanAccent: Color
    @Composable
    get() = LocalAppTheme.current.primaryAccent

val CyanAccentMuted: Color
    @Composable
    get() = LocalAppTheme.current.accentMuted

val BluePrimary: Color
    @Composable
    get() = LocalAppTheme.current.secondaryAccent

val BlueLight: Color
    @Composable
    get() = LocalAppTheme.current.tertiaryAccent

val NavyBackground: Color
    @Composable
    get() = LocalAppTheme.current.background

val NavySurface: Color
    @Composable
    get() = LocalAppTheme.current.surface

val NavyCard: Color
    @Composable
    get() = LocalAppTheme.current.cardBackground

val NavyCardElevated: Color
    @Composable
    get() = LocalAppTheme.current.cardBackgroundElevated

val NavyBorder: Color
    @Composable
    get() = LocalAppTheme.current.border

val NavyBorderSubtle: Color
    @Composable
    get() = LocalAppTheme.current.borderSubtle

val NavyTextPrimary: Color
    @Composable
    get() = LocalAppTheme.current.textPrimary

val NavyTextSecondary: Color
    @Composable
    get() = LocalAppTheme.current.textSecondary

val NavyTextTertiary: Color
    @Composable
    get() = LocalAppTheme.current.textTertiary

val StatusReceived: Color
    @Composable
    get() = LocalAppTheme.current.statusReceived

val StatusChecking: Color
    @Composable
    get() = LocalAppTheme.current.statusChecking

val StatusRepairing: Color
    @Composable
    get() = LocalAppTheme.current.statusRepairing

val StatusReady: Color
    @Composable
    get() = LocalAppTheme.current.statusReady

val StatusDelivered: Color
    @Composable
    get() = LocalAppTheme.current.statusDelivered

val StatusCancelled: Color
    @Composable
    get() = LocalAppTheme.current.statusCancelled

val PaymentPaid: Color
    @Composable
    get() = LocalAppTheme.current.paymentPaid

val PaymentPartial: Color
    @Composable
    get() = LocalAppTheme.current.paymentPartial

val PaymentUnpaid: Color
    @Composable
    get() = LocalAppTheme.current.paymentUnpaid

val OnPrimaryAccent: Color
    @Composable
    get() = LocalAppTheme.current.onPrimaryAccent

val PrimaryButtonBackground: Color
    @Composable
    get() = LocalAppTheme.current.primaryButtonBackground

val PrimaryButtonText: Color
    @Composable
    get() = LocalAppTheme.current.primaryButtonText

val SecondaryButtonBackground: Color
    @Composable
    get() = LocalAppTheme.current.secondaryButtonBackground

val SecondaryButtonText: Color
    @Composable
    get() = LocalAppTheme.current.secondaryButtonText

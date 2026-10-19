package com.example.printer

import android.content.Context
import android.content.SharedPreferences

object PrinterPreferences {
    private const val PREFS_NAME = "udm_printer_prefs"
    private const val KEY_PRINTER_NAME = "saved_printer_name"
    private const val KEY_PRINTER_MAC = "saved_printer_mac"
    private const val KEY_AUTO_PRINT = "auto_print_customer_receipt"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedPrinterName(context: Context): String? {
        return getPrefs(context).getString(KEY_PRINTER_NAME, null)
    }

    fun getSavedPrinterMac(context: Context): String? {
        return getPrefs(context).getString(KEY_PRINTER_MAC, null)
    }

    fun savePrinter(context: Context, name: String, mac: String) {
        getPrefs(context).edit()
            .putString(KEY_PRINTER_NAME, name)
            .putString(KEY_PRINTER_MAC, mac)
            .apply()
    }

    fun clearSavedPrinter(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PRINTER_NAME)
            .remove(KEY_PRINTER_MAC)
            .apply()
    }

    fun isAutoPrintEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_PRINT, false)
    }

    fun setAutoPrintEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_AUTO_PRINT, enabled)
            .apply()
    }

    private const val KEY_CUSTOMER_RECEIPT_DIAGNOSTIC_MODE = "customer_receipt_diagnostic_mode"

    /**
     * Diagnostic mode prints explicit SLICE 0, SLICE 1, SLICE 2 test labels
     * to identify where P50S physical printing halts.
     * Defaulted to false for normal operation.
     */
    fun isCustomerReceiptDiagnosticMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CUSTOMER_RECEIPT_DIAGNOSTIC_MODE, false)
    }

    fun setCustomerReceiptDiagnosticMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_CUSTOMER_RECEIPT_DIAGNOSTIC_MODE, enabled)
            .apply()
    }

    // ==========================================
    // P50S ADVANCED PRINTING SYSTEM SETTINGS
    // ==========================================
    private const val KEY_P50S_PACING_MS = "p50s_image_packet_pacing_ms"
    private const val KEY_P50S_START_CMD = "p50s_start_command_enabled"
    private const val KEY_P50S_MAX_RASTER_HEIGHT = "p50s_raster_max_height"
    private const val KEY_P50S_FF03_CHECK = "p50s_ff03_completion_check_enabled"
    private const val KEY_P50S_POST_RASTER_DELAY_MS = "p50s_post_raster_delay_ms"
    private const val KEY_P50S_INTER_JOB_DELAY_MS = "p50s_inter_job_delay_ms"
    private const val KEY_P50S_STOP_CMD = "p50s_stop_command_enabled"
    private const val KEY_P50S_EXPERIMENTAL_MULTI_RASTER_MODE = "p50s_experimental_multi_raster_mode"

    fun getP50SSettings(context: Context): P50SPrintingSystemSettings {
        val prefs = getPrefs(context)
        return P50SPrintingSystemSettings(
            imagePacketPacingMs = prefs.getLong(KEY_P50S_PACING_MS, P50SPrintingSystemSettings.DEFAULT_IMAGE_PACKET_PACING_MS),
            startCommandEnabled = prefs.getBoolean(KEY_P50S_START_CMD, P50SPrintingSystemSettings.DEFAULT_START_COMMAND_ENABLED),
            rasterMaxHeight = prefs.getInt(KEY_P50S_MAX_RASTER_HEIGHT, P50SPrintingSystemSettings.DEFAULT_RASTER_MAX_HEIGHT),
            ff03CompletionCheckEnabled = prefs.getBoolean(KEY_P50S_FF03_CHECK, P50SPrintingSystemSettings.DEFAULT_FF03_COMPLETION_CHECK_ENABLED),
            postRasterDelayMs = prefs.getLong(KEY_P50S_POST_RASTER_DELAY_MS, P50SPrintingSystemSettings.DEFAULT_POST_RASTER_DELAY_MS),
            interJobDelayMs = prefs.getLong(KEY_P50S_INTER_JOB_DELAY_MS, P50SPrintingSystemSettings.DEFAULT_INTER_JOB_DELAY_MS),
            stopCommandEnabled = prefs.getBoolean(KEY_P50S_STOP_CMD, P50SPrintingSystemSettings.DEFAULT_STOP_COMMAND_ENABLED),
            experimentalMultiRasterModeEnabled = prefs.getBoolean(KEY_P50S_EXPERIMENTAL_MULTI_RASTER_MODE, P50SPrintingSystemSettings.DEFAULT_EXPERIMENTAL_MULTI_RASTER_MODE_ENABLED)
        )
    }

    fun saveP50SSettings(context: Context, settings: P50SPrintingSystemSettings) {
        val safeSettings = settings.sanitized()
        getPrefs(context).edit()
            .putLong(KEY_P50S_PACING_MS, safeSettings.imagePacketPacingMs)
            .putBoolean(KEY_P50S_START_CMD, safeSettings.startCommandEnabled)
            .putInt(KEY_P50S_MAX_RASTER_HEIGHT, safeSettings.rasterMaxHeight)
            .putBoolean(KEY_P50S_FF03_CHECK, safeSettings.ff03CompletionCheckEnabled)
            .putLong(KEY_P50S_POST_RASTER_DELAY_MS, safeSettings.postRasterDelayMs)
            .putLong(KEY_P50S_INTER_JOB_DELAY_MS, safeSettings.interJobDelayMs)
            .putBoolean(KEY_P50S_STOP_CMD, safeSettings.stopCommandEnabled)
            .putBoolean(KEY_P50S_EXPERIMENTAL_MULTI_RASTER_MODE, safeSettings.experimentalMultiRasterModeEnabled)
            .apply()
    }

    fun resetP50SSettings(context: Context): P50SPrintingSystemSettings {
        val defaultSettings = P50SPrintingSystemSettings()
        saveP50SSettings(context, defaultSettings)
        return defaultSettings
    }
}

/**
 * P50S Printing System Configuration model.
 * All timing and protocol switches for runtime tuning without APK rebuild.
 */
data class P50SPrintingSystemSettings(
    val imagePacketPacingMs: Long = DEFAULT_IMAGE_PACKET_PACING_MS,
    val startCommandEnabled: Boolean = DEFAULT_START_COMMAND_ENABLED,
    val rasterMaxHeight: Int = DEFAULT_RASTER_MAX_HEIGHT,
    val ff03CompletionCheckEnabled: Boolean = DEFAULT_FF03_COMPLETION_CHECK_ENABLED,
    val postRasterDelayMs: Long = DEFAULT_POST_RASTER_DELAY_MS,
    val interJobDelayMs: Long = DEFAULT_INTER_JOB_DELAY_MS,
    val stopCommandEnabled: Boolean = DEFAULT_STOP_COMMAND_ENABLED,
    val experimentalMultiRasterModeEnabled: Boolean = DEFAULT_EXPERIMENTAL_MULTI_RASTER_MODE_ENABLED
) {
    /**
     * Sanitizes all fields to prevent crashes, infinite loops, or negative values.
     */
    fun sanitized(): P50SPrintingSystemSettings {
        return copy(
            imagePacketPacingMs = imagePacketPacingMs.coerceIn(MIN_PACKET_PACING_MS, MAX_PACKET_PACING_MS),
            rasterMaxHeight = rasterMaxHeight.coerceIn(MIN_RASTER_HEIGHT, MAX_RASTER_HEIGHT),
            postRasterDelayMs = postRasterDelayMs.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS),
            interJobDelayMs = interJobDelayMs.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
        )
    }

    companion object {
        const val DEFAULT_IMAGE_PACKET_PACING_MS = 28L
        const val DEFAULT_START_COMMAND_ENABLED = true
        const val DEFAULT_RASTER_MAX_HEIGHT = 200
        const val DEFAULT_FF03_COMPLETION_CHECK_ENABLED = true
        const val DEFAULT_POST_RASTER_DELAY_MS = 500L
        const val DEFAULT_INTER_JOB_DELAY_MS = 300L
        const val DEFAULT_STOP_COMMAND_ENABLED = true
        const val DEFAULT_EXPERIMENTAL_MULTI_RASTER_MODE_ENABLED = false

        const val MIN_PACKET_PACING_MS = 1L
        const val MAX_PACKET_PACING_MS = 500L

        const val MIN_RASTER_HEIGHT = 20
        const val MAX_RASTER_HEIGHT = 2000

        const val MIN_DELAY_MS = 0L
        const val MAX_DELAY_MS = 10000L
    }
}

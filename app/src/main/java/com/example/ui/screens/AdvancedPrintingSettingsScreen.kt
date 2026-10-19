package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.printer.P50SPrintingSystemSettings
import com.example.printer.PrintResult
import com.example.printer.PrinterConnectionState
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.viewmodel.RepairViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedPrintingSettingsScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    val settings by viewModel.p50sPrintingSettings.collectAsState()
    val connectionState by viewModel.printerConnectionState.collectAsState()
    val printStatusMsg by viewModel.printStatusMessage.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var isTestingPrint by remember { mutableStateOf(false) }

    // Local text states for numeric inputs to allow smooth editing
    var pacingInput by remember(settings.imagePacketPacingMs) { mutableStateOf(settings.imagePacketPacingMs.toString()) }
    var maxHeightInput by remember(settings.rasterMaxHeight) { mutableStateOf(settings.rasterMaxHeight.toString()) }
    var postDelayInput by remember(settings.postRasterDelayMs) { mutableStateOf(settings.postRasterDelayMs.toString()) }
    var interJobInput by remember(settings.interJobDelayMs) { mutableStateOf(settings.interJobDelayMs.toString()) }

    var pacingError by remember { mutableStateOf<String?>(null) }
    var maxHeightError by remember { mutableStateOf<String?>(null) }
    var postDelayError by remember { mutableStateOf<String?>(null) }
    var interJobError by remember { mutableStateOf<String?>(null) }

    val isPrintingNow = isTestingPrint || connectionState is PrinterConnectionState.Printing

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Advanced Printing System",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Marklife P50S Engine Tuning & Protocol Controls",
                            fontSize = 11.sp,
                            color = CyanAccent
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("advanced_settings_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.testTag("reset_printing_defaults_top_btn")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Defaults")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("advanced_printing_settings_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ====================================================
            // 1. ACTIVE PRINTING CONFIGURATION SUMMARY CARD
            // ====================================================
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CyanAccent.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CyanAccent, RoundedCornerShape(14.dp))
                        .testTag("current_printing_config_summary_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "P50S Printing Configuration",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyanAccent.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyanAccent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Monospace config summary
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                ConfigSummaryRow("Mode:", if (settings.experimentalMultiRasterModeEnabled) "Experimental Multi-Raster Single-Job" else "Stable Separate-Job")
                                ConfigSummaryRow("Packet:", "${settings.imagePacketPacingMs} ms")
                                ConfigSummaryRow("START:", if (settings.startCommandEnabled) "ON" else "OFF")
                                ConfigSummaryRow("Max Raster:", "${settings.rasterMaxHeight} px")
                                ConfigSummaryRow("FF03 Check:", if (settings.ff03CompletionCheckEnabled) "ON" else "OFF")
                                ConfigSummaryRow("Post Raster:", "${settings.postRasterDelayMs} ms")
                                ConfigSummaryRow("Inter Job:", "${settings.interJobDelayMs} ms")
                                ConfigSummaryRow("STOP:", if (settings.stopCommandEnabled) "ON" else "OFF")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Settings take effect immediately on next Customer & Payment receipt without rebuilding or restarting the app.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ====================================================
            // SECTION: TIMING & PROTOCOL CONTROLS
            // ====================================================
            item {
                Text(
                    text = "PRINT ENGINE TUNING PARAMETERS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            // 1. IMAGE PACKET PACING
            item {
                SettingCard(
                    title = "Image Packet Pacing",
                    badgeText = "${settings.imagePacketPacingMs} ms",
                    description = "Controls the delay between consecutive BLE raster image packets sent to Marklife P50S. Current proven default: 28 ms."
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = pacingInput,
                            onValueChange = { input ->
                                pacingInput = input
                                val value = input.toLongOrNull()
                                if (value == null || value <= 0L) {
                                    pacingError = "Must be a positive number (> 0 ms)"
                                } else if (value > 500L) {
                                    pacingError = "Must be <= 500 ms"
                                } else {
                                    pacingError = null
                                    viewModel.updateImagePacketPacing(value)
                                }
                            },
                            label = { Text("Pacing (ms)") },
                            suffix = { Text("ms") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            isError = pacingError != null,
                            supportingText = pacingError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_packet_pacing"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                focusedLabelColor = CyanAccent
                            ),
                            singleLine = true
                        )

                        PresetButton("28ms (Default)") {
                            pacingInput = "28"
                            pacingError = null
                            viewModel.updateImagePacketPacing(28L)
                        }
                    }
                }
            }

            // 2. START COMMAND
            item {
                SettingSwitchCard(
                    title = "START Command",
                    badgeText = if (settings.startCommandEnabled) "ON" else "OFF",
                    checked = settings.startCommandEnabled,
                    onCheckedChange = { viewModel.updateStartCommand(it) },
                    description = "When ON, sends the standard START_PRINT_JOB command before each raster block. When OFF, skips sending START_PRINT_JOB without modifying the command bytes. Default: ON.",
                    testTag = "switch_start_command"
                )
            }

            // 3. RASTER MAXIMUM HEIGHT
            item {
                SettingCard(
                    title = "Raster Maximum Height",
                    badgeText = "${settings.rasterMaxHeight} px",
                    description = "Maximum raster height per print job. Content-aware boundary detection remains active to prevent splitting text, letters, or logos. Actual blocks will be <= this limit. Proven default: 200 px."
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = maxHeightInput,
                            onValueChange = { input ->
                                maxHeightInput = input
                                val value = input.toIntOrNull()
                                if (value == null || value < 20) {
                                    maxHeightError = "Must be >= 20 px"
                                } else if (value > 2000) {
                                    maxHeightError = "Must be <= 2000 px"
                                } else {
                                    maxHeightError = null
                                    viewModel.updateRasterMaxHeight(value)
                                }
                            },
                            label = { Text("Max Height (px)") },
                            suffix = { Text("px") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            isError = maxHeightError != null,
                            supportingText = maxHeightError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_raster_max_height"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                focusedLabelColor = CyanAccent
                            ),
                            singleLine = true
                        )

                        PresetButton("200px (Default)") {
                            maxHeightInput = "200"
                            maxHeightError = null
                            viewModel.updateRasterMaxHeight(200)
                        }
                    }
                }
            }

            // 4. FF03 = 4 COMPLETION CHECK
            item {
                SettingSwitchCard(
                    title = "FF03 = 4 Completion Check",
                    badgeText = if (settings.ff03CompletionCheckEnabled) "ON" else "OFF",
                    checked = settings.ff03CompletionCheckEnabled,
                    onCheckedChange = { viewModel.updateFf03CompletionCheck(it) },
                    description = "When ON, waits for the printer 0xFF03 credit=4 notification ([0x01, 0x04]) after transmitting each raster block. Represents printer buffer/processing completion (not physical thermal printing completion). When OFF, skips waiting for FF03 and directly waits the configured Post-Raster Delay. Default: ON.",
                    testTag = "switch_ff03_check"
                )
            }

            // 5. POST-RASTER DELAY
            item {
                SettingCard(
                    title = "Post-Raster Delay",
                    badgeText = "${settings.postRasterDelayMs} ms",
                    description = if (settings.ff03CompletionCheckEnabled) {
                        "Physical settling delay applied after FF03 credit=4 is received before sending STOP (RASTER → wait FF03=4 → Post-Raster Delay → STOP). Proven default: 500 ms."
                    } else {
                        "Settling delay applied directly after raster transmission before sending STOP (RASTER → Post-Raster Delay → STOP). Proven default: 500 ms."
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = postDelayInput,
                            onValueChange = { input ->
                                postDelayInput = input
                                val value = input.toLongOrNull()
                                if (value == null || value < 0L) {
                                    postDelayError = "Cannot be negative"
                                } else if (value > 10000L) {
                                    postDelayError = "Must be <= 10000 ms"
                                } else {
                                    postDelayError = null
                                    viewModel.updatePostRasterDelay(value)
                                }
                            },
                            label = { Text("Delay (ms)") },
                            suffix = { Text("ms") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            isError = postDelayError != null,
                            supportingText = postDelayError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_post_raster_delay"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                focusedLabelColor = CyanAccent
                            ),
                            singleLine = true
                        )

                        PresetButton("500ms (Default)") {
                            postDelayInput = "500"
                            postDelayError = null
                            viewModel.updatePostRasterDelay(500L)
                        }
                    }
                }
            }

            // 6. INTER-JOB DELAY
            item {
                SettingCard(
                    title = "Inter-Job Delay",
                    badgeText = "${settings.interJobDelayMs} ms",
                    description = "Delay after STOP_PRINT_JOB before starting the next raster job (STOP → Inter-Job Delay → START → next raster). No paper feed or alignment occurs during this delay. Proven default: 300 ms."
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = interJobInput,
                            onValueChange = { input ->
                                interJobInput = input
                                val value = input.toLongOrNull()
                                if (value == null || value < 0L) {
                                    interJobError = "Cannot be negative"
                                } else if (value > 10000L) {
                                    interJobError = "Must be <= 10000 ms"
                                } else {
                                    interJobError = null
                                    viewModel.updateInterJobDelay(value)
                                }
                            },
                            label = { Text("Inter-Job Delay (ms)") },
                            suffix = { Text("ms") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            isError = interJobError != null,
                            supportingText = interJobError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_inter_job_delay"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                focusedLabelColor = CyanAccent
                            ),
                            singleLine = true
                        )

                        PresetButton("300ms (Default)") {
                            interJobInput = "300"
                            interJobError = null
                            viewModel.updateInterJobDelay(300L)
                        }
                    }
                }
            }

            // 7. STOP COMMAND
            item {
                SettingSwitchCard(
                    title = "STOP Command",
                    badgeText = if (settings.stopCommandEnabled) "ON" else "OFF",
                    checked = settings.stopCommandEnabled,
                    onCheckedChange = { viewModel.updateStopCommand(it) },
                    description = "When ON, sends the standard STOP_PRINT_JOB command after each raster block's completion/wait sequence. When OFF, skips sending STOP_PRINT_JOB without modifying the command bytes. Default: ON.",
                    testTag = "switch_stop_command"
                )
            }

            // ====================================================
            // SECTION: EXPERIMENTAL TEST FEATURES
            // ====================================================
            item {
                Text(
                    text = "EXPERIMENTAL TEST FEATURES",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFFFB300),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }

            // 8. EXPERIMENTAL MULTI-RASTER SINGLE-JOB MODE
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.experimentalMultiRasterModeEnabled) {
                            Color(0xFFFFB300).copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (settings.experimentalMultiRasterModeEnabled) {
                                Modifier.border(1.2.dp, Color(0xFFFFB300), RoundedCornerShape(12.dp))
                            } else Modifier
                        )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Experimental Multi-Raster Mode",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (settings.experimentalMultiRasterModeEnabled) Color(0xFFFFB300).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (settings.experimentalMultiRasterModeEnabled) "EXPERIMENTAL ON" else "OFF (STABLE)",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (settings.experimentalMultiRasterModeEnabled) Color(0xFFFFB300) else Color.Gray,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Switch(
                                checked = settings.experimentalMultiRasterModeEnabled,
                                onCheckedChange = { viewModel.updateExperimentalMultiRasterMode(it) },
                                modifier = Modifier.testTag("switch_experimental_multi_raster_mode"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF090E1A),
                                    checkedTrackColor = Color(0xFFFFB300)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Warning banner
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Warning",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Experimental: Multiple raster blocks inside one P50S print session. Keep OFF for the proven stable mode.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "When ON, opens ONE START_PRINT_JOB session for the entire receipt and transmits all raster blocks (<= configured Max Raster Height) sequentially with completion checking, before sending a single STOP_PRINT_JOB followed by final feed and alignment. No START, STOP, paper feed, or alignment occurs between intermediate raster blocks.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // ====================================================
            // RESET & TEST ACTIONS
            // ====================================================
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // TEST PRINT WITH ACTIVE SETTINGS
                    Button(
                        onClick = {
                            if (isPrintingNow) return@Button
                            isTestingPrint = true
                            viewModel.printTestReceipt { result ->
                                isTestingPrint = false
                                coroutineScope.launch {
                                    when (result) {
                                        is PrintResult.Success -> {
                                            snackbarHostState.showSnackbar("Test Print Complete using Active Configuration ✓")
                                        }
                                        is PrintResult.Error -> {
                                            snackbarHostState.showSnackbar("Print error: ${result.message}")
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isPrintingNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_print_active_config_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isPrintingNow) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF090E1A)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (printStatusMsg.isNotBlank()) printStatusMsg.uppercase() else "PRINTING...",
                                color = Color(0xFF090E1A),
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = null,
                                tint = Color(0xFF090E1A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TEST PRINT WITH ACTIVE SETTINGS",
                                color = Color(0xFF090E1A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // RESET BUTTON
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("reset_printing_system_defaults_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset Printing System Defaults",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // CONFIRMATION DIALOG: RESET DEFAULTS
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset Printing System Defaults?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will restore all P50S printing system parameters to proven defaults:\n\n" +
                            "• Image Packet Pacing = 28 ms\n" +
                            "• START Command = ON\n" +
                            "• Raster Maximum Height = 200 px\n" +
                            "• FF03 = 4 Completion Check = ON\n" +
                            "• Post-Raster Delay = 500 ms\n" +
                            "• Inter-Job Delay = 300 ms\n" +
                            "• STOP Command = ON\n\n" +
                            "These will immediately apply to both Customer and Payment receipts."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reset = viewModel.resetP50SSettings()
                        pacingInput = reset.imagePacketPacingMs.toString()
                        maxHeightInput = reset.rasterMaxHeight.toString()
                        postDelayInput = reset.postRasterDelayMs.toString()
                        interJobInput = reset.interJobDelayMs.toString()
                        pacingError = null
                        maxHeightError = null
                        postDelayError = null
                        interJobError = null
                        showResetDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("P50S settings restored to proven defaults ✓")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_printing_defaults_btn")
                ) {
                    Text("Reset Defaults")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ConfigSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (value == "ON" || value.endsWith("ms") || value.endsWith("px")) CyanAccent else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingCard(
    title: String,
    badgeText: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    badgeText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (checked) PaymentPaid.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (checked) PaymentPaid else Color.Gray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag(testTag),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF090E1A),
                        checkedTrackColor = CyanAccent
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 11.sp)
    }
}

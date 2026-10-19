package com.example.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.data.repository.SmsSendResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Inventory2
import com.example.data.repository.StockCheckResult
import com.example.data.repository.InsufficientStockInfo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.PaymentEntity
import com.example.data.entity.RepairEntity
import com.example.data.entity.RepairItemEntity
import com.example.ui.components.PaymentBadge
import com.example.ui.components.SmartAutocompleteField
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.PaymentPartial
import com.example.ui.theme.StatusCancelled
import com.example.ui.theme.StatusChecking
import com.example.ui.theme.StatusDelivered
import com.example.ui.theme.StatusReady
import com.example.ui.theme.StatusReceived
import com.example.ui.theme.StatusRepairing
import com.example.ui.util.PhoneActions
import com.example.ui.util.safeGetItemsAndPayments
import com.example.ui.viewmodel.RepairViewModel
import com.example.util.ReceiptHelper
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairDetailsScreen(
    repairId: Long,
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allRepairs by viewModel.allRepairs.collectAsState()
    val repair = allRepairs.find { it.id == repairId }
    val settings by viewModel.settings.collectAsState()
    val allRepairTypes by viewModel.allRepairTypes.collectAsState()
    val allUniversalRepairTypes by viewModel.allUniversalRepairTypes.collectAsState()
    val allPrices by viewModel.allPrices.collectAsState()

    val (items, payments) = safeGetItemsAndPayments(repairId, viewModel)
    val historyFlow = remember(repairId) { viewModel.getHistoryForRepair(repairId) }
    val history by historyFlow.collectAsState(initial = emptyList())

    // Observe SMS events for this repair job
    LaunchedEffect(repairId) {
        viewModel.smsEvents.collect { event ->
            if (event.repairId == repairId) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            repair?.let { currentRepair ->
                viewModel.sendReadySmsExplicit(currentRepair) { result ->
                    coroutineScope.launch {
                        val msg = when (result) {
                            is SmsSendResult.SentOrQueued -> "Ready SMS initiated and queued for ${currentRepair.customerPhone}"
                            is SmsSendResult.PermissionDenied -> "SMS permission not granted."
                            is SmsSendResult.Failed -> "Failed to send SMS: ${result.error}"
                            is SmsSendResult.DisabledInSettings -> "Automatic SMS is disabled in Settings."
                        }
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("SMS permission not granted. Please allow SMS permission in Android settings.")
            }
        }
    }

    // Dialog States
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<PaymentEntity?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<RepairItemEntity?>(null) }
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isPrintingCustomerReceipt by remember { mutableStateOf(false) }
    var recentlyAddedPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var showPrinterDisconnectedDialog by remember { mutableStateOf(false) }
    var onRetryPrintAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var cancellationReasonInput by remember { mutableStateOf("") }
    var insufficientStockItems by remember { mutableStateOf<List<InsufficientStockInfo>?>(null) }
    var pendingStatusToUpdate by remember { mutableStateOf<String?>(null) }
    var pendingPaymentAmount by remember { mutableStateOf<Double?>(null) }
    var emergencyAdjustmentReason by remember { mutableStateOf("") }
    var showEmergencyAdjustmentDialog by remember { mutableStateOf(false) }

    val handleStatusClick: (String) -> Unit = { st ->
        repair?.let { currentRepair ->
            if (st.equals("CANCELLED", ignoreCase = true)) {
                if (!currentRepair.status.equals("CANCELLED", ignoreCase = true)) {
                    cancellationReasonInput = ""
                    showCancelConfirmDialog = true
                }
            } else if ((st.equals("READY", ignoreCase = true) || st.equals("DELIVERED", ignoreCase = true)) &&
                !currentRepair.status.equals("READY", ignoreCase = true) && !currentRepair.status.equals("DELIVERED", ignoreCase = true)
            ) {
                coroutineScope.launch {
                    when (val check = viewModel.checkStockForRepair(currentRepair.id)) {
                        is StockCheckResult.Insufficient -> {
                            insufficientStockItems = check.items
                            pendingStatusToUpdate = st
                        }
                        is StockCheckResult.Ok -> {
                            viewModel.updateStatus(currentRepair.id, st)
                        }
                    }
                }
            } else {
                viewModel.updateStatus(currentRepair.id, st)
            }
        }
    }

    fun handlePrintResult(
        result: com.example.printer.PrintResult,
        successMsg: String,
        onRetry: () -> Unit
    ) {
        coroutineScope.launch {
            when (result) {
                is com.example.printer.PrintResult.Success -> {
                    snackbarHostState.showSnackbar(successMsg)
                }
                is com.example.printer.PrintResult.Error -> {
                    if (result.message.contains("not connected", ignoreCase = true)) {
                        onRetryPrintAction = onRetry
                        showPrinterDisconnectedDialog = true
                    } else {
                        snackbarHostState.showSnackbar(result.message)
                    }
                }
            }
        }
    }

    if (repair == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Repair not found", color = MaterialTheme.colorScheme.onSurface)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CyanAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#${repair.jobNumber}",
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = repair.customerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("details_back_btn")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(repair.id) }, modifier = Modifier.testTag("details_edit_btn")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Job", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showMenu = !showMenu }, modifier = Modifier.testTag("details_more_btn")) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Print Receipt (Marklife P50S)") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = CyanAccent) },
                            onClick = {
                                showMenu = false
                                isPrintingCustomerReceipt = true
                                viewModel.printCustomerReceiptDirect(repair) { result ->
                                    isPrintingCustomerReceipt = false
                                    handlePrintResult(result, "Customer receipt printed on Marklife P50S") {
                                        viewModel.printCustomerReceiptDirect(repair) { res2 ->
                                            handlePrintResult(res2, "Customer receipt printed on Marklife P50S") {}
                                        }
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Print 3-Job Diagnostic (Job 1/2/3)") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFFFFB300)) },
                            onClick = {
                                showMenu = false
                                isPrintingCustomerReceipt = true
                                viewModel.printCustomerReceiptDiagnostic { result ->
                                    isPrintingCustomerReceipt = false
                                    handlePrintResult(result, "3-Job diagnostic printed on Marklife P50S") {}
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Print Normal Customer Receipt") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = CyanAccent) },
                            onClick = {
                                showMenu = false
                                isPrintingCustomerReceipt = true
                                viewModel.printNormalCustomerReceiptDirect(repair) { result ->
                                    isPrintingCustomerReceipt = false
                                    handlePrintResult(result, "Normal customer receipt printed on Marklife P50S") {}
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("System / PDF Receipt") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                ReceiptHelper.printReceipt(context, repair, items, payments, settings)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Receipt") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                ReceiptHelper.shareReceipt(context, repair, items, payments, settings)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send SMS Again") },
                            leadingIcon = { Icon(Icons.Default.Message, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.SEND_SMS
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    viewModel.sendReadySmsExplicit(repair) { result ->
                                        coroutineScope.launch {
                                            val msg = when (result) {
                                                is SmsSendResult.SentOrQueued -> "Ready SMS initiated and queued for ${repair.customerPhone}"
                                                is SmsSendResult.PermissionDenied -> "SMS permission not granted. Please allow SMS permission in Android settings."
                                                is SmsSendResult.Failed -> "Failed to send SMS: ${result.error}"
                                                is SmsSendResult.DisabledInSettings -> "Automatic SMS is disabled in Settings."
                                            }
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                } else {
                                    smsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Repair Job", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { PhoneActions.callCustomer(context, repair.customerPhone) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("details_call_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call", color = CyanAccent)
                    }

                    OutlinedButton(
                        onClick = { showWhatsAppDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("details_whatsapp_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp", color = Color(0xFF25D366))
                    }

                    Button(
                        onClick = { showAddPaymentDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("details_add_payment_btn")
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Payment", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // STATUS & AUTOMATION BANNER
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Status:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            StatusBadge(status = repair.status)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status Change Chips
                        val statuses = listOf("RECEIVED", "CHECKING", "REPAIRING", "READY", "DELIVERED", "CANCELLED")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            statuses.take(3).forEach { st ->
                                val isSelected = repair.status.equals(st, ignoreCase = true)
                                val statusColor = when (st.uppercase()) {
                                    "RECEIVED" -> StatusReceived
                                    "CHECKING" -> StatusChecking
                                    "REPAIRING" -> StatusRepairing
                                    "READY" -> StatusReady
                                    "DELIVERED" -> StatusDelivered
                                    "CANCELLED" -> StatusCancelled
                                    else -> CyanAccent
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { handleStatusClick(st) },
                                    label = { Text(st, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = statusColor.copy(alpha = 0.2f),
                                        selectedLabelColor = statusColor
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = MaterialTheme.colorScheme.outline,
                                        selectedBorderColor = statusColor,
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.5.dp,
                                        enabled = true,
                                        selected = isSelected
                                    ),
                                    modifier = Modifier.weight(1f).testTag("status_chip_$st")
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            statuses.takeLast(3).forEach { st ->
                                val isSelected = repair.status.equals(st, ignoreCase = true)
                                val statusColor = when (st.uppercase()) {
                                    "RECEIVED" -> StatusReceived
                                    "CHECKING" -> StatusChecking
                                    "REPAIRING" -> StatusRepairing
                                    "READY" -> StatusReady
                                    "DELIVERED" -> StatusDelivered
                                    "CANCELLED" -> StatusCancelled
                                    else -> CyanAccent
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { handleStatusClick(st) },
                                    label = { Text(st, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = statusColor.copy(alpha = 0.2f),
                                        selectedLabelColor = statusColor
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = MaterialTheme.colorScheme.outline,
                                        selectedBorderColor = statusColor,
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.5.dp,
                                        enabled = true,
                                        selected = isSelected
                                    ),
                                    modifier = Modifier.weight(1f).testTag("status_chip_$st")
                                )
                            }
                        }

                        if (repair.status == "DELIVERED" && repair.paymentStatus == "PAID") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Auto-marked DELIVERED: Full payment received.",
                                fontSize = 11.sp,
                                color = PaymentPaid,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (repair.status == "CANCELLED") {
                            val cancelEntry = history.lastOrNull { it.status.equals("CANCELLED", ignoreCase = true) }
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "JOB CANCELLED",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (repair.customerPhone.isNotBlank()) {
                                            TextButton(
                                                onClick = {
                                                    val reason = cancelEntry?.notes ?: ""
                                                    viewModel.sendCancellationSmsExplicit(repair, reason) { result ->
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(result.message)
                                                        }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.testTag("resend_cancellation_sms_btn")
                                            ) {
                                                Icon(Icons.Default.Send, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Resend SMS", fontSize = 11.sp, color = CyanAccent)
                                            }
                                        }
                                    }
                                    if (cancelEntry != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Cancelled on ${cancelEntry.date} at ${cancelEntry.time}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (cancelEntry.notes.isNotBlank() && !cancelEntry.notes.equals("Job cancelled", ignoreCase = true)) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Reason: ${cancelEntry.notes}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // CUSTOMER RECEIPT ACTION BANNER
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CUSTOMER RECEIPT",
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Marklife P50S thermal receipt (Job #${repair.jobNumber})",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                isPrintingCustomerReceipt = true
                                viewModel.printCustomerReceiptDirect(repair) { result ->
                                    isPrintingCustomerReceipt = false
                                    handlePrintResult(result, "Customer receipt printed on Marklife P50S") {
                                        viewModel.printCustomerReceiptDirect(repair) { res2 ->
                                            handlePrintResult(res2, "Customer receipt printed on Marklife P50S") {}
                                        }
                                    }
                                }
                            },
                            enabled = !isPrintingCustomerReceipt,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("details_print_receipt_btn")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPrintingCustomerReceipt) "PRINTING..." else "PRINT RECEIPT",
                                color = Color(0xFF090E1A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // FINANCIAL SUMMARY CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PAYMENT SUMMARY", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                            PaymentBadge(paymentStatus = repair.paymentStatus)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Repair Price", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(
                                "${settings.currency} ${String.format(Locale.US, "%,.2f", repair.totalPrice)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Paid", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(
                                "${settings.currency} ${String.format(Locale.US, "%,.2f", repair.amountPaid)}",
                                fontWeight = FontWeight.Bold,
                                color = PaymentPaid
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Balance Due", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(
                                "${settings.currency} ${String.format(Locale.US, "%,.2f", repair.balance)}",
                                fontWeight = FontWeight.Bold,
                                color = if (repair.balance > 0) PaymentPartial else CyanAccent
                            )
                        }
                    }
                }
            }

            // DEVICE & CUSTOMER DETAILS
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("DEVICE & CUSTOMER", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        DetailRow(label = "Customer Name", value = repair.customerName)
                        DetailRow(label = "Phone Number", value = repair.customerPhone)
                        DetailRow(label = "Device", value = "${repair.brand} ${repair.model}")
                        if (repair.imei.isNotBlank()) DetailRow(label = "IMEI", value = repair.imei)
                        if (repair.deviceColour.isNotBlank()) DetailRow(label = "Colour", value = repair.deviceColour)
                        if (repair.accessories.isNotBlank()) DetailRow(label = "Accessories", value = repair.accessories)
                        DetailRow(label = "Customer Fault", value = repair.fault)
                        if (repair.technicianNotes.isNotBlank()) DetailRow(label = "Tech Notes", value = repair.technicianNotes)
                        if (repair.remarks.isNotBlank()) DetailRow(label = "Remarks", value = repair.remarks)
                        DetailRow(label = "Received Date", value = "${repair.receivedDate} at ${repair.receivedTime}")
                    }
                }
            }

            // REPAIR ITEMS
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("REPAIR ITEMS (${items.size})", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                            TextButton(
                                onClick = { showAddItemDialog = true },
                                modifier = Modifier.testTag("details_add_item_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Item", color = CyanAccent, fontSize = 12.sp)
                            }
                        }

                        if (items.isEmpty()) {
                            Text("No separate items listed", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        } else {
                            items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${item.repairType}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "— ${settings.currency} ${String.format(Locale.US, "%,.0f", item.price)}",
                                            fontWeight = FontWeight.Bold,
                                            color = CyanAccent,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { itemToEdit = item },
                                            modifier = Modifier.size(24.dp).testTag("details_edit_item_${item.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.removeRepairItem(item.id, repair.id) },
                                            modifier = Modifier.size(24.dp).testTag("details_delete_item_${item.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // PAYMENT HISTORY
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PAYMENT HISTORY (${payments.size})", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                            TextButton(onClick = { showAddPaymentDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Payment", color = CyanAccent, fontSize = 12.sp)
                            }
                        }

                        if (payments.isEmpty()) {
                            Text("No payments recorded yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        } else {
                            payments.forEach { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Payment #${p.paymentNumber}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${p.date} ${p.time}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${settings.currency} ${String.format(Locale.US, "%,.2f", p.amount)}",
                                            fontWeight = FontWeight.Bold,
                                            color = PaymentPaid,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.printPaymentReceipt(repair.id, p) { result ->
                                                    handlePrintResult(result, "Payment receipt printed on Marklife P50S") {
                                                        viewModel.printPaymentReceipt(repair.id, p) { res2 ->
                                                            handlePrintResult(res2, "Payment receipt printed on Marklife P50S") {}
                                                        }
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("print_payment_${p.paymentNumber}")
                                        ) {
                                            Icon(
                                                Icons.Default.Print,
                                                contentDescription = "Print Payment Receipt",
                                                tint = CyanAccent,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("PRINT", fontSize = 11.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { paymentToDelete = p },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STATUS HISTORY TIMELINE
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("STATUS HISTORY", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (history.isEmpty()) {
                            Text("No history entries", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        } else {
                            history.forEach { h ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(CyanAccent)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = h.status,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${h.date} ${h.time}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (h.notes.isNotBlank()) {
                                            Text(text = h.notes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG: CANCEL REPAIR JOB
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showCancelConfirmDialog = false
                cancellationReasonInput = ""
            },
            title = {
                Text(
                    text = "Cancel Repair Job",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Are you sure you want to cancel Job #${repair.jobNumber}?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "The repair total and outstanding balance will be set to Rs. 0. Historical payment transactions are safely preserved.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = cancellationReasonInput,
                        onValueChange = { cancellationReasonInput = it },
                        label = { Text("Cancellation Reason") },
                        placeholder = { Text("Optional reason (e.g. Spare part not available)") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancellation_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = cancellationReasonInput.trim()
                        showCancelConfirmDialog = false
                        cancellationReasonInput = ""
                        viewModel.cancelRepair(repair.id, reason)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_cancel_btn")
                ) {
                    Text("Confirm Cancel", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmDialog = false
                        cancellationReasonInput = ""
                    },
                    modifier = Modifier.testTag("dismiss_cancel_btn")
                ) {
                    Text("Back")
                }
            }
        )
    }

    // DIALOG: ADD PAYMENT
    if (showAddPaymentDialog) {
        if (repair.balance <= 0.0 || repair.paymentStatus.equals("PAID", ignoreCase = true)) {
            AlertDialog(
                onDismissRequest = { showAddPaymentDialog = false },
                title = { Text("Payment Completed", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "This repair job is already fully PAID.",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Total Price: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.totalPrice)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Total Paid: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.amountPaid)}",
                            color = PaymentPaid,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Remaining Balance: ${settings.currency} 0",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Status: DELIVERED",
                            color = PaymentPaid,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showAddPaymentDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("OK", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                    }
                }
            )
        } else {
            var paymentMode by remember { mutableStateOf("FULL") } // "FULL" (default) or "PARTIAL"
            val fullAmountStr = remember(repair.balance) {
                if (repair.balance % 1.0 == 0.0) repair.balance.toInt().toString() else String.format(Locale.US, "%.2f", repair.balance)
            }
            var partialAmountInput by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddPaymentDialog = false },
                title = { Text("Add Payment", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Price:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text("${settings.currency} ${String.format(Locale.US, "%,.0f", repair.totalPrice)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Already Paid:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text("${settings.currency} ${String.format(Locale.US, "%,.0f", repair.amountPaid)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Remaining Balance:", color = PaymentPartial, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${settings.currency} ${String.format(Locale.US, "%,.0f", repair.balance)}", color = PaymentPartial, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                }
                            }
                        }

                        Text("Select Payment Option:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = CyanAccent)

                        // Mode selection: FULL PAYMENT (default) vs PARTIAL PAYMENT
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { paymentMode = "FULL" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (paymentMode == "FULL"),
                                onClick = { paymentMode = "FULL" },
                                colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("FULL PAYMENT ✓", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Amount: ${settings.currency} $fullAmountStr (Settles to Rs. 0 & DELIVERED)", fontSize = 11.sp, color = CyanAccent)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { paymentMode = "PARTIAL" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (paymentMode == "PARTIAL"),
                                onClick = { paymentMode = "PARTIAL" },
                                colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PARTIAL PAYMENT", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        if (paymentMode == "PARTIAL") {
                            OutlinedTextField(
                                value = partialAmountInput,
                                onValueChange = { partialAmountInput = it },
                                label = { Text("Partial Amount (${settings.currency})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_payment_amount_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanAccent,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amt = if (paymentMode == "FULL") {
                                repair.balance
                            } else {
                                partialAmountInput.toDoubleOrNull() ?: 0.0
                            }
                            if (amt > 0) {
                                val willAutoDeliver = (repair.totalPrice > 0 && (repair.amountPaid + amt) >= repair.totalPrice)
                                if (willAutoDeliver && repair.status != "DELIVERED" && repair.status != "READY") {
                                    coroutineScope.launch {
                                        when (val check = viewModel.checkStockForRepair(repair.id)) {
                                            is StockCheckResult.Insufficient -> {
                                                insufficientStockItems = check.items
                                                pendingStatusToUpdate = "DELIVERED"
                                                pendingPaymentAmount = amt
                                                showAddPaymentDialog = false
                                            }
                                            is StockCheckResult.Ok -> {
                                                viewModel.addPayment(repair.id, amt) {
                                                    coroutineScope.launch {
                                                        val updatedPayments = viewModel.repository.getPaymentsForRepairDirect(repair.id)
                                                        recentlyAddedPayment = updatedPayments.lastOrNull()
                                                    }
                                                }
                                                showAddPaymentDialog = false
                                            }
                                        }
                                    }
                                } else {
                                    viewModel.addPayment(repair.id, amt) {
                                        coroutineScope.launch {
                                            val updatedPayments = viewModel.repository.getPaymentsForRepairDirect(repair.id)
                                            recentlyAddedPayment = updatedPayments.lastOrNull()
                                        }
                                    }
                                    showAddPaymentDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("dialog_payment_confirm_btn")
                    ) {
                        Text(
                            text = if (paymentMode == "FULL") "SAVE FULL PAYMENT" else "ADD PAYMENT",
                            color = Color(0xFF090E1A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddPaymentDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // DIALOG: ADD REPAIR ITEM
    if (showAddItemDialog) {
        var itemTypeInput by remember { mutableStateOf("") }
        var itemPriceInput by remember { mutableStateOf("") }
        var priceNotSetMessage by remember { mutableStateOf(false) }
        var saveForFutureJobs by remember { mutableStateOf(false) }
        var addSavePriceTarget by remember { mutableStateOf("MODEL") }

        val brand = repair.brand.trim()
        val model = repair.model.trim()

        val matchingPrices = remember(allPrices, brand, model) {
            allPrices.filter {
                it.brand.trim().equals(brand, ignoreCase = true) &&
                it.model.trim().equals(model, ignoreCase = true)
            }
        }

        val availableSuggestions = remember(matchingPrices, allRepairTypes, allUniversalRepairTypes) {
            (matchingPrices.map { it.repairType.trim() } +
             allRepairTypes.map { it.name.trim() } +
             allUniversalRepairTypes.map { it.name.trim() })
                .filter { it.isNotBlank() }
                .distinct()
        }

        fun updateRepairType(newType: String) {
            itemTypeInput = newType
            val trimmed = newType.trim()
            if (trimmed.isNotBlank()) {
                val matched = matchingPrices.find { it.repairType.trim().equals(trimmed, ignoreCase = true) }?.price
                if (matched != null && matched >= 0.0) {
                    itemPriceInput = if (matched % 1.0 == 0.0) matched.toInt().toString() else String.format(Locale.US, "%.2f", matched)
                    priceNotSetMessage = false
                } else {
                    val universalMatched = allUniversalRepairTypes.find { it.name.trim().equals(trimmed, ignoreCase = true) }?.defaultPrice
                    if (universalMatched != null && universalMatched >= 0.0) {
                        itemPriceInput = if (universalMatched % 1.0 == 0.0) universalMatched.toInt().toString() else String.format(Locale.US, "%.2f", universalMatched)
                        priceNotSetMessage = false
                    } else {
                        itemPriceInput = ""
                        priceNotSetMessage = true
                    }
                }
            } else {
                itemPriceInput = ""
                priceNotSetMessage = false
            }
        }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Add Repair Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (brand.isNotBlank() || model.isNotBlank()) {
                        Text(
                            text = "Device: $brand $model",
                            fontSize = 12.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    SmartAutocompleteField(
                        label = "Repair Type / Service",
                        value = itemTypeInput,
                        onValueChange = { updateRepairType(it) },
                        suggestions = availableSuggestions,
                        onSuggestionSelected = { updateRepairType(it) },
                        onAddNew = {
                            viewModel.addRepairType(it)
                            updateRepairType(it)
                        },
                        placeholder = "e.g. Battery, Screen Replacement",
                        testTag = "dialog_details_add_type_input"
                    )

                    if (priceNotSetMessage && brand.isNotBlank() && model.isNotBlank() && itemTypeInput.isNotBlank()) {
                        Surface(
                            color = PaymentPartial.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PaymentPartial, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Price not set for this model and repair type.",
                                    color = PaymentPartial,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = itemPriceInput,
                        onValueChange = { itemPriceInput = it },
                        label = { Text("Price (${settings.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_details_add_price_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveForFutureJobs = !saveForFutureJobs }
                            .padding(vertical = 4.dp)
                            .testTag("dialog_add_save_price_checkbox_row")
                    ) {
                        Checkbox(
                            checked = saveForFutureJobs,
                            onCheckedChange = { saveForFutureJobs = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CyanAccent,
                                checkmarkColor = Color(0xFF090E1A)
                            ),
                            modifier = Modifier.testTag("dialog_add_save_price_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save this price for future jobs",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (saveForFutureJobs) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, top = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { addSavePriceTarget = "MODEL" }
                                    .testTag("dialog_details_add_save_model_row")
                            ) {
                                RadioButton(
                                    selected = addSavePriceTarget == "MODEL",
                                    onClick = { addSavePriceTarget = "MODEL" },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyanAccent),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (brand.isNotBlank() && model.isNotBlank())
                                        "For $brand $model only"
                                    else "For this phone model only",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { addSavePriceTarget = "UNIVERSAL" }
                                    .testTag("dialog_details_add_save_universal_row")
                            ) {
                                RadioButton(
                                    selected = addSavePriceTarget == "UNIVERSAL",
                                    onClick = { addSavePriceTarget = "UNIVERSAL" },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyanAccent),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "As Universal default (all phone models)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = itemPriceInput.toDoubleOrNull()
                        if (itemTypeInput.isNotBlank() && itemPriceInput.isNotBlank() && p != null && p >= 0.0) {
                            val cleanType = itemTypeInput.trim()
                            viewModel.addRepairType(cleanType)
                            viewModel.addRepairItem(repair.id, cleanType, p)
                            if (saveForFutureJobs) {
                                if (addSavePriceTarget == "UNIVERSAL") {
                                    viewModel.saveUniversalRepairType(cleanType, p)
                                } else if (brand.isNotBlank() && model.isNotBlank()) {
                                    viewModel.savePrice(brand, model, cleanType, p)
                                }
                            }
                            showAddItemDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("dialog_add_item_confirm_btn")
                ) {
                    Text("Add", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: EDIT REPAIR ITEM
    val currentItem = itemToEdit
    if (currentItem != null) {
        var editTypeInput by remember(currentItem.id) { mutableStateOf(currentItem.repairType) }
        var editPriceInput by remember(currentItem.id) {
            val formatted = if (currentItem.price % 1.0 == 0.0) currentItem.price.toInt().toString() else String.format(Locale.US, "%.2f", currentItem.price)
            mutableStateOf(formatted)
        }
        var lastSelectedType by remember(currentItem.id) { mutableStateOf(currentItem.repairType) }
        var editPriceNotSetMessage by remember(currentItem.id) { mutableStateOf(false) }
        var saveForFutureJobs by remember(currentItem.id) { mutableStateOf(false) }
        var editSavePriceTarget by remember(currentItem.id) { mutableStateOf("MODEL") }

        val brand = repair.brand.trim()
        val model = repair.model.trim()

        val matchingPrices = remember(allPrices, brand, model) {
            allPrices.filter {
                it.brand.trim().equals(brand, ignoreCase = true) &&
                it.model.trim().equals(model, ignoreCase = true)
            }
        }

        val availableSuggestions = remember(matchingPrices, allRepairTypes, allUniversalRepairTypes) {
            (matchingPrices.map { it.repairType.trim() } +
             allRepairTypes.map { it.name.trim() } +
             allUniversalRepairTypes.map { it.name.trim() })
                .filter { it.isNotBlank() }
                .distinct()
        }

        fun updateEditRepairType(newType: String) {
            editTypeInput = newType
            if (newType.trim() != lastSelectedType.trim()) {
                lastSelectedType = newType.trim()
                val trimmed = newType.trim()
                if (trimmed.isNotBlank()) {
                    val matched = matchingPrices.find { it.repairType.trim().equals(trimmed, ignoreCase = true) }?.price
                    if (matched != null && matched >= 0.0) {
                        editPriceInput = if (matched % 1.0 == 0.0) matched.toInt().toString() else String.format(Locale.US, "%.2f", matched)
                        editPriceNotSetMessage = false
                    } else {
                        val universalMatched = allUniversalRepairTypes.find { it.name.trim().equals(trimmed, ignoreCase = true) }?.defaultPrice
                        if (universalMatched != null && universalMatched >= 0.0) {
                            editPriceInput = if (universalMatched % 1.0 == 0.0) universalMatched.toInt().toString() else String.format(Locale.US, "%.2f", universalMatched)
                            editPriceNotSetMessage = false
                        } else {
                            editPriceInput = ""
                            editPriceNotSetMessage = true
                        }
                    }
                } else {
                    editPriceInput = ""
                    editPriceNotSetMessage = false
                }
            }
        }

        fun onSuggestionChosen(selectedType: String) {
            editTypeInput = selectedType
            lastSelectedType = selectedType.trim()
            val trimmed = selectedType.trim()
            if (trimmed.isNotBlank()) {
                val matched = matchingPrices.find { it.repairType.trim().equals(trimmed, ignoreCase = true) }?.price
                if (matched != null && matched >= 0.0) {
                    editPriceInput = if (matched % 1.0 == 0.0) matched.toInt().toString() else String.format(Locale.US, "%.2f", matched)
                    editPriceNotSetMessage = false
                } else {
                    val universalMatched = allUniversalRepairTypes.find { it.name.trim().equals(trimmed, ignoreCase = true) }?.defaultPrice
                    if (universalMatched != null && universalMatched >= 0.0) {
                        editPriceInput = if (universalMatched % 1.0 == 0.0) universalMatched.toInt().toString() else String.format(Locale.US, "%.2f", universalMatched)
                        editPriceNotSetMessage = false
                    } else {
                        editPriceInput = ""
                        editPriceNotSetMessage = true
                    }
                }
            } else {
                editPriceInput = ""
                editPriceNotSetMessage = false
            }
        }

        AlertDialog(
            onDismissRequest = { itemToEdit = null },
            title = { Text("Edit Repair Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (brand.isNotBlank() || model.isNotBlank()) {
                        Text(
                            text = "Device: $brand $model",
                            fontSize = 12.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    SmartAutocompleteField(
                        label = "Repair Type / Service",
                        value = editTypeInput,
                        onValueChange = { updateEditRepairType(it) },
                        suggestions = availableSuggestions,
                        onSuggestionSelected = { onSuggestionChosen(it) },
                        onAddNew = {
                            viewModel.addRepairType(it)
                            onSuggestionChosen(it)
                        },
                        testTag = "dialog_details_edit_type_input"
                    )

                    if (editPriceNotSetMessage && brand.isNotBlank() && model.isNotBlank() && editTypeInput.isNotBlank()) {
                        Surface(
                            color = PaymentPartial.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PaymentPartial, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Price not set for this model and repair type.",
                                    color = PaymentPartial,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editPriceInput,
                        onValueChange = { editPriceInput = it },
                        label = { Text("Price (${settings.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_details_edit_price_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveForFutureJobs = !saveForFutureJobs }
                            .padding(vertical = 4.dp)
                            .testTag("dialog_edit_save_price_checkbox_row")
                    ) {
                        Checkbox(
                            checked = saveForFutureJobs,
                            onCheckedChange = { saveForFutureJobs = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CyanAccent,
                                checkmarkColor = Color(0xFF090E1A)
                            ),
                            modifier = Modifier.testTag("dialog_edit_save_price_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save this price for future jobs",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (saveForFutureJobs) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, top = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editSavePriceTarget = "MODEL" }
                                    .testTag("dialog_details_edit_save_model_row")
                            ) {
                                RadioButton(
                                    selected = editSavePriceTarget == "MODEL",
                                    onClick = { editSavePriceTarget = "MODEL" },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyanAccent),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (brand.isNotBlank() && model.isNotBlank())
                                        "For $brand $model only"
                                    else "For this phone model only",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editSavePriceTarget = "UNIVERSAL" }
                                    .testTag("dialog_details_edit_save_universal_row")
                            ) {
                                RadioButton(
                                    selected = editSavePriceTarget == "UNIVERSAL",
                                    onClick = { editSavePriceTarget = "UNIVERSAL" },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyanAccent),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "As Universal default (all phone models)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = editPriceInput.toDoubleOrNull()
                        if (editTypeInput.isNotBlank() && editPriceInput.isNotBlank() && p != null && p >= 0.0) {
                            val cleanType = editTypeInput.trim()
                            viewModel.addRepairType(cleanType)
                            viewModel.updateRepairItem(currentItem.id, repair.id, cleanType, p)
                            if (saveForFutureJobs) {
                                if (editSavePriceTarget == "UNIVERSAL") {
                                    viewModel.saveUniversalRepairType(cleanType, p)
                                } else if (brand.isNotBlank() && model.isNotBlank()) {
                                    viewModel.savePrice(brand, model, cleanType, p)
                                }
                            }
                            itemToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("dialog_details_confirm_edit_item_btn")
                ) {
                    Text("Save", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: CONFIRM DELETE PAYMENT
    if (paymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("Delete Payment?") },
            text = {
                Text("Are you sure you want to delete Payment #${paymentToDelete?.paymentNumber} of ${settings.currency} ${paymentToDelete?.amount}? The balance and status will be updated accordingly.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        paymentToDelete?.let { viewModel.deletePayment(it.id) }
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: CONFIRM DELETE ENTIRE REPAIR
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Repair #${repair.jobNumber}?") },
            text = { Text("This will permanently remove this repair record, its items, and its payment history.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRepair(repair.id) {
                            showDeleteConfirmDialog = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: EDIT WHATSAPP MESSAGE BEFORE SENDING
    if (showWhatsAppDialog) {
        var waMessage by remember {
            mutableStateOf(
                "Hello ${repair.customerName}, this is ${settings.shopName} regarding your repair Job #${repair.jobNumber} (${repair.brand} ${repair.model}). Current status: ${repair.status}. Balance: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.balance)}."
            )
        }
        AlertDialog(
            onDismissRequest = { showWhatsAppDialog = false },
            title = { Text("WhatsApp Message") },
            text = {
                OutlinedTextField(
                    value = waMessage,
                    onValueChange = { waMessage = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        PhoneActions.openWhatsApp(context, repair.customerPhone, waMessage)
                        showWhatsAppDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Text("Send on WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppDialog = false }) { Text("Cancel") }
            }
        )
    }

    // DIALOG: PAYMENT ADDED CONFIRMATION (WITH PRINT PAYMENT RECEIPT)
    val paymentJustAdded = recentlyAddedPayment
    if (paymentJustAdded != null) {
        AlertDialog(
            onDismissRequest = { recentlyAddedPayment = null },
            title = { Text("Payment Recorded ✓", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Payment #${paymentJustAdded.paymentNumber} of ${settings.currency} ${String.format(Locale.US, "%,.2f", paymentJustAdded.amount)} successfully recorded.",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Print thermal payment receipt for customer?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = paymentJustAdded
                        recentlyAddedPayment = null
                        viewModel.printPaymentReceipt(repair.id, p) { result ->
                            handlePrintResult(result, "Payment receipt printed on Marklife P50S") {
                                viewModel.printPaymentReceipt(repair.id, p) { res2 ->
                                    handlePrintResult(res2, "Payment receipt printed on Marklife P50S") {}
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("dialog_print_payment_receipt_btn")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PRINT PAYMENT RECEIPT", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { recentlyAddedPayment = null },
                    modifier = Modifier.testTag("dialog_dismiss_payment_receipt_btn")
                ) {
                    Text("DONE")
                }
            }
        )
    }

    if (showPrinterDisconnectedDialog) {
        AlertDialog(
            onDismissRequest = { showPrinterDisconnectedDialog = false },
            title = { Text("Printer Not Connected", fontWeight = FontWeight.Bold) },
            text = { Text("Please connect P50S-496A-BLE and try again.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPrinterDisconnectedDialog = false
                        onRetryPrintAction?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("printer_disconnected_retry_btn")
                ) {
                    Text("Print Again", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrinterDisconnectedDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // INSUFFICIENT STOCK WARNING DIALOG
    if (insufficientStockItems != null && pendingStatusToUpdate != null) {
        val itemsList = insufficientStockItems ?: emptyList()
        AlertDialog(
            onDismissRequest = {
                insufficientStockItems = null
                pendingStatusToUpdate = null
                pendingPaymentAmount = null
                showEmergencyAdjustmentDialog = false
                emergencyAdjustmentReason = ""
            },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Insufficient Stock Warning", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The following stock items do not have enough quantity to complete this repair:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    itemsList.forEach { info ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("insufficient_stock_card_${info.stockItem.id}")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Stock Item: ${info.stockItem.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("stock_item_name_${info.stockItem.id}")
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Available in Stock: ${info.available}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("stock_item_available_${info.stockItem.id}")
                                    )
                                    Text(
                                        text = "Required for Repair: ${info.required}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("stock_item_required_${info.stockItem.id}")
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Shortage: ${info.shortage}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("stock_item_shortage_${info.stockItem.id}")
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Completion is prevented until stock is available, or you can record a controlled stock adjustment.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showEmergencyAdjustmentDialog) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = emergencyAdjustmentReason,
                            onValueChange = { emergencyAdjustmentReason = it },
                            label = { Text("Adjustment Reason (Required)") },
                            placeholder = { Text("e.g. Physical stock verified / urgent arrival") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("stock_adjustment_reason_input")
                        )
                    }
                }
            },
            confirmButton = {
                if (!showEmergencyAdjustmentDialog) {
                    Button(
                        onClick = { showEmergencyAdjustmentDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier.testTag("record_stock_adjustment_btn")
                    ) {
                        Text("Record Adjustment & Complete", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            val target = pendingStatusToUpdate
                            val reason = emergencyAdjustmentReason.trim().ifEmpty { "Controlled stock adjustment before job completion" }
                            val pendingAmt = pendingPaymentAmount
                            if (pendingAmt != null) {
                                viewModel.addPayment(repair.id, pendingAmt, emergencyAdjustmentReason = reason) {
                                    coroutineScope.launch {
                                        val updatedPayments = viewModel.repository.getPaymentsForRepairDirect(repair.id)
                                        recentlyAddedPayment = updatedPayments.lastOrNull()
                                    }
                                }
                            } else if (target != null) {
                                viewModel.updateStatus(repair.id, target, emergencyAdjustmentReason = reason)
                            }
                            insufficientStockItems = null
                            pendingStatusToUpdate = null
                            pendingPaymentAmount = null
                            showEmergencyAdjustmentDialog = false
                            emergencyAdjustmentReason = ""
                        },
                        enabled = emergencyAdjustmentReason.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier.testTag("confirm_emergency_adjustment_btn")
                    ) {
                        Text("Confirm & Complete", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        insufficientStockItems = null
                        pendingStatusToUpdate = null
                        pendingPaymentAmount = null
                        showEmergencyAdjustmentDialog = false
                        emergencyAdjustmentReason = ""
                    },
                    modifier = Modifier.testTag("cancel_insufficient_stock_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.RepairEntity
import com.example.data.repository.InsufficientStockInfo
import com.example.data.repository.StockCheckResult
import com.example.ui.components.AccessoriesSelector
import com.example.ui.components.SmartAutocompleteField
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPartial
import com.example.ui.viewmodel.RepairViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class RepairItemInput(
    val id: String = UUID.randomUUID().toString(),
    val repairType: String,
    val price: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRepairScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCustomerHistory: (String) -> Unit = {},
    onNavigateToRepairDetails: (Long) -> Unit = {},
    onRepairCreated: (RepairEntity) -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    var pendingSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var insufficientStockItems by remember { mutableStateOf<List<InsufficientStockInfo>?>(null) }
    var savedRepairWithWarning by remember { mutableStateOf<RepairEntity?>(null) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingSaveAction?.invoke()
        pendingSaveAction = null
    }

    // Focus Requesters for seamless keyboard navigation
    val phoneFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }
    val brandFocusRequester = remember { FocusRequester() }
    val modelFocusRequester = remember { FocusRequester() }
    val repairTypeFocusRequester = remember { FocusRequester() }
    val repairPriceFocusRequester = remember { FocusRequester() }
    val advancePaymentFocusRequester = remember { FocusRequester() }

    fun safeRequestFocus(requester: FocusRequester) {
        try {
            requester.requestFocus()
        } catch (_: Exception) {
            try {
                if (!focusManager.moveFocus(FocusDirection.Down)) {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            } catch (_: Exception) {}
        }
    }

    // Master lists from ViewModel
    val allBrands by viewModel.allBrands.collectAsState()
    val allRepairTypes by viewModel.allRepairTypes.collectAsState()
    val allUniversalRepairTypes by viewModel.allUniversalRepairTypes.collectAsState()
    val allPrices by viewModel.allPrices.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // 1. Customer Details (Phone number is first!)
    var customerPhone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }

    // Previous customer search state
    var previousCustomerName by remember { mutableStateOf("") }
    var previousRepairsCount by remember { mutableIntStateOf(0) }
    var hasPreviousHistory by remember { mutableStateOf(false) }
    var previousRepairsList by remember { mutableStateOf<List<RepairEntity>>(emptyList()) }

    LaunchedEffect(customerPhone) {
        val clean = customerPhone.filter { it.isDigit() }
        if (clean.length >= 3) {
            viewModel.searchCustomerHistory(customerPhone) { cust, repairs ->
                if (repairs.isNotEmpty()) {
                    hasPreviousHistory = true
                    previousRepairsList = repairs
                    previousRepairsCount = repairs.size
                    val foundName = cust?.name ?: repairs.firstOrNull()?.customerName ?: ""
                    previousCustomerName = foundName
                    if (customerName.isBlank() && foundName.isNotBlank()) {
                        customerName = foundName
                    }
                } else {
                    hasPreviousHistory = false
                    previousRepairsList = emptyList()
                    previousRepairsCount = 0
                    previousCustomerName = ""
                }
            }
        } else {
            hasPreviousHistory = false
            previousRepairsList = emptyList()
            previousRepairsCount = 0
            previousCustomerName = ""
        }
    }

    val previousCreditJobs = remember(previousRepairsList) {
        previousRepairsList.filter { it.status == "DELIVERED" && it.balance > 0.0 }
    }
    val previousCreditBalance = remember(previousCreditJobs) {
        previousCreditJobs.sumOf { it.balance }
    }

    // 2. Primary Device Details
    var selectedBrand by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }

    // Models for selected brand
    var brandModels by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(selectedBrand) {
        if (selectedBrand.isNotBlank()) {
            viewModel.getModelsForBrand(selectedBrand).collect { models ->
                brandModels = models.map { it.modelName }
            }
        } else {
            brandModels = emptyList()
        }
    }

    // Multiple Repair Items Support
    val repairItems = remember { mutableStateListOf<RepairItemInput>() }

    // Inputs for the first / current repair item
    var selectedRepairType by remember { mutableStateOf("") }
    var repairPriceStr by remember { mutableStateOf("") }
    var priceNotSetMessage by remember { mutableStateOf(false) }
    var showSaveMainPriceDialog by remember { mutableStateOf(false) }
    var saveMainPriceTarget by remember { mutableStateOf("MODEL") }

    // Dialog state for "Add Another Repair"
    var showAddDialog by remember { mutableStateOf(false) }
    var dialogRepairType by remember { mutableStateOf("") }
    var dialogPriceStr by remember { mutableStateOf("") }
    var dialogPriceNotSet by remember { mutableStateOf(false) }
    var saveDialogPriceForFuture by remember { mutableStateOf(false) }
    var dialogSavePriceTarget by remember { mutableStateOf("MODEL") }

    // Dialog state for "Edit Repair Item"
    var itemToEdit by remember { mutableStateOf<RepairItemInput?>(null) }
    var editRepairType by remember { mutableStateOf("") }
    var editPriceStr by remember { mutableStateOf("") }
    var editPriceNotSet by remember { mutableStateOf(false) }
    var saveEditPriceForFuture by remember { mutableStateOf(false) }
    var editSavePriceTarget by remember { mutableStateOf("MODEL") }

    val availableSuggestions = remember(allPrices, allRepairTypes, allUniversalRepairTypes, selectedBrand, selectedModel) {
        val fromPrices = allPrices
            .filter { it.brand.equals(selectedBrand.trim(), ignoreCase = true) && it.model.equals(selectedModel.trim(), ignoreCase = true) }
            .map { it.repairType.trim() }
        val fromMaster = allRepairTypes.map { it.name.trim() }
        val fromUniversal = allUniversalRepairTypes.map { it.name.trim() }
        (fromPrices + fromMaster + fromUniversal).filter { it.isNotBlank() }.distinct()
    }

    // Smart Price Lookup for initial item: Brand + Model + Repair Type (with Universal fallback)
    LaunchedEffect(selectedBrand, selectedModel, selectedRepairType) {
        val b = selectedBrand.trim()
        val m = selectedModel.trim()
        val r = selectedRepairType.trim()
        if (r.isNotBlank()) {
            if (b.isNotBlank() && m.isNotBlank()) {
                viewModel.lookupPrice(b, m, r) { price ->
                    if (price != null && price >= 0.0) {
                        repairPriceStr = if (price % 1.0 == 0.0) price.toInt().toString() else String.format(Locale.US, "%.2f", price)
                        priceNotSetMessage = false
                    } else {
                        repairPriceStr = "" // Instantly clear previous price!
                        priceNotSetMessage = true
                    }
                }
            } else {
                // Brand or Model not yet chosen: check if Universal Repair Type
                viewModel.lookupPrice("", "", r) { price ->
                    if (price != null && price >= 0.0) {
                        repairPriceStr = if (price % 1.0 == 0.0) price.toInt().toString() else String.format(Locale.US, "%.2f", price)
                        priceNotSetMessage = false
                    } else {
                        repairPriceStr = ""
                        priceNotSetMessage = false
                    }
                }
            }
        } else {
            priceNotSetMessage = false
            repairPriceStr = ""
        }
    }

    // Smart Price Lookup for Dialog: Brand + Model + dialogRepairType
    LaunchedEffect(selectedBrand, selectedModel, dialogRepairType) {
        val b = selectedBrand.trim()
        val m = selectedModel.trim()
        val r = dialogRepairType.trim()
        if (r.isNotBlank()) {
            if (b.isNotBlank() && m.isNotBlank()) {
                viewModel.lookupPrice(b, m, r) { price ->
                    if (price != null && price >= 0.0) {
                        dialogPriceStr = if (price % 1.0 == 0.0) price.toInt().toString() else String.format(Locale.US, "%.2f", price)
                        dialogPriceNotSet = false
                    } else {
                        dialogPriceStr = ""
                        dialogPriceNotSet = true
                    }
                }
            } else {
                viewModel.lookupPrice("", "", r) { price ->
                    if (price != null && price >= 0.0) {
                        dialogPriceStr = if (price % 1.0 == 0.0) price.toInt().toString() else String.format(Locale.US, "%.2f", price)
                        dialogPriceNotSet = false
                    } else {
                        dialogPriceStr = ""
                        dialogPriceNotSet = false
                    }
                }
            }
        } else {
            dialogPriceNotSet = false
            dialogPriceStr = ""
        }
    }

    // Smart Price Lookup for Edit Dialog: Brand + Model + editRepairType
    LaunchedEffect(selectedBrand, selectedModel, editRepairType) {
        val b = selectedBrand.trim()
        val m = selectedModel.trim()
        val r = editRepairType.trim()
        if (r.isNotBlank() && itemToEdit != null) {
            viewModel.lookupPrice(b, m, r) { price ->
                if (price != null && price >= 0.0) {
                    editPriceStr = if (price % 1.0 == 0.0) price.toInt().toString() else String.format(Locale.US, "%.2f", price)
                    editPriceNotSet = false
                } else {
                    if (r != itemToEdit?.repairType?.trim()) {
                        editPriceStr = ""
                    }
                    editPriceNotSet = b.isNotBlank() && m.isNotBlank()
                }
            }
        }
    }

    // When Brand or Model changes: Re-validate prices for all existing repair items
    LaunchedEffect(selectedBrand, selectedModel) {
        val b = selectedBrand.trim()
        val m = selectedModel.trim()
        if (b.isNotBlank() && m.isNotBlank() && repairItems.isNotEmpty()) {
            repairItems.forEachIndexed { index, item ->
                viewModel.lookupPrice(b, m, item.repairType) { newPrice ->
                    if (newPrice != null && newPrice >= 0.0) {
                        repairItems[index] = item.copy(price = newPrice)
                    }
                }
            }
        }
    }

    // Optional Additional Details (IMEI, Colour, Accessories, Remarks)
    var showMoreDetails by remember { mutableStateOf(false) }
    var imei by remember { mutableStateOf("") }
    var deviceColour by remember { mutableStateOf("") }
    val selectedAccessories = remember { mutableStateListOf<String>() }
    var technicianNotes by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    // 3. Payment input
    var amountPaidStr by remember { mutableStateOf("") }

    // Calculations
    val totalPrice = if (repairItems.isNotEmpty()) {
        repairItems.sumOf { it.price }
    } else {
        repairPriceStr.toDoubleOrNull() ?: 0.0
    }
    val amountPaid = amountPaidStr.toDoubleOrNull() ?: 0.0
    val balance = (totalPrice - amountPaid).coerceAtLeast(0.0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New Repair Job",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val itemsCount = if (repairItems.isNotEmpty()) repairItems.size else if (selectedRepairType.isNotBlank()) 1 else 0
                            Text(
                                text = "Total: ${settings.currency} ${String.format(Locale.US, "%,.0f", totalPrice)}" + if (itemsCount > 1) " ($itemsCount items)" else "",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (amountPaid > 0) {
                                Text(
                                    text = "Balance: ${settings.currency} ${String.format(Locale.US, "%,.0f", balance)}",
                                    fontSize = 13.sp,
                                    color = if (balance > 0) PaymentPartial else CyanAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                // Validation
                                when {
                                    customerPhone.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please enter customer phone number") }
                                    }
                                    customerName.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please enter customer name") }
                                    }
                                    selectedBrand.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please select or enter device brand") }
                                    }
                                    selectedModel.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please select or enter device model") }
                                    }
                                    repairItems.isEmpty() && selectedRepairType.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please select or enter repair type") }
                                    }
                                    (repairItems.isEmpty() && (repairPriceStr.isBlank() || (repairPriceStr.toDoubleOrNull() ?: -1.0) < 0.0)) ||
                                    (repairItems.isNotEmpty() && (totalPrice < 0.0 || repairItems.any { it.price < 0.0 })) -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please enter a valid repair price") }
                                    }
                                    else -> {
                                        val itemsList = if (repairItems.isNotEmpty()) {
                                            repairItems.map { it.repairType.trim() to it.price }
                                        } else {
                                            listOf(selectedRepairType.trim() to totalPrice)
                                        }
                                        val faultSummary = itemsList.joinToString(", ") { it.first }

                                        val doSave = {
                                            viewModel.createRepair(
                                                customerName = customerName,
                                                customerPhone = customerPhone,
                                                brand = selectedBrand,
                                                model = selectedModel,
                                                imei = imei,
                                                deviceColour = deviceColour,
                                                accessories = selectedAccessories.joinToString(", "),
                                                fault = faultSummary,
                                                technicianNotes = technicianNotes,
                                                remarks = remarks,
                                                repairItems = itemsList,
                                                initialPaymentAmount = amountPaid,
                                                onSuccess = { created ->
                                                    coroutineScope.launch {
                                                        when (val check = viewModel.checkStockForRepair(created.id)) {
                                                            is StockCheckResult.Insufficient -> {
                                                                savedRepairWithWarning = created
                                                                insufficientStockItems = check.items
                                                            }
                                                            is StockCheckResult.Ok -> {
                                                                onRepairCreated(created)
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }

                                        val hasSmsPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.SEND_SMS
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (settings.autoRegistrationSms && !hasSmsPermission) {
                                            pendingSaveAction = doSave
                                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                        } else {
                                            doSave()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("save_repair_button")
                        ) {
                            Text(
                                "SAVE REPAIR",
                                color = Color(0xFF090E1A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. CUSTOMER DETAILS (Phone Number First!)
            item {
                SectionCard(title = "1. CUSTOMER DETAILS") {
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Customer Phone Number *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { safeRequestFocus(nameFocusRequester) }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(phoneFocusRequester)
                            .testTag("input_customer_phone"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (settings.autoRegistrationSms) {
                        Text(
                            text = "💬 Customer will receive confirmation SMS automatically on save",
                            fontSize = 11.sp,
                            color = CyanAccent,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                        )
                    }

                    // PROMINENT PREVIOUS OUTSTANDING BALANCE WARNING if unpaid delivered jobs exist
                    if (previousCreditJobs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = PaymentPartial.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, PaymentPartial),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("previous_balance_warning_banner")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = PaymentPartial,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "OUTSTANDING CREDIT BALANCE WARNING",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = PaymentPartial
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Customer has ${settings.currency} ${String.format(Locale.US, "%,.0f", previousCreditBalance)} unpaid balance across ${previousCreditJobs.size} delivered ${if (previousCreditJobs.size == 1) "job" else "jobs"}:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                previousCreditJobs.forEach { unpaidJob ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clickable { onNavigateToRepairDetails(unpaidJob.id) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Job #${unpaidJob.jobNumber} · ${unpaidJob.brand} ${unpaidJob.model}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Total: ${settings.currency} ${String.format(Locale.US, "%,.0f", unpaidJob.totalPrice)} · Paid: ${settings.currency} ${String.format(Locale.US, "%,.0f", unpaidJob.amountPaid)}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "Due: ${settings.currency} ${String.format(Locale.US, "%,.0f", unpaidJob.balance)}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PaymentPartial
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { onNavigateToCustomerHistory(customerPhone) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                                        border = BorderStroke(1.dp, CyanAccent)
                                    ) {
                                        Text("View History & Pay Balance", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (hasPreviousHistory && previousRepairsCount > 0) {
                        // Returning customer without unpaid debts
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = CyanAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToCustomerHistory(customerPhone) }
                                .testTag("compact_previous_customer_row")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Returning Customer: ${previousCustomerName.ifBlank { "Customer" }} · $previousRepairsCount Previous ${if (previousRepairsCount == 1) "Repair" else "Repairs"} (All Paid)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CyanAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Open History",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer Name *") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { safeRequestFocus(brandFocusRequester) }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester)
                            .testTag("input_customer_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            // 2. DEVICE DETAILS
            item {
                SectionCard(title = "2. DEVICE DETAILS") {
                    // Brand Autocomplete
                    SmartAutocompleteField(
                        label = "Brand *",
                        value = selectedBrand,
                        onValueChange = { selectedBrand = it },
                        suggestions = allBrands.map { it.name },
                        onSuggestionSelected = { brand ->
                            selectedBrand = brand
                            safeRequestFocus(modelFocusRequester)
                        },
                        onAddNew = { newBrand ->
                            viewModel.addBrand(newBrand)
                            safeRequestFocus(modelFocusRequester)
                        },
                        focusRequester = brandFocusRequester,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { safeRequestFocus(modelFocusRequester) }
                        ),
                        placeholder = "e.g. Samsung, Apple, Xiaomi",
                        testTag = "input_brand_autocomplete"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Model Autocomplete (Filtered by selected Brand)
                    SmartAutocompleteField(
                        label = "Model *",
                        value = selectedModel,
                        onValueChange = { selectedModel = it },
                        suggestions = brandModels,
                        onSuggestionSelected = { model ->
                            selectedModel = model
                            safeRequestFocus(repairTypeFocusRequester)
                        },
                        onAddNew = { newModel ->
                            if (selectedBrand.isNotBlank()) {
                                viewModel.addModel(selectedBrand, newModel)
                            }
                            safeRequestFocus(repairTypeFocusRequester)
                        },
                        focusRequester = modelFocusRequester,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { safeRequestFocus(repairTypeFocusRequester) }
                        ),
                        placeholder = if (selectedBrand.isBlank()) "Select Brand first" else "e.g. Galaxy M02, iPhone 11",
                        testTag = "input_model_autocomplete"
                    )
                }
            }

            // 3. REPAIR ITEMS (Supports Multiple Repair Types under ONE Job Number)
            item {
                SectionCard(title = "3. REPAIR ITEMS") {
                    if (repairItems.isEmpty()) {
                        // First repair item input flow
                        SmartAutocompleteField(
                            label = "Repair Type / Service *",
                            value = selectedRepairType,
                            onValueChange = { selectedRepairType = it },
                            suggestions = availableSuggestions,
                            onSuggestionSelected = { type ->
                                selectedRepairType = type
                                safeRequestFocus(repairPriceFocusRequester)
                            },
                            onAddNew = { newType ->
                                viewModel.addRepairType(newType)
                                safeRequestFocus(repairPriceFocusRequester)
                            },
                            focusRequester = repairTypeFocusRequester,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { safeRequestFocus(repairPriceFocusRequester) }
                            ),
                            placeholder = "e.g. Display Replacement, Charging Port",
                            testTag = "input_repair_type_autocomplete"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Price Not Set Warning
                        if (priceNotSetMessage && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && selectedRepairType.isNotBlank()) {
                            Surface(
                                color = PaymentPartial.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = PaymentPartial, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Price not set for this model and repair type.",
                                        color = PaymentPartial,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = repairPriceStr,
                            onValueChange = { repairPriceStr = it },
                            label = { Text("Repair Price (${settings.currency}) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { safeRequestFocus(advancePaymentFocusRequester) }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(repairPriceFocusRequester)
                                .testTag("input_repair_price"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        // SAVE THIS PRICE button if price was not set
                        if (priceNotSetMessage && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && selectedRepairType.isNotBlank()) {
                            val enteredPrice = repairPriceStr.toDoubleOrNull()
                            if (enteredPrice != null && enteredPrice >= 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        showSaveMainPriceDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("save_this_price_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SAVE THIS PRICE FOR FUTURE JOBS", fontSize = 12.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // + Add Another Repair button (commits first item and opens add dialog)
                        OutlinedButton(
                            onClick = {
                                val p = repairPriceStr.toDoubleOrNull()
                                if (selectedRepairType.isBlank()) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Please select a repair type first") }
                                } else if (repairPriceStr.isBlank() || p == null || p < 0.0) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Please enter a valid price for this repair") }
                                } else {
                                    repairItems.add(RepairItemInput(repairType = selectedRepairType.trim(), price = p))
                                    selectedRepairType = ""
                                    repairPriceStr = ""
                                    priceNotSetMessage = false
                                    dialogRepairType = ""
                                    dialogPriceStr = ""
                                    dialogPriceNotSet = false
                                    showAddDialog = true
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("add_another_repair_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Add Another Repair", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 13.sp)
                        }
                    } else {
                        // Displays all committed repair items with Edit / Delete
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            repairItems.forEachIndexed { index, item ->
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${index + 1}. ${item.repairType}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${settings.currency} ${String.format(Locale.US, "%,.0f", item.price)}",
                                                color = CyanAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    itemToEdit = item
                                                    editRepairType = item.repairType
                                                    editPriceStr = if (item.price % 1.0 == 0.0) item.price.toInt().toString() else String.format(Locale.US, "%.2f", item.price)
                                                    editPriceNotSet = false
                                                },
                                                modifier = Modifier.size(32.dp).testTag("edit_item_${index}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent, modifier = Modifier.size(18.dp))
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            IconButton(
                                                onClick = {
                                                    repairItems.removeAt(index)
                                                },
                                                modifier = Modifier.size(32.dp).testTag("delete_item_${index}")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedButton(
                                onClick = {
                                    dialogRepairType = ""
                                    dialogPriceStr = ""
                                    dialogPriceNotSet = false
                                    showAddDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("add_another_repair_btn"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Add Another Repair", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 4. OPTIONAL ADDITIONAL DETAILS (IMEI, Colour, Accessories, Remarks)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        OutlinedButton(
                            onClick = { showMoreDetails = !showMoreDetails },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toggle_more_details_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                        ) {
                            Icon(
                                imageVector = if (showMoreDetails) Icons.Default.ExpandLess else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = CyanAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showMoreDetails) "− Hide Additional Details" else "+ More Details (IMEI, Colour, Accessories, Remarks)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = CyanAccent
                            )
                        }

                        AnimatedVisibility(visible = showMoreDetails) {
                            Column(modifier = Modifier.padding(top = 14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = imei,
                                        onValueChange = { imei = it },
                                        label = { Text("IMEI (Optional)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_imei"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyanAccent,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                    OutlinedTextField(
                                        value = deviceColour,
                                        onValueChange = { deviceColour = it },
                                        label = { Text("Device Colour") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_device_colour"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyanAccent,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Accessories Multi-select
                                AccessoriesSelector(
                                    selectedAccessories = selectedAccessories.toSet(),
                                    onToggleAccessory = { item ->
                                        if (selectedAccessories.contains(item)) {
                                            selectedAccessories.remove(item)
                                        } else {
                                            selectedAccessories.add(item)
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = remarks,
                                    onValueChange = { remarks = it },
                                    label = { Text("Customer Remarks") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_remarks"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyanAccent,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = technicianNotes,
                                    onValueChange = { technicianNotes = it },
                                    label = { Text("Technician Notes (Internal)") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_technician_notes"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyanAccent,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 5. PAYMENT DETAILS
            item {
                SectionCard(title = "4. PAYMENT DETAILS") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Repair Price:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${settings.currency} ${String.format(Locale.US, "%,.0f", totalPrice)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = amountPaidStr,
                        onValueChange = { amountPaidStr = it },
                        label = { Text("Advance / Deposit Paid (${settings.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(advancePaymentFocusRequester)
                            .testTag("input_advance_payment"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Remaining Balance (This Job):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${settings.currency} ${String.format(Locale.US, "%,.0f", balance)}",
                            fontWeight = FontWeight.Bold,
                            color = if (balance > 0) PaymentPartial else CyanAccent,
                            fontSize = 16.sp
                        )
                    }

                    if (previousCreditBalance > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Previous Unpaid Balance:",
                                color = PaymentPartial,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${settings.currency} ${String.format(Locale.US, "%,.0f", previousCreditBalance)}",
                                fontWeight = FontWeight.Bold,
                                color = PaymentPartial,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Total Customer Outstanding:",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${settings.currency} ${String.format(Locale.US, "%,.0f", balance + previousCreditBalance)}",
                                fontWeight = FontWeight.ExtraBold,
                                color = PaymentPartial,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // DIALOG: ADD ANOTHER REPAIR
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "Add Another Repair",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (selectedBrand.isNotBlank() && selectedModel.isNotBlank()) {
                        Text(
                            text = "Device: $selectedBrand $selectedModel",
                            fontSize = 12.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    SmartAutocompleteField(
                        label = "Repair Type / Service *",
                        value = dialogRepairType,
                        onValueChange = { dialogRepairType = it },
                        suggestions = availableSuggestions,
                        onSuggestionSelected = { dialogRepairType = it },
                        onAddNew = { viewModel.addRepairType(it) },
                        placeholder = "e.g. Battery, Charging Port",
                        testTag = "dialog_add_repair_type_input"
                    )

                    if (dialogPriceNotSet && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && dialogRepairType.isNotBlank()) {
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
                        value = dialogPriceStr,
                        onValueChange = { dialogPriceStr = it },
                        label = { Text("Price (${settings.currency}) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_add_price_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveDialogPriceForFuture = !saveDialogPriceForFuture }
                            .padding(vertical = 4.dp)
                            .testTag("dialog_add_save_price_checkbox_row")
                    ) {
                        Checkbox(
                            checked = saveDialogPriceForFuture,
                            onCheckedChange = { saveDialogPriceForFuture = it },
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

                    if (saveDialogPriceForFuture) {
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
                                    .clickable { dialogSavePriceTarget = "MODEL" }
                                    .testTag("dialog_add_save_model_row")
                            ) {
                                RadioButton(
                                    selected = dialogSavePriceTarget == "MODEL",
                                    onClick = { dialogSavePriceTarget = "MODEL" },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyanAccent),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedBrand.isNotBlank() && selectedModel.isNotBlank())
                                        "For $selectedBrand $selectedModel only"
                                    else "For this phone model only",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { dialogSavePriceTarget = "UNIVERSAL" }
                                    .testTag("dialog_add_save_universal_row")
                            ) {
                                RadioButton(
                                    selected = dialogSavePriceTarget == "UNIVERSAL",
                                    onClick = { dialogSavePriceTarget = "UNIVERSAL" },
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
                        val p = dialogPriceStr.toDoubleOrNull()
                        if (dialogRepairType.isNotBlank() && dialogPriceStr.isNotBlank() && p != null && p >= 0.0) {
                            val cleanType = dialogRepairType.trim()
                            viewModel.addRepairType(cleanType)
                            repairItems.add(RepairItemInput(repairType = cleanType, price = p))
                            if (saveDialogPriceForFuture) {
                                if (dialogSavePriceTarget == "UNIVERSAL") {
                                    viewModel.saveUniversalRepairType(cleanType, p)
                                } else if (selectedBrand.isNotBlank() && selectedModel.isNotBlank()) {
                                    viewModel.savePrice(selectedBrand.trim(), selectedModel.trim(), cleanType, p)
                                }
                            }
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("dialog_confirm_add_repair")
                ) {
                    Text("Add Repair", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: EDIT REPAIR ITEM
    if (itemToEdit != null) {
        AlertDialog(
            onDismissRequest = { itemToEdit = null },
            title = {
                Text(
                    text = "Edit Repair Item",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmartAutocompleteField(
                        label = "Repair Type / Service",
                        value = editRepairType,
                        onValueChange = { editRepairType = it },
                        suggestions = availableSuggestions,
                        onSuggestionSelected = { editRepairType = it },
                        onAddNew = { viewModel.addRepairType(it) },
                        testTag = "dialog_edit_repair_type_input"
                    )

                    if (editPriceNotSet && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && editRepairType.isNotBlank()) {
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
                        value = editPriceStr,
                        onValueChange = { editPriceStr = it },
                        label = { Text("Price (${settings.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_edit_price_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveEditPriceForFuture = !saveEditPriceForFuture }
                            .padding(vertical = 4.dp)
                            .testTag("dialog_edit_save_price_checkbox_row")
                    ) {
                        Checkbox(
                            checked = saveEditPriceForFuture,
                            onCheckedChange = { saveEditPriceForFuture = it },
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

                    if (saveEditPriceForFuture) {
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
                                    .testTag("dialog_edit_save_model_row")
                            ) {
                                RadioButton(
                                    selected = editSavePriceTarget == "MODEL",
                                    onClick = { editSavePriceTarget = "MODEL" },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyanAccent),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedBrand.isNotBlank() && selectedModel.isNotBlank())
                                        "For $selectedBrand $selectedModel only"
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
                                    .testTag("dialog_edit_save_universal_row")
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
                        val p = editPriceStr.toDoubleOrNull()
                        if (editRepairType.isNotBlank() && editPriceStr.isNotBlank() && p != null && p >= 0.0) {
                            val cleanType = editRepairType.trim()
                            viewModel.addRepairType(cleanType)
                            val idx = repairItems.indexOfFirst { it.id == itemToEdit?.id }
                            if (idx != -1) {
                                repairItems[idx] = repairItems[idx].copy(repairType = cleanType, price = p)
                            }
                            if (saveEditPriceForFuture) {
                                if (editSavePriceTarget == "UNIVERSAL") {
                                    viewModel.saveUniversalRepairType(cleanType, p)
                                } else if (selectedBrand.isNotBlank() && selectedModel.isNotBlank()) {
                                    viewModel.savePrice(selectedBrand.trim(), selectedModel.trim(), cleanType, p)
                                }
                            }
                            itemToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("dialog_confirm_edit_repair")
                ) {
                    Text("Save Changes", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: CHOOSE SAVE PRICE TARGET FOR MAIN REPAIR ITEM
    if (showSaveMainPriceDialog) {
        val enteredPrice = repairPriceStr.toDoubleOrNull() ?: 0.0
        val pFormatted = if (enteredPrice % 1.0 == 0.0) enteredPrice.toInt().toString() else String.format(Locale.US, "%.2f", enteredPrice)
        AlertDialog(
            onDismissRequest = { showSaveMainPriceDialog = false },
            title = { Text("Save Price for Future Jobs", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose how to save ${settings.currency} $pFormatted for '$selectedRepairType':",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveMainPriceTarget = "MODEL" }
                            .padding(vertical = 4.dp)
                            .testTag("save_choice_model_row")
                    ) {
                        RadioButton(
                            selected = saveMainPriceTarget == "MODEL",
                            onClick = { saveMainPriceTarget = "MODEL" },
                            colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("For $selectedBrand $selectedModel only", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Only auto-fills for this specific phone model", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveMainPriceTarget = "UNIVERSAL" }
                            .padding(vertical = 4.dp)
                            .testTag("save_choice_universal_row")
                    ) {
                        RadioButton(
                            selected = saveMainPriceTarget == "UNIVERSAL",
                            onClick = { saveMainPriceTarget = "UNIVERSAL" },
                            colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("As Universal default (all phone models)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Auto-fills for all brands & models unless overridden", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = repairPriceStr.toDoubleOrNull()
                        if (p != null && p >= 0.0 && selectedRepairType.isNotBlank()) {
                            if (saveMainPriceTarget == "UNIVERSAL") {
                                viewModel.saveUniversalRepairType(selectedRepairType.trim(), p) {
                                    priceNotSetMessage = false
                                    showSaveMainPriceDialog = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Saved as Universal default price for all models!")
                                    }
                                }
                            } else {
                                viewModel.savePrice(selectedBrand.trim(), selectedModel.trim(), selectedRepairType.trim(), p) {
                                    priceNotSetMessage = false
                                    showSaveMainPriceDialog = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Price saved for $selectedBrand $selectedModel!")
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("confirm_save_price_target_btn")
                ) {
                    Text("Save", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveMainPriceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // INSUFFICIENT STOCK WARNING DIALOG FOR SAVED REPAIR JOB
    if (insufficientStockItems != null && savedRepairWithWarning != null) {
        val repair = savedRepairWithWarning!!
        val itemsList = insufficientStockItems ?: emptyList()
        AlertDialog(
            onDismissRequest = {
                val targetRepair = repair
                insufficientStockItems = null
                savedRepairWithWarning = null
                onRepairCreated(targetRepair)
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Insufficient Stock Warning",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("insufficient_stock_dialog_title")
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Repair Job #${repair.jobNumber} was saved successfully, but the required stock is unavailable:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    itemsList.forEach { info ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("insufficient_stock_card_${info.stockItem.id}")
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
                        text = "The repair job has been saved and will remain pending until the required stock is replenished. Stock has not been deducted.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetRepair = repair
                        insufficientStockItems = null
                        savedRepairWithWarning = null
                        onRepairCreated(targetRepair)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("close_insufficient_stock_btn")
                ) {
                    Text(
                        "View Saved Job",
                        color = Color(0xFF090E1A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = CyanAccent,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

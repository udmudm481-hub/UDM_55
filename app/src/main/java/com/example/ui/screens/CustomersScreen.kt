package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.repository.UpdateCustomerResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.CustomerEntity
import com.example.data.entity.RepairEntity
import com.example.ui.components.SearchInputField
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPartial
import com.example.ui.util.PhoneActions
import com.example.ui.viewmodel.RepairViewModel
import java.util.Locale

@Composable
fun CustomersScreen(
    viewModel: RepairViewModel,
    onSelectCustomer: (String) -> Unit
) {
    val customers by viewModel.allCustomers.collectAsState()
    val allRepairs by viewModel.allRepairs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var filterOnlyCredit by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var deletingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    val customersWithCreditCount = remember(customers, allRepairs) {
        customers.count { cust ->
            allRepairs.any { it.customerPhone == cust.phone && it.status == "DELIVERED" && it.balance > 0.0 }
        }
    }

    val filteredCustomers = remember(customers, searchQuery, filterOnlyCredit, allRepairs) {
        val base = if (searchQuery.isBlank()) customers
        else customers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery, ignoreCase = true)
        }

        if (filterOnlyCredit) {
            base.filter { cust ->
                allRepairs.any { it.customerPhone == cust.phone && it.status == "DELIVERED" && it.balance > 0.0 }
            }
        } else {
            base
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = "CUSTOMERS DIRECTORY",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = CyanAccent,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SearchInputField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholderText = "Search by customer name or phone...",
            testTag = "customers_search_input"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips: All vs Credit Due
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !filterOnlyCredit,
                onClick = { filterOnlyCredit = false },
                label = { Text("All (${customers.size})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                    selectedLabelColor = CyanAccent
                )
            )
            FilterChip(
                selected = filterOnlyCredit,
                onClick = { filterOnlyCredit = true },
                label = { Text("Credit Due (${customersWithCreditCount})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PaymentPartial.copy(alpha = 0.2f),
                    selectedLabelColor = PaymentPartial
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (filteredCustomers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (customers.isEmpty()) "No customers recorded yet" else "No matching customers found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredCustomers, key = { it.phone }) { customer ->
                    val customerRepairs = allRepairs.filter { it.customerPhone == customer.phone }
                    val totalSpent = customerRepairs.sumOf { it.amountPaid }
                    val totalDue = customerRepairs.sumOf { it.balance }
                    val creditRepairs = customerRepairs.filter { it.status == "DELIVERED" && it.balance > 0.0 }
                    val creditDue = creditRepairs.sumOf { it.balance }

                    CustomerCard(
                        customer = customer,
                        jobsCount = customerRepairs.size,
                        totalSpent = totalSpent,
                        totalDue = totalDue,
                        creditDue = creditDue,
                        currency = settings.currency,
                        onClick = { onSelectCustomer(customer.phone) },
                        onEdit = { editingCustomer = customer },
                        onDelete = { deletingCustomer = customer }
                    )
                }
            }
        }
    }

    if (editingCustomer != null) {
        EditCustomerDialog(
            customer = editingCustomer!!,
            viewModel = viewModel,
            onDismiss = { editingCustomer = null },
            onSuccess = { _, _ -> editingCustomer = null }
        )
    }

    if (deletingCustomer != null) {
        DeleteCustomerDialog(
            customer = deletingCustomer!!,
            allRepairs = allRepairs,
            currency = settings.currency,
            viewModel = viewModel,
            onDismiss = { deletingCustomer = null },
            onDeleted = { deletingCustomer = null }
        )
    }
}

@Composable
fun CustomerCard(
    customer: CustomerEntity,
    jobsCount: Int,
    totalSpent: Double,
    totalDue: Double,
    creditDue: Double,
    currency: String,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("customer_card_${customer.phone}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CyanAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = customer.phone,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "$jobsCount Jobs",
                        fontSize = 12.sp,
                        color = CyanAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (creditDue > 0) {
                        Text(
                            text = "Credit: $currency ${String.format(Locale.US, "%,.0f", creditDue)}",
                            fontSize = 12.sp,
                            color = PaymentPartial,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else if (totalDue > 0) {
                        Text(
                            text = "Due: $currency ${String.format(Locale.US, "%,.0f", totalDue)}",
                            fontSize = 12.sp,
                            color = PaymentPartial,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("edit_customer_${customer.phone}")
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Customer",
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_customer_${customer.phone}")
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Customer",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerHistoryScreen(
    customerPhone: String,
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRepairDetails: (Long) -> Unit
) {
    BackHandler { onNavigateBack() }

    var currentPhone by remember(customerPhone) { mutableStateOf(customerPhone) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val allRepairs by viewModel.allRepairs.collectAsState()
    val allCustomers by viewModel.allCustomers.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val customer = allCustomers.find { it.phone == currentPhone }
    val customerRepairs = allRepairs.filter { it.customerPhone == currentPhone }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.name ?: customerRepairs.firstOrNull()?.customerName ?: "Customer History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.testTag("edit_customer_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer Details", tint = CyanAccent)
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("delete_customer_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Customer", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { PhoneActions.callCustomer(context, currentPhone) }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = CyanAccent)
                    }
                    IconButton(onClick = { PhoneActions.openWhatsApp(context, currentPhone) }) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = Color(0xFF25D366))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Customer Header Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = customer?.name ?: customerRepairs.firstOrNull()?.customerName ?: "Customer",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(text = currentPhone, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showEditDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("edit_customer_header_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanAccent)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", fontSize = 13.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showDeleteDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("delete_customer_header_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val creditRepairs = customerRepairs.filter { it.status == "DELIVERED" && it.balance > 0.0 }
                        val creditDue = creditRepairs.sumOf { it.balance }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Jobs:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text("${customerRepairs.size}", fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Repair Value:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text("${settings.currency} ${String.format(Locale.US, "%,.0f", customerRepairs.sumOf { it.totalPrice })}", fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Balance Due:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(
                                "${settings.currency} ${String.format(Locale.US, "%,.0f", customerRepairs.sumOf { it.balance })}",
                                fontWeight = FontWeight.Bold,
                                color = if (customerRepairs.sumOf { it.balance } > 0) PaymentPartial else CyanAccent
                            )
                        }
                        if (creditDue > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Delivered Credit Owed:", color = PaymentPartial, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${settings.currency} ${String.format(Locale.US, "%,.0f", creditDue)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PaymentPartial,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "REPAIR HISTORY (${customerRepairs.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyanAccent
                )
            }

            items(customerRepairs) { repair ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (repair.status == "DELIVERED" && repair.balance > 0) PaymentPartial.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onNavigateToRepairDetails(repair.id) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = CyanAccent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "#${repair.jobNumber}",
                                    color = CyanAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (repair.status == "DELIVERED" && repair.balance > 0) {
                                    Surface(
                                        color = PaymentPartial.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "CREDIT DUE",
                                            color = PaymentPartial,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                StatusBadge(status = repair.status)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${repair.brand} ${repair.model}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = repair.fault,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Total: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.totalPrice)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Paid: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.amountPaid)}", fontSize = 12.sp, color = com.example.ui.theme.PaymentPaid, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Balance: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.balance)}",
                                fontSize = 12.sp,
                                color = if (repair.balance > 0) PaymentPartial else CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        val currentCustomer = customer ?: CustomerEntity(
            phone = currentPhone,
            name = customerRepairs.firstOrNull()?.customerName ?: "Customer"
        )
        EditCustomerDialog(
            customer = currentCustomer,
            viewModel = viewModel,
            onDismiss = { showEditDialog = false },
            onSuccess = { newPhone, _ ->
                showEditDialog = false
                currentPhone = newPhone
            }
        )
    }

    if (showDeleteDialog) {
        val currentCustomer = customer ?: CustomerEntity(
            phone = currentPhone,
            name = customerRepairs.firstOrNull()?.customerName ?: "Customer"
        )
        DeleteCustomerDialog(
            customer = currentCustomer,
            allRepairs = allRepairs,
            currency = settings.currency,
            viewModel = viewModel,
            onDismiss = { showDeleteDialog = false },
            onDeleted = {
                showDeleteDialog = false
                onNavigateBack()
            }
        )
    }
}

@Composable
fun EditCustomerDialog(
    customer: CustomerEntity,
    viewModel: RepairViewModel,
    onDismiss: () -> Unit,
    onSuccess: (newPhone: String, newName: String) -> Unit
) {
    var editName by remember { mutableStateOf(customer.name) }
    var editPhone by remember { mutableStateOf(customer.phone) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var conflictWarning by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit Customer Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Update the customer's name and contact number. All linked repair records and balances will be preserved.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Conflict warning banner
                if (conflictWarning != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_customer_error_message")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = conflictWarning!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Customer Name Input
                OutlinedTextField(
                    value = editName,
                    onValueChange = {
                        editName = it
                        nameError = null
                    },
                    label = { Text("Customer Name *") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = CyanAccent)
                    },
                    isError = nameError != null,
                    supportingText = {
                        if (nameError != null) {
                            Text(nameError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_customer_name_input")
                )

                // Customer Phone Input
                OutlinedTextField(
                    value = editPhone,
                    onValueChange = {
                        editPhone = it
                        phoneError = null
                        conflictWarning = null
                    },
                    label = { Text("Phone Number *") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = CyanAccent)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phoneError != null,
                    supportingText = {
                        if (phoneError != null) {
                            Text(phoneError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_customer_phone_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = editName.trim()
                    val trimmedPhone = editPhone.trim()
                    var hasError = false

                    if (trimmedName.isBlank()) {
                        nameError = "Please enter customer name"
                        hasError = true
                    }
                    if (trimmedPhone.isBlank()) {
                        phoneError = "Please enter customer phone number"
                        hasError = true
                    } else if (trimmedPhone.filter { it.isDigit() }.length < 3) {
                        phoneError = "Phone number must contain at least 3 digits"
                        hasError = true
                    }

                    if (hasError) return@Button

                    isSaving = true
                    viewModel.updateCustomer(customer.phone, trimmedPhone, trimmedName) { result ->
                        isSaving = false
                        when (result) {
                            is UpdateCustomerResult.Success -> {
                                onSuccess(trimmedPhone, trimmedName)
                            }
                            is UpdateCustomerResult.PhoneConflict -> {
                                conflictWarning = "Cannot change phone number: '$trimmedPhone' is already registered to ${result.existingCustomerName}. Please use a different phone number."
                            }
                            is UpdateCustomerResult.ValidationError -> {
                                phoneError = result.message
                            }
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                modifier = Modifier.testTag("edit_customer_save_button")
            ) {
                Text(
                    text = if (isSaving) "Saving..." else "Save Changes",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.testTag("edit_customer_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteCustomerDialog(
    customer: CustomerEntity,
    allRepairs: List<RepairEntity>,
    currency: String,
    viewModel: RepairViewModel,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val customerRepairs = remember(customer.phone, allRepairs) {
        val clean = customer.phone.filter { it.isDigit() }
        allRepairs.filter { repair ->
            val repClean = repair.customerPhone.filter { it.isDigit() }
            repair.customerPhone == customer.phone || (clean.isNotEmpty() && repClean == clean)
        }
    }
    val jobsCount = customerRepairs.size
    val totalPaid = customerRepairs.sumOf { it.amountPaid }
    val totalBalance = customerRepairs.sumOf { it.balance }
    val hasHistory = jobsCount > 0 || totalPaid > 0 || totalBalance > 0
    var isDeleting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        modifier = Modifier.testTag("delete_customer_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (hasHistory) Icons.Default.Warning else Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Customer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Customer details box showing Name and Phone Number
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Customer Name:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = customer.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Phone Number:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = customer.phone, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        }
                    }
                }

                if (hasHistory) {
                    // Warning section for existing repairs / payments / debt
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_customer_warning_banner")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Existing Records Found",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = buildString {
                                    append("This customer has $jobsCount repair ${if (jobsCount == 1) "job" else "jobs"}")
                                    if (totalPaid > 0) {
                                        append(", $currency ${String.format(Locale.US, "%,.0f", totalPaid)} total payments")
                                    }
                                    if (totalBalance > 0) {
                                        append(", and an outstanding balance of $currency ${String.format(Locale.US, "%,.0f", totalBalance)}")
                                    }
                                    append(".")
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = "DATA SAFETY GUARANTEE: Only the customer entry will be removed from the Customers directory. All historical repair jobs, payments, outstanding balances, and job numbers will remain safely preserved in the system.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                } else {
                    Text(
                        text = "Are you sure you want to remove this customer from the Customers directory? Cancel will leave all data unchanged.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isDeleting = true
                    viewModel.deleteCustomer(customer.phone) {
                        isDeleting = false
                        onDeleted()
                    }
                },
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_customer_button")
            ) {
                Text(
                    text = if (isDeleting) "Deleting..." else "Delete",
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier.testTag("cancel_delete_customer_button")
            ) {
                Text("Cancel")
            }
        }
    )
}


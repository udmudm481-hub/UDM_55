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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.StockItemEntity
import com.example.data.entity.StockMovementEntity
import com.example.data.entity.StockRepairConnectionEntity
import com.example.ui.components.SearchInputField
import com.example.ui.components.SmartAutocompleteField
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.PaymentPartial
import com.example.ui.theme.StatusCancelled
import com.example.ui.theme.StatusChecking
import com.example.ui.viewmodel.RepairViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockManagementScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val stockItems by viewModel.allStockItems.collectAsState()
    val connections by viewModel.allStockConnections.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val allBrands by viewModel.allBrands.collectAsState()
    val allRepairTypes by viewModel.allRepairTypes.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterLowStockOnly by remember { mutableStateOf(false) }

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<StockItemEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<StockItemEntity?>(null) }
    var selectedItemDetail by remember { mutableStateOf<StockItemEntity?>(null) }

    // Quick stock actions
    var stockActionItem by remember { mutableStateOf<StockItemEntity?>(null) }
    var stockActionMode by remember { mutableStateOf<StockActionMode?>(null) } // ADD, REMOVE, CORRECT

    val filteredItems = remember(stockItems, searchQuery, filterLowStockOnly) {
        stockItems.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.supplier.contains(searchQuery, ignoreCase = true) ||
                item.notes.contains(searchQuery, ignoreCase = true)
            val matchesFilter = !filterLowStockOnly || (item.quantity <= item.lowStockWarning)
            matchesSearch && matchesFilter
        }
    }

    val lowStockCount = remember(stockItems) {
        stockItems.count { it.quantity <= it.lowStockWarning }
    }
    val totalUnits = remember(stockItems) {
        stockItems.sumOf { it.quantity }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stock Management", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "${stockItems.size} items • $totalUnits units in stock",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("stock_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = CyanAccent,
                modifier = Modifier.testTag("add_stock_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Stock Item", tint = Color(0xFF090E1A))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Metrics Summary Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    label = "Total Items",
                    value = stockItems.size.toString(),
                    color = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "Total Units",
                    value = totalUnits.toString(),
                    color = PaymentPaid,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "Low Stock Alert",
                    value = lowStockCount.toString(),
                    color = if (lowStockCount > 0) StatusCancelled else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            // Search Bar & Low Stock Filter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchInputField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholderText = "Search stock items by name...",
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "stock_search_input"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !filterLowStockOnly,
                        onClick = { filterLowStockOnly = false },
                        label = { Text("All Items (${stockItems.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                            selectedLabelColor = CyanAccent
                        ),
                        modifier = Modifier.testTag("filter_all_stock")
                    )

                    FilterChip(
                        selected = filterLowStockOnly,
                        onClick = { filterLowStockOnly = true },
                        label = { Text("Low Stock ($lowStockCount)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StatusCancelled.copy(alpha = 0.2f),
                            selectedLabelColor = StatusCancelled
                        ),
                        leadingIcon = if (lowStockCount > 0) {
                            { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusCancelled, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.testTag("filter_low_stock")
                    )
                }
            }

            // Stock Items List
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || filterLowStockOnly) "No matching stock items found" else "No physical stock items yet",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || filterLowStockOnly) "Try clearing search or filters" else "Tap '+' to create your first stock item",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val itemConnections = connections.filter { it.stockItemId == item.id }
                        val isLowStock = item.quantity <= item.lowStockWarning

                        StockItemCard(
                            item = item,
                            currency = settings.currency,
                            connectionsCount = itemConnections.size,
                            isLowStock = isLowStock,
                            onClick = { selectedItemDetail = item },
                            onAddStock = {
                                stockActionItem = item
                                stockActionMode = StockActionMode.ADD
                            },
                            onRemoveStock = {
                                stockActionItem = item
                                stockActionMode = StockActionMode.REMOVE
                            },
                            onCorrectStock = {
                                stockActionItem = item
                                stockActionMode = StockActionMode.CORRECT
                            },
                            onEdit = { itemToEdit = item },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // CREATE / EDIT STOCK ITEM DIALOG
    if (showCreateDialog || itemToEdit != null) {
        val editing = itemToEdit
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var quantityStr by remember { mutableStateOf(editing?.quantity?.toString() ?: "10") }
        var purchasePriceStr by remember { mutableStateOf(editing?.let { if (it.purchasePrice > 0) it.purchasePrice.toString() else "" } ?: "") }
        var sellingPriceStr by remember { mutableStateOf(editing?.let { if (it.sellingPrice > 0) it.sellingPrice.toString() else "" } ?: "") }
        var supplier by remember { mutableStateOf(editing?.supplier ?: "") }
        var notes by remember { mutableStateOf(editing?.notes ?: "") }
        var lowStockWarningStr by remember { mutableStateOf(editing?.lowStockWarning?.toString() ?: "2") }

        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                itemToEdit = null
            },
            title = {
                Text(if (editing == null) "New Stock Item" else "Edit Stock Item", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Stock Item Name *") },
                        placeholder = { Text("e.g. T2 Charging Pin, iPhone X Battery") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("stock_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it },
                            label = { Text("Quantity *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("stock_qty_input")
                        )

                        OutlinedTextField(
                            value = lowStockWarningStr,
                            onValueChange = { lowStockWarningStr = it },
                            label = { Text("Low Stock Alert *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("stock_warning_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it },
                            label = { Text("Buy Price (${settings.currency})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = sellingPriceStr,
                            onValueChange = { sellingPriceStr = it },
                            label = { Text("Sell Price (${settings.currency})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = { Text("Supplier (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = quantityStr.toIntOrNull() ?: 0
                        val warning = lowStockWarningStr.toIntOrNull() ?: 2
                        val buyPrice = purchasePriceStr.toDoubleOrNull() ?: 0.0
                        val sellPrice = sellingPriceStr.toDoubleOrNull() ?: 0.0

                        if (name.isNotBlank()) {
                            if (editing == null) {
                                viewModel.createStockItem(
                                    name = name,
                                    quantity = qty,
                                    purchasePrice = buyPrice,
                                    sellingPrice = sellPrice,
                                    supplier = supplier,
                                    notes = notes,
                                    lowStockWarning = warning
                                ) {
                                    showCreateDialog = false
                                }
                            } else {
                                val updated = editing.copy(
                                    name = name.trim(),
                                    quantity = qty,
                                    purchasePrice = buyPrice,
                                    sellingPrice = sellPrice,
                                    supplier = supplier.trim(),
                                    notes = notes.trim(),
                                    lowStockWarning = warning
                                )
                                viewModel.updateStockItem(updated) {
                                    itemToEdit = null
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("save_stock_btn")
                ) {
                    Text("Save", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        itemToEdit = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // DELETE STOCK ITEM CONFIRMATION
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Stock Item?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete '${target.name}'? Existing repair job records and past job history will NOT be modified, but connected repair type links and stock movement records for this item will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStockItem(target) {
                            itemToDelete = null
                            if (selectedItemDetail?.id == target.id) {
                                selectedItemDetail = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_stock_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // QUICK STOCK MOVEMENT DIALOG (ADD, REMOVE, CORRECT)
    if (stockActionItem != null && stockActionMode != null) {
        val item = stockActionItem!!
        val mode = stockActionMode!!
        var quantityInput by remember { mutableStateOf(if (mode == StockActionMode.CORRECT) item.quantity.toString() else "1") }
        var reasonInput by remember {
            mutableStateOf(
                when (mode) {
                    StockActionMode.ADD -> "Shipment received"
                    StockActionMode.REMOVE -> "Damaged / discarded"
                    StockActionMode.CORRECT -> "Inventory audit"
                }
            )
        }

        AlertDialog(
            onDismissRequest = {
                stockActionItem = null
                stockActionMode = null
            },
            title = {
                Text(
                    text = when (mode) {
                        StockActionMode.ADD -> "Add Stock: ${item.name}"
                        StockActionMode.REMOVE -> "Remove Stock: ${item.name}"
                        StockActionMode.CORRECT -> "Correct Stock: ${item.name}"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Current quantity: ${item.quantity}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it },
                        label = {
                            Text(
                                when (mode) {
                                    StockActionMode.ADD -> "Quantity to Add"
                                    StockActionMode.REMOVE -> "Quantity to Remove"
                                    StockActionMode.CORRECT -> "New Exact Quantity"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("stock_action_qty_input")
                    )

                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Reason for Movement") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("stock_action_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = quantityInput.toIntOrNull()
                        if (qty != null && qty >= 0) {
                            when (mode) {
                                StockActionMode.ADD -> {
                                    if (qty > 0) {
                                        viewModel.addStock(item.id, qty, reasonInput) {
                                            stockActionItem = null
                                            stockActionMode = null
                                        }
                                    }
                                }
                                StockActionMode.REMOVE -> {
                                    if (qty > 0) {
                                        viewModel.removeStock(item.id, qty, reasonInput) {
                                            stockActionItem = null
                                            stockActionMode = null
                                        }
                                    }
                                }
                                StockActionMode.CORRECT -> {
                                    viewModel.correctStockQuantity(item.id, qty, reasonInput) {
                                        stockActionItem = null
                                        stockActionMode = null
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (mode) {
                            StockActionMode.ADD -> CyanAccent
                            StockActionMode.REMOVE -> MaterialTheme.colorScheme.error
                            StockActionMode.CORRECT -> StatusChecking
                        }
                    ),
                    modifier = Modifier.testTag("stock_action_confirm_btn")
                ) {
                    Text(
                        text = when (mode) {
                            StockActionMode.ADD -> "Add"
                            StockActionMode.REMOVE -> "Remove"
                            StockActionMode.CORRECT -> "Save Correction"
                        },
                        color = if (mode == StockActionMode.REMOVE) Color.White else Color(0xFF090E1A),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        stockActionItem = null
                        stockActionMode = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // FULL STOCK ITEM DETAIL DIALOG (Connections + History)
    if (selectedItemDetail != null) {
        val currentItem = stockItems.find { it.id == selectedItemDetail!!.id } ?: selectedItemDetail!!
        StockItemDetailDialog(
            item = currentItem,
            currency = settings.currency,
            viewModel = viewModel,
            allBrands = allBrands.map { it.name },
            allRepairTypes = allRepairTypes.map { it.name },
            onDismiss = { selectedItemDetail = null },
            onEdit = {
                itemToEdit = currentItem
            },
            onAddStock = {
                stockActionItem = currentItem
                stockActionMode = StockActionMode.ADD
            },
            onRemoveStock = {
                stockActionItem = currentItem
                stockActionMode = StockActionMode.REMOVE
            },
            onCorrectStock = {
                stockActionItem = currentItem
                stockActionMode = StockActionMode.CORRECT
            }
        )
    }
}

enum class StockActionMode {
    ADD, REMOVE, CORRECT
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StockItemCard(
    item: StockItemEntity,
    currency: String,
    connectionsCount: Int,
    isLowStock: Boolean,
    onClick: () -> Unit,
    onAddStock: () -> Unit,
    onRemoveStock: () -> Unit,
    onCorrectStock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isLowStock) StatusCancelled else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .testTag("stock_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (item.supplier.isNotBlank()) {
                        Text(
                            text = "Supplier: ${item.supplier}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quantity badge
                Surface(
                    color = if (isLowStock) StatusCancelled.copy(alpha = 0.18f) else PaymentPaid.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLowStock) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusCancelled, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "${item.quantity} in stock",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = if (isLowStock) StatusCancelled else PaymentPaid
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub info row: Warning level & Connected repairs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Alert at ≤ ${item.lowStockWarning}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = CyanAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "$connectionsCount Connected Repair${if (connectionsCount == 1) "" else "s"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (item.purchasePrice > 0 || item.sellingPrice > 0) {
                    Text(
                        text = buildString {
                            if (item.purchasePrice > 0) append("Buy: $currency ${String.format(Locale.US, "%,.0f", item.purchasePrice)} ")
                            if (item.sellingPrice > 0) append("Sell: $currency ${String.format(Locale.US, "%,.0f", item.sellingPrice)}")
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onAddStock,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("quick_add_stock_${item.id}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Stock", fontSize = 11.sp, color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onRemoveStock,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = ButtonDefaults.outlinedButtonBorder,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("quick_remove_stock_${item.id}")
                    ) {
                        Text("- Stock", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onCorrectStock,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = ButtonDefaults.outlinedButtonBorder,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Audit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StockItemDetailDialog(
    item: StockItemEntity,
    currency: String,
    viewModel: RepairViewModel,
    allBrands: List<String>,
    allRepairTypes: List<String>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAddStock: () -> Unit,
    onRemoveStock: () -> Unit,
    onCorrectStock: () -> Unit
) {
    val movements by viewModel.getMovementsForStockItem(item.id).collectAsState(initial = emptyList())
    val connections by viewModel.getConnectionsForStockItem(item.id).collectAsState(initial = emptyList())

    var activeTab by remember { mutableIntStateOf(0) } // 0: Connected Repairs, 1: History
    var showAddConnectionDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Stock: ${item.quantity} units (Alert ≤ ${item.lowStockWarning})", fontSize = 12.sp, color = CyanAccent)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onAddStock,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Add", fontSize = 11.sp, color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRemoveStock,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("- Remove", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onCorrectStock,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusChecking),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Audit", fontSize = 11.sp, color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs for Connections vs History
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Connected Repairs (${connections.size})", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("History (${movements.size})", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.height(280.dp).fillMaxWidth()) {
                    if (activeTab == 0) {
                        // Connected Repairs Tab
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Connected Phone Models",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { showAddConnectionDialog = true },
                                    modifier = Modifier.testTag("add_connection_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Connect", fontSize = 11.sp, color = CyanAccent)
                                }
                            }

                            if (connections.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No repair types connected yet.\nConnect this stock item to phone models in Price Manager or tap 'Connect' above.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(connections, key = { it.id }) { conn ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    val modelText = if (conn.brand.isNotBlank() || conn.model.isNotBlank()) {
                                                        "${conn.brand} ${conn.model}".trim()
                                                    } else "Universal (All Models)"
                                                    Text(
                                                        text = modelText,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Repair Type: ${conn.repairType} • Qty used: ${conn.quantityUsed}",
                                                        fontSize = 11.sp,
                                                        color = CyanAccent
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteStockConnection(conn.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remove connection", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // History Tab
                        if (movements.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No movement history recorded yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(movements, key = { it.id }) { m ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = m.reason,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${m.date} ${m.time} • Balance: ${m.resultingQuantity}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Surface(
                                                color = if (m.quantityChange > 0) PaymentPaid.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (m.quantityChange > 0) "+${m.quantityChange}" else "${m.quantityChange}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (m.quantityChange > 0) PaymentPaid else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text("Close", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onEdit) {
                Text("Edit Item")
            }
        }
    )

    // ADD CONNECTION DIALOG
    if (showAddConnectionDialog) {
        var cBrand by remember { mutableStateOf("") }
        var cModel by remember { mutableStateOf("") }
        var cRepairType by remember { mutableStateOf("") }
        var cQtyUsedStr by remember { mutableStateOf("1") }
        var brandModels by remember { mutableStateOf<List<String>>(emptyList()) }

        AlertDialog(
            onDismissRequest = { showAddConnectionDialog = false },
            title = { Text("Connect Repair to ${item.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmartAutocompleteField(
                        label = "Brand",
                        value = cBrand,
                        onValueChange = { cBrand = it },
                        suggestions = allBrands,
                        onSuggestionSelected = { cBrand = it },
                        onAddNew = { viewModel.addBrand(it) }
                    )

                    OutlinedTextField(
                        value = cModel,
                        onValueChange = { cModel = it },
                        label = { Text("Model Name") },
                        placeholder = { Text("e.g. Nokia 105, Greentel Keypad") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("connect_model_input")
                    )

                    SmartAutocompleteField(
                        label = "Repair Type",
                        value = cRepairType,
                        onValueChange = { cRepairType = it },
                        suggestions = allRepairTypes,
                        onSuggestionSelected = { cRepairType = it },
                        onAddNew = { viewModel.addRepairType(it) }
                    )

                    OutlinedTextField(
                        value = cQtyUsedStr,
                        onValueChange = { cQtyUsedStr = it },
                        label = { Text("Quantity used per repair") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("connect_qty_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = cQtyUsedStr.toIntOrNull() ?: 1
                        if (cRepairType.isNotBlank()) {
                            viewModel.saveStockConnection(
                                stockItemId = item.id,
                                brand = cBrand,
                                model = cModel,
                                repairType = cRepairType,
                                quantityUsed = qty
                            ) {
                                showAddConnectionDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("confirm_add_connection_btn")
                ) {
                    Text("Connect", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddConnectionDialog = false }) { Text("Cancel") }
            }
        )
    }
}

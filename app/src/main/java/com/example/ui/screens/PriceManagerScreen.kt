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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.PriceEntity
import com.example.data.entity.UniversalRepairTypeEntity
import com.example.ui.components.SearchInputField
import com.example.ui.components.SmartAutocompleteField
import com.example.ui.theme.CyanAccent
import com.example.ui.viewmodel.RepairViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceManagerScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val prices by viewModel.allPrices.collectAsState()
    val universalRepairTypes by viewModel.allUniversalRepairTypes.collectAsState()
    val allBrands by viewModel.allBrands.collectAsState()
    val allRepairTypes by viewModel.allRepairTypes.collectAsState()
    val stockItems by viewModel.allStockItems.collectAsState()
    val stockConnections by viewModel.allStockConnections.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Model Prices, 1: Universal Repair Types
    var searchQuery by remember { mutableStateOf("") }
    var universalSearchQuery by remember { mutableStateOf("") }

    // Model Price Dialog States
    var showAddEditDialog by remember { mutableStateOf(false) }
    var priceToEdit by remember { mutableStateOf<PriceEntity?>(null) }
    var priceToDelete by remember { mutableStateOf<PriceEntity?>(null) }

    // Universal Repair Type Dialog States
    var showAddUniversalDialog by remember { mutableStateOf(false) }
    var universalToEdit by remember { mutableStateOf<UniversalRepairTypeEntity?>(null) }
    var universalToDelete by remember { mutableStateOf<UniversalRepairTypeEntity?>(null) }

    val filteredPrices = remember(prices, searchQuery) {
        if (searchQuery.isBlank()) prices
        else prices.filter {
            it.brand.contains(searchQuery, ignoreCase = true) ||
            it.model.contains(searchQuery, ignoreCase = true) ||
            it.repairType.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredUniversalTypes = remember(universalRepairTypes, universalSearchQuery) {
        if (universalSearchQuery.isBlank()) universalRepairTypes
        else universalRepairTypes.filter {
            it.name.contains(universalSearchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price Manager", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        priceToEdit = null
                        showAddEditDialog = true
                    } else {
                        universalToEdit = null
                        showAddUniversalDialog = true
                    }
                },
                containerColor = CyanAccent,
                modifier = Modifier.testTag(if (selectedTab == 0) "add_price_fab" else "add_universal_fab")
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (selectedTab == 0) "Add Model Price" else "Add Universal Repair Type",
                    tint = Color(0xFF090E1A)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = CyanAccent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Model Prices (${prices.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_model_prices")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Universal Repair Types (${universalRepairTypes.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_universal_repair_types")
                )
            }

            if (selectedTab == 0) {
                // MODEL PRICES TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    SearchInputField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholderText = "Search by Brand, Model, or Repair Type...",
                        testTag = "price_search_input"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${filteredPrices.size} model price records configured",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredPrices.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No model-specific prices found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredPrices, key = { it.id }) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                        .testTag("price_item_${item.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${item.brand} ${item.model}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = item.repairType,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${settings.currency} ${String.format(Locale.US, "%,.2f", item.price)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = CyanAccent
                                            )
                                            val connectedStock = stockConnections.find {
                                                it.brand.equals(item.brand, ignoreCase = true) &&
                                                it.model.equals(item.model, ignoreCase = true) &&
                                                it.repairType.equals(item.repairType, ignoreCase = true)
                                            } ?: stockConnections.find {
                                                it.brand.isBlank() && it.model.isBlank() &&
                                                it.repairType.equals(item.repairType, ignoreCase = true)
                                            }
                                            if (connectedStock != null) {
                                                val sItem = stockItems.find { it.id == connectedStock.stockItemId }
                                                if (sItem != null) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Surface(
                                                        color = CyanAccent.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Stock: ${sItem.name} (x${connectedStock.quantityUsed}) • ${sItem.quantity} left",
                                                                fontSize = 11.sp,
                                                                color = CyanAccent,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    priceToEdit = item
                                                    showAddEditDialog = true
                                                },
                                                modifier = Modifier.testTag("edit_price_${item.id}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent, modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(
                                                onClick = { priceToDelete = item },
                                                modifier = Modifier.testTag("delete_price_${item.id}")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // UNIVERSAL REPAIR TYPES TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Universal repair types have a fixed default price that automatically applies across all phone brands and models unless an exact model-specific price is configured.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SearchInputField(
                        value = universalSearchQuery,
                        onValueChange = { universalSearchQuery = it },
                        placeholderText = "Search Universal Repair Types...",
                        testTag = "universal_search_input"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${filteredUniversalTypes.size} universal repair types configured",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredUniversalTypes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No universal repair types found. Tap + to add one.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredUniversalTypes, key = { it.id }) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                        .testTag("universal_item_${item.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Surface(
                                                color = CyanAccent.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "UNIVERSAL • ALL MODELS",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CyanAccent,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "${settings.currency} ${String.format(Locale.US, "%,.2f", item.defaultPrice)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = CyanAccent
                                            )
                                            val connectedStock = stockConnections.find {
                                                it.brand.isBlank() && it.model.isBlank() &&
                                                it.repairType.equals(item.name, ignoreCase = true)
                                            }
                                            if (connectedStock != null) {
                                                val sItem = stockItems.find { it.id == connectedStock.stockItemId }
                                                if (sItem != null) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Surface(
                                                        color = CyanAccent.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Stock: ${sItem.name} (x${connectedStock.quantityUsed}) • ${sItem.quantity} left",
                                                                fontSize = 11.sp,
                                                                color = CyanAccent,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    universalToEdit = item
                                                    showAddUniversalDialog = true
                                                },
                                                modifier = Modifier.testTag("edit_universal_${item.id}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent, modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(
                                                onClick = { universalToDelete = item },
                                                modifier = Modifier.testTag("delete_universal_${item.id}")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
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
    }

    // ADD / EDIT MODEL PRICE DIALOG
    if (showAddEditDialog) {
        var dBrand by remember { mutableStateOf(priceToEdit?.brand ?: "") }
        var dModel by remember { mutableStateOf(priceToEdit?.model ?: "") }
        var dRepairType by remember { mutableStateOf(priceToEdit?.repairType ?: "") }
        var dPriceStr by remember { mutableStateOf(priceToEdit?.let { if (it.price % 1.0 == 0.0) it.price.toInt().toString() else String.format(Locale.US, "%.2f", it.price) } ?: "") }
        var brandModels by remember { mutableStateOf<List<String>>(emptyList()) }

        val existingConn = remember(priceToEdit, stockConnections) {
            priceToEdit?.let { p ->
                stockConnections.find {
                    it.brand.equals(p.brand, ignoreCase = true) &&
                    it.model.equals(p.model, ignoreCase = true) &&
                    it.repairType.equals(p.repairType, ignoreCase = true)
                }
            }
        }
        var usesStockItem by remember { mutableStateOf(existingConn != null) }
        var selectedStockItemId by remember { mutableStateOf<Long?>(existingConn?.stockItemId) }
        var stockQuantityUsedStr by remember { mutableStateOf((existingConn?.quantityUsed ?: 1).toString()) }
        var showStockDropdown by remember { mutableStateOf(false) }
        var showQuickCreateStockDialog by remember { mutableStateOf(false) }

        LaunchedEffect(dBrand) {
            if (dBrand.isNotBlank()) {
                viewModel.getModelsForBrand(dBrand).collect { list ->
                    brandModels = list.map { it.modelName }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(if (priceToEdit == null) "Add Model Price" else "Edit Model Price") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmartAutocompleteField(
                        label = "Brand",
                        value = dBrand,
                        onValueChange = { dBrand = it },
                        suggestions = allBrands.map { it.name },
                        onSuggestionSelected = { dBrand = it },
                        onAddNew = { viewModel.addBrand(it) }
                    )

                    SmartAutocompleteField(
                        label = "Model",
                        value = dModel,
                        onValueChange = { dModel = it },
                        suggestions = brandModels,
                        onSuggestionSelected = { dModel = it },
                        onAddNew = {
                            if (dBrand.isNotBlank()) viewModel.addModel(dBrand, it)
                        }
                    )

                    SmartAutocompleteField(
                        label = "Repair Type",
                        value = dRepairType,
                        onValueChange = { dRepairType = it },
                        suggestions = (allRepairTypes.map { it.name } + universalRepairTypes.map { it.name }).distinct(),
                        onSuggestionSelected = { dRepairType = it },
                        onAddNew = { viewModel.addRepairType(it) }
                    )

                    OutlinedTextField(
                        value = dPriceStr,
                        onValueChange = { dPriceStr = it },
                        label = { Text("Price (${settings.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("model_price_input")
                    )

                    // Stock Item Connection Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { usesStockItem = !usesStockItem },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = usesStockItem,
                                    onCheckedChange = { usesStockItem = it },
                                    colors = CheckboxDefaults.colors(checkedColor = CyanAccent),
                                    modifier = Modifier.testTag("uses_stock_item_checkbox")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("Uses Stock Item", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Auto-deduct stock upon repair completion", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (usesStockItem) {
                                val selectedItem = stockItems.find { it.id == selectedStockItemId }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Connected Stock Item:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    TextButton(
                                        onClick = { showQuickCreateStockDialog = true },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("create_new_stock_from_price_btn")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("+ New Stock", fontSize = 11.sp, color = CyanAccent)
                                    }
                                }

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedCard(
                                        onClick = { showStockDropdown = true },
                                        modifier = Modifier.fillMaxWidth().testTag("select_stock_item_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (selectedItem != null) "${selectedItem.name} (${selectedItem.quantity} in stock)" else "Select Stock Item...",
                                                color = if (selectedItem != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CyanAccent)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showStockDropdown,
                                        onDismissRequest = { showStockDropdown = false },
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        if (stockItems.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("No stock items found. Tap '+ New Stock'") },
                                                onClick = {
                                                    showStockDropdown = false
                                                    showQuickCreateStockDialog = true
                                                }
                                            )
                                        } else {
                                            stockItems.forEach { s ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(s.name, fontWeight = FontWeight.Medium)
                                                            Text("${s.quantity} units", color = if (s.quantity <= s.lowStockWarning) MaterialTheme.colorScheme.error else CyanAccent, fontSize = 12.sp)
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedStockItemId = s.id
                                                        showStockDropdown = false
                                                    },
                                                    modifier = Modifier.testTag("stock_item_option_${s.id}")
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = stockQuantityUsedStr,
                                    onValueChange = { stockQuantityUsedStr = it },
                                    label = { Text("Quantity used per repair") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("stock_quantity_used_input")
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = dPriceStr.toDoubleOrNull()
                        if (dBrand.isNotBlank() && dModel.isNotBlank() && dRepairType.isNotBlank() && p != null && p >= 0.0) {
                            viewModel.savePrice(dBrand, dModel, dRepairType, p) {
                                if (usesStockItem && selectedStockItemId != null) {
                                    val qty = stockQuantityUsedStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    viewModel.saveStockConnection(selectedStockItemId!!, dBrand, dModel, dRepairType, qty) {
                                        showAddEditDialog = false
                                    }
                                } else {
                                    viewModel.deleteStockConnectionForRepair(dBrand, dModel, dRepairType) {
                                        showAddEditDialog = false
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("save_model_price_btn")
                ) {
                    Text("Save", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )

        if (showQuickCreateStockDialog) {
            var newStockName by remember { mutableStateOf("") }
            var newStockQtyStr by remember { mutableStateOf("10") }
            var newStockLowWarningStr by remember { mutableStateOf("2") }
            AlertDialog(
                onDismissRequest = { showQuickCreateStockDialog = false },
                title = { Text("New Stock Item") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newStockName,
                            onValueChange = { newStockName = it },
                            label = { Text("Stock Item Name (e.g. T2 Charging Pin)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("quick_stock_name_input")
                        )
                        OutlinedTextField(
                            value = newStockQtyStr,
                            onValueChange = { newStockQtyStr = it },
                            label = { Text("Current Quantity") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("quick_stock_qty_input")
                        )
                        OutlinedTextField(
                            value = newStockLowWarningStr,
                            onValueChange = { newStockLowWarningStr = it },
                            label = { Text("Low Stock Warning Level") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("quick_stock_warning_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newStockName.isNotBlank()) {
                                val qty = newStockQtyStr.toIntOrNull() ?: 0
                                val warning = newStockLowWarningStr.toIntOrNull() ?: 2
                                viewModel.createStockItem(
                                    name = newStockName,
                                    quantity = qty,
                                    lowStockWarning = warning
                                ) { created ->
                                    selectedStockItemId = created.id
                                    usesStockItem = true
                                    showQuickCreateStockDialog = false
                                }
                            }
                        },
                        enabled = newStockName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier.testTag("quick_save_stock_btn")
                    ) {
                        Text("Create & Select", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuickCreateStockDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // DELETE MODEL PRICE CONFIRMATION DIALOG
    if (priceToDelete != null) {
        AlertDialog(
            onDismissRequest = { priceToDelete = null },
            title = { Text("Delete Price?") },
            text = {
                Text("Delete price for ${priceToDelete?.brand} ${priceToDelete?.model} - ${priceToDelete?.repairType}? Note: Existing completed/open repair records will NOT be modified.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        priceToDelete?.let { viewModel.deletePrice(it) }
                        priceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { priceToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ADD / EDIT UNIVERSAL REPAIR TYPE DIALOG
    if (showAddUniversalDialog) {
        var uName by remember { mutableStateOf(universalToEdit?.name ?: "") }
        var uPriceStr by remember {
            mutableStateOf(
                universalToEdit?.let {
                    if (it.defaultPrice % 1.0 == 0.0) it.defaultPrice.toInt().toString()
                    else String.format(Locale.US, "%.2f", it.defaultPrice)
                } ?: ""
            )
        }

        val existingConn = remember(universalToEdit, stockConnections) {
            universalToEdit?.let { u ->
                stockConnections.find {
                    it.brand.isBlank() && it.model.isBlank() &&
                    it.repairType.equals(u.name, ignoreCase = true)
                }
            }
        }
        var usesStockItem by remember { mutableStateOf(existingConn != null) }
        var selectedStockItemId by remember { mutableStateOf<Long?>(existingConn?.stockItemId) }
        var stockQuantityUsedStr by remember { mutableStateOf((existingConn?.quantityUsed ?: 1).toString()) }
        var showStockDropdown by remember { mutableStateOf(false) }
        var showQuickCreateStockDialog by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showAddUniversalDialog = false
                universalToEdit = null
            },
            title = { Text(if (universalToEdit == null) "Add Universal Repair Type" else "Edit Universal Repair Type") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This default price will apply to all phone brands and models automatically.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SmartAutocompleteField(
                        label = "Repair Type Name",
                        value = uName,
                        onValueChange = { uName = it },
                        suggestions = allRepairTypes.map { it.name },
                        onSuggestionSelected = { uName = it },
                        onAddNew = {
                            viewModel.addRepairType(it)
                            uName = it
                        }
                    )

                    OutlinedTextField(
                        value = uPriceStr,
                        onValueChange = { uPriceStr = it },
                        label = { Text("Default Price (${settings.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("universal_price_input")
                    )

                    // Stock Item Connection Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { usesStockItem = !usesStockItem },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = usesStockItem,
                                    onCheckedChange = { usesStockItem = it },
                                    colors = CheckboxDefaults.colors(checkedColor = CyanAccent),
                                    modifier = Modifier.testTag("universal_uses_stock_item_checkbox")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("Uses Stock Item", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Auto-deduct for all models using this repair type", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (usesStockItem) {
                                val selectedItem = stockItems.find { it.id == selectedStockItemId }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Connected Stock Item:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    TextButton(
                                        onClick = { showQuickCreateStockDialog = true },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("universal_create_new_stock_btn")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("+ New Stock", fontSize = 11.sp, color = CyanAccent)
                                    }
                                }

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedCard(
                                        onClick = { showStockDropdown = true },
                                        modifier = Modifier.fillMaxWidth().testTag("universal_select_stock_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (selectedItem != null) "${selectedItem.name} (${selectedItem.quantity} in stock)" else "Select Stock Item...",
                                                color = if (selectedItem != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CyanAccent)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showStockDropdown,
                                        onDismissRequest = { showStockDropdown = false },
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        if (stockItems.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("No stock items found. Tap '+ New Stock'") },
                                                onClick = {
                                                    showStockDropdown = false
                                                    showQuickCreateStockDialog = true
                                                }
                                            )
                                        } else {
                                            stockItems.forEach { s ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(s.name, fontWeight = FontWeight.Medium)
                                                            Text("${s.quantity} units", color = if (s.quantity <= s.lowStockWarning) MaterialTheme.colorScheme.error else CyanAccent, fontSize = 12.sp)
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedStockItemId = s.id
                                                        showStockDropdown = false
                                                    },
                                                    modifier = Modifier.testTag("universal_stock_option_${s.id}")
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = stockQuantityUsedStr,
                                    onValueChange = { stockQuantityUsedStr = it },
                                    label = { Text("Quantity used per repair") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("universal_stock_quantity_input")
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = uPriceStr.toDoubleOrNull()
                        if (uName.isNotBlank() && p != null && p >= 0.0) {
                            val saveStockAction: () -> Unit = {
                                if (usesStockItem && selectedStockItemId != null) {
                                    val qty = stockQuantityUsedStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    viewModel.saveStockConnection(selectedStockItemId!!, brand = "", model = "", repairType = uName, quantityUsed = qty) {
                                        showAddUniversalDialog = false
                                        universalToEdit = null
                                    }
                                } else {
                                    viewModel.deleteStockConnectionForRepair(brand = "", model = "", repairType = uName) {
                                        showAddUniversalDialog = false
                                        universalToEdit = null
                                    }
                                }
                            }

                            if (universalToEdit == null) {
                                viewModel.saveUniversalRepairType(uName, p) {
                                    saveStockAction()
                                }
                            } else {
                                viewModel.updateUniversalRepairType(
                                    universalToEdit!!.copy(name = uName, defaultPrice = p)
                                ) {
                                    saveStockAction()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("save_universal_btn")
                ) {
                    Text("Save", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddUniversalDialog = false
                        universalToEdit = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )

        if (showQuickCreateStockDialog) {
            var newStockName by remember { mutableStateOf("") }
            var newStockQtyStr by remember { mutableStateOf("10") }
            var newStockLowWarningStr by remember { mutableStateOf("2") }
            AlertDialog(
                onDismissRequest = { showQuickCreateStockDialog = false },
                title = { Text("New Stock Item") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newStockName,
                            onValueChange = { newStockName = it },
                            label = { Text("Stock Item Name (e.g. T2 Charging Pin)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("univ_quick_stock_name_input")
                        )
                        OutlinedTextField(
                            value = newStockQtyStr,
                            onValueChange = { newStockQtyStr = it },
                            label = { Text("Current Quantity") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("univ_quick_stock_qty_input")
                        )
                        OutlinedTextField(
                            value = newStockLowWarningStr,
                            onValueChange = { newStockLowWarningStr = it },
                            label = { Text("Low Stock Warning Level") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("univ_quick_stock_warning_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newStockName.isNotBlank()) {
                                val qty = newStockQtyStr.toIntOrNull() ?: 0
                                val warning = newStockLowWarningStr.toIntOrNull() ?: 2
                                viewModel.createStockItem(
                                    name = newStockName,
                                    quantity = qty,
                                    lowStockWarning = warning
                                ) { created ->
                                    selectedStockItemId = created.id
                                    usesStockItem = true
                                    showQuickCreateStockDialog = false
                                }
                            }
                        },
                        enabled = newStockName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier.testTag("univ_quick_save_stock_btn")
                    ) {
                        Text("Create & Select", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuickCreateStockDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // DELETE UNIVERSAL REPAIR TYPE DIALOG
    if (universalToDelete != null) {
        AlertDialog(
            onDismissRequest = { universalToDelete = null },
            title = { Text("Delete Universal Repair Type?") },
            text = {
                Text("Delete universal repair type '${universalToDelete?.name}'? Note: Existing repair records and job history will NOT be modified.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        universalToDelete?.let { viewModel.deleteUniversalRepairType(it) }
                        universalToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_universal_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { universalToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

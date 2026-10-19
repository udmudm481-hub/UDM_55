package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.printer.DefaultTemplates
import com.example.printer.ReceiptAlignment
import com.example.printer.ReceiptBitmapGenerator
import com.example.printer.ReceiptDividerStyle
import com.example.printer.ReceiptElement
import com.example.printer.ReceiptElementType
import com.example.printer.ReceiptLogoSize
import com.example.printer.ReceiptPreferences
import com.example.printer.ReceiptTemplate
import com.example.printer.ReceiptTextSize
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurface
import com.example.ui.viewmodel.RepairViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptCustomizationScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val savedCustomerTemplate by viewModel.customerTemplate.collectAsState()
    val savedPaymentTemplate by viewModel.paymentTemplate.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Customer Receipt, 1 = Payment Receipt

    // Working local template copies for responsive UI
    var custTemplate by remember(savedCustomerTemplate) { mutableStateOf(savedCustomerTemplate) }
    var payTemplate by remember(savedPaymentTemplate) { mutableStateOf(savedPaymentTemplate) }

    val currentTemplate = if (activeTab == 0) custTemplate else payTemplate

    fun updateCurrentTemplate(newTemplate: ReceiptTemplate) {
        if (activeTab == 0) {
            custTemplate = newTemplate
        } else {
            payTemplate = newTemplate
        }
    }

    var showAddElementDialog by remember { mutableStateOf(false) }
    var editingElementIndex by remember { mutableStateOf<Int?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showFullPreviewModal by remember { mutableStateOf(false) }

    // Live rendered bitmap previews using the exact same rendering engine
    val customerPreviewBitmap = remember(custTemplate) {
        ReceiptBitmapGenerator.generateCustomerReceiptPreview(custTemplate)
    }
    val paymentPreviewBitmap = remember(payTemplate) {
        ReceiptBitmapGenerator.generatePaymentReceiptPreview(payTemplate)
    }
    val currentPreviewBitmap = if (activeTab == 0) customerPreviewBitmap else paymentPreviewBitmap

    // Image Picker for Logo selection from Android storage
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val prefix = if (activeTab == 0) "cust_logo" else "pay_logo"
                val savedPath = ReceiptPreferences.saveLogoFromUri(context, uri, prefix)
                if (savedPath != null) {
                    val editIdx = editingElementIndex
                    if (editIdx != null && editIdx in currentTemplate.elements.indices) {
                        val list = currentTemplate.elements.toMutableList()
                        list[editIdx] = list[editIdx].copy(logoPath = savedPath)
                        updateCurrentTemplate(currentTemplate.copy(elements = list))
                    } else {
                        // Add as new logo element
                        val newEl = ReceiptElement(
                            type = ReceiptElementType.IMAGE_LOGO,
                            logoPath = savedPath,
                            logoSize = ReceiptLogoSize.MEDIUM,
                            alignment = ReceiptAlignment.CENTER,
                            spacingBottom = 6
                        )
                        updateCurrentTemplate(currentTemplate.copy(elements = currentTemplate.elements + newEl))
                    }
                    snackbarHostState.showSnackbar("Logo image added successfully")
                } else {
                    snackbarHostState.showSnackbar("Failed to import logo image")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt Designer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("receipt_custom_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showFullPreviewModal = true },
                        modifier = Modifier.testTag("receipt_full_preview_btn")
                    ) {
                        Icon(Icons.Default.Preview, contentDescription = "Full Preview", tint = CyanAccent)
                    }
                    Button(
                        onClick = {
                            if (activeTab == 0) {
                                viewModel.updateCustomerTemplate(custTemplate)
                            } else {
                                viewModel.updatePaymentTemplate(payTemplate)
                            }
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (activeTab == 0) "Customer Receipt Template Saved" else "Payment Receipt Template Saved"
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_template_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Independent Template Tabs
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = NavySurface,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = CyanAccent,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier.testTag("receipt_tab_customer"),
                    text = {
                        Text(
                            "CUSTOMER RECEIPT",
                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeTab == 0) CyanAccent else Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    modifier = Modifier.testTag("receipt_tab_payment"),
                    text = {
                        Text(
                            "PAYMENT RECEIPT",
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeTab == 1) CyanAccent else Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            // Top Quick Action & Preview Summary Header
            Surface(
                color = NavySurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Actions row: Add Element, Reset Default, Preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showAddElementDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_element_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Element", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ADD ELEMENT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7043)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF7043)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("reset_template_btn")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("RESET", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showFullPreviewModal = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("preview_btn")
                        ) {
                            Icon(Icons.Default.Preview, contentDescription = "Preview", modifier = Modifier.size(16.dp), tint = CyanAccent)
                            Spacer(Modifier.width(4.dp))
                            Text("PREVIEW", fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Compact Live Preview Strip (tap opens full zoom)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFullPreviewModal = true },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CyanAccent)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Live Thermal Layout: ${currentTemplate.elements.size} elements (${currentPreviewBitmap.height}px)",
                                    fontSize = 12.sp,
                                    color = Color.LightGray
                                )
                            }
                            Text(
                                text = "TAP TO ZOOM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        }
                    }
                }
            }

            // Elements List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentTemplate.elements.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NavySurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Template has no elements", fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.height(4.dp))
                                Text("Tap '+ ADD ELEMENT' to build your receipt layout.", fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                itemsIndexed(currentTemplate.elements, key = { _, el -> el.id }) { index, element ->
                    ReceiptElementCard(
                        index = index,
                        totalCount = currentTemplate.elements.size,
                        element = element,
                        onMoveUp = {
                            if (index > 0) {
                                val list = currentTemplate.elements.toMutableList()
                                val item = list.removeAt(index)
                                list.add(index - 1, item)
                                updateCurrentTemplate(currentTemplate.copy(elements = list))
                            }
                        },
                        onMoveDown = {
                            if (index < currentTemplate.elements.size - 1) {
                                val list = currentTemplate.elements.toMutableList()
                                val item = list.removeAt(index)
                                list.add(index + 1, item)
                                updateCurrentTemplate(currentTemplate.copy(elements = list))
                            }
                        },
                        onToggleVisibility = {
                            val list = currentTemplate.elements.toMutableList()
                            list[index] = list[index].copy(isVisible = !list[index].isVisible)
                            updateCurrentTemplate(currentTemplate.copy(elements = list))
                        },
                        onEdit = {
                            editingElementIndex = index
                        },
                        onDelete = {
                            val list = currentTemplate.elements.toMutableList()
                            val removed = list.removeAt(index)
                            if (removed.logoPath.isNotBlank()) {
                                ReceiptPreferences.deleteLogoFile(removed.logoPath)
                            }
                            updateCurrentTemplate(currentTemplate.copy(elements = list))
                        }
                    )
                }
            }
        }
    }

    // Add Element Dialog
    if (showAddElementDialog) {
        AddElementDialog(
            activeTab = activeTab,
            onDismiss = { showAddElementDialog = false },
            onAddText = {
                val newEl = ReceiptElement(
                    type = ReceiptElementType.TEXT,
                    text = "Thank you for choosing UDM Mobile Repair.",
                    fontSize = ReceiptTextSize.NORMAL,
                    isBold = false,
                    alignment = ReceiptAlignment.CENTER,
                    spacingBottom = 4
                )
                val newList = currentTemplate.elements + newEl
                updateCurrentTemplate(currentTemplate.copy(elements = newList))
                editingElementIndex = newList.lastIndex
                showAddElementDialog = false
            },
            onAddDynamicField = { tag, label ->
                val newEl = ReceiptElement(
                    type = ReceiptElementType.DYNAMIC_FIELD,
                    fieldTag = tag,
                    customLabel = label,
                    fontSize = ReceiptTextSize.NORMAL,
                    isBold = tag == "{TOTAL}" || tag == "{JOB_NO}",
                    alignment = if (tag == "{SHOP_NAME}" || tag == "{SUBTITLE}") ReceiptAlignment.CENTER else ReceiptAlignment.LEFT,
                    spacingBottom = 4
                )
                val newList = currentTemplate.elements + newEl
                updateCurrentTemplate(currentTemplate.copy(elements = newList))
                showAddElementDialog = false
            },
            onAddLogo = {
                showAddElementDialog = false
                logoPickerLauncher.launch("image/*")
            },
            onAddDivider = { style ->
                val newEl = ReceiptElement(
                    type = ReceiptElementType.DIVIDER,
                    dividerStyle = style,
                    spacingBottom = 6
                )
                updateCurrentTemplate(currentTemplate.copy(elements = currentTemplate.elements + newEl))
                showAddElementDialog = false
            },
            onAddBlankSpace = { heightPx ->
                val newEl = ReceiptElement(
                    type = ReceiptElementType.BLANK_SPACE,
                    spaceHeight = heightPx
                )
                updateCurrentTemplate(currentTemplate.copy(elements = currentTemplate.elements + newEl))
                showAddElementDialog = false
            }
        )
    }

    // Edit Element Dialog
    editingElementIndex?.let { editIdx ->
        if (editIdx in currentTemplate.elements.indices) {
            val el = currentTemplate.elements[editIdx]
            EditElementDialog(
                element = el,
                activeTab = activeTab,
                onDismiss = { editingElementIndex = null },
                onSave = { updatedEl ->
                    val list = currentTemplate.elements.toMutableList()
                    list[editIdx] = updatedEl
                    updateCurrentTemplate(currentTemplate.copy(elements = list))
                    editingElementIndex = null
                },
                onPickNewLogo = {
                    logoPickerLauncher.launch("image/*")
                }
            )
        } else {
            editingElementIndex = null
        }
    }

    // Reset Template Confirmation Dialog
    if (showResetDialog) {
        val tabTitle = if (activeTab == 0) "Customer Receipt" else "Payment Receipt"
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = NavySurface,
            title = { Text("Reset $tabTitle Template?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will restore the factory default layout for the $tabTitle template only. The other template will not be affected.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activeTab == 0) {
                            val defaultTemplate = viewModel.resetCustomerTemplate()
                            custTemplate = defaultTemplate
                        } else {
                            val defaultTemplate = viewModel.resetPaymentTemplate()
                            payTemplate = defaultTemplate
                        }
                        showResetDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("$tabTitle restored to default")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043))
                ) {
                    Text("Reset to Default", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Full Realistic Receipt Preview Dialog
    if (showFullPreviewModal) {
        FullPreviewModal(
            bitmap = currentPreviewBitmap,
            templateName = if (activeTab == 0) "Customer Receipt" else "Payment Receipt",
            onDismiss = { showFullPreviewModal = false }
        )
    }
}

/**
 * An individual receipt element item card with comprehensive controls:
 * Edit, Hide/Show, Move Up, Move Down, Delete.
 */
@Composable
fun ReceiptElementCard(
    index: Int,
    totalCount: Int,
    element: ReceiptElement,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisibility: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("element_card_$index"),
        colors = CardDefaults.cardColors(
            containerColor = if (element.isVisible) NavySurface else Color(0xFF161F33)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (element.isVisible) NavyBorder else Color(0xFF283652)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle / index badge
            Surface(
                color = if (element.isVisible) Color(0xFF1E2D4A) else Color(0xFF1A2234),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#${index + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (element.isVisible) CyanAccent else Color.Gray
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (element.type) {
                        ReceiptElementType.TEXT -> Icons.Default.TextFields
                        ReceiptElementType.DYNAMIC_FIELD -> Icons.Default.DataObject
                        ReceiptElementType.IMAGE_LOGO -> Icons.Default.Image
                        ReceiptElementType.DIVIDER -> Icons.Default.HorizontalRule
                        ReceiptElementType.BLANK_SPACE -> Icons.Default.SpaceBar
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (element.isVisible) CyanAccent else Color.Gray,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))

                    val title = when (element.type) {
                        ReceiptElementType.TEXT -> {
                            val firstLine = element.text.lines().firstOrNull { it.isNotBlank() } ?: "Empty Text"
                            "TEXT: \"${firstLine.take(24)}\""
                        }
                        ReceiptElementType.DYNAMIC_FIELD -> {
                            val tag = element.fieldTag
                            if (element.customLabel.isNotBlank()) "$tag (${element.customLabel})" else tag
                        }
                        ReceiptElementType.IMAGE_LOGO -> "LOGO / IMAGE (${element.logoSize.displayName})"
                        ReceiptElementType.DIVIDER -> "${element.dividerStyle.name} DIVIDER"
                        ReceiptElementType.BLANK_SPACE -> "BLANK SPACE (${element.spaceHeight}px)"
                    }

                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (element.isVisible) Color.White else Color.Gray
                    )
                }

                Spacer(Modifier.height(3.dp))

                // Detail chips row
                val details = buildString {
                    when (element.type) {
                        ReceiptElementType.TEXT, ReceiptElementType.DYNAMIC_FIELD -> {
                            append(element.fontSize.name)
                            if (element.isBold) append(" • Bold")
                            append(" • ${element.alignment.name}")
                            append(" • ${element.spacingBottom}px space")
                        }
                        ReceiptElementType.IMAGE_LOGO -> {
                            append("${element.alignment.name} • ${element.spacingBottom}px space")
                        }
                        ReceiptElementType.DIVIDER -> {
                            append("${element.spacingBottom}px space")
                        }
                        ReceiptElementType.BLANK_SPACE -> {
                            append("Height ${element.spaceHeight}px")
                        }
                    }
                    if (!element.isVisible) {
                        append(" • (HIDDEN)")
                    }
                }
                Text(
                    text = details,
                    fontSize = 11.sp,
                    color = if (element.isVisible) Color(0xFF90A4AE) else Color(0xFF607D8B)
                )
            }

            // Element Controls: Move Up, Move Down, Hide/Show, Edit, Delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Move Up
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("element_move_up_$index")
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (index > 0) Color.White else Color(0xFF455A64),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Move Down
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("element_move_down_$index")
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (index < totalCount - 1) Color.White else Color(0xFF455A64),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Hide / Show
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("element_toggle_vis_$index")
                ) {
                    Icon(
                        imageVector = if (element.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Visibility",
                        tint = if (element.isVisible) CyanAccent else Color(0xFF78909C),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Edit
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("element_edit_$index")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("element_delete_$index")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Add Element Modal with 5 options:
 * + Text, + Dynamic Field, + Logo / Image, + Divider, + Blank Space
 */
@Composable
fun AddElementDialog(
    activeTab: Int,
    onDismiss: () -> Unit,
    onAddText: () -> Unit,
    onAddDynamicField: (tag: String, label: String) -> Unit,
    onAddLogo: () -> Unit,
    onAddDivider: (ReceiptDividerStyle) -> Unit,
    onAddBlankSpace: (Int) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NavySurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategory == null) "Add Receipt Element" else "Choose $selectedCategory",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (selectedCategory == null) {
                    // Category Selection
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AddElementOptionCard(
                            icon = Icons.Default.TextFields,
                            title = "Custom Text",
                            subtitle = "Thank you messages, terms & warranty notes",
                            onClick = onAddText
                        )
                        AddElementOptionCard(
                            icon = Icons.Default.DataObject,
                            title = "Dynamic Field",
                            subtitle = "Bind real repair data ({SHOP_NAME}, {TOTAL}, etc.)",
                            onClick = { selectedCategory = "Dynamic Field" }
                        )
                        AddElementOptionCard(
                            icon = Icons.Default.Image,
                            title = "Logo / Image",
                            subtitle = "Select phone repair shop logo from device storage",
                            onClick = onAddLogo
                        )
                        AddElementOptionCard(
                            icon = Icons.Default.HorizontalRule,
                            title = "Divider Line",
                            subtitle = "Dashed or solid section separator",
                            onClick = { selectedCategory = "Divider" }
                        )
                        AddElementOptionCard(
                            icon = Icons.Default.SpaceBar,
                            title = "Blank Space",
                            subtitle = "Vertical padding between receipt sections",
                            onClick = { selectedCategory = "Blank Space" }
                        )
                    }
                } else if (selectedCategory == "Dynamic Field") {
                    val availableFields = if (activeTab == 0) DefaultTemplates.CUSTOMER_FIELDS else DefaultTemplates.PAYMENT_FIELDS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for ((tag, description) in availableFields) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val defaultLabel = when (tag) {
                                            "{JOB_NO}" -> "Job No: "
                                            "{CUSTOMER_NAME}" -> "Customer: "
                                            "{CUSTOMER_PHONE}" -> "Phone: "
                                            "{DATE}" -> "Date: "
                                            "{TIME}" -> "Time: "
                                            "{BRAND}" -> "Brand: "
                                            "{MODEL}" -> "Model: "
                                            "{TOTAL}" -> "TOTAL:"
                                            "{PAID}" -> "PAID:"
                                            "{BALANCE}" -> "BALANCE:"
                                            "{REPAIR_ITEMS}" -> "Repair:"
                                            "{PAYMENT_AMOUNT}" -> "Payment Received:"
                                            else -> ""
                                        }
                                        onAddDynamicField(tag, defaultLabel)
                                    },
                                color = Color(0xFF1A2640),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = tag, fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 14.sp)
                                        Text(text = description, color = Color.LightGray, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                } else if (selectedCategory == "Divider") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AddElementOptionCard(
                            icon = Icons.Default.HorizontalRule,
                            title = "Dashed Divider",
                            subtitle = "Traditional thermal receipt dashed line (---)",
                            onClick = { onAddDivider(ReceiptDividerStyle.DASHED) }
                        )
                        AddElementOptionCard(
                            icon = Icons.Default.HorizontalRule,
                            title = "Solid Divider",
                            subtitle = "Clean continuous thin black line",
                            onClick = { onAddDivider(ReceiptDividerStyle.SOLID) }
                        )
                    }
                } else if (selectedCategory == "Blank Space") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val heights = listOf(4, 8, 12, 16, 24, 32)
                        for (h in heights) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddBlankSpace(h) },
                                color = Color(0xFF1A2640),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Space ${h}px", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("+ Insert", color = CyanAccent, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (selectedCategory != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = { selectedCategory = null },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("← Back to Categories", color = CyanAccent)
                    }
                }
            }
        }
    }
}

@Composable
fun AddElementOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color(0xFF1A2640),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF22355A),
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text(subtitle, color = Color.LightGray, fontSize = 11.sp)
            }
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Element Editor Dialog for modifying properties:
 * text, labels, font size, bold, alignment, spacing, logo size/image, divider style, etc.
 */
@Composable
fun EditElementDialog(
    element: ReceiptElement,
    activeTab: Int,
    onDismiss: () -> Unit,
    onSave: (ReceiptElement) -> Unit,
    onPickNewLogo: () -> Unit
) {
    var workingElement by remember { mutableStateOf(element) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NavySurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit ${workingElement.type.displayName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(Modifier.height(16.dp))

                when (workingElement.type) {
                    ReceiptElementType.TEXT -> {
                        Text("Custom Text Content", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = workingElement.text,
                            onValueChange = { workingElement = workingElement.copy(text = it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    ReceiptElementType.DYNAMIC_FIELD -> {
                        Text("Dynamic Field: ${workingElement.fieldTag}", fontSize = 13.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Custom Label Prefix", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = workingElement.customLabel,
                            onValueChange = { workingElement = workingElement.copy(customLabel = it) },
                            placeholder = { Text("e.g. Job No: or TOTAL:", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    ReceiptElementType.IMAGE_LOGO -> {
                        Text("Logo Configuration", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        // Logo status & Pick/Replace button
                        if (workingElement.logoPath.isNotBlank() && File(workingElement.logoPath).exists()) {
                            Surface(
                                color = Color(0xFF1E2D4A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Logo Stored Locally", color = Color.White, fontSize = 12.sp)
                                    }
                                    TextButton(onClick = {
                                        workingElement = workingElement.copy(logoPath = "")
                                    }) {
                                        Text("Remove Logo", color = Color(0xFFFF5252), fontSize = 12.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        Button(
                            onClick = onPickNewLogo,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2D4A), contentColor = CyanAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (workingElement.logoPath.isBlank()) "Choose Image from Storage" else "Replace Image from Storage",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Logo Size", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReceiptLogoSize.entries.forEach { size ->
                                FilterChip(
                                    selected = workingElement.logoSize == size,
                                    onClick = { workingElement = workingElement.copy(logoSize = size) },
                                    label = { Text(size.name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanAccent,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF1E2D4A),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    ReceiptElementType.DIVIDER -> {
                        Text("Divider Style", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(ReceiptDividerStyle.DASHED, ReceiptDividerStyle.SOLID).forEach { style ->
                                FilterChip(
                                    selected = workingElement.dividerStyle == style,
                                    onClick = { workingElement = workingElement.copy(dividerStyle = style) },
                                    label = { Text(style.name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanAccent,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF1E2D4A),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    ReceiptElementType.BLANK_SPACE -> {
                        Text("Space Height (px)", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(4, 8, 12, 16, 20, 24, 32).forEach { h ->
                                FilterChip(
                                    selected = workingElement.spaceHeight == h,
                                    onClick = { workingElement = workingElement.copy(spaceHeight = h) },
                                    label = { Text("${h}px", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanAccent,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF1E2D4A),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Typography & Formatting (Only for Text & Dynamic fields)
                if (workingElement.type == ReceiptElementType.TEXT || workingElement.type == ReceiptElementType.DYNAMIC_FIELD) {
                    Spacer(Modifier.height(14.dp))
                    Text("Font Size", fontSize = 12.sp, color = Color.LightGray)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReceiptTextSize.entries.forEach { size ->
                            FilterChip(
                                selected = workingElement.fontSize == size,
                                onClick = { workingElement = workingElement.copy(fontSize = size) },
                                label = { Text(size.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanAccent,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E2D4A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatBold, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bold Style", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = workingElement.isBold,
                            onCheckedChange = { workingElement = workingElement.copy(isBold = it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CyanAccent)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Text Alignment", fontSize = 12.sp, color = Color.LightGray)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReceiptAlignment.entries.forEach { align ->
                            val icon = when (align) {
                                ReceiptAlignment.LEFT -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                ReceiptAlignment.CENTER -> Icons.Default.FormatAlignCenter
                                ReceiptAlignment.RIGHT -> Icons.AutoMirrored.Filled.FormatAlignRight
                            }
                            FilterChip(
                                selected = workingElement.alignment == align,
                                onClick = { workingElement = workingElement.copy(alignment = align) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(align.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanAccent,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E2D4A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Logo Alignment
                if (workingElement.type == ReceiptElementType.IMAGE_LOGO) {
                    Spacer(Modifier.height(12.dp))
                    Text("Logo Alignment", fontSize = 12.sp, color = Color.LightGray)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReceiptAlignment.entries.forEach { align ->
                            FilterChip(
                                selected = workingElement.alignment == align,
                                onClick = { workingElement = workingElement.copy(alignment = align) },
                                label = { Text(align.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanAccent,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E2D4A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Spacing below element (for all except pure blank space)
                if (workingElement.type != ReceiptElementType.BLANK_SPACE) {
                    Spacer(Modifier.height(12.dp))
                    Text("Bottom Spacing: ${workingElement.spacingBottom}px", fontSize = 12.sp, color = Color.LightGray)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 2, 4, 6, 8, 12, 16).forEach { sp ->
                            FilterChip(
                                selected = workingElement.spacingBottom == sp,
                                onClick = { workingElement = workingElement.copy(spacingBottom = sp) },
                                label = { Text("${sp}px", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanAccent,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E2D4A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons: Done & Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(workingElement) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Apply Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Full Realistic Thermal Receipt Paper Preview Modal.
 * Renders the exact 384-dot 1-bit thermal print bitmap on simulated white paper with shadow and tear lines.
 */
@Composable
fun FullPreviewModal(
    bitmap: Bitmap,
    templateName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = NavyBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavySurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Physical Receipt Preview",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "$templateName • 384x${bitmap.height} px • Marklife P50S",
                            fontSize = 11.sp,
                            color = CyanAccent
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Scrollable simulated paper container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF090D16))
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .width(320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Rendered thermal bitmap
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Receipt Preview",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Footer note
                Surface(
                    color = NavySurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rendered using the same 1-bit thermal engine and layout pipeline as the P50S printer.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

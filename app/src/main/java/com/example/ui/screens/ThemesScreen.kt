package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppThemeConfig
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalAppTheme
import com.example.ui.viewmodel.RepairViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsState()
    val currentTheme = LocalAppTheme.current

    val currentThemeMode = remember(settings.themeName) {
        AppThemeMode.fromId(settings.themeName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Themes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            // Header Info Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(currentTheme.cardCornerRadiusDp.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(currentTheme.cardCornerRadiusDp.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(currentTheme.primaryAccent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = currentTheme.primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "SELECT APP THEME",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = currentTheme.primaryAccent
                                )
                            }

                            Surface(
                                color = currentTheme.primaryAccent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, currentTheme.primaryAccent.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = currentThemeMode.displayName.uppercase(),
                                    color = currentTheme.primaryAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Choose a coordinated color system. Themes adjust all navigation elements, dashboard cards, repair forms, status badges, buttons, dialogs, and balance warnings seamlessly.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Theme 1: UDM CLASSIC
            item {
                ThemeCard(
                    themeConfig = AppThemeConfig.UDM_CLASSIC_THEME,
                    badgeLabel = "DEFAULT",
                    isSelected = currentThemeMode == AppThemeMode.UDM_CLASSIC,
                    testTag = "theme_card_udm_classic",
                    buttonTestTag = "theme_btn_udm_classic",
                    onSelect = {
                        viewModel.selectTheme(AppThemeMode.UDM_CLASSIC)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("UDM Classic theme applied successfully")
                        }
                    }
                )
            }

            // Theme 2: MIDNIGHT LUXURY
            item {
                ThemeCard(
                    themeConfig = AppThemeConfig.MIDNIGHT_LUXURY_THEME,
                    badgeLabel = "PREMIUM DARK",
                    isSelected = currentThemeMode == AppThemeMode.MIDNIGHT_LUXURY,
                    testTag = "theme_card_midnight_luxury",
                    buttonTestTag = "theme_btn_midnight_luxury",
                    onSelect = {
                        viewModel.selectTheme(AppThemeMode.MIDNIGHT_LUXURY)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Midnight Luxury theme applied successfully")
                        }
                    }
                )
            }

            // Theme 3: ROYAL VIOLET
            item {
                ThemeCard(
                    themeConfig = AppThemeConfig.ROYAL_VIOLET_THEME,
                    badgeLabel = "ELEGANT",
                    isSelected = currentThemeMode == AppThemeMode.ROYAL_VIOLET,
                    testTag = "theme_card_royal_violet",
                    buttonTestTag = "theme_btn_royal_violet",
                    onSelect = {
                        viewModel.selectTheme(AppThemeMode.ROYAL_VIOLET)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Royal Violet theme applied successfully")
                        }
                    }
                )
            }

            // Architecture Extensibility Note
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(currentTheme.cardCornerRadiusDp.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "CUSTOM THEME EDITOR FOUNDATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.primaryAccent,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "UDM Repair's architecture uses a modular AppThemeConfig model supporting custom hex colors, card radii, status indicators, and full JSON persistence for future custom palette editing.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeCard(
    themeConfig: AppThemeConfig,
    badgeLabel: String,
    isSelected: Boolean,
    testTag: String,
    buttonTestTag: String,
    onSelect: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) themeConfig.primaryAccent else MaterialTheme.colorScheme.outline,
        label = "border"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = themeConfig.cardBackground),
        shape = RoundedCornerShape(themeConfig.cardCornerRadiusDp.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(themeConfig.cardCornerRadiusDp.dp)
            )
            .clickable(onClick = onSelect)
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title Row with Selection Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = themeConfig.name.uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = themeConfig.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = themeConfig.primaryAccent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            color = themeConfig.primaryAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Checkmark / Active Indicator
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(themeConfig.primaryAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active",
                            tint = themeConfig.onPrimaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, themeConfig.border, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = themeConfig.themeMode.subtitle,
                fontSize = 12.sp,
                color = themeConfig.textSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Palette Swatches
            Text(
                text = "COORDINATED PALETTE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = themeConfig.primaryAccent,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SwatchItem("Base", themeConfig.background)
                SwatchItem("Surface", themeConfig.surface)
                SwatchItem("Card", themeConfig.cardBackground)
                SwatchItem("Accent", themeConfig.primaryAccent)
                SwatchItem("Status", themeConfig.statusDelivered)
                SwatchItem("Balance", themeConfig.paymentPartial)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Realistic Preview Card
            Text(
                text = "SCREEN PREVIEW",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = themeConfig.textTertiary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = themeConfig.background,
                shape = RoundedCornerShape(themeConfig.cardCornerRadiusDp.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, themeConfig.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = themeConfig.primaryAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#0014",
                                color = themeConfig.primaryAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = themeConfig.statusDelivered.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(themeConfig.statusDelivered)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "DELIVERED",
                                    color = themeConfig.statusDelivered,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Samsung Galaxy S22 • Broken Screen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = themeConfig.textPrimary
                    )
                    Text(
                        text = "Kamal Silva • 077 123 4567",
                        fontSize = 11.sp,
                        color = themeConfig.textSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total: Rs. 14,000",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = themeConfig.textPrimary
                        )
                        Text(
                            text = "Balance: Rs. 0",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeConfig.primaryAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button
            if (isSelected) {
                Surface(
                    color = themeConfig.primaryAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(themeConfig.buttonCornerRadiusDp.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, themeConfig.primaryAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = themeConfig.primaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACTIVE THEME",
                                fontWeight = FontWeight.Bold,
                                color = themeConfig.primaryAccent,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                val buttonModifier = if (themeConfig.primaryButtonGradientColors != null) {
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(themeConfig.buttonCornerRadiusDp.dp))
                        .background(Brush.horizontalGradient(themeConfig.primaryButtonGradientColors))
                        .clickable(onClick = onSelect)
                        .testTag(buttonTestTag)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(themeConfig.buttonCornerRadiusDp.dp))
                        .background(themeConfig.primaryButtonBackground)
                        .clickable(onClick = onSelect)
                        .testTag(buttonTestTag)
                }

                Box(
                    modifier = buttonModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "APPLY ${themeConfig.name.uppercase()}",
                        color = themeConfig.primaryButtonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SwatchItem(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

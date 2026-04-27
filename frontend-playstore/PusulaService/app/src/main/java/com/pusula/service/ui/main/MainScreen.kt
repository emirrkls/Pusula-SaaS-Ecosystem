package com.pusula.service.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.core.SessionManager
import com.pusula.service.core.featureGated
import com.pusula.service.ui.admin.AdminDashboardScreen
import com.pusula.service.ui.admin.AdminViewModel
import com.pusula.service.ui.admin.CatalogScreen
import com.pusula.service.ui.finance.FinanceScreen
import com.pusula.service.ui.profile.ProfileScreen
import com.pusula.service.ui.proposal.ProposalScreen
import com.pusula.service.ui.settings.SettingsScreen
import com.pusula.service.ui.customer.CustomerScreen
import com.pusula.service.ui.technician.TicketListScreen
import com.pusula.service.ui.theme.Spacing

@Composable
fun MainScreen(
    sessionManager: SessionManager,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenTicket: (Long) -> Unit,
    onNavigateFieldRadar: () -> Unit = {},
    onNavigateProfitAnalysis: () -> Unit = {},
    onNavigateCatalog: () -> Unit = {}
) {
    val session by sessionManager.state.collectAsState()
    val adminViewModel: AdminViewModel = hiltViewModel()
    LaunchedEffect(session.isAuthenticated, session.isAdmin) {
        if (session.isAuthenticated && session.isAdmin) adminViewModel.loadDashboard()
    }
    val tabs = if (session.isAdmin) {
        listOf(
            MainTab("Özet", Icons.Default.Summarize),
            MainTab("Operasyon", Icons.AutoMirrored.Filled.ListAlt),
            MainTab("Finans", Icons.Default.Analytics, featureKey = "FINANCE_MODULE"),
            MainTab("Hesap", Icons.Default.Settings)
        )
    } else {
        listOf(
            MainTab("İşlerim", Icons.AutoMirrored.Filled.ListAlt),
            MainTab("Hesap", Icons.Default.Person)
        )
    }
    var selectedTab by remember(tabs) { mutableStateOf(tabs.first().title) }
    var showQuickActions by remember { mutableStateOf(false) }
    val quickSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quickActions = listOf(
        MainTab("Müşteriler", Icons.Default.Group),
        MainTab("Teklifler", Icons.Default.RequestQuote),
        MainTab("Stok", Icons.Default.Inventory2, featureKey = "BASIC_INVENTORY")
    ).filter { tab ->
        tab.featureKey == null || (session.features[tab.featureKey] ?: false)
    }

    Scaffold(
        bottomBar = {
            BottomAppBar {
                if (session.isAdmin && tabs.size == 4) {
                    val leftTabs = tabs.take(2)
                    val rightTabs = tabs.drop(2)
                    leftTabs.forEach { tab ->
                        val selected = selectedTab == tab.title
                        val tabModifier = if (tab.featureKey != null) {
                            Modifier
                                .weight(1f)
                                .featureGated(sessionManager = sessionManager, featureKey = tab.featureKey)
                        } else {
                            Modifier.weight(1f)
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = tab.title },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = tabModifier
                        )
                    }

                    NavigationBarItem(
                        selected = false,
                        onClick = { showQuickActions = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Diğer Modüller",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Diğer",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    rightTabs.forEach { tab ->
                        val selected = selectedTab == tab.title
                        val tabModifier = if (tab.featureKey != null) {
                            Modifier
                                .weight(1f)
                                .featureGated(sessionManager = sessionManager, featureKey = tab.featureKey)
                        } else {
                            Modifier.weight(1f)
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = tab.title },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = tabModifier
                        )
                    }
                } else {
                    tabs.forEach { tab ->
                        val selected = selectedTab == tab.title
                        val tabModifier = if (tab.featureKey != null) {
                            Modifier
                                .weight(1f)
                                .featureGated(sessionManager = sessionManager, featureKey = tab.featureKey)
                        } else {
                            Modifier.weight(1f)
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = tab.title },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = tabModifier
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if ((session.trialDaysRemaining ?: 0) <= 7 && (session.trialDaysRemaining ?: 0) >= 0) {
                TrialBanner(days = session.trialDaysRemaining ?: 0, onUpgrade = onUpgrade)
                Spacer(modifier = Modifier.height(Spacing.xs))
            }
            if (session.isReadOnly) {
                ReadOnlyBanner()
                Spacer(modifier = Modifier.height(Spacing.xs))
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    "İşlerim", "Operasyon", "İş Emirleri" -> TicketListScreen(onOpenTicket = onOpenTicket)
                    "Hesap" -> if (session.isAdmin) {
                        SettingsScreen(
                            sessionManager = sessionManager,
                            onLogout = onLogout,
                            onDeleteAccount = onDeleteAccount
                        )
                    } else {
                        ProfileScreen(
                            sessionManager = sessionManager,
                            onLogout = onLogout,
                            onDeleteAccount = onDeleteAccount
                        )
                    }
                    "Özet" -> if (session.isAdmin) {
                        AdminDashboardScreen(
                            onLogout = onLogout,
                            onOpenRadar = onNavigateFieldRadar,
                            onOpenProfit = onNavigateProfitAnalysis,
                            onOpenCatalog = onNavigateCatalog,
                            onOpenUpgrade = onUpgrade,
                            viewModel = adminViewModel,
                            embedded = true
                        )
                    } else {
                        PlaceholderPanel(title = selectedTab)
                    }
                    "Finans" -> if (session.isAdmin) {
                        FinanceScreen()
                    } else {
                        PlaceholderPanel(title = selectedTab)
                    }
                    "Müşteriler" -> if (session.isAdmin) {
                        CustomerScreen()
                    } else {
                        PlaceholderPanel(title = selectedTab)
                    }
                    "Teklifler" -> if (session.isAdmin) {
                        ProposalScreen()
                    } else {
                        PlaceholderPanel(title = selectedTab)
                    }
                    "Stok" -> if (session.isAdmin) {
                        CatalogScreen(viewModel = adminViewModel)
                    } else {
                        TicketListScreen(onOpenTicket = onOpenTicket)
                    }
                    else -> PlaceholderPanel(title = selectedTab)
                }
            }
        }
    }

    if (showQuickActions) {
        ModalBottomSheet(
            onDismissRequest = { showQuickActions = false },
            sheetState = quickSheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Diğer Modüller",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Hızlı erişim için bir modül seçin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                if (quickActions.isEmpty()) {
                    Text("Ek modül bulunmuyor.")
                } else {
                    quickActions.forEach { tab ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    shape = MaterialTheme.shapes.medium
                                )
                                .clickable {
                                    selectedTab = tab.title
                                    showQuickActions = false
                                }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Icon(tab.icon, contentDescription = tab.title, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tab.title, style = MaterialTheme.typography.bodyLarge)
                                Text("Modülü aç", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                FilledTonalButton(
                    onClick = { showQuickActions = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kapat")
                }
            }
        }
    }
}

@Composable
private fun TrialBanner(days: Int, onUpgrade: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "Deneme süreniz $days gün sonra bitiyor",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onUpgrade,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Text("Yükselt")
        }
    }
}

@Composable
private fun ReadOnlyBanner() {
    Text(
        text = "Aboneliğiniz sona erdi. Sadece görüntüleme modundasınız.",
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun PlaceholderPanel(title: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "$title ekranı hazırlanıyor",
            modifier = Modifier.padding(16.dp)
        )
    }
}

private data class MainTab(
    val title: String,
    val icon: ImageVector,
    val featureKey: String? = null
)

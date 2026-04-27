package com.pusula.service.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pusula.service.core.SessionManager
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.theme.AccentCyan
import com.pusula.service.ui.theme.AccentOrange
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.ErrorTone
import com.pusula.service.ui.theme.Info
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning

@Composable
fun ProfileScreen(
    sessionManager: SessionManager,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val session by sessionManager.state.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.md,
            bottom = Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
    ) {
        item {
            AppHeroCard(
                eyebrow = "Hesap",
                title = session.fullName.ifBlank { "Kullanıcı" },
                subtitle = listOfNotNull(
                    session.role.takeIf { it.isNotBlank() }?.let { translateRole(it) },
                    session.companyName?.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifBlank { "Profil bilgileri" },
                badge = session.planType.takeIf { it.isNotBlank() }?.let { "$it Plan" }
            )
        }

        item {
            AppDashboardSection(title = "Profil Bilgileri") {
                AppGhostCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ProfileLine(
                            icon = Icons.Outlined.Person,
                            tint = Info,
                            label = "Ad Soyad",
                            value = session.fullName.ifBlank { "-" }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ProfileLine(
                            icon = Icons.Outlined.Badge,
                            tint = AccentPurple,
                            label = "Rol",
                            value = session.role.takeIf { it.isNotBlank() }?.let { translateRole(it) } ?: "-"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ProfileLine(
                            icon = Icons.Outlined.Apartment,
                            tint = AccentCyan,
                            label = "Şirket",
                            value = session.companyName ?: "-"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ProfileLine(
                            icon = Icons.Outlined.Star,
                            tint = AccentOrange,
                            label = "Plan",
                            value = session.planType.ifBlank { "-" }
                        )
                    }
                }
            }
        }

        item {
            AppDashboardSection(title = "Hesap Durumu") {
                AppGhostCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        val days = session.trialDaysRemaining ?: 0
                        ProfileLine(
                            icon = Icons.Outlined.Star,
                            tint = if (days > 7) Success else if (days > 0) Warning else ErrorTone,
                            label = "Deneme Süresi",
                            value = "$days gün"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ProfileLine(
                            icon = Icons.Outlined.Badge,
                            tint = if (session.isReadOnly) Warning else Success,
                            label = "Erişim",
                            value = if (session.isReadOnly) "Sadece Görüntüleme" else "Tam Erişim"
                        )
                    }
                }
            }
        }

        item {
            AppDashboardSection(title = "Güvenlik") {
                AppGhostCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorTone)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.padding(start = Spacing.xs))
                            Text("Çıkış Yap", color = Color.White)
                        }
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Outlined.DeleteForever, contentDescription = null, tint = ErrorTone)
                            Spacer(Modifier.padding(start = Spacing.xs))
                            Text("Hesabımı Sil", color = ErrorTone)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.lg)) }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Hesabımı Sil") },
            text = {
                Text("Hesabınızı ve tüm verilerinizi kalıcı olarak silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDeleteAccount()
                }) {
                    Text("Evet, Sil", color = ErrorTone)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
private fun ProfileLine(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AppIconBadge(icon = icon, tint = tint, size = 32.dp, iconSize = 16.dp, cornerRadius = 10.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

private fun translateRole(role: String): String = when (role.uppercase()) {
    "ADMIN" -> "Yönetici"
    "TECHNICIAN" -> "Teknisyen"
    "OWNER" -> "Şirket Sahibi"
    else -> role
}

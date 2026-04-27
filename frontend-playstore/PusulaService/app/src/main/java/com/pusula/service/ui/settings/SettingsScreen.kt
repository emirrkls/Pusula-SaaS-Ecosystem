package com.pusula.service.ui.settings

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.GroupOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.core.SessionManager
import com.pusula.service.core.readOnlyProtected
import com.pusula.service.data.model.CompanyDTO
import com.pusula.service.data.model.UserDTO
import com.pusula.service.data.model.VehicleDTO
import com.pusula.service.ui.components.AppDashboardSection
import com.pusula.service.ui.components.AppDestructiveButton
import com.pusula.service.ui.components.AppEmptyState
import com.pusula.service.ui.components.AppGhostCard
import com.pusula.service.ui.components.AppHeroCard
import com.pusula.service.ui.components.AppIconBadge
import com.pusula.service.ui.components.AppInitialsAvatar
import com.pusula.service.ui.components.AppRoleBadge
import com.pusula.service.ui.components.AppPrimaryButton
import com.pusula.service.ui.components.AppTextField
import com.pusula.service.ui.theme.AccentCyan
import com.pusula.service.ui.theme.AccentPurple
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.ui.theme.Success
import com.pusula.service.ui.theme.Warning
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

private val settingsTabs = listOf("Kullanıcılar", "Araçlar", "Firma")
private val roleOptions = listOf("COMPANY_ADMIN", "TECHNICIAN")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by sessionManager.state.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(settingsTabs.first()) }
    var editingUser by remember { mutableStateOf<UserDTO?>(null) }
    var editingVehicle by remember { mutableStateOf<VehicleDTO?>(null) }
    var showUserDialog by remember { mutableStateOf(false) }
    var showVehicleDialog by remember { mutableStateOf(false) }
    var passwordResetUserId by remember { mutableStateOf<Long?>(null) }
    var deleteUserId by remember { mutableStateOf<Long?>(null) }
    var deleteVehicleId by remember { mutableStateOf<Long?>(null) }
    var signatureUploadUserId by remember { mutableStateOf<Long?>(null) }

    val userSignaturePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val userId = signatureUploadUserId
        signatureUploadUserId = null
        if (uri != null && userId != null) {
            createMultipartFromUri(context, uri, "file")?.let { part ->
                viewModel.uploadUserSignature(userId, part)
            }
        }
    }
    val companyLogoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            createMultipartFromUri(context, uri, "file")?.let { part ->
                viewModel.uploadCompanyLogo(part)
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.loadSettings() }
    LaunchedEffect(uiState.userSavedAt) {
        if (uiState.userSavedAt != null) {
            showUserDialog = false
            editingUser = null
            viewModel.consumeUserSavedEvent()
        }
    }
    LaunchedEffect(uiState.vehicleSavedAt) {
        if (uiState.vehicleSavedAt != null) {
            showVehicleDialog = false
            editingVehicle = null
            viewModel.consumeVehicleSavedEvent()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Hesap & Ayarlar") }) }) { padding ->
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.refreshing,
                onRefresh = { viewModel.loadSettings(refresh = true) },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
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
                            eyebrow = "Yönetim",
                            title = uiState.company?.name?.takeIf { it.isNotBlank() } ?: "Ayarlar",
                            subtitle = "${uiState.users.size} kullanıcı • ${uiState.vehicles.size} araç",
                            badge = session.planType.takeIf { it.isNotBlank() }?.let { "$it Plan" }
                        )
                    }
                    item {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            settingsTabs.forEach { tab ->
                                SegmentedButton(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                        index = settingsTabs.indexOf(tab),
                                        count = settingsTabs.size
                                    ),
                                    label = { Text(tab) }
                                )
                            }
                        }
                    }
                    if (uiState.error != null) {
                        item {
                            AppEmptyState(
                                title = "Bir hata oluştu",
                                subtitle = uiState.error,
                                icon = Icons.Outlined.GroupOff,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    when (selectedTab) {
                        "Kullanıcılar" -> {
                            item {
                                AppDashboardSection(
                                    title = "Kullanıcılar",
                                    subtitle = "${uiState.users.size} kayıt"
                                ) {
                                    AppPrimaryButton(
                                        text = "Yeni Kullanıcı",
                                        onClick = {
                                            editingUser = null
                                            showUserDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth().readOnlyProtected(session.isReadOnly)
                                    )
                                }
                            }
                            if (uiState.users.isEmpty()) {
                                item {
                                    AppEmptyState(
                                        title = "Kullanıcı yok",
                                        subtitle = "İlk kullanıcıyı yukarıdaki düğmeden ekleyebilirsiniz.",
                                        icon = Icons.Outlined.GroupOff,
                                        tint = AccentPurple
                                    )
                                }
                            }
                            items(uiState.users, key = { it.id ?: it.username }) { user ->
                                UserCard(
                                    user = user,
                                    readOnly = session.isReadOnly,
                                    onEdit = {
                                        editingUser = user
                                        showUserDialog = true
                                    },
                                    onDelete = {
                                        if (user.id != null) deleteUserId = user.id
                                    },
                                    onResetPassword = {
                                        if (user.id != null) passwordResetUserId = user.id
                                    },
                                    onUploadSignature = {
                                        if (user.id != null) {
                                            signatureUploadUserId = user.id
                                            userSignaturePicker.launch("image/*")
                                        }
                                    }
                                )
                            }
                        }

                        "Araçlar" -> {
                            item {
                                AppDashboardSection(
                                    title = "Araçlar",
                                    subtitle = "${uiState.vehicles.size} araç"
                                ) {
                                    AppPrimaryButton(
                                        text = "Yeni Araç",
                                        onClick = {
                                            editingVehicle = null
                                            showVehicleDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth().readOnlyProtected(session.isReadOnly)
                                    )
                                }
                            }
                            if (uiState.vehicles.isEmpty()) {
                                item {
                                    AppEmptyState(
                                        title = "Araç yok",
                                        subtitle = "Henüz kayıtlı araç bulunmuyor.",
                                        icon = Icons.Outlined.DirectionsCar,
                                        tint = AccentPurple
                                    )
                                }
                            }
                            items(uiState.vehicles, key = { it.id ?: it.licensePlate }) { vehicle ->
                                VehicleCard(
                                    vehicle = vehicle,
                                    readOnly = session.isReadOnly,
                                    onEdit = {
                                        editingVehicle = vehicle
                                        showVehicleDialog = true
                                    },
                                    onDelete = {
                                        if (vehicle.id != null) deleteVehicleId = vehicle.id
                                    }
                                )
                            }
                        }

                        else -> {
                            item {
                                CompanyCard(
                                    company = uiState.company,
                                    readOnly = session.isReadOnly,
                                    saving = uiState.saving,
                                    onSave = { name, phone, email, address ->
                                        viewModel.saveCompany(name, phone, email, address)
                                    },
                                    onUploadLogo = {
                                        companyLogoPicker.launch("image/*")
                                    }
                                )
                            }
                        }
                    }

                    item {
                        AppDashboardSection(title = "Hesap İşlemleri") {
                            AppGhostCard {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    AppPrimaryButton(text = "Çıkış Yap", onClick = onLogout, modifier = Modifier.fillMaxWidth())
                                    AppDestructiveButton(text = "Hesabımı Sil", onClick = onDeleteAccount, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUserDialog) {
        UserDialog(
            initial = editingUser,
            saving = uiState.saving,
            readOnly = session.isReadOnly,
            onDismiss = {
                showUserDialog = false
                editingUser = null
            },
            onSave = { id, username, fullName, role, password ->
                viewModel.saveUser(id, username, fullName, role, password)
            }
        )
    }

    if (showVehicleDialog) {
        VehicleDialog(
            initial = editingVehicle,
            saving = uiState.saving,
            readOnly = session.isReadOnly,
            onDismiss = {
                showVehicleDialog = false
                editingVehicle = null
            },
            onSave = { id, plate, driverName, isActive ->
                viewModel.saveVehicle(id, plate, driverName, isActive)
            }
        )
    }

    if (deleteUserId != null) {
        ConfirmDialog(
            title = "Kullanıcı silinsin mi?",
            message = "Seçilen kullanıcı silinecek.",
            onDismiss = { deleteUserId = null },
            onConfirm = {
                viewModel.deleteUser(deleteUserId ?: return@ConfirmDialog)
                deleteUserId = null
            }
        )
    }

    if (deleteVehicleId != null) {
        ConfirmDialog(
            title = "Araç silinsin mi?",
            message = "Seçilen araç silinecek.",
            onDismiss = { deleteVehicleId = null },
            onConfirm = {
                viewModel.deleteVehicle(deleteVehicleId ?: return@ConfirmDialog)
                deleteVehicleId = null
            }
        )
    }

    if (passwordResetUserId != null) {
        PasswordResetDialog(
            onDismiss = { passwordResetUserId = null },
            onSubmit = { password ->
                viewModel.resetPassword(passwordResetUserId ?: return@PasswordResetDialog, password)
                passwordResetUserId = null
            }
        )
    }
}

@Composable
private fun UserCard(
    user: UserDTO,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onResetPassword: () -> Unit,
    onUploadSignature: () -> Unit
) {
    AppGhostCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            AppInitialsAvatar(text = user.fullName ?: user.username)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = user.fullName ?: user.username,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            AppRoleBadge(role = user.role ?: "UNKNOWN")
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                modifier = Modifier.weight(1f).readOnlyProtected(readOnly),
                onClick = onEdit
            ) { Text("Düzenle") }
            OutlinedButton(
                modifier = Modifier.weight(1f).readOnlyProtected(readOnly),
                onClick = onResetPassword
            ) { Text("Şifre") }
            OutlinedButton(
                modifier = Modifier.weight(1f).readOnlyProtected(readOnly),
                onClick = onUploadSignature
            ) { Text("İmza") }
        }
        TextButton(
            modifier = Modifier.align(Alignment.End).readOnlyProtected(readOnly),
            onClick = onDelete
        ) {
            Text("Sil", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun VehicleCard(
    vehicle: VehicleDTO,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AppGhostCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            AppIconBadge(icon = Icons.Outlined.DirectionsCar, tint = AccentCyan)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = vehicle.licensePlate,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                if (!vehicle.driverName.isNullOrBlank()) {
                    Text(
                        text = "Şoför: ${vehicle.driverName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = if (vehicle.isActive) "Aktif" else "Pasif",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (vehicle.isActive) Success else Warning
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                modifier = Modifier.weight(1f).readOnlyProtected(readOnly),
                onClick = onEdit
            ) { Text("Düzenle") }
            OutlinedButton(
                modifier = Modifier.weight(1f).readOnlyProtected(readOnly),
                onClick = onDelete
            ) {
                Text("Sil", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CompanyCard(
    company: CompanyDTO?,
    readOnly: Boolean,
    saving: Boolean,
    onSave: (String, String, String, String) -> Unit,
    onUploadLogo: () -> Unit
) {
    var name by remember(company?.id) { mutableStateOf(company?.name.orEmpty()) }
    var phone by remember(company?.id) { mutableStateOf(company?.phone.orEmpty()) }
    var email by remember(company?.id) { mutableStateOf(company?.email.orEmpty()) }
    var address by remember(company?.id) { mutableStateOf(company?.address.orEmpty()) }

    AppGhostCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "Firma Bilgileri",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            AppTextField(value = name, onValueChange = { name = it }, label = "Firma Adı", modifier = Modifier.fillMaxWidth())
            AppTextField(value = phone, onValueChange = { phone = it }, label = "Telefon", modifier = Modifier.fillMaxWidth())
            AppTextField(value = email, onValueChange = { email = it }, label = "E-posta", modifier = Modifier.fillMaxWidth())
            AppTextField(value = address, onValueChange = { address = it }, label = "Adres", modifier = Modifier.fillMaxWidth())
            Text(
                "Logo: ${company?.logoUrl ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppPrimaryButton(
                text = "Logo Yükle",
                onClick = onUploadLogo,
                enabled = !saving && !readOnly,
                modifier = Modifier.fillMaxWidth().readOnlyProtected(readOnly)
            )
            AppPrimaryButton(
                text = if (saving) "Kaydediliyor..." else "Kaydet",
                onClick = { onSave(name.trim(), phone.trim(), email.trim(), address.trim()) },
                enabled = !saving && !readOnly && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().readOnlyProtected(readOnly)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDialog(
    initial: UserDTO?,
    saving: Boolean,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, String, String) -> Unit
) {
    var username by remember(initial?.id) { mutableStateOf(initial?.username.orEmpty()) }
    var fullName by remember(initial?.id) { mutableStateOf(initial?.fullName.orEmpty()) }
    var role by remember(initial?.id) { mutableStateOf(initial?.role ?: "TECHNICIAN") }
    var password by remember(initial?.id) { mutableStateOf("") }
    var roleExpanded by remember(initial?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni Kullanıcı" else "Kullanıcı Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Kullanıcı Adı") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Ad Soyad") }, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = roleExpanded, onExpandedChange = { roleExpanded = it }) {
                    OutlinedTextField(
                        value = role,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                        roleOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    role = option
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (initial == null) "Şifre" else "Yeni Şifre (opsiyonel)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && !readOnly && username.isNotBlank() && fullName.isNotBlank() && (initial != null || password.isNotBlank()),
                onClick = { onSave(initial?.id, username.trim(), fullName.trim(), role, password.trim()) }
            ) { Text(if (saving) "Kaydediliyor..." else "Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun VehicleDialog(
    initial: VehicleDTO?,
    saving: Boolean,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, Boolean) -> Unit
) {
    var plate by remember(initial?.id) { mutableStateOf(initial?.licensePlate.orEmpty()) }
    var driverName by remember(initial?.id) { mutableStateOf(initial?.driverName.orEmpty()) }
    var isActive by remember(initial?.id) { mutableStateOf(initial?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni Araç" else "Araç Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text("Plaka") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = driverName, onValueChange = { driverName = it }, label = { Text("Şoför Adı") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aktif")
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && !readOnly && plate.isNotBlank(),
                onClick = { onSave(initial?.id, plate.trim(), driverName.trim(), isActive) }
            ) { Text(if (saving) "Kaydediliyor..." else "Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun PasswordResetDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Şifre Sıfırla") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Yeni Şifre") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = password.isNotBlank(), onClick = { onSubmit(password.trim()) }) { Text("Sıfırla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Onayla") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

private fun createMultipartFromUri(
    context: Context,
    uri: Uri,
    formField: String
): MultipartBody.Part? {
    return runCatching {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File.createTempFile("upload_", ".bin", context.cacheDir)
        tempFile.outputStream().use { output -> inputStream.copyTo(output) }
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        MultipartBody.Part.createFormData(formField, tempFile.name, requestBody)
    }.getOrNull()
}

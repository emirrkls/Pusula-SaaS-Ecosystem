package com.pusula.service.ui.technician

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketFromOperationDialog(
    customers: List<CustomerDTO>,
    technicians: List<TechnicianDTO>,
    customersLoading: Boolean,
    customerSaving: Boolean,
    creatingTicket: Boolean,
    onDismiss: () -> Unit,
    onRefreshCustomers: () -> Unit,
    onQuickCreateCustomer: (String, String, String, (CustomerDTO) -> Unit) -> Unit,
    onCreateTicket: (Long, String, String, Long?) -> Unit
) {
    var customerInput by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var customerExpanded by remember { mutableStateOf(false) }
    var showQuickCreate by remember { mutableStateOf(false) }

    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var technicianExpanded by remember { mutableStateOf(false) }
    var selectedTechName by remember { mutableStateOf("Atamasız") }
    var selectedTechId by remember { mutableStateOf<Long?>(null) }

    val q = customerInput.trim().lowercase()
    val filteredCustomers = remember(customers, q) {
        customers.filter {
            q.isBlank() ||
                it.name.lowercase().contains(q) ||
                (it.phone?.lowercase()?.contains(q) == true) ||
                (it.address?.lowercase()?.contains(q) == true)
        }.sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Operasyondan Servis Fişi Oluştur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ExposedDropdownMenuBox(
                    expanded = customerExpanded,
                    onExpandedChange = { customerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = customerInput,
                        onValueChange = {
                            customerInput = it
                            selectedCustomerId = null
                            customerExpanded = true
                        },
                        label = { Text("Müşteri ara ve seç") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerExpanded) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = customerExpanded,
                        onDismissRequest = { customerExpanded = false }
                    ) {
                        filteredCustomers.take(30).forEach { customer ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${customer.name} • ${customer.phone ?: "Telefon yok"}",
                                        maxLines = 1
                                    )
                                },
                                onClick = {
                                    selectedCustomerId = customer.id
                                    customerInput = customer.name
                                    customerExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = onRefreshCustomers,
                        enabled = !customersLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (customersLoading) "Yükleniyor..." else "Müşterileri Yenile")
                    }
                    Button(
                        onClick = { showQuickCreate = true },
                        enabled = !customerSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text(" Hızlı Müşteri")
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Servis fişi açıklaması") },
                    leadingIcon = { Icon(Icons.Outlined.Receipt, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notlar (opsiyonel)") },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = technicianExpanded, onExpandedChange = { technicianExpanded = it }) {
                    OutlinedTextField(
                        value = selectedTechName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Teknisyen ataması") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = technicianExpanded) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = technicianExpanded, onDismissRequest = { technicianExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Atamasız") },
                            onClick = {
                                selectedTechId = null
                                selectedTechName = "Atamasız"
                                technicianExpanded = false
                            }
                        )
                        technicians.forEach { tech ->
                            DropdownMenuItem(
                                text = { Text(tech.fullName ?: "Teknisyen #${tech.id}") },
                                onClick = {
                                    selectedTechId = tech.id
                                    selectedTechName = tech.fullName ?: "Teknisyen #${tech.id}"
                                    technicianExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedCustomerId != null && description.isNotBlank() && !creatingTicket,
                onClick = {
                    onCreateTicket(
                        selectedCustomerId ?: return@TextButton,
                        description.trim(),
                        notes.trim(),
                        selectedTechId
                    )
                }
            ) { Text(if (creatingTicket) "Oluşturuluyor..." else "Fiş Oluştur") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )

    if (showQuickCreate) {
        QuickCustomerCreateDialog(
            saving = customerSaving,
            onDismiss = { showQuickCreate = false },
            onCreate = { name, phone, address ->
                onQuickCreateCustomer(name, phone, address) { created ->
                    showQuickCreate = false
                    selectedCustomerId = created.id
                    customerInput = created.name
                }
            }
        )
    }
}

@Composable
private fun QuickCustomerCreateDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hızlı Müşteri Oluştur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ad Soyad") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefon") },
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adres") },
                    leadingIcon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = { onCreate(name.trim(), phone.trim(), address.trim()) }
            ) { Text(if (saving) "Kaydediliyor..." else "Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

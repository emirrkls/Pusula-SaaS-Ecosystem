package com.pusula.service.ui.technician

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pusula.service.data.model.FieldTicketDTO
import com.pusula.service.data.model.TechnicianDTO
import com.pusula.service.ui.theme.Spacing
import com.pusula.service.util.safeForComposeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkAssignDialog(
    tickets: List<FieldTicketDTO>,
    technicians: List<TechnicianDTO>,
    assigning: Boolean,
    onDismiss: () -> Unit,
    onAssignSelected: (ticketIds: List<Long>, technicianId: Long) -> Unit
) {
    var selectedIds by remember(tickets.map { it.id }) { mutableStateOf(setOf<Long>()) }
    var techExpanded by remember { mutableStateOf(false) }
    var techName by remember { mutableStateOf("Teknisyen seç") }
    var techId by remember { mutableStateOf<Long?>(null) }

    val visibleIds = tickets.map { it.id }.toSet()
    val effectiveSelection = selectedIds.intersect(visibleIds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Toplu Atama",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "${tickets.size} atama bekleyen fiş. Seçip teknisyene atayın; aynı pencerede farklı gruplara farklı teknisyen atayabilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TextButton(
                        onClick = { selectedIds = visibleIds },
                        enabled = tickets.isNotEmpty()
                    ) {
                        Text("Tümünü seç")
                    }
                    TextButton(
                        onClick = { selectedIds = emptySet() },
                        enabled = effectiveSelection.isNotEmpty()
                    ) {
                        Text("Temizle")
                    }
                    Text(
                        text = "${effectiveSelection.size} seçili",
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(tickets, key = { it.id }) { ticket ->
                        BulkAssignTicketRow(
                            ticket = ticket,
                            checked = ticket.id in effectiveSelection,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) {
                                    selectedIds + ticket.id
                                } else {
                                    selectedIds - ticket.id
                                }
                            }
                        )
                    }
                }
                HorizontalDivider()
                ExposedDropdownMenuBox(
                    expanded = techExpanded,
                    onExpandedChange = { techExpanded = it }
                ) {
                    OutlinedTextField(
                        value = techName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Teknisyen") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = techExpanded) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = techExpanded,
                        onDismissRequest = { techExpanded = false }
                    ) {
                        technicians.forEach { tech ->
                            val label = (tech.fullName ?: "Teknisyen #${tech.id}")
                                .safeForComposeText("Teknisyen #${tech.id}")
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    techId = tech.id
                                    techName = label
                                    techExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = effectiveSelection.isNotEmpty() && techId != null && !assigning,
                onClick = {
                    val id = techId ?: return@TextButton
                    onAssignSelected(effectiveSelection.toList(), id)
                    selectedIds = emptySet()
                }
            ) {
                Text(if (assigning) "Atanıyor..." else "Seçilenleri Ata")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}

@Composable
private fun BulkAssignTicketRow(
    ticket: FieldTicketDTO,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ticket.customerName.safeForComposeText("Müşteri"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1
            )
            val detail = buildString {
                ticket.customerPhone?.takeIf { it.isNotBlank() }?.let { append(it.safeForComposeText()) }
                ticket.scheduledDate?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it.safeForComposeText())
                }
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

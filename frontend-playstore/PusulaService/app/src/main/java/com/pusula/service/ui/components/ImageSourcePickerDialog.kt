package com.pusula.service.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ImageSourcePickerDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Görsel kaynağı") },
        text = { Text("Fotoğrafı kameradan çekmek veya galeriden seçmek için bir seçenek belirleyin.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onCamera()
            }) { Text("Kamera") }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onGallery()
            }) { Text("Galeri") }
        }
    )
}

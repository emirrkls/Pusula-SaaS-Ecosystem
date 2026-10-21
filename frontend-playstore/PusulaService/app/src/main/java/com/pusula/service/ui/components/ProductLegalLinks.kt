package com.pusula.service.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.pusula.service.core.GuideReadOnlyGate
import com.pusula.service.core.ProductLegal

@Composable
fun ProductLegalLinks(modifier: Modifier = Modifier, onOpenLink: ((String) -> Unit)? = null) {
    val uriHandler = LocalUriHandler.current
    val guide by GuideReadOnlyGate.active.collectAsState()
    var error by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        listOf("Gizlilik Politikası" to ProductLegal.PRIVACY_URL,
            "Kullanım Şartları" to ProductLegal.TERMS_URL).forEach { (label, url) ->
            TextButton(enabled = !guide, modifier = Modifier.fillMaxWidth(), onClick = {
                error = runCatching { if (onOpenLink != null) onOpenLink(url) else uriHandler.openUri(url) }.isFailure
            }) { Text(label) }
        }
        if (error) Text("Bağlantı açılamadı. pusulaiklimlendirme.store adresinden ulaşabilirsiniz.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

package com.pusula.service.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pusula.service.ui.components.AppPrimaryButton
import com.pusula.service.ui.theme.BrandCyan
import com.pusula.service.ui.theme.BrandGray
import com.pusula.service.ui.theme.BrandNavy
import com.pusula.service.ui.theme.Spacing

@Composable
fun RegisterScreen(
    uiState: AuthUiState,
    onRegister: (String, String, String, String) -> Unit,
    onGoogleRegister: (String?) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = BrandCyan,
        focusedLabelColor = BrandCyan,
        cursorColor = BrandCyan,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline
    )

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Hesap Oluştur",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy
                    )
                )
                Text(
                    text = "14 gün ücretsiz deneyin",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BrandGray
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Ad Soyad") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-posta") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Kullanıcı Adı") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Şifre") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        singleLine = true
                    )

                    uiState.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    AppPrimaryButton(
                        text = if (uiState.isLoading) "Kaydediliyor…" else "Kayıt Ol",
                        onClick = { onRegister(email, username, password, fullName) },
                        enabled = !uiState.isLoading &&
                            fullName.isNotBlank() && email.isNotBlank() &&
                            username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = BrandCyan,
                            strokeWidth = 2.dp
                        )
                    }

                    TextButton(
                        onClick = { onGoogleRegister(username.ifBlank { null }) },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Google ile Kayıt Ol", color = BrandNavy, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

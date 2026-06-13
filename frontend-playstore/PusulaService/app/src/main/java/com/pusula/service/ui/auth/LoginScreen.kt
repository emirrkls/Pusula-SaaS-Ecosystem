package com.pusula.service.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pusula.service.ui.components.AppPrimaryButton
import com.pusula.service.ui.theme.BrandCyan
import com.pusula.service.ui.theme.BrandGray
import com.pusula.service.ui.theme.BrandNavy
import com.pusula.service.ui.theme.Spacing

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onLogin: (String, String, String?) -> Unit,
    onGoogleLogin: () -> Unit,
    onNavigateRegister: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var orgCode by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = BrandCyan,
        focusedLabelColor = BrandCyan,
        cursorColor = BrandCyan,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
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
                    text = "Pusula",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy
                    )
                )
                Text(
                    text = "Servis yönetim ekosistemi",
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("Bireysel", "Kurumsal").forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                onClick = { selectedTab = index },
                                selected = selectedTab == index,
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = BrandCyan.copy(alpha = 0.12f),
                                    activeContentColor = BrandNavy
                                )
                            ) { Text(label) }
                        }
                    }

                    if (selectedTab == 1) {
                        OutlinedTextField(
                            value = orgCode,
                            onValueChange = { orgCode = it.trim() },
                            label = { Text("Kurum Kodu") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(if (selectedTab == 0) "E-posta veya kullanıcı adı" else "Kullanıcı adı") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { new ->
                            password = new.filter { ch -> ch != '\n' && ch != '\r' }
                        },
                        label = { Text("Şifre") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        singleLine = true
                    )

                    uiState.errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    AppPrimaryButton(
                        text = if (uiState.isLoading) "Giriş yapılıyor…" else "Giriş Yap",
                        onClick = {
                            onLogin(username, password, if (selectedTab == 1) orgCode else null)
                        },
                        enabled = !uiState.isLoading && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = BrandCyan,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TextButton(
                        onClick = onGoogleLogin,
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(
                            "Google ile Giriş Yap",
                            color = BrandNavy,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    TextButton(onClick = onNavigateRegister, enabled = !uiState.isLoading) {
                        Text("Hesabınız yok mu? ", color = BrandGray)
                        Text("Ücretsiz Kayıt Ol", color = BrandCyan, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

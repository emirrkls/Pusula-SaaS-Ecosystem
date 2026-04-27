package com.pusula.service.ui.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pusula.service.ui.theme.DarkEnd
import com.pusula.service.ui.theme.DarkStart

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(DarkStart, DarkEnd)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Pusula Servis", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Servis yönetim ekosistemine hoş geldiniz", color = Color.White.copy(alpha = 0.7f))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Bireysel", "Kurumsal").forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        onClick = { selectedTab = index },
                        selected = selectedTab == index
                    ) { Text(label) }
                }
            }

            if (selectedTab == 1) {
                OutlinedTextField(
                    value = orgCode,
                    onValueChange = { orgCode = it },
                    label = { Text("Kurum Kodu") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(if (selectedTab == 0) "E-posta veya kullanıcı adı" else "Kullanıcı adı") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            uiState.errorMessage?.let {
                Text(it, color = Color.Red)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF22D3EE))))
                    .clickable(enabled = !uiState.isLoading && username.isNotBlank() && password.isNotBlank()) {
                        onLogin(username, password, if (selectedTab == 1) orgCode else null)
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text("Giriş Yap", color = Color.White)
                }
            }

            if (selectedTab == 0) {
                TextButton(
                    onClick = onGoogleLogin,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Google ile Giriş Yap")
                }
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text("Hesabınız yok mu? ", color = Color.White.copy(alpha = 0.7f))
                    TextButton(onClick = onNavigateRegister) {
                        Text("Ücretsiz Kayıt Ol")
                    }
                }
            }
        }
    }
}

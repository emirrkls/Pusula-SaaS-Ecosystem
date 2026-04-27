package com.pusula.service.ui.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.pusula.service.BuildConfig
import com.pusula.service.core.SessionManager
import com.pusula.service.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun tryRestoreSession() = sessionManager.tryRestoreSession()

    fun login(username: String, password: String, orgCode: String?) = viewModelScope.launch {
        _uiState.value = AuthUiState(isLoading = true)
        runCatching {
            if (!orgCode.isNullOrBlank()) {
                authRepository.loginCorporate(orgCode, username, password)
            } else {
                authRepository.login(username, password)
            }
        }.onFailure {
            _uiState.value = AuthUiState(errorMessage = it.message ?: "Giriş sırasında hata oluştu")
        }.onSuccess {
            _uiState.value = AuthUiState()
        }
    }

    fun register(email: String, username: String, password: String, fullName: String) = viewModelScope.launch {
        _uiState.value = AuthUiState(isLoading = true)
        runCatching {
            authRepository.registerIndividual(email, username, password, fullName)
        }.onFailure {
            _uiState.value = AuthUiState(errorMessage = it.message ?: "Kayıt sırasında hata oluştu")
        }.onSuccess {
            _uiState.value = AuthUiState()
        }
    }

    fun registerWithGoogle(context: Context, preferredUsername: String?) = viewModelScope.launch {
        _uiState.value = AuthUiState(isLoading = true)
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            _uiState.value = AuthUiState(errorMessage = "Google OAuth client ID ayarlı değil.")
            return@launch
        }

        val activity = context.findComponentActivity()
        if (activity == null) {
            _uiState.value = AuthUiState(errorMessage = "Google girişi için Activity gerekli.")
            return@launch
        }

        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        runCatching {
            val result = withContext(Dispatchers.Main) {
                credentialManager.getCredential(
                    context = activity,
                    request = request
                )
            }
            val idToken = extractGoogleIdToken(result.credential)
            authRepository.authenticateWithGoogle(
                idToken = idToken,
                preferredUsername = preferredUsername
            )
        }.onFailure { error ->
            val message = when (error) {
                is GetCredentialException -> {
                    val detail = error.message?.takeIf { it.isNotBlank() }
                    if (detail != null) "Google girişi başarısız: $detail" else "Google hesabı seçilemedi"
                }
                is GoogleIdTokenParsingException -> "Google token okunamadı"
                else -> error.message ?: "Google ile doğrulama sırasında hata oluştu"
            }
            _uiState.value = AuthUiState(errorMessage = message)
        }.onSuccess {
            _uiState.value = AuthUiState()
        }
    }

    fun loginWithGoogle(context: Context) = registerWithGoogle(context, preferredUsername = null)

    private fun extractGoogleIdToken(credential: Credential): String {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            return googleCredential.idToken
        }
        if (credential is GoogleIdTokenCredential) {
            return credential.idToken
        }
        throw IllegalStateException("Beklenmeyen credential türü: ${credential::class.java.simpleName}")
    }

    private tailrec fun Context.findComponentActivity(): ComponentActivity? {
        return when (this) {
            is ComponentActivity -> this
            is ContextWrapper -> baseContext.findComponentActivity()
            else -> null
        }
    }

    fun deleteAccount() = viewModelScope.launch {
        _uiState.value = AuthUiState(isLoading = true)
        runCatching {
            authRepository.deleteAccount()
        }.onFailure {
            _uiState.value = AuthUiState(errorMessage = it.message ?: "Hesap silinirken hata oluştu")
        }.onSuccess {
            _uiState.value = AuthUiState()
        }
    }
}

package com.pusula.service.ui.auth

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.pusula.service.BuildConfig
import com.pusula.service.core.SessionManager
import com.pusula.service.data.repository.AuthRepository
import com.pusula.service.util.toAuthUserMessage
import com.pusula.service.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private var pendingPreferredUsername: String? = null

    fun tryRestoreSession() = viewModelScope.launch {
        sessionManager.tryRestoreSession()
        if (!sessionManager.state.value.isAuthenticated) return@launch
        runCatching {
            authRepository.syncRestoredSession()
        }.onFailure {
            sessionManager.logout()
        }
    }

    fun login(username: String, password: String, orgCode: String?) = viewModelScope.launch {
        sessionManager.logout()
        _uiState.value = AuthUiState(isLoading = true)
        val u = username.trim()
        val p = password.trim()
        val org = orgCode?.trim()?.takeIf { it.isNotBlank() }
        runCatching {
            if (!org.isNullOrBlank()) {
                authRepository.loginCorporate(org, u, p)
            } else {
                authRepository.login(u, p)
            }
        }.onFailure {
            _uiState.value = AuthUiState(errorMessage = it.toAuthUserMessage("Giriş sırasında hata oluştu"))
        }.onSuccess {
            _uiState.value = AuthUiState()
        }
    }

    fun register(email: String, username: String, password: String, fullName: String) = viewModelScope.launch {
        sessionManager.logout()
        _uiState.value = AuthUiState(isLoading = true)
        runCatching {
            authRepository.registerIndividual(
                email.trim(),
                username.trim(),
                password.trim(),
                fullName.trim()
            )
        }.onFailure {
            _uiState.value = AuthUiState(errorMessage = it.toAuthUserMessage("Kayıt sırasında hata oluştu"))
        }.onSuccess {
            _uiState.value = AuthUiState()
        }
    }

    fun loginWithGoogle(
        activity: ComponentActivity,
        launchSignIn: (Intent) -> Unit
    ) {
        pendingPreferredUsername = null
        startLegacyGoogleSignIn(activity, launchSignIn)
    }

    fun registerWithGoogle(
        activity: ComponentActivity,
        preferredUsername: String?,
        launchSignIn: (Intent) -> Unit
    ) {
        pendingPreferredUsername = preferredUsername?.trim()?.takeIf { it.isNotBlank() }
        startLegacyGoogleSignIn(activity, launchSignIn)
    }

    fun completeLegacyGoogleSignIn(data: Intent?) = viewModelScope.launch {
        if (!_uiState.value.isGoogleLoading) return@launch

        runCatching {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
                ?: throw IllegalStateException("Google kimlik jetonu alınamadı")
            Log.d(TAG, "Google idToken received, calling backend")
            authRepository.authenticateWithGoogle(
                idToken = idToken,
                preferredUsername = pendingPreferredUsername
            )
            runCatching { authRepository.refreshFeatureContext() }
                .onFailure { Log.w(TAG, "Feature context refresh failed after Google auth", it) }
            pendingPreferredUsername = null
        }.onFailure { error ->
            Log.e(TAG, "Google auth failed", error)
            reportGoogleAuthFailure(error)
        }.onSuccess {
            Log.d(TAG, "Google auth success")
            _uiState.value = AuthUiState()
            _userMessages.emit("Google ile giriş başarılı")
        }
    }

    private fun startLegacyGoogleSignIn(
        activity: ComponentActivity,
        launchSignIn: (Intent) -> Unit
    ) {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) {
            viewModelScope.launch { failGoogleAuth("Google OAuth client ID ayarlı değil.") }
            return
        }

        _uiState.value = AuthUiState(isGoogleLoading = true, errorMessage = null)
        Log.d(TAG, "Legacy Google auth started (clientId=${webClientId.take(12)}…)")

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(activity, options)

        viewModelScope.launch {
            client.signOut().addOnCompleteListener {
                launchSignIn(client.signInIntent)
            }
        }
    }

    private suspend fun failGoogleAuth(message: String) {
        pendingPreferredUsername = null
        _uiState.value = AuthUiState(errorMessage = message)
        _userMessages.emit(message)
    }

    private suspend fun reportGoogleAuthFailure(error: Throwable) {
        pendingPreferredUsername = null
        val message = when (error) {
            is ApiException -> when (error.statusCode) {
                10 -> "Google OAuth yapılandırması hatalı. Google Cloud Console'da " +
                    "com.pusula.service için Android istemcisine upload SHA-1 ekleyin: " +
                    "43:17:6D:30:6E:9C:F5:16:BC:16:5E:8A:E0:55:11:7C:A8:30:F6:7F"
                12501 -> "Google girişi iptal edildi."
                7 -> "Ağ hatası. İnternet bağlantınızı kontrol edin."
                else -> "Google girişi başarısız (kod ${error.statusCode})"
            }
            is IllegalStateException -> error.message ?: "Google ile doğrulama sırasında hata oluştu"
            else -> error.toAuthUserMessage("Google ile doğrulama sırasında hata oluştu")
        }
        failGoogleAuth(message)
    }

    private companion object {
        const val TAG = "PusulaAuth"
    }

    fun deleteAccount() = viewModelScope.launch {
        _uiState.value = AuthUiState(isLoading = true)
        runCatching {
            authRepository.deleteAccount()
        }.onFailure {
            _uiState.value = AuthUiState(errorMessage = it.toUserMessage("Hesap silinirken hata oluştu"))
        }.onSuccess {
            _uiState.value = AuthUiState()
        }
    }
}

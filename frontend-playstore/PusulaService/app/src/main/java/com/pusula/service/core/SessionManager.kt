package com.pusula.service.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pusula.service.data.model.AuthResponse
import com.pusula.service.data.model.QuotaDTO
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionState(
    val isAuthenticated: Boolean = false,
    val token: String? = null,
    val role: String = "",
    val fullName: String = "",
    val companyId: Int? = null,
    val companyName: String? = null,
    val planType: String = "CIRAK",
    val features: Map<String, Boolean> = emptyMap(),
    val quota: QuotaDTO? = null,
    val isReadOnly: Boolean = false,
    val trialDaysRemaining: Int? = null
) {
    val isAdmin: Boolean get() = role == "COMPANY_ADMIN" || role == "SUPER_ADMIN"
    val isTechnician: Boolean get() = role == "TECHNICIAN"
}

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "pusula_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun configure(response: AuthResponse) {
        securePrefs.edit()
            .putString("token", response.token)
            .apply()

        _state.update {
            it.copy(
                isAuthenticated = true,
                token = response.token,
                role = response.role,
                fullName = response.fullName.orEmpty(),
                companyId = response.companyId,
                companyName = response.companyName,
                planType = response.planType ?: "CIRAK",
                features = response.features ?: emptyMap(),
                quota = response.quota,
                isReadOnly = response.isReadOnly ?: false,
                trialDaysRemaining = response.trialDaysRemaining
            )
        }
    }

    fun logout() {
        securePrefs.edit().remove("token").apply()
        _state.value = SessionState()
    }

    fun tryRestoreSession() {
        val token = securePrefs.getString("token", null) ?: return
        _state.update {
            it.copy(
                isAuthenticated = true,
                token = token
            )
        }
    }
}

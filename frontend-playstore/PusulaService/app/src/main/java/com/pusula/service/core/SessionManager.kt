package com.pusula.service.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pusula.service.data.model.AuthProfileResponse
import com.pusula.service.data.model.AuthResponse
import com.pusula.service.data.model.QuotaDTO
import com.pusula.service.data.model.SubscriptionContextDto
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

/**
 * Oturum JWT’si [EncryptedSharedPreferences] ile şifrelenmiş olarak cihazda saklanır (dosya düz metin değildir).
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    /** OkHttp interceptors must not use [runBlocking]; use this for Authorization header. */
    @Volatile
    private var tokenCache: String? = null

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

    /** Persisted so cold start can restore admin vs technician shell before /auth/feature-context returns. */
    private companion object {
        const val PREF_ROLE = "session_role"
    }

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun bearerTokenSnapshot(): String? =
        tokenCache ?: securePrefs.getString("token", null).also { tokenCache = it }

    fun configure(response: AuthResponse) {
        tokenCache = response.token
        securePrefs.edit()
            .putString("token", response.token)
            .putString(PREF_ROLE, response.role)
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
        tokenCache = null
        securePrefs.edit()
            .remove("token")
            .remove(PREF_ROLE)
            .apply()
        _state.value = SessionState()
    }

    fun tryRestoreSession() {
        val token = securePrefs.getString("token", null) ?: return
        val cachedRole = securePrefs.getString(PREF_ROLE, "").orEmpty()
        tokenCache = token
        _state.update {
            it.copy(
                isAuthenticated = true,
                token = token,
                role = cachedRole
            )
        }
    }

    fun mergeProfile(profile: AuthProfileResponse) {
        _state.update {
            it.copy(
                role = profile.role?.takeIf { r -> r.isNotBlank() } ?: it.role,
                fullName = profile.fullName ?: it.fullName,
                companyId = profile.companyId?.toInt() ?: it.companyId,
                companyName = profile.companyName ?: it.companyName
            )
        }
        val r = _state.value.role
        if (r.isNotBlank()) {
            securePrefs.edit().putString(PREF_ROLE, r).apply()
        }
    }

    fun mergeSubscription(ctx: SubscriptionContextDto) {
        _state.update {
            it.copy(
                planType = ctx.planType ?: it.planType,
                features = ctx.features ?: it.features,
                quota = ctx.quota ?: it.quota,
                isReadOnly = ctx.isReadOnly ?: it.isReadOnly,
                trialDaysRemaining = ctx.trialDaysRemaining ?: it.trialDaysRemaining
            )
        }
    }
}

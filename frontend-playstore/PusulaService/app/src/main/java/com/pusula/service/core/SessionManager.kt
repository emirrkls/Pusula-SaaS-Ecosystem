package com.pusula.service.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pusula.service.data.model.AuthProfileResponse
import com.pusula.service.data.model.AuthResponse
import com.pusula.service.data.model.QuotaDTO
import com.pusula.service.data.model.SubscriptionContextDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
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
    @ApplicationContext private val context: Context
) {
    /** OkHttp interceptors must not use [runBlocking]; use this for Authorization header. */
    @Volatile
    private var tokenCache: String? = null

    @Volatile
    private var securePrefs: SharedPreferences = openSecurePrefs(context)

    /** Persisted so cold start can restore admin vs technician shell before /auth/feature-context returns. */
    private companion object {
        const val PREF_NAME = "pusula_secure_prefs"
        const val PREF_ROLE = "session_role"
        const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"

        fun openSecurePrefs(context: Context): SharedPreferences {
            return try {
                createSecurePrefs(context)
            } catch (_: Exception) {
                clearCorruptedSecureStorage(context)
                createSecurePrefs(context)
            }
        }

        private fun createSecurePrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        private fun clearCorruptedSecureStorage(context: Context) {
            context.deleteSharedPreferences(PREF_NAME)
            context.deleteSharedPreferences(MASTER_KEY_ALIAS)
        }

        fun isSecureStorageError(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                if (current is AEADBadTagException ||
                    current is GeneralSecurityException ||
                    current.javaClass.name.contains("KeyStoreException", ignoreCase = true)
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }
    }

    private fun resetSecurePrefs() {
        clearCorruptedSecureStorage(context)
        tokenCache = null
        _state.value = SessionState()
        securePrefs = openSecurePrefs(context)
    }

    private fun readString(key: String): String? {
        return try {
            securePrefs.getString(key, null)
        } catch (error: Exception) {
            if (!isSecureStorageError(error)) throw error
            resetSecurePrefs()
            null
        }
    }

    private fun writePrefs(block: SharedPreferences.Editor.() -> Unit) {
        try {
            securePrefs.edit().apply(block).apply()
        } catch (error: Exception) {
            if (!isSecureStorageError(error)) throw error
            resetSecurePrefs()
            securePrefs.edit().apply(block).apply()
        }
    }

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun bearerTokenSnapshot(): String? =
        tokenCache ?: readString("token").also { tokenCache = it }

    fun configure(response: AuthResponse) {
        tokenCache = response.token
        writePrefs {
            putString("token", response.token)
            putString(PREF_ROLE, response.role)
        }

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
        writePrefs {
            remove("token")
            remove(PREF_ROLE)
        }
        _state.value = SessionState()
    }

    fun tryRestoreSession() {
        val token = readString("token") ?: return
        val cachedRole = readString(PREF_ROLE).orEmpty()
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
            writePrefs { putString(PREF_ROLE, r) }
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

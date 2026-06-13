package com.pusula.service.data.repository

import com.pusula.service.core.SessionManager
import com.pusula.service.data.model.AuthRequest
import com.pusula.service.data.model.AuthResponse
import com.pusula.service.data.model.GoogleAuthRequest
import com.pusula.service.data.model.RegisterRequest
import com.pusula.service.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun login(username: String, password: String): AuthResponse {
        val response = apiService.authenticate(
            AuthRequest(username = username.trim(), password = password.trim())
        )
        sessionManager.configure(response)
        return response
    }

    suspend fun loginCorporate(orgCode: String, username: String, password: String): AuthResponse {
        val response = apiService.authenticate(
            AuthRequest(
                username = username.trim(),
                password = password.trim(),
                orgCode = orgCode.trim()
            )
        )
        sessionManager.configure(response)
        return response
    }

    suspend fun registerIndividual(
        email: String,
        username: String,
        password: String,
        fullName: String
    ): AuthResponse {
        val response = apiService.registerIndividual(
            RegisterRequest(
                email = email.trim(),
                username = username.trim(),
                password = password.trim(),
                fullName = fullName.trim()
            )
        )
        sessionManager.configure(response)
        return response
    }

    suspend fun deleteAccount() {
        apiService.deleteAccount()
        sessionManager.logout()
    }

    suspend fun authenticateWithGoogle(idToken: String, preferredUsername: String?): AuthResponse {
        val response = apiService.authenticateWithGoogle(
            GoogleAuthRequest(idToken = idToken, preferredUsername = preferredUsername)
        )
        sessionManager.configure(response)
        return response
    }

    suspend fun refreshFeatureContext() {
        val profile = apiService.authFeatureContext()
        sessionManager.mergeProfile(profile)
        val sub = apiService.subscriptionMyContext()
        sessionManager.mergeSubscription(sub)
    }

    /**
     * After restoring JWT from storage, load role/plan/features from the API.
     * Must not call [SessionManager.configure] with a wrong-shaped response.
     */
    suspend fun syncRestoredSession() {
        val profile = apiService.authFeatureContext()
        sessionManager.mergeProfile(profile)
        val sub = apiService.subscriptionMyContext()
        sessionManager.mergeSubscription(sub)
    }
}

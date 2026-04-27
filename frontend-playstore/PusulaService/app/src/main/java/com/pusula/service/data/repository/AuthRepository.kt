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
        val response = apiService.authenticate(AuthRequest(username = username, password = password))
        sessionManager.configure(response)
        return response
    }

    suspend fun loginCorporate(orgCode: String, username: String, password: String): AuthResponse {
        val response = apiService.authenticate(
            AuthRequest(username = username, password = password, orgCode = orgCode)
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
                email = email,
                username = username,
                password = password,
                fullName = fullName
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

    suspend fun refreshFeatureContext(): AuthResponse {
        val context = apiService.myContext()
        sessionManager.configure(context)
        return context
    }
}

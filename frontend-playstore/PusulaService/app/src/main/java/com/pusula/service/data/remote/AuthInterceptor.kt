package com.pusula.service.data.remote

import com.pusula.service.core.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val skipAuthHeader = path == "/api/auth/authenticate"
            || path == "/api/auth/register-individual"
            || path == "/api/auth/google"
            || path == "/api/auth/register"
        val token = if (skipAuthHeader) null else sessionManager.bearerTokenSnapshot()
        val pinned = original.header("Authorization")
        // A queued photo belongs to the captured account; never substitute a
        // newer login's token while preparing/sending a retained payload.
        if (pinned != null && pinned != token?.let { "Bearer $it" }) {
            throw java.io.IOException("Oturum değişti. Bu yükleme durduruldu.")
        }
        val request = original.newBuilder().apply {
            if (pinned == null && !token.isNullOrBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()
        return chain.proceed(request)
    }
}

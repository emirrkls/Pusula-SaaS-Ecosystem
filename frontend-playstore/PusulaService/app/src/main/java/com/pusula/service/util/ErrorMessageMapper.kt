package com.pusula.service.util

import com.google.gson.Gson
import com.pusula.service.data.remote.NetworkError
import java.io.IOException
import retrofit2.HttpException

private val gson = Gson()

private data class ApiErrorBody(
    val message: String? = null,
    val code: String? = null
)

private fun HttpException.apiErrorBody(): ApiErrorBody? {
    val raw = response()?.errorBody()?.string() ?: return null
    return runCatching { gson.fromJson(raw, ApiErrorBody::class.java) }.getOrNull()
}

fun Throwable.toUserMessage(fallback: String): String {
    return when (this) {
        is NetworkError -> message ?: fallback
        is HttpException -> {
            val serverMessage = apiErrorBody()?.message?.takeIf { it.isNotBlank() }
            when (code()) {
                401 -> serverMessage ?: "Oturum süreniz doldu. Lütfen tekrar giriş yapın."
                403 -> serverMessage ?: "Bu işlem için yetkiniz yok."
                500 -> serverMessage ?: "Sunucu hatası. Yöneticiyle iletişime geçin."
                413 -> "Görsel dosyası çok büyük. Daha küçük bir fotoğraf deneyin."
                429 -> serverMessage ?: "İstek limitine ulaştınız. Lütfen biraz sonra tekrar deneyin."
                else -> serverMessage ?: message?.takeIf { it.isNotBlank() } ?: fallback
            }
        }
        is IOException -> "Sunucuya bağlanılamadı. İnternet bağlantınızı kontrol edin."
        else -> message?.takeIf { it.isNotBlank() } ?: fallback
    }
}

/** Giriş / kayıt ekranları — 401 oturum süresi değil, kimlik doğrulama hatasıdır. */
fun Throwable.toAuthUserMessage(fallback: String): String {
    return when (this) {
        is HttpException -> {
            val body = apiErrorBody()
            val serverMessage = body?.message?.takeIf { it.isNotBlank() }
            when (code()) {
                401 -> when (body?.code) {
                    "AUTH_FAILED" -> serverMessage
                        ?: "Giriş başarısız. Kullanıcı adı veya şifre hatalı."
                    else -> serverMessage ?: "Giriş başarısız. Bilgilerinizi kontrol edip tekrar deneyin."
                }
                403 -> serverMessage ?: "Giriş reddedildi. Hesabınızın yetkisi yok."
                429 -> serverMessage
                    ?: "Çok fazla başarısız deneme. Lütfen bir süre sonra tekrar deneyin."
                else -> serverMessage ?: toUserMessage(fallback)
            }
        }
        else -> toUserMessage(fallback)
    }
}

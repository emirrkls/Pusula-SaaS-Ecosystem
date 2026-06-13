package com.pusula.service.util

import com.pusula.service.data.remote.NetworkError
import java.io.IOException
import retrofit2.HttpException

fun Throwable.toUserMessage(fallback: String): String {
    return when (this) {
        is NetworkError -> message ?: fallback
        is HttpException -> when (code()) {
            401 -> "Oturum süreniz doldu. Lütfen tekrar giriş yapın."
            403 -> "Bu işlem için yetkiniz yok."
            429 -> "İstek limitine ulaştınız. Lütfen biraz sonra tekrar deneyin."
            else -> message?.takeIf { it.isNotBlank() } ?: fallback
        }
        is IOException -> "Sunucuya bağlanılamadı. İnternet bağlantınızı kontrol edin."
        else -> message?.takeIf { it.isNotBlank() } ?: fallback
    }
}

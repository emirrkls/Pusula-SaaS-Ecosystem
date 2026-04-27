package com.pusula.service.data.remote

sealed class NetworkError(message: String) : Exception(message) {
    data object Unauthorized : NetworkError("Oturum süresi doldu")
    data object Forbidden : NetworkError("Bu işlem için yetkiniz yok")
    data object QuotaExceeded : NetworkError("Kota limitinize ulaştınız")
    data object InvalidUrl : NetworkError("Geçersiz URL")
    data class Server(val code: Int) : NetworkError("Sunucu hatası ($code)")
}

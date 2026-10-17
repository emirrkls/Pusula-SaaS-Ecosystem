package com.pusula.service.ui.technician

import java.util.UUID

/** Frozen intent per image. Retrying a lost response never creates a new key or payload. */
data class ServicePhotoUploadBatch<S, P>(val type: String, val note: String, val items: List<Item<S, P>>) {
    data class Item<S, P>(val id: String = UUID.randomUUID().toString(), val source: S?,
        val payload: P? = null, val confirmed: Boolean = false, val error: String? = null)
    companion object {
        const val LIMIT = 10
        fun <S, P> create(sources: List<S>, type: String, note: String): ServicePhotoUploadBatch<S, P> =
            ServicePhotoUploadBatch(type, note, sources.take(LIMIT).map { Item<S, P>(source = it) })
    }
    val pendingIds get() = items.filterNot { it.confirmed }.map { it.id }
    val confirmedCount get() = items.count { it.confirmed }
    fun prepare(id: String, payload: P) = copy(items = items.map {
        if (it.id == id && !it.confirmed && it.payload == null) it.copy(source = null, payload = payload) else it
    })
    fun succeed(id: String) = copy(items = items.map {
        if (it.id == id) it.copy(source = null, payload = null, confirmed = true, error = null) else it
    })
    fun fail(id: String, message: String) = copy(items = items.map {
        if (it.id == id && !it.confirmed) it.copy(error = message) else it
    })
}

data class PhotoBatchProgress(val total: Int = 0, val confirmed: Int = 0, val current: Int = 0,
    val failures: List<String> = emptyList()) {
    val pending get() = total - confirmed
}

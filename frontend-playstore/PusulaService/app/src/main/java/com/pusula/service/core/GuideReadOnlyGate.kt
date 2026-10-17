package com.pusula.service.core

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response

/** A scoped lease: stale dismiss callbacks cannot unlock another active tour. */
object GuideReadOnlyGate {
    private val leases = mutableMapOf<UUID,Boolean>()
    private val mutable = MutableStateFlow(false)
    val active = mutable.asStateFlow()
    @Synchronized fun begin(allowReads:Boolean=true): UUID = UUID.randomUUID().also { leases[it]=allowReads; mutable.value = true }
    @Synchronized fun end(id: UUID) { leases.remove(id); mutable.value = leases.isNotEmpty() }
    @Synchronized fun allows(method: String): Boolean = leases.isEmpty() || leases.values.all{it} && method.uppercase() in setOf("GET", "HEAD")
    fun validate(method: String) { if (!allows(method)) throw IOException("Salt okunur turda kayıt değiştirilemez.") }
}
class GuideReadOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        GuideReadOnlyGate.validate(chain.request().method)
        return chain.proceed(chain.request())
    }
}

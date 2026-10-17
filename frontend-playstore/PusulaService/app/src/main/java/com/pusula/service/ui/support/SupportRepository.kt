package com.pusula.service.ui.support

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.*
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface SupportApi {
    @GET("api/public/support-contact") suspend fun contact():SupportContact
    @GET("api/support/unread-count") suspend fun unread(@Header("Authorization") token:String):Map<String,Long>
    @GET("api/support/tickets") suspend fun list(@Header("Authorization") token:String,@Query("page") page:Int):SupportPage
    @GET("api/support/tickets/{id}") suspend fun detail(@Header("Authorization") token:String,@Path("id") id:Long,@Query("before") before:Long?):SupportDetail
    @POST("api/support/tickets") suspend fun create(@Header("Authorization") token:String,@Body body:Map<String,@JvmSuppressWildcards Any>):SupportDetail
    @POST("api/support/tickets/{id}/messages") suspend fun reply(@Header("Authorization") token:String,@Path("id") id:Long,@Body body:Map<String,@JvmSuppressWildcards Any>):SupportDetail
    @PATCH("api/support/tickets/{id}/read") suspend fun read(@Header("Authorization") token:String,@Path("id") id:Long)
    @GET("api/support/attachments/{id}") suspend fun attachment(@Header("Authorization") token:String,@Path("id") id:Long):ResponseBody
}

@Singleton class SupportRepository private constructor(private val context:Context,private val gson:Gson,val api:SupportApi) {
    @Inject constructor(@ApplicationContext context:Context,retrofit:Retrofit,gson:Gson):this(context,gson,isolatedApi(retrofit))
    // The instrumentation suite supplies an in-memory transport; production DI always uses Retrofit.
    internal constructor(context:Context,api:SupportApi):this(context,Gson(),api)
    private companion object {
    // Isolated client: no body logger and no interceptor that can substitute a newer account's token.
        fun isolatedApi(retrofit:Retrofit):SupportApi=retrofit.newBuilder().client(OkHttpClient.Builder()
            .addInterceptor(com.pusula.service.core.GuideReadOnlyInterceptor())
            .addNetworkInterceptor(com.pusula.service.core.GuideReadOnlyInterceptor())
            .connectTimeout(10,java.util.concurrent.TimeUnit.SECONDS).readTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build()).build().create(SupportApi::class.java)
    }
    private val mutex=Mutex()
    private val preferences by lazy { EncryptedSharedPreferences.create(context,"pusula_support_drafts",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM) }
    fun accountKey(token:String,company:Int?):String {
        // Local storage partition only; all access authorization remains on the backend.
        val subject=runCatching { val body=token.split('.')[1]; val json=String(android.util.Base64.decode(body,android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING));gson.fromJson(json,Map::class.java)["sub"].toString() }.getOrElse { token }
        return MessageDigest.getInstance("SHA-256").digest("$company:$subject".toByteArray()).joinToString(""){"%02x".format(it)}
    }
    suspend fun load(key:String):SupportDraft?=withContext(Dispatchers.IO){mutex.withLock { runCatching { preferences.getString(key,null)?.takeIf{it.length<=9_000_000}?.let{gson.fromJson(it,SupportDraft::class.java)} }.getOrNull() }}
    suspend fun save(key:String,draft:SupportDraft)=withContext(Dispatchers.IO){mutex.withLock { ensureActive();val data=gson.toJson(draft);require(data.length<=9_000_000);check(preferences.edit().putString(key,data).commit()){ "Taslak cihazda saklanamadı." } }}
    suspend fun clear(key:String)=withContext(Dispatchers.IO){mutex.withLock {preferences.edit().remove(key).commit();Unit}}
    suspend fun send(token:String,draft:SupportDraft):SupportDetail {
        val body=mutableMapOf<String,Any>("clientKey" to draft.clientKey,"body" to draft.body,"attachments" to draft.attachments)
        return if(draft.ticketId!=null) {body["internalNote"]=false;api.reply("Bearer $token",draft.ticketId,body)}
        else {body["subject"]=draft.subject;body["category"]=draft.category;body["platform"]=draft.platform;body["appVersion"]=draft.appVersion;api.create("Bearer $token",body)}
    }
}

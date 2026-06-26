# R8 strips Gson / Retrofit reflection targets without these rules — release login / API parse crashes.

-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes RuntimeVisibleAnnotations

# Gson (Retrofit converter-gson): keep model types and Kotlin metadata used by Gson
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Backend JSON DTOs — must not rename fields/classes used by Gson
-keep class com.pusula.service.data.model.** { <fields>; <init>(...); }

-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-dontwarn okhttp3.internal.platform.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Navigation Compose typed routes (@Serializable kotlinx.serialization)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pusula.service.**$$serializer { *; }
-keepclassmembers class com.pusula.service.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Credential Manager / Google Sign-In
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.api.** { *; }

# Play Billing / Play Services stubs
-keep class com.android.billingclient.** { *; }

# Maps & ML Kit (runtime reflection Class.forName paths)
-dontwarn com.google.android.gms.**
-keep class com.google.mlkit.** { *; }

# Hilt
-dontwarn com.google.android.apps.common.testing.accessibility.framework.**

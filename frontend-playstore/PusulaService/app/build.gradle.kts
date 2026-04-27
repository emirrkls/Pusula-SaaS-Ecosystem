import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    kotlin("kapt")
}

fun loadLocalProperties(): Properties {
    val props = Properties()
    val candidates = listOf(
        rootProject.file("local.properties"),
        project.file("local.properties")
    ).distinct()
    candidates.forEach { f ->
        if (f.exists()) {
            f.inputStream().use { props.load(it) }
        }
    }
    return props
}

val localProperties = loadLocalProperties()

fun apiBaseUrl(raw: String?, fallback: String): String {
    val trimmed = (raw ?: fallback).trim().trimEnd('/')
    return "$trimmed/"
}

val defaultVpsApiHost = "http://168.231.104.133:8080"
val debugApiBaseUrl = apiBaseUrl(
    localProperties.getProperty("debug.api.base.url"),
    defaultVpsApiHost
)
val releaseApiBaseUrl = apiBaseUrl(
    localProperties.getProperty("release.api.base.url"),
    defaultVpsApiHost
)
val googleWebClientId = (
    localProperties.getProperty("google.web.client.id")
        ?: localProperties.getProperty("GOOGLE_WEB_CLIENT_ID")
        ?: providers.environmentVariable("GOOGLE_WEB_CLIENT_ID").orNull
        ?: providers.gradleProperty("google.web.client.id").orNull
        ?: providers.gradleProperty("GOOGLE_WEB_CLIENT_ID").orNull
        ?: ""
).trim()

android {
    namespace = "com.pusula.service"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pusula.service"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            buildConfigField(
                "String",
                "TICKET_DEEP_LINK_BASE",
                "\"${releaseApiBaseUrl.removeSuffix("/") + "/tickets"}\""
            )
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            buildConfigField(
                "String",
                "TICKET_DEEP_LINK_BASE",
                "\"${debugApiBaseUrl.removeSuffix("/") + "/tickets"}\""
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.8")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.55")
    kapt("com.google.dagger:hilt-compiler:2.55")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.accompanist:accompanist-permissions:0.37.2")
    implementation("com.google.android.gms:play-services-maps:19.1.0")
    implementation("com.google.maps.android:maps-compose:6.4.1")
    implementation("com.android.billingclient:billing-ktx:6.2.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

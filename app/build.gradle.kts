import java.net.URI
import java.net.URISyntaxException
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun externalProperty(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name)

fun String?.trimmedOrEmpty(): String = this?.trim().orEmpty()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun validatedBaseUrl(
    propertyName: String,
    value: String,
    release: Boolean,
): String {
    val uri = try {
        URI(value)
    } catch (_: URISyntaxException) {
        throw GradleException("$propertyName must be a valid absolute HTTP(S) URL.")
    }
    val scheme = uri.scheme?.lowercase()
    val host = uri.host

    if (scheme !in setOf("http", "https") || host.isNullOrBlank()) {
        throw GradleException("$propertyName must be a valid absolute HTTP(S) URL.")
    }
    if (release && scheme != "https") {
        throw GradleException("$propertyName must use HTTPS for release builds.")
    }
    if (release && host == "10.0.2.2") {
        throw GradleException("$propertyName must not use the Android emulator host in release builds.")
    }
    if (!release && scheme == "http" && host != "10.0.2.2") {
        throw GradleException("$propertyName may use HTTP only with the Android emulator host 10.0.2.2.")
    }

    return if (value.endsWith('/')) value else "$value/"
}

val googleServerClientId = externalProperty("GOOGLE_SERVER_CLIENT_ID").trimmedOrEmpty()
val debugApiBaseUrl = validatedBaseUrl(
    propertyName = "DEBUG_API_BASE_URL",
    value = externalProperty("DEBUG_API_BASE_URL").trimmedOrEmpty()
        .ifEmpty { "http://10.0.2.2:8080/" },
    release = false,
)
val releaseApiBaseUrl = externalProperty("RELEASE_API_BASE_URL").trimmedOrEmpty().let { value ->
    if (value.isEmpty()) value else validatedBaseUrl("RELEASE_API_BASE_URL", value, release = true)
}

val validateDebugConfiguration by tasks.registering {
    group = "verification"
    description = "Validates external configuration required by the debug variant."
    doLast {
        if (googleServerClientId.isEmpty()) {
            throw GradleException(
                "Missing GOOGLE_SERVER_CLIENT_ID. Define the Web/server OAuth client ID " +
                    "in local.properties, as a Gradle project property, or as an environment variable."
            )
        }
    }
}

val validateReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Validates external configuration required by the release variant."
    doLast {
        if (googleServerClientId.isEmpty()) {
            throw GradleException(
                "Missing GOOGLE_SERVER_CLIENT_ID. Define the Web/server OAuth client ID " +
                    "in local.properties, as a Gradle project property, or as an environment variable."
            )
        }
        if (releaseApiBaseUrl.isEmpty()) {
            throw GradleException(
                "Missing RELEASE_API_BASE_URL. Define the HTTPS production API URL " +
                    "in local.properties, as a Gradle project property, or as an environment variable."
            )
        }
    }
}

android {
    namespace = "com.mar.gym"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.mar.gym"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", debugApiBaseUrl.asBuildConfigString())
            buildConfigField(
                "String",
                "GOOGLE_SERVER_CLIENT_ID",
                googleServerClientId.asBuildConfigString(),
            )
        }
        release {
            buildConfigField("String", "API_BASE_URL", releaseApiBaseUrl.asBuildConfigString())
            buildConfigField(
                "String",
                "GOOGLE_SERVER_CLIENT_ID",
                googleServerClientId.asBuildConfigString(),
            )
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

tasks.configureEach {
    when (name) {
        "generateDebugBuildConfig" -> dependsOn(validateDebugConfiguration)
        "generateReleaseBuildConfig" -> dependsOn(validateReleaseConfiguration)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

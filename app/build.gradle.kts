import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningProperty(name: String): String? =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseSigningStoreFile = releaseSigningProperty("CAMPZONE_RELEASE_STORE_FILE")
val releaseSigningStorePassword = releaseSigningProperty("CAMPZONE_RELEASE_STORE_PASSWORD")
val releaseSigningKeyAlias = releaseSigningProperty("CAMPZONE_RELEASE_KEY_ALIAS")
val releaseSigningKeyPassword = releaseSigningProperty("CAMPZONE_RELEASE_KEY_PASSWORD")
val releaseSigningValues = mapOf(
    "CAMPZONE_RELEASE_STORE_FILE" to releaseSigningStoreFile,
    "CAMPZONE_RELEASE_STORE_PASSWORD" to releaseSigningStorePassword,
    "CAMPZONE_RELEASE_KEY_ALIAS" to releaseSigningKeyAlias,
    "CAMPZONE_RELEASE_KEY_PASSWORD" to releaseSigningKeyPassword,
)
val hasReleaseSigningConfig = releaseSigningValues.values.all { it != null }
check(releaseSigningValues.values.none { it != null } || hasReleaseSigningConfig) {
    "Release signing is partially configured. Provide all of: ${releaseSigningValues.keys.joinToString()}."
}

android {
    namespace = "fr.ziyon.campzone"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "fr.ziyon.campzone"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseSigningStoreFile!!)
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"https://notification-backend-chi.vercel.app\""
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"https://notification-backend-chi.vercel.app\""
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildToolsVersion = "37.0.0"
}

dependencies {
    // Compose BOM + core UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Firebase (versions pinned by the BoM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.ui)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Coil (image loading from Cloudinary URLs)
    implementation(libs.coil.compose)

    // osmdroid (OpenStreetMap — key-free in-app venue map preview)
    implementation(libs.osmdroid.android)

    // Markdown (Markdown → Spanned for announcement/camping body, including tables)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.tables)

    // Stripe PaymentSheet
    implementation(libs.stripe.android)

    // Credential Manager + Google ID (consumed by the auth screen in A6)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.location)

    // CameraX + ML Kit (QR check-in scanner)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

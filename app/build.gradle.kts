/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder                                           ║
 * ║  Developer : MNM YOUNUS                                                  ║
 * ║  File      : app/build.gradle.kts                                        ║
 * ║                                                                          ║
 * ║  FIX APPLIED: compileSdk/targetSdk reverted 35 → 34                      ║
 * ║  Reason: AGP 8.3.2 is only verified up to compileSdk 34. Using 35        ║
 * ║  caused AAPT2 to fail resolving framework resources                      ║
 * ║  (android:style/Theme.Material.NoTitleBar not found) because the        ║
 * ║  API 35 platform jar isn't fully supported by this AGP version.         ║
 * ║  API 34 also matches the originally specified target range              ║
 * ║  (Android 10 / API 29 → Android 14+ / API 34+).                         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.parcelize)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

android {
    namespace   = "com.mnmyounus.yacr"
    compileSdk  = 34          // FIXED — was 35

    defaultConfig {
        applicationId        = "com.mnmyounus.yacr"
        minSdk               = 29          // Android 10 (Q)
        targetSdk             = 34          // FIXED — was 35, Android 14
        versionCode          = 1
        versionName          = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables { useSupportLibrary = true }

        ksp {
            arg("room.schemaLocation",   "$projectDir/schemas")
            arg("room.incremental",      "true")
            arg("room.expandProjection", "true")
        }

        buildConfigField("String", "APP_VERSION",    "\"${versionName}\"")
        buildConfigField("String", "DEVELOPER",      "\"MNM YOUNUS\"")
        buildConfigField("String", "KEYSTORE_ALIAS", "\"yacr_master_key\"")
    }

    signingConfigs {
        create("release") {
            storeFile = (System.getenv("SIGNING_KEYSTORE_PATH")
                ?: keystoreProperties["storeFile"] as? String)
                ?.let { file(it) }
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                ?: keystoreProperties["storePassword"] as? String
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                ?: keystoreProperties["keyAlias"] as? String
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                ?: keystoreProperties["keyPassword"] as? String
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-DEBUG"
            isDebuggable        = true
            isMinifyEnabled     = false
            buildConfigField("Boolean", "ENABLE_VERBOSE_LOGGING", "true")
        }
        release {
            isMinifyEnabled     = true
            isShrinkResources   = true
            isDebuggable        = false
            signingConfig       = signingConfigs.getByName("release")
            buildConfigField("Boolean", "ENABLE_VERBOSE_LOGGING", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable             = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk       = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    implementation(libs.security.crypto)
    implementation(libs.biometric)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.accompanist.permissions)

    implementation(libs.work.runtime.ktx)

    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

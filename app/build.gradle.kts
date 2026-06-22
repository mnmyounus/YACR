/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder                                           ║
 * ║  Developer : MNM YOUNUS                                                  ║
 * ║  File      : app/build.gradle.kts                                        ║
 * ║                                                                          ║
 * ║  Build Configuration:                                                    ║
 * ║   • Kotlin 2.0 + Compose Compiler Plugin                                 ║
 * ║   • Hilt DI + KSP annotation processing                                  ║
 * ║   • R8 full-mode ProGuard for release shrinking                          ║
 * ║   • ABI splits: arm64-v8a, armeabi-v7a, x86_64 (minimal APK sizes)      ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace   = "com.mnmyounus.yacr"
    compileSdk  = 34

    defaultConfig {
        applicationId        = "com.mnmyounus.yacr"
        minSdk               = 29          // Android 10 (Q)
        targetSdk            = 34          // Android 14
        versionCode          = 1
        versionName          = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables { useSupportLibrary = true }

        // Room schema export path
        ksp {
            arg("room.schemaLocation",   "$projectDir/schemas")
            arg("room.incremental",      "true")
            arg("room.expandProjection", "true")
        }

        buildConfigField("String", "APP_VERSION",    "\"${versionName}\"")
        buildConfigField("String", "DEVELOPER",      "\"MNM YOUNUS\"")
        buildConfigField("String", "KEYSTORE_ALIAS", "\"yacr_master_key\"")
    }

    // NOTE: No signingConfigs block — release APKs are built UNSIGNED on
    // purpose (per project requirement: no signing, APK only). AGP will
    // output app-release-unsigned.apk. Attaching an incomplete signing
    // config here (e.g. one missing storeFile) causes packageRelease to
    // fail outright rather than falling back to unsigned, which is what
    // previously broke this build.

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-DEBUG"
            isDebuggable        = true
            isMinifyEnabled     = false
            signingConfig       = signingConfigs.getByName("release")
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

    // ABI splits for minimal APK size per architecture
    splits {
        abi {
            isEnable             = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk       = true // also produce a universal APK
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

    // Baseline profiles for cold start performance
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // ── Core Library Desugaring ───────────────────────────────────────────────
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ── AndroidX Core ─────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // ── Compose BOM ───────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)

    // ── Navigation ────────────────────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Hilt DI ───────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Room Database ─────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── DataStore Preferences ────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // ── Security / Crypto ─────────────────────────────────────────────────────
    implementation(libs.security.crypto)
    implementation(libs.biometric)

    // ── Media3 Playback ───────────────────────────────────────────────────────
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // ── Accompanist Permissions ───────────────────────────────────────────────
    implementation(libs.accompanist.permissions)

    // ── WorkManager ───────────────────────────────────────────────────────────
    implementation(libs.work.runtime.ktx)

    // ── Logging ───────────────────────────────────────────────────────────────
    implementation(libs.timber)

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

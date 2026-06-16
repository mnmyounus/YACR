# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  YACR – Your All Call Recorder  |  proguard-rules.pro                      ║
# ║  Developer : MNM YOUNUS                                                      ║
# ║                                                                              ║
# ║  R8 full-mode ProGuard configuration.                                        ║
# ║  Principle: shrink everything — keep only what is explicitly needed.        ║
# ╚══════════════════════════════════════════════════════════════════════════════╝

# ── General Android ───────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ── Application Entry Points ──────────────────────────────────────────────────
-keep class com.mnmyounus.yacr.YACRApplication { *; }
-keep class com.mnmyounus.yacr.presentation.MainActivity { *; }

# ── Hilt / Dagger DI ─────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ── Room Database ─────────────────────────────────────────────────────────────
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.Metadata { *; }

# ── Kotlin Serialization (if used) ───────────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class kotlinx.serialization.** { *; }

# ── Parcelize ─────────────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ── Android Services and Receivers ───────────────────────────────────────────
-keep class com.mnmyounus.yacr.service.** { *; }
-keep class com.mnmyounus.yacr.data.crypto.** { *; }

# ── Accessibility Service ─────────────────────────────────────────────────────
-keep class com.mnmyounus.yacr.service.YACRAccessibilityService { *; }

# ── Domain Models (passed via Intent extras / Parcelable) ─────────────────────
-keep class com.mnmyounus.yacr.domain.model.** { *; }

# ── DataStore ────────────────────────────────────────────────────────────────
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ── ExoPlayer / Media3 ────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Biometric ────────────────────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ── Compose (R8 handles most, but keep these) ─────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Timber (logging) ─────────────────────────────────────────────────────────
-dontwarn com.jakewharton.timber.**

# ── Remove all logging in release ────────────────────────────────────────────
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# ── Remove debug stack traces in release ─────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# ── Kotlin Reflect ────────────────────────────────────────────────────────────
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ── Suppress warnings for unused library classes ─────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

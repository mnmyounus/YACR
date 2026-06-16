/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  presentation/MainActivity.kt            ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ║                                                                              ║
 * ║  Single-Activity architecture. Hosts the Compose NavHost.                   ║
 * ║  Handles runtime permission requests and biometric authentication gate.      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.mnmyounus.yacr.data.local.datastore.YACRPreferences
import com.mnmyounus.yacr.presentation.navigation.YACRNavHost
import com.mnmyounus.yacr.presentation.theme.YACRTheme
import com.mnmyounus.yacr.presentation.theme.YacrBackground
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var preferences: YACRPreferences

    // ── Runtime permission launcher ──────────────────────────────────────────
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        Timber.d("MainActivity: Permission results — $results | allGranted=$allGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRequiredPermissions()

        setContent {
            YACRTheme {
                val navController = rememberNavController()
                var biometricPassed by remember { mutableStateOf(false) }
                var biometricRequired by remember { mutableStateOf(false) }

                // Check if biometric lock is enabled
                LaunchedEffect(Unit) {
                    val bioEnabled = preferences.biometricLockEnabled.first()
                    biometricRequired = bioEnabled
                    if (bioEnabled) {
                        showBiometricPrompt(
                            onSuccess = { biometricPassed = true },
                            onFailed  = { biometricPassed = false }
                        )
                    } else {
                        biometricPassed = true
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(YacrBackground)
                ) {
                    if (!biometricRequired || biometricPassed) {
                        YACRNavHost(navController = navController)
                    }
                    // else: blank screen shown while biometric prompt is open
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Runtime Permissions
    // ─────────────────────────────────────────────────────────────────────────

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.PROCESS_OUTGOING_CALLS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
            permissions += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val notGranted = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            Timber.d("MainActivity: Requesting ${notGranted.size} permissions")
            permissionsLauncher.launch(notGranted.toTypedArray())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Biometric Authentication
    // ─────────────────────────────────────────────────────────────────────────

    private fun showBiometricPrompt(onSuccess: () -> Unit, onFailed: () -> Unit) {
        val biometricManager = BiometricManager.from(this)

        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Timber.w("MainActivity: Biometric not available (code=$canAuthenticate) — bypassing lock")
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Timber.d("MainActivity: Biometric authentication succeeded")
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Timber.w("MainActivity: Biometric error $errorCode: $errString")
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    finish() // User cancelled — close the app
                } else {
                    onFailed()
                }
            }
            override fun onAuthenticationFailed() {
                Timber.w("MainActivity: Biometric authentication failed")
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("YACR")
            .setSubtitle("Authenticate to access your recordings")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        BiometricPrompt(this, executor, callback).authenticate(promptInfo)
    }
}

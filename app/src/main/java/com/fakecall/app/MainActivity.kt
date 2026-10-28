package com.fakecall.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

data class PermissionStatus(
    val callLog: Boolean = true,
    val notifications: Boolean = true,
    val fullScreen: Boolean = true,
) {
    val allGranted: Boolean get() = callLog && notifications && fullScreen
}

class MainActivity : ComponentActivity() {

    private var permissions by mutableStateOf(PermissionStatus())

    /** True while this app is still the default SMS app after adding a simulated text. */
    private var isDefaultSmsApp by mutableStateOf(false)

    /** Set once Android stops showing the permission dialog, so we open Settings instead. */
    private var sendToSettings = false

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            refreshPermissions()
            sendToSettings = result.filterValues { granted -> !granted }.keys
                .any { !shouldShowRequestPermissionRationale(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissions()
        if (savedInstanceState == null && missingRuntimePermissions().isNotEmpty()) {
            permissionRequest.launch(missingRuntimePermissions())
        }

        closeWhenCallEnds()

        setContent {
            AppTheme {
                SetupScreen(
                    permissions = permissions,
                    isDefaultSmsApp = isDefaultSmsApp,
                    onRequestPermissions = ::requestPermissions,
                    onAllowFullScreen = ::openFullScreenSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    /**
     * Closes the app once a call that rang is over (answered, declined or missed), so
     * hanging up returns to whatever was on screen before instead of to this setup screen.
     * Cancelling a call that hasn't rung yet keeps the app open.
     */
    private fun closeWhenCallEnds() {
        lifecycleScope.launch {
            var rang = false
            CallState.session.collect { session ->
                when {
                    session != null -> rang = session.phase != Phase.WAITING
                    rang -> finishAndRemoveTask()
                }
            }
        }
    }

    private fun missingRuntimePermissions(): Array<String> =
        buildList {
            add(Manifest.permission.WRITE_CALL_LOG)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterNot(::isGranted).toTypedArray()

    private fun isGranted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun refreshPermissions() {
        permissions = PermissionStatus(
            callLog = isGranted(Manifest.permission.WRITE_CALL_LOG),
            notifications = Build.VERSION.SDK_INT < 33 || isGranted(Manifest.permission.POST_NOTIFICATIONS),
            fullScreen = Build.VERSION.SDK_INT < 34 ||
                getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
        )
        isDefaultSmsApp = SmsWriter.isDefaultSmsApp(this)
    }

    private fun requestPermissions() {
        if (sendToSettings) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        } else {
            permissionRequest.launch(missingRuntimePermissions())
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", packageName, null)),
            )
        }
    }
}

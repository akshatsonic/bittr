package com.bitter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitter.ble.BleMeshService
import com.bitter.ui.BitterTheme
import com.bitter.ui.SplashScreen
import com.bitter.ui.TimelineScreen
import com.bitter.ui.TimelineViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val focusEventId = MutableStateFlow<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        startMeshService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as BitterApplication).graph

        focusEventId.value = intent.getStringExtra(EXTRA_FOCUS_EVENT_ID)

        setContent {
            val darkTheme by graph.darkTheme.collectAsState()
            val focusId by focusEventId.collectAsState()
            BitterTheme(darkTheme = darkTheme) {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    TimelineScreen(
                        viewModel = viewModel(
                            factory = TimelineViewModelFactory(
                                repository = graph.repository,
                                username = graph.username,
                                deviceId = graph.deviceId,
                                nicknames = graph.nicknames,
                                meshStatus = graph.meshStatus,
                                ownNickname = graph.ownNickname,
                                setOwnNickname = graph::setOwnNickname,
                                logStore = graph.logStore,
                            ),
                        ),
                        isDarkTheme = darkTheme,
                        onToggleTheme = { graph.setDarkTheme(!darkTheme) },
                        focusEventId = focusId,
                        onFocusConsumed = { focusEventId.value = null },
                    )
                }
            }
        }

        if (allPermissionsGranted()) {
            startMeshService()
        } else {
            permissionLauncher.launch(requiredPermissions().toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        focusEventId.value = intent.getStringExtra(EXTRA_FOCUS_EVENT_ID)
    }

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun allPermissionsGranted(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun startMeshService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, BleMeshService::class.java),
        )
    }

    companion object {
        const val EXTRA_FOCUS_EVENT_ID = "com.bitter.focus_event_id"
    }
}

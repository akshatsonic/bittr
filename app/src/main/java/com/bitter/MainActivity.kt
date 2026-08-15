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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitter.ble.BleMeshService
import com.bitter.ui.BitterTheme
import com.bitter.ui.TimelineScreen
import com.bitter.ui.TimelineViewModelFactory

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        startMeshService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as BitterApplication).graph

        setContent {
            BitterTheme {
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
                        ),
                    ),
                )
            }
        }

        if (allPermissionsGranted()) {
            startMeshService()
        } else {
            permissionLauncher.launch(requiredPermissions().toTypedArray())
        }
    }

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
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
}

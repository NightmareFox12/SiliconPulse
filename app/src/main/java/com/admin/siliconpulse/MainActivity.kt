package com.admin.siliconpulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.admin.siliconpulse.ui.MainScreen
import com.admin.siliconpulse.ui.PermissionsBus
import com.admin.siliconpulse.ui.theme.SiliconPulseTheme

class MainActivity : ComponentActivity() {

    private val runtimePermissions: List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun missing(): List<String> =
        runtimePermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var missingPermissions by remember { mutableStateOf(missing()) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                missingPermissions = missing()
                PermissionsBus.notifyChanged()
            }
            LaunchedEffect(Unit) {
                if (missingPermissions.isNotEmpty()) launcher.launch(missingPermissions.toTypedArray())
            }
            SiliconPulseTheme {
                MainScreen(
                    container = appContainer,
                    missingPermissions = missingPermissions,
                    onRequestPermissions = { launcher.launch(missingPermissions.toTypedArray()) },
                )
            }
        }
    }
}

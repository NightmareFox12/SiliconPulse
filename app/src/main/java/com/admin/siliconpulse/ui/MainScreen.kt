package com.admin.siliconpulse.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.admin.siliconpulse.AppContainer
import com.admin.siliconpulse.ui.export.ExportScreen
import com.admin.siliconpulse.ui.export.ExportViewModel
import com.admin.siliconpulse.ui.oscilloscope.OscilloscopeScreen
import com.admin.siliconpulse.ui.oscilloscope.OscilloscopeViewModel
import com.admin.siliconpulse.ui.power.PowerScreen
import com.admin.siliconpulse.ui.power.PowerViewModel
import com.admin.siliconpulse.ui.privacy.PrivacyScreen
import com.admin.siliconpulse.ui.privacy.PrivacyViewModel
import com.admin.siliconpulse.ui.radios.RadiosScreen
import com.admin.siliconpulse.ui.radios.RadiosViewModel
import com.admin.siliconpulse.ui.theme.Amber

enum class Tab(val label: String, val icon: ImageVector) {
    SENSORS("Sensores", Icons.Filled.Build),
    PRIVACY("Privacidad", Icons.Filled.Lock),
    RADIOS("Radios", Icons.Filled.Phone),
    POWER("Energia", Icons.Filled.Settings),
    EXPORT("Exportar", Icons.Filled.Share),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(container: AppContainer, missingPermissions: List<String>, onRequestPermissions: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.SENSORS) }

    val factory = remember(container) {
        viewModelFactory {
            initializer { OscilloscopeViewModel(container.sensors) }
            initializer { PrivacyViewModel(container.privacy) }
            initializer { RadiosViewModel(container.radios) }
            initializer { PowerViewModel(container.power) }
            initializer { ExportViewModel(container.sensors, container.privacy, container.radios, container.power, container.exporter) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SiliconPulse", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (missingPermissions.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Faltan ${missingPermissions.size} permisos: parte de la telemetria esta limitada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRequestPermissions) { Text("Conceder") }
                }
            }
            when (tab) {
                Tab.SENSORS -> OscilloscopeScreen(viewModel(factory = factory))
                Tab.PRIVACY -> PrivacyScreen(viewModel(factory = factory))
                Tab.RADIOS -> RadiosScreen(viewModel(factory = factory))
                Tab.POWER -> PowerScreen(viewModel(factory = factory))
                Tab.EXPORT -> ExportScreen(viewModel(factory = factory))
            }
        }
    }
}

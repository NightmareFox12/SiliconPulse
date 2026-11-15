package com.admin.siliconpulse.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.admin.siliconpulse.ui.simple.SimpleScreen
import com.admin.siliconpulse.ui.simple.SimpleViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admin.siliconpulse.ui.theme.AccentBrush
import com.admin.siliconpulse.ui.theme.Amber
import com.admin.siliconpulse.ui.theme.BackgroundBrush
import com.admin.siliconpulse.ui.theme.Cyan
import com.admin.siliconpulse.ui.theme.Ink
import com.admin.siliconpulse.ui.theme.Mint
import com.admin.siliconpulse.ui.theme.Outline
import com.admin.siliconpulse.ui.theme.Panel
import com.admin.siliconpulse.ui.theme.TextMuted

enum class Tab(val label: String, val title: String, val caption: String, val icon: ImageVector) {
    SENSORS("Sensores", "Osciloscopio", "SensorManager en crudo", Icons.Filled.Build),
    PRIVACY("Privacidad", "Auditoria", "Mic · Camara · Ubicacion", Icons.Filled.Lock),
    RADIOS("Radios", "Radios", "Celular · Wi-Fi · Bluetooth", Icons.Filled.Phone),
    POWER("Energia", "Energia", "Bateria · Termico · Deep sleep", Icons.Filled.Settings),
    EXPORT("Exportar", "Diagnostico", "Snapshot JSON del sistema", Icons.Filled.Share),
}

@Composable
fun MainScreen(container: AppContainer, missingPermissions: List<String>, onRequestPermissions: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.SENSORS) }
    val mode by container.viewMode.mode.collectAsStateWithLifecycle()

    val factory = remember(container) {
        viewModelFactory {
            initializer { OscilloscopeViewModel(container.sensors) }
            initializer { PrivacyViewModel(container.privacy) }
            initializer { RadiosViewModel(container.radios) }
            initializer { PowerViewModel(container.power) }
            initializer { ExportViewModel(container.sensors, container.privacy, container.radios, container.power, container.exporter) }
            initializer { SimpleViewModel(container.privacy, container.power, container.audit) }
        }
    }

    Box(Modifier.fillMaxSize().background(BackgroundBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            // Con fondo transparente Compose asumiria negro como color de contenido: lo fijamos.
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { Header(tab, mode) { container.viewMode.set(it) } },
            bottomBar = { if (mode == ViewMode.PRO) BottomNav(tab) { tab = it } },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (missingPermissions.isNotEmpty()) PermissionBanner(missingPermissions.size, onRequestPermissions)
                if (mode == ViewMode.SIMPLE) {
                    Box(Modifier.navigationBarsPadding()) { SimpleScreen(viewModel(factory = factory)) }
                } else AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 30 })
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "tab",
                ) { t ->
                    when (t) {
                        Tab.SENSORS -> OscilloscopeScreen(viewModel(factory = factory))
                        Tab.PRIVACY -> PrivacyScreen(viewModel(factory = factory))
                        Tab.RADIOS -> RadiosScreen(viewModel(factory = factory))
                        Tab.POWER -> PowerScreen(viewModel(factory = factory))
                        Tab.EXPORT -> ExportScreen(viewModel(factory = factory))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(tab: Tab, mode: ViewMode, onMode: (ViewMode) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(AccentBrush))
            Spacer(Modifier.width(8.dp))
            Text("SiliconPulse", style = MaterialTheme.typography.titleSmall, color = Cyan)
            Spacer(Modifier.weight(1f))
            DeviceChip()
        }
        Spacer(Modifier.height(10.dp))
        Text(if (mode == ViewMode.SIMPLE) "Tu telefono" else tab.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(if (mode == ViewMode.SIMPLE) "Resumen facil de entender" else tab.caption, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.height(12.dp))
        ModeSwitch(mode, onMode)
    }
}

/** Conmutador Sencilla / Pro: control segmentado a todo el ancho, imposible de pasar por alto. */
@Composable
private fun ModeSwitch(mode: ViewMode, onMode: (ViewMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .padding(4.dp),
    ) {
        ViewMode.entries.forEach { m ->
            val selected = m == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Cyan.copy(alpha = 0.16f) else Color.Transparent)
                    .border(if (selected) 1.5.dp else 0.dp, if (selected) Cyan else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onMode(m) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (m == ViewMode.SIMPLE) "Vista sencilla" else "Vista Pro",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) Cyan else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (m == ViewMode.SIMPLE) "Para cualquiera" else "Para tecnicos",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) Cyan.copy(alpha = 0.85f) else TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceChip() {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Panel)
            .border(1.dp, Outline, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Mint))
        Spacer(Modifier.width(6.dp))
        Text(
            "${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun PermissionBanner(count: Int, onRequest: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Amber.copy(alpha = 0.10f))
            .border(1.dp, Amber.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Faltan $count permisos: parte de la telemetria esta limitada.",
            style = MaterialTheme.typography.bodySmall,
            color = Amber,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRequest) { Text("Conceder", color = Amber) }
    }
}

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.92f), Ink))),
    ) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
            Tab.entries.forEach { t ->
                NavigationBarItem(
                    selected = current == t,
                    onClick = { onSelect(t) },
                    icon = { Icon(t.icon, contentDescription = t.label) },
                    label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Cyan,
                        selectedTextColor = Cyan,
                        indicatorColor = Cyan.copy(alpha = 0.18f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                    ),
                )
            }
        }
    }
}

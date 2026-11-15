package com.admin.siliconpulse.ui.simple

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admin.siliconpulse.domain.model.AuditedApp
import com.admin.siliconpulse.domain.model.SimpleFinding
import com.admin.siliconpulse.domain.model.Verdict
import com.admin.siliconpulse.ui.components.Hairline
import com.admin.siliconpulse.ui.components.Note
import com.admin.siliconpulse.ui.components.Panel
import com.admin.siliconpulse.ui.components.StatusPill
import com.admin.siliconpulse.ui.theme.Amber
import com.admin.siliconpulse.ui.theme.Cyan
import com.admin.siliconpulse.ui.theme.Ink
import com.admin.siliconpulse.ui.theme.Mint
import com.admin.siliconpulse.ui.theme.Rose
import com.admin.siliconpulse.ui.theme.TextFaint
import com.admin.siliconpulse.ui.theme.TextMuted
import com.admin.siliconpulse.ui.theme.Violet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SimpleScreen(vm: SimpleViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // Semaforo en vivo: lo primero que se ve, sin pulsar nada.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveTile("Camara", if (live.camera.inUse) "ENCENDIDA" else "apagada", live.camera.inUse, Modifier.weight(1f))
                LiveTile("Microfono", if (live.microphone.inUse) "ESCUCHANDO" else "en silencio", live.microphone.inUse, Modifier.weight(1f))
                LiveTile("Ubicacion", if (live.location.locationEnabled) "encendida" else "apagada", false, Modifier.weight(1f), neutral = !live.location.locationEnabled)
            }
        }
        item { AnalyzeButton(analyzing = ui.analyzing, progress = ui.progress, onClick = vm::analyze) }

        ui.error?.let { e -> item { Panel("No pude completar el analisis", accent = Rose) { Text(e, color = Rose) } } }

        ui.report?.let { r ->
            item {
                val c = verdictColor(r.overall)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.22f), c.copy(alpha = 0.06f))))
                        .border(1.dp, c.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VerdictDot(r.overall, 14.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(r.headline, style = MaterialTheme.typography.titleLarge, color = c)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Analizado a las ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(r.generatedAtEpochMs))} · ${r.totalApps} apps revisadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
            items(r.findings) { f -> FindingCard(f, vm) }

            if (!r.usageAccessGranted) {
                item {
                    Panel("Quieres saber QUE app usa la camara o el micro?", accent = Violet) {
                        Text(
                            "Android no deja preguntarlo directamente, pero si me das 'Acceso a datos de uso' puedo cruzar el momento exacto en que se enciende con la app que estaba activa y decirte cual fue.",
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { ctx.startActivity(vm.usageAccessIntent()) }) { Text("Dar acceso a datos de uso") }
                    }
                }
            }
            item {
                Panel("Historial oficial de Android", subtitle = "quien uso camara, micro y ubicacion en 24 h", accent = Cyan) {
                    Text(
                        "El propio sistema lleva un registro exacto por app. Es la fuente mas fiable para confirmar sospechas.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    val intent = vm.privacyDashboardIntent()
                    OutlinedButton(onClick = { intent?.let(ctx::startActivity) }, enabled = intent != null) { Text("Abrir Panel de privacidad") }
                    if (intent == null) Note("Disponible en Android 12 o superior.")
                }
            }
            if (r.suspiciousApps.isNotEmpty()) {
                item { Text("APPS A REVISAR", style = MaterialTheme.typography.labelSmall, color = TextFaint, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
                items(r.suspiciousApps, key = { it.packageName }) { app -> AppCard(app, vm) }
            }
        }

        if (ui.report == null && !ui.analyzing) {
            item {
                Panel("Que hace el analisis", accent = Violet) {
                    Bullet("Comprueba si la camara o el microfono estan en uso en este instante.")
                    Bullet("Revisa todas las apps instaladas: de donde vienen, que permisos tienen y si se esconden.")
                    Bullet("Te dice en una frase si el telefono descansa bien o algo lo mantiene despierto.")
                    Bullet("Nada sale de tu telefono. No hay internet de por medio.")
                }
            }
        }
    }
}

@Composable
private fun LiveTile(label: String, value: String, alert: Boolean, modifier: Modifier, neutral: Boolean = false) {
    val c = when { neutral -> TextMuted; alert -> Rose; else -> Mint }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.copy(alpha = 0.08f))
            .border(1.dp, c.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(c, alert)
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = c, maxLines = 1)
    }
}

/** Punto que late cuando hay alerta, para que se note aunque no se lea. */
@Composable
private fun PulseDot(color: Color, pulse: Boolean) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Canvas(Modifier.size(10.dp)) {
        if (pulse) drawCircle(color.copy(alpha = a * 0.5f), radius = size.minDimension / 2 * 1.8f)
        drawCircle(color, radius = size.minDimension / 2 * 0.6f)
    }
}

@Composable
private fun AnalyzeButton(analyzing: Boolean, progress: String?, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "sheen")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "s")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Cyan, Violet), start = androidx.compose.ui.geometry.Offset(shift * 600f, 0f), end = androidx.compose.ui.geometry.Offset(shift * 600f + 700f, 300f)))
            .clickable(enabled = !analyzing, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (analyzing) CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.5.dp)
            else Box(Modifier.size(22.dp).clip(CircleShape).background(Ink.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Ink))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (analyzing) "Analizando…" else "Analizar mi telefono", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold)
                Text(progress ?: "Camara, microfono, ubicacion, apps sospechosas y bateria. Tarda unos segundos.", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun FindingCard(f: SimpleFinding, vm: SimpleViewModel) {
    val c = verdictColor(f.verdict)
    Panel(f.title, accent = c, trailing = { StatusPill(verdictLabel(f.verdict), alert = f.verdict == Verdict.ALERT, neutral = f.verdict == Verdict.OK) }) {
        Text(f.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        if (f.apps.isNotEmpty()) {
            Hairline()
            f.apps.forEach { app -> AppRow(app, vm) }
        }
    }
}

@Composable
private fun AppCard(app: AuditedApp, vm: SimpleViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val c = if (app.score >= 5) Rose else Amber
    Panel(app.label, subtitle = app.packageName, accent = c, trailing = { StatusPill("riesgo ${app.score}", alert = app.score >= 5) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (app.camera) Tag("camara", Rose)
            if (app.microphone) Tag("micro", Rose)
            if (app.location) Tag(if (app.backgroundLocation) "ubicacion siempre" else "ubicacion", Amber)
            if (app.readSms) Tag("SMS", Amber)
            if (!app.fromPlayStore) Tag("fuera de Play", Violet)
        }
        Spacer(Modifier.height(8.dp))
        app.reasons.take(if (expanded) 10 else 2).forEach { Bullet(it) }
        if (app.reasons.size > 2) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Menos" else "Ver ${app.reasons.size - 2} motivos mas") }
        Hairline()
        AppActions(app, vm)
    }
}

@Composable
private fun AppRow(app: AuditedApp, vm: SimpleViewModel) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(app.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (app.inForegroundNow) StatusPill("EN PANTALLA", alert = true)
            else app.lastForegroundEpochMs?.let { Text("hace ${(System.currentTimeMillis() - it) / 1000} s", style = MaterialTheme.typography.labelSmall, color = TextMuted) }
        }
        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = TextFaint)
        AppActions(app, vm)
    }
}

@Composable
private fun AppActions(app: AuditedApp, vm: SimpleViewModel) {
    val ctx = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { ctx.startActivity(vm.appInfoIntent(app.packageName)) }) { Text("Ver permisos") }
        if (!app.isSystem) TextButton(onClick = { ctx.startActivity(vm.uninstallIntent(app.packageName)) }) { Text("Desinstalar", color = Rose) }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•", color = TextMuted, modifier = Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
private fun VerdictDot(v: Verdict, size: androidx.compose.ui.unit.Dp) {
    val c = verdictColor(v)
    Canvas(Modifier.size(size)) {
        drawCircle(c.copy(alpha = 0.3f), radius = this.size.minDimension / 2 * 1.5f)
        drawCircle(c)
    }
}

private fun verdictColor(v: Verdict) = when (v) { Verdict.OK -> Mint; Verdict.WARN -> Amber; Verdict.ALERT -> Rose }
private fun verdictLabel(v: Verdict) = when (v) { Verdict.OK -> "OK"; Verdict.WARN -> "REVISAR"; Verdict.ALERT -> "ALERTA" }

@Suppress("unused")
@Composable
private fun Shown(visible: Boolean, content: @Composable () -> Unit) = AnimatedVisibility(visible) { content() }

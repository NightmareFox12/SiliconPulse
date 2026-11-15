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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.admin.siliconpulse.ui.theme.Panel
import com.admin.siliconpulse.ui.theme.PanelHigh
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
                        style = MaterialTheme.typography.bodyMedium,
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
                item { Text("Apps a revisar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
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
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
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
    val t = rememberInfiniteTransition(label = "scan")
    // Anillos que se expanden desde el disco (como un radar).
    val ring1 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "r1")
    val ring2 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, delayMillis = 1100, easing = LinearEasing)), label = "r2")
    // Barrido que gira mientras analiza.
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweep")
    // Respiracion suave del disco en reposo.
    val breath by t.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breath")

    val dark = com.admin.siliconpulse.ui.theme.SPTheme.palette.isDark
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val base = size.minDimension / 2 * 0.62f
                fun ring(p: Float) {
                    if (p <= 0f) return
                    val r = base + (size.minDimension / 2 - base) * p
                    drawCircle(Cyan.copy(alpha = (1f - p) * 0.45f), radius = r, center = c, style = Stroke(width = 3f))
                }
                ring(ring1); ring(ring2)
                // Halo: solo en oscuro. Sobre blanco parece una mancha, no una luz.
                if (dark) drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = 0.35f), Color.Transparent), center = c, radius = base * 1.35f), radius = base * 1.35f, center = c)
                if (analyzing) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, Cyan, Mint), center = c),
                        startAngle = sweep,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(c.x - base - 14f, c.y - base - 14f),
                        size = androidx.compose.ui.geometry.Size((base + 14f) * 2, (base + 14f) * 2),
                        style = Stroke(width = 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
            val scale = if (analyzing) 1f else breath
            // Disco oscuro tipo radar: obsidiana con halo cian, anillo luminoso y texto claro.
            Box(
                Modifier
                    .size((204 * scale).dp)
                    .then(if (dark) Modifier else Modifier.shadow(18.dp, CircleShape, ambientColor = Cyan.copy(alpha = 0.35f), spotColor = Cyan.copy(alpha = 0.35f)))
                    .clip(CircleShape)
                    .background(if (dark) Brush.radialGradient(listOf(Cyan.copy(alpha = 0.22f), PanelHigh, Panel)) else Brush.linearGradient(listOf(Panel, Panel)))
                    .border(2.dp, Brush.sweepGradient(listOf(Cyan, Mint, Cyan.copy(alpha = 0.3f), Cyan)), CircleShape)
                    .clickable(enabled = !analyzing, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (analyzing) {
                        CircularProgressIndicator(Modifier.size(34.dp), color = Cyan, strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("Analizando", style = MaterialTheme.typography.titleSmall, color = Cyan, fontWeight = FontWeight.Bold)
                    } else {
                        ShieldGlyph()
                        Spacer(Modifier.height(6.dp))
                        Text("ANALIZAR", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black)
                        Text("mi telefono", style = MaterialTheme.typography.bodyMedium, color = Cyan)
                    }
                }
            }
        }
        Text(
            progress ?: "Toca para revisar camara, microfono, ubicacion, apps sospechosas y bateria",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** Escudo con tilde dibujado a mano: icono de "auditoria" sin depender de iconos extendidos. */
@Composable
private fun ShieldGlyph() {
    Canvas(Modifier.size(34.dp)) {
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w / 2, 0f)
            lineTo(w, h * 0.2f)
            lineTo(w * 0.92f, h * 0.6f)
            quadraticTo(w * 0.8f, h * 0.9f, w / 2, h)
            quadraticTo(w * 0.2f, h * 0.9f, w * 0.08f, h * 0.6f)
            lineTo(0f, h * 0.2f)
            close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Cyan, Mint)), style = Stroke(width = 3f))
        drawPath(p, Cyan.copy(alpha = 0.12f))
        val check = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.3f, h * 0.5f); lineTo(w * 0.45f, h * 0.65f); lineTo(w * 0.72f, h * 0.35f)
        }
        drawPath(check, Mint, style = Stroke(width = 3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun FindingCard(f: SimpleFinding, vm: SimpleViewModel) {
    val c = verdictColor(f.verdict)
    Panel(f.title, accent = c, trailing = { StatusPill(verdictLabel(f.verdict), alert = f.verdict == Verdict.ALERT, neutral = f.verdict == Verdict.OK) }) {
        Text(f.detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
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

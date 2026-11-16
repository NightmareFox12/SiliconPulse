package com.admin.siliconpulse.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.admin.siliconpulse.data.export.AiTarget
import com.admin.siliconpulse.data.export.AiTargets
import com.admin.siliconpulse.ui.theme.Cyan
import com.admin.siliconpulse.ui.theme.Ink
import com.admin.siliconpulse.ui.theme.Panel
import com.admin.siliconpulse.ui.theme.Sky
import com.admin.siliconpulse.ui.theme.TextMuted

/**
 * selector de IA y opcionalmente ofrece compartir el texto por WhatsApp.
 * Lo usan la vista Pro (pestana Informe) y la vista Sencilla (tras el analisis).
 */
@Composable
fun AiExportBlock(
    visible: Boolean,
    fullPrompt: () -> String?,
    shortPrompt: () -> String?,
    onMessage: (String) -> Unit,
    shareText: (() -> String?)? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        AiTargetDialog(
            onDismiss = { showDialog = false },
            onPick = { t ->
                showDialog = false
                val full = fullPrompt(); val short = shortPrompt()
                if (full != null && short != null) onMessage(AiTargets.open(ctx, t, full, short))
            },
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(450)) +
            expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically(),
    ) {
        AiExportCard(
            onClick = { showDialog = true },
            onShare = shareText?.let { provider -> { provider()?.let { text -> shareViaWhatsAppOrChooser(ctx, text) } } },
        )
    }
}

private fun shareViaWhatsAppOrChooser(ctx: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    val wa = Intent(send).setPackage("com.whatsapp")
    val ok = runCatching { ctx.startActivity(wa); true }.getOrDefault(false)
    if (!ok) ctx.startActivity(Intent.createChooser(send, "Compartir informe"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiExportCard(onClick: () -> Unit, onShare: (() -> Unit)?) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Cyan.copy(alpha = 0.18f), Sky.copy(alpha = 0.10f))))
            .border(1.5.dp, Brush.linearGradient(listOf(Cyan, Sky)), shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Informe listo para una IA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "El informe se envia con instrucciones para que la IA lo interprete y explique, en lenguaje claro, si hay algo preocupante y que hacer.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AiTargets.all.forEach { AiLogo(it, 34.dp) }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink),
        ) {
            Text("Elegir IA y enviar")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        if (onShare != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, Cyan),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartir por WhatsApp")
            }
        }
    }
}

@Composable
fun AiLogo(t: AiTarget, size: Dp) {
    val ctx = LocalContext.current
    val resId = remember(t.id) { ctx.resources.getIdentifier(t.logoResName, "drawable", ctx.packageName) }
    if (resId != 0) {
        Image(painter = painterResource(resId), contentDescription = t.name, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(t.brand), contentAlignment = Alignment.Center) {
            Text(t.name.take(1), style = MaterialTheme.typography.titleSmall, color = Color.White)
        }
    }
}

@Composable
fun AiTargetDialog(onDismiss: () -> Unit, onPick: (AiTarget) -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Enviar informe a una IA", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text("El informe con instrucciones se copia al portapapeles y se abre la app o su web.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Spacer(Modifier.height(10.dp))
                AiTargets.all.forEach { t ->
                    val installed = remember(t.id) { AiTargets.isInstalled(ctx, t) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(t) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AiLogo(t, 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                when {
                                    installed -> "App instalada"
                                    t.prefillUrl != null -> "Web, con el resumen ya escrito"
                                    else -> "Web, hay que pegar el informe"
                                },
                                style = MaterialTheme.typography.bodySmall, color = TextMuted,
                            )
                        }
                        if (installed) StatusPill("APP", alert = false)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

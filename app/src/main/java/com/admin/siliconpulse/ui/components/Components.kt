package com.admin.siliconpulse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.admin.siliconpulse.ui.theme.Mint
import com.admin.siliconpulse.ui.theme.Rose
import com.admin.siliconpulse.ui.theme.TextMuted

@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun KeyValue(key: String, value: String?, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.weight(1f))
        Text(
            value ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = if (value == null) TextMuted else valueColor,
            modifier = Modifier.weight(1.3f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
fun BigStat(label: String, value: String, unit: String = "", color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

/** Indicador de estado tipo LED: verde = ok/inactivo, rojo = alerta/activo. */
@Composable
fun StatusPill(text: String, alert: Boolean, modifier: Modifier = Modifier, neutral: Boolean = false) {
    val color = when {
        neutral -> TextMuted
        alert -> Rose
        else -> Mint
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Dot(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(8.dp).clip(CircleShape)) { drawCircle(color) }
}

@Composable
fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

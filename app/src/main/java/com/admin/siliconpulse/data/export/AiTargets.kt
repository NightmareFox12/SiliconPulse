package com.admin.siliconpulse.data.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color

/**
 * Destinos de "Exportar para IA". Para cada uno: nombre, color de marca (para el monograma
 * si no hay logo), paquete Android y, si lo soporta, URL web con el texto prellenado.
 *
 * Flujo: siempre copiamos el prompt al portapapeles; luego abrimos la app si esta instalada
 * o la web. Las URLs con texto prellenado tienen limite practico (~8 KB), asi que en ese
 * caso mandamos solo el resumen ejecutivo y el usuario pega el resto desde el portapapeles.
 */
data class AiTarget(
    val id: String,
    val name: String,
    val brand: Color,
    val packageName: String,
    val webUrl: String,
    /** Plantilla con %s para el texto prellenado, o null si la web no lo soporta. */
    val prefillUrl: String?,
) {
    /** Nombre del drawable opcional: ai_<id>.png en res/drawable-nodpi. */
    val logoResName: String get() = "ai_$id"
}

object AiTargets {
    val all = listOf(
        AiTarget("claude", "Claude", Color(0xFFD97757), "com.anthropic.claude", "https://claude.ai/new", "https://claude.ai/new?q=%s"),
        AiTarget("chatgpt", "ChatGPT", Color(0xFF10A37F), "com.openai.chatgpt", "https://chatgpt.com/", "https://chatgpt.com/?q=%s"),
        AiTarget("gemini", "Gemini", Color(0xFF4E8DF5), "com.google.android.apps.bard", "https://gemini.google.com/app", null),
        AiTarget("deepseek", "DeepSeek", Color(0xFF4D6BFE), "com.deepseek.chat", "https://chat.deepseek.com/", null),
        AiTarget("copilot", "Copilot", Color(0xFF7B61FF), "com.microsoft.copilot", "https://copilot.microsoft.com/", null),
        AiTarget("perplexity", "Perplexity", Color(0xFF20808D), "ai.perplexity.app.android", "https://www.perplexity.ai/", "https://www.perplexity.ai/search?q=%s"),
        AiTarget("kimi", "Kimi", Color(0xFF111111), "com.moonshot.kimichat", "https://www.kimi.com/", null),
    )

    fun isInstalled(context: Context, t: AiTarget): Boolean =
        runCatching { context.packageManager.getPackageInfo(t.packageName, 0); true }.getOrDefault(false)

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /**
     * Abre el destino. Devuelve un mensaje para el usuario.
     * `fullPrompt` va al portapapeles; `shortPrompt` se usa para prellenar la web si cabe.
     */
    fun open(context: Context, t: AiTarget, fullPrompt: String, shortPrompt: String): String {
        copyToClipboard(context, "SiliconPulse para ${t.name}", fullPrompt)
        val installed = isInstalled(context, t)
        if (installed) {
            // Primero intentamos ACTION_SEND al paquete (muchas apps de IA aceptan texto compartido).
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, fullPrompt)
                setPackage(t.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val sendOk = runCatching { context.startActivity(send); true }.getOrDefault(false)
            if (sendOk) return "Enviado a ${t.name}. Si no aparece el texto, pegalo: ya esta en el portapapeles."
            val launch = context.packageManager.getLaunchIntentForPackage(t.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return "${t.name} abierta. Pega el informe (ya esta en el portapapeles)."
            }
        }
        val url = t.prefillUrl?.let { String.format(it, Uri.encode(shortPrompt)) } ?: t.webUrl
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return if (t.prefillUrl != null) "${t.name} abierta en el navegador con el resumen. El informe completo esta en el portapapeles."
        else "${t.name} abierta en el navegador. Pega el informe (ya esta en el portapapeles)."
    }
}

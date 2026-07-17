package com.aisoul.app.widgets.launcher

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aisoul.app.AiSoulApp
import com.aisoul.app.MainActivity
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import com.aisoul.app.widgets.WidgetSpec
import com.aisoul.app.widgets.WidgetStore

/**
 * D-033 — the home-screen widget: a zero-config mini-dashboard. Renders the
 * first few ACTIVE widgets from their cached .state/ values only — it never
 * executes sources, so the capability freeze is untouched. Tap opens the app.
 */
class AiSoulGlanceWidget : GlanceAppWidget() {

    data class LauncherRow(val title: String, val line: String)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = runCatching { loadRows(context) }.getOrElse { emptyList() }
        provideContent { WidgetContent(rows) }
    }

    private suspend fun loadRows(context: Context): List<LauncherRow> {
        val container = (context.applicationContext as AiSoulApp).container
        val store = container.widgets
        return store.list()
            .filter { it.state == WidgetStore.State.ACTIVE }
            .take(3)
            .mapNotNull { installed ->
                val spec = installed.spec ?: return@mapNotNull null
                val values = store.readValues(spec.id)?.values ?: emptyMap()
                LauncherRow(spec.title, primaryLine(spec, values))
            }
    }
}

// Android's ICU regex rejects unescaped closing braces — keep them escaped.
private val template = Regex("\\{([a-z0-9_]+)\\}")

private fun substitute(text: String?, values: Map<String, String>): String =
    template.replace(text.orEmpty()) { match -> values[match.groupValues[1]] ?: "—" }

/** the one line that stands for a widget: first stat, text, or list head */
private fun primaryLine(spec: WidgetSpec, values: Map<String, String>): String {
    spec.body.forEach { component ->
        when (component.type) {
            "stat" -> return listOf(substitute(component.value, values), component.label.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" ")
            "text" -> {
                val line = substitute(component.text, values)
                if (line.isNotBlank()) return line.take(80)
            }
            "list" -> {
                val raw = component.items_from?.let { values[it] }.orEmpty()
                raw.lines().firstOrNull { it.isNotBlank() }?.let { return it.trim().take(80) }
            }
        }
    }
    return ""
}

@Composable
private fun WidgetContent(rows: List<AiSoulGlanceWidget.LauncherRow>) {
    var surface = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Surface1))
    if (Build.VERSION.SDK_INT >= 31) surface = surface.cornerRadius(20.dp)
    Column(
        modifier = surface
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp),
    ) {
        Text(
            text = "AISOUL",
            style = TextStyle(color = ColorProvider(TextTertiary), fontSize = 10.sp, fontWeight = FontWeight.Medium),
        )
        if (rows.isEmpty()) {
            Spacer(GlanceModifier.height(10.dp))
            Text(
                text = "open aisoul to grow your dashboard",
                style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 12.sp),
            )
        }
        rows.forEach { row ->
            Spacer(GlanceModifier.height(10.dp))
            Text(
                text = row.title,
                style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 12.sp),
                maxLines = 1,
            )
            if (row.line.isNotBlank()) {
                Text(
                    text = row.line,
                    style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }
}

class AiSoulWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AiSoulGlanceWidget()
}

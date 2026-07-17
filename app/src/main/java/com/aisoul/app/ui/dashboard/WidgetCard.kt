package com.aisoul.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aisoul.app.ui.common.CountUpText
import com.aisoul.app.ui.common.GhostButton
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.Divider
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Negative
import com.aisoul.app.ui.theme.Positive
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.RadiusChip
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.Surface2
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import com.aisoul.app.ui.theme.aiSoulSpring
import com.aisoul.app.ui.theme.fadeSpec
import com.aisoul.app.ui.theme.reducedMotionFade
import com.aisoul.app.ui.theme.rememberReducedMotion
import com.aisoul.app.widgets.ActionSpec
import com.aisoul.app.widgets.ComponentSpec
import com.aisoul.app.widgets.WidgetStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DESIGN.md §6 — widget birth, the one theatrical moment: an approved widget
 * materializes once, springs to rest, and never performs again.
 */
fun Modifier.birthEntrance(run: Boolean, onDone: () -> Unit): Modifier = composed {
    if (!run) return@composed this
    val reduced = rememberReducedMotion()
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(if (reduced) 1f else 0.82f) }
    LaunchedEffect(Unit) {
        delay(250)
        launch { alpha.animateTo(1f, if (reduced) reducedMotionFade() else fadeSpec()) }
        if (!reduced) scale.animateTo(1f, aiSoulSpring())
        onDone()
    }
    graphicsLayer {
        this.alpha = alpha.value
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** SPEC §8 — one composable maps DSL components to themed composables. */
@Composable
fun WidgetCard(
    ui: WidgetUi,
    onAction: (ActionSpec) -> Unit,
    onApprove: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAiSoulTypography.current
    val spec = ui.installed.spec

    val base = modifier
        .fillMaxWidth()
        .clip(RadiusCard)
        .background(Surface1)
        .border(1.dp, BorderSubtle, RadiusCard)

    when (ui.installed.state) {
        WidgetStore.State.INVALID -> Column(modifier = base.padding(Space.card)) {
            Text(text = ui.installed.id.uppercase(), style = type.overline, color = TextTertiary)
            Spacer(Modifier.height(Space.s8))
            Text(text = "this widget can't render.", style = type.body, color = TextSecondary)
            ui.installed.problems.firstOrNull()?.let {
                Spacer(Modifier.height(Space.s4))
                Text(text = it, style = type.caption, color = TextTertiary)
            }
            Spacer(Modifier.height(Space.s8))
            Row {
                GhostButton(text = "remove", onClick = onRemove)
            }
        }

        WidgetStore.State.NEEDS_APPROVAL -> Column(modifier = base.padding(Space.card)) {
            Text(text = (spec?.title ?: ui.installed.id).uppercase(), style = type.overline, color = TextTertiary)
            Spacer(Modifier.height(Space.s8))
            Text(
                text = "this widget changed since you approved it. frozen means frozen — review, then re-approve.",
                style = type.body,
                color = TextSecondary,
            )
            spec?.let { s ->
                ui.installed.capabilities?.summaryLines(s.refresh)?.forEach { line ->
                    Spacer(Modifier.height(Space.s4))
                    Text(text = "· $line", style = type.caption, color = TextTertiary)
                }
            }
            Spacer(Modifier.height(Space.s8))
            Row {
                GhostButton(text = "re-approve", onClick = onApprove)
                GhostButton(text = "remove", onClick = onRemove)
            }
        }

        WidgetStore.State.ACTIVE -> {
            spec ?: return
            val heightFloor = when (spec.size) {
                "small" -> 88.dp
                "large" -> 220.dp
                else -> 150.dp
            }
            Column(
                modifier = base
                    .heightIn(min = heightFloor)
                    .let { m -> if (spec.tap != null) m.pressable { onAction(spec.tap) } else m }
                    .padding(Space.card),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = spec.title.uppercase(),
                        style = type.overline,
                        color = TextTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    if (spec.sources.isNotEmpty()) {
                        Text(
                            text = ui.lastRefreshedAt?.let { relative(it) } ?: "",
                            style = type.caption,
                            color = TextTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(Space.s12))
                Column(verticalArrangement = Arrangement.spacedBy(Space.s12)) {
                    spec.body.forEach { component ->
                        WidgetComponent(component, ui, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetComponent(
    component: ComponentSpec,
    ui: WidgetUi,
    onAction: (ActionSpec) -> Unit,
) {
    val type = LocalAiSoulTypography.current
    when (component.type) {
        "text" -> {
            val text = resolve(component.text, ui.values)
            Text(
                text = text,
                style = when (component.style) {
                    "title" -> type.title
                    "caption" -> type.caption
                    else -> type.body
                },
                color = when (component.style) {
                    "caption" -> TextTertiary
                    else -> TextPrimary
                },
            )
        }

        "stat" -> {
            val value = resolve(component.value, ui.values)
            val color = component.ok_when?.let { expected ->
                if (value.trim().equals(expected.trim(), ignoreCase = true)) Positive else Negative
            } ?: TextPrimary
            Column {
                val asInt = value.trim().toIntOrNull()
                if (asInt != null) {
                    CountUpText(value = asInt, style = type.dataHero, color = color)
                } else {
                    Text(text = value, style = type.headline, color = color)
                }
                Spacer(Modifier.height(Space.s4))
                Text(
                    text = resolve(component.label, ui.values),
                    style = type.caption,
                    color = TextSecondary,
                )
            }
        }

        "list" -> {
            val raw = component.items_from?.let { ui.values[it] }.orEmpty()
            val items = raw.lines().map(String::trim).filter { it.isNotEmpty() && it != "—" }
            if (items.isEmpty()) {
                Text(
                    text = component.empty ?: "—",
                    style = type.body,
                    color = TextTertiary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                    items.take(8).forEach { item ->
                        Text(text = "· $item", style = type.body, color = TextSecondary)
                    }
                }
            }
        }

        "progress" -> {
            val value = resolve(component.value, ui.values).toFloatOrNull() ?: 0f
            val max = component.max?.let { resolve(it, ui.values).toFloatOrNull() } ?: 1f
            val fraction = (if (max > 0f) value / max else 0f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RadiusChip)
                    .background(Surface2),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RadiusChip)
                        .background(AccentIce),
                )
            }
        }

        "sparkline" -> {
            val samples = component.source?.let { ui.histories[it] }.orEmpty()
            if (samples.size < 2) {
                Text(text = "collecting…", style = type.caption, color = TextTertiary)
            } else {
                Sparkline(samples)
            }
        }

        "buttons" -> Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
            component.items.forEach { button ->
                GhostButton(text = button.label, onClick = { onAction(button.action) })
            }
        }

        "divider" -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider),
        )
    }
}

@Composable
private fun Sparkline(samples: List<Double>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        val min = samples.min()
        val max = samples.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
        val pad = 3.dp.toPx()
        val usable = size.height - pad * 2
        var previous: Offset? = null
        samples.forEachIndexed { index, sample ->
            val x = index * stepX
            val y = pad + usable * (1f - ((sample - min) / span).toFloat())
            val point = Offset(x, y)
            previous?.let { from ->
                drawLine(
                    color = AccentIce,
                    start = from,
                    end = point,
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            previous = point
        }
    }
}

/** {source} templating against the widget's frozen values — data only, never capability. */
private fun resolve(template: String?, values: Map<String, String>): String {
    template ?: return ""
    return Regex("\\{([a-z0-9_]+)\\}").replace(template) { match ->
        values[match.groupValues[1]] ?: "—"
    }
}

private fun relative(at: Long): String {
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

package com.aisoul.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface0
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary

/**
 * The persistent app shell (D-038). Four primary destinations live in a bottom
 * bar (DESIGN.md §3 caps nav at 4); everything else is a drill-in with a back
 * affordance in the shared [TopBar].
 */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("shell_home", "home", AiSoulIcons.Grid),
    CHAT("shell_chat", "chat", AiSoulIcons.Chat),
    FILES("shell_files", "files", AiSoulIcons.Files),
    SETTINGS("shell_settings", "settings", AiSoulIcons.Settings),
    ;

    companion object {
        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}

/**
 * The one top bar. A quiet utility row — back on the left when the screen is a
 * drill-in, an overline label, actions on the right. The screen's hero stays in
 * its content (DESIGN.md §3), never up here.
 */
@Composable
fun TopBar(
    label: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val type = LocalAiSoulTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s12, vertical = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(icon = AiSoulIcons.Back, contentDescription = "back", onClick = onBack)
        }
        Text(
            text = label,
            style = type.overline,
            color = TextTertiary,
            modifier = Modifier
                .padding(start = if (onBack == null) Space.s12 else Space.s4)
                .weight(1f),
        )
        actions()
    }
}

/** A quiet 44dp icon target for the top bar — no fill, no border, just the glyph. */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RadiusCard)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The persistent bottom bar. No accent here — selection is lightness, not color. */
@Composable
fun BottomBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    val type = LocalAiSoulTypography.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface0)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderSubtle),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.s8, vertical = Space.s4),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Destination.entries.forEach { dest ->
                val selected = dest == current
                val tint = if (selected) TextPrimary else TextTertiary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RadiusCard)
                        .pressable { onSelect(dest) }
                        .padding(vertical = Space.s8),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.s4),
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = dest.label,
                        style = type.caption,
                        color = tint,
                    )
                }
            }
        }
    }
}

package com.aisoul.app.ui.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.aisoul.app.di.AppContainer
import com.aisoul.app.harness.HarnessEntry
import com.aisoul.app.ui.common.TopBar
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary

/**
 * SPEC §3 — every harness file is visible and editable. This browser is the
 * product's honesty made navigable.
 */
@Composable
fun FilesScreen(
    container: AppContainer,
    path: String = "",
    onBack: (() -> Unit)? = null,
    onOpenDir: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val type = LocalAiSoulTypography.current
    val entries by produceState<List<HarnessEntry>?>(initialValue = null, path) {
        value = runCatching { container.harness.listDir(path) }.getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(label = "FILES", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.s16),
        ) {
            item {
                Column(modifier = Modifier.staggeredEntrance(0)) {
                    Text(
                        text = path.ifEmpty { "your files" },
                        style = type.headline,
                        color = TextPrimary,
                    )
                    if (path.isEmpty()) {
                        Spacer(Modifier.height(Space.s8))
                        Text(
                            text = "everything the ai is, in plain text. tap to read or edit.",
                            style = type.body,
                            color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(Space.stack))
                }
            }

            val list = entries
            if (list != null && list.isEmpty()) {
                item {
                    Text(text = "nothing here yet", style = type.body, color = TextTertiary)
                }
            }

            items(list?.size ?: 0) { index ->
                val entry = list!![index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RadiusCard)
                        .pressable {
                            if (entry.isDir) onOpenDir(entry.relPath) else onOpenFile(entry.relPath)
                        }
                        .padding(vertical = Space.s16, horizontal = Space.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (entry.isDir) "${entry.name}/" else entry.name,
                            style = type.body,
                            color = TextPrimary,
                        )
                        if (!entry.isDir) {
                            Text(
                                text = formatSize(entry.size),
                                style = type.caption,
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes b"
    bytes < 1024 * 1024 -> "${bytes / 1024} kb"
    else -> "${bytes / (1024 * 1024)} mb"
}

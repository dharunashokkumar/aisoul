package com.aisoul.app.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aisoul.app.di.AppContainer
import com.aisoul.app.ui.common.GhostButton
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import com.aisoul.app.widgets.ActionSpec
import kotlinx.coroutines.launch

/**
 * SPEC §4 step 5 / §8 — the home screen the AI grows. Default widgets are
 * ordinary DSL files; AI-proposed ones arrive through the approval sheet and
 * are born here, once.
 */
@Composable
fun DashboardScreen(
    container: AppContainer,
    onOpenChat: (prompt: String?) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTerminal: (command: String?) -> Unit,
    onOpenBackup: () -> Unit,
) {
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val widgets by viewModel.widgets.collectAsStateWithLifecycle()
    val type = LocalAiSoulTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // SPEC §4 step 6 — the backup nudge: a dismissible card, never a blocking step
    val driveEnabled by container.backupSettings.driveEnabled.collectAsStateWithLifecycle(initialValue = true)
    val nudgeDismissed by container.backupSettings.nudgeDismissed.collectAsStateWithLifecycle(initialValue = true)
    val showNudge = !driveEnabled && !nudgeDismissed

    fun handle(ui: WidgetUi, action: ActionSpec) {
        when (action.type) {
            "chat" -> onOpenChat(action.prompt)
            "run" -> onOpenTerminal(action.command)
            "url" -> action.url?.let { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            "refresh" -> viewModel.refresh(ui.installed.id)
            "screen" -> when (action.screen) {
                "chat" -> onOpenChat(null)
                "memory" -> onOpenMemory()
                "files" -> onOpenFiles()
                "terminal" -> onOpenTerminal(null)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.s12, vertical = Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AISOUL",
                style = type.overline,
                color = TextTertiary,
                modifier = Modifier.padding(start = Space.s12).weight(1f),
            )
            GhostButton(text = "files", onClick = onOpenFiles)
            GhostButton(text = "settings", onClick = onOpenSettings)
        }

        val list = widgets ?: return
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.s16),
            verticalArrangement = Arrangement.spacedBy(Space.s16),
        ) {
            if (showNudge) {
                item(key = "backup-nudge") {
                    BackupNudgeCard(
                        onSetUp = onOpenBackup,
                        onDismiss = { scope.launch { container.backupSettings.dismissNudge() } },
                    )
                }
            }
            items(list.size) { index ->
                val ui = list[index]
                WidgetCard(
                    ui = ui,
                    onAction = { action -> handle(ui, action) },
                    onApprove = { viewModel.approveCurrent(ui.installed.id) },
                    onRemove = { viewModel.remove(ui.installed.id) },
                    modifier = if (ui.installed.needsBirth) {
                        Modifier.birthEntrance(run = true) { viewModel.markBorn(ui.installed.id) }
                    } else {
                        Modifier.staggeredEntrance(minOf(index, 9))
                    },
                )
            }
        }
    }
}

/** SPEC §4 step 6 — offers drive backup once real data exists to lose */
@Composable
private fun BackupNudgeCard(onSetUp: () -> Unit, onDismiss: () -> Unit) {
    val type = LocalAiSoulTypography.current
    Column(
        modifier = Modifier
            .staggeredEntrance(0)
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(Surface1)
            .border(1.dp, BorderSubtle, RadiusCard)
            .padding(Space.card),
    ) {
        Text(text = "BACKUP", style = type.overline, color = TextTertiary)
        Spacer(Modifier.height(Space.s8))
        Text(
            text = "everything it knows lives only on this phone. encrypted backups to your own drive keep it that way, safely.",
            style = type.body,
            color = TextSecondary,
        )
        Spacer(Modifier.height(Space.s16))
        Row {
            GhostButton(text = "set up", onClick = onSetUp)
            GhostButton(text = "later", onClick = onDismiss)
        }
    }
}

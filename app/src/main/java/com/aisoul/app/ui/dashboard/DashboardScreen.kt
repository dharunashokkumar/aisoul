package com.aisoul.app.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aisoul.app.di.AppContainer
import com.aisoul.app.ui.common.AiSoulTextField
import com.aisoul.app.ui.common.GhostButton
import com.aisoul.app.ui.common.PrimaryButton
import com.aisoul.app.ui.common.SecondaryButton
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.common.TopBar
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Negative
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.RadiusChip
import com.aisoul.app.ui.theme.RadiusSheet
import com.aisoul.app.ui.theme.Scrim
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.Surface2
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import com.aisoul.app.widgets.ActionSpec
import com.aisoul.app.widgets.WidgetStore
import kotlinx.coroutines.launch

/**
 * SPEC §4 step 5 / §8 — the home screen the AI grows. Default widgets are
 * ordinary DSL files; AI-proposed ones wait in the proposal inbox (D-030)
 * and are born here, once approved.
 */
@Composable
fun DashboardScreen(
    container: AppContainer,
    onOpenChat: (prompt: String?) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTerminal: (command: String?) -> Unit,
    onOpenBackup: () -> Unit,
) {
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val widgets by viewModel.widgets.collectAsStateWithLifecycle()
    val proposals by viewModel.proposals.collectAsStateWithLifecycle()
    val type = LocalAiSoulTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(label = "AISOUL") {
            GhostButton(text = "add", onClick = { showAdd = true })
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
            // D-030 — the proposal inbox: AI-proposed widgets wait here
            items(proposals.size, key = { "proposal-${proposals[it].id}" }) { index ->
                val proposal = proposals[index]
                ProposalCard(
                    proposal = proposal,
                    onApprove = { viewModel.approveProposal(proposal.id) },
                    onDismiss = { viewModel.dismissProposal(proposal.id) },
                )
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

    if (showAdd) {
        AddWidgetSheet(
            onAddCountdown = { title, date -> viewModel.addCountdown(title, date) },
            onAddHabit = { name -> viewModel.addHabit(name) },
            onDismiss = { showAdd = false },
        )
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

/** D-030 — one waiting proposal: what it is, exactly what it may ever do. */
@Composable
private fun ProposalCard(
    proposal: WidgetStore.Proposal,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val type = LocalAiSoulTypography.current
    Column(
        modifier = Modifier
            .staggeredEntrance(0)
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(Surface1)
            .border(1.dp, AccentIce.copy(alpha = 0.25f), RadiusCard)
            .padding(Space.card),
    ) {
        Text(text = "PROPOSED BY YOUR AI", style = type.overline, color = TextTertiary)
        Spacer(Modifier.height(Space.s8))
        Text(text = proposal.spec.title, style = type.title, color = TextPrimary)
        Spacer(Modifier.height(Space.s12))
        Text(text = "this widget will:", style = type.caption, color = TextSecondary)
        Spacer(Modifier.height(Space.s4))
        proposal.capabilities.forEach { line ->
            Text(text = "· $line", style = type.body, color = TextPrimary)
        }
        Spacer(Modifier.height(Space.s16))
        Row {
            GhostButton(text = "approve", onClick = onApprove)
            GhostButton(text = "dismiss", onClick = onDismiss)
        }
    }
}

/** D-031 — the add-widget gallery: golden DSL specs the user fills in. */
@Composable
private fun AddWidgetSheet(
    onAddCountdown: (title: String, date: String) -> String?,
    onAddHabit: (name: String) -> String?,
    onDismiss: () -> Unit,
) {
    val type = LocalAiSoulTypography.current
    var template by remember { mutableStateOf("countdown") }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var habit by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Scrim)) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .staggeredEntrance(0)
                .clip(RadiusSheet)
                .background(Surface2)
                .navigationBarsPadding()
                .padding(Space.card),
        ) {
            Text(text = "ADD WIDGET", style = type.overline, color = TextTertiary)
            Spacer(Modifier.height(Space.s16))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                TemplateChip(
                    label = "countdown",
                    selected = template == "countdown",
                    onClick = { template = "countdown"; error = null },
                )
                TemplateChip(
                    label = "habit",
                    selected = template == "habit",
                    onClick = { template = "habit"; error = null },
                )
            }
            Spacer(Modifier.height(Space.s16))
            if (template == "countdown") {
                Text(
                    text = "counts the days to a date, right on your dashboard.",
                    style = type.caption,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(Space.s12))
                AiSoulTextField(value = title, onValueChange = { title = it }, label = "what for (e.g. exam)")
                Spacer(Modifier.height(Space.s12))
                AiSoulTextField(value = date, onValueChange = { date = it }, label = "date (yyyy-mm-dd)")
            } else {
                Text(
                    text = "a daily habit log — one tap tells your ai you did it.",
                    style = type.caption,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(Space.s12))
                AiSoulTextField(value = habit, onValueChange = { habit = it }, label = "the habit (e.g. morning run)")
            }
            error?.let {
                Spacer(Modifier.height(Space.s8))
                Text(text = it, style = type.caption, color = Negative)
            }
            Spacer(Modifier.height(Space.s24))
            Row(modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Space.s12))
                PrimaryButton(
                    text = "add",
                    onClick = {
                        val problem = if (template == "countdown") {
                            onAddCountdown(title, date)
                        } else {
                            onAddHabit(habit)
                        }
                        if (problem == null) onDismiss() else error = problem
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TemplateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val type = LocalAiSoulTypography.current
    Box(
        modifier = Modifier
            .clip(RadiusChip)
            .background(if (selected) Surface1 else Surface2)
            .border(1.dp, if (selected) AccentIce.copy(alpha = 0.4f) else BorderSubtle, RadiusChip)
            .pressable(onClick = onClick)
            .padding(horizontal = Space.s16, vertical = Space.s8),
    ) {
        Text(
            text = label,
            style = type.caption,
            color = if (selected) TextPrimary else TextSecondary,
        )
    }
}

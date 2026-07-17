package com.aisoul.app.ui.backup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aisoul.app.backup.DriveArchive
import com.aisoul.app.di.AppContainer
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.AiSoulTextField
import com.aisoul.app.ui.common.PrimaryButton
import com.aisoul.app.ui.common.SecondaryButton
import com.aisoul.app.ui.common.hapticConfirm
import com.aisoul.app.ui.common.hapticReject
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderStrong
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.Grabber
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.Negative
import com.aisoul.app.ui.theme.Positive
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.RadiusChip
import com.aisoul.app.ui.theme.RadiusInput
import com.aisoul.app.ui.theme.RadiusSheet
import com.aisoul.app.ui.theme.Scrim
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.Surface2
import com.aisoul.app.ui.theme.TextInverse
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * SPEC §9 / §10 — drive connect, passphrase, schedule, archives, restore,
 * and the accountless SAF path. Restore always ends in a typed confirmation.
 */
@Composable
fun BackupScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val type = LocalAiSoulTypography.current
    val view = LocalView.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result.data) }
    LaunchedEffect(consent) {
        consent?.let {
            viewModel.consentLaunched()
            consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let(viewModel::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }

    if (state.restored) {
        RestoredScreen()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
        ) {
            Spacer(Modifier.height(Space.s8))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RadiusCard)
                    .pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AiSoulIcons.Back,
                    contentDescription = "back",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(Space.s32))
            Text(
                text = "backup",
                style = type.display,
                color = TextPrimary,
                modifier = Modifier.staggeredEntrance(0),
            )
            Spacer(Modifier.height(Space.s8))
            Text(
                text = "your whole harness, encrypted on this phone before it goes anywhere.",
                style = type.body,
                color = TextSecondary,
                modifier = Modifier.staggeredEntrance(1),
            )
            Spacer(Modifier.height(Space.stack))

            // ---- passphrase ----
            BackupCard(entrance = 2, label = "PASSPHRASE") {
                PassphraseSection(
                    hasPassphrase = state.hasPassphrase,
                    onSet = { pass, again ->
                        view.hapticConfirm()
                        viewModel.setPassphrase(pass, again)
                    },
                )
            }
            Spacer(Modifier.height(Space.s24))

            // ---- google drive ----
            BackupCard(entrance = 3, label = "GOOGLE DRIVE") {
                if (!state.driveEnabled) {
                    Text(
                        text = "daily encrypted backups to an “AiSoul Backups” folder in your own drive. aisoul can only ever see files it created.",
                        style = type.body,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(Space.s16))
                    SecondaryButton(text = "connect google drive", onClick = { viewModel.connect() })
                } else {
                    StatusRow(label = "account", value = state.accountEmail ?: "connected")
                    StatusRow(
                        label = "last backup",
                        value = if (state.lastBackupAt > 0) relativeTime(state.lastBackupAt) else "never",
                    )
                    state.lastResult?.let { StatusRow(label = "status", value = it) }
                    Spacer(Modifier.height(Space.s8))
                    CheckRow(
                        checked = state.wifiOnly,
                        label = "wi-fi only",
                        onToggle = { viewModel.setWifiOnly(!state.wifiOnly) },
                    )
                    if (state.needsReconnect) {
                        Spacer(Modifier.height(Space.s8))
                        Text(
                            text = "drive authorization lapsed — reconnect to keep backing up.",
                            style = type.caption,
                            color = Negative,
                        )
                        Spacer(Modifier.height(Space.s8))
                        SecondaryButton(text = "reconnect", onClick = { viewModel.connect() })
                        Spacer(Modifier.height(Space.s12))
                    } else {
                        Spacer(Modifier.height(Space.s16))
                    }
                    SecondaryButton(text = "back up now", onClick = {
                        view.hapticConfirm()
                        viewModel.backupNow()
                    })
                    Spacer(Modifier.height(Space.s12))
                    SecondaryButton(text = "disconnect", onClick = {
                        view.hapticReject()
                        viewModel.disconnect()
                    })

                    state.archives?.let { archives ->
                        Spacer(Modifier.height(Space.s24))
                        Text(text = "ARCHIVES", style = type.overline, color = TextTertiary)
                        Spacer(Modifier.height(Space.s8))
                        if (archives.isEmpty()) {
                            Text(
                                text = "no archives yet. the first backup lands after “back up now” or tonight's schedule.",
                                style = type.caption,
                                color = TextTertiary,
                            )
                        } else {
                            archives.forEach { archive ->
                                ArchiveRow(archive = archive, onRestore = { viewModel.restoreFromDrive(archive) })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s24))

            // ---- SAF fallback (SPEC §9): no google account needed ----
            BackupCard(entrance = 4, label = "THIS DEVICE") {
                Text(
                    text = "the same encrypted archive, through the system file picker. works with no google account; also the device-to-device path.",
                    style = type.body,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(Space.s16))
                SecondaryButton(text = "export archive to a file", onClick = {
                    exportLauncher.launch(viewModel.exportName())
                })
                Spacer(Modifier.height(Space.s12))
                SecondaryButton(text = "import archive from a file", onClick = {
                    importLauncher.launch(arrayOf("*/*"))
                })
            }

            Spacer(Modifier.height(Space.s24))
            state.busy?.let {
                Text(text = it, style = type.caption, color = TextTertiary)
                Spacer(Modifier.height(Space.s8))
            }
            state.error?.let {
                Text(
                    text = it,
                    style = type.caption,
                    color = Negative,
                    modifier = Modifier.pressable { viewModel.dismissMessage() },
                )
                Spacer(Modifier.height(Space.s8))
            }
            state.notice?.let {
                Text(
                    text = it,
                    style = type.caption,
                    color = Positive,
                    modifier = Modifier.pressable { viewModel.dismissMessage() },
                )
            }
            Spacer(Modifier.height(Space.s48))
        }

        state.passphrasePrompt?.let { prompt ->
            PassphraseSheet(
                prompt = prompt,
                onSubmit = viewModel::submitRestorePassphrase,
                onDismiss = viewModel::dismissRestore,
            )
        }
        state.restoreCandidate?.let { candidate ->
            RestoreConfirmSheet(
                candidate = candidate,
                onConfirm = {
                    view.hapticConfirm()
                    viewModel.confirmRestore()
                },
                onDismiss = viewModel::dismissRestore,
            )
        }
    }
}

@Composable
private fun BackupCard(entrance: Int, label: String, content: @Composable () -> Unit) {
    val type = LocalAiSoulTypography.current
    Column(
        modifier = Modifier
            .staggeredEntrance(entrance)
            .fillMaxWidth()
            .clip(RadiusCard)
            .background(Surface1)
            .border(1.dp, BorderSubtle, RadiusCard)
            .padding(Space.card),
    ) {
        Text(text = label, style = type.overline, color = TextTertiary)
        Spacer(Modifier.height(Space.s16))
        content()
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    val type = LocalAiSoulTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = type.body, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(text = value, style = type.body, color = TextPrimary)
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onToggle: () -> Unit) {
    val type = LocalAiSoulTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RadiusInput)
            .pressable(onClick = onToggle)
            .padding(vertical = Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RadiusInput)
                .background(if (checked) AccentIce else Surface1)
                .border(1.dp, BorderStrong, RadiusInput),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = AiSoulIcons.Check,
                    contentDescription = null,
                    tint = TextInverse,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.width(Space.s12))
        Text(text = label, style = type.body, color = TextPrimary)
    }
}

@Composable
private fun PassphraseSection(hasPassphrase: Boolean, onSet: (String, String) -> Unit) {
    val type = LocalAiSoulTypography.current
    var editing by remember { mutableStateOf(!hasPassphrase) }
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }

    if (!editing) {
        StatusRow(label = "passphrase", value = "set")
        Spacer(Modifier.height(Space.s12))
        SecondaryButton(text = "change passphrase", onClick = { editing = true })
    } else {
        Text(
            text = "every archive is sealed with this on the phone. lose the passphrase, lose the backups — nobody can recover them, including us.",
            style = type.body,
            color = TextSecondary,
        )
        Spacer(Modifier.height(Space.s16))
        AiSoulTextField(
            value = pass,
            onValueChange = { pass = it },
            label = "passphrase (8+ characters)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(Space.s12))
        AiSoulTextField(
            value = again,
            onValueChange = { again = it },
            label = "same passphrase, again",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(Space.s16))
        SecondaryButton(
            text = if (hasPassphrase) "change passphrase" else "set passphrase",
            enabled = pass.length >= 8 && again.isNotEmpty(),
            onClick = {
                onSet(pass, again)
                pass = ""
                again = ""
                editing = false
            },
        )
    }
}

@Composable
private fun ArchiveRow(archive: DriveArchive, onRestore: () -> Unit) {
    val type = LocalAiSoulTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = archive.name, style = type.code, color = TextPrimary, maxLines = 1)
            Text(
                text = "${relativeTime(archive.createdAtMillis)} · ${archive.sizeBytes / 1024} kb",
                style = type.caption,
                color = TextTertiary,
            )
        }
        Text(
            text = "restore",
            style = type.caption,
            color = TextSecondary,
            modifier = Modifier
                .clip(RadiusChip)
                .pressable(onClick = onRestore)
                .padding(horizontal = Space.s12, vertical = Space.s8),
        )
    }
}

// ---- restore sheets ----

@Composable
private fun SheetScaffold(content: @Composable () -> Unit) {
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
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RadiusChip)
                    .background(Grabber),
            )
            Spacer(Modifier.height(Space.s24))
            content()
        }
    }
}

@Composable
private fun PassphraseSheet(
    prompt: PassphrasePrompt,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val type = LocalAiSoulTypography.current
    var pass by remember { mutableStateOf("") }
    SheetScaffold {
        Text(text = "UNLOCK ARCHIVE", style = type.overline, color = TextTertiary)
        Spacer(Modifier.height(Space.s8))
        Text(text = prompt.sourceLabel, style = type.title, color = TextPrimary)
        Spacer(Modifier.height(Space.s16))
        AiSoulTextField(
            value = pass,
            onValueChange = { pass = it },
            label = "backup passphrase",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        prompt.error?.let {
            Spacer(Modifier.height(Space.s8))
            Text(text = it, style = type.caption, color = Negative)
        }
        Spacer(Modifier.height(Space.s24))
        Row(modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(text = "cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Space.s12))
            PrimaryButton(
                text = "unlock",
                enabled = pass.isNotEmpty(),
                onClick = { onSubmit(pass) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RestoreConfirmSheet(
    candidate: RestoreCandidate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val type = LocalAiSoulTypography.current
    var typed by remember { mutableStateOf("") }
    SheetScaffold {
        Text(text = "RESTORE", style = type.overline, color = TextTertiary)
        Spacer(Modifier.height(Space.s8))
        Text(text = "replace this phone's harness?", style = type.title, color = TextPrimary)
        Spacer(Modifier.height(Space.s16))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .clip(RadiusInput)
                .background(Surface1)
                .padding(Space.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "${candidate.preview.fileCount} files · ${candidate.preview.totalBytes / 1024} kb · newest ${relativeTime(candidate.preview.newestAt)}",
                style = type.body,
                color = TextPrimary,
            )
            Spacer(Modifier.height(Space.s8))
            candidate.preview.tree.forEach { (dir, count) ->
                Text(
                    text = "$dir — $count file${if (count == 1) "" else "s"}",
                    style = type.code,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(Space.s16))
        Text(
            text = "the current harness is set aside as one .pre-restore generation. api keys are never in backups — re-enter them after restoring to a new device.",
            style = type.caption,
            color = TextSecondary,
        )
        Spacer(Modifier.height(Space.s16))
        AiSoulTextField(
            value = typed,
            onValueChange = { typed = it },
            label = "type “restore” to confirm",
        )
        Spacer(Modifier.height(Space.s24))
        Row(modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(text = "cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Space.s12))
            PrimaryButton(
                text = "restore",
                enabled = typed.trim().equals("restore", ignoreCase = true),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RestoredScreen() {
    val type = LocalAiSoulTypography.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.screen),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(text = "restored.", style = type.display, color = TextPrimary, modifier = Modifier.staggeredEntrance(0))
        Spacer(Modifier.height(Space.s12))
        Text(
            text = "your harness is back. close aisoul so everything reopens from the restored files.",
            style = type.body,
            color = TextSecondary,
            modifier = Modifier.staggeredEntrance(1),
        )
        Spacer(Modifier.height(Space.s32))
        PrimaryButton(
            text = "close aisoul",
            onClick = {
                context.findActivity()?.finishAffinity()
                exitProcess(0)
            },
            modifier = Modifier.staggeredEntrance(2),
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun relativeTime(at: Long): String {
    if (at <= 0) return "unknown"
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 2 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 48 * 60 -> "${minutes / 60} h ago"
        minutes < 14 * 24 * 60 -> "${minutes / (60 * 24)} days ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(at))
    }
}

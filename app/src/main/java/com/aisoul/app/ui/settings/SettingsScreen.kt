package com.aisoul.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aisoul.app.AppLinks
import com.aisoul.app.BuildConfig
import com.aisoul.app.agent.PermissionMode
import com.aisoul.app.di.AppContainer
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.SecondaryButton
import com.aisoul.app.ui.common.hapticReject
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderStrong
import com.aisoul.app.ui.theme.BorderSubtle
import com.aisoul.app.ui.theme.LocalAiSoulTypography
import com.aisoul.app.ui.theme.RadiusCard
import com.aisoul.app.ui.theme.RadiusChip
import com.aisoul.app.ui.theme.RadiusInput
import com.aisoul.app.ui.theme.Space
import com.aisoul.app.ui.theme.Surface1
import com.aisoul.app.ui.theme.TextPrimary
import com.aisoul.app.ui.theme.TextSecondary
import com.aisoul.app.ui.theme.TextTertiary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** SPEC §10 — providers, permissions, memory, backup, terminal, about. */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onEditProvider: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val type = LocalAiSoulTypography.current
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val info by produceState<Triple<String, String, String?>?>(initialValue = null) {
        val provider = container.settings.selectedProvider.first()
        value = Triple(
            provider.display,
            container.settings.modelFor(provider).first(),
            container.keys.maskedKey(provider),
        )
    }
    val mode by container.permissions.mode.collectAsStateWithLifecycle(initialValue = PermissionMode.STANDARD)
    val hosts by container.permissions.allowedHosts.collectAsStateWithLifecycle(initialValue = emptySet())
    val commands by container.permissions.allowedCommands.collectAsStateWithLifecycle(initialValue = emptySet())

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
            text = "settings",
            style = type.display,
            color = TextPrimary,
            modifier = Modifier.staggeredEntrance(0),
        )
        Spacer(Modifier.height(Space.stack))

        Column(
            modifier = Modifier
                .staggeredEntrance(1)
                .fillMaxWidth()
                .clip(RadiusCard)
                .background(Surface1)
                .border(1.dp, BorderSubtle, RadiusCard)
                .padding(Space.card),
        ) {
            Text(text = "PROVIDER", style = type.overline, color = TextTertiary)
            Spacer(Modifier.height(Space.s16))
            info?.let { (providerName, model, maskedKey) ->
                SettingRow(label = "provider", value = providerName)
                SettingRow(label = "model", value = model)
                SettingRow(label = "api key", value = maskedKey ?: "not set")
            } ?: Text(text = "…", style = type.body, color = TextTertiary)
            Spacer(Modifier.height(Space.s24))
            SecondaryButton(text = "change provider or key", onClick = onEditProvider)
            Spacer(Modifier.height(Space.s12))
            SecondaryButton(
                text = "delete key from this device",
                onClick = {
                    scope.launch {
                        val provider = container.settings.selectedProvider.first()
                        container.keys.deleteKey(provider)
                        view.hapticReject()
                        onEditProvider()
                    }
                },
            )
        }

        Spacer(Modifier.height(Space.s24))

        // SPEC §6/§10 — the permission model, visible and revocable
        Column(
            modifier = Modifier
                .staggeredEntrance(2)
                .fillMaxWidth()
                .clip(RadiusCard)
                .background(Surface1)
                .border(1.dp, BorderSubtle, RadiusCard)
                .padding(Space.card),
        ) {
            Text(text = "PERMISSIONS", style = type.overline, color = TextTertiary)
            Spacer(Modifier.height(Space.s16))
            PermissionMode.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RadiusInput)
                        .pressable {
                            scope.launch { container.permissions.setMode(option) }
                        }
                        .padding(vertical = Space.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RadiusChip)
                            .border(1.dp, if (mode == option) AccentIce else BorderStrong, RadiusChip),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (mode == option) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RadiusChip)
                                    .background(AccentIce),
                            )
                        }
                    }
                    Spacer(Modifier.width(Space.s12))
                    Column {
                        Text(text = option.display, style = type.body, color = TextPrimary)
                        Text(text = option.blurb, style = type.caption, color = TextTertiary)
                    }
                }
            }

            if (hosts.isNotEmpty() || commands.isNotEmpty()) {
                Spacer(Modifier.height(Space.s16))
                Text(text = "ALWAYS ALLOWED", style = type.overline, color = TextTertiary)
                Spacer(Modifier.height(Space.s8))
                hosts.sorted().forEach { host ->
                    RuleRow(label = host) {
                        scope.launch {
                            container.permissions.revokeHost(host)
                            view.hapticReject()
                        }
                    }
                }
                commands.sorted().forEach { command ->
                    RuleRow(label = command) {
                        scope.launch {
                            container.permissions.revokeCommand(command)
                            view.hapticReject()
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(Space.s8))
                Text(
                    text = "no always-allow rules yet. they appear here when you approve one.",
                    style = type.caption,
                    color = TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(Space.s24))
        SecondaryButton(
            text = "memory — what it knows",
            onClick = onOpenMemory,
            modifier = Modifier.staggeredEntrance(3),
        )
        Spacer(Modifier.height(Space.s12))
        SecondaryButton(
            text = "backup — drive & export",
            onClick = onOpenBackup,
            modifier = Modifier.staggeredEntrance(4),
        )
        Spacer(Modifier.height(Space.s12))
        SecondaryButton(
            text = "terminal — the toolbox",
            onClick = onOpenTerminal,
            modifier = Modifier.staggeredEntrance(5),
        )

        Spacer(Modifier.height(Space.s24))

        // SPEC §10 about — version, licenses, privacy policy, report a problem
        Column(
            modifier = Modifier
                .staggeredEntrance(6)
                .fillMaxWidth()
                .clip(RadiusCard)
                .background(Surface1)
                .border(1.dp, BorderSubtle, RadiusCard)
                .padding(Space.card),
        ) {
            Text(text = "ABOUT", style = type.overline, color = TextTertiary)
            Spacer(Modifier.height(Space.s16))
            SettingRow(label = "version", value = BuildConfig.VERSION_NAME)
            LinkRow(label = "privacy policy") {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.PRIVACY_POLICY_URL)))
                }
            }
            LinkRow(label = "licenses", onClick = onOpenLicenses)
            LinkRow(label = "report a problem") {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(AppLinks.DEVELOPER_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, "aisoul ${BuildConfig.VERSION_NAME} — a problem")
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.stack))
        Text(
            text = "aisoul ${BuildConfig.VERSION_NAME} — your ai, your keys, your files.",
            style = type.caption,
            color = TextTertiary,
            modifier = Modifier.staggeredEntrance(7),
        )
        Spacer(Modifier.height(Space.s48))
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    val type = LocalAiSoulTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = type.body, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(text = value, style = type.body, color = TextPrimary)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    val type = LocalAiSoulTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RadiusInput)
            .pressable(onClick = onClick)
            .padding(vertical = Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = type.body, color = TextPrimary, modifier = Modifier.weight(1f))
        Icon(
            imageVector = AiSoulIcons.Chevron,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun RuleRow(label: String, onRevoke: () -> Unit) {
    val type = LocalAiSoulTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = type.code,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "revoke",
            style = type.caption,
            color = TextTertiary,
            modifier = Modifier
                .clip(RadiusChip)
                .pressable(onClick = onRevoke)
                .padding(horizontal = Space.s12, vertical = Space.s8),
        )
    }
}

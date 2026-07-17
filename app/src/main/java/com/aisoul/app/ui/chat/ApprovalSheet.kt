package com.aisoul.app.ui.chat

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.aisoul.app.agent.GateAction
import com.aisoul.app.agent.PermissionGate
import com.aisoul.app.ui.common.AiSoulIcons
import com.aisoul.app.ui.common.PrimaryButton
import com.aisoul.app.ui.common.SecondaryButton
import com.aisoul.app.ui.common.hapticConfirm
import com.aisoul.app.ui.common.hapticReject
import com.aisoul.app.ui.common.pressable
import com.aisoul.app.ui.common.staggeredEntrance
import com.aisoul.app.ui.theme.AccentIce
import com.aisoul.app.ui.theme.BorderStrong
import com.aisoul.app.ui.theme.Grabber
import com.aisoul.app.ui.theme.LocalAiSoulTypography
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

/**
 * SPEC §6 — the approval sheet. Shows the EXACT command / url / content,
 * never a summary alone. Suspends the agent turn until answered.
 */
@Composable
fun ApprovalSheet(
    approval: PermissionGate.PendingApproval,
    onRespond: (approved: Boolean, alwaysAllow: Boolean) -> Unit,
) {
    val type = LocalAiSoulTypography.current
    val view = LocalView.current
    var alwaysAllow by remember(approval) { mutableStateOf(false) }

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

            Text(text = "PERMISSION", style = type.overline, color = TextTertiary)
            Spacer(Modifier.height(Space.s8))
            Text(text = approval.action.headline(), style = type.title, color = TextPrimary)
            Spacer(Modifier.height(Space.s16))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(RadiusInput)
                    .background(Surface1)
                    .padding(Space.s16)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (val action = approval.action) {
                    is GateAction.InstallWidget -> {
                        Text(text = "this widget will:", style = type.caption, color = TextSecondary)
                        Spacer(Modifier.height(Space.s8))
                        action.capabilities.forEach { line ->
                            Text(text = "· $line", style = type.body, color = TextPrimary)
                        }
                    }
                    else -> Text(
                        text = approval.action.detail(),
                        style = type.code,
                        color = TextPrimary,
                    )
                }
            }

            approval.ruleOffer?.let { offer ->
                Spacer(Modifier.height(Space.s16))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RadiusInput)
                        .pressable { alwaysAllow = !alwaysAllow }
                        .padding(vertical = Space.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RadiusInput)
                            .background(if (alwaysAllow) AccentIce else Surface1)
                            .border(1.dp, BorderStrong, RadiusInput),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (alwaysAllow) {
                            Icon(
                                imageVector = AiSoulIcons.Check,
                                contentDescription = null,
                                tint = TextInverse,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(Space.s12))
                    Text(text = offer, style = type.body, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(Space.s24))
            Row(modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = "deny",
                    onClick = {
                        view.hapticReject()
                        onRespond(false, false)
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.s12))
                PrimaryButton(
                    text = "allow",
                    onClick = {
                        view.hapticConfirm()
                        onRespond(true, alwaysAllow)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun GateAction.headline(): String = when (this) {
    is GateAction.RunCommand -> "run a command"
    is GateAction.FetchHost -> "reach $host"
    is GateAction.EditSoulUser -> "edit $path"
    is GateAction.OverwriteFile -> "write $path"
    is GateAction.AppendMemoryNote -> "append to $path"
    is GateAction.InstallWidget -> "add widget “$title”"
}

private fun GateAction.detail(): String = when (this) {
    is GateAction.RunCommand -> command
    is GateAction.FetchHost -> "$method $url"
    is GateAction.EditSoulUser -> preview
    is GateAction.OverwriteFile -> preview
    is GateAction.AppendMemoryNote -> preview
    is GateAction.InstallWidget -> specJson
}

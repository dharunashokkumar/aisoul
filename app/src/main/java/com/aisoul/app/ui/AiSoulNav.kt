package com.aisoul.app.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aisoul.app.di.AppContainer
import com.aisoul.app.ui.backup.BackupScreen
import com.aisoul.app.ui.chat.ChatScreen
import com.aisoul.app.ui.dashboard.DashboardScreen
import com.aisoul.app.ui.files.FileEditorScreen
import com.aisoul.app.ui.files.FilesScreen
import com.aisoul.app.ui.history.HistoryScreen
import com.aisoul.app.ui.memory.MemoryScreen
import com.aisoul.app.ui.onboarding.OnboardingScreen
import com.aisoul.app.ui.onboarding.WelcomeScreen
import com.aisoul.app.ui.settings.LicensesScreen
import com.aisoul.app.ui.settings.SettingsScreen
import com.aisoul.app.ui.setup.SetupScreen
import com.aisoul.app.ui.terminal.TerminalScreen
import com.aisoul.app.ui.theme.Surface0
import com.aisoul.app.ui.theme.fadeSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object Routes {
    const val WELCOME = "welcome"
    const val SETUP = "setup"
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val CHAT = "chat?chatId={chatId}&prompt={prompt}"
    const val HISTORY = "history"
    const val TERMINAL = "terminal?cmd={cmd}"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val LICENSES = "licenses"
    const val MEMORY = "memory"
    const val FILES = "files?path={path}"
    const val EDIT = "edit?path={path}"

    fun chat(chatId: String? = null, prompt: String? = null) =
        "chat?chatId=${Uri.encode(chatId ?: "")}&prompt=${Uri.encode(prompt ?: "")}"

    fun terminal(cmd: String? = null) = "terminal?cmd=${Uri.encode(cmd ?: "")}"
    fun files(path: String) = "files?path=${Uri.encode(path)}"
    fun edit(path: String) = "edit?path=${Uri.encode(path)}"
}

@Composable
fun AiSoulNav(container: AppContainer) {
    // the front door: no key -> welcome; key but no soul -> interview; else the dashboard (D-023)
    val startRoute by produceState<String?>(initialValue = null) {
        val provider = container.settings.selectedProvider.first()
        val hasKey = container.keys.getKey(provider) != null
        val onboarded = container.settings.onboarded.first()
        value = when {
            !hasKey -> Routes.WELCOME
            !onboarded -> Routes.ONBOARDING
            else -> Routes.DASHBOARD
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface0)) {
        val start = startRoute ?: return@Box
        val nav = rememberNavController()
        val scope = rememberCoroutineScope()

        NavHost(
            navController = nav,
            startDestination = start,
            // DESIGN.md §4 — never a hard cut between screens
            enterTransition = { fadeIn(fadeSpec()) + slideInVertically(initialOffsetY = { it / 24 }) },
            exitTransition = { fadeOut(fadeSpec()) },
            popEnterTransition = { fadeIn(fadeSpec()) },
            popExitTransition = { fadeOut(fadeSpec()) },
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(onBegin = { nav.navigate(Routes.SETUP) })
            }
            composable(Routes.SETUP) {
                SetupScreen(
                    container = container,
                    onDone = {
                        scope.launch {
                            val next = if (container.settings.onboarded.first()) Routes.DASHBOARD else Routes.ONBOARDING
                            nav.navigate(next) { popUpTo(0) { inclusive = true } }
                        }
                    },
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    container = container,
                    onDone = {
                        nav.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    container = container,
                    onOpenChat = { prompt -> nav.navigate(Routes.chat(prompt = prompt)) },
                    onOpenFiles = { nav.navigate(Routes.files("")) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenMemory = { nav.navigate(Routes.MEMORY) },
                    onOpenTerminal = { cmd -> nav.navigate(Routes.terminal(cmd)) },
                    onOpenBackup = { nav.navigate(Routes.BACKUP) },
                )
            }
            composable(
                Routes.CHAT,
                arguments = listOf(
                    navArgument("chatId") { defaultValue = "" },
                    navArgument("prompt") { defaultValue = "" },
                ),
            ) { entry ->
                val chatId = Uri.decode(entry.arguments?.getString("chatId").orEmpty()).ifBlank { null }
                val prompt = Uri.decode(entry.arguments?.getString("prompt").orEmpty())
                ChatScreen(
                    container = container,
                    chatId = chatId,
                    initialPrompt = prompt,
                    onBack = { nav.popBackStack() },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    container = container,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { chatId ->
                        nav.navigate(Routes.chat(chatId = chatId)) {
                            popUpTo(Routes.DASHBOARD)
                        }
                    },
                )
            }
            composable(
                Routes.TERMINAL,
                arguments = listOf(navArgument("cmd") { defaultValue = "" }),
            ) { entry ->
                val cmd = Uri.decode(entry.arguments?.getString("cmd").orEmpty()).ifBlank { null }
                TerminalScreen(
                    container = container,
                    initialCommand = cmd,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = { nav.popBackStack() },
                    onEditProvider = { nav.navigate(Routes.SETUP) },
                    onOpenMemory = { nav.navigate(Routes.MEMORY) },
                    onOpenTerminal = { nav.navigate(Routes.terminal()) },
                    onOpenBackup = { nav.navigate(Routes.BACKUP) },
                    onOpenLicenses = { nav.navigate(Routes.LICENSES) },
                )
            }
            composable(Routes.BACKUP) {
                BackupScreen(
                    container = container,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.LICENSES) {
                LicensesScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.MEMORY) {
                MemoryScreen(
                    container = container,
                    onBack = { nav.popBackStack() },
                    onOpenFile = { path -> nav.navigate(Routes.edit(path)) },
                )
            }
            composable(
                Routes.FILES,
                arguments = listOf(navArgument("path") { defaultValue = "" }),
            ) { entry ->
                val path = Uri.decode(entry.arguments?.getString("path") ?: "")
                FilesScreen(
                    container = container,
                    path = path,
                    onBack = { nav.popBackStack() },
                    onOpenDir = { dir -> nav.navigate(Routes.files(dir)) },
                    onOpenFile = { file -> nav.navigate(Routes.edit(file)) },
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(navArgument("path") { defaultValue = "" }),
            ) { entry ->
                val path = Uri.decode(entry.arguments?.getString("path") ?: "")
                FileEditorScreen(
                    container = container,
                    path = path,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

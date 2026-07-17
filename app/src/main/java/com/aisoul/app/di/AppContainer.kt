package com.aisoul.app.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.aisoul.app.agent.AgentRuntime
import com.aisoul.app.agent.FetchTool
import com.aisoul.app.agent.ListFilesTool
import com.aisoul.app.agent.PermissionGate
import com.aisoul.app.agent.PermissionStore
import com.aisoul.app.agent.ProposeWidgetTool
import com.aisoul.app.agent.ReadFileTool
import com.aisoul.app.agent.RememberTool
import com.aisoul.app.agent.RunCommandTool
import com.aisoul.app.agent.ToolRegistry
import com.aisoul.app.agent.WriteFileTool
import com.aisoul.app.backup.Archiver
import com.aisoul.app.backup.Argon2Kdf
import com.aisoul.app.backup.BackupCrypto
import com.aisoul.app.backup.BackupManager
import com.aisoul.app.backup.BackupSettings
import com.aisoul.app.backup.BackupWorker
import com.aisoul.app.backup.DriveAuth
import com.aisoul.app.backup.DriveClient
import com.aisoul.app.distill.DistillWorker
import com.aisoul.app.harness.HarnessStore
import com.aisoul.app.harness.MemoryStore
import com.aisoul.app.providers.ProviderFactory
import com.aisoul.app.settings.SettingsStore
import com.aisoul.app.toolbox.ToolboxRunner
import com.aisoul.app.vault.KeyVault
import com.aisoul.app.vault.ProviderKeyStore
import com.aisoul.app.widgets.WidgetEngine
import com.aisoul.app.widgets.WidgetRefreshWorker
import com.aisoul.app.widgets.WidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual DI (D-012) — the app is small enough; keep it boring. */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val settingsDataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) { context.preferencesDataStoreFile("settings") }

    private val vaultDataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) { context.preferencesDataStoreFile("vault") }

    private val permissionsDataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) { context.preferencesDataStoreFile("permissions") }

    private val vault = KeyVault()

    val settings = SettingsStore(settingsDataStore)
    val keys = ProviderKeyStore(vaultDataStore, vault)
    val providerFactory = ProviderFactory(http)
    val harness = HarnessStore(context, json)
    val memories = MemoryStore(harness.root, json)

    val backupSettings = BackupSettings(settingsDataStore, vaultDataStore, vault)
    val driveAuth = DriveAuth(appContext)
    val backup = BackupManager(
        context = appContext,
        harnessRoot = harness.root,
        archiver = Archiver(),
        crypto = BackupCrypto(Argon2Kdf()),
        drive = DriveClient(http, json),
        settings = backupSettings,
    )

    val permissions = PermissionStore(permissionsDataStore)
    val gate = PermissionGate(permissions)
    val toolbox = ToolboxRunner(context, harness.root)
    val widgets = WidgetStore(harness.root, json)
    val widgetEngine = WidgetEngine(harness, memories, toolbox, http)

    val agent = AgentRuntime(
        registry = ToolRegistry(
            listOf(
                ReadFileTool(harness),
                ListFilesTool(harness),
                WriteFileTool(harness),
                FetchTool(http),
                RunCommandTool(toolbox),
                RememberTool(memories),
                ProposeWidgetTool(widgets),
            ),
        ),
        gate = gate,
    )

    fun scheduleDistill(chatId: String, delayMinutes: Long = 10) {
        DistillWorker.schedule(appContext, chatId, delayMinutes)
    }

    init {
        appScope.launch {
            harness.ensureSeeded()
            widgets.ensureDefaults()
            toolbox.ensureBootstrapped()
        }
        WidgetRefreshWorker.schedule(appContext)

        // IMPLEMENTATION §8 — every harness write slides the debounced backup
        val slideBackupDebounce = {
            appScope.launch {
                if (backupSettings.driveEnabled.first()) {
                    BackupWorker.debounce(appContext, backupSettings.wifiOnly.first())
                }
            }
            Unit
        }
        harness.onMutation = slideBackupDebounce
        memories.onMutation = slideBackupDebounce
        appScope.launch {
            if (backupSettings.driveEnabled.first()) {
                BackupWorker.schedulePeriodic(appContext, backupSettings.wifiOnly.first())
            }
        }
    }
}

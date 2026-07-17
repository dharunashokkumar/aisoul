package com.aisoul.app.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aisoul.app.AiSoulApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * IMPLEMENTATION §8 — daily periodic backup plus a debounced one-shot that
 * slides 30 minutes out on every harness write. Both land here; the worker
 * itself decides whether there is anything to do.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AiSoulApp).container
        val settings = container.backupSettings
        if (!settings.driveEnabled.first()) return Result.success()
        if (settings.passphrase() == null) {
            settings.recordBackup("no passphrase set — backup skipped", succeeded = false)
            return Result.success()
        }
        return when (val auth = runCatching { container.driveAuth.authorize() }.getOrNull()) {
            is DriveAuth.State.Ready -> {
                runCatching { container.backup.backupToDrive(auth.accessToken) }
                    .fold(
                        onSuccess = { Result.success() },
                        onFailure = { e ->
                            settings.recordBackup(e.message ?: "backup failed", succeeded = false)
                            if (runAttemptCount < 3) Result.retry() else Result.success()
                        },
                    )
            }
            is DriveAuth.State.NeedsConsent -> {
                settings.recordBackup("reconnect google drive to keep backing up", succeeded = false)
                Result.success()
            }
            null -> if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "drive-backup"
        private const val DEBOUNCED = "drive-backup-debounced"

        private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context, wifiOnly: Boolean) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints(wifiOnly))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(DEBOUNCED)
        }

        /** every harness write re-slides this 30 minutes out (quiet window) */
        fun debounce(context: Context, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(30, TimeUnit.MINUTES)
                .setConstraints(constraints(wifiOnly))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                DEBOUNCED,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

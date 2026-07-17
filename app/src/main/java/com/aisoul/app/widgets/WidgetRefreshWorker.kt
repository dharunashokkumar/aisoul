package com.aisoul.app.widgets

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aisoul.app.AiSoulApp
import java.util.concurrent.TimeUnit

/**
 * IMPLEMENTATION §6 — one periodic worker (15-min floor, WorkManager's own
 * minimum) walks due widgets. On-open refresh is immediate and in-process;
 * this is only the background path.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AiSoulApp).container
        val store = container.widgets
        val now = System.currentTimeMillis()
        store.list()
            .filter { it.state == WidgetStore.State.ACTIVE }
            .filter { (it.spec?.refresh?.interval_min ?: 0) > 0 }
            .forEach { widget ->
                val interval = widget.spec!!.refresh.interval_min * 60_000L
                val last = store.readValues(widget.id)?.at ?: 0L
                // 60s slack so a 15-min widget refreshes on every 15-min pass
                if (now - last >= interval - 60_000) {
                    runCatching { container.widgetEngine.refresh(widget, store) }
                }
            }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "widget-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

package com.aisoul.app.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.aisoul.app.AiSoulApp
import com.aisoul.app.MainActivity
import com.aisoul.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * IMPLEMENTATION §4 / O-6 — an agent turn survives the user switching apps:
 * the chat ViewModel starts this `specialUse` foreground service when a turn
 * begins and stops it when the turn ends. The notification flips to "needs
 * approval" while the permission gate is suspended on the human.
 */
class AgentTurnService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = build("aisoul is working — tap to open")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        scope.launch {
            (application as AiSoulApp).container.gate.pending.collect { pending ->
                notify(
                    if (pending != null) "aisoul needs approval to continue"
                    else "aisoul is working — tap to open",
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "agent turns", NotificationManager.IMPORTANCE_LOW).apply {
                description = "shown only while aisoul is working on a turn"
                setShowBadge(false)
            },
        )
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("aisoul")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(text))
    }

    companion object {
        private const val CHANNEL_ID = "agent-turn"
        private const val NOTIFICATION_ID = 41

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, AgentTurnService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AgentTurnService::class.java)) }
        }
    }
}

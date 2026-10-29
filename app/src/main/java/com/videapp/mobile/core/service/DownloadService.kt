package com.videapp.mobile.core.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.videapp.mobile.R
import com.videapp.mobile.VideAppApplication

/**
 * Foreground Service for background downloads with persistent notifications.
 * Complies with Android 14+ (API 34/35) FOREGROUND_SERVICE_DATA_SYNC requirement.
 */
class DownloadService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_DOWNLOAD = "com.videapp.mobile.action.START_DOWNLOAD"
        const val ACTION_STOP_DOWNLOAD = "com.videapp.mobile.action.STOP_DOWNLOAD"
        const val EXTRA_URL = "extra_download_url"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: ""
                startForegroundNotification("Descargando video...", url)
            }
            ACTION_STOP_DOWNLOAD -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, VideAppApplication.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}


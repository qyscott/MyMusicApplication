package com.example.mymusicapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mymusicapplication.music.PlaybackController
import com.example.mymusicapplication.music.PlaybackUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PlaybackService : Service() {
    private val controller: PlaybackController by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            controller.uiState.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> serviceScope.launch { controller.togglePlayPause() }
            ACTION_PREVIOUS -> serviceScope.launch { controller.playPrevious() }
            ACTION_NEXT -> serviceScope.launch { controller.playNext() }
            ACTION_STOP -> serviceScope.launch {
                controller.stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(state: PlaybackUiState) {
        val currentTrack = state.currentTrack ?: run {
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            return
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(currentTrack.title)
            .setContentText(currentTrack.artist)
            .setContentIntent(openAppPendingIntent())
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(0, "上一曲", playbackPendingIntent(ACTION_PREVIOUS))
            .addAction(0, if (state.isPlaying) "暂停" else "播放", playbackPendingIntent(ACTION_TOGGLE_PLAY))
            .addAction(0, "下一曲", playbackPendingIntent(ACTION_NEXT))
            .addAction(0, "停止", playbackPendingIntent(ACTION_STOP))
            .build()

        if (!isForeground) {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun playbackPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_TOGGLE_PLAY = "com.example.mymusicapplication.action.TOGGLE_PLAY"
        private const val ACTION_PREVIOUS = "com.example.mymusicapplication.action.PREVIOUS"
        private const val ACTION_NEXT = "com.example.mymusicapplication.action.NEXT"
        private const val ACTION_STOP = "com.example.mymusicapplication.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}


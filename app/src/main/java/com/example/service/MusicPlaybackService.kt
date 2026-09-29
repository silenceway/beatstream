package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.Track
import com.example.player.PlayerController

class MusicPlaybackService : Service() {

    private val binder = MusicBinder()
    private var playerController: PlayerController? = null

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.service.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.service.NEXT"
        const val ACTION_PREV = "com.example.service.PREV"
        const val ACTION_STOP = "com.example.service.STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "beatstream_playback_channel"

        fun start(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun attachPlayer(controller: PlayerController) {
        this.playerController = controller
        controller.onTrackChangedListener = { track ->
            updateNotification(track, controller.state.value.isPlaying)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playerController?.togglePlayPause()
            ACTION_NEXT -> playerController?.skipNext()
            ACTION_PREV -> playerController?.skipPrevious()
            ACTION_STOP -> {
                playerController?.pause()
                stopForeground(true)
                stopSelf()
            }
        }

        val track = playerController?.state?.value?.currentTrack
        if (track != null) {
            val isPlaying = playerController?.state?.value?.isPlaying ?: false
            val notification = buildNotification(track, isPlaying)
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    fun updateNotification(track: Track, isPlaying: Boolean) {
        val notification = buildNotification(track, isPlaying)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(track: Track, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, flags)

        val prevIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREV }
        val pendingPrev = PendingIntent.getService(this, 1, prevIntent, flags)

        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val pendingPlayPause = PendingIntent.getService(this, 2, playPauseIntent, flags)

        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }
        val pendingNext = PendingIntent.getService(this, 3, nextIntent, flags)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album.ifBlank { "YouTube Music" })
            .setContentIntent(pendingOpenApp)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", pendingPrev)
            .addAction(playPauseIcon, playPauseTitle, pendingPlayPause)
            .addAction(android.R.drawable.ic_media_next, "Next", pendingNext)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for background YouTube Music playback"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController = null
    }
}

package com.riderai.riderai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationCompat

class RiderForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        startForegroundInternal()
    }

    private fun startForegroundInternal() {
        val channelId = "rider_assistant_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Rider Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rider Assistant Active")
            .setContentText("Voice commands & music control running...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY_SPOTIFY" -> handleSpotifyCommand()
            "PLAY_PAUSE" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT_TRACK" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREVIOUS_TRACK" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        return START_STICKY
    }

    private fun handleSpotifyCommand() {
        // 🔥 BACKGROUND SPOTIFY LAUNCH + PLAY
        trySpotifyLaunchInBackground()
    }

    private fun trySpotifyLaunchInBackground() {
        val spotifyMethods = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("spotify://")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("spotify:app:home")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("spotify:search")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        spotifyMethods.forEachIndexed { index, intent ->
            handler.postDelayed({
                try {
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    // Continue to next method
                }
            }, (index * 500L))
        }

        // Media keys after app launch attempts
        handler.postDelayed({
            playSpotifyMediaKeys()
        }, 3000)
    }

    private fun playSpotifyMediaKeys() {
        val playSequence = listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT
        )

        playSequence.forEachIndexed { index, keyCode ->
            handler.postDelayed({
                sendMediaKey(keyCode)
            }, (index * 700L))
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            handler.postDelayed({
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }, 50)
        } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

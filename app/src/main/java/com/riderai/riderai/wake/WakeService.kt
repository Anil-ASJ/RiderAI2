package com.riderai.riderai.wake

import ai.picovoice.porcupine.PorcupineManager
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.riderai.riderai.MainActivity
import com.riderai.riderai.R
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

class WakeService : Service() {

    private lateinit var porcupineManager: PorcupineManager
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var bluetoothScoStarted = false

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AudioManager::class.java)

        startForegroundNotification()
        configureAudioForWakeWord()

        porcupineManager = PorcupineManager.Builder()
            .setAccessKey("SDQzQBUf1KP8K9RkBZDDezAkdAYfM6F40M1ELDepdf1s8/xKR2QFrg==")
            .setKeywordPath("hey-rider.ppn")
            .setSensitivity(0.7f)
            .build(applicationContext) {
                Log.d("WAKE", "🔥 HEY RIDER DETECTED")
                wakeApp()
            }

        porcupineManager.start()
    }

    private fun configureAudioForWakeWord() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (audioManager.isBluetoothScoAvailableOffCall) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            bluetoothScoStarted = true
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
        } else {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun wakeApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    private fun startForegroundNotification() {
        val channelId = "wake_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Wake Word Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Rider AI")
            .setContentText("Listening for Hey Rider")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        porcupineManager.stop()
        porcupineManager.delete()

        if (bluetoothScoStarted) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            bluetoothScoStarted = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

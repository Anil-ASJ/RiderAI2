package com.riderai.riderai.wake

import ai.picovoice.porcupine.PorcupineManager
import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.media.session.MediaSession
import com.riderai.riderai.MainActivity
import com.riderai.riderai.R

class WakeService : Service() {

    private lateinit var porcupineManager: PorcupineManager
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSession

    private var audioFocusRequest: AudioFocusRequest? = null
    private var bluetoothScoStarted = false
    private var mediaButtonDownTime: Long? = null

    private val longPressThresholdMs = 3000L

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AudioManager::class.java)

        startForegroundNotification()
        configureAudioForWakeWord()
        setupMediaSession()

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

    // ================= AUDIO =================

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

        audioFocusRequest =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    // ================= MEDIA SESSION =================

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "RiderAIMediaSession").apply {

            setCallback(object : MediaSession.Callback() {

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent =
                        mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                            ?: return false

                    val keyCode = keyEvent.keyCode
                    val isHeadsetButton =
                        keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE

                    if (!isHeadsetButton) return false

                    return when (keyEvent.action) {
                        KeyEvent.ACTION_DOWN -> {
                            if (mediaButtonDownTime == null) {
                                mediaButtonDownTime = keyEvent.downTime
                            }
                            true
                        }

                        KeyEvent.ACTION_UP -> {
                            val downTime = mediaButtonDownTime ?: keyEvent.downTime
                            val duration = keyEvent.eventTime - downTime
                            mediaButtonDownTime = null

                            if (duration >= longPressThresholdMs) {
                                triggerEarbudLongPress()
                            }
                            true
                        }

                        else -> false
                    }
                }
            })

            setMediaButtonReceiver(null)
            isActive = true
        }
    }

    // ================= ACTIONS =================

    private fun wakeApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_WAKE_LISTEN
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    private fun triggerEarbudLongPress() {
        wakeApp()
    }

    // ================= NOTIFICATION =================

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

    // ================= LIFECYCLE =================

    override fun onDestroy() {
        porcupineManager.stop()
        porcupineManager.delete()

        if (bluetoothScoStarted) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            bluetoothScoStarted = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }

        audioManager.mode = AudioManager.MODE_NORMAL
        mediaSession.release()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

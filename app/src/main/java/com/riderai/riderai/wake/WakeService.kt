package com.riderai.riderai.wake

import ai.picovoice.porcupine.PorcupineManager
import android.app.*
import android.content.Intent
import android.media.*
import android.media.session.MediaSession
import android.os.Build
import android.view.KeyEvent
import android.os.Bundle
import android.os.IBinder
import android.speech.*
import android.util.Log
import com.riderai.riderai.R
import com.riderai.riderai.voice.VoiceCommandProcessor
import com.riderai.riderai.MainActivity


class WakeService : Service() {

    private lateinit var porcupineManager: PorcupineManager
    private lateinit var audioManager: AudioManager
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor

    private var audioFocusRequest: AudioFocusRequest? = null
    private var bluetoothScoStarted = false

    private lateinit var mediaSession: MediaSession
    private var mediaButtonDownTime: Long? = null

    private val longPressThresholdMs = 3000L
    private var wakeWordActive = false
    private var isListeningForCommand = false

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AudioManager::class.java)

        startForegroundNotification()
        configureAudioForWakeWord()
        setupMediaSession()


        voiceCommandProcessor = VoiceCommandProcessor(this) { message ->
            Log.d("VOICE", message)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle) {
                    Log.d("VOICE", "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("VOICE", "Speech beginning")
                }

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    Log.d("VOICE", "Speech ended")
                    finishCommandListening()
                }

                override fun onError(error: Int) {
                    Log.w("VOICE", "Speech error: $error")
                    finishCommandListening()
                }

                override fun onResults(results: Bundle) {
                    val spokenText = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.lowercase()
                        ?: ""

                    if (spokenText.isNotBlank()) {
                        voiceCommandProcessor.process(spokenText)
                    } else {
                        Log.d("VOICE", "No speech recognized")
                    }
                }

                override fun onPartialResults(partialResults: Bundle) = Unit

                override fun onEvent(eventType: Int, params: Bundle) = Unit
            })
        }

        porcupineManager = PorcupineManager.Builder()
            .setAccessKey("SDQzQBUf1KP8K9RkBZDDezAkdAYfM6F40M1ELDepdf1s8/xKR2QFrg==")
            .setKeywordPath("hey-rider.ppn")
            .setSensitivity(0.7f)
            .build(applicationContext) {
                Log.d("WAKE", "🔥 HEY RIDER DETECTED")
                startCommandListening()
            }

        porcupineManager.start()
        wakeWordActive = true
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

    private fun startCommandListening() {
        if (isListeningForCommand) return

        isListeningForCommand = true
        pauseWakeWord()
        playBeep()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        speechRecognizer.startListening(intent)
    }

    private fun finishCommandListening() {
        if (!isListeningForCommand) return

        isListeningForCommand = false
        speechRecognizer.stopListening()
        resumeWakeWord()
    }

    private fun pauseWakeWord() {
        if (!wakeWordActive) return

        porcupineManager.stop()
        wakeWordActive = false
    }

    private fun resumeWakeWord() {
        if (wakeWordActive) return

        porcupineManager.start()
        wakeWordActive = true
    }

    private fun playBeep() {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            .startTone(ToneGenerator.TONE_PROP_BEEP, 150)
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
        speechRecognizer.destroy()
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

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "RiderAIMediaSession").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = mediaButtonIntent
                        ?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return false

                    val keyCode = keyEvent.keyCode
                    val isHeadsetButton = keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE

                    if (!isHeadsetButton) {
                        return false
                    }

                    when (keyEvent.action) {
                        KeyEvent.ACTION_DOWN -> {
                            if (mediaButtonDownTime == null) {
                                mediaButtonDownTime = keyEvent.downTime
                            }
                            return true
                        }
                        KeyEvent.ACTION_UP -> {
                            val downTime = mediaButtonDownTime ?: keyEvent.downTime
                            val duration = keyEvent.eventTime - downTime
                            mediaButtonDownTime = null

                            if (duration >= longPressThresholdMs) {
                                triggerEarbudLongPress()
                                return true
                            }
                            return false
                        }
                        else -> return false
                    }
                }
            })
            isActive = true
        }
    }

    private fun triggerEarbudLongPress() {
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
}

package com.riderai.riderai

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.riderai.riderai.wake.WakeService
import com.riderai.riderai.voice.VoiceCommandProcessor

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_WAKE_LISTEN = "com.riderai.riderai.ACTION_WAKE_LISTEN"
    }
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isBluetoothConnected = false
    private var lastCalledNumber: String? = null

    private val VOICE_REQUEST_CODE = 200

    // UI
    private lateinit var statusBluetooth: TextView
    private lateinit var statusMic: TextView
    private lateinit var statusCall: TextView
    private lateinit var voiceStatus: TextView

    private lateinit var voiceCommandProcessor: VoiceCommandProcessor

    private var wakeServiceStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI refs
        statusBluetooth = findViewById(R.id.statusBluetooth)
        statusMic = findViewById(R.id.statusMic)
        statusCall = findViewById(R.id.statusCall)
        voiceStatus = findViewById(R.id.voiceStatus)

        voiceCommandProcessor = VoiceCommandProcessor(this) { message ->
            voiceStatus.text = message
        }

        // Mic button
        findViewById<Button>(R.id.btnMic).setOnClickListener {
            startVoiceRecognition()
        }

        requestPermissionsSafely()
        startBluetoothListener()
        handleWakeIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleWakeIntent(intent)
    }


    // ================= PERMISSIONS =================

    private fun requestPermissionsSafely() {

        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
            return
        }
        startWakeServiceIfPermitted()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != 100) return

        startWakeServiceIfPermitted()
    }

    private fun startWakeServiceIfPermitted() {
        if (wakeServiceStarted) return

        val hasRecordAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasRecordAudio) return

        wakeServiceStarted = true
        startWakeService()
    }

    // ================= WAKE SERVICE =================

    private fun startWakeService() {
        val intent = Intent(this, WakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // ================= BLUETOOTH =================

    private fun startBluetoothListener() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        bluetoothAdapter?.getProfileProxy(
            this,
            object : BluetoothProfile.ServiceListener {

                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.HEADSET) {
                        isBluetoothConnected = true
                        statusBluetooth.text = "🔵 Bluetooth Connected"
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HEADSET) {
                        isBluetoothConnected = false
                        statusBluetooth.text = "⚪ Bluetooth Disconnected"
                    }
                }
            },
            BluetoothProfile.HEADSET
        )
    }

    // ================= VOICE =================

    private fun startVoiceRecognition(playTone: Boolean = true) {

        if (!isBluetoothConnected) {

            statusMic.text = "🎤 Mic On"
            voiceStatus.text = "Using device mic (Bluetooth not connected)"
        }

        statusMic.text = "🎤 Listening…"
        voiceStatus.text = "Listening for commands…"

        if (playTone) {
            playBeep()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }

        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE)
        } catch (e: Exception) {
            statusMic.text = "🎤 Error"
            voiceStatus.text = "Voice recognition not available"
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != VOICE_REQUEST_CODE) return

        statusMic.text = "🎤 Idle"

        val spokenText = data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.lowercase()
            ?: return

        voiceCommandProcessor.process(spokenText)
    }

    private fun playBeep() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            .startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.action == ACTION_WAKE_LISTEN) {
            voiceStatus.text = "👋 Hey Rider detected. Listening..."
            playBeep()
            startVoiceRecognition()
        }
    }
}

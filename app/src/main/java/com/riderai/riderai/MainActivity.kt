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

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isBluetoothConnected = false
    private var lastCalledNumber: String? = null

    private val VOICE_REQUEST_CODE = 200

    // UI
    private lateinit var statusBluetooth: TextView
    private lateinit var statusMic: TextView
    private lateinit var statusCall: TextView
    private lateinit var voiceStatus: TextView

    private var wakeServiceStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI refs
        statusBluetooth = findViewById(R.id.statusBluetooth)
        statusMic = findViewById(R.id.statusMic)
        statusCall = findViewById(R.id.statusCall)
        voiceStatus = findViewById(R.id.voiceStatus)

        // Mic button
        findViewById<Button>(R.id.btnMic).setOnClickListener {
            startVoiceRecognition()
        }

        requestPermissionsSafely()
        startBluetoothListener()
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

    private fun startVoiceRecognition() {

        if (!isBluetoothConnected) {
            statusMic.text = "🎤 Mic Off"
            voiceStatus.text = "Bluetooth not connected"
            return
        }

        statusMic.text = "🎤 Listening…"
        voiceStatus.text = "Listening for commands…"

        playBeep()

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

        voiceStatus.text = "Heard: \"$spokenText\""

        when {
            spokenText == "call again" -> callAgain()
            spokenText.startsWith("call") -> handleCallCommand(spokenText)
            spokenText.contains("play spotify") -> openSpotifyAndPlay()
            spokenText.contains("pause") -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            spokenText.contains("next") -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            spokenText.contains("previous") -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            else -> voiceStatus.text = "❓ Unknown: \"$spokenText\""
        }
    }

    // ================= CALLING =================

    private fun handleCallCommand(command: String) {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val name = command.replace("call", "").trim()
        if (name.isEmpty()) return

        val number = getPhoneNumberByName(name) ?: return
        lastCalledNumber = number

        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    private fun callAgain() {
        lastCalledNumber?.let {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$it")))
        }
    }

    private fun getPhoneNumberByName(name: String): String? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    // ================= MEDIA =================

    private fun sendMediaKey(keyCode: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun playBeep() {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            .startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    private fun openSpotifyAndPlay() {
        val intent = Intent(this, RiderForegroundService::class.java)
        intent.action = "PLAY_SPOTIFY"
        startService(intent)
        voiceStatus.text = "🎵 Spotify started..."
    }
}

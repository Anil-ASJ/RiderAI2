package com.riderai.riderai.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.riderai.riderai.RiderForegroundService

class VoiceCommandProcessor(
    private val context: Context,
    private val statusListener: ((String) -> Unit)? = null
) {

    private var lastCalledNumber: String? = null

    fun process(spokenText: String) {
        val normalized = spokenText.trim().lowercase()
        if (normalized.isEmpty()) {
            statusListener?.invoke("❓ No command detected")
            return
        }

        statusListener?.invoke("Heard: \"$normalized\"")

        when {
            normalized == "call again" -> callAgain()
            normalized.startsWith("call") -> handleCallCommand(normalized)
            normalized.contains("play spotify") -> openSpotifyAndPlay()
            normalized.contains("pause") -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            normalized.contains("next") -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            normalized.contains("previous") -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            else -> statusListener?.invoke("❓ Unknown: \"$normalized\"")
        }
    }

    private fun handleCallCommand(command: String) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            statusListener?.invoke("📵 Call permission missing")
            return
        }

        val name = command.replace("call", "").trim()
        if (name.isEmpty()) {
            statusListener?.invoke("❓ Who should I call?")
            return
        }

        val number = getPhoneNumberByName(name)
        if (number.isNullOrBlank()) {
            statusListener?.invoke("❓ Contact not found: \"$name\"")
            return
        }

        lastCalledNumber = number
        context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        statusListener?.invoke("📞 Calling $name")
    }

    private fun callAgain() {
        val number = lastCalledNumber
        if (number.isNullOrBlank()) {
            statusListener?.invoke("❓ No previous call to repeat")
            return
        }
        context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        statusListener?.invoke("📞 Calling again")
    }

    private fun getPhoneNumberByName(name: String): String? {
        val cursor = context.contentResolver.query(
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

    private fun sendMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        statusListener?.invoke("🎵 Media command sent")
    }

    private fun openSpotifyAndPlay() {
        val intent = Intent(context, RiderForegroundService::class.java)
        intent.action = "PLAY_SPOTIFY"
        context.startService(intent)
        statusListener?.invoke("🎵 Spotify started...")
    }
}

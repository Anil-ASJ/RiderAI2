package com.riderai.riderai.wake

import ai.picovoice.porcupine.PorcupineManager
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.riderai.riderai.MainActivity
import com.riderai.riderai.R

class WakeService : Service() {

    private lateinit var porcupineManager: PorcupineManager

    override fun onCreate() {
        super.onCreate()

        startForegroundNotification()

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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

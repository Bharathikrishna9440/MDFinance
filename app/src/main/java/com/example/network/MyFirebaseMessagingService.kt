package com.example.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New Firebase Messaging token generated: $token")
        saveTokenToPreferences(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        // Log the message to Firebase Analytics
        FirebaseAnalyticsManager.logEvent("fcm_message_received")

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            val title = it.title ?: "MD Finance Notification"
            val body = it.body ?: ""
            showLocalNotification(title, body)
        } ?: run {
            // Check if message contains data payload
            if (remoteMessage.data.isNotEmpty()) {
                val title = remoteMessage.data["title"] ?: "MD Finance Alert"
                val body = remoteMessage.data["body"] ?: ""
                showLocalNotification(title, body)
            }
        }
    }

    private fun showLocalNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create Notification Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cloud Messages & Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows live push messages and reminders synced from Firebase Cloud"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Find the default application launcher icon dynamically
        val iconResId = applicationInfo.icon

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconResId)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun saveTokenToPreferences(token: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    companion object {
        private const val TAG = "MyFirebaseMessaging"
        private const val CHANNEL_ID = "fcm_cloud_alerts_channel"
        private const val PREFS_NAME = "weekly_finance_prefs"
        private const val KEY_FCM_TOKEN = "firebase_fcm_token"

        fun getSavedFcmToken(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_FCM_TOKEN, "") ?: ""
        }
    }
}

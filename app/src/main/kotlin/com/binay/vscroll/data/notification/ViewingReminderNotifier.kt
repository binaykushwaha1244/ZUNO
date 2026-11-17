package com.binay.vscroll.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.binay.vscroll.MainActivity
import com.binay.vscroll.R

object ViewingReminderNotifier {

    const val CHANNEL_ID = "zuno_viewing_reminders_v2"
    const val LEGACY_CHANNEL_ID = "zuno_viewing_reminders"
    const val NOTIFICATION_ID = 4001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Clean up legacy channel to avoid stale vibration-only configuration
            try {
                notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            } catch (_: Exception) {}

            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                val name = "Viewing Reminders"
                val descriptionText = "Gentle alerts when it's time to take a break while watching videos."
                val importance = NotificationManager.IMPORTANCE_HIGH

                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()

                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 200, 350)
                    setSound(soundUri, audioAttributes)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun formatDurationForMessage(intervalMinutes: Int): String {
        val h = intervalMinutes / 60
        val m = intervalMinutes % 60
        return when {
            h > 0 && m > 0 -> "$h hour${if (h == 1) "" else "s"} $m minute${if (m == 1) "" else "s"}"
            h > 0 -> "$h hour${if (h == 1) "" else "s"}"
            else -> "$m minute${if (m == 1) "" else "s"}"
        }
    }

    fun sendBreakReminderNotification(context: Context, intervalMinutes: Int) {
        createNotificationChannel(context)

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTime = formatDurationForMessage(intervalMinutes)
        val title = "Time for a little break"
        val body = "You've been watching for $formattedTime. Consider taking a short break."

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationPattern = longArrayOf(0, 350, 200, 350)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setVibrate(vibrationPattern)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission was revoked or denied; silently ignore without crashing
        } catch (e: Exception) {
            // Safe fallback for platform exceptions
        }
    }
}

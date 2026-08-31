package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_ID_ATTENDANCE = "dtu_attendance_alerts"
    const val CHANNEL_ID_REMINDERS = "dtu_class_reminders"
    const val CHANNEL_ID_ALARMS = "dtu_first_class_alarms"
    const val CHANNEL_ID_CLASS_END = "dtu_class_end_updates"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Attendance Alerts Channel
            val attendanceChannel = NotificationChannel(
                CHANNEL_ID_ATTENDANCE,
                "Attendance & Exam Eligibility Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical 75% attendance threshold warnings, level changes, and bunk advise."
                enableVibration(true)
            }

            // 2. Class Reminders Channel
            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Lecture & Lab Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "30-minute upcoming class notifications with classroom numbers."
                enableVibration(true)
            }

            // 3. Alarms Channel
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARMS,
                "First Class Wake-Up Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wake-up alarms scheduled hours before your first class of the day."
                enableVibration(true)
                val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(defaultSoundUri, null)
            }

            // 4. Class End Updates Channel
            val classEndChannel = NotificationChannel(
                CHANNEL_ID_CLASS_END,
                "Post-Class Attendance Prompts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timely prompts right after a class finishes to log attendance."
                enableVibration(true)
            }

            manager.createNotificationChannel(attendanceChannel)
            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(alarmChannel)
            manager.createNotificationChannel(classEndChannel)
        }
    }

    fun showClassEndNotification(
        context: Context,
        subjectId: Long,
        subjectName: String,
        room: String,
        slotTime: String,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        createNotificationChannels(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "ATTENDANCE")
            putExtra("SUBJECT_ID", subjectId)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Action: Present
        val presentIntent = Intent(context, com.example.data.receiver.AlarmReceiver::class.java).apply {
            action = "ACTION_MARK_PRESENT_NOTIFICATION"
            putExtra("subjectId", subjectId)
            putExtra("subjectName", subjectName)
            putExtra("slotTime", slotTime)
            putExtra("notificationId", notificationId)
        }
        val presentPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 100000,
            presentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Action: Absent
        val absentIntent = Intent(context, com.example.data.receiver.AlarmReceiver::class.java).apply {
            action = "ACTION_MARK_ABSENT_NOTIFICATION"
            putExtra("subjectId", subjectId)
            putExtra("subjectName", subjectName)
            putExtra("slotTime", slotTime)
            putExtra("notificationId", notificationId)
        }
        val absentPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 200000,
            absentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = "🔔 Class Ended: $subjectName"
        val message = "$slotTime at $room just ended. Did you attend today's session?"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CLASS_END)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "✅ Present", presentPendingIntent)
            .addAction(android.R.drawable.ic_delete, "❌ Absent", absentPendingIntent)
            .setVibrate(longArrayOf(0, 300, 150, 300))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            // ignore
        }
    }

    fun showSystemNotification(
        context: Context,
        title: String,
        message: String,
        type: String = "REMINDER", // DANGER, ALARM, REMINDER, LEVEL_DROP
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        createNotificationChannels(context)

        val channelId = when (type) {
            "ALARM" -> CHANNEL_ID_ALARMS
            "DANGER", "LEVEL_DROP" -> CHANNEL_ID_ATTENDANCE
            else -> CHANNEL_ID_REMINDERS
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val soundUri = if (type == "ALARM") {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        val iconRes = android.R.drawable.ic_dialog_info

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Safely catch SecurityException on Android 13+
        } catch (e: Throwable) {
            // Catch other possible exceptions
        }
    }
}

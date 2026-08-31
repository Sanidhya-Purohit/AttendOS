package com.example.data.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.Toast
import com.example.data.local.AppDatabase
import com.example.data.repository.AttendanceRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == "ACTION_MARK_PRESENT_NOTIFICATION" || action == "ACTION_MARK_ABSENT_NOTIFICATION") {
            val subjectId = intent.getLongExtra("subjectId", -1L)
            val subjectName = intent.getStringExtra("subjectName") ?: "Class"
            val slotTime = intent.getStringExtra("slotTime") ?: ""
            val notificationId = intent.getIntExtra("notificationId", 0)

            // Dismiss the notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationId != 0) {
                notificationManager?.cancel(notificationId)
            }

            if (subjectId != -1L) {
                val db = AppDatabase.getDatabase(context)
                val repo = AttendanceRepository(db)
                val status = if (action == "ACTION_MARK_PRESENT_NOTIFICATION") "PRESENT" else "ABSENT"
                
                CoroutineScope(Dispatchers.IO).launch {
                    repo.markAttendance(
                        subjectId = subjectId,
                        status = status,
                        slotTime = slotTime,
                        note = "Marked via post-class prompt"
                    )
                }

                val msg = if (status == "PRESENT") "Marked $subjectName as Present! ✅" else "Marked $subjectName as Absent! ❌"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (action == "ACTION_CLASS_ENDED_PROMPT") {
            val subjectId = intent.getLongExtra("subjectId", -1L)
            val subjectName = intent.getStringExtra("subjectName") ?: "Class"
            val room = intent.getStringExtra("room") ?: "Campus"
            val slotTime = intent.getStringExtra("slotTime") ?: ""
            val notifId = intent.getIntExtra("notificationId", (System.currentTimeMillis() % 100000).toInt())

            NotificationHelper.showClassEndNotification(
                context = context,
                subjectId = subjectId,
                subjectName = subjectName,
                room = room,
                slotTime = slotTime,
                notificationId = notifId
            )
            return
        }

        // Standard First-Class Wake Up Alarm
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "DTUAttend:FirstClassAlarmWakeLock"
        )
        wakeLock?.acquire(30000L) // 30s wakelock

        val className = intent.getStringExtra("className") ?: "First Class"
        val classTime = intent.getStringExtra("classTime") ?: "Upcoming"
        val room = intent.getStringExtra("room") ?: "Campus"
        val leadHours = intent.getDoubleExtra("leadHours", 2.0)

        val title = "⏰ DTU Wake-Up Alarm: First Class in ${formatLeadTime(leadHours)}!"
        val message = "📚 $className is scheduled for $classTime at $room. Time to get ready and head out!"

        NotificationHelper.showSystemNotification(
            context = context,
            title = title,
            message = message,
            type = "ALARM",
            notificationId = 8888
        )

        try {
            wakeLock?.release()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun formatLeadTime(leadHours: Double): String {
        return if (leadHours == leadHours.toInt().toDouble()) {
            "${leadHours.toInt()}h"
        } else {
            "${leadHours}h"
        }
    }
}


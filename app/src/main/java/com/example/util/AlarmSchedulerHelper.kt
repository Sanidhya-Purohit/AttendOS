package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.widget.Toast
import com.example.data.local.dao.SlotWithSubject
import com.example.data.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmSchedulerHelper {

    fun scheduleFirstClassAlarm(
        context: Context,
        slots: List<SlotWithSubject>,
        alarmEnabled: Boolean,
        leadHours: Double,
        alarmMode: String, // "RELATIVE" or "DAY_SPECIFIC"
        daySpecificTimes: Map<Int, String> // 1 to 6 -> "07:00 AM"
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        if (!alarmEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        // Find today's earliest class
        val calendar = Calendar.getInstance()
        var currentDay = calendar.get(Calendar.DAY_OF_WEEK) - 1
        if (currentDay == 0) currentDay = 7 // Sunday

        if (currentDay > 6) {
            // Sunday - schedule for Monday (day 1)
            currentDay = 1
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val todaySlots = slots.filter { it.slot.dayOfWeek == currentDay }
            .sortedBy { parseTimeToMinutes(it.slot.startTime) }

        if (todaySlots.isEmpty()) {
            return
        }

        val firstSlot = todaySlots.first()
        val firstClassMinutes = parseTimeToMinutes(firstSlot.slot.startTime)

        val targetHour: Int
        val targetMinute: Int

        if (alarmMode == "DAY_SPECIFIC") {
            val customTime = daySpecificTimes[currentDay] ?: "07:00 AM"
            val parsedCustom = parseTimeToMinutes(customTime)
            targetHour = parsedCustom / 60
            targetMinute = parsedCustom % 60
        } else {
            // Relative: e.g. 2 hours before first class
            val leadMinutes = (leadHours * 60).toInt()
            val alarmMinutes = (firstClassMinutes - leadMinutes).coerceAtLeast(0)
            targetHour = alarmMinutes / 60
            targetMinute = alarmMinutes % 60
        }

        val alarmTimeCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        intent.putExtra("className", firstSlot.subject?.name ?: "First Lecture")
        intent.putExtra("classTime", firstSlot.slot.startTime)
        intent.putExtra("room", firstSlot.slot.location)
        intent.putExtra("leadHours", leadHours)

        val updatedPendingIntent = PendingIntent.getBroadcast(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTimeCal.timeInMillis,
                        updatedPendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        alarmTimeCal.timeInMillis,
                        updatedPendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeCal.timeInMillis,
                    updatedPendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeCal.timeInMillis,
                    updatedPendingIntent
                )
            }
        } catch (e: Throwable) {
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeCal.timeInMillis,
                    updatedPendingIntent
                )
            } catch (ignored: Throwable) {
                // Safely ignore if system restricts alarm
            }
        }
    }

    fun schedulePostClassAttendancePrompts(
        context: Context,
        slots: List<SlotWithSubject>
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val calendar = Calendar.getInstance()
        var currentDay = calendar.get(Calendar.DAY_OF_WEEK) - 1
        if (currentDay == 0) currentDay = 7

        val todaySlots = slots.filter { it.slot.dayOfWeek == currentDay }
        val nowCal = Calendar.getInstance()

        todaySlots.forEachIndexed { idx, slotWithSub ->
            val endTimeStr = slotWithSub.slot.endTime
            val endMinutes = parseTimeToMinutes(endTimeStr)
            val endHour = endMinutes / 60
            val endMin = endMinutes % 60

            val slotCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, endHour)
                set(Calendar.MINUTE, endMin)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If time is in the future today
            if (slotCal.after(nowCal)) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = "ACTION_CLASS_ENDED_PROMPT"
                    putExtra("subjectId", slotWithSub.subject?.id ?: 0L)
                    putExtra("subjectName", slotWithSub.subject?.name ?: "Class")
                    putExtra("room", slotWithSub.slot.location)
                    putExtra("slotTime", "${slotWithSub.slot.startTime} - ${slotWithSub.slot.endTime}")
                    putExtra("notificationId", 50000 + idx)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    50000 + idx,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, slotCal.timeInMillis, pendingIntent)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, slotCal.timeInMillis, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, slotCal.timeInMillis, pendingIntent)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun setSystemClockAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        message: String = "DTU First Class Wake-Up"
    ) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
            Toast.makeText(context, "Alarm synced with Phone Clock App for %02d:%02d".format(hour, minute), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Alarm set in Draco at %02d:%02d".format(hour, minute), Toast.LENGTH_SHORT).show()
        }
    }

    fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val format = SimpleDateFormat("hh:mm a", Locale.US)
            val date = format.parse(timeStr.trim()) ?: return 540 // Default 9:00 AM (9*60)
            val cal = Calendar.getInstance().apply { time = date }
            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        } catch (e: Exception) {
            9 * 60 // 9:00 AM
        }
    }
}

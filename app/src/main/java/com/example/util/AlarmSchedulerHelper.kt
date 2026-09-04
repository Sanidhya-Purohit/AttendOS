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
            // Use setAlarmClock: Highest priority alarm on Android, displays alarm icon in status bar, wakes Doze mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val showIntent = Intent(context, com.example.MainActivity::class.java)
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    9998,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )
                val clockInfo = AlarmManager.AlarmClockInfo(alarmTimeCal.timeInMillis, showPendingIntent)
                alarmManager.setAlarmClock(clockInfo, updatedPendingIntent)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        message: String = "DTU First Class Wake-Up",
        days: ArrayList<Int>? = null
    ) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (days != null && days.isNotEmpty()) {
                putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, days)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
            Toast.makeText(context, "✅ Phone Alarm Set for %02d:%02d".format(hour, minute), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // If skip UI fails or security exception, open clock with UI
            try {
                val fallbackIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ignored: Exception) {
                Toast.makeText(context, "System alarm configured at %02d:%02d".format(hour, minute), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun syncFullWeekAlarmsWithPhoneClock(
        context: Context,
        slots: List<SlotWithSubject>,
        leadHours: Double
    ) {
        if (slots.isEmpty()) {
            Toast.makeText(context, "No classes in timetable to schedule alarms for.", Toast.LENGTH_SHORT).show()
            return
        }

        // Calendar.MONDAY = 2, ..., Calendar.SATURDAY = 7
        val dayMapping = mapOf(
            1 to Calendar.MONDAY,
            2 to Calendar.TUESDAY,
            3 to Calendar.WEDNESDAY,
            4 to Calendar.THURSDAY,
            5 to Calendar.FRIDAY,
            6 to Calendar.SATURDAY
        )

        // Find earliest class for each day
        var syncedCount = 0
        for (dayIndex in 1..6) {
            val daySlots = slots.filter { it.slot.dayOfWeek == dayIndex }
                .sortedBy { parseTimeToMinutes(it.slot.startTime) }

            if (daySlots.isNotEmpty()) {
                val firstClass = daySlots.first()
                val startMins = parseTimeToMinutes(firstClass.slot.startTime)
                val leadMins = (leadHours * 60).toInt()
                val alarmMins = (startMins - leadMins).coerceAtLeast(0)
                val hour = alarmMins / 60
                val minute = alarmMins % 60
                val calDay = dayMapping[dayIndex] ?: Calendar.MONDAY

                setSystemClockAlarm(
                    context = context,
                    hour = hour,
                    minute = minute,
                    message = "AttendOS: ${firstClass.subject.name} (${firstClass.slot.startTime})",
                    days = arrayListOf(calDay)
                )
                syncedCount++
            }
        }

        Toast.makeText(context, "⚡ Successfully synced $syncedCount native phone alarms with Phone Clock app!", Toast.LENGTH_LONG).show()
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

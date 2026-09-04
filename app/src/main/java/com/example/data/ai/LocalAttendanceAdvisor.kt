package com.example.data.ai

import com.example.data.local.dao.SlotWithSubject
import com.example.data.local.entity.AcademicBreakEntity
import com.example.data.local.entity.AttendanceRecordEntity
import com.example.data.local.entity.SemesterEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.domain.SubjectStats
import com.example.ui.viewmodel.LeaveDayScore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

object LocalAttendanceAdvisor {

    fun generateAdvice(
        query: String,
        stats: List<SubjectStats>,
        slots: List<SlotWithSubject>,
        profile: UserProfileEntity?,
        breaks: List<AcademicBreakEntity>,
        records: List<AttendanceRecordEntity>,
        currentSemester: SemesterEntity?,
        leaveScores: List<LeaveDayScore>
    ): String {
        val lowerQuery = query.lowercase().trim()
        val targetThreshold = profile?.globalThreshold ?: 75.0
        val emergencyBuffer = if (profile?.includeBufferInCalculations == true) (profile.globalEmergencyBuffer) else 0

        val calendar = Calendar.getInstance()
        val todayDayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7 // Sunday
        }
        val tomorrowDayOfWeek = if (todayDayOfWeek == 7) 1 else todayDayOfWeek + 1
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val todayName = dayNames[todayDayOfWeek - 1]
        val tomorrowName = dayNames[tomorrowDayOfWeek - 1]

        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        // 1. SPECIFIC SUBJECT QUERY CHECK
        // Match against subject name or code
        val matchedStat = stats.firstOrNull { stat ->
            val code = stat.subject.code.lowercase().replace("-", "").trim()
            val queryCode = lowerQuery.replace("-", "")
            val name = stat.subject.name.lowercase().trim()

            queryCode.contains(code) ||
            lowerQuery.contains(name) ||
            name.split(" ").any { word -> word.length > 3 && lowerQuery.contains(word) }
        }

        // Check if query is specifically inquiring about skipping/attendance for a subject
        val isSubjectQuery = lowerQuery.contains("skip") ||
                lowerQuery.contains("bunk") ||
                lowerQuery.contains("attendance in") ||
                lowerQuery.contains("can i miss") ||
                lowerQuery.contains("status of") ||
                lowerQuery.contains("percentage in")

        if (matchedStat != null) {
            return generateSubjectSpecificAdvice(
                stat = matchedStat,
                slots = slots,
                targetThreshold = targetThreshold,
                emergencyBuffer = emergencyBuffer,
                query = lowerQuery,
                todayDayOfWeek = todayDayOfWeek,
                tomorrowDayOfWeek = tomorrowDayOfWeek,
                todayName = todayName,
                tomorrowName = tomorrowName
            )
        } else if (isSubjectQuery && !lowerQuery.contains("today") && !lowerQuery.contains("tomorrow") && !lowerQuery.contains("week")) {
            // User asked about a specific course that is NOT registered in the app
            return buildString {
                append("⚠️ **Subject Not Found in App Records**\n\n")
                append("I checked your registered timetable and course list, but that subject is **not recorded** in AttendOS.\n\n")
                append("**Your currently registered subjects in AttendOS:**\n")
                if (stats.isEmpty()) {
                    append("• *(No subjects currently set up. Please add subjects or scan your timetable in the Setup tab.)*\n")
                } else {
                    stats.forEach { s ->
                        append("• **${s.subject.name}** (`${s.subject.code}`) — ${String.format(Locale.US, "%.1f", s.percentage)}% (${s.subject.attendedClasses}/${s.subject.conductedClasses} attended)\n")
                    }
                }
                append("\n💡 **Action:** If you are enrolled in this subject, please add it in the **Subjects** tab so I can track your attendance and calculate exact bunk capacity accurately without guessing!")
            }
        }

        // 2. "TODAY" SCHEDULE & BUNK QUERIES
        if (lowerQuery.contains("today") || lowerQuery.contains("classes today") || lowerQuery.contains("schedule today")) {
            return generateTodayAdvice(
                todayDayOfWeek = todayDayOfWeek,
                todayName = todayName,
                todayDateStr = todayDateStr,
                slots = slots,
                stats = stats,
                breaks = breaks,
                targetThreshold = targetThreshold
            )
        }

        // 3. "TOMORROW" SCHEDULE & BUNK QUERIES
        if (lowerQuery.contains("tomorrow") || lowerQuery.contains("classes tomorrow") || lowerQuery.contains("schedule tomorrow")) {
            return generateTomorrowAdvice(
                tomorrowDayOfWeek = tomorrowDayOfWeek,
                tomorrowName = tomorrowName,
                slots = slots,
                stats = stats,
                breaks = breaks,
                targetThreshold = targetThreshold
            )
        }

        // 4. "WEEK" / TIMETABLE SUMMARY QUERIES
        if (lowerQuery.contains("week") || lowerQuery.contains("timetable") || lowerQuery.contains("schedule")) {
            return generateWeekSummary(
                slots = slots,
                stats = stats,
                leaveScores = leaveScores,
                dayNames = dayNames
            )
        }

        // 5. "LEAVE" / "OPTIMAL DAY TO BUNK" QUERIES
        if (lowerQuery.contains("leave") || lowerQuery.contains("optimal day") || lowerQuery.contains("best day") || lowerQuery.contains("safest day")) {
            return generateLeaveRecommendation(
                leaveScores = leaveScores,
                slots = slots,
                stats = stats
            )
        }

        // 6. EXAM / CUTOFF / RECOVERY TARGETS
        if (lowerQuery.contains("midsem") || lowerQuery.contains("endsem") || lowerQuery.contains("exam") || lowerQuery.contains("cutoff") || lowerQuery.contains("recover") || lowerQuery.contains("target")) {
            return generateExamRecoveryRoadmap(
                stats = stats,
                profile = profile,
                targetThreshold = targetThreshold
            )
        }

        // 7. LAB / PRACTICAL IMPACT QUERIES
        if (lowerQuery.contains("lab") || lowerQuery.contains("practical")) {
            return generateLabImpactAdvice(
                slots = slots,
                stats = stats
            )
        }

        // 8. GENERAL INTELLIGENCE OVERVIEW (GROUNDED IN REAL DATA)
        return generateGeneralOverview(
            stats = stats,
            slots = slots,
            profile = profile,
            todayDayOfWeek = todayDayOfWeek,
            todayName = todayName
        )
    }

    private fun generateSubjectSpecificAdvice(
        stat: SubjectStats,
        slots: List<SlotWithSubject>,
        targetThreshold: Double,
        emergencyBuffer: Int,
        query: String,
        todayDayOfWeek: Int,
        tomorrowDayOfWeek: Int,
        todayName: String,
        tomorrowName: String
    ): String {
        val s = stat.subject
        val att = s.attendedClasses
        val cond = s.conductedClasses
        val pct = stat.percentage

        val simConducted = cond + 1
        val simPct = if (simConducted > 0) (att.toDouble() / simConducted.toDouble()) * 100.0 else 100.0

        val subjectSlots = slots.filter { it.subject.id == s.id }
        val scheduledToday = subjectSlots.filter { it.slot.dayOfWeek == todayDayOfWeek }
        val scheduledTomorrow = subjectSlots.filter { it.slot.dayOfWeek == tomorrowDayOfWeek }

        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        return buildString {
            append("📊 **Attendance Analysis: ${s.name} (`${s.code}`)**\n\n")
            append("• **Current Standing:** **${String.format(Locale.US, "%.1f", pct)}%** ($att attended / $cond conducted)\n")
            append("• **Target Threshold:** ${targetThreshold.toInt()}%\n")
            append("• **Status Tier:** ${stat.tier.levelName}\n")

            if (pct >= targetThreshold) {
                append("• **Bunk Capacity:** You can safely miss **${stat.rawBunkCapacity} class(es)** without dropping below ${targetThreshold.toInt()}%.\n")
                if (emergencyBuffer > 0) {
                    append("  *(With emergency buffer enabled: ${stat.safeBunkWithBuffer} classes)*\n")
                }
                append("• **If you miss 1 class:** Your attendance drops from **${String.format(Locale.US, "%.1f", pct)}%** ➔ **${String.format(Locale.US, "%.1f", simPct)}%**.\n\n")

                if (simPct >= targetThreshold) {
                    append("✅ **VERDICT: SAFE TO SKIP.** Missing 1 class keeps you at ${String.format(Locale.US, "%.1f", simPct)}%, above the ${targetThreshold.toInt()}% requirement.")
                } else {
                    append("⚠️ **VERDICT: CAUTION.** Skipping even 1 class will drop your attendance to ${String.format(Locale.US, "%.1f", simPct)}%, putting you below the ${targetThreshold.toInt()}% cutoff!")
                }
            } else {
                append("• **Shortage Deficit:** Currently **${String.format(Locale.US, "%.1f", targetThreshold - pct)}% below target**!\n")
                append("• **Recovery Trajectory:** Must attend **${stat.classesToReachTarget} consecutive classes** (0 bunks) to restore attendance to ${targetThreshold.toInt()}%.\n")
                append("• **If you miss another class:** Drops further from **${String.format(Locale.US, "%.1f", pct)}%** ➔ **${String.format(Locale.US, "%.1f", simPct)}%**.\n\n")
                append("🚨 **VERDICT: DO NOT SKIP.** You are in the shortage zone. Attending every lecture is critical for exam eligibility.")
            }

            // Timetable occurrence check
            append("\n\n📅 **Schedule Check:**\n")
            if (scheduledTomorrow.isNotEmpty()) {
                val times = scheduledTomorrow.joinToString(", ") { "${it.slot.startTime} - ${it.slot.endTime} (${it.slot.location})" }
                append("• **Tomorrow ($tomorrowName):** Scheduled at $times.\n")
            } else if (scheduledToday.isNotEmpty()) {
                val times = scheduledToday.joinToString(", ") { "${it.slot.startTime} - ${it.slot.endTime} (${it.slot.location})" }
                append("• **Today ($todayName):** Scheduled at $times.\n")
            } else {
                val days = subjectSlots.map { dayNames.getOrElse(it.slot.dayOfWeek - 1) { "Day" } }.distinct()
                append("• **Weekly Occurrences:** Scheduled on: ${if (days.isNotEmpty()) days.joinToString(", ") else "No recurring slots set"}.\n")
            }
        }
    }

    private fun generateTodayAdvice(
        todayDayOfWeek: Int,
        todayName: String,
        todayDateStr: String,
        slots: List<SlotWithSubject>,
        stats: List<SubjectStats>,
        breaks: List<AcademicBreakEntity>,
        targetThreshold: Double
    ): String {
        val activeBreak = breaks.firstOrNull { b -> todayDateStr >= b.startDate && todayDateStr <= b.endDate }
        if (activeBreak != null) {
            return "🎉 **Campus Break / Holiday Today ($todayName)**\n\n" +
                    "Today is **${activeBreak.title}** (${activeBreak.startDate} to ${activeBreak.endDate}).\n" +
                    "• **Description:** ${activeBreak.description}\n" +
                    "• **All classes are officially suspended!** Enjoy your break without any attendance penalty."
        }

        if (todayDayOfWeek == 7) {
            return "🌴 **No Classes Today ($todayName)**\n\nToday is Sunday. There are no academic timetable slots scheduled."
        }

        val todaySlots = slots.filter { it.slot.dayOfWeek == todayDayOfWeek }.sortedBy { it.slot.startTime }
        if (todaySlots.isEmpty()) {
            return "📅 **Schedule for Today ($todayName):**\n\nYou have no timetable slots scheduled for today!"
        }

        return buildString {
            append("📅 **Today's Classes ($todayName):**\n\n")
            var hasDangerClass = false

            todaySlots.forEach { slot ->
                val subStat = stats.find { it.subject.id == slot.subject.id }
                val pctStr = if (subStat != null) "${String.format(Locale.US, "%.1f", subStat.percentage)}%" else "N/A"
                val isSafeToBunk = (subStat?.rawBunkCapacity ?: 0) > 0

                val badge = if (isSafeToBunk) "🟢 Safe" else "🔴 Critical"
                if (!isSafeToBunk) hasDangerClass = true

                append("• **${slot.slot.startTime} - ${slot.slot.endTime}**: ${slot.subject.name} (`${slot.subject.code}`) in ${slot.slot.location} — Current: **$pctStr** [$badge]\n")
            }

            append("\n🎯 **Daily Bunk Verdict:**\n")
            if (hasDangerClass) {
                append("⚠️ **High Attendance Risk Today!** One or more classes today are already below or right at the ${targetThreshold.toInt()}% threshold. Bunking today will trigger detention warnings in shortage subjects.")
            } else {
                append("✅ **Low Attendance Risk Today!** All scheduled subjects currently have safe bunk buffers above ${targetThreshold.toInt()}%. You can safely take leave if needed.")
            }
        }
    }

    private fun generateTomorrowAdvice(
        tomorrowDayOfWeek: Int,
        tomorrowName: String,
        slots: List<SlotWithSubject>,
        stats: List<SubjectStats>,
        breaks: List<AcademicBreakEntity>,
        targetThreshold: Double
    ): String {
        if (tomorrowDayOfWeek == 7) {
            return "🌴 **No Classes Tomorrow ($tomorrowName)**\n\nTomorrow is Sunday. No academic classes scheduled."
        }

        val tomorrowSlots = slots.filter { it.slot.dayOfWeek == tomorrowDayOfWeek }.sortedBy { it.slot.startTime }
        if (tomorrowSlots.isEmpty()) {
            return "📅 **Schedule for Tomorrow ($tomorrowName):**\n\nYou have no classes scheduled for tomorrow in your timetable."
        }

        return buildString {
            append("📅 **Tomorrow's Schedule ($tomorrowName):**\n\n")
            var canBunkAll = true

            tomorrowSlots.forEach { slot ->
                val subStat = stats.find { it.subject.id == slot.subject.id }
                val pct = subStat?.percentage ?: 100.0
                val safeBunks = subStat?.rawBunkCapacity ?: 0
                val isSafe = safeBunks > 0
                if (!isSafe) canBunkAll = false

                append("• **${slot.slot.startTime} - ${slot.slot.endTime}**: ${slot.subject.name} (`${slot.subject.code}`) at ${slot.slot.location}\n")
                append("  Current: **${String.format(Locale.US, "%.1f", pct)}%** | Safe Bunks Left: **$safeBunks**\n")
            }

            append("\n💡 **Bunk Recommendation for Tomorrow:**\n")
            if (canBunkAll) {
                append("✅ **Safe to skip classes tomorrow.** All scheduled subjects have available bunk margins above ${targetThreshold.toInt()}%.")
            } else {
                append("⚠️ **Attend Caution:** Some subjects tomorrow have 0 safe bunks. Missing tomorrow will push attendance into the shortage danger zone.")
            }
        }
    }

    private fun generateWeekSummary(
        slots: List<SlotWithSubject>,
        stats: List<SubjectStats>,
        leaveScores: List<LeaveDayScore>,
        dayNames: List<String>
    ): String {
        return buildString {
            append("🗓️ **Weekly Timetable & Attendance Summary**\n\n")
            for (day in 1..6) {
                val daySlots = slots.filter { it.slot.dayOfWeek == day }.sortedBy { it.slot.startTime }
                val dayName = dayNames[day - 1]
                val scoreObj = leaveScores.find { it.dayOfWeek == day }
                val score = scoreObj?.safetyScore ?: 50

                append("**$dayName** (Leave Safety Score: ${score}/100):\n")
                if (daySlots.isEmpty()) {
                    append("  • *No scheduled classes (Free Day)*\n")
                } else {
                    daySlots.forEach { s ->
                        append("  • ${s.slot.startTime} - ${s.slot.endTime}: ${s.subject.name} [${s.slot.slotType}] in ${s.slot.location}\n")
                    }
                }
                append("\n")
            }

            val bestDay = leaveScores.maxByOrNull { it.safetyScore }
            if (bestDay != null) {
                append("🏆 **Safest Day for Leave:** **${bestDay.dayName}** (Score: ${bestDay.safetyScore}/100 — least academic impact).\n")
            }
        }
    }

    private fun generateLeaveRecommendation(
        leaveScores: List<LeaveDayScore>,
        slots: List<SlotWithSubject>,
        stats: List<SubjectStats>
    ): String {
        if (leaveScores.isEmpty()) {
            return "Please configure your timetable slots to enable automated leave day scoring."
        }

        val sorted = leaveScores.sortedByDescending { it.safetyScore }
        val best = sorted.first()
        val worst = sorted.last()

        return buildString {
            append("🎯 **Optimal Leave Day Recommendation**\n\n")
            append("🟢 **Best Day to Take Leave:** **${best.dayName}** (Safety Score: **${best.safetyScore}/100**)\n")
            append("• ${best.recommendation}\n")
            append("• Scheduled classes on ${best.dayName}: ${best.totalSlots} classes.\n\n")

            append("🔴 **Worst Day to Bunk:** **${worst.dayName}** (Safety Score: **${worst.safetyScore}/100**)\n")
            append("• ${worst.recommendation}\n")
            append("• Contains heavy practical labs or subjects with critical attendance shortage.\n\n")

            append("💡 **Ranking of All Days (Safest to Riskiest):**\n")
            sorted.forEachIndexed { index, s ->
                val emoji = if (s.safetyScore >= 75) "🟢" else if (s.safetyScore >= 50) "🟡" else "🔴"
                append("${index + 1}. $emoji **${s.dayName}**: Score ${s.safetyScore}/100 (${s.totalSlots} classes)\n")
            }
        }
    }

    private fun generateExamRecoveryRoadmap(
        stats: List<SubjectStats>,
        profile: UserProfileEntity?,
        targetThreshold: Double
    ): String {
        val midsem = profile?.midsemDate ?: "Midsem"
        val endsem = profile?.endsemDate ?: "Endsem"
        val deficitStats = stats.filter { it.percentage < targetThreshold }
        val safeStats = stats.filter { it.percentage >= targetThreshold }

        return buildString {
            append("🎯 **Exam Clearance & Attendance Recovery Roadmap**\n\n")
            append("• **Target Threshold:** ${targetThreshold.toInt()}% (Mandatory for Midsem [$midsem] & Endsem [$endsem])\n\n")

            if (deficitStats.isEmpty()) {
                append("🎉 **Outstanding! All subjects are currently >= ${targetThreshold.toInt()}%.**\n\n")
                append("**Available Bunk Buffers:**\n")
                safeStats.forEach { s ->
                    append("• **${s.subject.name}**: ${String.format(Locale.US, "%.1f", s.percentage)}% — Can safely miss **${s.rawBunkCapacity} classes**.\n")
                }
            } else {
                append("🚨 **Subjects Requiring Immediate Recovery (<${targetThreshold.toInt()}%):**\n")
                deficitStats.forEach { s ->
                    append("• **${s.subject.name}** (`${s.subject.code}`): **${String.format(Locale.US, "%.1f", s.percentage)}%** (${s.subject.attendedClasses}/${s.subject.conductedClasses})\n")
                    append("  ➔ **Must attend ${s.classesToReachTarget} consecutive classes** without any bunks to reach ${targetThreshold.toInt()}%.\n")
                }

                if (safeStats.isNotEmpty()) {
                    append("\n✅ **Safe Subjects (>=${targetThreshold.toInt()}%):**\n")
                    safeStats.forEach { s ->
                        append("• **${s.subject.name}**: ${String.format(Locale.US, "%.1f", s.percentage)}% (Buffer: ${s.rawBunkCapacity} bunks)\n")
                    }
                }
            }
        }
    }

    private fun generateLabImpactAdvice(
        slots: List<SlotWithSubject>,
        stats: List<SubjectStats>
    ): String {
        val labs = slots.filter { it.slot.slotType.equals("Lab", ignoreCase = true) }

        return buildString {
            append("🔬 **Practical Lab Attendance Penalty Impact**\n\n")
            append("• **Higher Weightage:** In university evaluation, 2-hour practical lab slots carry double credit impact or mandatory lab performance evaluation.\n")
            append("• Missing a single 2-hour lab session has twice the percentage penalty of a 1-hour regular lecture.\n\n")

            if (labs.isNotEmpty()) {
                append("**Your Scheduled Practical Labs in AttendOS:**\n")
                val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                labs.forEach { l ->
                    val dayName = dayNames.getOrElse(l.slot.dayOfWeek - 1) { "Day" }
                    val subStat = stats.find { it.subject.id == l.subject.id }
                    val pctStr = if (subStat != null) "${String.format(Locale.US, "%.1f", subStat.percentage)}%" else "N/A"
                    append("• **${l.subject.name} Lab** on **$dayName** (${l.slot.startTime} - ${l.slot.endTime}) at ${l.slot.location} — Current: **$pctStr**\n")
                }
                append("\n💡 **Advice:** Prioritize lab attendance above all else to avoid lab manual suspension or grade debarment.")
            } else {
                append("• No dedicated lab slots are currently registered in your timetable.")
            }
        }
    }

    private fun generateGeneralOverview(
        stats: List<SubjectStats>,
        slots: List<SlotWithSubject>,
        profile: UserProfileEntity?,
        todayDayOfWeek: Int,
        todayName: String
    ): String {
        val totalAttended = stats.sumOf { it.subject.attendedClasses }
        val totalConducted = stats.sumOf { it.subject.conductedClasses }
        val overallPct = if (totalConducted > 0) (totalAttended.toDouble() / totalConducted.toDouble()) * 100.0 else 100.0
        val target = profile?.globalThreshold ?: 75.0

        val deficitCount = stats.count { it.percentage < target }
        val todaySlots = slots.filter { it.slot.dayOfWeek == todayDayOfWeek }

        return buildString {
            append("✨ **AttendOS Real-Time Academic Standing**\n\n")
            append("• **Student:** ${profile?.studentName ?: "Student"} (${profile?.branch ?: "Engineering"})\n")
            append("• **Overall Attendance:** **${String.format(Locale.US, "%.1f", overallPct)}%** ($totalAttended / $totalConducted attended)\n")
            append("• **Target Threshold:** ${target.toInt()}%\n")
            append("• **Subjects at Risk:** ${if (deficitCount > 0) "🚨 $deficitCount subject(s) below ${target.toInt()}%" else "✅ 0 subjects in shortage"}\n")
            append("• **Today ($todayName):** ${todaySlots.size} classes scheduled.\n\n")

            append("**Your Registered Subjects:**\n")
            stats.forEach { s ->
                val badge = if (s.percentage >= target) "🟢 Safe (${s.rawBunkCapacity} bunks)" else "🔴 Shortage (Need ${s.classesToReachTarget})"
                append("• **${s.subject.name}** (`${s.subject.code}`): **${String.format(Locale.US, "%.1f", s.percentage)}%** — $badge\n")
            }

            append("\n💡 **Ask me anything about your data:**\n")
            append("• *'Can I skip [Subject Name] tomorrow?'*\n")
            append("• *'Can I bunk classes today?'*\n")
            append("• *'Summarize my week'* or *'Which day is best for leave?'*\n")
            append("• *'How many classes to reach 75%?'*")
        }
    }
}

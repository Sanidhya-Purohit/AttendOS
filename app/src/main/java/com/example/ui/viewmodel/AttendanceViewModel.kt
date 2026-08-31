package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ChatMessage
import com.example.data.ai.GeminiService
import com.example.data.ai.ParsedSubjectSlot
import com.example.data.local.AppDatabase
import com.example.data.local.dao.SlotWithSubject
import com.example.data.local.entity.AcademicBreakEntity
import com.example.data.local.entity.AttendanceRecordEntity
import com.example.data.local.entity.ScheduleSlotEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.AttendanceRepository
import com.example.domain.AttendanceCalculator
import com.example.domain.CalculationTimeframe
import com.example.domain.SubjectStats
import com.example.util.AlarmSchedulerHelper
import com.example.util.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LeaveDayScore(
    val dayOfWeek: Int,
    val dayName: String,
    val totalSlots: Int,
    val hasLab: Boolean,
    val safetyScore: Int, // 0 - 100 (Higher = safer to take off)
    val recommendation: String,
    val criticalSubjectsCount: Int
)

data class ExamProjection(
    val examName: String,
    val targetDate: String,
    val weeksRemaining: Int,
    val projectedSubjectOutcomes: List<ProjectedSubjectOutcome>
)

data class ProjectedSubjectOutcome(
    val subjectName: String,
    val currentPercentage: Double,
    val remainingClassesEstimate: Int,
    val ifAttendAllPercentage: Double,
    val ifAttend75Percentage: Double,
    val isRecoverableBeforeExam: Boolean
)

data class InAppNotification(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val type: String, // "DANGER", "REMINDER_30M", "LEVEL_DROP", "STREAK"
    val timestamp: Long = System.currentTimeMillis()
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AttendanceRepository(AppDatabase.getDatabase(application))
    private val geminiService = GeminiService()

    val allSubjects: StateFlow<List<SubjectEntity>> = repository.allSubjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSlotsWithSubjects: StateFlow<List<SlotWithSubject>> = repository.allSlotsWithSubjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentRecords: StateFlow<List<AttendanceRecordEntity>> = repository.recentRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttendanceRecords: StateFlow<List<AttendanceRecordEntity>> = repository.recentRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBreaks: StateFlow<List<AcademicBreakEntity>> = repository.allBreaks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBreakToday: StateFlow<AcademicBreakEntity?> = allBreaks.map { breaks ->
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        breaks.firstOrNull { it.startDate <= today && today <= it.endDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val attendanceRecordsByDate: StateFlow<Map<String, List<AttendanceRecordEntity>>> = allAttendanceRecords
        .map { list -> list.groupBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Selected Day in Timetable / Dashboard (1 = Mon .. 6 = Sat)
    private val _selectedDay = MutableStateFlow(getTodayDayOfWeek())
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    // Calculated Subject Stats with Milestone Awareness (Next Exam vs Full Semester)
    val subjectStatsList: StateFlow<List<SubjectStats>> = combine(
        allSubjects,
        userProfile,
        allSlotsWithSubjects
    ) { subjects, profile, slots ->
        try {
            val globalTarget = profile?.globalThreshold ?: 75.0
            val globalBuffer = profile?.globalEmergencyBuffer ?: 3
            val includeBuffer = profile?.includeBufferInCalculations ?: true
            val calculationMode = CalculationTimeframe.fromId(profile?.calculationMode)
            val leadDays = profile?.examCutoffLeadDays ?: 2

            val targetExamDate = if (calculationMode == CalculationTimeframe.FULL_SEMESTER) {
                profile?.endsemDate ?: "2026-12-08"
            } else {
                profile?.midsemDate ?: "2026-10-15"
            }

            subjects.map { subject ->
                val weeklyFreq = slots.count { it.subject.id == subject.id }.coerceAtLeast(1)
                AttendanceCalculator.calculateStats(
                    subject = subject,
                    globalTarget = globalTarget,
                    globalBuffer = globalBuffer,
                    includeBuffer = includeBuffer,
                    timeframe = calculationMode,
                    examDateStr = targetExamDate,
                    leadDays = leadDays,
                    weeklyClassCount = weeklyFreq
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Overall Average Attendance
    val overallAttendance: StateFlow<Double> = allSubjects.map { subjects ->
        try {
            val totalConducted = subjects.sumOf { it.conductedClasses }
            val totalAttended = subjects.sumOf { it.attendedClasses }
            if (totalConducted == 0) 100.0 else (totalAttended.toDouble() / totalConducted.toDouble()) * 100.0
        } catch (e: Throwable) {
            100.0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100.0)

    // AI Chat Messages
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "ai",
                text = "✨ Hey there! I'm **AttendOS AI**, your intelligent attendance strategist.\n\nAsk me anything about your schedule, bunk margins, or exam criteria:\n• *'Can I safely bunk DBMS today?'*\n• *'Which day is optimal for a leave this week?'*\n• *'Calculate my Midsem 75% cutoff targets.'*"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    val isChatLoading = MutableStateFlow(false)

    // Timetable Scanner State
    val isScanningTimetable = MutableStateFlow(false)
    val parsedSlotsReview = MutableStateFlow<List<ParsedSubjectSlot>?>(null)

    // Notifications List
    private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    init {
        viewModelScope.launch {
            repository.checkAndInitializeDefaultDTUData()
            generateMockClassRemindersAndAlerts()
        }
    }

    fun setSelectedDay(day: Int) {
        _selectedDay.value = day
    }

    fun markAttendance(subjectId: Long, status: String, slotTime: String = "", note: String = "") {
        viewModelScope.launch {
            repository.markAttendance(subjectId, status, slotTime, note)
            checkLevelDropAlert(subjectId, status)
        }
    }

    fun markAttendanceForDate(
        subjectId: Long,
        status: String,
        date: String,
        slotTime: String = "",
        note: String = ""
    ) {
        viewModelScope.launch {
            repository.markAttendanceForDate(subjectId, status, date, slotTime, note)
            checkLevelDropAlert(subjectId, status)
        }
    }

    fun setSessionStartDate(startDate: String) {
        viewModelScope.launch {
            repository.setSessionStartDate(startDate)
        }
    }

    fun undoRecord(record: AttendanceRecordEntity) {
        viewModelScope.launch {
            repository.undoLastRecord(record)
        }
    }

    fun addOrUpdateSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            if (subject.id == 0L) {
                repository.insertSubject(subject)
            } else {
                repository.updateSubject(subject)
            }
            refreshAlarms()
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
            refreshAlarms()
        }
    }

    fun addOrUpdateSlot(slot: ScheduleSlotEntity) {
        viewModelScope.launch {
            if (slot.id == 0L) {
                repository.insertSlot(slot)
            } else {
                repository.updateSlot(slot)
            }
            refreshAlarms()
        }
    }

    fun deleteSlot(slot: ScheduleSlotEntity) {
        viewModelScope.launch {
            repository.deleteSlot(slot)
            refreshAlarms()
        }
    }

    fun updateProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            repository.updateProfile(profile)
            refreshAlarms()
        }
    }

    fun addAcademicBreak(academicBreak: AcademicBreakEntity) {
        viewModelScope.launch {
            repository.addAcademicBreak(academicBreak)
        }
    }

    fun deleteAcademicBreak(academicBreak: AcademicBreakEntity) {
        viewModelScope.launch {
            repository.deleteAcademicBreak(academicBreak)
        }
    }

    fun deleteAcademicBreakById(id: Long) {
        viewModelScope.launch {
            repository.deleteAcademicBreakById(id)
        }
    }

    fun completeOnboardingSetup(
        subjects: List<SubjectEntity>,
        slotAssignments: List<Pair<Int, ScheduleSlotEntity>>,
        profile: UserProfileEntity
    ) {
        completeOnboardingSetupWithHistory(
            subjects = subjects,
            slotAssignments = slotAssignments,
            records = emptyList(),
            profile = profile
        )
    }

    fun completeOnboardingSetupWithHistory(
        subjects: List<SubjectEntity>,
        slotAssignments: List<Pair<Int, ScheduleSlotEntity>>,
        records: List<Pair<Int, AttendanceRecordEntity>>,
        profile: UserProfileEntity
    ) {
        viewModelScope.launch {
            repository.completeSetupWithCustomScheduleAndHistory(subjects, slotAssignments, records, profile)
            refreshAlarms()
            // Send initial confirmation system notification
            NotificationHelper.showSystemNotification(
                context = getApplication<Application>(),
                title = "🎉 AttendOS Schedule & Attendance Configured!",
                message = "Your weekly timetable, session dates, and attendance tracking are active. Post-class prompts & alarms are enabled.",
                type = "REMINDER"
            )
        }
    }

    fun updateSchedulePreservingAttendance(
        subjects: List<SubjectEntity>,
        slotAssignments: List<Pair<Int, ScheduleSlotEntity>>
    ) {
        viewModelScope.launch {
            repository.updateSchedulePreservingAttendance(subjects, slotAssignments)
            refreshAlarms()
            NotificationHelper.showSystemNotification(
                context = getApplication<Application>(),
                title = "🗓️ Timetable Updated",
                message = "Your course schedule was updated while preserving all recorded attendance progress.",
                type = "REMINDER"
            )
        }
    }

    fun bulkUpdateAttendance(updatedSubjects: List<SubjectEntity>) {
        viewModelScope.launch {
            repository.bulkUpdateSubjects(updatedSubjects)
        }
    }

    fun resetAppToSetup() {
        viewModelScope.launch {
            repository.resetSetup()
        }
    }

    fun replaceActiveSchedule(
        subjects: List<SubjectEntity>,
        slotAssignments: List<Pair<Int, ScheduleSlotEntity>>
    ) {
        viewModelScope.launch {
            repository.replaceSchedule(subjects, slotAssignments)
            refreshAlarms()
            NotificationHelper.showSystemNotification(
                context = getApplication<Application>(),
                title = "🗓️ Timetable Updated Successfully",
                message = "New courses and class schedule have been synced with alarms and calculations.",
                type = "REMINDER"
            )
        }
    }

    fun quickStartWithDTUDefault(freshZeroAttendance: Boolean = false) {
        viewModelScope.launch {
            repository.loadDTUDefaultSubjectsAndSchedule(freshZeroAttendance)
            val current = userProfile.value ?: UserProfileEntity()
            repository.updateProfile(current.copy(isSetupComplete = true))
            refreshAlarms()
        }
    }

    fun updateAlarmSettings(
        enabled: Boolean,
        leadHours: Double,
        mode: String,
        mon: String = "07:00 AM",
        tue: String = "07:00 AM",
        wed: String = "07:00 AM",
        thu: String = "07:00 AM",
        fri: String = "08:00 AM",
        sat: String = "08:00 AM"
    ) {
        val current = userProfile.value ?: return
        val updated = current.copy(
            alarmEnabled = enabled,
            alarmLeadHours = leadHours,
            alarmMode = mode,
            monAlarmTime = mon,
            tueAlarmTime = tue,
            wedAlarmTime = wed,
            thuAlarmTime = thu,
            friAlarmTime = fri,
            satAlarmTime = sat
        )
        viewModelScope.launch {
            repository.updateProfile(updated)
            refreshAlarms(updated)
        }
    }

    fun triggerTestPhoneNotification() {
        val notif = InAppNotification(
            title = "🚨 DTU Attendance Warning (Phone Push)",
            message = "DBMS is currently at 70.0% (<75% Exam Threshold). Emergency buffer activated for Pragya Bhavan / AB-4 classes!",
            type = "DANGER"
        )
        _notifications.value = listOf(notif) + _notifications.value

        NotificationHelper.showSystemNotification(
            context = getApplication<Application>(),
            title = notif.title,
            message = notif.message,
            type = "DANGER"
        )
    }

    fun triggerTestAlarmNow() {
        val profile = userProfile.value
        val lead = profile?.alarmLeadHours ?: 2.0
        val leadStr = if (lead == lead.toInt().toDouble()) "${lead.toInt()} Hours" else "$lead Hours"

        NotificationHelper.showSystemNotification(
            context = getApplication<Application>(),
            title = "⏰ DTU Wake-Up Alarm: First Class in $leadStr!",
            message = "📚 Software Engineering lecture is scheduled at Pragya Bhavan PB-02. Time to wake up and get ready!",
            type = "ALARM"
        )
    }

    fun syncWithSystemClock(hour: Int, minute: Int, message: String = "DTU First Class Alarm") {
        AlarmSchedulerHelper.setSystemClockAlarm(getApplication<Application>(), hour, minute, message)
    }

    private fun refreshAlarms(customProfile: UserProfileEntity? = null) {
        val profile = customProfile ?: userProfile.value ?: return
        val slots = allSlotsWithSubjects.value
        val dayTimes = mapOf(
            1 to profile.monAlarmTime,
            2 to profile.tueAlarmTime,
            3 to profile.wedAlarmTime,
            4 to profile.thuAlarmTime,
            5 to profile.friAlarmTime,
            6 to profile.satAlarmTime
        )

        AlarmSchedulerHelper.scheduleFirstClassAlarm(
            context = getApplication<Application>(),
            slots = slots,
            alarmEnabled = profile.alarmEnabled,
            leadHours = profile.alarmLeadHours,
            alarmMode = profile.alarmMode,
            daySpecificTimes = dayTimes
        )

        AlarmSchedulerHelper.schedulePostClassAttendancePrompts(
            context = getApplication<Application>(),
            slots = slots
        )
    }

    fun toggleEmergencyBuffer() {
        val current = userProfile.value ?: return
        viewModelScope.launch {
            try {
                repository.updateProfile(current.copy(includeBufferInCalculations = !current.includeBufferInCalculations))
            } catch (e: Throwable) {
                // Defensive
            }
        }
    }

    fun setEmergencyBufferCount(count: Int) {
        val current = userProfile.value ?: return
        viewModelScope.launch {
            try {
                repository.updateProfile(current.copy(globalEmergencyBuffer = count))
            } catch (e: Throwable) {
                // Defensive
            }
        }
    }

    fun setGlobalThreshold(target: Double) {
        val current = userProfile.value ?: return
        viewModelScope.launch {
            try {
                repository.updateProfile(current.copy(globalThreshold = target))
            } catch (e: Throwable) {
                // Defensive
            }
        }
    }

    fun setCalculationMode(mode: String) {
        val current = userProfile.value ?: return
        viewModelScope.launch {
            try {
                repository.updateProfile(current.copy(calculationMode = mode))
            } catch (e: Throwable) {
                // Defensive
            }
        }
    }

    fun setExamCutoffLeadDays(leadDays: Int) {
        val current = userProfile.value ?: return
        viewModelScope.launch {
            try {
                repository.updateProfile(current.copy(examCutoffLeadDays = leadDays))
            } catch (e: Throwable) {
                // Defensive
            }
        }
    }

    fun updateExamSettings(
        mode: String,
        leadDays: Int,
        nextExamName: String,
        midsemDate: String,
        endsemDate: String
    ) {
        val current = userProfile.value ?: return
        viewModelScope.launch {
            try {
                repository.updateProfile(
                    current.copy(
                        calculationMode = mode,
                        examCutoffLeadDays = leadDays,
                        nextExamName = nextExamName,
                        midsemDate = midsemDate,
                        endsemDate = endsemDate
                    )
                )
            } catch (e: Throwable) {
                // Defensive
            }
        }
    }

    fun resetToDTUDefaults() {
        viewModelScope.launch {
            repository.loadDTUDefaultSubjectsAndSchedule(freshZeroAttendance = false)
        }
    }

    fun analyzeTimetableImage(bitmap: Bitmap) {
        viewModelScope.launch {
            isScanningTimetable.value = true
            val result = geminiService.analyzeTimetableImage(bitmap)
            isScanningTimetable.value = false
            if (result.isSuccess) {
                parsedSlotsReview.value = result.getOrNull()
            }
        }
    }

    fun applyParsedSchedule(slots: List<ParsedSubjectSlot>) {
        viewModelScope.launch {
            // Group by unique subject
            val uniqueSubjects = slots.distinctBy { it.subjectName }
            val existingSubjects = allSubjects.value
            
            // Map created subject IDs
            val subjectIdMap = mutableMapOf<String, Long>()
            uniqueSubjects.forEach { parsed ->
                val match = existingSubjects.find { it.name.equals(parsed.subjectName, ignoreCase = true) }
                if (match != null) {
                    subjectIdMap[parsed.subjectName] = match.id
                } else {
                    val newId = repository.insertSubject(
                        SubjectEntity(
                            name = parsed.subjectName,
                            code = parsed.subjectCode,
                            defaultRoom = parsed.location,
                            category = parsed.category
                        )
                    )
                    subjectIdMap[parsed.subjectName] = newId
                }
            }

            // Insert slots
            slots.forEach { slot ->
                val subId = subjectIdMap[slot.subjectName] ?: 1L
                repository.insertSlot(
                    ScheduleSlotEntity(
                        subjectId = subId,
                        dayOfWeek = slot.dayOfWeek,
                        startTime = slot.startTime,
                        endTime = slot.endTime,
                        location = slot.location,
                        slotType = slot.slotType
                    )
                )
            }
            parsedSlotsReview.value = null
        }
    }

    fun dismissParsedReview() {
        parsedSlotsReview.value = null
    }

    fun sendChatMessage(query: String) {
        if (query.isBlank()) return
        val userMsg = ChatMessage(sender = "user", text = query)
        _chatMessages.value = _chatMessages.value + userMsg
        isChatLoading.value = true

        viewModelScope.launch {
            val contextSummary = buildStudentContextSummary()
            val aiResponseText = geminiService.askAttendanceAdvisor(contextSummary, query)
            _chatMessages.value = _chatMessages.value + ChatMessage(sender = "ai", text = aiResponseText)
            isChatLoading.value = false
        }
    }

    fun clearChatMessages() {
        _chatMessages.value = emptyList()
    }

    fun retryLastChatMessage() {
        val lastUserMsg = _chatMessages.value.lastOrNull { it.sender == "user" }
        if (lastUserMsg != null) {
            isChatLoading.value = true
            viewModelScope.launch {
                val contextSummary = buildStudentContextSummary()
                val aiResponseText = geminiService.askAttendanceAdvisor(contextSummary, lastUserMsg.text)
                _chatMessages.value = _chatMessages.value + ChatMessage(sender = "ai", text = aiResponseText)
                isChatLoading.value = false
            }
        }
    }


    fun dismissNotification(id: String) {
        _notifications.value = _notifications.value.filter { it.id != id }
    }

    private fun buildStudentContextSummary(): String {
        val stats = subjectStatsList.value
        val profile = userProfile.value
        val slots = allSlotsWithSubjects.value
        val attendanceRecords = allAttendanceRecords.value
        val breaks = allBreaks.value

        val leadDays = profile?.examCutoffLeadDays ?: 2
        val calcMode = CalculationTimeframe.fromId(profile?.calculationMode)
        val sb = StringBuilder()
        
        val currentTime = java.util.Calendar.getInstance()
        val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormatter = java.text.SimpleDateFormat("hh:mm a", Locale.US)
        val daysOfWeekFull = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentDayName = daysOfWeekFull[currentTime.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        
        sb.append("CURRENT REAL-TIME:\n")
        sb.append("Date: ${dateFormatter.format(currentTime.time)} ($currentDayName)\n")
        sb.append("Time: ${timeFormatter.format(currentTime.time)}\n\n")

        sb.append("Student: ${profile?.studentName ?: "DTU Student"} (${profile?.branch ?: "SE"})\n")
        sb.append("Global Threshold: ${profile?.globalThreshold ?: 75.0}%\n")
        sb.append("Emergency Leave Buffer: ${profile?.globalEmergencyBuffer ?: 3} classes (Active: ${profile?.includeBufferInCalculations})\n")
        sb.append("Current Calculation Mode: ${calcMode.title} (Cutoff is $leadDays days before exam date)\n")
        sb.append("Midsem Date: ${profile?.midsemDate ?: "2026-10-15"}, Endsem Date: ${profile?.endsemDate ?: "2026-12-08"}\n")
        sb.append("Alarm Settings: Enabled=${profile?.alarmEnabled}, Mode=${profile?.alarmMode}, LeadHours=${profile?.alarmLeadHours}\n\n")
        
        sb.append("SUBJECT WISE STANDING & MILESTONE ELIGIBILITY:\n")
        stats.forEach { s ->
            sb.append("- ${s.subject.name} (${s.subject.code}): ${String.format(Locale.US, "%.1f", s.percentage)}% [${s.subject.attendedClasses}/${s.subject.conductedClasses}]. Tier: ${s.tier.levelName}. Safe Bunk Till Cutoff (${s.cutoffDateStr}): ${s.safeBunkWithBuffer} classes. Remaining classes in window: ${s.remainingClassesInWindow}. Must attend: ${s.classesToReachTarget}. Room: ${s.subject.defaultRoom}\n")
        }

        sb.append("\nCOMPLETE WEEKLY TIMETABLE SCHEDULE:\n")
        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        for (day in 1..6) {
            val daySlots = slots.filter { it.slot.dayOfWeek == day }.sortedBy { it.slot.startTime }
            if (daySlots.isNotEmpty()) {
                sb.append("${daysOfWeek[day - 1]}:\n")
                daySlots.forEach {
                    sb.append("  - ${it.slot.startTime} to ${it.slot.endTime}: ${it.subject.name} (${it.slot.slotType}) at ${it.slot.location}\n")
                }
            }
        }

        sb.append("\nRECENT ABSENT DATES & BUNK HISTORY:\n")
        val absentRecords = attendanceRecords.filter { it.status.equals("ABSENT", ignoreCase = true) }
            .sortedByDescending { it.date }
            .take(15) // take the last 15 absences to avoid context overflow
        if (absentRecords.isEmpty()) {
            sb.append("- No recent absences recorded.\n")
        } else {
            absentRecords.forEach { rec ->
                val subjectName = stats.find { it.subject.id == rec.subjectId }?.subject?.name ?: "Unknown"
                sb.append("  - Missed ${subjectName} on ${rec.date} (${rec.slotTime})\n")
            }
        }

        sb.append("\nACADEMIC BREAKS / HOLIDAYS:\n")
        if (breaks.isEmpty()) {
            sb.append("- No holidays recorded.\n")
        } else {
            breaks.forEach { b ->
                sb.append("  - ${b.title}: from ${b.startDate} to ${b.endDate}\n")
            }
        }

        return sb.toString()
    }

    fun getLeaveDayScores(): List<LeaveDayScore> {
        val slots = allSlotsWithSubjects.value
        val stats = subjectStatsList.value
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

        return (1..6).map { day ->
            val daySlots = slots.filter { it.slot.dayOfWeek == day }
            val hasLab = daySlots.any { it.slot.slotType.equals("Lab", ignoreCase = true) }
            val criticalSubs = daySlots.count { slot ->
                val subStat = stats.find { it.subject.id == slot.subject.id }
                subStat?.tier?.isDanger == true
            }

            var score = 100
            if (hasLab) score -= 35 // Labs are difficult to compensate
            score -= (criticalSubs * 25)
            score -= (daySlots.size * 5)
            if (score < 10) score = 10

            val rec = when {
                score >= 75 -> "🟢 Best Day for Leave: Safe to bunk! Low academic risk."
                score >= 50 -> "🟡 Moderate Risk: Keep an eye on shortage subjects."
                else -> "🔴 Highly Discouraged: Contains critical labs or danger tier subjects!"
            }

            LeaveDayScore(
                dayOfWeek = day,
                dayName = dayNames.getOrElse(day - 1) { "Day $day" },
                totalSlots = daySlots.size,
                hasLab = hasLab,
                safetyScore = score,
                recommendation = rec,
                criticalSubjectsCount = criticalSubs
            )
        }
    }

    fun getExamProjections(isEndsem: Boolean): ExamProjection {
        val profile = userProfile.value
        val leadDays = profile?.examCutoffLeadDays ?: 2
        val examName = if (isEndsem) "End-Semester Examinations" else "Mid-Semester Examinations"
        val targetDate = if (isEndsem) (profile?.endsemDate ?: "2026-12-08") else (profile?.midsemDate ?: "2026-10-15")
        val slots = allSlotsWithSubjects.value
        val stats = subjectStatsList.value

        val outcomes = stats.map { stat ->
            val weeklyFreq = slots.count { it.subject.id == stat.subject.id }.coerceAtLeast(1)
            val cutoffInfo = AttendanceCalculator.calculateCutoffInfo(targetDate, leadDays, weeklyFreq)
            val remainingClasses = cutoffInfo.remainingClasses
            val totalFuture = stat.subject.conductedClasses + remainingClasses

            // If 100% attended in future
            val attendAll = if (totalFuture == 0) 100.0 else {
                ((stat.subject.attendedClasses + remainingClasses).toDouble() / totalFuture) * 100.0
            }
            // If 75% attended in future
            val attend75Count = (remainingClasses * 0.75).toInt()
            val attend75Pct = if (totalFuture == 0) 100.0 else {
                ((stat.subject.attendedClasses + attend75Count).toDouble() / totalFuture) * 100.0
            }

            ProjectedSubjectOutcome(
                subjectName = stat.subject.name,
                currentPercentage = stat.percentage,
                remainingClassesEstimate = remainingClasses,
                ifAttendAllPercentage = attendAll,
                ifAttend75Percentage = attend75Pct,
                isRecoverableBeforeExam = attendAll >= (profile?.globalThreshold ?: 75.0)
            )
        }

        val weeksEst = if (outcomes.isNotEmpty()) (outcomes.first().remainingClassesEstimate / 3).coerceAtLeast(1) else (if (isEndsem) 12 else 6)

        return ExamProjection(
            examName = examName,
            targetDate = targetDate,
            weeksRemaining = weeksEst,
            projectedSubjectOutcomes = outcomes
        )
    }

    private fun checkLevelDropAlert(subjectId: Long, status: String) {
        if (status == "ABSENT") {
            val stat = subjectStatsList.value.find { it.subject.id == subjectId }
            if (stat != null && stat.tier.isDanger) {
                val newNotif = InAppNotification(
                    title = "🚨 Attendance Level Dropped!",
                    message = "${stat.subject.name} dropped to ${String.format("%.1f", stat.percentage)}% (${stat.tier.levelName}). You need ${stat.classesToReachTarget} consecutive attendances to sit in exams!",
                    type = "DANGER"
                )
                _notifications.value = listOf(newNotif) + _notifications.value

                NotificationHelper.showSystemNotification(
                    context = getApplication<Application>(),
                    title = newNotif.title,
                    message = newNotif.message,
                    type = "DANGER"
                )
            }
        }
    }

    private fun generateMockClassRemindersAndAlerts() {
        _notifications.value = listOf(
            InAppNotification(
                title = "⏰ 30-Min Class Alert: Machine Learning",
                message = "ML Lecture starts in 30 mins at Pragya Bhavan PB-04. Current attendance is 86.3% (High Buffer).",
                type = "REMINDER_30M"
            ),
            InAppNotification(
                title = "🚨 Critical Shortage Alert: DBMS (SE-307)",
                message = "DBMS is at 70.0% (<75% Exam Threshold). 30-min reminder active for AB-4 Room 204. Attend today!",
                type = "DANGER"
            ),
            InAppNotification(
                title = "🔥 4-Day Attendance Streak Active!",
                message = "Keep attending without bunks to unlock the 'DTU Iron Student' Gold Badge.",
                type = "STREAK"
            )
        )
    }

    private fun getTodayDayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 1 // Default to Monday on Sundays
            else -> 1
        }
    }
}

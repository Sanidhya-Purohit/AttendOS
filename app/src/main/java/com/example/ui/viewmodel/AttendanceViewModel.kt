package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ChatConversation
import com.example.data.ai.ChatMessage
import com.example.data.ai.GeminiService
import com.example.data.ai.LocalAttendanceAdvisor
import com.example.data.ai.ParsedSubjectSlot
import com.example.data.local.AppDatabase
import com.example.data.local.dao.SlotWithSubject
import com.example.data.local.entity.AcademicBreakEntity
import com.example.data.local.entity.AttendanceRecordEntity
import com.example.data.local.entity.ScheduleSlotEntity
import com.example.data.local.entity.SemesterEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.DayAttendanceRecordUpdate
import com.example.domain.AttendanceCalculator
import com.example.domain.CalculationTimeframe
import com.example.domain.SubjectStats
import com.example.util.AlarmSchedulerHelper
import com.example.util.ExportBackupManager
import com.example.util.NotificationHelper
import com.example.util.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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
    val isRecoverableBeforeExam: Boolean,
    val classesToReachTarget: Int = 0,
    val safeBunkCapacity: Int = 0
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

    private val prefs = application.getSharedPreferences("attendos_prefs", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "DARK") ?: "DARK")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun toggleTheme() {
        val next = if (_themeMode.value == "LIGHT") "DARK" else "LIGHT"
        setThemeMode(next)
    }

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

    val allSemesters: StateFlow<List<SemesterEntity>> = repository.allSemesters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSemester: StateFlow<SemesterEntity?> = repository.currentSemester
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isCurrentSemesterLocked: StateFlow<Boolean> = repository.currentSemester
        .map { it?.isLocked == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    // AI Chat Conversations & Multiple Chats
    private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    val conversations: StateFlow<List<ChatConversation>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String>("")
    val activeConversationId: StateFlow<String> = _activeConversationId.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    val isChatLoading = MutableStateFlow(false)

    // Timetable Scanner State
    val isScanningTimetable = MutableStateFlow(false)
    val parsedSlotsReview = MutableStateFlow<List<ParsedSubjectSlot>?>(null)

    // Notifications List
    private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    init {
        loadConversationsFromDisk()
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
        sendMultimodalChatMessage(query, null)
    }

    fun sendMultimodalChatMessage(query: String, imageBitmap: Bitmap? = null) {
        if (query.isBlank() && imageBitmap == null) return
        val userMsg = ChatMessage(sender = "user", text = query, imageBitmap = imageBitmap)
        
        var currentActiveId = _activeConversationId.value
        if (currentActiveId.isBlank() || _conversations.value.none { it.id == currentActiveId }) {
            val newId = java.util.UUID.randomUUID().toString()
            val newConv = ChatConversation(
                id = newId,
                title = query.take(28).ifBlank { "Schedule Query" },
                messages = emptyList()
            )
            _conversations.value = listOf(newConv) + _conversations.value
            _activeConversationId.value = newId
            currentActiveId = newId
        }

        val updatedMessages = _chatMessages.value + userMsg
        _chatMessages.value = updatedMessages

        val currentConv = _conversations.value.find { it.id == currentActiveId }
        val isFirstUserMsg = currentConv?.messages?.none { it.sender == "user" } ?: true
        val autoTitle = if (isFirstUserMsg && query.isNotBlank()) {
            val clean = query.trim().replace("\n", " ")
            if (clean.length > 26) clean.take(26) + "..." else clean
        } else currentConv?.title ?: "Chat"

        _conversations.value = _conversations.value.map { conv ->
            if (conv.id == currentActiveId) {
                conv.copy(
                    title = if (conv.title == "New chat" || conv.title == "New conversation") autoTitle else conv.title,
                    messages = updatedMessages,
                    updatedAt = System.currentTimeMillis()
                )
            } else conv
        }
        saveConversationsToDisk()

        isChatLoading.value = true

        viewModelScope.launch {
            val contextSummary = buildStudentContextSummary()
            val recentMsgs = updatedMessages.dropLast(1).takeLast(8)
            val result = geminiService.askAttendanceAdvisorMultimodal(
                studentContext = contextSummary,
                userQuery = query,
                imageBitmap = imageBitmap,
                recentMessages = recentMsgs
            )
            val aiResponseText = if (result.isSuccess && result.getOrNull()?.isNotBlank() == true) {
                result.getOrNull()!!
            } else {
                LocalAttendanceAdvisor.generateAdvice(
                    query = query,
                    stats = subjectStatsList.value,
                    slots = allSlotsWithSubjects.value,
                    profile = userProfile.value,
                    breaks = allBreaks.value,
                    records = allAttendanceRecords.value,
                    currentSemester = currentSemester.value,
                    leaveScores = getLeaveDayScores()
                )
            }
            val aiMsg = ChatMessage(sender = "ai", text = aiResponseText)
            val finalMessages = _chatMessages.value + aiMsg
            _chatMessages.value = finalMessages

            _conversations.value = _conversations.value.map { conv ->
                if (conv.id == currentActiveId) {
                    conv.copy(messages = finalMessages, updatedAt = System.currentTimeMillis())
                } else conv
            }
            isChatLoading.value = false
            saveConversationsToDisk()
        }
    }

    fun createNewSemester(
        semesterNumber: Int,
        title: String,
        academicYear: String = "2026-2027",
        startDate: String,
        endDate: String,
        lockPrevious: Boolean = true,
        onComplete: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val newId = repository.createNewSemester(
                semesterNumber = semesterNumber,
                title = title,
                academicYear = academicYear,
                startDate = startDate,
                endDate = endDate,
                lockCurrent = lockPrevious
            )
            onComplete(newId)
        }
    }

    fun lockCurrentSemester(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.lockCurrentSemester()
            onComplete()
        }
    }

    fun unlockSemester(semesterId: Long) {
        viewModelScope.launch {
            repository.unlockSemester(semesterId)
        }
    }

    fun switchCurrentSemester(semesterId: Long) {
        viewModelScope.launch {
            repository.switchCurrentSemester(semesterId)
        }
    }

    fun batchUpdateDayAttendance(
        date: String,
        updates: List<DayAttendanceRecordUpdate>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.batchUpdateDayAttendance(date, updates)
            onComplete()
        }
    }

    fun markDayAsHoliday(date: String, holidayTitle: String = "College Holiday") {
        viewModelScope.launch {
            repository.markDayAsHoliday(date, holidayTitle)
        }
    }

    fun exportPdfReport(context: Context) {
        viewModelScope.launch {
            val subjects = repository.allSubjects.firstOrNull() ?: emptyList()
            val records = repository.recentRecords.firstOrNull() ?: emptyList()
            val profile = repository.userProfile.firstOrNull()
            val currentSem = repository.currentSemester.firstOrNull()
            ExportBackupManager.generateAndSharePdfReport(
                context = context,
                subjects = subjects,
                records = records,
                profile = profile,
                semester = currentSem
            )
        }
    }

    fun exportCsvSheet(context: Context) {
        viewModelScope.launch {
            val subjects = repository.allSubjects.firstOrNull() ?: emptyList()
            val records = repository.recentRecords.firstOrNull() ?: emptyList()
            ExportBackupManager.generateAndShareAttendanceCsv(
                context = context,
                subjects = subjects,
                records = records
            )
        }
    }

    fun exportFullBackup(context: Context) {
        viewModelScope.launch {
            ExportBackupManager.exportAndShareFullBackupJson(
                context = context,
                database = AppDatabase.getDatabase(context)
            )
        }
    }

    fun restoreFromBackup(
        context: Context,
        jsonString: String,
        onResult: (RestoreResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = ExportBackupManager.restoreFromBackupJson(
                context = context,
                database = AppDatabase.getDatabase(context),
                jsonContent = jsonString
            )
            onResult(result)
        }
    }

    fun startNewChat() {
        val newId = java.util.UUID.randomUUID().toString()
        val initialMsg = ChatMessage(
            sender = "ai",
            text = "Hello! I am your Attend AI academic strategist. How can I help you optimize your schedule, calculate bunk targets, or check exam clearances today?"
        )
        val newConv = ChatConversation(
            id = newId,
            title = "New conversation",
            messages = listOf(initialMsg),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _conversations.value = listOf(newConv) + _conversations.value
        _activeConversationId.value = newId
        _chatMessages.value = newConv.messages
        saveConversationsToDisk()
    }

    fun selectConversation(conversationId: String) {
        val conv = _conversations.value.find { it.id == conversationId }
        if (conv != null) {
            _activeConversationId.value = conv.id
            _chatMessages.value = conv.messages
        }
    }

    fun deleteConversation(conversationId: String) {
        val updated = _conversations.value.filter { it.id != conversationId }
        _conversations.value = updated
        if (_activeConversationId.value == conversationId) {
            if (updated.isNotEmpty()) {
                val next = updated.first()
                _activeConversationId.value = next.id
                _chatMessages.value = next.messages
            } else {
                startNewChat()
            }
        }
        saveConversationsToDisk()
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        _conversations.value = _conversations.value.map {
            if (it.id == conversationId) it.copy(title = newTitle.trim()) else it
        }
        saveConversationsToDisk()
    }

    fun toggleMessageLike(messageId: String, liked: Boolean) {
        val activeId = _activeConversationId.value
        _chatMessages.value = _chatMessages.value.map { msg ->
            if (msg.id == messageId) {
                val newLiked = if (msg.isLiked == liked) null else liked
                msg.copy(isLiked = newLiked)
            } else msg
        }
        _conversations.value = _conversations.value.map { conv ->
            if (conv.id == activeId) conv.copy(messages = _chatMessages.value) else conv
        }
        saveConversationsToDisk()
    }

    fun clearChatMessages() {
        _chatMessages.value = emptyList()
        val activeId = _activeConversationId.value
        _conversations.value = _conversations.value.map { conv ->
            if (conv.id == activeId) conv.copy(messages = emptyList()) else conv
        }
        saveConversationsToDisk()
    }

    fun retryLastChatMessage() {
        val lastUserMsg = _chatMessages.value.lastOrNull { it.sender == "user" }
        if (lastUserMsg != null) {
            val activeId = _activeConversationId.value
            isChatLoading.value = true
            viewModelScope.launch {
                val contextSummary = buildStudentContextSummary()
                val recentMsgs = _chatMessages.value.filter { it.id != lastUserMsg.id }.takeLast(8)
                val result = geminiService.askAttendanceAdvisorMultimodal(
                    studentContext = contextSummary,
                    userQuery = lastUserMsg.text,
                    imageBitmap = lastUserMsg.imageBitmap,
                    recentMessages = recentMsgs
                )
                val aiResponseText = if (result.isSuccess && result.getOrNull()?.isNotBlank() == true) {
                    result.getOrNull()!!
                } else {
                    LocalAttendanceAdvisor.generateAdvice(
                        query = lastUserMsg.text,
                        stats = subjectStatsList.value,
                        slots = allSlotsWithSubjects.value,
                        profile = userProfile.value,
                        breaks = allBreaks.value,
                        records = allAttendanceRecords.value,
                        currentSemester = currentSemester.value,
                        leaveScores = getLeaveDayScores()
                    )
                }
                val newMsgs = _chatMessages.value + ChatMessage(sender = "ai", text = aiResponseText)
                _chatMessages.value = newMsgs
                _conversations.value = _conversations.value.map { conv ->
                    if (conv.id == activeId) conv.copy(messages = newMsgs, updatedAt = System.currentTimeMillis()) else conv
                }
                isChatLoading.value = false
                saveConversationsToDisk()
            }
        }
    }

    private fun saveConversationsToDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(getApplication<Application>().filesDir, "gemini_conversations.json")
                val array = JSONArray()
                for (conv in _conversations.value) {
                    val convObj = JSONObject().apply {
                        put("id", conv.id)
                        put("title", conv.title)
                        put("createdAt", conv.createdAt)
                        put("updatedAt", conv.updatedAt)
                        val msgsArray = JSONArray()
                        for (m in conv.messages) {
                            val mObj = JSONObject().apply {
                                put("id", m.id)
                                put("sender", m.sender)
                                put("text", m.text)
                                put("timestamp", m.timestamp)
                                m.isLiked?.let { put("isLiked", it) }
                            }
                            msgsArray.put(mObj)
                        }
                        put("messages", msgsArray)
                    }
                    array.put(convObj)
                }
                file.writeText(array.toString())
            } catch (e: Exception) {
                // non-fatal
            }
        }
    }

    private fun loadConversationsFromDisk() {
        try {
            val file = java.io.File(getApplication<Application>().filesDir, "gemini_conversations.json")
            if (file.exists()) {
                val text = file.readText()
                val array = JSONArray(text)
                val loadedConvs = mutableListOf<ChatConversation>()
                for (i in 0 until array.length()) {
                    val cObj = array.getJSONObject(i)
                    val mArray = cObj.optJSONArray("messages") ?: JSONArray()
                    val msgs = mutableListOf<ChatMessage>()
                    for (j in 0 until mArray.length()) {
                        val mObj = mArray.getJSONObject(j)
                        val isLikedVal = if (mObj.has("isLiked")) mObj.getBoolean("isLiked") else null
                        msgs.add(
                            ChatMessage(
                                id = mObj.optString("id", java.util.UUID.randomUUID().toString()),
                                sender = mObj.optString("sender", "ai"),
                                text = mObj.optString("text", ""),
                                timestamp = mObj.optLong("timestamp", System.currentTimeMillis()),
                                isLiked = isLikedVal
                            )
                        )
                    }
                    loadedConvs.add(
                        ChatConversation(
                            id = cObj.optString("id", java.util.UUID.randomUUID().toString()),
                            title = cObj.optString("title", "Chat"),
                            messages = msgs,
                            createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = cObj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (loadedConvs.isNotEmpty()) {
                    _conversations.value = loadedConvs
                    _activeConversationId.value = loadedConvs.first().id
                    _chatMessages.value = loadedConvs.first().messages
                    return
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        startNewChat()
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
        val currentSem = currentSemester.value
        val allSems = allSemesters.value
        val leaveScores = getLeaveDayScores()

        val leadDays = profile?.examCutoffLeadDays ?: 2
        val calcMode = CalculationTimeframe.fromId(profile?.calculationMode)
        val sb = StringBuilder()
        
        val currentTime = java.util.Calendar.getInstance()
        val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormatter = java.text.SimpleDateFormat("hh:mm a", Locale.US)
        val daysOfWeekFull = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentDayOfWeek = currentTime.get(java.util.Calendar.DAY_OF_WEEK)
        val currentDayName = daysOfWeekFull[currentDayOfWeek - 1]
        
        // 1-indexed (1=Monday ... 6=Saturday, 7=Sunday)
        val appDayOfWeek = when (currentDayOfWeek) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            else -> 7
        }
        val tomorrowAppDay = if (appDayOfWeek == 7) 1 else appDayOfWeek + 1
        val daysOfWeekShort = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val tomorrowDayName = daysOfWeekShort[tomorrowAppDay - 1]

        val totalConducted = stats.sumOf { it.subject.conductedClasses }
        val totalAttended = stats.sumOf { it.subject.attendedClasses }
        val overallPct = if (totalConducted > 0) (totalAttended.toDouble() / totalConducted.toDouble()) * 100.0 else 100.0
        val targetThreshold = profile?.globalThreshold ?: 75.0

        sb.append("CURRENT SYSTEM REAL-TIME CLOCK:\n")
        sb.append("Today's Date: ${dateFormatter.format(currentTime.time)} ($currentDayName)\n")
        sb.append("Current Time: ${timeFormatter.format(currentTime.time)}\n")
        sb.append("Tomorrow is: $tomorrowDayName\n\n")

        sb.append("STUDENT PROFILE & ACADEMIC CONFIG:\n")
        sb.append("Student Name: ${profile?.studentName ?: "Student"}\n")
        sb.append("Roll Number: ${profile?.rollNumber ?: "N/A"}\n")
        sb.append("College / University: ${profile?.college ?: "DTU"}\n")
        sb.append("Branch & Semester: ${profile?.branch ?: "Engineering"} (Semester ${profile?.semester ?: 5})\n")
        sb.append("Active Semester: ${currentSem?.title ?: "Current Semester"} [${currentSem?.academicYear ?: "2026-2027"}] (Start: ${currentSem?.startDate ?: "2026-08-01"} to End: ${currentSem?.endDate ?: "2026-12-15"})\n")
        sb.append("Global Mandatory Threshold: ${targetThreshold}%\n")
        sb.append("Emergency Leave Buffer: ${profile?.globalEmergencyBuffer ?: 3} classes (Included in math: ${profile?.includeBufferInCalculations})\n")
        sb.append("Calculation Mode: ${calcMode.title} (Cutoff is $leadDays days before exam)\n")
        sb.append("Midsem Date: ${profile?.midsemDate ?: "2026-10-15"}, Endsem Date: ${profile?.endsemDate ?: "2026-12-08"}\n")
        sb.append("Attendance Streak: ${profile?.currentStreak ?: 0} days (Longest: ${profile?.longestStreak ?: 0} days)\n")
        sb.append("Overall Attendance: ${String.format(Locale.US, "%.1f", overallPct)}% ($totalAttended / $totalConducted attended)\n\n")

        sb.append("TODAY'S SCHEDULE ($currentDayName):\n")
        if (appDayOfWeek == 7) {
            sb.append("- Sunday: No classes scheduled.\n")
        } else {
            val todaySlots = slots.filter { it.slot.dayOfWeek == appDayOfWeek }.sortedBy { it.slot.startTime }
            if (todaySlots.isEmpty()) {
                sb.append("- No classes scheduled today.\n")
            } else {
                todaySlots.forEach { s ->
                    val stat = stats.find { it.subject.id == s.subject.id }
                    val curPct = stat?.let { String.format(Locale.US, "%.1f", it.percentage) } ?: "N/A"
                    val safeBunk = stat?.rawBunkCapacity ?: 0
                    sb.append("- ${s.slot.startTime} to ${s.slot.endTime}: ${s.subject.name} (${s.subject.code}) [${s.slot.slotType}] at ${s.slot.location} | Current: $curPct% (Safe bunks: $safeBunk)\n")
                }
            }
        }

        sb.append("\nTOMORROW'S SCHEDULE ($tomorrowDayName):\n")
        if (tomorrowAppDay == 7) {
            sb.append("- Sunday: No classes scheduled.\n")
        } else {
            val tomorrowSlots = slots.filter { it.slot.dayOfWeek == tomorrowAppDay }.sortedBy { it.slot.startTime }
            if (tomorrowSlots.isEmpty()) {
                sb.append("- No classes scheduled tomorrow.\n")
            } else {
                tomorrowSlots.forEach { s ->
                    val stat = stats.find { it.subject.id == s.subject.id }
                    val curPct = stat?.let { String.format(Locale.US, "%.1f", it.percentage) } ?: "N/A"
                    val safeBunk = stat?.rawBunkCapacity ?: 0
                    sb.append("- ${s.slot.startTime} to ${s.slot.endTime}: ${s.subject.name} (${s.subject.code}) [${s.slot.slotType}] at ${s.slot.location} | Current: $curPct% (Safe bunks: $safeBunk)\n")
                }
            }
        }

        sb.append("\nALL REGISTERED SUBJECTS & LIVE MATHEMATICAL STATS:\n")
        if (stats.isEmpty()) {
            sb.append("- No subjects currently registered.\n")
        } else {
            stats.forEach { s ->
                val deficit = if (s.percentage < targetThreshold) " [SHORTAGE DEFICIT: Needs ${s.classesToReachTarget} consecutive attendances]" else ""
                sb.append("- ${s.subject.name} (${s.subject.code}): ${String.format(Locale.US, "%.1f", s.percentage)}% [Attended: ${s.subject.attendedClasses}, Conducted: ${s.subject.conductedClasses}, Missed: ${s.subject.conductedClasses - s.subject.attendedClasses}]. Tier: ${s.tier.levelName}. Safe Bunk (Target ${targetThreshold.toInt()}%): ${s.rawBunkCapacity} classes. Safe Bunk (With Buffer): ${s.safeBunkWithBuffer} classes. Remaining in window: ${s.remainingClassesInWindow}. Room: ${s.subject.defaultRoom}. Category: ${s.subject.category}, Credits: ${s.subject.credits}$deficit\n")
            }
        }

        sb.append("\nCOMPLETE WEEKLY TIMETABLE SCHEDULE (ALL DAYS):\n")
        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        for (day in 1..6) {
            val daySlots = slots.filter { it.slot.dayOfWeek == day }.sortedBy { it.slot.startTime }
            if (daySlots.isNotEmpty()) {
                sb.append("${daysOfWeek[day - 1]}:\n")
                daySlots.forEach {
                    sb.append("  - ${it.slot.startTime} to ${it.slot.endTime}: ${it.subject.name} (${it.subject.code}) [${it.slot.slotType}] at ${it.slot.location}\n")
                }
            } else {
                sb.append("${daysOfWeek[day - 1]}: Free day (no classes)\n")
            }
        }

        sb.append("\nLEAVE PLANNING & DAY SAFETY RATINGS:\n")
        leaveScores.forEach { ls ->
            val emoji = if (ls.safetyScore >= 75) "🟢" else if (ls.safetyScore >= 50) "🟡" else "🔴"
            sb.append("- $emoji ${ls.dayName}: Safety Score ${ls.safetyScore}/100 (${ls.totalSlots} classes). ${ls.recommendation}\n")
        }

        sb.append("\nACADEMIC BREAKS / CAMPUS HOLIDAYS:\n")
        if (breaks.isEmpty()) {
            sb.append("- No breaks recorded.\n")
        } else {
            breaks.forEach { b ->
                sb.append("- ${b.title} [${b.category}]: ${b.startDate} to ${b.endDate} (${b.description})\n")
            }
        }

        sb.append("\nRECENT ABSENCE AUDIT (LAST 15 ABSENCES):\n")
        val absentRecords = attendanceRecords.filter { it.status.equals("ABSENT", ignoreCase = true) }
            .sortedByDescending { it.date }
            .take(15)
        if (absentRecords.isEmpty()) {
            sb.append("- No recent absences recorded in history.\n")
        } else {
            absentRecords.forEach { rec ->
                val subjectName = stats.find { it.subject.id == rec.subjectId }?.subject?.name ?: "Subject #${rec.subjectId}"
                sb.append("  - Missed $subjectName on ${rec.date} (${rec.slotTime})\n")
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
                isRecoverableBeforeExam = attendAll >= (profile?.globalThreshold ?: 75.0),
                classesToReachTarget = stat.classesToReachTarget,
                safeBunkCapacity = stat.safeBunkWithBuffer
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

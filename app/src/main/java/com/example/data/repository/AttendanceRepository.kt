package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.dao.SlotWithSubject
import com.example.data.local.entity.AcademicBreakEntity
import com.example.data.local.entity.AttendanceRecordEntity
import com.example.data.local.entity.ScheduleSlotEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceRepository(private val database: AppDatabase) {
    private val subjectDao = database.subjectDao()
    private val slotDao = database.scheduleSlotDao()
    private val recordDao = database.attendanceRecordDao()
    private val profileDao = database.userProfileDao()
    private val breakDao = database.academicBreakDao()

    val allSubjects: Flow<List<SubjectEntity>> = subjectDao.getAllSubjects()
    val allSlotsWithSubjects: Flow<List<SlotWithSubject>> = slotDao.getAllSlotsWithSubjects()
    val userProfile: Flow<UserProfileEntity?> = profileDao.getProfileFlow()
    val recentRecords: Flow<List<AttendanceRecordEntity>> = recordDao.getAllRecords()
    val allBreaks: Flow<List<AcademicBreakEntity>> = breakDao.getAllBreaks()

    fun getSlotsForDay(dayOfWeek: Int): Flow<List<SlotWithSubject>> {
        return slotDao.getSlotsForDayWithSubjects(dayOfWeek)
    }

    fun getSubjectByIdFlow(id: Long): Flow<SubjectEntity?> {
        return subjectDao.getSubjectByIdFlow(id)
    }

    fun getRecordsForSubject(subjectId: Long): Flow<List<AttendanceRecordEntity>> {
        return recordDao.getRecordsForSubject(subjectId)
    }

    suspend fun markAttendance(
        subjectId: Long,
        status: String, // "PRESENT", "ABSENT", "CANCELLED", "DUTY_LEAVE"
        slotTime: String = "",
        note: String = ""
    ) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        when (status) {
            "PRESENT" -> {
                subjectDao.markPresent(subjectId)
                updateStreakOnPresent()
            }
            "ABSENT" -> {
                subjectDao.markAbsent(subjectId)
                resetStreakOnAbsent()
            }
            "DUTY_LEAVE" -> {
                // DTU Duty / Fest leaves usually count as attended or exempted
                subjectDao.markPresent(subjectId)
            }
            "CANCELLED" -> {
                // No count change to conducted or attended
            }
        }

        recordDao.insertRecord(
            AttendanceRecordEntity(
                subjectId = subjectId,
                date = todayStr,
                slotTime = slotTime,
                status = status,
                timestamp = System.currentTimeMillis(),
                note = note
            )
        )
    }

    suspend fun markAttendanceForDate(
        subjectId: Long,
        status: String, // "PRESENT", "ABSENT", "CANCELLED", "DUTY_LEAVE", "NONE"
        date: String,
        slotTime: String = "",
        note: String = ""
    ) {
        val existingRecords = recordDao.getRecordsForSubjectAndDate(subjectId, date)
        val existing = if (slotTime.isNotBlank()) {
            existingRecords.find { it.slotTime == slotTime || it.slotTime.isBlank() }
        } else {
            existingRecords.firstOrNull()
        }

        if (existing != null) {
            when (existing.status) {
                "PRESENT", "DUTY_LEAVE" -> subjectDao.undoPresent(subjectId)
                "ABSENT" -> subjectDao.undoAbsent(subjectId)
            }
            recordDao.deleteRecord(existing)
        }

        if (status != "NONE" && status != "UNMARKED" && status.isNotBlank()) {
            when (status) {
                "PRESENT" -> {
                    subjectDao.markPresent(subjectId)
                    updateStreakOnPresent()
                }
                "ABSENT" -> {
                    subjectDao.markAbsent(subjectId)
                    resetStreakOnAbsent()
                }
                "DUTY_LEAVE" -> {
                    subjectDao.markPresent(subjectId)
                }
                "CANCELLED" -> {
                    // No count changes
                }
            }

            recordDao.insertRecord(
                AttendanceRecordEntity(
                    subjectId = subjectId,
                    date = date,
                    slotTime = slotTime,
                    status = status,
                    timestamp = System.currentTimeMillis(),
                    note = note
                )
            )
        }
    }

    suspend fun setSessionStartDate(startDate: String) {
        val current = profileDao.getProfile() ?: return
        profileDao.updateProfile(current.copy(semesterStartDate = startDate))
    }

    suspend fun undoLastRecord(record: AttendanceRecordEntity) {
        when (record.status) {
            "PRESENT", "DUTY_LEAVE" -> subjectDao.undoPresent(record.subjectId)
            "ABSENT" -> subjectDao.undoAbsent(record.subjectId)
        }
        recordDao.deleteRecord(record)
    }

    suspend fun insertSubject(subject: SubjectEntity): Long {
        return subjectDao.insertSubject(subject)
    }

    suspend fun updateSubject(subject: SubjectEntity) {
        subjectDao.updateSubject(subject)
    }

    suspend fun deleteSubject(subject: SubjectEntity) {
        subjectDao.deleteSubject(subject)
    }

    suspend fun insertSlot(slot: ScheduleSlotEntity): Long {
        return slotDao.insertSlot(slot)
    }

    suspend fun updateSlot(slot: ScheduleSlotEntity) {
        slotDao.updateSlot(slot)
    }

    suspend fun deleteSlot(slot: ScheduleSlotEntity) {
        slotDao.deleteSlot(slot)
    }

    suspend fun updateProfile(profile: UserProfileEntity) {
        profileDao.saveProfile(profile)
    }

    suspend fun addAcademicBreak(academicBreak: AcademicBreakEntity): Long {
        return breakDao.insertBreak(academicBreak)
    }

    suspend fun updateAcademicBreak(academicBreak: AcademicBreakEntity) {
        breakDao.updateBreak(academicBreak)
    }

    suspend fun deleteAcademicBreak(academicBreak: AcademicBreakEntity) {
        breakDao.deleteBreak(academicBreak)
    }

    suspend fun deleteAcademicBreakById(id: Long) {
        breakDao.deleteBreakById(id)
    }

    suspend fun getAllBreaksList(): List<AcademicBreakEntity> {
        return breakDao.getAllBreaksList()
    }

    suspend fun checkAndInitializeDefaultDTUData() {
        val existingProfile = profileDao.getProfile()
        if (existingProfile == null) {
            val defaultProfile = UserProfileEntity(
                id = 1,
                isSetupComplete = false,
                studentName = "DTUite",
                rollNumber = "2K23/SE/101",
                college = "Delhi Technological University (DTU)",
                branch = "Software Engineering (SE)",
                semester = 5,
                globalThreshold = 75.0,
                stepPercentage = 5.0,
                globalEmergencyBuffer = 3,
                includeBufferInCalculations = true,
                midsemDate = "2026-10-15",
                endsemDate = "2026-12-08",
                reminder30MinEnabled = true,
                levelDropNotificationEnabled = true,
                currentStreak = 0,
                longestStreak = 0,
                alarmEnabled = true,
                alarmLeadHours = 2.0,
                alarmMode = "RELATIVE"
            )
            profileDao.saveProfile(defaultProfile)
        }
    }

    suspend fun completeSetupWithCustomSchedule(
        subjects: List<SubjectEntity>,
        slotAssignments: List<Pair<Int, ScheduleSlotEntity>>, // Pair(subjectIndex, Slot)
        profileUpdate: UserProfileEntity
    ) {
        completeSetupWithCustomScheduleAndHistory(
            subjects = subjects,
            slotAssignments = slotAssignments,
            records = emptyList(),
            profileUpdate = profileUpdate
        )
    }

    suspend fun completeSetupWithCustomScheduleAndHistory(
        subjects: List<SubjectEntity>,
        slotAssignments: List<Pair<Int, ScheduleSlotEntity>>, // Pair(subjectIndex, Slot)
        records: List<Pair<Int, AttendanceRecordEntity>>, // Pair(subjectIndex, Record)
        profileUpdate: UserProfileEntity
    ) {
        subjectDao.deleteAllSubjects()
        slotDao.deleteAllSlots()
        recordDao.deleteAllRecords()

        val insertedIds = subjectDao.insertSubjects(subjects)

        val slotsToInsert = slotAssignments.mapNotNull { (subIndex, slotTemplate) ->
            val actualSubId = insertedIds.getOrNull(subIndex)
            if (actualSubId != null) {
                slotTemplate.copy(id = 0, subjectId = actualSubId)
            } else null
        }

        if (slotsToInsert.isNotEmpty()) {
            slotDao.insertSlots(slotsToInsert)
        }

        val recordsToInsert = records.mapNotNull { (subIndex, recordTemplate) ->
            val actualSubId = insertedIds.getOrNull(subIndex)
            if (actualSubId != null) {
                recordTemplate.copy(id = 0, subjectId = actualSubId)
            } else null
        }

        if (recordsToInsert.isNotEmpty()) {
            recordDao.insertRecords(recordsToInsert)
        }

        profileDao.saveProfile(profileUpdate.copy(isSetupComplete = true))
    }

    suspend fun updateSchedulePreservingAttendance(
        subjects: List<SubjectEntity>,
        slotAssignments: List<Pair<Int, ScheduleSlotEntity>>
    ) {
        slotDao.deleteAllSlots()
        val currentSubjects = subjectDao.getAllSubjects()
        // Update or insert subjects
        val insertedIds = mutableListOf<Long>()
        subjects.forEach { sub ->
            if (sub.id != 0L) {
                subjectDao.updateSubject(sub)
                insertedIds.add(sub.id)
            } else {
                val newId = subjectDao.insertSubject(sub)
                insertedIds.add(newId)
            }
        }

        val slotsToInsert = slotAssignments.mapNotNull { (subIndex, slotTemplate) ->
            val actualSubId = insertedIds.getOrNull(subIndex)
            if (actualSubId != null) {
                slotTemplate.copy(id = 0, subjectId = actualSubId)
            } else null
        }

        if (slotsToInsert.isNotEmpty()) {
            slotDao.insertSlots(slotsToInsert)
        }
    }

    suspend fun replaceSchedule(
        subjects: List<SubjectEntity>,
        slotAssignments: List<Pair<Int, ScheduleSlotEntity>>
    ) {
        subjectDao.deleteAllSubjects()
        slotDao.deleteAllSlots()
        recordDao.deleteAllRecords()

        val insertedIds = subjectDao.insertSubjects(subjects)

        val slotsToInsert = slotAssignments.mapNotNull { (subIndex, slotTemplate) ->
            val actualSubId = insertedIds.getOrNull(subIndex)
            if (actualSubId != null) {
                slotTemplate.copy(id = 0, subjectId = actualSubId)
            } else null
        }

        if (slotsToInsert.isNotEmpty()) {
            slotDao.insertSlots(slotsToInsert)
        }
    }

    suspend fun bulkUpdateSubjects(updatedSubjects: List<SubjectEntity>) {
        updatedSubjects.forEach { subject ->
            subjectDao.updateSubject(subject)
        }
    }

    suspend fun resetSetup() {
        subjectDao.deleteAllSubjects()
        slotDao.deleteAllSlots()
        recordDao.deleteAllRecords()
        val current = profileDao.getProfile()
        if (current != null) {
            profileDao.saveProfile(current.copy(isSetupComplete = false, currentStreak = 0))
        }
    }

    suspend fun loadDTUDefaultSubjectsAndSchedule(freshZeroAttendance: Boolean = false) {
        val dtuSubjects = listOf(
            SubjectEntity(
                name = "Software Engineering",
                code = "SE-301",
                conductedClasses = if (freshZeroAttendance) 0 else 24,
                attendedClasses = if (freshZeroAttendance) 0 else 20,
                targetPercentage = 75.0,
                emergencyBuffer = 3,
                colorHex = "#00E5FF", // Neon Cyan
                defaultRoom = "Pragya Bhavan PB-02",
                professorName = "Prof. Ruchika M.",
                category = "Core",
                credits = 4
            ),
            SubjectEntity(
                name = "Machine Learning",
                code = "SE-303",
                conductedClasses = if (freshZeroAttendance) 0 else 22,
                attendedClasses = if (freshZeroAttendance) 0 else 19,
                targetPercentage = 75.0,
                emergencyBuffer = 3,
                colorHex = "#10B981", // Emerald
                defaultRoom = "Pragya Bhavan PB-04",
                professorName = "Prof. Vinod Kumar",
                category = "Core",
                credits = 4
            ),
            SubjectEntity(
                name = "Operating Systems",
                code = "SE-305",
                conductedClasses = if (freshZeroAttendance) 0 else 25,
                attendedClasses = if (freshZeroAttendance) 0 else 18,
                targetPercentage = 75.0,
                emergencyBuffer = 2,
                colorHex = "#F59E0B", // Amber Gold
                defaultRoom = "SPS-09 (Science Block)",
                professorName = "Prof. Abhilasha S.",
                category = "Core",
                credits = 4
            ),
            SubjectEntity(
                name = "Database Management Systems",
                code = "SE-307",
                conductedClasses = if (freshZeroAttendance) 0 else 20,
                attendedClasses = if (freshZeroAttendance) 0 else 14,
                targetPercentage = 75.0,
                emergencyBuffer = 3,
                colorHex = "#EA580C", // Shortage Orange
                defaultRoom = "AB-4 Room 204",
                professorName = "Prof. Manoj Kumar",
                category = "Core",
                credits = 4
            ),
            SubjectEntity(
                name = "Software Engineering Lab",
                code = "SE-309",
                conductedClasses = if (freshZeroAttendance) 0 else 8,
                attendedClasses = if (freshZeroAttendance) 0 else 8,
                targetPercentage = 75.0,
                emergencyBuffer = 1,
                colorHex = "#A855F7", // Purple
                defaultRoom = "AB-4 Software Lab-1",
                professorName = "Lab Incharge",
                category = "Lab",
                credits = 2
            ),
            SubjectEntity(
                name = "ML & Data Science Lab",
                code = "SE-311",
                conductedClasses = if (freshZeroAttendance) 0 else 8,
                attendedClasses = if (freshZeroAttendance) 0 else 7,
                targetPercentage = 75.0,
                emergencyBuffer = 1,
                colorHex = "#3B82F6", // Blue
                defaultRoom = "Pragya Bhavan AI Lab 302",
                professorName = "Lab Faculty",
                category = "Lab",
                credits = 2
            ),
            SubjectEntity(
                name = "Economics for Engineers",
                code = "HU-301",
                conductedClasses = if (freshZeroAttendance) 0 else 16,
                attendedClasses = if (freshZeroAttendance) 0 else 14,
                targetPercentage = 75.0,
                emergencyBuffer = 2,
                colorHex = "#14B8A6", // Teal
                defaultRoom = "Tw-1 (Technological Wing)",
                professorName = "Prof. Humanities",
                category = "Elective",
                credits = 3
            )
        )

        val insertedIds = subjectDao.insertSubjects(dtuSubjects)
        
        // Map slot schedule to newly inserted IDs
        val seId = insertedIds.getOrElse(0) { 1L }
        val mlId = insertedIds.getOrElse(1) { 2L }
        val osId = insertedIds.getOrElse(2) { 3L }
        val dbmsId = insertedIds.getOrElse(3) { 4L }
        val seLabId = insertedIds.getOrElse(4) { 5L }
        val mlLabId = insertedIds.getOrElse(5) { 6L }
        val ecoId = insertedIds.getOrElse(6) { 7L }

        val dtuSlots = listOf(
            // Monday
            ScheduleSlotEntity(subjectId = seId, dayOfWeek = 1, startTime = "09:00 AM", endTime = "10:00 AM", location = "Pragya Bhavan PB-02", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = mlId, dayOfWeek = 1, startTime = "10:00 AM", endTime = "11:00 AM", location = "Pragya Bhavan PB-04", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = osId, dayOfWeek = 1, startTime = "11:00 AM", endTime = "12:00 PM", location = "SPS-09", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = seLabId, dayOfWeek = 1, startTime = "02:00 PM", endTime = "04:00 PM", location = "AB-4 Software Lab-1", slotType = "Lab"),

            // Tuesday
            ScheduleSlotEntity(subjectId = dbmsId, dayOfWeek = 2, startTime = "09:00 AM", endTime = "10:00 AM", location = "AB-4 Room 204", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = seId, dayOfWeek = 2, startTime = "10:00 AM", endTime = "11:00 AM", location = "Pragya Bhavan PB-02", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = ecoId, dayOfWeek = 2, startTime = "11:00 AM", endTime = "12:00 PM", location = "Tw-1 (Tech Wing)", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = mlId, dayOfWeek = 2, startTime = "01:00 PM", endTime = "02:00 PM", location = "Pragya Bhavan PB-04", slotType = "Lecture"),

            // Wednesday
            ScheduleSlotEntity(subjectId = osId, dayOfWeek = 3, startTime = "09:00 AM", endTime = "10:00 AM", location = "SPS-09", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = dbmsId, dayOfWeek = 3, startTime = "10:00 AM", endTime = "11:00 AM", location = "AB-4 Room 204", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = mlLabId, dayOfWeek = 3, startTime = "02:00 PM", endTime = "04:00 PM", location = "Pragya Bhavan AI Lab 302", slotType = "Lab"),

            // Thursday
            ScheduleSlotEntity(subjectId = seId, dayOfWeek = 4, startTime = "09:00 AM", endTime = "10:00 AM", location = "Pragya Bhavan PB-02", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = mlId, dayOfWeek = 4, startTime = "10:00 AM", endTime = "11:00 AM", location = "Pragya Bhavan PB-04", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = osId, dayOfWeek = 4, startTime = "11:00 AM", endTime = "12:00 PM", location = "SPS-09", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = dbmsId, dayOfWeek = 4, startTime = "12:00 PM", endTime = "01:00 PM", location = "AB-4 Room 204", slotType = "Lecture"),

            // Friday
            ScheduleSlotEntity(subjectId = ecoId, dayOfWeek = 5, startTime = "10:00 AM", endTime = "11:00 AM", location = "Tw-1 (Tech Wing)", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = dbmsId, dayOfWeek = 5, startTime = "11:00 AM", endTime = "12:00 PM", location = "AB-4 Room 204", slotType = "Lecture"),
            ScheduleSlotEntity(subjectId = seId, dayOfWeek = 5, startTime = "02:00 PM", endTime = "03:00 PM", location = "Pragya Bhavan PB-02", slotType = "Tutorial")
        )

        slotDao.insertSlots(dtuSlots)

        // Seed Sample Academic Breaks / College Fests (e.g. Invictus Tech Fest, Midsem Prep Break, Diwali)
        val defaultBreaks = listOf(
            AcademicBreakEntity(
                title = "Invictus Tech Fest 2026",
                startDate = "2026-09-18",
                endDate = "2026-09-20",
                category = "TECH_FEST",
                description = "Annual DTU Tech Fest - Hackathons, Robot Wars, and Tech Expos. All lectures suspended.",
                affectsAllClasses = true
            ),
            AcademicBreakEntity(
                title = "Midsem Preparatory Break",
                startDate = "2026-10-10",
                endDate = "2026-10-14",
                category = "EXAM_BREAK",
                description = "Self-study and preparation leave before Mid-Semester Examinations.",
                affectsAllClasses = true
            ),
            AcademicBreakEntity(
                title = "Diwali & Festive Holidays",
                startDate = "2026-11-01",
                endDate = "2026-11-05",
                category = "HOLIDAY",
                description = "Festive vacation break - College closed.",
                affectsAllClasses = true
            )
        )
        defaultBreaks.forEach { breakDao.insertBreak(it) }
    }

    private suspend fun updateStreakOnPresent() {
        val current = profileDao.getProfile() ?: return
        val newStreak = current.currentStreak + 1
        val newLongest = maxOf(current.longestStreak, newStreak)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        profileDao.updateProfile(
            current.copy(
                currentStreak = newStreak,
                longestStreak = newLongest,
                lastAttendanceDate = todayStr
            )
        )
    }

    private suspend fun resetStreakOnAbsent() {
        val current = profileDao.getProfile() ?: return
        profileDao.updateProfile(current.copy(currentStreak = 0))
    }
}

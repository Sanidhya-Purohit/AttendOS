package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val code: String,
    val conductedClasses: Int = 0,
    val attendedClasses: Int = 0,
    val targetPercentage: Double = 75.0,
    val emergencyBuffer: Int = 3,
    val colorHex: String = "#00E5FF",
    val defaultRoom: String = "Pragya Bhavan PB-02",
    val professorName: String = "Prof. Sharma",
    val category: String = "Core", // Core, Elective, Lab, MOOC
    val credits: Int = 4
)

@Entity(
    tableName = "schedule_slots",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class ScheduleSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val dayOfWeek: Int, // 1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday, 5 = Friday, 6 = Saturday
    val startTime: String, // "09:00 AM"
    val endTime: String,   // "10:00 AM"
    val slotType: String = "Lecture", // Lecture, Lab, Tutorial
    val location: String = "Pragya Bhavan",
    val isCancelled: Boolean = false
)

@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId"), Index("date")]
)
data class AttendanceRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val date: String, // "YYYY-MM-DD"
    val slotTime: String = "",
    val status: String, // "PRESENT", "ABSENT", "CANCELLED", "DUTY_LEAVE"
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val isSetupComplete: Boolean = false,
    val studentName: String = "DTUite",
    val rollNumber: String = "2K23/SE/101",
    val college: String = "Delhi Technological University (DTU)",
    val branch: String = "Software Engineering (SE)",
    val semester: Int = 5,
    val globalThreshold: Double = 75.0,
    val stepPercentage: Double = 5.0,
    val globalEmergencyBuffer: Int = 3,
    val includeBufferInCalculations: Boolean = true,
    // Exam & Calculation Timeframe Settings
    val calculationMode: String = "NEXT_EXAM", // "NEXT_EXAM" (default, till next exam) or "FULL_SEMESTER" (till endsems)
    val examCutoffLeadDays: Int = 2, // customizable (e.g. 2 days before exam date)
    val nextExamName: String = "Midsem Exams",
    val midsemDate: String = "2026-10-15",
    val endsemDate: String = "2026-12-08",
    val semesterStartDate: String = "2026-08-01",
    val reminder30MinEnabled: Boolean = true,
    val levelDropNotificationEnabled: Boolean = true,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastAttendanceDate: String = "",
    // Smart First-Class Alarm Settings
    val alarmEnabled: Boolean = true,
    val alarmLeadHours: Double = 2.0, // e.g. 2.0 = 2 hours before first class
    val alarmMode: String = "RELATIVE", // "RELATIVE" or "DAY_SPECIFIC"
    val monAlarmTime: String = "07:00 AM",
    val tueAlarmTime: String = "07:00 AM",
    val wedAlarmTime: String = "07:00 AM",
    val thuAlarmTime: String = "07:00 AM",
    val friAlarmTime: String = "08:00 AM",
    val satAlarmTime: String = "08:00 AM"
)

@Entity(tableName = "academic_breaks")
data class AcademicBreakEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String, // e.g. "Invictus Tech Fest", "Engifest", "Midsem Break", "Diwali Break", "Preparatory Leave"
    val startDate: String, // "YYYY-MM-DD"
    val endDate: String,   // "YYYY-MM-DD"
    val category: String = "TECH_FEST", // "TECH_FEST", "CULTURAL_FEST", "HOLIDAY", "EXAM_BREAK", "WEATHER_SUSPENSION", "OTHER"
    val description: String = "Classes suspended due to college fest / holiday",
    val affectsAllClasses: Boolean = true
)


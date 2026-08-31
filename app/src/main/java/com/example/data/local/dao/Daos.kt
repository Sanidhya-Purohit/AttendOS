package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.AcademicBreakEntity
import com.example.data.local.entity.AttendanceRecordEntity
import com.example.data.local.entity.ScheduleSlotEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

data class SlotWithSubject(
    @Embedded val slot: ScheduleSlotEntity,
    @Relation(
        parentColumn = "subjectId",
        entityColumn = "id"
    )
    val subject: SubjectEntity
)

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getSubjectById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subjects WHERE id = :id")
    fun getSubjectByIdFlow(id: Long): Flow<SubjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>): List<Long>

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("UPDATE subjects SET attendedClasses = attendedClasses + 1, conductedClasses = conductedClasses + 1 WHERE id = :id")
    suspend fun markPresent(id: Long)

    @Query("UPDATE subjects SET conductedClasses = conductedClasses + 1 WHERE id = :id")
    suspend fun markAbsent(id: Long)

    @Query("UPDATE subjects SET attendedClasses = CASE WHEN attendedClasses > 0 THEN attendedClasses - 1 ELSE 0 END, conductedClasses = CASE WHEN conductedClasses > 0 THEN conductedClasses - 1 ELSE 0 END WHERE id = :id")
    suspend fun undoPresent(id: Long)

    @Query("UPDATE subjects SET conductedClasses = CASE WHEN conductedClasses > 0 THEN conductedClasses - 1 ELSE 0 END WHERE id = :id")
    suspend fun undoAbsent(id: Long)

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()
}

@Dao
interface ScheduleSlotDao {
    @Transaction
    @Query("SELECT * FROM schedule_slots ORDER BY dayOfWeek ASC, startTime ASC")
    fun getAllSlotsWithSubjects(): Flow<List<SlotWithSubject>>

    @Transaction
    @Query("SELECT * FROM schedule_slots WHERE dayOfWeek = :dayOfWeek ORDER BY startTime ASC")
    fun getSlotsForDayWithSubjects(dayOfWeek: Int): Flow<List<SlotWithSubject>>

    @Query("SELECT * FROM schedule_slots WHERE subjectId = :subjectId")
    fun getSlotsForSubject(subjectId: Long): Flow<List<ScheduleSlotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: ScheduleSlotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<ScheduleSlotEntity>): List<Long>

    @Update
    suspend fun updateSlot(slot: ScheduleSlotEntity)

    @Delete
    suspend fun deleteSlot(slot: ScheduleSlotEntity)

    @Query("DELETE FROM schedule_slots")
    suspend fun deleteAllSlots()
}

@Dao
interface AttendanceRecordDao {
    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE subjectId = :subjectId ORDER BY timestamp DESC")
    fun getRecordsForSubject(subjectId: Long): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getRecordsForDate(date: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE subjectId = :subjectId AND date = :date")
    suspend fun getRecordsForSubjectAndDate(subjectId: Long, date: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    suspend fun getRecordsListForDate(date: String): List<AttendanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AttendanceRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<AttendanceRecordEntity>): List<Long>

    @Delete
    suspend fun deleteRecord(record: AttendanceRecordEntity)

    @Query("DELETE FROM attendance_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM attendance_records")
    suspend fun deleteAllRecords()
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfileFlow(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfileEntity)

    @Update
    suspend fun updateProfile(profile: UserProfileEntity)
}

@Dao
interface AcademicBreakDao {
    @Query("SELECT * FROM academic_breaks ORDER BY startDate ASC")
    fun getAllBreaks(): Flow<List<AcademicBreakEntity>>

    @Query("SELECT * FROM academic_breaks ORDER BY startDate ASC")
    suspend fun getAllBreaksList(): List<AcademicBreakEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreak(academicBreak: AcademicBreakEntity): Long

    @Update
    suspend fun updateBreak(academicBreak: AcademicBreakEntity)

    @Delete
    suspend fun deleteBreak(academicBreak: AcademicBreakEntity)

    @Query("DELETE FROM academic_breaks WHERE id = :id")
    suspend fun deleteBreakById(id: Long)
}

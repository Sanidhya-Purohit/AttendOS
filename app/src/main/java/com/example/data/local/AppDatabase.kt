package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AcademicBreakDao
import com.example.data.local.dao.AttendanceRecordDao
import com.example.data.local.dao.ScheduleSlotDao
import com.example.data.local.dao.SubjectDao
import com.example.data.local.dao.UserProfileDao
import com.example.data.local.entity.AcademicBreakEntity
import com.example.data.local.entity.AttendanceRecordEntity
import com.example.data.local.entity.ScheduleSlotEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.UserProfileEntity

@Database(
    entities = [
        SubjectEntity::class,
        ScheduleSlotEntity::class,
        AttendanceRecordEntity::class,
        UserProfileEntity::class,
        AcademicBreakEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun scheduleSlotDao(): ScheduleSlotDao
    abstract fun attendanceRecordDao(): AttendanceRecordDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun academicBreakDao(): AcademicBreakDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dtu_attendance_master.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

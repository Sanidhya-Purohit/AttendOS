package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.SubjectStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RestoreResult(
    val success: Boolean,
    val semestersCount: Int = 0,
    val subjectsCount: Int = 0,
    val slotsCount: Int = 0,
    val recordsCount: Int = 0,
    val breaksCount: Int = 0,
    val errorMessage: String? = null
)

object ExportBackupManager {

    /**
     * Generates a formal, university-ready A4 PDF attendance report
     */
    suspend fun exportAttendancePdf(
        context: Context,
        profile: UserProfileEntity,
        semester: SemesterEntity?,
        subjects: List<SubjectEntity>,
        statsList: List<SubjectStats>
    ): File = withContext(Dispatchers.IO) {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72 dpi
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }
        val titlePaint = Paint().apply {
            isAntiAlias = true
            isFakeBoldText = true
        }

        val primaryDark = Color.rgb(15, 23, 42) // Slate 900
        val accentBlue = Color.rgb(2, 132, 199) // Sky 600
        val textDark = Color.rgb(30, 41, 59)   // Slate 800
        val textMuted = Color.rgb(100, 116, 139) // Slate 500
        val borderGray = Color.rgb(226, 232, 240) // Slate 200
        val tableHeaderBg = Color.rgb(241, 245, 249) // Slate 100
        val greenSuccess = Color.rgb(22, 163, 74) // Green 600
        val redDanger = Color.rgb(220, 38, 38)   // Red 600

        var currentY = 36f

        // 1. Top Decorative Bar
        paint.color = accentBlue
        canvas.drawRect(0f, 0f, 595f, 8f, paint)

        // 2. University / Institution Header
        titlePaint.color = primaryDark
        titlePaint.textSize = 15f
        titlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(profile.college.uppercase(Locale.US), 297.5f, currentY, titlePaint)
        currentY += 16f

        titlePaint.textSize = 12f
        titlePaint.color = accentBlue
        canvas.drawText("OFFICIAL ACADEMIC ATTENDANCE AUDIT & COMPLIANCE RECORD", 297.5f, currentY, titlePaint)
        currentY += 14f

        paint.textSize = 8.5f
        paint.color = textMuted
        paint.textAlign = Paint.Align.CENTER
        val semTitle = semester?.title ?: "Semester ${profile.semester}"
        val auditDate = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.US).format(Date())
        canvas.drawText("Academic Session: ${semester?.academicYear ?: "2026-2027"} • $semTitle • Generated: $auditDate", 297.5f, currentY, paint)
        currentY += 12f

        // Thin divider
        paint.color = borderGray
        paint.strokeWidth = 1f
        canvas.drawLine(36f, currentY, 559f, currentY, paint)
        currentY += 14f

        // 3. Student Credentials Matrix
        paint.textAlign = Paint.Align.LEFT
        paint.color = tableHeaderBg
        canvas.drawRoundRect(RectF(36f, currentY, 559f, currentY + 54f), 6f, 6f, paint)

        paint.color = borderGray
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(RectF(36f, currentY, 559f, currentY + 54f), 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        // Row 1 of info
        val infoY1 = currentY + 18f
        titlePaint.textAlign = Paint.Align.LEFT
        titlePaint.textSize = 9f
        titlePaint.color = textMuted
        canvas.drawText("STUDENT NAME:", 46f, infoY1, titlePaint)
        titlePaint.color = primaryDark
        titlePaint.textSize = 9.5f
        canvas.drawText(profile.studentName, 126f, infoY1, titlePaint)

        titlePaint.color = textMuted
        titlePaint.textSize = 9f
        canvas.drawText("ROLL NUMBER:", 330f, infoY1, titlePaint)
        titlePaint.color = primaryDark
        titlePaint.textSize = 9.5f
        canvas.drawText(profile.rollNumber, 410f, infoY1, titlePaint)

        // Row 2 of info
        val infoY2 = currentY + 38f
        titlePaint.color = textMuted
        titlePaint.textSize = 9f
        canvas.drawText("DEPARTMENT/BRANCH:", 46f, infoY2, titlePaint)
        titlePaint.color = primaryDark
        titlePaint.textSize = 9.5f
        canvas.drawText(profile.branch, 160f, infoY2, titlePaint)

        titlePaint.color = textMuted
        titlePaint.textSize = 9f
        canvas.drawText("SEMESTER STATUS:", 330f, infoY2, titlePaint)
        val lockStatus = if (semester?.isLocked == true) "FINALIZED (LOCKED)" else "ACTIVE (ONGOING)"
        titlePaint.color = if (semester?.isLocked == true) primaryDark else accentBlue
        titlePaint.textSize = 9.5f
        canvas.drawText(lockStatus, 430f, infoY2, titlePaint)

        currentY += 66f

        // 4. Overall Attendance KPI Summary Card
        val totalConducted = subjects.sumOf { it.conductedClasses }
        val totalAttended = subjects.sumOf { it.attendedClasses }
        val overallPct = if (totalConducted > 0) (totalAttended.toDouble() / totalConducted) * 100.0 else 0.0
        val isEligible = overallPct >= profile.globalThreshold

        paint.color = if (isEligible) Color.rgb(240, 253, 244) else Color.rgb(254, 242, 242)
        canvas.drawRoundRect(RectF(36f, currentY, 559f, currentY + 44f), 6f, 6f, paint)
        paint.color = if (isEligible) Color.rgb(187, 247, 208) else Color.rgb(254, 202, 202)
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(RectF(36f, currentY, 559f, currentY + 44f), 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        titlePaint.textSize = 14f
        titlePaint.color = if (isEligible) greenSuccess else redDanger
        canvas.drawText("${String.format(Locale.US, "%.1f", overallPct)}%", 48f, currentY + 28f, titlePaint)

        paint.textSize = 9f
        paint.color = textDark
        val eligibilityText = if (isEligible) "ELIGIBLE FOR SEMESTER EXAMINATIONS (Above ${profile.globalThreshold}% Threshold)"
        else "CRITICAL SHORTAGE - DEBARMENT WARNING (Below ${profile.globalThreshold}% Threshold)"
        paint.isFakeBoldText = true
        canvas.drawText(eligibilityText, 115f, currentY + 20f, paint)
        paint.isFakeBoldText = false
        paint.color = textMuted
        canvas.drawText("Total Conducted: $totalConducted classes | Total Attended: $totalAttended classes | Bunked/Missed: ${totalConducted - totalAttended} classes", 115f, currentY + 34f, paint)

        currentY += 56f

        // 5. Subject Breakdown Table Header
        paint.color = tableHeaderBg
        canvas.drawRect(36f, currentY, 559f, currentY + 22f, paint)
        paint.color = borderGray
        paint.strokeWidth = 1f
        canvas.drawLine(36f, currentY + 22f, 559f, currentY + 22f, paint)

        titlePaint.textSize = 8.5f
        titlePaint.color = primaryDark
        canvas.drawText("CODE", 42f, currentY + 15f, titlePaint)
        canvas.drawText("SUBJECT / COURSE TITLE", 96f, currentY + 15f, titlePaint)
        canvas.drawText("TYPE", 290f, currentY + 15f, titlePaint)
        canvas.drawText("CR", 336f, currentY + 15f, titlePaint)
        canvas.drawText("COND", 366f, currentY + 15f, titlePaint)
        canvas.drawText("ATTN", 408f, currentY + 15f, titlePaint)
        canvas.drawText("PERCENT", 448f, currentY + 15f, titlePaint)
        canvas.drawText("STATUS", 504f, currentY + 15f, titlePaint)

        currentY += 22f

        // 6. Subject Rows
        paint.textSize = 8.5f
        subjects.forEachIndexed { index, sub ->
            val rowY = currentY + 17f
            if (index % 2 == 1) {
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRect(36f, currentY, 559f, currentY + 22f, paint)
            }

            // Divider line
            paint.color = borderGray
            paint.strokeWidth = 0.5f
            canvas.drawLine(36f, currentY + 22f, 559f, currentY + 22f, paint)

            paint.color = textDark
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(sub.code.ifBlank { "SUB-${index + 1}" }, 42f, rowY, paint)

            val displayName = if (sub.name.length > 28) sub.name.take(26) + ".." else sub.name
            canvas.drawText(displayName, 96f, rowY, paint)
            canvas.drawText(sub.category, 290f, rowY, paint)
            canvas.drawText("${sub.credits}", 338f, rowY, paint)
            canvas.drawText("${sub.conductedClasses}", 370f, rowY, paint)
            canvas.drawText("${sub.attendedClasses}", 412f, rowY, paint)

            val pct = if (sub.conductedClasses > 0) (sub.attendedClasses.toDouble() / sub.conductedClasses) * 100.0 else 0.0
            val subEligible = pct >= sub.targetPercentage

            titlePaint.textSize = 8.5f
            titlePaint.color = if (subEligible) greenSuccess else redDanger
            canvas.drawText("${String.format(Locale.US, "%.1f", pct)}%", 448f, rowY, titlePaint)

            val statText = if (subEligible) "Safe" else "Shortage"
            canvas.drawText(statText, 504f, rowY, titlePaint)

            currentY += 22f
        }

        currentY += 20f

        // 7. Academic Regulations Note
        paint.color = tableHeaderBg
        canvas.drawRoundRect(RectF(36f, currentY, 559f, currentY + 50f), 4f, 4f, paint)
        paint.color = textMuted
        paint.textSize = 7.5f
        canvas.drawText("OFFICIAL COMPLIANCE & UNDERTAKING CLAUSE:", 44f, currentY + 14f, paint)
        canvas.drawText("1. Minimum 75% attendance is required in each registered theory, practical, and elective course under university ordinances.", 44f, currentY + 26f, paint)
        canvas.drawText("2. Verified digital timestamp audit. This report reflects authenticated class logs recorded through AttendOS.", 44f, currentY + 38f, paint)

        currentY += 80f

        // 8. Signatures Section
        val sigLineY = currentY + 26f
        paint.color = textDark
        paint.strokeWidth = 0.8f
        canvas.drawLine(44f, sigLineY, 170f, sigLineY, paint)
        canvas.drawLine(230f, sigLineY, 360f, sigLineY, paint)
        canvas.drawLine(420f, sigLineY, 545f, sigLineY, paint)

        paint.textSize = 8f
        paint.color = textMuted
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Student Signature", 107f, sigLineY + 12f, paint)
        canvas.drawText("Faculty Advisor / Mentor", 295f, sigLineY + 12f, paint)
        canvas.drawText("Head of Department (HOD)", 482f, sigLineY + 12f, paint)

        // Footer
        paint.textSize = 7.5f
        paint.color = textMuted
        canvas.drawText("AttendOS • Cryptographic Local Record Verification • Page 1 of 1", 297.5f, 824f, paint)

        pdfDoc.finishPage(page)

        // Save PDF file to cache
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val pdfFile = File(exportDir, "DTU_Attendance_Report_${profile.rollNumber.replace("/", "_")}.pdf")
        val out = FileOutputStream(pdfFile)
        pdfDoc.writeTo(out)
        out.flush()
        out.close()
        pdfDoc.close()

        pdfFile
    }

    /**
     * Generates a fully-formatted CSV / Excel file
     */
    suspend fun exportAttendanceCsv(
        context: Context,
        profile: UserProfileEntity,
        semester: SemesterEntity?,
        subjects: List<SubjectEntity>,
        records: List<AttendanceRecordEntity>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val csvFile = File(exportDir, "DTU_Attendance_Sheet_${profile.rollNumber.replace("/", "_")}.csv")

        val sb = StringBuilder()
        // UTF-8 BOM for perfect Excel encoding
        sb.append("\uFEFF")

        // Metadata Header
        sb.append("OFFICIAL ATTENDANCE AUDIT SHEET\n")
        sb.append("University,\"${profile.college}\"\n")
        sb.append("Student Name,\"${profile.studentName}\"\n")
        sb.append("Roll Number,\"${profile.rollNumber}\"\n")
        sb.append("Branch,\"${profile.branch}\"\n")
        sb.append("Semester,\"${semester?.title ?: "Semester ${profile.semester}"}\"\n")
        sb.append("Generated At,\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\"\n\n")

        // Subjects Table
        sb.append("Course Code,Course Name,Category,Credits,Conducted Classes,Attended Classes,Attendance %,Target %,Eligibility Status\n")
        for (sub in subjects) {
            val pct = if (sub.conductedClasses > 0) (sub.attendedClasses.toDouble() / sub.conductedClasses) * 100.0 else 0.0
            val status = if (pct >= sub.targetPercentage) "Eligible" else "Debarment Risk"
            sb.append("\"${sub.code}\",\"${sub.name}\",\"${sub.category}\",${sub.credits},${sub.conductedClasses},${sub.attendedClasses},${String.format(Locale.US, "%.1f", pct)}%,${sub.targetPercentage}%,$status\n")
        }

        // Attendance Log Trail
        sb.append("\n\nDETAILED ATTENDANCE AUDIT LOGS\n")
        sb.append("Date,Time Slot,Subject ID,Status,Notes\n")
        for (rec in records.sortedByDescending { it.date }) {
            sb.append("${rec.date},\"${rec.slotTime}\",${rec.subjectId},${rec.status},\"${rec.note}\"\n")
        }

        FileOutputStream(csvFile).use { fos ->
            fos.write(sb.toString().toByteArray(Charsets.UTF_8))
        }

        csvFile
    }

    /**
     * Generates a complete portable JSON backup file
     */
    suspend fun exportJsonBackup(
        context: Context,
        profile: UserProfileEntity,
        semesters: List<SemesterEntity>,
        subjects: List<SubjectEntity>,
        slots: List<ScheduleSlotEntity>,
        records: List<AttendanceRecordEntity>,
        breaks: List<AcademicBreakEntity>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val backupFile = File(exportDir, "attendos_backup_$timeStamp.json")

        val root = JSONObject()
        root.put("app", "AttendOS")
        root.put("version", 4)
        root.put("exportTimestamp", System.currentTimeMillis())
        root.put("exportDateFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

        // Profile
        val profileObj = JSONObject().apply {
            put("studentName", profile.studentName)
            put("rollNumber", profile.rollNumber)
            put("college", profile.college)
            put("branch", profile.branch)
            put("semester", profile.semester)
            put("globalThreshold", profile.globalThreshold)
            put("globalEmergencyBuffer", profile.globalEmergencyBuffer)
            put("calculationMode", profile.calculationMode)
            put("midsemDate", profile.midsemDate)
            put("endsemDate", profile.endsemDate)
            put("semesterStartDate", profile.semesterStartDate)
        }
        root.put("profile", profileObj)

        // Semesters
        val semArray = JSONArray()
        for (s in semesters) {
            semArray.put(JSONObject().apply {
                put("id", s.id)
                put("semesterNumber", s.semesterNumber)
                put("title", s.title)
                put("academicYear", s.academicYear)
                put("startDate", s.startDate)
                put("endDate", s.endDate)
                put("isLocked", s.isLocked)
                put("isCurrent", s.isCurrent)
                put("lockedAtTimestamp", s.lockedAtTimestamp)
                put("finalAttendancePercentage", s.finalAttendancePercentage)
                put("totalConducted", s.totalConducted)
                put("totalAttended", s.totalAttended)
                put("notes", s.notes)
            })
        }
        root.put("semesters", semArray)

        // Subjects
        val subArray = JSONArray()
        for (sub in subjects) {
            subArray.put(JSONObject().apply {
                put("id", sub.id)
                put("semesterId", sub.semesterId)
                put("name", sub.name)
                put("code", sub.code)
                put("conductedClasses", sub.conductedClasses)
                put("attendedClasses", sub.attendedClasses)
                put("targetPercentage", sub.targetPercentage)
                put("emergencyBuffer", sub.emergencyBuffer)
                put("colorHex", sub.colorHex)
                put("defaultRoom", sub.defaultRoom)
                put("category", sub.category)
                put("credits", sub.credits)
            })
        }
        root.put("subjects", subArray)

        // Slots
        val slotArray = JSONArray()
        for (slot in slots) {
            slotArray.put(JSONObject().apply {
                put("id", slot.id)
                put("subjectId", slot.subjectId)
                put("dayOfWeek", slot.dayOfWeek)
                put("startTime", slot.startTime)
                put("endTime", slot.endTime)
                put("slotType", slot.slotType)
                put("location", slot.location)
                put("isCancelled", slot.isCancelled)
            })
        }
        root.put("schedule_slots", slotArray)

        // Records
        val recArray = JSONArray()
        for (rec in records) {
            recArray.put(JSONObject().apply {
                put("id", rec.id)
                put("subjectId", rec.subjectId)
                put("date", rec.date)
                put("slotTime", rec.slotTime)
                put("status", rec.status)
                put("timestamp", rec.timestamp)
                put("note", rec.note)
            })
        }
        root.put("attendance_records", recArray)

        // Breaks
        val breakArray = JSONArray()
        for (b in breaks) {
            breakArray.put(JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("startDate", b.startDate)
                put("endDate", b.endDate)
                put("category", b.category)
                put("description", b.description)
                put("affectsAllClasses", b.affectsAllClasses)
            })
        }
        root.put("academic_breaks", breakArray)

        FileOutputStream(backupFile).use { fos ->
            fos.write(root.toString(2).toByteArray(Charsets.UTF_8))
        }

        backupFile
    }

    /**
     * Safely restores all data from a JSON backup file
     */
    suspend fun restoreFromJson(
        context: Context,
        jsonString: String,
        db: AppDatabase
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("app") || root.getString("app") != "AttendOS") {
                return@withContext RestoreResult(success = false, errorMessage = "Invalid backup file: Not an AttendOS backup.")
            }

            var sCount = 0
            var subCount = 0
            var slotCount = 0
            var recCount = 0
            var breakCount = 0

            // Restore Semesters
            if (root.has("semesters")) {
                val semArray = root.getJSONArray("semesters")
                for (i in 0 until semArray.length()) {
                    val obj = semArray.getJSONObject(i)
                    val sem = SemesterEntity(
                        id = obj.optLong("id", 0L),
                        semesterNumber = obj.optInt("semesterNumber", 5),
                        title = obj.optString("title", "Semester"),
                        academicYear = obj.optString("academicYear", "2026-2027"),
                        startDate = obj.optString("startDate", "2026-08-01"),
                        endDate = obj.optString("endDate", "2026-12-15"),
                        isLocked = obj.optBoolean("isLocked", false),
                        isCurrent = obj.optBoolean("isCurrent", false),
                        lockedAtTimestamp = obj.optLong("lockedAtTimestamp", 0L),
                        finalAttendancePercentage = obj.optDouble("finalAttendancePercentage", 0.0),
                        totalConducted = obj.optInt("totalConducted", 0),
                        totalAttended = obj.optInt("totalAttended", 0),
                        notes = obj.optString("notes", "")
                    )
                    db.semesterDao().insertSemester(sem)
                    sCount++
                }
            }

            // Restore Subjects
            if (root.has("subjects")) {
                val subArray = root.getJSONArray("subjects")
                for (i in 0 until subArray.length()) {
                    val obj = subArray.getJSONObject(i)
                    val sub = SubjectEntity(
                        id = obj.optLong("id", 0L),
                        semesterId = obj.optLong("semesterId", 1L),
                        name = obj.getString("name"),
                        code = obj.optString("code", ""),
                        conductedClasses = obj.optInt("conductedClasses", 0),
                        attendedClasses = obj.optInt("attendedClasses", 0),
                        targetPercentage = obj.optDouble("targetPercentage", 75.0),
                        emergencyBuffer = obj.optInt("emergencyBuffer", 3),
                        colorHex = obj.optString("colorHex", "#00E5FF"),
                        defaultRoom = obj.optString("defaultRoom", "Pragya Bhavan"),
                        category = obj.optString("category", "Core"),
                        credits = obj.optInt("credits", 4)
                    )
                    db.subjectDao().insertSubject(sub)
                    subCount++
                }
            }

            // Restore Slots
            if (root.has("schedule_slots")) {
                val slotArray = root.getJSONArray("schedule_slots")
                for (i in 0 until slotArray.length()) {
                    val obj = slotArray.getJSONObject(i)
                    val slot = ScheduleSlotEntity(
                        id = obj.optLong("id", 0L),
                        subjectId = obj.getLong("subjectId"),
                        dayOfWeek = obj.getInt("dayOfWeek"),
                        startTime = obj.getString("startTime"),
                        endTime = obj.getString("endTime"),
                        slotType = obj.optString("slotType", "Lecture"),
                        location = obj.optString("location", "Campus Hall"),
                        isCancelled = obj.optBoolean("isCancelled", false)
                    )
                    db.scheduleSlotDao().insertSlot(slot)
                    slotCount++
                }
            }

            // Restore Records
            if (root.has("attendance_records")) {
                val recArray = root.getJSONArray("attendance_records")
                for (i in 0 until recArray.length()) {
                    val obj = recArray.getJSONObject(i)
                    val rec = AttendanceRecordEntity(
                        id = obj.optLong("id", 0L),
                        subjectId = obj.getLong("subjectId"),
                        date = obj.getString("date"),
                        slotTime = obj.optString("slotTime", ""),
                        status = obj.getString("status"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        note = obj.optString("note", "")
                    )
                    db.attendanceRecordDao().insertRecord(rec)
                    recCount++
                }
            }

            // Restore Breaks
            if (root.has("academic_breaks")) {
                val breakArray = root.getJSONArray("academic_breaks")
                for (i in 0 until breakArray.length()) {
                    val obj = breakArray.getJSONObject(i)
                    val b = AcademicBreakEntity(
                        id = obj.optLong("id", 0L),
                        title = obj.getString("title"),
                        startDate = obj.getString("startDate"),
                        endDate = obj.getString("endDate"),
                        category = obj.optString("category", "HOLIDAY"),
                        description = obj.optString("description", ""),
                        affectsAllClasses = obj.optBoolean("affectsAllClasses", true)
                    )
                    db.academicBreakDao().insertBreak(b)
                    breakCount++
                }
            }

            RestoreResult(
                success = true,
                semestersCount = sCount,
                subjectsCount = subCount,
                slotsCount = slotCount,
                recordsCount = recCount,
                breaksCount = breakCount
            )
        } catch (e: Exception) {
            RestoreResult(success = false, errorMessage = e.localizedMessage ?: "Unknown restore error")
        }
    }

    /**
     * Shares a file via Android Intent Chooser
     */
    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun generateAndSharePdfReport(
        context: Context,
        subjects: List<SubjectEntity>,
        records: List<AttendanceRecordEntity>,
        profile: UserProfileEntity?,
        semester: SemesterEntity?,
        statsList: List<SubjectStats> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val prof = profile ?: UserProfileEntity(
            studentName = "University Student",
            rollNumber = "2K23/SE/001",
            college = "Delhi Technological University",
            branch = "Software Engineering",
            semester = semester?.semesterNumber ?: 5
        )
        val file = exportAttendancePdf(
            context = context,
            profile = prof,
            semester = semester,
            subjects = subjects,
            statsList = statsList
        )
        withContext(Dispatchers.Main) {
            shareFile(context, file, "application/pdf", "Share Official Attendance PDF Report")
        }
    }

    suspend fun generateAndShareAttendanceCsv(
        context: Context,
        subjects: List<SubjectEntity>,
        records: List<AttendanceRecordEntity>,
        profile: UserProfileEntity? = null,
        semester: SemesterEntity? = null
    ) = withContext(Dispatchers.IO) {
        val prof = profile ?: UserProfileEntity(
            studentName = "University Student",
            rollNumber = "2K23/SE/001",
            college = "Delhi Technological University",
            branch = "Software Engineering",
            semester = semester?.semesterNumber ?: 5
        )
        val file = exportAttendanceCsv(
            context = context,
            profile = prof,
            semester = semester,
            subjects = subjects,
            records = records
        )
        withContext(Dispatchers.Main) {
            shareFile(context, file, "text/csv", "Share Attendance CSV / Excel Audit Sheet")
        }
    }

    suspend fun exportAndShareFullBackupJson(
        context: Context,
        database: AppDatabase
    ) = withContext(Dispatchers.IO) {
        val profile = database.userProfileDao().getProfile() ?: UserProfileEntity(
            studentName = "DTU Student",
            rollNumber = "2K23/SE/001",
            college = "Delhi Technological University",
            branch = "Software Engineering"
        )
        val semesters = database.semesterDao().getAllSemestersList()
        val subjects = database.subjectDao().getAllSubjectsList()
        val slots = database.scheduleSlotDao().getAllSlotsList()
        val records = database.attendanceRecordDao().getAllRecordsList()
        val breaks = database.academicBreakDao().getAllBreaksList()

        val file = exportJsonBackup(
            context = context,
            profile = profile,
            semesters = semesters,
            subjects = subjects,
            slots = slots,
            records = records,
            breaks = breaks
        )
        withContext(Dispatchers.Main) {
            shareFile(context, file, "application/json", "Share AttendOS Encrypted Backup File")
        }
    }

    suspend fun restoreFromBackupJson(
        context: Context,
        database: AppDatabase,
        jsonContent: String
    ): RestoreResult = withContext(Dispatchers.IO) {
        restoreFromJson(context, jsonContent, database)
    }
}

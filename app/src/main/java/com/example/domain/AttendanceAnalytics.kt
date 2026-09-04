package com.example.domain

import androidx.compose.ui.graphics.Color
import com.example.data.local.entity.SubjectEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class CalculationTimeframe(
    val id: String,
    val title: String,
    val shortLabel: String,
    val badgeLabel: String,
    val description: String
) {
    NEXT_EXAM(
        id = "NEXT_EXAM",
        title = "Till Next Exam (Midsem)",
        shortLabel = "Next Exam",
        badgeLabel = "🎯 TILL MIDSEM CUTOFF",
        description = "Calculations target exam eligibility up to 2 days before Midsem exams."
    ),
    FULL_SEMESTER(
        id = "FULL_SEMESTER",
        title = "Semester Analysis (Endsem)",
        shortLabel = "Full Semester",
        badgeLabel = "🎓 FULL SEMESTER (ENDSEM)",
        description = "Calculations target final semester eligibility up to 2 days before Endsem exams."
    );

    companion object {
        fun fromId(id: String?): CalculationTimeframe {
            return if (id?.equals("FULL_SEMESTER", ignoreCase = true) == true) {
                FULL_SEMESTER
            } else {
                NEXT_EXAM
            }
        }
    }
}

enum class AttendanceTier(
    val levelName: String,
    val description: String,
    val color: Color,
    val badgeLabel: String,
    val isDanger: Boolean
) {
    ELITE(
        levelName = "Elite Champion (>=95%)",
        description = "Ultimate tier! Maximum safety buffer and complete immunity.",
        color = TierEmeraldColor,
        badgeLabel = "💎 ELITE 95+",
        isDanger = false
    ),
    EXCELLENT(
        levelName = "Excellent Standing (90-94%)",
        description = "Outstanding attendance. Huge buffer available.",
        color = Color(0xFF00E5FF),
        badgeLabel = "🌟 EXCELLENT 90+",
        isDanger = false
    ),
    HIGH_BUFFER(
        levelName = "High Buffer (85-89%)",
        description = "Comfortably safe. Emergency buffer fully intact.",
        color = TierMintColor,
        badgeLabel = "🛡️ BUFFER 85+",
        isDanger = false
    ),
    SAFE_ZONE(
        levelName = "Safe Zone (80-84%)",
        description = "Good standing. 1-2 skips possible safely.",
        color = TierGoldColor,
        badgeLabel = "✨ SAFE 80+",
        isDanger = false
    ),
    BORDERLINE(
        levelName = "Borderline (75-79%)",
        description = "Right on the 75% exam eligibility threshold. Do not skip carelessly.",
        color = TierWarningColor,
        badgeLabel = "⚠️ 75%+ BORDERLINE",
        isDanger = false
    ),
    SHORTAGE(
        levelName = "Shortage Alert (70-74%)",
        description = "Below exam criteria! Immediate consecutive attendance required.",
        color = Color(0xFFF97316),
        badgeLabel = "🚨 SHORTAGE (<75%)",
        isDanger = true
    ),
    CRITICAL(
        levelName = "Critical Deficit (<70%)",
        description = "Emergency deficit! High debarment risk. Mandatory attendance.",
        color = TierSevereColor,
        badgeLabel = "🔥 CRITICAL DEFICIT",
        isDanger = true
    );

    companion object {
        // Backward compatibility mappings
        val EMERALD get() = ELITE
        val MINT get() = HIGH_BUFFER
        val GOLD get() = SAFE_ZONE
        val WARNING get() = BORDERLINE
        val SHORT_1 get() = SHORTAGE
        val SHORT_2 get() = CRITICAL
    }
}

data class SubjectStats(
    val subject: SubjectEntity,
    val percentage: Double,
    val tier: AttendanceTier,
    val isEligible: Boolean,
    val classesToReachTarget: Int,
    val classesToReachNextLevel: Int,
    val nextLevelTarget: Double,
    val rawBunkCapacity: Int,
    val safeBunkWithBuffer: Int,
    val effectiveBufferUsed: Int,
    val statusMessage: String,
    val urgencyLevel: Int, // 1 (Relaxed) to 5 (Ultra Critical)
    // Milestone Specific Metrics
    val timeframe: CalculationTimeframe = CalculationTimeframe.NEXT_EXAM,
    val cutoffDateStr: String = "",
    val remainingClassesInWindow: Int = 0,
    val totalProjectedInWindow: Int = 0,
    val mustAttendInWindow: Int = 0,
    val safeBunksInWindow: Int = 0,
    val rawBunksInWindow: Int = 0,
    val projectedMaxPercentage: Double = 100.0,
    val isDebarmentRiskAtMilestone: Boolean = false
) {
    val milestoneName: String get() = timeframe.shortLabel
}

object AttendanceCalculator {

    fun getTier(percentage: Double, target: Double = 75.0): AttendanceTier {
        return when {
            percentage >= 95.0 -> AttendanceTier.ELITE
            percentage >= 90.0 -> AttendanceTier.EXCELLENT
            percentage >= 85.0 -> AttendanceTier.HIGH_BUFFER
            percentage >= 80.0 -> AttendanceTier.SAFE_ZONE
            percentage >= target -> AttendanceTier.BORDERLINE
            percentage >= (target - 5.0) -> AttendanceTier.SHORTAGE
            else -> AttendanceTier.CRITICAL
        }
    }

    /**
     * Primary calculation engine supporting both:
     * 1. Till Next Exam (e.g. Midsem - 2 days cutoff)
     * 2. Full Semester Analysis (e.g. Endsem - 2 days cutoff)
     */
    fun calculateStats(
        subject: SubjectEntity,
        globalTarget: Double = 75.0,
        globalBuffer: Int = 3,
        includeBuffer: Boolean = true,
        timeframe: CalculationTimeframe = CalculationTimeframe.NEXT_EXAM,
        examDateStr: String = "2026-10-15",
        leadDays: Int = 2,
        weeklyClassCount: Int = 3
    ): SubjectStats {
        val conducted = max(0, subject.conductedClasses)
        val attended = max(0, min(conducted, subject.attendedClasses))
        val target = if (subject.targetPercentage > 0) subject.targetPercentage else globalTarget
        val buffer = if (subject.emergencyBuffer > 0) subject.emergencyBuffer else globalBuffer

        val percentage = if (conducted == 0) {
            100.0
        } else {
            (attended.toDouble() / conducted.toDouble()) * 100.0
        }

        val tier = getTier(percentage, target)
        val isEligible = percentage >= target

        // 1. Calculate Cutoff Date and Remaining Weeks/Classes till Cutoff
        val cutoffInfo = calculateCutoffInfo(examDateStr, leadDays, weeklyClassCount)
        val remainingClassesInWindow = cutoffInfo.remainingClasses
        val cutoffFormatted = cutoffInfo.formattedCutoffDate
        val totalProjectedInWindow = conducted + remainingClassesInWindow

        // 2. Target calculations in the milestone window
        val totalRequiredAttendedInWindow = ceil((target / 100.0) * totalProjectedInWindow).toInt()
        val mustAttendInWindow = max(0, totalRequiredAttendedInWindow - attended)
        
        // Debarment risk if even attending all remaining classes cannot reach required target
        val isDebarmentRiskAtMilestone = (attended + remainingClassesInWindow) < totalRequiredAttendedInWindow
        
        // Projected Maximum percentage (if student attends 100% of remaining classes)
        val projectedMaxPercentage = if (totalProjectedInWindow == 0) 100.0 else {
            ((attended + remainingClassesInWindow).toDouble() / totalProjectedInWindow.toDouble()) * 100.0
        }

        // Bunks allowable in window till cutoff
        val rawBunksInWindow = max(0, remainingClassesInWindow - mustAttendInWindow)
        val safeBunksInWindow = if (includeBuffer) {
            max(0, rawBunksInWindow - buffer)
        } else {
            rawBunksInWindow
        }

        // 3. Traditional Instant Rolling Calculations (for immediate step feedback)
        val nextLevelTarget = when {
            percentage < target -> target
            percentage < 80.0 -> 80.0
            percentage < 85.0 -> 85.0
            percentage < 90.0 -> 90.0
            percentage < 95.0 -> 95.0
            else -> 100.0
        }

        val classesToReachTargetImmediate = if (percentage >= target) {
            0
        } else {
            val reqNormal = calculateClassesToAttend(attended, conducted, target)
            if (includeBuffer && buffer > 0) {
                calculateClassesToAttendWithBuffer(attended, conducted, target, buffer)
            } else {
                reqNormal
            }
        }

        val classesToReachNextLevel = if (percentage >= 100.0 || (percentage >= target && nextLevelTarget <= percentage)) {
            0
        } else {
            calculateClassesToAttend(attended, conducted, nextLevelTarget)
        }

        val rawBunkCapacityImmediate = if (percentage >= target) {
            calculateBunkCapacity(attended, conducted, target)
        } else {
            0
        }

        val safeBunkWithBufferImmediate = if (includeBuffer) {
            max(0, rawBunkCapacityImmediate - buffer)
        } else {
            rawBunkCapacityImmediate
        }

        // Combine milestone-aware stats
        val finalClassesToReachTarget = if (remainingClassesInWindow > 0) {
            mustAttendInWindow
        } else {
            classesToReachTargetImmediate
        }

        val finalSafeBunk = if (remainingClassesInWindow > 0) {
            safeBunksInWindow
        } else {
            safeBunkWithBufferImmediate
        }

        val finalRawBunk = if (remainingClassesInWindow > 0) {
            rawBunksInWindow
        } else {
            rawBunkCapacityImmediate
        }

        // Status Message construction
        val timeframeLabel = if (timeframe == CalculationTimeframe.NEXT_EXAM) "Next Exam (Midsem)" else "Full Semester (Endsem)"
        val cutoffNote = "Target: $timeframeLabel [Cutoff: $cutoffFormatted]"

        val (statusMsg, urgency) = when {
            isDebarmentRiskAtMilestone -> {
                Pair("🔥 CRITICAL DEBARMENT RISK! Even attending all $remainingClassesInWindow remaining classes before $timeframeLabel only reaches ${projectedMaxPercentage.formatOneDecimal()}%. Immediate duty leave or professor consultation required!", 5)
            }
            percentage >= 95.0 -> {
                val bunkText = if (includeBuffer) "You can safely bunk $finalSafeBunk classes (preserving $buffer emergency leaves)" else "You can bunk $finalRawBunk classes"
                Pair("💎 ELITE 95%+ Standing! $bunkText out of $remainingClassesInWindow remaining before $timeframeLabel without falling below $target%.", 1)
            }
            percentage >= 90.0 -> {
                val bunkText = if (includeBuffer) "Safe to bunk $finalSafeBunk classes (with $buffer buffer reserved)" else "Safe to bunk $finalRawBunk classes"
                Pair("🌟 Excellent standing (90%+). $cutoffNote: Attend $finalClassesToReachTarget of $remainingClassesInWindow remaining to secure $target%. $bunkText.", 1)
            }
            percentage >= 85.0 -> {
                val bunkText = if (includeBuffer) "Safe to bunk $finalSafeBunk classes ($buffer buffer reserved)" else "Safe to bunk $finalRawBunk classes"
                Pair("🛡️ Solid 85%+ buffer. $cutoffNote: Must attend $finalClassesToReachTarget of $remainingClassesInWindow remaining classes. $bunkText.", 2)
            }
            percentage >= 80.0 -> {
                Pair("✨ Good zone (80%+). $cutoffNote: Attend $finalClassesToReachTarget of $remainingClassesInWindow classes to finish above $target%. Max safe bunk: $finalSafeBunk.", 2)
            }
            percentage >= target -> {
                Pair("⚠️ Borderline (75-79%)! $cutoffNote: Must attend $finalClassesToReachTarget of $remainingClassesInWindow remaining classes. Keep bunks to zero.", 3)
            }
            percentage >= (target - 5.0) -> {
                Pair("🚨 Shortage (<75%)! Short by ${(target - percentage).formatOneDecimal()}%. $cutoffNote: Must attend $finalClassesToReachTarget of $remainingClassesInWindow remaining classes to sit in exams!", 4)
            }
            else -> {
                Pair("🔥 CRITICAL DEFICIT (<70%)! $cutoffNote: Must attend $finalClassesToReachTarget of $remainingClassesInWindow remaining classes without fail to qualify!", 5)
            }
        }

        return SubjectStats(
            subject = subject,
            percentage = percentage,
            tier = tier,
            isEligible = isEligible,
            classesToReachTarget = finalClassesToReachTarget,
            classesToReachNextLevel = classesToReachNextLevel,
            nextLevelTarget = nextLevelTarget,
            rawBunkCapacity = finalRawBunk,
            safeBunkWithBuffer = finalSafeBunk,
            effectiveBufferUsed = buffer,
            statusMessage = statusMsg,
            urgencyLevel = urgency,
            timeframe = timeframe,
            cutoffDateStr = cutoffFormatted,
            remainingClassesInWindow = remainingClassesInWindow,
            totalProjectedInWindow = totalProjectedInWindow,
            mustAttendInWindow = mustAttendInWindow,
            safeBunksInWindow = safeBunksInWindow,
            rawBunksInWindow = rawBunksInWindow,
            projectedMaxPercentage = projectedMaxPercentage,
            isDebarmentRiskAtMilestone = isDebarmentRiskAtMilestone
        )
    }

    data class CutoffInfo(
        val remainingClasses: Int,
        val formattedCutoffDate: String,
        val daysUntilCutoff: Int
    )

    fun calculateCutoffInfo(
        examDateStr: String,
        leadDays: Int = 2,
        weeklyClassCount: Int = 3
    ): CutoffInfo {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val examDate = sdf.parse(examDateStr.trim()) ?: Date()
            val calCutoff = Calendar.getInstance().apply {
                time = examDate
                add(Calendar.DAY_OF_YEAR, -max(0, leadDays))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
            }

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            }

            val diffMillis = calCutoff.timeInMillis - today.timeInMillis
            val diffDays = max(0, (diffMillis / (1000 * 60 * 60 * 24)).toInt())
            val remainingWeeks = max(1, diffDays / 7)
            val remainingClasses = if (diffDays <= 0) 0 else max(1, (diffDays * weeklyClassCount) / 7)

            val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val formattedDate = displayFormat.format(calCutoff.time)

            CutoffInfo(
                remainingClasses = remainingClasses,
                formattedCutoffDate = formattedDate,
                daysUntilCutoff = diffDays
            )
        } catch (e: Exception) {
            CutoffInfo(
                remainingClasses = 18,
                formattedCutoffDate = "Cutoff Date",
                daysUntilCutoff = 42
            )
        }
    }

    fun calculateClassesToAttend(attended: Int, conducted: Int, target: Double): Int {
        if (target >= 100.0) {
            return if (attended < conducted) 99 else 0
        }
        val num = (target * conducted) - (100.0 * attended)
        val den = 100.0 - target
        if (den <= 0) return 0
        val result = ceil(num / den).toInt()
        return max(0, result)
    }

    fun calculateClassesToAttendWithBuffer(attended: Int, conducted: Int, target: Double, buffer: Int): Int {
        val num = (target * (conducted + buffer)) - (100.0 * attended)
        val den = 100.0 - target
        if (den <= 0) return 0
        val result = ceil(num / den).toInt()
        return max(0, result)
    }

    fun calculateBunkCapacity(attended: Int, conducted: Int, target: Double): Int {
        if (target <= 0.0) return 99
        val num = (100.0 * attended) - (target * conducted)
        if (num <= 0) return 0
        val result = floor(num / target).toInt()
        return max(0, result)
    }

    private fun Double.formatOneDecimal(): String {
        return try {
            String.format(Locale.US, "%.1f", this)
        } catch (e: Exception) {
            "$this"
        }
    }
}

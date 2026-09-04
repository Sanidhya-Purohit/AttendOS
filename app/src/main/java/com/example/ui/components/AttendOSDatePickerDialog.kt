package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendOSDatePickerDialog(
    title: String = "Select Date",
    initialDate: String = "",
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentCal = remember { Calendar.getInstance() }
    val currentYear = remember { currentCal.get(Calendar.YEAR) }

    // Provide 6 years: for 2026, calendar covers from 2021 to 2027 (currentYear - 5 .. currentYear + 1)
    val startYear = currentYear - 5
    val endYear = currentYear + 1

    val minSelectableMillis = remember(startYear) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, startYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Max selectable date: December 31 of endYear (e.g. 2027)
    val maxSelectableMillis = remember(endYear) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, endYear)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    val initialMillis = try {
        if (initialDate.isNotBlank()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = sdf.parse(initialDate)?.time ?: System.currentTimeMillis()
            parsed.coerceIn(minSelectableMillis, maxSelectableMillis)
        } else {
            System.currentTimeMillis().coerceIn(minSelectableMillis, maxSelectableMillis)
        }
    } catch (e: Exception) {
        System.currentTimeMillis().coerceIn(minSelectableMillis, maxSelectableMillis)
    }

    val selectableDatesConstraint = remember(minSelectableMillis, maxSelectableMillis, startYear, endYear) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis in minSelectableMillis..maxSelectableMillis
            }

            override fun isSelectableYear(year: Int): Boolean {
                return year in startYear..endYear
            }
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        yearRange = startYear..endYear,
        selectableDates = selectableDatesConstraint
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = millis
                        }
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        val formattedDate = sdf.format(cal.time)
                        onDateSelected(formattedDate)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.primary,
                    contentColor = if (AppTheme.colors.isDark) Color(0xFF130E24) else Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_confirm_calendar_date")
            ) {
                Text("Confirm Date", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_calendar_date")
            ) {
                Text("Cancel", color = AppTheme.colors.textSecondary)
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = DatePickerDefaults.colors(
            containerColor = AppTheme.colors.surfaceCard
        ),
        modifier = Modifier.testTag("dialog_attendos_datepicker")
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                )
            },
            colors = DatePickerDefaults.colors(
                containerColor = AppTheme.colors.surfaceCard,
                titleContentColor = AppTheme.colors.textPrimary,
                headlineContentColor = AppTheme.colors.primary,
                weekdayContentColor = AppTheme.colors.textMuted,
                subheadContentColor = AppTheme.colors.textSecondary,
                yearContentColor = AppTheme.colors.textPrimary,
                currentYearContentColor = AppTheme.colors.primary,
                selectedYearContentColor = if (AppTheme.colors.isDark) Color(0xFF130E24) else Color.White,
                selectedYearContainerColor = AppTheme.colors.primary,
                dayContentColor = AppTheme.colors.textPrimary,
                selectedDayContentColor = if (AppTheme.colors.isDark) Color(0xFF130E24) else Color.White,
                selectedDayContainerColor = AppTheme.colors.primary,
                todayContentColor = AppTheme.colors.primary,
                todayDateBorderColor = AppTheme.colors.primary
            )
        )
    }
}

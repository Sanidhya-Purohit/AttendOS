package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Universal interactive Date Selector Card.
 * Tapping this card immediately displays the graphical Material 3 Calendar Date Picker dialog,
 * guaranteeing students never have to manually type date strings.
 */
@Composable
fun AttendOSDateSelectorCard(
    label: String,
    selectedDate: String, // "YYYY-MM-DD"
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    pickerTitle: String = "Select Date",
    enabled: Boolean = true
) {
    var isPickerOpen by remember { mutableStateOf(false) }

    val formattedDisplayDate = remember(selectedDate) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = parser.parse(selectedDate) ?: Date()
            val formatter = SimpleDateFormat("EEE, d MMM yyyy", Locale.US)
            formatter.format(d)
        } catch (e: Exception) {
            selectedDate
        }
    }

    Surface(
        color = AppTheme.colors.surfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppTheme.colors.border),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { isPickerOpen = true }
            .testTag("date_selector_card_${label.lowercase().replace(" ", "_")}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.primary.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Pick date",
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = label.uppercase(Locale.getDefault()),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.colors.primary,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDisplayDate,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                }
            }

            // Interactive "Pick Date" Action Chip
            Surface(
                color = AppTheme.colors.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.EditCalendar,
                        contentDescription = null,
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Calendar",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.primary
                    )
                }
            }
        }
    }

    if (isPickerOpen) {
        AttendOSDatePickerDialog(
            title = pickerTitle,
            initialDate = selectedDate,
            onDateSelected = { newDate ->
                onDateSelected(newDate)
                isPickerOpen = false
            },
            onDismiss = { isPickerOpen = false }
        )
    }
}

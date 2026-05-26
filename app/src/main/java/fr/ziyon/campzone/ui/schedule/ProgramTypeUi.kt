package fr.ziyon.campzone.ui.schedule

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.EmojiPeople
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.model.ScheduleReminderTiming
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ProgramType.displayName: String
    get() = when (this) {
        ProgramType.Reception -> "Reception"
        ProgramType.Games -> "Games"
        ProgramType.Preaching -> "Preaching"
        ProgramType.Prayer -> "Prayer"
        ProgramType.Breakfast -> "Breakfast"
        ProgramType.Lunch -> "Lunch"
        ProgramType.Dinner -> "Dinner"
        ProgramType.Snack -> "Snack"
        ProgramType.Other -> "Other"
        ProgramType.Rest -> "Rest"
        ProgramType.Break -> "Break"
        ProgramType.Custom -> "Custom"
    }

val ProgramType.icon: ImageVector
    get() = when (this) {
        ProgramType.Reception -> Icons.Rounded.EmojiPeople
        ProgramType.Games -> Icons.Rounded.SportsEsports
        ProgramType.Preaching -> Icons.Rounded.RecordVoiceOver
        ProgramType.Prayer -> Icons.Rounded.Favorite
        ProgramType.Breakfast -> Icons.Rounded.FreeBreakfast
        ProgramType.Lunch -> Icons.Rounded.Restaurant
        ProgramType.Dinner -> Icons.Rounded.DinnerDining
        ProgramType.Snack -> Icons.Rounded.LocalCafe
        ProgramType.Other -> Icons.Rounded.MoreHoriz
        ProgramType.Rest -> Icons.Rounded.Bedtime
        ProgramType.Break -> Icons.Rounded.Coffee
        ProgramType.Custom -> Icons.Rounded.Star
    }

val ProgramType.accentColor: Color
    get() = when (this) {
        ProgramType.Reception -> Color(0xFFD97706)
        ProgramType.Games -> Color(0xFF3B82F6)
        ProgramType.Preaching -> Color(0xFFFF6B35)
        ProgramType.Prayer -> Color(0xFF8B5CF6)
        ProgramType.Breakfast -> Color(0xFFFF8C00)
        ProgramType.Lunch -> Color(0xFF4A7C59)
        ProgramType.Dinner -> Color(0xFF1D4ED8)
        ProgramType.Snack -> Color(0xFFFFB347)
        ProgramType.Other -> Color(0xFF6B6052)
        ProgramType.Rest -> Color(0xFF4F46E5)
        ProgramType.Break -> Color(0xFF6B6052)
        ProgramType.Custom -> Color(0xFFFF6B35)
    }

val ScheduleReminderTiming.displayName: String
    get() = when (this) {
        ScheduleReminderTiming.None -> "None"
        ScheduleReminderTiming.AtStart -> "At start"
        ScheduleReminderTiming.FiveMinutes -> "5 min before"
        ScheduleReminderTiming.FifteenMinutes -> "15 min before"
        ScheduleReminderTiming.ThirtyMinutes -> "30 min before"
        ScheduleReminderTiming.OneHour -> "1 hour before"
    }

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayTitleFormatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
private val weekdayFormatter = SimpleDateFormat("EEE", Locale.getDefault())
private val dayNumberFormatter = SimpleDateFormat("d", Locale.getDefault())

fun Date.programTimeText(): String = timeFormatter.format(this)

fun CampDay.dateTitle(): String = dayTitleFormatter.format(date)

fun CampDay.weekdayText(): String = weekdayFormatter.format(date)

fun CampDay.dayNumberText(): String = dayNumberFormatter.format(date)

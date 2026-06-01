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
import fr.ziyon.campzone.R
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.model.ScheduleReminderTiming
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ProgramType.displayNameRes: Int
    get() = when (this) {
        ProgramType.Reception -> R.string.program_type_reception
        ProgramType.Games -> R.string.program_type_games
        ProgramType.Preaching -> R.string.program_type_preaching
        ProgramType.Prayer -> R.string.program_type_prayer
        ProgramType.Breakfast -> R.string.program_type_breakfast
        ProgramType.Lunch -> R.string.program_type_lunch
        ProgramType.Dinner -> R.string.program_type_dinner
        ProgramType.Snack -> R.string.program_type_snack
        ProgramType.Other -> R.string.program_type_other
        ProgramType.Rest -> R.string.program_type_rest
        ProgramType.Break -> R.string.program_type_break
        ProgramType.Custom -> R.string.program_type_custom
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

val ScheduleReminderTiming.displayNameRes: Int
    get() = when (this) {
        ScheduleReminderTiming.None -> R.string.schedule_reminder_none
        ScheduleReminderTiming.AtStart -> R.string.schedule_reminder_at_start
        ScheduleReminderTiming.FiveMinutes -> R.string.schedule_reminder_five_minutes
        ScheduleReminderTiming.FifteenMinutes -> R.string.schedule_reminder_fifteen_minutes
        ScheduleReminderTiming.ThirtyMinutes -> R.string.schedule_reminder_thirty_minutes
        ScheduleReminderTiming.OneHour -> R.string.schedule_reminder_one_hour
    }

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayTitleFormatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
private val weekdayFormatter = SimpleDateFormat("EEE", Locale.getDefault())
private val dayNumberFormatter = SimpleDateFormat("d", Locale.getDefault())

fun Date.programTimeText(): String = timeFormatter.format(this)

fun CampDay.dateTitle(): String = dayTitleFormatter.format(date)

fun CampDay.weekdayText(): String = weekdayFormatter.format(date)

fun CampDay.dayNumberText(): String = dayNumberFormatter.format(date)

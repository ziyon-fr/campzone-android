package fr.ziyon.campzone.ui.schedule

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EmojiPeople
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import fr.ziyon.campzone.R
import fr.ziyon.campzone.data.model.CustomProgramType
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.Program
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

val Program.resolvedIcon: ImageVector
    get() = customType?.symbol?.programSymbolIcon ?: type.icon

val Program.resolvedAccentColor: Color
    get() = customType?.colorHex?.toScheduleColorOrNull() ?: type.accentColor

val Program.resolvedTypeNameRes: Int
    get() = type.displayNameRes

val String.programSymbolIcon: ImageVector
    get() = when (this) {
        "sparkles" -> Icons.Rounded.AutoAwesome
        "star.fill" -> Icons.Rounded.Star
        "flame.fill" -> Icons.Rounded.LocalFireDepartment
        "heart.fill" -> Icons.Rounded.Favorite
        "bolt.fill" -> Icons.Rounded.Bolt
        "music.note" -> Icons.Rounded.MusicNote
        "mic.fill" -> Icons.Rounded.Mic
        "theatermasks.fill" -> Icons.Rounded.TheaterComedy
        "paintpalette.fill" -> Icons.Rounded.Palette
        "book.fill" -> Icons.Rounded.MenuBook
        "graduationcap.fill" -> Icons.Rounded.School
        "pencil" -> Icons.Rounded.Edit
        "figure.run" -> Icons.Rounded.DirectionsRun
        "figure.walk" -> Icons.Rounded.DirectionsWalk
        "sportscourt.fill" -> Icons.Rounded.SportsBasketball
        "soccerball" -> Icons.Rounded.SportsSoccer
        "bicycle" -> Icons.Rounded.DirectionsBike
        "tent.fill" -> Icons.Rounded.Forest
        "mountain.2.fill" -> Icons.Rounded.Landscape
        "tree.fill" -> Icons.Rounded.Forest
        "leaf.fill" -> Icons.Rounded.Eco
        "sun.max.fill" -> Icons.Rounded.WbSunny
        "moon.stars.fill" -> Icons.Rounded.Nightlight
        "cloud.sun.fill" -> Icons.Rounded.Cloud
        "drop.fill" -> Icons.Rounded.WaterDrop
        "fork.knife" -> Icons.Rounded.Restaurant
        "cup.and.saucer.fill" -> Icons.Rounded.FreeBreakfast
        "gift.fill" -> Icons.Rounded.CardGiftcard
        "camera.fill" -> Icons.Rounded.PhotoCamera
        "film.fill" -> Icons.Rounded.Movie
        "megaphone.fill" -> Icons.Rounded.Campaign
        "hands.sparkles.fill" -> Icons.Rounded.BackHand
        "cross.fill" -> Icons.Rounded.Church
        "bell.fill" -> Icons.Rounded.Notifications
        "flag.fill" -> Icons.Rounded.Flag
        "trophy.fill" -> Icons.Rounded.EmojiEvents
        "map.fill" -> Icons.Rounded.Map
        "binoculars.fill" -> Icons.Rounded.TravelExplore
        "backpack.fill" -> Icons.Rounded.Backpack
        "globe.americas.fill" -> Icons.Rounded.Public
        else -> Icons.Rounded.AutoAwesome
    }

object CustomProgramTypeOptions {
    val symbols: List<String> = listOf(
        "sparkles", "star.fill", "flame.fill", "heart.fill", "bolt.fill",
        "music.note", "mic.fill", "theatermasks.fill", "paintpalette.fill", "book.fill",
        "graduationcap.fill", "pencil", "figure.run", "figure.walk", "sportscourt.fill",
        "soccerball", "bicycle", "tent.fill", "mountain.2.fill", "tree.fill",
        "leaf.fill", "sun.max.fill", "moon.stars.fill", "cloud.sun.fill", "drop.fill",
        "fork.knife", "cup.and.saucer.fill", "gift.fill", "camera.fill", "film.fill",
        "megaphone.fill", "hands.sparkles.fill", "cross.fill", "bell.fill", "flag.fill",
        "trophy.fill", "map.fill", "binoculars.fill", "backpack.fill", "globe.americas.fill",
    )

    val palette: List<String> = listOf(
        "#E2582B", "#F59E0B", "#D7263D", "#C2185B", "#7B3FA0", "#3F51B5",
        "#1565C0", "#0EA5E9", "#00897B", "#2E7D32", "#6D4C41", "#546E7A",
    )
}

fun String.toScheduleColorOrNull(): Color? = runCatching {
    Color(android.graphics.Color.parseColor(this))
}.getOrNull()

fun CustomProgramType.color(): Color =
    colorHex.toScheduleColorOrNull() ?: CustomProgramType.FallbackColorHex.toScheduleColorOrNull() ?: Color(0xFF8D6E63)

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

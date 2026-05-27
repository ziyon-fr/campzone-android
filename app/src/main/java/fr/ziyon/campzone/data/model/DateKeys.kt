package fr.ziyon.campzone.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Deterministic document-ID date keys (`07-data-contract-rules.md` §4). The
 * date component is formatted with the **gregorian** calendar, **en_US_POSIX**
 * locale, and the **local** time zone - replicated exactly from iOS so the same
 * day resolves to the same doc ID on every platform.
 */
internal object DateKeys {

    private fun dayFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale("en", "US", "POSIX")).apply {
            calendar = GregorianCalendar(TimeZone.getDefault())
            timeZone = TimeZone.getDefault()
        }

    /** `yyyy-MM-dd` of [date] at the local calendar day. */
    fun dayKey(date: Date): String = dayFormatter().format(date)

    /** Local midnight (`startOfDay`) of [date] - stored in `CampDay.date`. */
    fun startOfDay(date: Date): Date {
        val calendar = GregorianCalendar(TimeZone.getDefault())
        calendar.time = date
        calendar.set(GregorianCalendar.HOUR_OF_DAY, 0)
        calendar.set(GregorianCalendar.MINUTE, 0)
        calendar.set(GregorianCalendar.SECOND, 0)
        calendar.set(GregorianCalendar.MILLISECOND, 0)
        return calendar.time
    }

    /** Schedule day doc ID: `"<campingID>-day-<yyyy-MM-dd>"`. */
    fun campDayId(campingId: String, startDate: Date): String =
        "$campingId-day-${dayKey(startDate)}"

    /** Food menu doc ID: `"<yyyy-MM-dd>-<meal>"` (campingID intentionally omitted). */
    fun foodMenuId(date: Date, meal: FoodMealKind): String =
        "${dayKey(date)}-${meal.wireValue}"

    /** Generated meal-program doc ID: `"menu-<foodMenuId>"`. */
    fun menuProgramId(foodMenuId: String): String = "menu-$foodMenuId"
}

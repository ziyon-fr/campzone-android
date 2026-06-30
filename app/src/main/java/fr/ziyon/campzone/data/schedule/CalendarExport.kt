package fr.ziyon.campzone.data.schedule

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.Program
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalendarEventDraft(
    val id: String,
    val title: String,
    val startDate: Date,
    val endDate: Date,
    val location: String,
    val notes: String,
)

data class CalendarExportLabels(
    val camping: String = "Camping",
    val type: String = "Type",
)

/** Pure planner shared by the single-program intent and whole-schedule export. */
object ScheduleCalendarExportPlanner {
    private const val FallbackDurationMillis = 30 * 60 * 1_000L

    fun draft(
        program: Program,
        campingTitle: String,
        typeName: String,
        labels: CalendarExportLabels = CalendarExportLabels(),
    ): CalendarEventDraft {
        val endDate = program.endDate.takeIf { it.after(program.startDate) }
            ?: Date(program.startDate.time + FallbackDurationMillis)
        val description = program.description.trim()
        val notes = buildList {
            add("${labels.camping}: $campingTitle")
            add("${labels.type}: $typeName")
            if (description.isNotEmpty()) {
                add("")
                add(description)
            }
        }.joinToString("\n")
        return CalendarEventDraft(
            id = program.id,
            title = program.title,
            startDate = program.startDate,
            endDate = endDate,
            location = program.location,
            notes = notes,
        )
    }

    fun drafts(
        schedule: CampingSchedule,
        campingTitle: String,
        labels: CalendarExportLabels = CalendarExportLabels(),
        programIds: Set<String>? = null,
        typeName: (Program) -> String,
    ): List<CalendarEventDraft> = schedule.allPrograms
        .filter { programIds == null || it.id in programIds }
        .sortedBy { it.startDate }
        .map { program -> draft(program, campingTitle, typeName(program), labels) }
}

/** Testable representation of the extras supplied to Android's calendar app. */
data class CalendarInsertIntentContract(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String,
    val description: String,
)

internal fun CalendarEventDraft.toInsertIntentContract(): CalendarInsertIntentContract =
    CalendarInsertIntentContract(
        title = title,
        startMillis = startDate.time,
        endMillis = endDate.time,
        location = location,
        description = notes,
    )

/**
 * Android calendar handoff without READ_CALENDAR/WRITE_CALENDAR permissions.
 * A program uses CalendarContract.ACTION_INSERT; a whole schedule is a
 * standards-compliant multi-event .ics file exposed by the existing FileProvider.
 */
object AndroidCalendarExportLauncher {
    fun openProgram(context: Context, draft: CalendarEventDraft): Boolean =
        runCatching {
            val contract = draft.toInsertIntentContract()
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, contract.title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, contract.startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, contract.endMillis)
                putExtra(CalendarContract.Events.EVENT_LOCATION, contract.location)
                putExtra(CalendarContract.Events.DESCRIPTION, contract.description)
            }
            context.startActivity(intent)
        }.recoverCatching { error ->
            if (error is ActivityNotFoundException || error is SecurityException) return false
            throw error
        }.isSuccess

    fun shareSchedule(
        context: Context,
        drafts: List<CalendarEventDraft>,
        campingTitle: String,
        chooserTitle: String,
    ): Boolean {
        if (drafts.isEmpty()) return false
        return runCatching {
            val exportDirectory = File(context.cacheDir, "calendar_exports").apply { mkdirs() }
            val file = File(exportDirectory, "${campingTitle.safeFileName()}-schedule.ics")
            file.writeText(CalendarIcsEncoder.encode(drafts), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, campingTitle)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
        }.recoverCatching { error ->
            if (error is ActivityNotFoundException || error is SecurityException) return false
            throw error
        }.isSuccess
    }
}

internal object CalendarIcsEncoder {
    private val utcFormatter = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun encode(drafts: List<CalendarEventDraft>, generatedAt: Date = Date()): String = buildString {
        append("BEGIN:VCALENDAR\r\n")
        append("VERSION:2.0\r\n")
        append("PRODID:-//Campzone//Schedule//EN\r\n")
        append("CALSCALE:GREGORIAN\r\n")
        append("METHOD:PUBLISH\r\n")
        drafts.forEach { draft ->
            append("BEGIN:VEVENT\r\n")
            append("UID:${icsEscape(draft.id)}@campzone\r\n")
            append("DTSTAMP:${utcFormatter.format(generatedAt)}\r\n")
            append("DTSTART:${utcFormatter.format(draft.startDate)}\r\n")
            append("DTEND:${utcFormatter.format(draft.endDate)}\r\n")
            append("SUMMARY:${icsEscape(draft.title)}\r\n")
            if (draft.location.isNotBlank()) append("LOCATION:${icsEscape(draft.location)}\r\n")
            append("DESCRIPTION:${icsEscape(draft.notes)}\r\n")
            append("END:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }

    private fun icsEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,")
}

private fun String.safeFileName(): String = lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .ifBlank { "campzone" }

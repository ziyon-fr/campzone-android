package fr.ziyon.campzone.data.model

import java.util.Calendar
import java.util.Date
import java.util.UUID

data class CampingTemplateCloneOptions(
    val includeSchedule: Boolean = true,
    val includeTeams: Boolean = true,
    val includeSongbook: Boolean = true,
    val includeGuidelines: Boolean = true,
) {
    val hasAnyContent: Boolean
        get() = includeSchedule || includeTeams || includeSongbook || includeGuidelines
}

data class CampingTemplateCloneRequest(
    val sourceCampingId: String,
    val targetCampingId: String,
    val title: String,
    val startDate: Date,
    val endDate: Date,
    val registrationStatus: CampingRegistrationStatus,
    val options: CampingTemplateCloneOptions,
)

data class CampingTemplateCloneForm(
    val title: String = "",
    val startDate: Date = Date(),
    val endDate: Date = Date(System.currentTimeMillis() + DEFAULT_DURATION_MILLIS),
    val registrationStatus: CampingRegistrationStatus = CampingRegistrationStatus.Closed,
    val options: CampingTemplateCloneOptions = CampingTemplateCloneOptions(),
) {
    fun validationErrors(): List<CampingTemplateCloneValidationError> = buildList {
        if (title.trim().isBlank()) add(CampingTemplateCloneValidationError.TitleRequired)
        if (endDate.before(startDate)) add(CampingTemplateCloneValidationError.EndDateBeforeStartDate)
        if (!options.hasAnyContent) add(CampingTemplateCloneValidationError.ContentRequired)
    }

    fun request(sourceCampingId: String): CampingTemplateCloneRequest =
        CampingTemplateCloneRequest(
            sourceCampingId = sourceCampingId,
            targetCampingId = UUID.randomUUID().toString(),
            title = title.trim(),
            startDate = startDate,
            endDate = endDate,
            registrationStatus = registrationStatus,
            options = options,
        )

    companion object {
        private const val DEFAULT_DURATION_MILLIS = 2L * 24 * 60 * 60 * 1000

        fun from(source: Camping, calendar: Calendar = Calendar.getInstance()): CampingTemplateCloneForm {
            val defaultStart = (calendar.clone() as Calendar).apply {
                time = source.startDate
                add(Calendar.YEAR, 1)
            }.time
            val duration = (source.endDate.time - source.startDate.time).coerceAtLeast(0L)
            return CampingTemplateCloneForm(
                title = nextTitle(
                    title = source.title,
                    sourceDate = source.startDate,
                    targetDate = defaultStart,
                    calendar = calendar,
                ),
                startDate = defaultStart,
                endDate = Date(defaultStart.time + duration),
                registrationStatus = CampingRegistrationStatus.Closed,
                options = CampingTemplateCloneOptions(),
            )
        }

        private fun nextTitle(
            title: String,
            sourceDate: Date,
            targetDate: Date,
            calendar: Calendar,
        ): String {
            val trimmed = title.trim()
            val sourceYear = (calendar.clone() as Calendar).apply { time = sourceDate }.get(Calendar.YEAR).toString()
            val targetYear = (calendar.clone() as Calendar).apply { time = targetDate }.get(Calendar.YEAR).toString()
            return if (sourceYear in trimmed) {
                trimmed.replace(sourceYear, targetYear)
            } else {
                "$trimmed $targetYear"
            }
        }
    }
}

enum class CampingTemplateCloneValidationError {
    TitleRequired,
    EndDateBeforeStartDate,
    ContentRequired,
}

fun Camping.templateClone(
    request: CampingTemplateCloneRequest,
    calendar: Calendar = Calendar.getInstance(),
): Camping {
    val dayOffset = CampingTemplateCloneDayOffset.daysBetween(startDate, request.startDate, calendar)
    return copy(
        id = request.targetCampingId,
        title = request.title,
        startDate = request.startDate,
        endDate = request.endDate,
        registrationStatus = request.registrationStatus,
        publicationStatus = CampingPublicationStatus.Draft,
        attendees = emptyList(),
        guidelines = if (request.options.includeGuidelines) guidelines else "",
        createdByUid = null,
        createdByName = null,
        createdAt = null,
        updatedAt = null,
        registrationDeadline = registrationDeadline?.let { CampingTemplateCloneDayOffset.shift(it, dayOffset, calendar) },
        isFeatured = false,
    )
}

internal object CampingTemplateCloneDayOffset {
    fun daysBetween(sourceStartDate: Date, targetStartDate: Date, calendar: Calendar = Calendar.getInstance()): Int {
        val source = calendar.startOfDay(sourceStartDate)
        val target = calendar.startOfDay(targetStartDate)
        return ((target.time - source.time) / MILLIS_PER_DAY).toInt()
    }

    fun shift(date: Date, days: Int, calendar: Calendar = Calendar.getInstance()): Date =
        (calendar.clone() as Calendar).apply {
            time = date
            add(Calendar.DAY_OF_YEAR, days)
        }.time

    private fun Calendar.startOfDay(date: Date): Date =
        (clone() as Calendar).apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
}

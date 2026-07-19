package fr.ziyon.campzone.core.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fr.ziyon.campzone.MainActivity
import fr.ziyon.campzone.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min

data class CampzoneWidgetSnapshot(
    val camp: WidgetCampSummary? = null,
    val programs: List<WidgetProgramSummary> = emptyList(),
    val pass: WidgetCampPassSummary? = null,
    val packing: WidgetPackingSummary? = null,
    val team: WidgetTeamSummary? = null,
    val announcement: WidgetAnnouncementSummary? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        putOpt("camp", camp?.toJson())
        put("programs", JSONArray().also { array ->
            programs.forEach { program -> array.put(program.toJson()) }
        })
        putOpt("pass", pass?.toJson())
        putOpt("packing", packing?.toJson())
        putOpt("team", team?.toJson())
        putOpt("announcement", announcement?.toJson())
    }

    companion object {
        val Empty = CampzoneWidgetSnapshot()

        fun fromJson(raw: String?): CampzoneWidgetSnapshot {
            if (raw.isNullOrBlank()) return Empty
            return runCatching {
                val json = JSONObject(raw)
                CampzoneWidgetSnapshot(
                    camp = json.optJSONObject("camp")?.let(WidgetCampSummary::fromJson),
                    programs = json.optJSONArray("programs")?.let { array ->
                        (0 until array.length()).mapNotNull { index ->
                            array.optJSONObject(index)?.let(WidgetProgramSummary::fromJson)
                        }
                    }.orEmpty(),
                    pass = json.optJSONObject("pass")?.let(WidgetCampPassSummary::fromJson),
                    packing = json.optJSONObject("packing")?.let(WidgetPackingSummary::fromJson),
                    team = json.optJSONObject("team")?.let(WidgetTeamSummary::fromJson),
                    announcement = json.optJSONObject("announcement")?.let(WidgetAnnouncementSummary::fromJson),
                )
            }.getOrDefault(Empty)
        }
    }
}

data class WidgetCampSummary(val id: String, val name: String, val startMillis: Long, val endMillis: Long) {
    val totalDays: Int
        get() {
            val start = startOfDay(startMillis)
            val end = startOfDay(endMillis)
            val days = ((end - start) / DateUtils.DAY_IN_MILLIS).toInt()
            return max(1, days + 1)
        }

    fun currentDay(now: Long = System.currentTimeMillis()): Int? {
        if (now < startOfDay(startMillis)) return null
        val day = ((startOfDay(min(now, endMillis)) - startOfDay(startMillis)) / DateUtils.DAY_IN_MILLIS).toInt() + 1
        return day.coerceIn(1, totalDays)
    }

    fun hasEnded(now: Long = System.currentTimeMillis()): Boolean = now > endMillis
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("start", startMillis).put("end", endMillis)

    companion object {
        fun fromJson(json: JSONObject) = WidgetCampSummary(
            id = json.optString("id"),
            name = json.optString("name"),
            startMillis = json.optLong("start"),
            endMillis = json.optLong("end"),
        )
    }
}

data class WidgetProgramSummary(
    val id: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String?,
    val campId: String? = null,
) {
    fun isOngoing(now: Long = System.currentTimeMillis()): Boolean = now >= startMillis && now < endMillis
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("start", startMillis)
        .put("end", endMillis)
        .putOpt("location", location)
        .putOpt("campId", campId)

    companion object {
        fun fromJson(json: JSONObject) = WidgetProgramSummary(
            id = json.optString("id"),
            title = json.optString("title"),
            startMillis = json.optLong("start"),
            endMillis = json.optLong("end"),
            location = json.optString("location").takeUnless { it.isBlank() },
            campId = json.optString("campId").takeUnless { it.isBlank() },
        )
    }
}

data class WidgetCampPassSummary(
    val campId: String,
    val campName: String,
    val qrValue: String,
    val attendeeName: String,
    val teamName: String?,
    val lodgingName: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("campId", campId)
        .put("campName", campName)
        .put("qrValue", qrValue)
        .put("attendeeName", attendeeName)
        .putOpt("teamName", teamName)
        .putOpt("lodgingName", lodgingName)

    companion object {
        fun fromJson(json: JSONObject) = WidgetCampPassSummary(
            campId = json.optString("campId"),
            campName = json.optString("campName"),
            qrValue = json.optString("qrValue"),
            attendeeName = json.optString("attendeeName"),
            teamName = json.optString("teamName").takeUnless { it.isBlank() },
            lodgingName = json.optString("lodgingName").takeUnless { it.isBlank() },
        )
    }
}

data class WidgetPackingSummary(val campName: String, val packedCount: Int, val totalCount: Int, val campId: String? = null) {
    val percent: Int get() = if (totalCount <= 0) 0 else ((packedCount.toDouble() / totalCount) * 100).toInt().coerceIn(0, 100)
    fun toJson(): JSONObject = JSONObject()
        .put("campName", campName)
        .put("packed", packedCount)
        .put("total", totalCount)
        .putOpt("campId", campId)
    companion object {
        fun fromJson(json: JSONObject) = WidgetPackingSummary(
            campName = json.optString("campName"),
            packedCount = json.optInt("packed"),
            totalCount = json.optInt("total"),
            campId = json.optString("campId").takeUnless { it.isBlank() },
        )
    }
}

data class WidgetTeamSummary(
    val teamName: String,
    val rank: Int,
    val totalTeams: Int,
    val points: Int,
    val teamId: String? = null,
    val campId: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("teamName", teamName)
        .put("rank", rank)
        .put("total", totalTeams)
        .put("points", points)
        .putOpt("teamId", teamId)
        .putOpt("campId", campId)
    companion object {
        fun fromJson(json: JSONObject) = WidgetTeamSummary(
            teamName = json.optString("teamName"),
            rank = json.optInt("rank"),
            totalTeams = json.optInt("total"),
            points = json.optInt("points"),
            teamId = json.optString("teamId").takeUnless { it.isBlank() },
            campId = json.optString("campId").takeUnless { it.isBlank() },
        )
    }
}

data class WidgetAnnouncementSummary(val id: String, val title: String, val body: String, val createdMillis: Long, val campName: String?) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("body", body).put("created", createdMillis).putOpt("campName", campName)
    companion object { fun fromJson(json: JSONObject) = WidgetAnnouncementSummary(json.optString("id"), json.optString("title"), json.optString("body"), json.optLong("created"), json.optString("campName").takeUnless { it.isBlank() }) }
}

object CampzoneWidgetStore {
    private const val Prefs = "campzone_widgets"
    private const val SnapshotKey = "snapshot_v1"

    fun read(context: Context): CampzoneWidgetSnapshot =
        CampzoneWidgetSnapshot.fromJson(context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString(SnapshotKey, null))

    fun write(context: Context, snapshot: CampzoneWidgetSnapshot) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .edit()
            .putString(SnapshotKey, snapshot.toJson().toString())
            .apply()
        CampzoneWidgetProvider.updateAll(context)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .edit()
            .remove(SnapshotKey)
            .apply()
        CampzoneWidgetProvider.updateAll(context)
    }
}

object CampzoneWidgetPublisher {
    fun updateHome(
        context: Context,
        camp: WidgetCampSummary?,
        programs: List<WidgetProgramSummary>,
        pass: WidgetCampPassSummary?,
        announcement: WidgetAnnouncementSummary?,
    ) = mutate(context) { it.copy(camp = camp, programs = programs, pass = pass, announcement = announcement) }

    fun updatePacking(context: Context, packing: WidgetPackingSummary?) = mutate(context) { it.copy(packing = packing) }
    fun updateTeam(context: Context, team: WidgetTeamSummary?) = mutate(context) { it.copy(team = team) }
    fun clearAll(context: Context) = CampzoneWidgetStore.clear(context)

    private fun mutate(context: Context, transform: (CampzoneWidgetSnapshot) -> CampzoneWidgetSnapshot) {
        val current = CampzoneWidgetStore.read(context)
        val next = transform(current)
        if (next != current) CampzoneWidgetStore.write(context, next)
    }
}

enum class WidgetSurface { Countdown, UpNext, Pass, Packing, Team, Announcement }

abstract class CampzoneWidgetProvider(private val surface: WidgetSurface) : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetRefreshAction) {
            updateAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id, surface) }
        scheduleNextWidgetRefresh(context, CampzoneWidgetStore.read(context))
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            WidgetProviderClasses.forEach { provider ->
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                ids.forEach { id -> updateWidget(context, manager, id, provider.surface()) }
            }
            scheduleNextWidgetRefresh(context, CampzoneWidgetStore.read(context))
        }

        private fun Class<out CampzoneWidgetProvider>.surface(): WidgetSurface = when (this) {
            CampCountdownWidgetProvider::class.java -> WidgetSurface.Countdown
            CampCountdownWideWidgetProvider::class.java -> WidgetSurface.Countdown
            UpNextWidgetProvider::class.java -> WidgetSurface.UpNext
            UpNextWideWidgetProvider::class.java -> WidgetSurface.UpNext
            CampPassWidgetProvider::class.java -> WidgetSurface.Pass
            CampPassWideWidgetProvider::class.java -> WidgetSurface.Pass
            PackingProgressWidgetProvider::class.java -> WidgetSurface.Packing
            TeamStandingsWidgetProvider::class.java -> WidgetSurface.Team
            TeamStandingsWideWidgetProvider::class.java -> WidgetSurface.Team
            else -> WidgetSurface.Announcement
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int, surface: WidgetSurface) {
            val snapshot = CampzoneWidgetStore.read(context)
            val views = RemoteViews(context.packageName, R.layout.widget_campzone_card)
            render(context, views, surface, snapshot)
            views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, snapshot, surface))
            manager.updateAppWidget(widgetId, views)
        }
    }
}

class CampCountdownWidgetProvider : CampzoneWidgetProvider(WidgetSurface.Countdown)
class CampCountdownWideWidgetProvider : CampzoneWidgetProvider(WidgetSurface.Countdown)
class UpNextWidgetProvider : CampzoneWidgetProvider(WidgetSurface.UpNext)
class UpNextWideWidgetProvider : CampzoneWidgetProvider(WidgetSurface.UpNext)
class CampPassWidgetProvider : CampzoneWidgetProvider(WidgetSurface.Pass)
class CampPassWideWidgetProvider : CampzoneWidgetProvider(WidgetSurface.Pass)
class PackingProgressWidgetProvider : CampzoneWidgetProvider(WidgetSurface.Packing)
class TeamStandingsWidgetProvider : CampzoneWidgetProvider(WidgetSurface.Team)
class TeamStandingsWideWidgetProvider : CampzoneWidgetProvider(WidgetSurface.Team)
class AnnouncementWidgetProvider : CampzoneWidgetProvider(WidgetSurface.Announcement)
class AnnouncementLargeWidgetProvider : CampzoneWidgetProvider(WidgetSurface.Announcement)

private fun render(context: Context, views: RemoteViews, surface: WidgetSurface, snapshot: CampzoneWidgetSnapshot) {
    resetChrome(context, views)
    when (surface) {
        WidgetSurface.Countdown -> renderCountdown(context, views, snapshot.camp)
        WidgetSurface.UpNext -> renderUpNext(context, views, snapshot.programs)
        WidgetSurface.Pass -> renderPass(context, views, snapshot.pass)
        WidgetSurface.Packing -> renderPacking(context, views, snapshot.packing)
        WidgetSurface.Team -> renderTeam(context, views, snapshot.team)
        WidgetSurface.Announcement -> renderAnnouncement(context, views, snapshot.announcement)
    }
}

private fun resetChrome(context: Context, views: RemoteViews) {
    applyChrome(
        context = context,
        views = views,
        backgroundRes = R.drawable.widget_campzone_background,
        accentColorRes = R.color.cz_widget_accent,
        titleColorRes = R.color.cz_widget_text,
        secondaryColorRes = R.color.cz_widget_text_secondary,
    )
    views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 20f)
    views.setTextViewTextSize(R.id.widget_subtitle, TypedValue.COMPLEX_UNIT_SP, 13f)
    views.setTextViewTextSize(R.id.widget_detail, TypedValue.COMPLEX_UNIT_SP, 12f)
    views.setViewVisibility(R.id.widget_qr_frame, View.GONE)
    views.setViewVisibility(R.id.widget_qr, View.GONE)
    views.setViewVisibility(R.id.widget_progress, View.GONE)
    views.setViewVisibility(R.id.widget_detail, View.GONE)
    views.setTextViewText(R.id.widget_detail, "")
}

private fun applyChrome(
    context: Context,
    views: RemoteViews,
    backgroundRes: Int,
    accentColorRes: Int,
    titleColorRes: Int,
    secondaryColorRes: Int,
) {
    views.setInt(R.id.widget_root, "setBackgroundResource", backgroundRes)
    views.setTextColor(R.id.widget_eyebrow, context.getColor(accentColorRes))
    views.setTextColor(R.id.widget_title, context.getColor(titleColorRes))
    views.setTextColor(R.id.widget_subtitle, context.getColor(secondaryColorRes))
    views.setTextColor(R.id.widget_detail, context.getColor(secondaryColorRes))
}

private fun renderCountdown(context: Context, views: RemoteViews, camp: WidgetCampSummary?) {
    applyChrome(
        context = context,
        views = views,
        backgroundRes = R.drawable.widget_countdown_background,
        accentColorRes = R.color.cz_widget_text_on_dark_secondary,
        titleColorRes = R.color.cz_widget_text_on_dark,
        secondaryColorRes = R.color.cz_widget_text_on_dark_secondary,
    )
    if (camp == null) return setEmpty(views, context.getString(R.string.widget_countdown_name), context.getString(R.string.widget_no_camp))
    val now = System.currentTimeMillis()
    val day = camp.currentDay(now)
    views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, if (day != null) 30f else 24f)
    setText(
        views,
        context.getString(R.string.widget_brand_name),
        when {
            camp.hasEnded(now) -> context.getString(R.string.widget_see_you_next_time)
            day != null -> context.getString(R.string.widget_day_number, day)
            else -> relative(context, camp.startMillis, now)
        },
        when {
            camp.hasEnded(now) -> camp.name
            day != null -> context.getString(R.string.widget_of_days_camp, camp.totalDays, camp.name)
            else -> context.getString(R.string.widget_until_camp, camp.name)
        },
    )
}

private fun renderUpNext(context: Context, views: RemoteViews, programs: List<WidgetProgramSummary>) {
    val now = System.currentTimeMillis()
    val current = programs.firstOrNull { it.isOngoing(now) }
    val next = current ?: programs.filter { it.startMillis > now }.minByOrNull { it.startMillis }
    if (next == null) return setEmpty(views, context.getString(R.string.widget_up_next_name), context.getString(R.string.widget_nothing_scheduled))
    views.setTextColor(R.id.widget_eyebrow, context.getColor(if (current != null) R.color.cz_widget_success else R.color.cz_widget_accent))
    setText(
        views,
        if (current != null) context.getString(R.string.widget_now) else context.getString(R.string.widget_up_next_label),
        next.title,
        next.location,
        context.getString(if (current != null) R.string.widget_ends else R.string.widget_starts, relative(context, if (current != null) next.endMillis else next.startMillis, now)),
    )
}

private fun renderPass(context: Context, views: RemoteViews, pass: WidgetCampPassSummary?) {
    applyChrome(
        context = context,
        views = views,
        backgroundRes = R.drawable.widget_pass_background,
        accentColorRes = R.color.cz_widget_text_on_dark_secondary,
        titleColorRes = R.color.cz_widget_text_on_dark,
        secondaryColorRes = R.color.cz_widget_text_on_dark_secondary,
    )
    views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 17f)
    if (pass == null) return setEmpty(views, context.getString(R.string.widget_pass_name), context.getString(R.string.widget_no_active_pass))
    setText(
        views,
        context.getString(R.string.widget_pass_name),
        pass.attendeeName,
        pass.campName,
        listOfNotNull(pass.teamName, pass.lodgingName).joinToString(" · ").takeUnless { it.isBlank() },
    )
    qrBitmap(pass.qrValue)?.let { bitmap ->
        views.setViewVisibility(R.id.widget_qr_frame, View.VISIBLE)
        views.setViewVisibility(R.id.widget_qr, View.VISIBLE)
        views.setImageViewBitmap(R.id.widget_qr, bitmap)
    }
}

private fun renderPacking(context: Context, views: RemoteViews, packing: WidgetPackingSummary?) {
    if (packing == null || packing.totalCount <= 0) return setEmpty(views, context.getString(R.string.widget_packing_name), context.getString(R.string.widget_no_packing_list))
    val complete = packing.packedCount >= packing.totalCount
    views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, if (complete) 20f else 30f)
    setText(
        views,
        context.getString(R.string.widget_packing_name),
        if (complete) context.getString(R.string.widget_all_packed) else "${packing.packedCount}/${packing.totalCount}",
        if (complete) packing.campName else context.getString(R.string.widget_items_packed),
        if (complete) null else packing.campName.takeUnless { it.isBlank() },
    )
    views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
    views.setProgressBar(R.id.widget_progress, 100, packing.percent, false)
}

private fun renderTeam(context: Context, views: RemoteViews, team: WidgetTeamSummary?) {
    if (team == null) return setEmpty(views, context.getString(R.string.widget_team_name), context.getString(R.string.widget_no_team_standings))
    views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 30f)
    setText(
        views,
        context.getString(R.string.widget_my_team),
        context.getString(R.string.widget_rank_badge, team.rank),
        team.teamName,
        listOf(
            context.getString(R.string.widget_points, team.points),
            context.getString(R.string.widget_rank_of, team.rank, team.totalTeams),
        ).joinToString(" · "),
    )
}

private fun renderAnnouncement(context: Context, views: RemoteViews, announcement: WidgetAnnouncementSummary?) {
    if (announcement == null) return setEmpty(views, context.getString(R.string.widget_announcement_name), context.getString(R.string.widget_no_announcements))
    val now = System.currentTimeMillis()
    val isNew = announcement.createdMillis > 0 && now - announcement.createdMillis <= DateUtils.DAY_IN_MILLIS * 2
    setText(
        views,
        listOfNotNull(
            context.getString(R.string.widget_announcement_label),
            context.getString(R.string.widget_new_badge).takeIf { isNew },
        ).joinToString(" · "),
        announcement.title,
        announcement.body,
        listOfNotNull(
            announcement.campName,
            announcement.createdMillis.takeIf { it > 0 }?.let { relative(context, it, now) },
        ).joinToString(" · ").takeUnless { it.isBlank() },
    )
}

private fun setText(views: RemoteViews, eyebrow: String, title: String, subtitle: String?, detail: String? = null) {
    views.setTextViewText(R.id.widget_eyebrow, eyebrow)
    views.setTextViewText(R.id.widget_title, title)
    views.setTextViewText(R.id.widget_subtitle, subtitle.orEmpty())
    views.setTextViewText(R.id.widget_detail, detail.orEmpty())
    views.setViewVisibility(R.id.widget_detail, if (detail.isNullOrBlank()) View.GONE else View.VISIBLE)
}

private fun setEmpty(views: RemoteViews, eyebrow: String, title: String) = setText(views, eyebrow, title, null)

private fun openIntent(context: Context, snapshot: CampzoneWidgetSnapshot, surface: WidgetSurface): PendingIntent {
    val uri = widgetDeepLinkUrl(surface, snapshot)?.let(Uri::parse)
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = uri
    }
    return PendingIntent.getActivity(context, 4400 + surface.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

internal fun widgetDeepLinkUrl(
    surface: WidgetSurface,
    snapshot: CampzoneWidgetSnapshot,
    now: Long = System.currentTimeMillis(),
): String? {
    val campId = snapshot.camp?.id?.takeUnless { it.isBlank() }
    return when (surface) {
        WidgetSurface.Countdown -> campId?.let { "campzone://camping/${it.urlSegment()}" }
        WidgetSurface.UpNext -> {
            val selectedProgram = selectUpNextProgram(snapshot.programs, now)
            val selectedCampId = selectedProgram?.campId?.takeUnless { it.isBlank() } ?: campId
            when {
                selectedProgram != null && selectedCampId != null ->
                    "campzone://program/${selectedProgram.id.urlSegment()}?c=${selectedCampId.urlSegment()}"
                campId != null -> "campzone://schedule/${campId.urlSegment()}"
                else -> null
            }
        }
        WidgetSurface.Pass -> snapshot.pass?.campId?.takeUnless { it.isBlank() }
            ?.let { "campzone://camp-pass/${it.urlSegment()}" }
            ?: campId?.let { "campzone://camping/${it.urlSegment()}" }
        WidgetSurface.Packing -> snapshot.packing?.campId?.takeUnless { it.isBlank() }
            ?.let { "campzone://packing/${it.urlSegment()}" }
            ?: campId?.let { "campzone://packing/${it.urlSegment()}" }
        WidgetSurface.Team -> {
            val team = snapshot.team
            val selectedCampId = team?.campId?.takeUnless { it.isBlank() } ?: campId
            when {
                team?.teamId?.isNotBlank() == true && selectedCampId != null ->
                    "campzone://team/${team.teamId.urlSegment()}?c=${selectedCampId.urlSegment()}"
                selectedCampId != null -> "campzone://camping-teams/${selectedCampId.urlSegment()}"
                else -> null
            }
        }
        WidgetSurface.Announcement -> snapshot.announcement?.id?.takeUnless { it.isBlank() }
            ?.let { "campzone://announcement/${it.urlSegment()}" }
            ?: campId?.let { "campzone://camping/${it.urlSegment()}" }
    }
}

private fun relative(context: Context, targetMillis: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(targetMillis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun nextWidgetRefreshMillis(snapshot: CampzoneWidgetSnapshot, now: Long = System.currentTimeMillis()): Long {
    val boundaries = buildList {
        snapshot.camp?.let { camp ->
            if (camp.startMillis > now) add(camp.startMillis)
            if (camp.endMillis > now) add(camp.endMillis)
        }
        snapshot.programs.forEach { program ->
            if (program.startMillis > now) add(program.startMillis)
            if (program.endMillis > now) add(program.endMillis)
        }
        add(nextMidnightMillis(now))
    }
    return boundaries
        .filter { it > now }
        .minOrNull()
        ?.plus(1_000L)
        ?: now + DateUtils.HOUR_IN_MILLIS
}

private fun scheduleNextWidgetRefresh(context: Context, snapshot: CampzoneWidgetSnapshot) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val pendingIntent = widgetRefreshIntent(context)
    if (!hasInstalledWidgets(context)) {
        alarmManager.cancel(pendingIntent)
        return
    }
    alarmManager.cancel(pendingIntent)
    alarmManager.set(AlarmManager.RTC_WAKEUP, nextWidgetRefreshMillis(snapshot), pendingIntent)
}

private fun hasInstalledWidgets(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    return WidgetProviderClasses.any { provider ->
        manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
    }
}

private fun widgetRefreshIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        WidgetRefreshRequestCode,
        Intent(context, CampCountdownWidgetProvider::class.java).setAction(WidgetRefreshAction),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun selectUpNextProgram(programs: List<WidgetProgramSummary>, now: Long): WidgetProgramSummary? =
    programs.firstOrNull { it.isOngoing(now) }
        ?: programs.filter { it.startMillis > now }.minByOrNull { it.startMillis }

private fun qrBitmap(value: String, size: Int = 220): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until size) for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
}.getOrNull()

fun Date.toWidgetMillis(): Long = time

private fun nextMidnightMillis(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun String.urlSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private const val WidgetRefreshAction = "fr.ziyon.campzone.action.WIDGET_REFRESH"
private const val WidgetRefreshRequestCode = 4499

private val WidgetProviderClasses = listOf(
    CampCountdownWidgetProvider::class.java,
    CampCountdownWideWidgetProvider::class.java,
    UpNextWidgetProvider::class.java,
    UpNextWideWidgetProvider::class.java,
    CampPassWidgetProvider::class.java,
    CampPassWideWidgetProvider::class.java,
    PackingProgressWidgetProvider::class.java,
    TeamStandingsWidgetProvider::class.java,
    TeamStandingsWideWidgetProvider::class.java,
    AnnouncementWidgetProvider::class.java,
    AnnouncementLargeWidgetProvider::class.java,
)

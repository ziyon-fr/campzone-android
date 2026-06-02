package fr.ziyon.campzone.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

data class Achievement(
    val id: String,
    val title: String,
    val summary: String,
    val detail: String,
    val icon: ImageVector,
    val tint: BadgeTint,
    val rarity: AchievementRarity,
    val awardKind: AchievementAwardKind = AchievementAwardKind.Manual,
) {
    val canBeAwardedManually: Boolean = awardKind == AchievementAwardKind.Manual

}

enum class BadgeTint(
    val color: Color
) {

    Ember(
        color = Color(0xFFE85D3F)
    ),

    Amber(
        color = Color(0xFFFFB300)
    ),

    Pine(
        color = Color(0xFF2E7D32)
    ),

    Sky(
        color = Color(0xFF42A5F5)
    ),

    Rose(
        color = Color(0xFFE91E63)
    ),

    Gold(
        color = Color(0xFFFFD54F)
    );

    companion object {
        fun fromWire(value: String?): BadgeTint? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

data class BadgeViewModel(
    val achievement: Achievement,
    val earned: EarnedBadge?,
) {
    val id: String get() = achievement.id
    val isEarned: Boolean get() = earned != null
}

object AchievementCatalog {
    val all: List<Achievement> = listOf(
        a("first-adventure", "First Adventure", "Attended your first camp", "Awarded by leadership after your first approved camping participation.", Icons.Filled.Terrain, BadgeTint.Ember, AchievementRarity.Common, AchievementAwardKind.Automatic),
        a("tent-ready", "Tent Ready", "Arrived prepared for camp", "Awarded by your team leader for arriving with the essentials and a ready attitude.", Icons.Filled.Backpack, BadgeTint.Pine, AchievementRarity.Common),
        a("camp-check-in", "Camp Check-In", "Completed camp check-in", "Awarded by staff when your arrival and team placement are fully confirmed.", Icons.Filled.CheckCircle, BadgeTint.Sky, AchievementRarity.Common, AchievementAwardKind.Automatic),
        a("team-roster", "Team Roster", "Joined an official team", "Awarded when you are assigned to a camping team roster.", Icons.Filled.Groups, BadgeTint.Amber, AchievementRarity.Common, AchievementAwardKind.Automatic),
        a("morning-circle", "Morning Circle", "Showed up for morning gathering", "Awarded by leaders for faithful presence at the morning team circle.", Icons.Filled.WbSunny, BadgeTint.Gold, AchievementRarity.Common),
        a("meal-line-helper", "Meal Line Helper", "Served during camp meals", "Awarded for helping your team keep meals organized and welcoming.", Icons.Filled.Restaurant, BadgeTint.Rose, AchievementRarity.Common),
        a("song-circle", "Song Circle", "Joined camp worship moments", "Awarded for participating with your team in worship and song moments.", Icons.Filled.MusicNote, BadgeTint.Sky, AchievementRarity.Common),
        a("good-neighbor", "Good Neighbor", "Encouraged another camper", "Awarded by a leader for kindness that made the camping experience better for someone else.", Icons.Filled.VolunteerActivism, BadgeTint.Rose, AchievementRarity.Common),
        a("trail-cleanup", "Trail Cleanup", "Helped keep camp clean", "Awarded for helping your team leave shared camp spaces cleaner than you found them.", Icons.Filled.Forest, BadgeTint.Pine, AchievementRarity.Common),
        a("memory-maker", "Memory Maker", "Contributed to camp spirit", "Awarded for bringing positive energy to a team activity, game, or evening moment.", Icons.Filled.AutoAwesome, BadgeTint.Gold, AchievementRarity.Common),
        a("trail-veteran", "Trail Veteran", "Attended 3 camps", "Awarded by leadership after three confirmed Campzone camping participations.", Icons.Filled.Terrain, BadgeTint.Pine, AchievementRarity.Uncommon, AchievementAwardKind.Automatic),
        a("team-player", "Team Player", "Served your team consistently", "Awarded by your team leader for steady participation across team challenges.", Icons.Filled.Groups, BadgeTint.Amber, AchievementRarity.Uncommon),
        a("score-spark", "Score Spark", "Earned personal team points", "Awarded when a leader recognizes your contribution to your team's score.", Icons.Filled.Star, BadgeTint.Gold, AchievementRarity.Uncommon, AchievementAwardKind.Automatic),
        a("prayer-partner", "Prayer Partner", "Supported someone in prayer", "Awarded by a pastor or leader for intentionally praying with or for another camper.", Icons.Filled.SelfImprovement, BadgeTint.Sky, AchievementRarity.Uncommon),
        a("kitchen-helper", "Kitchen Helper", "Helped the food team", "Awarded for practical service with meals, cleanup, or food distribution.", Icons.Filled.Kitchen, BadgeTint.Rose, AchievementRarity.Uncommon),
        a("clean-camp-champion", "Clean Camp Champion", "Led a cleanup effort", "Awarded by leadership for taking initiative to restore a shared camp area.", Icons.Filled.CleaningServices, BadgeTint.Pine, AchievementRarity.Uncommon),
        a("flag-circle", "Flag Circle", "Represented your team well", "Awarded for respectful participation in opening, closing, or team flag moments.", Icons.Filled.Flag, BadgeTint.Ember, AchievementRarity.Uncommon),
        a("night-watch-helper", "Night Watch Helper", "Helped evening routines", "Awarded for helping leaders keep nighttime transitions calm and organized.", Icons.Filled.Nightlight, BadgeTint.Sky, AchievementRarity.Uncommon),
        a("workshop-learner", "Workshop Learner", "Completed a camp workshop", "Awarded when a leader confirms active participation in a camping workshop.", Icons.Filled.Psychology, BadgeTint.Amber, AchievementRarity.Uncommon),
        a("welcome-crew", "Welcome Crew", "Welcomed new campers", "Awarded for helping new or quieter campers feel included in the team.", Icons.Filled.WavingHand, BadgeTint.Gold, AchievementRarity.Uncommon),
        a("camp-mentor", "Camp Mentor", "Guided another camper", "Awarded by leaders for mentoring a camper through a challenge, activity, or team responsibility.", Icons.Filled.Person, BadgeTint.Pine, AchievementRarity.Rare),
        a("team-captain", "Team Captain", "Led a team to glory", "Awarded once you are appointed team captain for any camp.", Icons.Filled.Groups, BadgeTint.Gold, AchievementRarity.Rare, AchievementAwardKind.Automatic),
        a("points-builder", "Points Builder", "Made your team's score climb", "Awarded for a meaningful contribution to your team's score during camping.", Icons.Filled.RocketLaunch, BadgeTint.Ember, AchievementRarity.Rare),
        a("service-squad", "Service Squad", "Joined a service action", "Awarded by leadership for participating in a recognized team service mission.", Icons.Filled.Handshake, BadgeTint.Sky, AchievementRarity.Rare),
        a("activity-leader", "Activity Leader", "Helped lead a camp activity", "Awarded for helping a leader run a team challenge, workshop, or camp activity.", Icons.Filled.Flag, BadgeTint.Amber, AchievementRarity.Rare),
        a("peacemaker", "Peacemaker", "Helped restore team unity", "Awarded by a pastor or leader for handling conflict with maturity and care.", Icons.Filled.VolunteerActivism, BadgeTint.Gold, AchievementRarity.Rare),
        a("language-bridge", "Language Bridge", "Helped campers communicate", "Awarded for translating, explaining, or helping campers across languages during camp.", Icons.Filled.Language, BadgeTint.Sky, AchievementRarity.Rare),
        a("camp-storyteller", "Camp Storyteller", "Shared a meaningful testimony", "Awarded by leadership for sharing a story or testimony that encouraged the camp.", Icons.Filled.Mic, BadgeTint.Rose, AchievementRarity.Rare),
        a("steady-servant", "Steady Servant", "Served without needing attention", "Awarded for quiet, reliable service that helped the team function well.", Icons.Filled.Shield, BadgeTint.Pine, AchievementRarity.Rare),
        a("voice-of-praise", "Voice of Praise", "Helped lead camp singing", "Awarded for helping your team participate in worship and camp singing.", Icons.Filled.MusicNote, BadgeTint.Rose, AchievementRarity.Rare),
        a("early-riser", "Early Riser", "Checked in before sunrise", "Awarded after a leader confirms an early-morning camp responsibility.", Icons.Filled.WbSunny, BadgeTint.Amber, AchievementRarity.Epic),
        a("shepherd", "Shepherd", "Led a season of camp", "Awarded to pastors and youth directors who shepherd a camp season.", Icons.Filled.Groups, BadgeTint.Pine, AchievementRarity.Epic),
        a("perfect-day", "Perfect Day", "Completed a full camp day", "Awarded for completing every assigned activity and team responsibility in one camp day.", Icons.Filled.CheckCircle, BadgeTint.Ember, AchievementRarity.Epic),
        a("team-builder", "Team Builder", "Strengthened your team culture", "Awarded by a leader for helping your team become more united, welcoming, and focused.", Icons.Filled.Groups, BadgeTint.Gold, AchievementRarity.Epic),
        a("challenge-champion", "Challenge Champion", "Won a major team challenge", "Awarded for a standout role in a major camping challenge or competition.", Icons.Filled.EmojiEvents, BadgeTint.Amber, AchievementRarity.Epic),
        a("worship-lead", "Worship Lead", "Led a worship moment", "Awarded by a pastor or leader for guiding worship, prayer, or reflection at camp.", Icons.Filled.MusicNote, BadgeTint.Rose, AchievementRarity.Epic),
        a("check-in-hero", "Check-In Hero", "Helped with camper check-ins", "Awarded for helping staff welcome, verify, or orient campers during arrival.", Icons.Filled.QrCodeScanner, BadgeTint.Sky, AchievementRarity.Epic),
        a("mission-maker", "Mission Maker", "Led service beyond your team", "Awarded for helping organize a service action that blessed the wider camp.", Icons.Filled.RocketLaunch, BadgeTint.Pine, AchievementRarity.Epic),
        a("legacy-helper", "Legacy Helper", "Helped leaders run camp", "Awarded for taking responsibility that made the camping program stronger for everyone.", Icons.Filled.Handshake, BadgeTint.Ember, AchievementRarity.Epic),
        a("all-camp-spirit", "All-Camp Spirit", "Lifted the whole camp", "Awarded by leadership for visible joy, encouragement, and service across team lines.", Icons.Filled.AutoAwesome, BadgeTint.Gold, AchievementRarity.Epic),
        a("perfect-attendance", "Perfect Attendance", "100% schedule completion", "Awarded after attending every scheduled program at a camp.", Icons.Filled.CheckCircle, BadgeTint.Ember, AchievementRarity.Legendary, AchievementAwardKind.Automatic),
        a("grand-camp-champion", "Grand Camp Champion", "Helped win the camping season", "Awarded for exceptional contribution to a team that finishes as camp champion.", Icons.Filled.EmojiEvents, BadgeTint.Gold, AchievementRarity.Legendary),
        a("servant-leader", "Servant Leader", "Led through service", "Awarded by admin, pastor, or youth leadership for rare servant-hearted leadership at camp.", Icons.Filled.VolunteerActivism, BadgeTint.Pine, AchievementRarity.Legendary),
        a("season-shepherd", "Season Shepherd", "Guided multiple teams well", "Awarded to a leader who shepherds teams across a full camping season with excellence.", Icons.Filled.Groups, BadgeTint.Sky, AchievementRarity.Legendary),
        a("campfire-legend", "Campfire Legend", "Left a lasting camp legacy", "Awarded for an unforgettable contribution that shaped the spirit of the whole camping.", Icons.Filled.LocalFireDepartment, BadgeTint.Ember, AchievementRarity.Legendary),
    )

    val manual: List<Achievement> = all.filter { it.canBeAwardedManually }

    fun achievement(id: String): Achievement? = all.firstOrNull { it.id == id }

    private fun a(
        id: String,
        title: String,
        summary: String,
        detail: String,
        icon: ImageVector,
        tint: BadgeTint,
        rarity: AchievementRarity,
        awardKind: AchievementAwardKind = AchievementAwardKind.Manual,
    ) = Achievement(id, title, summary, detail, icon, tint, rarity, awardKind)
}

internal data class AchievementCatalogEntry(
    val achievement: Achievement,
    val sortOrder: Int,
)

internal fun Map<String, Any?>.toAchievementCatalogEntryOrNull(documentId: String): AchievementCatalogEntry? {
    val id = stringValue("id") ?: documentId.takeUnless { it.isBlank() } ?: return null
    val fallback = AchievementCatalog.achievement(id)
    val localizedContent = localizedContent()
    val title = localizedString(localizedContent, "title", "name") ?: fallback?.title ?: return null
    val summary = localizedString(localizedContent, "summary", "subtitle", "description") ?: fallback?.summary ?: title
    val detail = localizedString(localizedContent, "detail", "details", "longDescription") ?: fallback?.detail ?: summary
    val icon = achievementIcon(
        stringValue("icon") ?: stringValue("iconName") ?: stringValue("systemImage"),
        fallback?.icon,
    )
    val tint = BadgeTint.fromWire(stringValue("tint") ?: stringValue("color")) ?: fallback?.tint ?: BadgeTint.Ember
    val rarity = AchievementRarity.fromWire((stringValue("rarity") ?: fallback?.rarity?.wireValue)?.lowercase())
    val awardKind = achievementAwardKind(fallback?.awardKind)
    val order = intValue("sortOrder") ?: intValue("order") ?: fallback?.let { AchievementCatalog.all.indexOf(it) } ?: Int.MAX_VALUE

    return AchievementCatalogEntry(
        achievement = Achievement(
            id = id,
            title = title,
            summary = summary,
            detail = detail,
            icon = icon,
            tint = tint,
            rarity = rarity,
            awardKind = awardKind,
        ),
        sortOrder = order,
    )
}

private fun Map<String, Any?>.achievementAwardKind(fallback: AchievementAwardKind?): AchievementAwardKind {
    val raw = stringValue("awardKind") ?: stringValue("kind")
    if (raw != null) return AchievementAwardKind.fromWire(raw.lowercase())
    return when (boolValue("automatic") ?: boolValue("isAutomatic")) {
        true -> AchievementAwardKind.Automatic
        false -> AchievementAwardKind.Manual
        null -> fallback ?: AchievementAwardKind.Manual
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.localizedString(
    localizedContent: Map<String, Map<String, Any?>>,
    vararg keys: String,
): String? {
    val candidates = localeCandidates()
    candidates.forEach { localeKey ->
        localizedContent.localizedMap(localeKey)?.let { content ->
            keys.forEach { key ->
                content.stringValue(key)?.let { return it }
            }
        }
    }
    for (key in keys) {
        when (val value = this[key]) {
            is String -> value.trim().takeUnless { it.isBlank() }?.let { return it }
            is Map<*, *> -> {
                val map = value as Map<String, Any?>
                map.localizedValue(candidates)?.let { return it }
            }
        }
    }
    return null
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.localizedContent(): Map<String, Map<String, Any?>> {
    val localizations = this["localizations"] as? Map<String, Any?>
    val translations = this["translations"] as? Map<String, Any?>
    return buildMap {
        localizations?.forEach { (localeKey, content) ->
            (content as? Map<String, Any?>)?.let { put(localeKey, it) }
        }
        translations?.forEach { (localeKey, content) ->
            (content as? Map<String, Any?>)?.let { putIfAbsent(localeKey, it) }
        }
    }
}

private fun Map<String, Map<String, Any?>>.localizedMap(localeKey: String): Map<String, Any?>? {
    this[localeKey]?.let { return it }
    val normalized = localeKey.normalizedLocaleKey()
    entries.firstOrNull { it.key.normalizedLocaleKey() == normalized }?.value?.let { return it }
    if (!normalized.contains("-")) {
        entries
            .sortedBy { it.key }
            .firstOrNull { it.key.normalizedLocaleKey().startsWith("$normalized-") }
            ?.value
            ?.let { return it }
    }
    return null
}

private fun Map<String, Any?>.localizedValue(localeKeys: List<String>): String? {
    localeKeys.forEach { localeKey ->
        stringValue(localeKey)?.let { return it }
        val normalized = localeKey.normalizedLocaleKey()
        entries.firstOrNull { it.key.normalizedLocaleKey() == normalized }?.value.asLocalizedString()?.let { return it }
        if (!normalized.contains("-")) {
            entries
                .sortedBy { it.key }
                .firstOrNull { it.key.normalizedLocaleKey().startsWith("$normalized-") }
                ?.value
                .asLocalizedString()
                ?.let { return it }
        }
    }
    return null
}

private fun Any?.asLocalizedString(): String? =
    (this as? String)?.trim()?.takeUnless { it.isBlank() }

private fun localeCandidates(): List<String> {
    val locale = Locale.getDefault()
    val language = locale.language.lowercase()
    val country = locale.country.lowercase()
    return buildList {
        if (language.isNotBlank() && country.isNotBlank()) {
            add("${language}_${country.uppercase()}")
            add("${language}-$country")
            if (language == "pt" && country == "br") add("pt-BR")
        }
        if (language.isNotBlank()) add(language)
        add("en")
        add("default")
    }.distinct()
}

private fun String.normalizedLocaleKey(): String =
    trim()
        .replace("_", "-")
        .lowercase()

private fun achievementIcon(raw: String?, fallback: ImageVector?): ImageVector {
    val key = raw
        ?.lowercase()
        ?.replace(".", "")
        ?.replace("-", "")
        ?.replace("_", "")
        ?.replace(" ", "")
    return when (key) {
        "autorenew", "autoawesome", "sparkles", "sparklemagnifyingglass" -> Icons.Filled.AutoAwesome
        "backpack", "backpackfill" -> Icons.Filled.Backpack
        "checkcircle", "checkmarksealfill", "calendarbadgecheckmark" -> Icons.Filled.CheckCircle
        "cleaningservices", "trashslashfill" -> Icons.Filled.CleaningServices
        "eco", "leaffill" -> Icons.Filled.Eco
        "emojievents", "trophyfill", "crownfill" -> Icons.Filled.EmojiEvents
        "favorite", "heartfill" -> Icons.Filled.Favorite
        "flag", "flagfill" -> Icons.Filled.Flag
        "forest" -> Icons.Filled.Forest
        "groups", "person2fill", "person3fill", "person3sequencefill", "figure2andchildholdinghands" -> Icons.Filled.Groups
        "handshake", "figure2armsopen", "handsandsparklesfill" -> Icons.Filled.Handshake
        "home" -> Icons.Filled.Home
        "kitchen", "takeoutbagandcupandstrawfill" -> Icons.Filled.Kitchen
        "language", "bubbleleftandbubblerightfill" -> Icons.Filled.Language
        "localfiredepartment", "flamefill" -> Icons.Filled.LocalFireDepartment
        "mic", "quotebubblefill" -> Icons.Filled.Mic
        "militarytech" -> Icons.Filled.MilitaryTech
        "musicnote", "musicmic", "musicquarternote3", "musicnotelist" -> Icons.Filled.MusicNote
        "nightlight", "moonstarsfill" -> Icons.Filled.Nightlight
        "person", "personcropcirclebadgecheckmark" -> Icons.Filled.Person
        "personadd" -> Icons.Filled.PersonAdd
        "psychology", "bookclosedfill" -> Icons.Filled.Psychology
        "qrcodescanner", "qrcodeviewfinder" -> Icons.Filled.QrCodeScanner
        "restaurant", "forkknife" -> Icons.Filled.Restaurant
        "rocketlaunch", "chartlineuptrendxyaxis", "globeeuropeafricafill" -> Icons.Filled.RocketLaunch
        "selfimprovement", "handssparklesfill" -> Icons.Filled.SelfImprovement
        "shield", "shieldlefthalffilled" -> Icons.Filled.Shield
        "star", "pluscirclefill" -> Icons.Filled.Star
        "terrain", "tentfill", "figurehiking" -> Icons.Filled.Terrain
        "volunteeractivism", "dovefill" -> Icons.Filled.VolunteerActivism
        "wbsunny", "sunrisefill", "sunhorizonfill" -> Icons.Filled.WbSunny
        "wavinghand", "handwavefill" -> Icons.Filled.WavingHand
        "work", "wrenchandscrewdriverfill" -> Icons.Filled.Work
        "workspacepremium" -> Icons.Filled.WorkspacePremium
        else -> fallback ?: Icons.Filled.WorkspacePremium
    }
}

val AchievementRarity.displayName: String
    get() = when (this) {
        AchievementRarity.Common -> "Common"
        AchievementRarity.Uncommon -> "Uncommon"
        AchievementRarity.Rare -> "Rare"
        AchievementRarity.Epic -> "Epic"
        AchievementRarity.Legendary -> "Legendary"
    }


val AchievementRarity.displayOrder: Int
    get() = when (this) {
        AchievementRarity.Common -> 0
        AchievementRarity.Uncommon -> 1
        AchievementRarity.Rare -> 2
        AchievementRarity.Epic -> 3
        AchievementRarity.Legendary -> 4
    }

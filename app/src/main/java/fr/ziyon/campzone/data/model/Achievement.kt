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
    )
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

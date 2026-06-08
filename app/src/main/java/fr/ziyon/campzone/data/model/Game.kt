package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{id}/games/{gameId}` (`02-firestore-schema.md` §5.3). `updatedAt` is
 * a **client clock** `Date()` (not serverTimestamp); `createdAt` is the model's
 * init `Date()` and the collection is ordered by it.
 */
data class Game(
    val id: String,
    val campingId: String,
    val name: String,
    val rules: String = "",
    val pointRules: List<PointRule> = emptyList(),
    val venuePointIds: List<String> = emptyList(),
    val locationVisibleToAll: Boolean = false,
    val createdBy: String = "",
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val linkedVenuePointIds: List<String> get() = venuePointIds
    val isLocationVisibleToAll: Boolean get() = locationVisibleToAll
}

data class PointRule(
    val id: String,
    val name: String,
    val points: Int,
    val reason: String = "",
    val ruleBrokenPenalty: Int? = null,
    val maxUses: Int? = null,
    val category: String? = null,
    val appliesTo: PointRuleTarget = PointRuleTarget.Any,
    val visibility: PointRuleVisibility = PointRuleVisibility.Immediate,
)

internal fun Map<String, Any?>.toGameOrNull(documentId: String): Game? {
    val name = stringValue("name") ?: return null
    return Game(
        id = stringValue("id") ?: documentId,
        campingId = stringValue("campingID").orEmpty(),
        name = name,
        rules = rawStringValue("rules").orEmpty(),
        pointRules = mapListValue("pointRules").mapNotNull { it.toPointRuleOrNull() },
        venuePointIds = stringListValue("venuePointIDs"),
        locationVisibleToAll = boolValue("locationVisibleToAll") ?: false,
        createdBy = stringValue("createdBy").orEmpty(),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

fun leadershipOnlyVenuePointIds(games: List<Game>): Set<String> {
    val hidden = linkedSetOf<String>()
    val published = linkedSetOf<String>()
    games.forEach { game ->
        if (game.locationVisibleToAll) {
            published.addAll(game.venuePointIds)
        } else {
            hidden.addAll(game.venuePointIds)
        }
    }
    return hidden.subtract(published)
}

fun VenueMap.visibleForGameLocationRules(
    games: List<Game>,
    canSeeHiddenGameLocations: Boolean,
): VenueMap {
    if (canSeeHiddenGameLocations) return this
    val hidden = leadershipOnlyVenuePointIds(games)
    if (hidden.isEmpty()) return this
    return copy(points = points.filterNot { it.id in hidden })
}

internal fun Map<String, Any?>.toPointRuleOrNull(): PointRule? {
    val id = stringValue("id") ?: return null
    return PointRule(
        id = id,
        name = rawStringValue("name").orEmpty(),
        points = intValue("points") ?: 0,
        reason = rawStringValue("reason").orEmpty(),
        ruleBrokenPenalty = intValue("ruleBrokenPenalty"),
        maxUses = intValue("maxUses"),
        category = stringValue("category"),
        appliesTo = PointRuleTarget.fromWire(stringValue("appliesTo")),
        visibility = PointRuleVisibility.fromWire(stringValue("visibility")),
    )
}

internal object GamePayload {
    fun gamePayload(game: Game, now: Date, includeCreatedAt: Boolean): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to game.id,
            "campingID" to game.campingId,
            "name" to game.name.trim(),
            "rules" to game.rules,
            "pointRules" to game.pointRules.map(::pointRuleMap),
            "createdBy" to game.createdBy,
            "updatedAt" to now,
        )
        game.venuePointIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .takeUnless { it.isEmpty() }
            ?.let { payload["venuePointIDs"] = it }
        if (game.locationVisibleToAll) payload["locationVisibleToAll"] = true
        if (includeCreatedAt) payload["createdAt"] = game.createdAt ?: now
        return payload
    }

    fun pointRuleMap(rule: PointRule): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to rule.id,
            "name" to rule.name.trim(),
            "points" to rule.points,
            "reason" to rule.reason,
            "appliesTo" to rule.appliesTo.wireValue,
            "visibility" to rule.visibility.wireValue,
        )
        rule.ruleBrokenPenalty?.let { map["ruleBrokenPenalty"] = it }
        rule.maxUses?.let { map["maxUses"] = it }
        rule.category?.trim()?.takeUnless { it.isBlank() }?.let { map["category"] = it }
        return map
    }

    fun leadershipOnlyVenuePointIds(games: List<Game>): Set<String> =
        fr.ziyon.campzone.data.model.leadershipOnlyVenuePointIds(games)
}

package fr.ziyon.campzone.data.model

import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementPayloadTest {
    @Test
    fun catalogMatchesShippedIosAchievementIds() {
        assertEquals(45, AchievementCatalog.all.size)
        assertEquals(45, AchievementCatalog.all.map { it.id }.distinct().size)
        assertEquals(7, AchievementCatalog.all.count { it.awardKind == AchievementAwardKind.Automatic })
        assertEquals(38, AchievementCatalog.manual.size)
        assertEquals(10, AchievementCatalog.all.count { it.rarity == AchievementRarity.Common })
        assertEquals(10, AchievementCatalog.all.count { it.rarity == AchievementRarity.Uncommon })
        assertEquals(10, AchievementCatalog.all.count { it.rarity == AchievementRarity.Rare })
        assertEquals(10, AchievementCatalog.all.count { it.rarity == AchievementRarity.Epic })
        assertEquals(5, AchievementCatalog.all.count { it.rarity == AchievementRarity.Legendary })
        assertTrue(AchievementCatalog.achievement("first-adventure") != null)
        assertTrue(AchievementCatalog.achievement("campfire-legend") != null)
    }

    @Test
    fun firebaseCatalogEntryDecodesDisplayFieldsAndMetadata() {
        val entry = mapOf(
            "title" to "Remote Badge",
            "summary" to "Loaded from Firestore",
            "detail" to "Rendered from the top-level badges collection.",
            "rarity" to "rare",
            "tint" to "gold",
            "awardKind" to "manual",
            "icon" to "trophy.fill",
            "sortOrder" to 7,
        ).toAchievementCatalogEntryOrNull("remote-badge")

        assertEquals("remote-badge", entry?.achievement?.id)
        assertEquals("Remote Badge", entry?.achievement?.title)
        assertEquals(AchievementRarity.Rare, entry?.achievement?.rarity)
        assertEquals(BadgeTint.Gold, entry?.achievement?.tint)
        assertEquals(AchievementAwardKind.Manual, entry?.achievement?.awardKind)
        assertEquals(7, entry?.sortOrder)
    }

    @Test
    fun firebaseCatalogEntryDecodesLocalizedContentWrapper() {
        withDefaultLocale(locale("fr", "FR")) {
            val entry = mapOf(
                "id" to "first-adventure",
                "localizations" to mapOf(
                    "en" to mapOf(
                        "title" to "First Adventure",
                        "summary" to "Attended your first camp",
                        "detail" to "Awarded by leadership after your first approved camping participation.",
                    ),
                    "fr" to mapOf(
                        "title" to "Première aventure",
                        "summary" to "A participé à son premier camp",
                        "detail" to "Attribué par les responsables après ta première participation approuvée à un camp.",
                    ),
                    "pt-BR" to mapOf(
                        "title" to "Primeira aventura",
                        "summary" to "Participou do seu primeiro acampamento",
                        "detail" to "Concedido pela liderança após sua primeira participação aprovada em um acampamento.",
                    ),
                ),
                "rarity" to "common",
                "tint" to "ember",
                "awardKind" to "automatic",
            ).toAchievementCatalogEntryOrNull("first-adventure")

            assertEquals("Première aventure", entry?.achievement?.title)
            assertEquals("A participé à son premier camp", entry?.achievement?.summary)
            assertEquals(
                "Attribué par les responsables après ta première participation approuvée à un camp.",
                entry?.achievement?.detail,
            )
            assertEquals(AchievementAwardKind.Automatic, entry?.achievement?.awardKind)
        }
    }

    @Test
    fun localizedCatalogEntryFallsBackFromLanguageToRegionalContent() {
        withDefaultLocale(locale("pt")) {
            val entry = mapOf(
                "id" to "activity-leader",
                "localizations" to mapOf(
                    "en" to mapOf(
                        "title" to "Activity Leader",
                        "summary" to "Helped lead a camp activity",
                        "detail" to "Awarded for helping a leader run a team challenge, workshop, or camp activity.",
                    ),
                    "pt-BR" to mapOf(
                        "title" to "Líder de atividade",
                        "summary" to "Ajudou a liderar uma atividade do acampamento",
                        "detail" to "Concedido por ajudar um líder a conduzir um desafio de equipe, uma oficina ou uma atividade do acampamento.",
                    ),
                ),
            ).toAchievementCatalogEntryOrNull("activity-leader")

            assertEquals("Líder de atividade", entry?.achievement?.title)
            assertEquals("Ajudou a liderar uma atividade do acampamento", entry?.achievement?.summary)
        }
    }

    @Test
    fun flatLocalizedFieldMapUsesRegionalFallback() {
        withDefaultLocale(locale("pt")) {
            val entry = mapOf(
                "id" to "remote-badge",
                "title" to mapOf(
                    "en" to "Remote Badge",
                    "pt-BR" to "Distintivo remoto",
                ),
                "summary" to mapOf(
                    "en" to "Loaded from Firestore",
                    "pt-BR" to "Carregado do Firestore",
                ),
                "detail" to "Rendered from the top-level badges collection.",
                "rarity" to "rare",
                "tint" to "gold",
            ).toAchievementCatalogEntryOrNull("remote-badge")

            assertEquals("Distintivo remoto", entry?.achievement?.title)
            assertEquals("Carregado do Firestore", entry?.achievement?.summary)
        }
    }

    @Test
    fun manualAwardPayloadKeepsExplicitNullKeys() {
        val payload = EarnedBadgePayload.awardPayload(
            badge = EarnedBadge(
                id = "tent-ready",
                userId = "user-1",
                earnedAt = Date(),
                campingId = null,
                note = "   ",
            ),
            serverTimestamp = TS,
        )

        assertEquals("tent-ready", payload["id"])
        assertEquals("user-1", payload["userID"])
        assertEquals(TS, payload["earnedAt"])
        assertTrue(payload.containsKey("campingID"))
        assertTrue(payload.containsKey("note"))
        assertNull(payload["campingID"])
        assertNull(payload["note"])
    }

    @Test
    fun decoderFallsBackToDocumentIdAndFiltersByCatalogAtServiceLayer() {
        val decoded = mapOf(
            "userID" to "user-1",
            "earnedAt" to TS,
            "campingID" to null,
            "note" to null,
        ).toEarnedBadgeOrNull("team-captain")

        assertEquals("team-captain", decoded.id)
        assertEquals("user-1", decoded.userId)
        assertEquals(TS, decoded.earnedAt)
    }

    private companion object {
        val TS = Date(42)
    }
}

private inline fun withDefaultLocale(locale: Locale, block: () -> Unit) {
    val previous = Locale.getDefault()
    try {
        Locale.setDefault(locale)
        block()
    } finally {
        Locale.setDefault(previous)
    }
}

private fun locale(language: String, region: String? = null): Locale =
    Locale.Builder()
        .setLanguage(language)
        .apply { region?.let(::setRegion) }
        .build()

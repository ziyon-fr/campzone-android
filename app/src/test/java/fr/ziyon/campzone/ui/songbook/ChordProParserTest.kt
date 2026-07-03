package fr.ziyon.campzone.ui.songbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProParserTest {

    @Test
    fun parsesWorshipChartSymbolsWithSlashBassesAndAlterations() {
        val symbols = listOf(
            "D/F#",
            "D4",
            "Bb2/D",
            "Ebmaj7/G",
            "F#m7(11)",
            "A7sus4",
            "C7(b9,#11)",
            "F#7(5+)",
            "Dadd9/F#",
            "Eø7",
            "Bº",
        )

        symbols.forEach { symbol ->
            assertNotNull("Expected $symbol to parse", ChordSymbolParser.parseOrNull(symbol))
        }
    }

    @Test
    fun parsesAdvancedNotationWithoutDroppingModifiers() {
        val symbols = listOf(
            "C7+",
            "C7M",
            "CM7",
            "CΔ9",
            "CmMaj7",
            "C+7",
            "C6/9",
            "F#m7b5",
            "C13#11",
            "G7alt",
            "Cno3",
        )

        symbols.forEach { symbol ->
            assertNotNull("Expected $symbol to parse", ChordSymbolParser.parseOrNull(symbol))
        }
    }

    @Test
    fun tokenizesShortNumericAndSlashChordsAsWholeSymbols() {
        val sheet = ChordProParser.parse("D4   D/A   D/F#   A4   F2/A   Bb2/D", existingId = "song")
        val chords = sheet.lines.single().chords

        assertEquals(listOf("D4", "D/A", "D/F#", "A4", "F2/A", "Bb2/D"), chords.map { it.chord })
        assertEquals(listOf(0, 5, 11, 18, 23, 30), chords.map { it.position })
    }

    @Test
    fun parsesChordOverLyricSheetsWithSectionsAndTrailingChords() {
        val sheet = ChordProParser.parse(
            """
            [Verse]
            C7(b9,#11)   F#7(5+)   Bb2/D
            Amazing grace
            [Chorus] Am F C G
            I once was lost
            """.trimIndent(),
            existingId = "song",
        )

        assertTrue(sheet.lines.any { it.isSectionHeader && it.text == "[Verse]" })
        assertTrue(sheet.lines.any { it.isSectionHeader && it.text == "[Chorus]" })
        assertTrue(sheet.lines.any { it.chords.map { chord -> chord.chord } == listOf("C7(b9,#11)", "F#7(5+)", "Bb2/D") })

        val chorusIndex = sheet.lines.indexOfFirst { it.isSectionHeader && it.text == "[Chorus]" }
        assertTrue(chorusIndex >= 0)
        assertEquals(listOf("Am", "F", "C", "G"), sheet.lines[chorusIndex + 1].chords.map { it.chord })
    }

    @Test
    fun parsesInlineChordProWithoutEatingLyrics() {
        val sheet = ChordProParser.parse("[D/F#]Amazing [C7(b9,#11)]grace", existingId = "song")
        val line = sheet.lines.single()

        assertEquals("Amazing grace", line.text)
        assertEquals(listOf("D/F#", "C7(b9,#11)"), line.chords.map { it.chord })
        assertEquals(listOf(0, 8), line.chords.map { it.position })
    }

    @Test
    fun rejectsSlashChordFragmentsEmbeddedInLyrics() {
        val sheet = ChordProParser.parse("D/Amazing grace", existingId = "song")
        val line = sheet.lines.single()

        assertEquals("D/Amazing grace", line.text)
        assertTrue(line.chords.isEmpty())
        assertFalse(ChordSymbolParser.isChord("D/Amazing"))
    }

    @Test
    fun transposesComplexSymbolsWithoutTreatingSixNineAsBass() {
        assertEquals("D6/9", ChordSymbolParser.transpose("C6/9", semitones = 2, originalKey = "C"))
        assertEquals(
            "D7(b9,#11)/F#",
            ChordSymbolParser.transpose("C7(b9,#11)/E", semitones = 2, originalKey = "C"),
        )
        assertEquals("Eb2/G", ChordSymbolParser.transpose("D2/F#", semitones = 1, originalKey = "Bb"))
    }
}

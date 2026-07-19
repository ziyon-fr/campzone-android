package fr.ziyon.campzone.data.songbook

import fr.ziyon.campzone.data.model.Chord
import fr.ziyon.campzone.data.model.ChordLine
import fr.ziyon.campzone.data.model.ChordSheet
import fr.ziyon.campzone.data.model.Song
import fr.ziyon.campzone.data.model.SongLyricsPart
import fr.ziyon.campzone.data.model.SongLyricsPartKind
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongPresentationDeckTest {

    @Test
    fun deckUsesChordSheetSectionsAndSkipsChordOnlyLines() {
        val song = Song(
            id = "song-1",
            title = "Amazing Grace",
            artist = "John Newton",
            chordSheet = ChordSheet(
                id = "song-1",
                originalKey = "Bb",
                lines = listOf(
                    ChordLine(id = "h1", text = "{Verse 1}", isSectionHeader = true),
                    ChordLine(id = "l1", text = "Amazing grace", chords = listOf(Chord(id = "c1", chord = "Bb"))),
                    ChordLine(id = "l2"),
                    ChordLine(id = "h2", text = "[Chorus]", isSectionHeader = true),
                    ChordLine(id = "c-only", chords = listOf(Chord(id = "c2", chord = "F"))),
                    ChordLine(id = "l3", text = "Praise the Lord"),
                ),
            ),
        )

        val deck = SongPresentationDeckBuilder.deck(song)

        assertEquals(3, deck.size)
        assertEquals(listOf("Amazing Grace"), deck[0].lines)
        assertEquals(SongPresentationSlideKind.Title("John Newton  ·  Bb"), deck[0].kind)
        assertEquals(SongPresentationSlideKind.Lyrics("Verse 1"), deck[1].kind)
        assertEquals(listOf("Amazing grace"), deck[1].lines)
        assertEquals(SongPresentationSlideKind.Lyrics("Chorus"), deck[2].kind)
        assertEquals(listOf("Praise the Lord"), deck[2].lines)
    }

    @Test
    fun deckFallsBackToStructuredLyricsParts() {
        val song = Song(
            id = "song-2",
            title = "Campfire Blessing",
            lyricsParts = listOf(
                SongLyricsPart(
                    id = "verse",
                    kind = SongLyricsPartKind.Verse,
                    number = 2,
                    text = "Walk in faith\n\nStay together",
                ),
                SongLyricsPart(
                    id = "custom",
                    kind = SongLyricsPartKind.Custom,
                    number = 1,
                    title = "Call",
                    text = "Are you ready?",
                ),
            ),
        )

        val deck = SongPresentationDeckBuilder.deck(song)

        assertEquals(3, deck.size)
        assertEquals(SongPresentationSlideKind.Lyrics("Verse 2"), deck[1].kind)
        assertEquals(listOf("Walk in faith", "Stay together"), deck[1].lines)
        assertEquals(SongPresentationSlideKind.Lyrics("Call"), deck[2].kind)
    }

    @Test
    fun downloaderCacheNameUsesUrlHashAndFallbackExtension() {
        val withExtension = SongDocumentDownloader.cachedFileName(
            "https://cdn.example.org/song/sheet.pdf?token=abc",
            SongDocumentKind.SheetPdf,
        )
        val withoutExtension = SongDocumentDownloader.cachedFileName(
            "https://cdn.example.org/song/slides?id=123",
            SongDocumentKind.Slides,
        )

        assertTrue(withExtension.endsWith(".pdf"))
        assertTrue(withExtension.substringBefore('.').length == 24)
        assertTrue(withoutExtension.endsWith(".pptx"))
        assertTrue(withoutExtension.substringBefore('.').length == 24)
    }

    @Test
    fun pptxSlideSizeReaderParsesPresentationXml() {
        val size = PptxSlideSizeReader.slideSizeFromPresentationXml(
            """<p:presentation><p:sldSz cy="6858000" cx="12192000"/></p:presentation>""",
        )

        assertEquals(PptxSlideSize(widthPoints = 960.0, heightPoints = 540.0), size)
    }

    @Test
    fun pptxSlideSizeReaderExtractsPresentationXmlFromArchive() {
        val file = File.createTempFile("campzone-slides", ".pptx")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("ppt/presentation.xml"))
                zip.write("""<p:presentation><p:sldSz cx="9144000" cy="6858000"/></p:presentation>""".toByteArray())
                zip.closeEntry()
            }

            assertEquals(PptxSlideSize(widthPoints = 720.0, heightPoints = 540.0), PptxSlideSizeReader.slideSize(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun pptxSlideSizeReaderRejectsMissingDimensions() {
        assertNull(PptxSlideSizeReader.slideSizeFromPresentationXml("<p:presentation/>"))
    }
}

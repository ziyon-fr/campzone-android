package fr.ziyon.campzone.data.songbook

import fr.ziyon.campzone.data.model.SongAudioTrackType
import fr.ziyon.campzone.data.model.SongAudioKind
import fr.ziyon.campzone.data.model.SongLyricsPartKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CantusCatalogTest {

    @Test
    fun songDraftPreservesCatalogMetadataAndChordProFields() {
        val song = CantusSong(
            id = "cantus-1",
            slug = "amazing-grace",
            title = "Amazing Grace",
            artist = CantusArtistRef(slug = "newton", name = "John Newton"),
            key = "Bb",
            bpm = 72.6,
            timeSignature = "3/4",
            language = "en",
            songbooks = listOf(CantusSongbookRef(slug = "hymnal", title = "Hymnal", number = 12)),
            media = CantusMedia(
                sheetPdfUrl = "https://cdn.example.org/amazing.pdf",
                powerpointUrl = "https://cdn.example.org/amazing.pptx",
                audioUrl = "https://cdn.example.org/amazing.mp3",
            ),
            chordedLyrics = """
                {Verse 1}
                A[Bb]mazing [F/A]grace
                {Chorus}
                [Eb]Praise the [Bb/D]Lord
            """.trimIndent(),
            author = "John Newton",
        )

        val draft = song.songDraft(campingId = "camp-1", orderIndex = 7)

        assertEquals("Amazing Grace", draft.title)
        assertEquals("John Newton", draft.artist)
        assertEquals("John Newton", draft.composer)
        assertEquals("amazing-grace", draft.cantusSlug)
        assertEquals("https://cdn.example.org/amazing.pdf", draft.pdfLink)
        assertEquals("https://cdn.example.org/amazing.pptx", draft.pptxLink)
        assertEquals(7, draft.orderIndex)
        assertEquals("Bb", draft.chordSheet.originalKey)
        assertEquals(73, draft.chordSheet.tempo)
        assertEquals("3/4", draft.chordSheet.timeSignature)
        assertEquals(
            listOf(SongLyricsPartKind.Verse, SongLyricsPartKind.Chorus),
            draft.lyricsParts.map { it.kind },
        )
        assertEquals(listOf("Amazing grace", "Praise the Lord"), draft.lyricsParts.map { it.text })
        assertEquals(song.chordedLyrics, draft.chordedLyrics)
        assertEquals("Bb", draft.chordSheet.lines[1].chords.first().chord)
        assertNotNull(draft.existingAudio)
        assertEquals(SongAudioTrackType.MainSong, draft.existingAudio?.trackType)
    }

    @Test
    fun songDraftPreservesCantusAudioFilesWithLabels() {
        val song = CantusSong(
            id = "cantus-2",
            slug = "voice-kits",
            title = "Voice Kits",
            media = CantusMedia(
                audioUrl = "https://cdn.example.org/legacy-main.mp3",
                audioFiles = listOf(
                    CantusAudioFile(
                        id = "main",
                        fileName = "main.wav",
                        contentType = "audio/wav",
                        storagePath = "cantus/audio/main",
                        downloadUrl = "https://cdn.example.org/main.wav",
                        kind = "wav",
                        duration = 123.0,
                        fileSize = 4096,
                        voiceType = "mainSong",
                        displayName = "Congregation",
                    ),
                    CantusAudioFile(
                        id = "playback",
                        fileName = "playback.m4a",
                        contentType = "audio/mp4",
                        storagePath = "cantus/audio/playback",
                        downloadUrl = "https://cdn.example.org/playback.m4a",
                        kind = "m4a",
                        duration = 124.0,
                        fileSize = 8192,
                        voiceType = "playback",
                        displayName = "Playback",
                    ),
                    CantusAudioFile(
                        id = "contralto",
                        fileName = "contralto.mp3",
                        contentType = "audio/mpeg",
                        storagePath = "cantus/audio/contralto",
                        downloadUrl = "https://cdn.example.org/contralto.mp3",
                        kind = "mp3",
                        duration = 125.0,
                        fileSize = 16384,
                        voiceType = "contralto",
                        displayName = "Contralto rehearsal",
                    ),
                    CantusAudioFile(
                        id = "guide",
                        fileName = "guide.aac",
                        contentType = "audio/aac",
                        storagePath = "cantus/audio/guide",
                        downloadUrl = "https://cdn.example.org/guide.aac",
                        kind = "aac",
                        duration = 126.0,
                        fileSize = 32768,
                        voiceType = "other",
                        displayName = "Leader guide",
                    ),
                ),
            ),
        )

        val draft = song.songDraft(campingId = "camp-1", orderIndex = 0)

        assertEquals(4, draft.existingAudioFiles.size)
        assertEquals("main", draft.existingAudio?.id)
        assertEquals(
            listOf(
                SongAudioTrackType.MainSong,
                SongAudioTrackType.Playback,
                SongAudioTrackType.Contralto,
                SongAudioTrackType.Other,
            ),
            draft.existingAudioFiles.map { it.trackType },
        )
        assertEquals(
            listOf("Congregation", "Playback", "Contralto rehearsal", "Leader guide"),
            draft.existingAudioFiles.map { it.displayName },
        )
        assertEquals(
            listOf(SongAudioKind.Wav, SongAudioKind.M4a, SongAudioKind.Mp3, SongAudioKind.Aac),
            draft.existingAudioFiles.map { it.kind },
        )
        assertEquals("https://cdn.example.org/contralto.mp3", draft.existingAudioFiles[2].downloadUrl)
    }
}

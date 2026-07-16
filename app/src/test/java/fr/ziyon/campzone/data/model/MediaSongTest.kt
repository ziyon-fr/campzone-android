package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.permissions.UserRole
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSongTest {

    // --- MediaItem / AlbumSettings ---

    @Test
    fun mediaFullSetOmitsNilOptionalsAndRoundTrips() {
        val media = MediaItem(
            id = "m1",
            campingId = "camp-1",
            kind = MediaKind.Photo,
            secureUrl = "https://cdn/img.jpg",
            publicId = "campzone/album/m1",
            uploaderId = "u1",
            uploaderName = "Maria",
            caption = "Sunset",
        )
        val payload = MediaPayload.mediaPayload(media, TS)
        assertEquals("photo", payload["kind"])
        assertEquals("cloudinary", payload["source"])
        assertEquals("campzone/album/m1", payload["publicID"])
        assertEquals(TS, payload["uploadedAt"])
        assertFalse(payload.containsKey("thumbnailURL")) // omit-when-nil
        assertFalse(payload.containsKey("width"))

        val decoded = payload.toMediaItemOrNull("m1")!!
        assertEquals(MediaKind.Photo, decoded.kind)
        assertEquals(MediaSource.Cloudinary, decoded.source)
        assertEquals("Sunset", decoded.caption)
        assertEquals("https://cdn/img.jpg", decoded.playbackUrl)

        val broken = payload.toMutableMap().apply { remove("secureURL") }
        assertNull(broken.toMediaItemOrNull("m1"))
    }

    @Test
    fun externalVideoOmitsPublicIdAndRoundTrips() {
        val videoUrl = "https://drive.google.com/file/d/video-id/view"
        val media = MediaItem(
            id = "m2",
            campingId = "camp-1",
            kind = MediaKind.Video,
            source = MediaSource.ExternalVideo,
            secureUrl = videoUrl,
            externalUrl = videoUrl,
            publicId = null,
            uploaderId = "u1",
            uploaderName = "Maria",
            caption = "Replay",
        )
        val payload = MediaPayload.mediaPayload(media, TS)
        assertEquals("video", payload["kind"])
        assertEquals("externalVideo", payload["source"])
        assertEquals(videoUrl, payload["secureURL"])
        assertEquals(videoUrl, payload["externalURL"])
        assertFalse(payload.containsKey("publicID"))

        val decoded = payload.toMediaItemOrNull("m2")!!
        assertEquals(MediaSource.ExternalVideo, decoded.source)
        assertEquals(videoUrl, decoded.playbackUrl)
        assertTrue(decoded.opensExternally)
        assertNull(decoded.displayThumbnailUrl)

        val legacy = payload.toMutableMap().apply { remove("externalURL") }
        val legacyDecoded = legacy.toMediaItemOrNull("m2")!!
        assertEquals(videoUrl, legacyDecoded.externalUrl)
    }

    @Test
    fun albumSettingsDefaultsAndSortedRaws() {
        val payload = MediaPayload.albumSettingsPayload(AlbumSettings())
        assertEquals(
            listOf("admin", "leader", "pastor", "photographer", "youth_director"),
            payload["allowedUploadRoles"],
        )
        val decoded = mapOf("allowedUploadRoles" to listOf("admin", "photographer")).toAlbumSettings()
        assertTrue(decoded.allows(UserRole.Admin))
        assertFalse(decoded.allows(UserRole.User))
    }

    // --- Song ---

    @Test
    fun audioDeleteWhenEmptyAndAudioFilesFallback() {
        val noAudio = SongPayload.songPayload(
            Song(id = "s1", title = "Hymn"),
            TS, DEL, rawDate = Date(1), includeCreatedAt = true,
        )
        assertEquals(DEL, noAudio["audio"]) // delete-when-empty
        assertEquals(TS, noAudio["createdAt"])

        // audioFiles falls back to [audio] on read when only the primary take is stored
        val audio = SongAudio(id = "a1", storagePath = "campzone/audio/a1", downloadUrl = "https://cdn/a1.mp3")
        val withAudio = SongPayload.songPayload(
            Song(id = "s1", title = "Hymn", audio = audio, audioFiles = emptyList()),
            TS, DEL, rawDate = Date(1), includeCreatedAt = false,
        ).toMutableMap()
        // simulate stored doc: primary audio present, audioFiles empty
        val decoded = withAudio.toSongOrNull("s1")
        assertEquals(1, decoded.audioFiles.size)
        assertEquals("a1", decoded.audioFiles.first().id)
    }

    @Test
    fun voiceKitsNormalizeLegacyTracksAndPersistMainAudio() {
        val legacy = listOf(
            SongAudio(id = "legacy", fileName = "old.mp3", voiceType = ""),
            SongAudio(id = "tenor", fileName = "tenor.mp3", voiceType = "tenor"),
        )
        val song = Song(id = "s1", title = "Hymn", audioFiles = legacy)

        assertEquals(SongAudioTrackType.MainSong, song.mainAudio?.trackType)
        assertEquals(listOf(SongAudioTrackType.MainSong, SongAudioTrackType.Tenor), song.orderedAudioFiles.map { it.trackType })

        val payload = SongPayload.songPayload(song, TS, DEL, Date(1), includeCreatedAt = false)
        @Suppress("UNCHECKED_CAST")
        val audioFiles = payload["audioFiles"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val primary = payload["audio"] as Map<String, Any?>
        assertEquals("mainSong", audioFiles.first()["voiceType"])
        assertEquals("legacy", primary["id"])
    }

    @Test
    fun voiceKitCustomNameRoundTrips() {
        val track = SongAudio(
            id = "demo",
            fileName = "demo.wav",
            voiceType = SongAudioTrackType.Other.wireValue,
            displayName = "Click track",
        )
        val payload = SongPayload.songPayload(
            Song(id = "s1", title = "Hymn", audioFiles = listOf(track)),
            TS,
            DEL,
            Date(1),
            includeCreatedAt = false,
        )
        val decoded = payload.toSongOrNull("s1")
        assertEquals("Click track", decoded.audioFiles.single().displayName)
    }

    @Test
    fun lossyOriginalKeyCollapsesUnknownToCMajor() {
        assertEquals("G", decodeOriginalKey("G"))
        assertEquals("Bb", decodeOriginalKey("B♭"))
        assertEquals("Am", decodeOriginalKey("A minor"))
        assertEquals("F#", decodeOriginalKey("F#"))
        assertEquals("Bbm", decodeOriginalKey("Bb minor"))
        assertEquals("C", decodeOriginalKey("H")) // unknown -> C major
        assertEquals("C", decodeOriginalKey(null))
    }

    @Test
    fun songRoundTripsWithChordSheet() {
        val original = Song(
            id = "s1",
            title = "Amazing Grace",
            composer = "John Newton",
            lyrics = "Amazing grace...",
            orderIndex = 3,
            isPinnedTheme = true,
            favoriteUserIds = listOf("u1", "u2"),
            lyricsParts = listOf(SongLyricsPart(id = "lp1", kind = SongLyricsPartKind.Chorus, number = 1, text = "...")),
            chordSheet = ChordSheet(
                id = "cs1",
                originalKey = "G",
                tempo = 72,
                lines = listOf(
                    ChordLine(id = "l1", text = "Amazing grace", chords = listOf(Chord(id = "c1", chord = "G", position = 0))),
                ),
            ),
        )
        val payload = SongPayload.songPayload(original, Date(9), DEL, rawDate = Date(5), includeCreatedAt = false)
        val decoded = payload.toSongOrNull("s1")

        assertEquals(original.title, decoded.title)
        assertEquals(3, decoded.orderIndex)
        assertTrue(decoded.isPinnedTheme)
        assertEquals(listOf("u1", "u2"), decoded.favoriteUserIds)
        assertEquals(SongLyricsPartKind.Chorus, decoded.lyricsParts.first().kind)
        assertEquals("G", decoded.chordSheet.originalKey)
        assertEquals(72, decoded.chordSheet.tempo)
        assertEquals("G", decoded.chordSheet.lines.first().chords.first().chord)
    }

    @Test
    fun songPayloadRoundTripsCanonicalCatalogFields() {
        val updatedAt = Date(12)
        val song = Song(
            id = "s1",
            title = "Catalog Song",
            chordedLyrics = "{Verse}\nA[G]mazing grace",
            cantusSlug = "amazing-grace",
            pdfLink = "https://cdn.example.org/sheet.pdf",
            pptxLink = "https://cdn.example.org/slides.pptx",
            chordSheet = ChordSheet(
                id = "cs1",
                updatedAt = updatedAt,
                lines = listOf(
                    ChordLine(
                        id = "l1",
                        text = "Amazing grace",
                        chords = listOf(Chord(id = "c1", chord = "G", position = 1)),
                    ),
                ),
            ),
        )

        val payload = SongPayload.songPayload(song, TS, DEL, rawDate = Date(1), includeCreatedAt = false)
        val decoded = payload.toSongOrNull("s1")

        assertEquals("{Verse}\nA[G]mazing grace", payload["chordedLyrics"])
        assertEquals(updatedAt, payload["chordedLyricsUpdatedAt"])
        assertEquals("amazing-grace", decoded.cantusSlug)
        assertEquals("https://cdn.example.org/slides.pptx", decoded.pptxLink)
        assertEquals("Amazing grace", decoded.chordSheet.lines[1].text)
        assertEquals("G", decoded.chordSheet.lines[1].chords.single().chord)
    }

    @Test
    fun chordedLyricsOverridesOlderStructuredSheet() {
        val decoded = mapOf(
            "title" to "Catalog Song",
            "chordedLyrics" to "{Verse}\nA[G]mazing grace",
            "chordedLyricsUpdatedAt" to Date(20_000),
            "chordSheet" to chordSheetMap(
                updatedAt = Date(10_000),
                text = "Old words",
                chord = "C",
            ),
        ).toSongOrNull("s1")

        assertEquals("Verse", decoded.chordSheet.lines.first().text)
        assertEquals("Amazing grace", decoded.chordSheet.lines[1].text)
        assertEquals("G", decoded.chordSheet.lines[1].chords.single().chord)
    }

    @Test
    fun newerStructuredSheetWinsOverStaleChordedLyrics() {
        val decoded = mapOf(
            "title" to "Edited Song",
            "chordedLyrics" to "{Verse}\nA[G]mazing grace",
            "chordedLyricsUpdatedAt" to Date(10_000),
            "chordSheet" to chordSheetMap(
                updatedAt = Date(20_000),
                text = "Edited words",
                chord = "D",
            ),
        ).toSongOrNull("s1")

        assertEquals("Edited words", decoded.chordSheet.lines.single().text)
        assertEquals("D", decoded.chordSheet.lines.single().chords.single().chord)
    }

    private fun chordSheetMap(updatedAt: Date, text: String, chord: String): Map<String, Any?> =
        mapOf(
            "id" to "cs1",
            "originalKey" to "C",
            "updatedAt" to updatedAt,
            "lines" to listOf(
                mapOf(
                    "id" to "l1",
                    "text" to text,
                    "chords" to listOf(
                        mapOf(
                            "id" to "c1",
                            "chord" to chord,
                            "position" to 0,
                        ),
                    ),
                ),
            ),
        )

    private companion object {
        const val TS = "serverTimestamp"
        const val DEL = "delete"
    }
}

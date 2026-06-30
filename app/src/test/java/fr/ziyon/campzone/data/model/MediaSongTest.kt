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
        assertEquals(TS, payload["uploadedAt"])
        assertFalse(payload.containsKey("thumbnailURL")) // omit-when-nil
        assertFalse(payload.containsKey("width"))

        val decoded = payload.toMediaItemOrNull("m1")!!
        assertEquals(MediaKind.Photo, decoded.kind)
        assertEquals("Sunset", decoded.caption)

        val broken = payload.toMutableMap().apply { remove("secureURL") }
        assertNull(broken.toMediaItemOrNull("m1"))
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
        assertFalse(decoded.allows(UserRole.Guest))
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
        assertEquals("C", decodeOriginalKey("F#")) // unknown → C major
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

    private companion object {
        const val TS = "serverTimestamp"
        const val DEL = "delete"
    }
}

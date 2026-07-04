package fr.ziyon.campzone.ui.songbook

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.Song
import fr.ziyon.campzone.data.model.SongAudioTrackType
import fr.ziyon.campzone.data.model.SongLyricsPart
import fr.ziyon.campzone.data.model.SongLyricsPartKind
import fr.ziyon.campzone.data.songbook.FakeSongbookService
import fr.ziyon.campzone.testing.FakeStringProvider
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SongbookViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadPublishesPinnedSongFirst() = runTest {
        val viewModel = viewModel()

        viewModel.load(campingId, admin)
        advanceUntilIdle()

        val state = viewModel.uiState.value as SongbookUiState.Loaded
        assertEquals("campzone-theme", state.songs.first().id)
        assertEquals("Hino Campzone", viewModel.pinnedSong(campingId)?.title)
    }

    @Test
    fun saveSongValidatesRequiredFields() = runTest {
        val viewModel = viewModel()
        viewModel.load(campingId, admin)
        advanceUntilIdle()
        viewModel.prepareNewSong(campingId)

        viewModel.saveSong(campingId)
        advanceUntilIdle()

        assertEquals("Song title is required.", viewModel.operationError.value)
        assertNull(viewModel.songById("", campingId))
    }

    @Test
    fun saveSongAddsPendingAudioAndPinnedTheme() = runTest {
        val viewModel = viewModel()
        viewModel.load(campingId, admin)
        advanceUntilIdle()
        viewModel.prepareNewSong(campingId)
        viewModel.updateForm {
            it.copy(
                title = "Campfire Blessing",
                lyrics = "Bless this campfire and every camper.",
                chordSheetText = "[D] Bless this campfire",
                isPinnedTheme = true,
            )
        }
        val accepted = viewModel.addPendingAudio(
            fileName = "campfire-blessing.mp3",
            contentType = "audio/mpeg",
            bytes = "audio".toByteArray(),
        )

        viewModel.saveSong(campingId)
        advanceUntilIdle()

        assertTrue(accepted)
        val saved = viewModel.songs(campingId).first { it.title == "Campfire Blessing" }
        assertEquals("campfire-blessing.mp3", saved.audio?.fileName)
        assertEquals("Campfire Blessing", viewModel.pinnedSong(campingId)?.title)
    }

    @Test
    fun saveSongPersistsChordedLyricsAndPptxLink() = runTest {
        val viewModel = viewModel()
        viewModel.load(campingId, admin)
        advanceUntilIdle()
        viewModel.prepareNewSong(campingId)
        viewModel.updateForm {
            it.copy(
                title = "Catalog Format",
                chordSheetText = "{Verse}\nA[G]mazing grace",
                pdfLink = "https://cdn.example.org/catalog-format.pdf",
                pptxLink = "https://cdn.example.org/catalog-format.pptx",
            )
        }

        viewModel.saveSong(campingId)
        advanceUntilIdle()

        val saved = viewModel.songs(campingId).first { it.title == "Catalog Format" }
        assertEquals("{Verse}\nA[G]mazing grace", saved.chordedLyrics)
        assertEquals("[Verse]\n G\nAmazing grace", saved.chords)
        assertEquals("https://cdn.example.org/catalog-format.pptx", saved.pptxLink)
    }

    @Test
    fun editingSongPreservesStructuredLyricsParts() = runTest {
        val structuredSong = Song(
            id = "structured",
            title = "Structured Song",
            lyrics = "[Verse 1]\nWalk in faith\n\n[Chorus 1]\nSing with joy",
            lyricsParts = listOf(
                SongLyricsPart(
                    id = "verse",
                    kind = SongLyricsPartKind.Verse,
                    number = 1,
                    text = "Walk in faith",
                ),
                SongLyricsPart(
                    id = "chorus",
                    kind = SongLyricsPartKind.Chorus,
                    number = 1,
                    text = "Sing with joy",
                ),
            ),
            orderIndex = 3,
        )
        val viewModel = viewModel(FakeSongbookService.previewSongs(campingId) + structuredSong)
        viewModel.load(campingId, admin)
        advanceUntilIdle()

        viewModel.prepareEditingSong(structuredSong)

        assertEquals(
            listOf("Verse 1", "Chorus 1"),
            viewModel.form.value.lyricsParts.map { it.displayTitle() },
        )
        assertEquals(
            "[Verse 1]\nWalk in faith\n\n[Chorus 1]\nSing with joy",
            viewModel.form.value.lyrics,
        )
    }

    @Test
    fun lyricsPartEditorAddsUpdatesMovesAndSavesParts() = runTest {
        val viewModel = viewModel()
        viewModel.load(campingId, admin)
        advanceUntilIdle()
        viewModel.prepareNewSong(campingId)
        viewModel.updateForm { it.copy(title = "Structured Praise") }

        viewModel.updateLyricsPartKind(SongLyricsPartKind.Chorus)
        viewModel.updateLyricsPartText("Sing with joy")
        viewModel.saveLyricsPart()
        val chorusId = viewModel.form.value.lyricsParts.single().id

        viewModel.updateLyricsPartKind(SongLyricsPartKind.Verse)
        viewModel.updateLyricsPartText("Walk in faith")
        viewModel.saveLyricsPart()
        val verseId = viewModel.form.value.lyricsParts.last().id
        viewModel.moveLyricsPart(verseId, SongMoveDirection.Up)

        viewModel.startLyricsPartEditor(chorusId)
        viewModel.updateLyricsPartText("Sing with courage")
        viewModel.saveLyricsPart()

        viewModel.saveSong(campingId)
        advanceUntilIdle()

        val saved = viewModel.songs(campingId).first { it.title == "Structured Praise" }
        assertEquals(
            listOf(SongLyricsPartKind.Verse, SongLyricsPartKind.Chorus),
            saved.lyricsParts.map { it.kind },
        )
        assertEquals(listOf("Walk in faith", "Sing with courage"), saved.lyricsParts.map { it.text })
        assertEquals("[Verse 1]\nWalk in faith\n\n[Chorus 1]\nSing with courage", saved.lyrics)
    }

    @Test
    fun nonAdminsCannotSaveSongs() = runTest {
        val viewModel = viewModel()
        viewModel.load(campingId, regularUser)
        advanceUntilIdle()
        viewModel.prepareNewSong(campingId)
        viewModel.updateForm {
            it.copy(
                title = "Unauthorized Song",
                lyrics = "This should not be saved.",
            )
        }

        viewModel.saveSong(campingId)
        advanceUntilIdle()

        assertEquals("Only admins can manage the songbook.", viewModel.operationError.value)
        assertNull(viewModel.songs(campingId).firstOrNull { it.title == "Unauthorized Song" })
    }

    @Test
    fun rejectsUnsupportedPendingAudio() = runTest {
        val viewModel = viewModel()

        val accepted = viewModel.addPendingAudio(
            fileName = "lyrics.txt",
            contentType = "text/plain",
            bytes = "not audio".toByteArray(),
        )

        assertFalse(accepted)
        assertTrue(viewModel.form.value.pendingAudioFiles.isEmpty())
        assertEquals(
            "Choose a supported audio file: MP3, M4A, AAC, or WAV.",
            viewModel.operationError.value,
        )
    }

    @Test
    fun audioTakesGetUniqueVoiceKitTypes() = runTest {
        val viewModel = viewModel()
        viewModel.prepareNewSong(campingId)

        viewModel.addPendingAudio("main.mp3", "audio/mpeg", "a".toByteArray())
        viewModel.addPendingAudio("backing.mp3", "audio/mpeg", "b".toByteArray())
        viewModel.addPendingAudio("tenor.mp3", "audio/mpeg", "c".toByteArray())
        val ids = viewModel.form.value.pendingAudioFiles.map { it.id }

        assertEquals(
            listOf(SongAudioTrackType.MainSong, SongAudioTrackType.Playback, SongAudioTrackType.Instrumental),
            viewModel.form.value.pendingAudioFiles.map { it.trackType },
        )
        assertTrue(viewModel.updateAudioTrackType(ids[2], SongAudioTrackType.Tenor))
        assertFalse(viewModel.updateAudioTrackType(ids[1], SongAudioTrackType.Tenor))
        assertEquals(SongAudioTrackType.Playback, viewModel.form.value.pendingAudioFiles[1].trackType)
    }

    @Test
    fun otherVoiceKitsAllowCustomNamesAndMainRemovalPromotesNext() = runTest {
        val viewModel = viewModel()
        viewModel.prepareNewSong(campingId)
        viewModel.addPendingAudio("main.mp3", "audio/mpeg", "a".toByteArray())
        viewModel.addPendingAudio("demo.mp3", "audio/mpeg", "b".toByteArray())
        val mainId = viewModel.form.value.pendingAudioFiles.first().id
        val demoId = viewModel.form.value.pendingAudioFiles.last().id

        assertTrue(viewModel.updateAudioTrackType(demoId, SongAudioTrackType.Other, "Click track"))
        assertEquals("Click track", viewModel.form.value.pendingAudioFiles.last().displayName)

        viewModel.removeAudio(mainId)

        assertEquals(SongAudioTrackType.MainSong, viewModel.form.value.pendingAudioFiles.single().trackType)
        assertEquals("", viewModel.form.value.pendingAudioFiles.single().displayName)
    }

    @Test
    fun remoteAudioValidatesDeduplicatesAndOpensVoiceKitEditor() = runTest {
        val viewModel = viewModel()
        viewModel.prepareNewSong(campingId)

        assertFalse(viewModel.addRemoteAudio("not-a-url"))
        assertTrue(viewModel.addRemoteAudio("https://cdn.example.org/audio/main.mp3"))
        val attached = viewModel.form.value.existingAudioFiles.single()
        assertEquals("main.mp3", attached.fileName)
        assertEquals(SongAudioTrackType.MainSong, attached.trackType)
        assertEquals(attached.id, viewModel.trackTypeEditorAudioId.value)
        assertFalse(viewModel.addRemoteAudio("https://cdn.example.org/audio/main.mp3"))
    }

    @Test
    fun favoritesCanBeToggledForSignedInUser() = runTest {
        val viewModel = viewModel()
        viewModel.load(campingId, admin)
        advanceUntilIdle()

        viewModel.toggleFavorite("por-toda-terra", campingId, "preview-user")
        advanceUntilIdle()

        assertTrue(viewModel.songById("por-toda-terra", campingId)?.isFavoritedBy("preview-user") == true)
    }

    @Test
    fun movingSongUpdatesOrder() = runTest {
        val viewModel = viewModel()
        viewModel.load(campingId, admin)
        advanceUntilIdle()

        viewModel.moveSong("maranata", SongMoveDirection.Up, campingId)
        advanceUntilIdle()

        assertEquals(
            listOf("campzone-theme", "maranata", "por-toda-terra"),
            viewModel.songs(campingId).map { it.id },
        )
    }

    private fun viewModel(
        songs: List<Song> = FakeSongbookService.previewSongs(campingId),
    ): SongbookViewModel =
        SongbookViewModel(
            songbookService = FakeSongbookService(
                mapOf(campingId to songs),
            ),
            campingService = FakeCampingService(listOf(camping())),
            stringProvider = FakeStringProvider(),
        )

    private fun camping(): Camping = Camping(
        id = campingId,
        title = "Summer Camp 2026",
        description = "Camp",
        startDate = Date(1),
        endDate = Date(2),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Paris",
        registrationStatus = CampingRegistrationStatus.Open,
    )

    private companion object {
        const val campingId = "summer-camp-2026"
        val admin = AuthenticatedUser(
            uid = "admin",
            email = "admin@example.com",
            displayName = "Admin",
            photoUrl = null,
            role = UserRole.Admin,
            church = "Paris Central SDA",
            age = 30,
            preferredLanguage = "en",
            gender = null,
            onboardingCompleted = true,
        )
        val regularUser = admin.copy(
            uid = "user",
            email = "user@example.com",
            displayName = "User",
            role = UserRole.User,
        )
    }
}

package fr.ziyon.campzone.ui.songbook

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.songbook.FakeSongbookService
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

    private fun viewModel(): SongbookViewModel =
        SongbookViewModel(
            songbookService = FakeSongbookService(
                mapOf(campingId to FakeSongbookService.previewSongs(campingId)),
            ),
            campingService = FakeCampingService(listOf(camping())),
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

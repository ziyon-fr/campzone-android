package fr.ziyon.campzone.ui.album

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.album.FakeAlbumService
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.media.AudioUploader
import fr.ziyon.campzone.data.media.CloudinaryUploadResult
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.model.AlbumSettings
import fr.ziyon.campzone.data.model.MediaItem
import fr.ziyon.campzone.data.model.MediaKind
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadPublishesMediaAndDefaultSettings() = runTest {
        val service = FakeAlbumService(
            initialMedia = listOf(media(id = "old", uploadedAt = Date(1)), media(id = "new", uploadedAt = Date(2))),
        )
        val viewModel = AlbumViewModel(service, FakeImageUploader(), FakeAudioUploader())

        viewModel.load("camp-1")
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as AlbumUiState.Loaded
        assertEquals(listOf("new", "old"), loaded.media.map { it.id })
        assertTrue(loaded.settings.allowedUploadRoles.contains(UserRole.Photographer))
    }

    @Test
    fun uploadPhotoWritesCloudinaryMetadataAndThumbnail() = runTest {
        val service = FakeAlbumService()
        val viewModel = AlbumViewModel(service, FakeImageUploader(), FakeAudioUploader())
        viewModel.load("camp-1")
        advanceUntilIdle()

        viewModel.uploadMedia(
            campingId = "camp-1",
            kind = MediaKind.Photo,
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            fileExtension = "jpg",
            caption = "  Sunset  ",
            uploader = user(),
        )
        advanceUntilIdle()

        val saved = service.loadMedia("camp-1").single()
        assertEquals("Sunset", saved.caption)
        assertEquals("cloudinary/photo", saved.publicId)
        assertTrue(saved.thumbnailUrl.orEmpty().contains("f_auto,q_auto,w_600,c_limit"))
    }

    @Test
    fun roleSettingsPersistThroughService() = runTest {
        val service = FakeAlbumService(initialSettings = mapOf("camp-1" to AlbumSettings(listOf(UserRole.Admin))))
        val viewModel = AlbumViewModel(service, FakeImageUploader(), FakeAudioUploader())
        viewModel.load("camp-1")
        advanceUntilIdle()

        viewModel.setRoleAllowed("camp-1", UserRole.Adult, true)
        advanceUntilIdle()

        assertTrue(service.loadSettings("camp-1").allowedUploadRoles.contains(UserRole.Adult))
    }

    private fun media(id: String, uploadedAt: Date) = MediaItem(
        id = id,
        campingId = "camp-1",
        kind = MediaKind.Photo,
        secureUrl = "https://res.cloudinary.com/demo/image/upload/$id.jpg",
        publicId = id,
        uploaderId = "u1",
        uploaderName = "Maria",
        uploadedAt = uploadedAt,
    )

    private fun user() = AuthenticatedUser(
        uid = "u1",
        email = "maria@example.com",
        displayName = "Maria",
        photoUrl = null,
        role = UserRole.Photographer,
        church = "Paris Central",
        age = 30,
        preferredLanguage = "en",
        gender = null,
        onboardingCompleted = true,
    )
}

private class FakeImageUploader : ImageUploader {
    override suspend fun uploadImage(
        assetIdPrefix: String,
        folder: String,
        tags: List<String>,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): CloudinaryUploadResult = CloudinaryUploadResult(
        secureUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg",
        publicId = "cloudinary/photo",
        width = 1200,
        height = 800,
    )
}

private class FakeAudioUploader : AudioUploader {
    override suspend fun uploadAudio(
        assetIdPrefix: String,
        folder: String,
        tags: List<String>,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): CloudinaryUploadResult = CloudinaryUploadResult(
        secureUrl = "https://res.cloudinary.com/demo/video/upload/clip.mp4",
        publicId = "cloudinary/video",
        duration = 12.0,
    )
}

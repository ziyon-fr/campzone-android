package fr.ziyon.campzone.ui.profile

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.church.FakeChurchDirectory
import fr.ziyon.campzone.data.profile.FakeUserProfileRepository
import fr.ziyon.campzone.data.profile.sampleUserProfile
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authenticatedUser = AuthenticatedUser(
        uid = "preview-user",
        email = "guest@campzone.app",
        displayName = "Campzone Guest",
        photoUrl = null,
        role = UserRole.User,
        church = "Paris Central SDA",
        age = 22,
        preferredLanguage = "fr",
        gender = UserGender.PreferNotToSay,
        onboardingCompleted = true,
    )

    private fun viewModel(
        repository: FakeUserProfileRepository = FakeUserProfileRepository(),
        church: FakeChurchDirectory = FakeChurchDirectory(),
    ) = ProfileViewModel(repository, church)

    @Test
    fun loadPopulatesFormFromRepository() = runTest {
        val viewModel = viewModel()

        viewModel.load(authenticatedUser)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Campzone Guest", state.form.displayName)
        assertEquals("22", state.form.ageText)
        assertEquals(UserRole.User, state.form.role)
        assertTrue(state.canEditRole)
        assertNull(state.errorMessage)
    }

    @Test
    fun loadSurfacesErrorWhenRepositoryFails() = runTest {
        val viewModel = viewModel(FakeUserProfileRepository(shouldFail = true))

        viewModel.load(authenticatedUser)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.loadedUser)
        assertFalse(state.errorMessage.isNullOrBlank())
    }

    @Test
    fun saveWithBlankDisplayNameProducesValidationError() = runTest {
        val viewModel = viewModel()
        viewModel.load(authenticatedUser)

        viewModel.updateDisplayName("   ")
        viewModel.save()

        val state = viewModel.uiState.value
        assertTrue(state.validationErrors.contains(ProfileValidationError.DisplayNameRequired))
        assertFalse(state.isSaving)
    }

    @Test
    fun saveWithUnderageValueProducesOutOfRangeError() = runTest {
        val viewModel = viewModel()
        viewModel.load(authenticatedUser)

        viewModel.updateAgeText("5")
        viewModel.save()

        assertTrue(
            viewModel.uiState.value.validationErrors
                .contains(ProfileValidationError.AgeOutOfRange),
        )
    }

    @Test
    fun saveSuccessPersistsCleanedProfileAndClearsErrors() = runTest {
        val repository = FakeUserProfileRepository()
        val viewModel = viewModel(repository)
        viewModel.load(authenticatedUser)

        viewModel.updateDisplayName("Renamed Camper")
        viewModel.save()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.validationErrors.isEmpty())
        assertEquals("Renamed Camper", state.loadedUser?.displayName)
        assertEquals("Renamed Camper", repository.user.displayName)
        assertTrue(repository.user.onboardingCompleted)
    }

    @Test
    fun saveFailureSurfacesErrorMessage() = runTest {
        val repository = FakeUserProfileRepository()
        val viewModel = viewModel(repository)
        viewModel.load(authenticatedUser)

        repository.shouldFail = true
        viewModel.save()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertFalse(state.errorMessage.isNullOrBlank())
    }

    @Test
    fun uploadPhotoPersistsReturnedUrls() = runTest {
        val viewModel = viewModel()
        viewModel.load(authenticatedUser)

        viewModel.uploadPhoto(bytes = byteArrayOf(1, 2, 3), mimeType = "image/jpeg", fileExtension = "jpg")

        val state = viewModel.uiState.value
        assertFalse(state.isUploadingPhoto)
        assertEquals("https://cdn.example/preview-user.jpg", state.loadedUser?.photoUrl)
        assertNull(state.photoError)
    }

    @Test
    fun requestAccountDeletionMarksPendingAndInvokesSignOut() = runTest {
        val viewModel = viewModel()
        viewModel.load(authenticatedUser)

        var signedOut = false
        viewModel.requestAccountDeletion(onSuccess = { signedOut = true })

        val state = viewModel.uiState.value
        assertTrue(state.isPendingDeletion)
        assertFalse(state.isProcessingDeletion)
        assertTrue(signedOut)
    }

    @Test
    fun cancelAccountDeletionClearsPendingFlag() = runTest {
        val repository = FakeUserProfileRepository(
            sampleUserProfile().copy(pendingDeletionAt = Date()),
        )
        val viewModel = viewModel(repository)
        viewModel.load(authenticatedUser)
        assertTrue(viewModel.uiState.value.isPendingDeletion)

        viewModel.cancelAccountDeletion()

        assertFalse(viewModel.uiState.value.isPendingDeletion)
    }

    @Test
    fun leadershipRoleIsReadOnly() = runTest {
        val repository = FakeUserProfileRepository(
            sampleUserProfile().copy(role = UserRole.Leader),
        )
        val viewModel = viewModel(repository)

        viewModel.load(authenticatedUser.copy(role = UserRole.Leader))

        assertFalse(viewModel.uiState.value.canEditRole)
    }
}

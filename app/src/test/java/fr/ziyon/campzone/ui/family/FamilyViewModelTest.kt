package fr.ziyon.campzone.ui.family

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.church.FakeChurchDirectory
import fr.ziyon.campzone.data.family.FakeFamilyRepository
import fr.ziyon.campzone.data.family.FamilyParticipantDuplicateMatch
import fr.ziyon.campzone.data.family.sampleChild
import fr.ziyon.campzone.data.media.FakeImageUploader
import fr.ziyon.campzone.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val adultUser = AuthenticatedUser(
        uid = "guardian-1",
        email = "guardian@example.com",
        displayName = "Guardian One",
        photoUrl = null,
        role = UserRole.Adult,
        church = "Paris Central SDA",
        age = 40,
        preferredLanguage = "fr",
        gender = UserGender.PreferNotToSay,
        onboardingCompleted = true,
    )

    private fun viewModel(
        repository: FakeFamilyRepository = FakeFamilyRepository(),
        uploader: FakeImageUploader = FakeImageUploader(),
        church: FakeChurchDirectory = FakeChurchDirectory(),
    ) = FamilyViewModel(repository, uploader, church)

    @Test
    fun loadShowsChildrenForAdult() = runTest {
        val repository = FakeFamilyRepository(mapOf("guardian-1" to listOf(sampleChild())))
        val viewModel = viewModel(repository)

        viewModel.load(adultUser)

        val state = viewModel.uiState.value
        assertTrue(state.canManageFamily)
        assertTrue(state.listState is FamilyListState.Loaded)
        assertEquals(1, state.children.size)
    }

    @Test
    fun loadShowsEmptyWhenNoChildren() = runTest {
        val viewModel = viewModel()
        viewModel.load(adultUser)
        assertEquals(FamilyListState.Empty, viewModel.uiState.value.listState)
    }

    @Test
    fun basicUserCannotManageFamily() = runTest {
        val viewModel = viewModel(FakeFamilyRepository(mapOf("guardian-1" to listOf(sampleChild()))))
        viewModel.load(adultUser.copy(role = UserRole.User))
        assertFalse(viewModel.uiState.value.canManageFamily)
    }

    @Test
    fun loadFailureShowsError() = runTest {
        val viewModel = viewModel(FakeFamilyRepository(shouldFail = true))
        viewModel.load(adultUser)
        assertTrue(viewModel.uiState.value.listState is FamilyListState.Error)
    }

    @Test
    fun openEditorForNewUsesGuardianDefaults() = runTest {
        val viewModel = viewModel()
        viewModel.load(adultUser)
        viewModel.openEditor(childId = null, user = adultUser)

        val editor = viewModel.uiState.value.editor
        assertNotNull(editor)
        assertFalse(editor!!.isEditing)
        assertEquals("Paris Central SDA", editor.form.church)
    }

    @Test
    fun openEditorForExistingLoadsChild() = runTest {
        val repository = FakeFamilyRepository(mapOf("guardian-1" to listOf(sampleChild())))
        val viewModel = viewModel(repository)
        viewModel.load(adultUser)
        viewModel.openEditor(childId = "child-1", user = adultUser)

        val editor = viewModel.uiState.value.editor
        assertNotNull(editor)
        assertTrue(editor!!.isEditing)
        assertEquals("Ana Santos", editor.form.displayName)
        assertEquals("10", editor.form.ageText)
    }

    @Test
    fun saveWithInvalidFormSurfacesValidationErrors() = runTest {
        val viewModel = viewModel()
        viewModel.load(adultUser)
        viewModel.openEditor(childId = null, user = adultUser)
        viewModel.save(adultUser, onSaved = {})

        val editor = viewModel.uiState.value.editor
        assertNotNull(editor)
        assertTrue(editor!!.validationErrors.isNotEmpty())
    }

    @Test
    fun saveValidParticipantPersistsAndClosesEditor() = runTest {
        val repository = FakeFamilyRepository()
        val viewModel = viewModel(repository)
        viewModel.load(adultUser)
        viewModel.openEditor(childId = null, user = adultUser)
        fillValidForm(viewModel)

        var saved = false
        viewModel.save(adultUser, onSaved = { saved = true })

        assertTrue(saved)
        assertNull(viewModel.uiState.value.editor)
        assertEquals(FamilyFeedback.Saved, viewModel.uiState.value.feedback)
        assertEquals(1, repository.store["guardian-1"]?.size)
    }

    @Test
    fun saveDetectsLocalDuplicateThenConfirms() = runTest {
        val repository = FakeFamilyRepository(mapOf("guardian-1" to listOf(sampleChild())))
        val viewModel = viewModel(repository)
        viewModel.load(adultUser)
        viewModel.openEditor(childId = null, user = adultUser)
        fillValidForm(viewModel)

        viewModel.save(adultUser, onSaved = {})
        assertNotNull(viewModel.uiState.value.editor?.pendingDuplicate)
        assertEquals(1, repository.store["guardian-1"]?.size)

        viewModel.confirmDuplicateSave(adultUser, onSaved = {})
        assertNull(viewModel.uiState.value.editor)
        assertEquals(2, repository.store["guardian-1"]?.size)
    }

    @Test
    fun saveDetectsCrossGuardianDuplicate() = runTest {
        val repository = FakeFamilyRepository(
            crossGuardianMatch = FamilyParticipantDuplicateMatch(
                displayName = "Ana Santos",
                age = 10,
                guardianDisplayName = "Uncle Mateo",
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.load(adultUser)
        viewModel.openEditor(childId = null, user = adultUser)
        fillValidForm(viewModel)

        viewModel.save(adultUser, onSaved = {})

        assertEquals("Uncle Mateo", viewModel.uiState.value.editor?.pendingDuplicate?.guardianDisplayName)
        assertTrue(repository.store[adultUser.uid].isNullOrEmpty())
    }

    @Test
    fun duplicateLookupFailureBlocksSave() = runTest {
        val repository = FakeFamilyRepository(duplicateLookupShouldFail = true)
        val viewModel = viewModel(repository)
        viewModel.load(adultUser)
        viewModel.openEditor(childId = null, user = adultUser)
        fillValidForm(viewModel)

        viewModel.save(adultUser, onSaved = {})

        assertNotNull(viewModel.uiState.value.editor?.errorMessage)
        assertTrue(repository.store[adultUser.uid].isNullOrEmpty())
    }

    @Test
    fun deleteRemovesChild() = runTest {
        val repository = FakeFamilyRepository(mapOf("guardian-1" to listOf(sampleChild())))
        val viewModel = viewModel(repository)
        viewModel.load(adultUser)

        viewModel.deleteChild(sampleChild(), adultUser)

        assertEquals(FamilyListState.Empty, viewModel.uiState.value.listState)
        assertEquals(FamilyFeedback.Removed, viewModel.uiState.value.feedback)
        assertTrue(repository.store["guardian-1"].isNullOrEmpty())
    }

    @Test
    fun uploadPhotoStoresParticipantUrl() = runTest {
        val uploader = FakeImageUploader()
        val viewModel = viewModel(uploader = uploader)
        viewModel.load(adultUser)
        viewModel.openEditor(childId = null, user = adultUser)
        viewModel.uploadPhoto(byteArrayOf(1, 2, 3), "image/jpeg", "jpg")

        val editor = viewModel.uiState.value.editor
        assertNotNull(editor?.form?.photoUrl)
        assertEquals("campzone/participants", uploader.lastFolder)
    }

    @Test
    fun loadChurchesPopulatesPicker() = runTest {
        val viewModel = viewModel()
        viewModel.load(adultUser)
        viewModel.openEditor(childId = null, user = adultUser)
        viewModel.loadChurches()

        assertTrue(viewModel.uiState.value.churches.isNotEmpty())
        assertTrue(viewModel.uiState.value.filteredChurchGroups.isNotEmpty())
    }

    private fun fillValidForm(viewModel: FamilyViewModel) {
        viewModel.updateForm {
            copy(
                displayName = "Ana Santos",
                ageText = "10",
                emergencyContactName = "Maria Santos",
                emergencyContactPhone = "+33 1 00 00 00 00",
                hasGuardianConsent = true,
            )
        }
    }
}

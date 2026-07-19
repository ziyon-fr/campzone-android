package fr.ziyon.campzone.ui.teams

import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingStaffRole
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.StaffCapability
import fr.ziyon.campzone.data.model.StaffRoleKind
import fr.ziyon.campzone.data.model.StaffRoleMember
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaffRoleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun memberScopeOnlyPublishesAssignedOperationsTeams() = runTest {
        val viewModel = StaffRoleViewModel(service())

        viewModel.start(CampingId, MemberId, canManageAll = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value as StaffRolesUiState.Loaded
        assertEquals(listOf("worship"), state.roles.map { it.id })
    }

    @Test
    fun managerScopePublishesEveryOperationsTeam() = runTest {
        val viewModel = StaffRoleViewModel(service())

        viewModel.start(CampingId, "manager", canManageAll = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as StaffRolesUiState.Loaded
        assertEquals(listOf("kitchen", "worship"), state.roles.map { it.id })
    }

    @Test
    fun editorAssignsApprovedParticipantTitleAndSavesNormalizedDraft() = runTest {
        val viewModel = StaffRoleViewModel(service())
        val attendee = attendee()
        viewModel.prepareNew()
        viewModel.updateForm {
            it.copy(
                name = "  Reception  ",
                kind = StaffRoleKind.Reception,
                colorHex = "118AB2",
                capabilities = listOf(StaffCapability.ManageCheckIns),
            )
        }
        viewModel.setMember(attendee, selected = true)
        viewModel.updateMemberTitle(MemberId, "Shift lead")

        var saved: CampingStaffRole? = null
        viewModel.save(CampingId, "creator") { saved = it }
        advanceUntilIdle()

        assertEquals("Reception", saved?.name)
        assertEquals("#118AB2", saved?.colorHex)
        assertEquals("Shift lead", saved?.members?.single()?.title)
        assertEquals(listOf(StaffCapability.ManageCheckIns), saved?.capabilities)
        assertEquals(StaffRoleOperationMessage.Saved, viewModel.operationMessage.value)
        assertFalse(viewModel.isSaving.value)
    }

    @Test
    fun blankNameCannotBeSaved() = runTest {
        val viewModel = StaffRoleViewModel(service())
        var callbackRan = false

        viewModel.prepareNew()
        viewModel.save(CampingId, "creator") { callbackRan = true }
        advanceUntilIdle()

        assertFalse(callbackRan)
        assertTrue(viewModel.form.value.name.isBlank())
    }

    private fun service() = FakeTeamService(
        teams = mutableListOf(),
        staffRoles = mutableListOf(
            CampingStaffRole(
                id = "worship",
                campingId = CampingId,
                name = "Worship",
                kind = StaffRoleKind.Worship,
                members = listOf(StaffRoleMember(MemberId, MemberId, "Ana")),
            ),
            CampingStaffRole(
                id = "kitchen",
                campingId = CampingId,
                name = "Kitchen",
                kind = StaffRoleKind.Kitchen,
            ),
        ),
    )

    private fun attendee() = CampingAttendee(
        id = MemberId,
        userId = MemberId,
        displayName = "Ana Silva",
        church = "Central SDA",
        age = 24,
        languages = listOf("pt"),
        registrationStatus = RegistrationApprovalStatus.Approved,
    )

    private companion object {
        const val CampingId = "camp"
        const val MemberId = "member"
    }
}

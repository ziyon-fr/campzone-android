package fr.ziyon.campzone.ui.admin.role

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.admin.FakeRoleAssignmentService
import fr.ziyon.campzone.data.admin.ManagedUser
import fr.ziyon.campzone.data.admin.RoleAssignmentService
import fr.ziyon.campzone.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleManagementViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun sampleUsers() = listOf(
        ManagedUser("u1", "Léa Müller", "lea@example.org", "Lausanne", UserRole.User, null, null),
        ManagedUser("u2", "Marc Dupont", "marc@example.org", "Lausanne", UserRole.YouthDirector, null, null),
        ManagedUser("u3", "David Chen", "david@example.org", "Paris", UserRole.Adult, null, null),
    )

    @Test
    fun loadGroupsUsersByChurchSortedAlphabetically() = runTest {
        val vm = RoleManagementViewModel(FakeRoleAssignmentService(sampleUsers()))

        vm.loadIfNeeded(churchFilter = null)
        advanceUntilIdle()

        val state = vm.uiState.value as RoleManagementUiState.Loaded
        assertEquals(listOf("Lausanne", "Paris"), state.groups.map { it.church })
        assertEquals(2, state.groups.first { it.church == "Lausanne" }.users.size)
        assertTrue(state.hasUsers)
    }

    @Test
    fun churchFilterIsForwardedToService() = runTest {
        val vm = RoleManagementViewModel(FakeRoleAssignmentService(sampleUsers()))

        vm.loadIfNeeded(churchFilter = "Lausanne")
        advanceUntilIdle()

        val state = vm.uiState.value as RoleManagementUiState.Loaded
        assertEquals(1, state.groups.size)
        assertEquals("Lausanne", state.groups.single().church)
        assertEquals(2, state.groups.single().users.size)
    }

    @Test
    fun searchFiltersByNameEmailOrChurch() = runTest {
        val vm = RoleManagementViewModel(FakeRoleAssignmentService(sampleUsers()))
        vm.loadIfNeeded(churchFilter = null)
        advanceUntilIdle()

        vm.onSearchChange("david")

        val state = vm.uiState.value as RoleManagementUiState.Loaded
        assertEquals(1, state.groups.sumOf { it.users.size })
        assertEquals("David Chen", state.groups.single().users.single().displayName)
        // The directory still has users; the empty list reflects the search only.
        assertTrue(state.hasUsers)
    }

    @Test
    fun updateRoleMutatesLocalStateAndReportsSuccess() = runTest {
        val vm = RoleManagementViewModel(FakeRoleAssignmentService(sampleUsers()))
        vm.loadIfNeeded(churchFilter = null)
        advanceUntilIdle()
        val target = (vm.uiState.value as RoleManagementUiState.Loaded)
            .groups.first { it.church == "Lausanne" }.users.first { it.id == "u1" }

        vm.updateRole(target, UserRole.Adult, writeIdField = true)
        advanceUntilIdle()

        val updated = (vm.uiState.value as RoleManagementUiState.Loaded)
            .groups.flatMap { it.users }.first { it.id == "u1" }
        assertEquals(UserRole.Adult, updated.role)
        assertEquals("Léa Müller", vm.operationMessage.value)
        assertNull(vm.operationError.value)
    }

    @Test
    fun updateRoleForwardsWriteIdFieldFlag() = runTest {
        val recording = RecordingRoleAssignmentService(sampleUsers())
        val vm = RoleManagementViewModel(recording)
        vm.loadIfNeeded(churchFilter = null)
        advanceUntilIdle()
        val target = sampleUsers().first { it.id == "u3" }

        vm.updateRole(target, UserRole.User, writeIdField = false)
        advanceUntilIdle()

        assertEquals(false, recording.lastWriteIdField)
        assertEquals("u3", recording.lastUid)
    }

    @Test
    fun updateRoleIsNoOpWhenRoleUnchanged() = runTest {
        val recording = RecordingRoleAssignmentService(sampleUsers())
        val vm = RoleManagementViewModel(recording)
        vm.loadIfNeeded(churchFilter = null)
        advanceUntilIdle()
        val target = sampleUsers().first { it.id == "u3" } // already Adult

        vm.updateRole(target, UserRole.Adult, writeIdField = true)
        advanceUntilIdle()

        assertEquals(0, recording.updateCount)
    }

    @Test
    fun serviceFailurePublishesErrorState() = runTest {
        val vm = RoleManagementViewModel(FakeRoleAssignmentService(shouldFail = true))

        vm.load(churchFilter = null)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is RoleManagementUiState.Error)
    }

    private class RecordingRoleAssignmentService(
        private val users: List<ManagedUser>,
    ) : RoleAssignmentService {
        var lastUid: String? = null
        var lastWriteIdField: Boolean? = null
        var updateCount = 0

        override suspend fun loadUsers(churchFilter: String?): List<ManagedUser> = users

        override suspend fun updateRole(uid: String, role: UserRole, writeIdField: Boolean) {
            lastUid = uid
            lastWriteIdField = writeIdField
            updateCount += 1
        }
    }
}

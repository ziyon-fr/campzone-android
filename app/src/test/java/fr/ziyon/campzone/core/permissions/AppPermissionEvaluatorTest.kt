package fr.ziyon.campzone.core.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPermissionEvaluatorTest {
    private val evaluator = AppPermissionEvaluator()
    private val ownChurchCamping = CampingPermissionContext(
        organizerLevelType = "church",
        organizerLevelValue = "Paris Central",
    )
    private val otherChurchCamping = CampingPermissionContext(
        organizerLevelType = "church",
        organizerLevelValue = "Lyon",
    )
    private val regionalCamping = CampingPermissionContext(
        organizerLevelType = "region",
        organizerLevelValue = "France",
    )

    @Test
    fun evaluatesEveryRoleAgainstEveryPermissionInTheMatrix() {
        UserRole.allWireRoles.forEach { role ->
            AppPermission.entries.forEach { permission ->
                val user = PermissionUser(role = role, church = "Paris Central")
                val expectation = permissionExpectations.getValue(permission).getValue(role)

                when (expectation) {
                    ExpectedAccess.Denied -> {
                        assertFalse(evaluator.hasPermission(user, permission))
                        assertFalse(evaluator.hasPermission(user, permission, ownChurchCamping))
                        assertFalse(evaluator.hasPermission(user, permission, otherChurchCamping))
                    }

                    ExpectedAccess.Global -> {
                        assertTrue(evaluator.hasPermission(user, permission))
                        assertTrue(evaluator.hasPermission(user, permission, ownChurchCamping))
                        assertTrue(evaluator.hasPermission(user, permission, otherChurchCamping))
                    }

                    ExpectedAccess.ChurchScoped -> {
                        assertFalse(evaluator.hasPermission(user, permission))
                        assertTrue(evaluator.hasPermission(user, permission, ownChurchCamping))
                        assertFalse(evaluator.hasPermission(user, permission, otherChurchCamping))
                        assertFalse(evaluator.hasPermission(user, permission, regionalCamping))
                    }
                }
            }
        }
    }

    @Test
    fun churchScopeRequiresChurchOrganizerTypeAndCaseInsensitiveMatchingValue() {
        val user = PermissionUser(role = UserRole.Pastor, church = "PARIS CENTRAL")
        val matchingCamping = CampingPermissionContext(
            organizerLevelType = "Church",
            organizerLevelValue = "paris central",
        )

        assertTrue(evaluator.hasPermission(user, AppPermission.ManageSchedule, matchingCamping))
        assertFalse(evaluator.hasPermission(user, AppPermission.ManageSchedule, regionalCamping))
    }

    @Test
    fun roleAssignmentMatchesEscalationRules() {
        val pastor = PermissionUser(role = UserRole.Pastor, church = "Paris Central")
        val admin = PermissionUser(role = UserRole.Admin, church = null)

        assertTrue(evaluator.canAssignRole(pastor, UserRole.Guest, ownChurchCamping))
        assertTrue(evaluator.canAssignRole(pastor, UserRole.User, ownChurchCamping))
        assertTrue(evaluator.canAssignRole(pastor, UserRole.Adult, ownChurchCamping))
        assertFalse(evaluator.canAssignRole(pastor, UserRole.Leader, ownChurchCamping))
        assertFalse(evaluator.canAssignRole(pastor, UserRole.User, otherChurchCamping))
        assertTrue(evaluator.canAssignRole(admin, UserRole.Leader, otherChurchCamping))
    }

    @Test
    fun legacyRoleValuesReadAsUserAndUnknownValuesReadAsGuest() {
        assertTrue(
            evaluator.hasPermission(
                PermissionUser(UserRole.fromWire("senior"), church = null),
                AppPermission.RegisterForCampings,
            ),
        )
        assertTrue(
            evaluator.hasPermission(
                PermissionUser(UserRole.fromWire("youth"), church = null),
                AppPermission.RegisterForCampings,
            ),
        )
        assertFalse(
            evaluator.hasPermission(
                PermissionUser(UserRole.fromWire("unknown"), church = null),
                AppPermission.RegisterForCampings,
            ),
        )
    }

    private enum class ExpectedAccess {
        Denied,
        Global,
        ChurchScoped,
    }

    private companion object {
        private val denied = UserRole.allWireRoles.associateWith { ExpectedAccess.Denied }

        private fun expectations(
            global: Set<UserRole> = emptySet(),
            scoped: Set<UserRole> = emptySet(),
        ): Map<UserRole, ExpectedAccess> = denied.toMutableMap().apply {
            global.forEach { this[it] = ExpectedAccess.Global }
            scoped.forEach { this[it] = ExpectedAccess.ChurchScoped }
            this[UserRole.Admin] = ExpectedAccess.Global
        }

        private val everyone = UserRole.allWireRoles.toSet()
        private val signedIn = everyone - UserRole.Guest
        private val campingCreators = setOf(UserRole.YouthDirector, UserRole.Pastor)
        private val registrationApprovers = setOf(UserRole.YouthDirector, UserRole.Leader)
        private val scheduleManagers = setOf(
            UserRole.YouthDirector,
            UserRole.Pastor,
            UserRole.Leader,
        )
        private val teamManagers = setOf(
            UserRole.YouthDirector,
            UserRole.GameMaster,
            UserRole.Leader,
        )
        private val gameManagers = setOf(
            UserRole.YouthDirector,
            UserRole.Pastor,
            UserRole.GameMaster,
            UserRole.Leader,
        )
        private val pointAssigners = setOf(
            UserRole.YouthDirector,
            UserRole.GameMaster,
            UserRole.Leader,
        )
        private val announcementEditors = setOf(
            UserRole.YouthDirector,
            UserRole.Pastor,
            UserRole.Leader,
        )

        private val permissionExpectations = mapOf(
            AppPermission.ViewPublicContent to expectations(global = everyone),
            AppPermission.RegisterForCampings to expectations(global = signedIn),
            AppPermission.ManageFamilyRegistrations to expectations(global = setOf(UserRole.Adult)),
            AppPermission.CreateCamping to expectations(scoped = campingCreators),
            AppPermission.EditCamping to expectations(scoped = campingCreators),
            AppPermission.CancelCamping to expectations(scoped = campingCreators),
            AppPermission.ApproveRegistrations to expectations(scoped = registrationApprovers),
            AppPermission.ManageSchedule to expectations(scoped = scheduleManagers),
            AppPermission.ManageFoodMenu to expectations(scoped = scheduleManagers),
            AppPermission.ManageTeams to expectations(scoped = teamManagers),
            AppPermission.ManageGames to expectations(scoped = gameManagers),
            AppPermission.AssignPoints to expectations(scoped = pointAssigners),
            AppPermission.RevealWinners to expectations(scoped = setOf(UserRole.GameMaster)),
            AppPermission.ManageAlbumMedia to expectations(scoped = setOf(UserRole.Photographer)),
            AppPermission.ManageCheckIns to expectations(scoped = scheduleManagers),
            AppPermission.ManageTransportation to expectations(scoped = scheduleManagers),
            AppPermission.AwardAchievements to expectations(scoped = gameManagers),
            AppPermission.RevokeAchievements to expectations(),
            AppPermission.ViewParticipantProfiles to expectations(scoped = gameManagers),
            AppPermission.EditAnnouncements to expectations(global = announcementEditors),
            AppPermission.DeleteAnnouncements to expectations(),
            AppPermission.ModerateContent to expectations(global = announcementEditors),
            AppPermission.ManageGuidelines to expectations(scoped = scheduleManagers),
            AppPermission.AssignOwnChurchRoles to expectations(scoped = campingCreators),
            AppPermission.AssignLeadershipRoles to expectations(),
            AppPermission.ViewAdminTools to expectations(),
            AppPermission.ManageSongs to expectations(),
            AppPermission.ManagePolls to expectations(global = announcementEditors),
            AppPermission.ModerateCampingChat to expectations(global = announcementEditors),
            AppPermission.ModerateTeamChat to expectations(scoped = gameManagers),
        )
    }
}

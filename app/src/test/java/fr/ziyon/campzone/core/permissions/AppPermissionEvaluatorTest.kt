package fr.ziyon.campzone.core.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPermissionEvaluatorTest {
    private val evaluator = AppPermissionEvaluator()
    private val ownChurchCamping = CampingPermissionContext(
        organizerLevelType = "church",
        organizerLevelValue = "Paris Central SDA",
    )
    private val otherChurchCamping = CampingPermissionContext(
        organizerLevelType = "church",
        organizerLevelValue = "Lyon SDA",
    )
    private val regionalCamping = CampingPermissionContext(
        organizerLevelType = "regional",
        organizerLevelValue = "France",
    )
    private val creatorCamping = regionalCamping.copy(createdByUid = "creator-user")

    @Test
    fun appPermissionEntriesMatchIosCasesAndOrder() {
        assertEquals(
            listOf(
                AppPermission.ViewPublishedCampings,
                AppPermission.RegisterForCampings,
                AppPermission.ApproveRegistrations,
                AppPermission.CreateCampings,
                AppPermission.EditCampings,
                AppPermission.CancelCampings,
                AppPermission.CreateOwnChurchCampings,
                AppPermission.EditOwnChurchCampings,
                AppPermission.CancelOwnChurchCampings,
                AppPermission.ViewAnnouncements,
                AppPermission.CreateAnnouncements,
                AppPermission.EditAnnouncements,
                AppPermission.DeleteAnnouncements,
                AppPermission.ViewSongbook,
                AppPermission.ManageSongbook,
                AppPermission.ManageSchedule,
                AppPermission.ManageTeams,
                AppPermission.ManageGames,
                AppPermission.AssignPoints,
                AppPermission.RevealWinners,
                AppPermission.ManageAlbumMedia,
                AppPermission.ManageAlbumSettings,
                AppPermission.ManageTransportation,
                AppPermission.ManageOwnChurchTransportation,
                AppPermission.AwardAchievements,
                AppPermission.RevokeAchievements,
                AppPermission.ManageCheckIns,
                AppPermission.ManageOwnChurchCheckIns,
                AppPermission.ViewParticipantProfiles,
                AppPermission.AssignLeadershipRoles,
                AppPermission.AssignOwnChurchRoles,
                AppPermission.ViewAdminTools,
                AppPermission.ManageFamilyRegistrations,
                AppPermission.EditGuidelines,
                AppPermission.EditOwnChurchGuidelines,
            ),
            AppPermission.entries,
        )
    }

    @Test
    fun userRoleOrderAndSelfAssignmentMatchIos() {
        assertEquals(
            listOf(
                UserRole.Guest,
                UserRole.User,
                UserRole.YouthDirector,
                UserRole.Pastor,
                UserRole.GameMaster,
                UserRole.Leader,
                UserRole.Photographer,
                UserRole.Adult,
                UserRole.Admin,
            ),
            UserRole.allWireRoles,
        )
        assertEquals(listOf(UserRole.Guest, UserRole.User, UserRole.Adult), UserRole.selfAssignableRoles.toList())
    }

    @Test
    fun adminReceivesEveryPermissionAndAdminUi() {
        val admin = PermissionUser(role = UserRole.Admin)

        AppPermission.entries.forEach { permission ->
            assertTrue(evaluator.can(admin, permission))
            assertTrue(evaluator.hasPermission(admin, permission, regionalCamping))
        }
        assertTrue(evaluator.hasPermission(admin, AppPermission.ViewAdminTools))
        assertTrue(evaluator.canModerateContent(admin))
    }

    @Test
    fun onlyAdminsCanPinFeaturedCampingToHome() {
        UserRole.entries.filterNot { it == UserRole.Admin }.forEach { role ->
            assertFalse(role.name, evaluator.canPinFeaturedCamping(PermissionUser(role = role)))
        }
        assertTrue(evaluator.canPinFeaturedCamping(PermissionUser(role = UserRole.Admin)))
    }

    @Test
    fun contentModerationMatchesFirestoreRoleGate() {
        assertTrue(evaluator.canModerateContent(PermissionUser(role = UserRole.YouthDirector)))
        assertTrue(evaluator.canModerateContent(PermissionUser(role = UserRole.Pastor)))
        assertTrue(evaluator.canModerateContent(PermissionUser(role = UserRole.Leader)))
        assertTrue(evaluator.canModerateContent(PermissionUser(role = UserRole.Admin)))

        assertFalse(evaluator.canModerateContent(PermissionUser(role = UserRole.Guest)))
        assertFalse(evaluator.canModerateContent(PermissionUser(role = UserRole.User)))
        assertFalse(evaluator.canModerateContent(PermissionUser(role = UserRole.Adult)))
        assertFalse(evaluator.canModerateContent(PermissionUser(role = UserRole.GameMaster)))
        assertFalse(evaluator.canModerateContent(PermissionUser(role = UserRole.Photographer)))
    }

    @Test
    fun rawRolePermissionsMatchIosPermissionSets() {
        val guest = PermissionUser(role = UserRole.Guest)
        val user = PermissionUser(role = UserRole.User)
        val adult = PermissionUser(role = UserRole.Adult)
        val youthDirector = PermissionUser(role = UserRole.YouthDirector)
        val pastor = PermissionUser(role = UserRole.Pastor)
        val gameMaster = PermissionUser(role = UserRole.GameMaster)
        val leader = PermissionUser(role = UserRole.Leader)
        val photographer = PermissionUser(role = UserRole.Photographer)

        assertTrue(evaluator.can(guest, AppPermission.ViewPublishedCampings))
        assertTrue(evaluator.can(guest, AppPermission.ViewAnnouncements))
        assertTrue(evaluator.can(guest, AppPermission.ViewSongbook))
        assertFalse(evaluator.can(guest, AppPermission.RegisterForCampings))

        assertTrue(evaluator.can(user, AppPermission.RegisterForCampings))
        assertFalse(evaluator.can(user, AppPermission.ViewAdminTools))

        assertTrue(evaluator.can(adult, AppPermission.ManageFamilyRegistrations))
        assertFalse(evaluator.can(adult, AppPermission.ManageAlbumMedia))

        assertTrue(evaluator.can(youthDirector, AppPermission.ApproveRegistrations))
        assertTrue(evaluator.can(youthDirector, AppPermission.ManageTeams))
        assertTrue(evaluator.can(youthDirector, AppPermission.ManageAlbumSettings))
        assertTrue(evaluator.can(youthDirector, AppPermission.CreateOwnChurchCampings))
        assertFalse(evaluator.can(youthDirector, AppPermission.CreateCampings))
        assertFalse(evaluator.can(youthDirector, AppPermission.ManageCheckIns))

        assertTrue(evaluator.can(pastor, AppPermission.ManageSchedule))
        assertTrue(evaluator.can(pastor, AppPermission.CreateAnnouncements))
        assertTrue(evaluator.can(pastor, AppPermission.ManageAlbumSettings))
        assertFalse(evaluator.can(pastor, AppPermission.ApproveRegistrations))
        assertFalse(evaluator.can(pastor, AppPermission.ManageTeams))
        assertFalse(evaluator.can(pastor, AppPermission.ManageSongbook))

        assertTrue(evaluator.can(gameMaster, AppPermission.ManageGames))
        assertTrue(evaluator.can(gameMaster, AppPermission.AssignPoints))
        assertTrue(evaluator.can(gameMaster, AppPermission.RevealWinners))

        assertTrue(evaluator.can(leader, AppPermission.ManageTeams))
        assertTrue(evaluator.can(leader, AppPermission.ManageOwnChurchTransportation))
        assertTrue(evaluator.can(leader, AppPermission.ManageAlbumSettings))
        assertFalse(evaluator.can(leader, AppPermission.AssignLeadershipRoles))

        assertTrue(evaluator.can(photographer, AppPermission.ManageAlbumMedia))
        assertFalse(evaluator.can(photographer, AppPermission.ManageAlbumSettings))
        assertFalse(evaluator.can(photographer, AppPermission.ManageTeams))
        assertFalse(evaluator.can(photographer, AppPermission.CreateAnnouncements))
    }

    @Test
    fun familyManagementMatchesIosAdultAndLeadershipRoles() {
        val managing = listOf(
            UserRole.Adult,
            UserRole.YouthDirector,
            UserRole.Pastor,
            UserRole.GameMaster,
            UserRole.Leader,
            UserRole.Photographer,
            UserRole.Admin,
        )
        managing.forEach { role ->
            assertTrue(
                role.name,
                evaluator.can(PermissionUser(role = role), AppPermission.ManageFamilyRegistrations),
            )
        }
        assertFalse(
            evaluator.can(PermissionUser(role = UserRole.Guest), AppPermission.ManageFamilyRegistrations),
        )
        assertFalse(
            evaluator.can(PermissionUser(role = UserRole.User), AppPermission.ManageFamilyRegistrations),
        )
    }

    @Test
    fun songbookWritesMatchScopedIosRule() {
        assertTrue(evaluator.canManageSongs(PermissionUser(role = UserRole.Admin)))
        assertTrue(evaluator.canManageSongs(PermissionUser(role = UserRole.YouthDirector)))
        assertTrue(evaluator.canManageSongs(PermissionUser(role = UserRole.Leader)))
        assertFalse(evaluator.canManageSongs(PermissionUser(role = UserRole.Pastor)))

        val ownChurchLeader = PermissionUser(
            role = UserRole.Leader,
            church = "Paris Central SDA",
        )
        val creator = PermissionUser(role = UserRole.User, userId = "creator-user")
        val otherUser = PermissionUser(role = UserRole.User, userId = "other-user")

        assertTrue(evaluator.canManageSongbook(PermissionUser(role = UserRole.Admin), regionalCamping))
        assertTrue(evaluator.canManageSongbook(ownChurchLeader, ownChurchCamping))
        assertFalse(evaluator.canManageSongbook(ownChurchLeader, otherChurchCamping))
        assertTrue(evaluator.canManageSongbook(creator, creatorCamping))
        assertFalse(evaluator.canManageSongbook(otherUser, creatorCamping))
    }

    @Test
    fun scopedOperationalHelpersRequireOwnChurchCamping() {
        val leader = PermissionUser(
            role = UserRole.Leader,
            church = "Paris Central SDA",
        )
        val gameMaster = PermissionUser(
            role = UserRole.GameMaster,
            church = "Paris Central SDA",
        )
        val photographer = PermissionUser(
            role = UserRole.Photographer,
            church = "Paris Central SDA",
        )

        assertTrue(evaluator.canManageSchedule(leader, ownChurchCamping))
        assertTrue(evaluator.canApproveRegistrations(leader, ownChurchCamping))
        assertTrue(evaluator.canManageAnnouncements(leader, ownChurchCamping))
        assertTrue(evaluator.canAssignPoints(leader, ownChurchCamping))
        assertTrue(evaluator.canManageTransportation(leader, ownChurchCamping))
        assertFalse(evaluator.canManageSchedule(leader, otherChurchCamping))
        assertFalse(evaluator.canManageAnnouncements(leader, otherChurchCamping))
        assertFalse(evaluator.canManageSchedule(leader, regionalCamping))
        assertFalse(evaluator.canManageAnnouncements(leader, regionalCamping))

        assertTrue(evaluator.canRevealWinners(gameMaster, ownChurchCamping))
        assertFalse(evaluator.canRevealWinners(gameMaster, otherChurchCamping))
        assertFalse(evaluator.canRevealWinners(gameMaster, regionalCamping))

        assertTrue(evaluator.canManageAlbumMedia(photographer, ownChurchCamping))
        assertFalse(evaluator.canManageAlbumMedia(photographer, otherChurchCamping))
        assertTrue(evaluator.canManageAlbumSettings(leader, ownChurchCamping))
        assertFalse(evaluator.canManageAlbumSettings(leader, otherChurchCamping))
        assertFalse(evaluator.canManageAlbumSettings(photographer, ownChurchCamping))
    }

    @Test
    fun campingCreatorCanManageTheirOwnCampingOperations() {
        val creator = PermissionUser(role = UserRole.User, userId = "creator-user")
        val otherUser = PermissionUser(role = UserRole.User, userId = "other-user")

        assertTrue(evaluator.canEditCamping(creator, creatorCamping))
        assertTrue(evaluator.canManageSchedule(creator, creatorCamping))
        assertTrue(evaluator.canManageTeams(creator, creatorCamping))
        assertFalse(evaluator.canManageAnnouncements(creator, creatorCamping))
        assertTrue(evaluator.canApproveRegistrations(creator, creatorCamping))
        assertTrue(evaluator.canViewParticipantProfiles(creator, creatorCamping))

        assertFalse(evaluator.canEditCamping(otherUser, creatorCamping))
        assertFalse(evaluator.canManageSchedule(otherUser, creatorCamping))
        assertFalse(evaluator.canViewParticipantProfiles(otherUser, creatorCamping))
    }

    @Test
    fun ownChurchLeadersCanEditAndSaveOnlyOwnChurchCampingForms() {
        val youthDirector = PermissionUser(
            role = UserRole.YouthDirector,
            church = "Paris Central SDA",
        )
        val pastor = PermissionUser(
            role = UserRole.Pastor,
            church = "Paris Central SDA",
        )

        assertTrue(evaluator.canEditCamping(youthDirector, ownChurchCamping))
        assertTrue(evaluator.canSaveCamping(youthDirector, ownChurchCamping, ownChurchCamping))
        assertFalse(evaluator.canEditCamping(youthDirector, otherChurchCamping))
        assertFalse(evaluator.canSaveCamping(youthDirector, ownChurchCamping, regionalCamping))

        assertTrue(evaluator.canEditCamping(pastor, ownChurchCamping))
        assertTrue(evaluator.canSaveCamping(pastor, ownChurchCamping, ownChurchCamping))
        assertFalse(evaluator.canEditCamping(pastor, otherChurchCamping))
        assertFalse(evaluator.canSaveCamping(pastor, ownChurchCamping, otherChurchCamping))
    }

    @Test
    fun campingCreatorCanSaveOrganizerChangesForTheirCamping() {
        val creator = PermissionUser(role = UserRole.User, userId = "creator-user")

        assertTrue(evaluator.canSaveCamping(creator, creatorCamping, otherChurchCamping))
        assertFalse(evaluator.canSaveCamping(creator, null, otherChurchCamping))
    }

    @Test
    fun roleAssignmentIsLimitedToSameChurchBasicRoles() {
        val youthDirector = PermissionUser(
            role = UserRole.YouthDirector,
            church = "Paris Central SDA",
        )
        val pastor = PermissionUser(
            role = UserRole.Pastor,
            church = "Paris Central SDA",
        )
        val admin = PermissionUser(role = UserRole.Admin)

        assertTrue(evaluator.canAssignRole(youthDirector, "Paris Central SDA"))
        assertFalse(evaluator.canAssignRole(youthDirector, "Lyon SDA"))
        assertEquals(UserRole.selfAssignableRoles.toList(), evaluator.assignableRoles(youthDirector))

        assertTrue(evaluator.canAssignRole(pastor, "Paris Central SDA"))
        assertEquals(UserRole.selfAssignableRoles.toList(), evaluator.assignableRoles(pastor))

        assertTrue(evaluator.canAssignRole(admin, null))
        assertEquals(UserRole.allWireRoles, evaluator.assignableRoles(admin))
    }

    @Test
    fun legacyRoleValuesReadAsUserAndUnknownValuesReadAsGuest() {
        assertTrue(
            evaluator.can(
                PermissionUser(UserRole.fromWire("senior")),
                AppPermission.RegisterForCampings,
            ),
        )
        assertTrue(
            evaluator.can(
                PermissionUser(UserRole.fromWire("youth")),
                AppPermission.RegisterForCampings,
            ),
        )
        assertFalse(
            evaluator.can(
                PermissionUser(UserRole.fromWire("unknown")),
                AppPermission.RegisterForCampings,
            ),
        )
    }
}

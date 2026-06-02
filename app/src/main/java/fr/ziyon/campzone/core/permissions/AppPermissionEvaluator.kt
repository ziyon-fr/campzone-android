package fr.ziyon.campzone.core.permissions

data class PermissionUser(
    val role: UserRole,
    val userId: String? = null,
    val church: String? = null,
)

data class CampingPermissionContext(
    val organizerLevelType: String?,
    val organizerLevelValue: String?,
    val createdByUid: String? = null,
)

class AppPermissionEvaluator {
    fun can(
        user: PermissionUser?,
        permission: AppPermission,
    ): Boolean = (user?.role ?: UserRole.Guest).permissions.contains(permission)

    fun hasPermission(
        user: PermissionUser?,
        permission: AppPermission,
        camping: CampingPermissionContext? = null,
    ): Boolean {
        val role = user?.role ?: UserRole.Guest
        if (role.isAdmin) return true

        return when (permission) {
            AppPermission.ViewPublishedCampings,
            AppPermission.RegisterForCampings,
            AppPermission.ViewAnnouncements,
            AppPermission.ViewSongbook,
            AppPermission.ManageFamilyRegistrations,
            AppPermission.CreateCampings,
            AppPermission.EditCampings,
            AppPermission.CancelCampings,
            AppPermission.DeleteAnnouncements,
            AppPermission.ManageSongbook,
            AppPermission.ManageTransportation,
            AppPermission.RevokeAchievements,
            AppPermission.ManageCheckIns,
            AppPermission.AssignLeadershipRoles,
            AppPermission.ViewAdminTools,
            AppPermission.EditGuidelines,
            -> can(user, permission)

            AppPermission.CreateAnnouncements,
            AppPermission.EditAnnouncements,
            -> if (camping == null) {
                can(user, permission)
            } else {
                canManageScoped(user, permission, camping)
            }

            AppPermission.CreateOwnChurchCampings,
            AppPermission.EditOwnChurchCampings,
            AppPermission.CancelOwnChurchCampings,
            AppPermission.ApproveRegistrations,
            AppPermission.ManageSchedule,
            AppPermission.ManageTeams,
            AppPermission.ManageGames,
            AppPermission.AssignPoints,
            AppPermission.RevealWinners,
            AppPermission.ManageAlbumMedia,
            AppPermission.ManageOwnChurchTransportation,
            AppPermission.AwardAchievements,
            AppPermission.ManageOwnChurchCheckIns,
            AppPermission.ViewParticipantProfiles,
            AppPermission.AssignOwnChurchRoles,
            AppPermission.EditOwnChurchGuidelines,
            -> canManageScoped(user, permission, camping)
        }
    }

    fun canCreateAnyCamping(user: PermissionUser?): Boolean =
        can(user, AppPermission.CreateCampings) ||
            (can(user, AppPermission.CreateOwnChurchCampings) && user.normalizedChurch() != null)

    fun canCreateCamping(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean {
        if (can(user, AppPermission.CreateCampings)) return true
        return can(user, AppPermission.CreateOwnChurchCampings) && user.isOwnChurchCamping(camping)
    }

    fun canEditCamping(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean {
        if (can(user, AppPermission.EditCampings)) return true
        if (user.isCampingCreator(camping)) return true
        return can(user, AppPermission.EditOwnChurchCampings) && user.isOwnChurchCamping(camping)
    }

    fun canSaveCamping(
        user: PermissionUser?,
        currentCamping: CampingPermissionContext?,
        proposedCamping: CampingPermissionContext?,
    ): Boolean {
        if (currentCamping == null) return canCreateCamping(user, proposedCamping)
        if (user.isCampingCreator(currentCamping)) return canEditCamping(user, currentCamping)
        return canEditCamping(user, currentCamping) && canCreateCamping(user, proposedCamping)
    }

    fun canCancelCamping(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean {
        if (can(user, AppPermission.CancelCampings)) return true
        return can(user, AppPermission.CancelOwnChurchCampings) && user.isOwnChurchCamping(camping)
    }

    fun canApproveRegistrations(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = user.isCampingCreator(camping) ||
        canManageScoped(user, AppPermission.ApproveRegistrations, camping)

    fun canManageSchedule(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = user.isCampingCreator(camping) ||
        canManageScoped(user, AppPermission.ManageSchedule, camping)

    fun canManageFoodMenu(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canManageSchedule(user, camping)

    fun canManageSongs(user: PermissionUser?): Boolean =
        can(user, AppPermission.ManageSongbook)

    fun canManageTeams(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = user.isCampingCreator(camping) ||
        canManageScoped(user, AppPermission.ManageTeams, camping)

    fun canManageGames(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canManageScoped(user, AppPermission.ManageGames, camping)

    fun canAssignPoints(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canManageScoped(user, AppPermission.AssignPoints, camping)

    fun canRevealWinners(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canManageScoped(user, AppPermission.RevealWinners, camping)

    fun canManageAlbumMedia(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canManageScoped(user, AppPermission.ManageAlbumMedia, camping)

    fun canManagePolls(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canEditCamping(user, camping) ||
        canManageScoped(user, AppPermission.EditAnnouncements, camping)

    fun canManageAnnouncements(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canManageScoped(user, AppPermission.CreateAnnouncements, camping) ||
        canManageScoped(user, AppPermission.EditAnnouncements, camping)

    fun canModerateCampingChat(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canManagePolls(user, camping)

    fun canModerateTeamChat(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canEditCamping(user, camping) ||
        canManageTeams(user, camping) ||
        canManageGames(user, camping)

    fun canAwardAchievements(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = canManageScoped(user, AppPermission.AwardAchievements, camping)

    fun canViewParticipantProfiles(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = user.isCampingCreator(camping) ||
        canManageScoped(user, AppPermission.ViewParticipantProfiles, camping)

    fun canManageCheckIns(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = user.isCampingCreator(camping) ||
        can(user, AppPermission.ManageCheckIns) ||
        (can(user, AppPermission.ManageOwnChurchCheckIns) && user.isOwnChurchCamping(camping))

    fun canManageTransportation(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = user.isCampingCreator(camping) ||
        can(user, AppPermission.ManageTransportation) ||
        (can(user, AppPermission.ManageOwnChurchTransportation) && user.isOwnChurchCamping(camping))

    fun canEditGuidelines(
        user: PermissionUser?,
        camping: CampingPermissionContext?,
    ): Boolean = user.isCampingCreator(camping) ||
        can(user, AppPermission.EditGuidelines) ||
        (can(user, AppPermission.EditOwnChurchGuidelines) && user.isOwnChurchCamping(camping))

    fun canModerateContent(user: PermissionUser?): Boolean =
        user?.role in setOf(
            UserRole.YouthDirector,
            UserRole.Pastor,
            UserRole.Leader,
            UserRole.Admin,
        )

    fun canViewAdminTools(user: PermissionUser?): Boolean =
        can(user, AppPermission.ViewAdminTools)

    fun canManageAnyCamping(user: PermissionUser?): Boolean =
        canCreateAnyCamping(user) ||
            can(user, AppPermission.EditCampings) ||
            can(user, AppPermission.CancelCampings) ||
            can(user, AppPermission.EditOwnChurchCampings) ||
            can(user, AppPermission.CancelOwnChurchCampings) ||
            can(user, AppPermission.ApproveRegistrations) ||
            can(user, AppPermission.ManageSchedule) ||
            can(user, AppPermission.ManageTeams) ||
            can(user, AppPermission.ManageGames) ||
            can(user, AppPermission.AssignPoints) ||
            can(user, AppPermission.RevealWinners) ||
            can(user, AppPermission.ManageAlbumMedia) ||
            can(user, AppPermission.ManageOwnChurchCheckIns) ||
            can(user, AppPermission.ManageOwnChurchTransportation)

    fun canAssignAnyRole(user: PermissionUser?): Boolean =
        can(user, AppPermission.AssignLeadershipRoles) ||
            can(user, AppPermission.AssignOwnChurchRoles)

    fun canAssignRole(
        assigner: PermissionUser?,
        targetChurch: String?,
    ): Boolean {
        if (can(assigner, AppPermission.AssignLeadershipRoles)) return true
        val myChurch = assigner.normalizedChurch() ?: return false
        val target = targetChurch?.trim()?.takeUnless { it.isBlank() } ?: return false

        return can(assigner, AppPermission.AssignOwnChurchRoles) &&
            target.equals(myChurch, ignoreCase = true)
    }

    fun canAssignRole(
        assigner: PermissionUser?,
        targetRole: UserRole,
        camping: CampingPermissionContext? = null,
    ): Boolean = targetRole in assignableRoles(assigner) &&
        canAssignRole(assigner, camping?.organizerLevelValue)

    fun assignableRoles(assigner: PermissionUser?): List<UserRole> = when {
        can(assigner, AppPermission.AssignLeadershipRoles) -> UserRole.allWireRoles
        can(assigner, AppPermission.AssignOwnChurchRoles) -> UserRole.selfAssignableRoles.toList()
        else -> emptyList()
    }

    private fun canManageScoped(
        user: PermissionUser?,
        permission: AppPermission,
        camping: CampingPermissionContext?,
    ): Boolean {
        if (!can(user, permission)) return false
        if (user?.role == UserRole.Admin) return true
        return user.isOwnChurchCamping(camping)
    }

    private fun PermissionUser?.isOwnChurchCamping(camping: CampingPermissionContext?): Boolean {
        val church = normalizedChurch() ?: return false
        val organizerType = camping?.organizerLevelType?.trim() ?: return false
        val organizerValue = camping.organizerLevelValue?.trim()?.takeUnless { it.isBlank() }
            ?: return false

        return organizerType.equals("church", ignoreCase = true) &&
            organizerValue.equals(church, ignoreCase = true)
    }

    private fun PermissionUser?.isCampingCreator(camping: CampingPermissionContext?): Boolean {
        val uid = this?.userId?.trim()?.takeUnless { it.isBlank() } ?: return false
        val creatorUid = camping?.createdByUid?.trim()?.takeUnless { it.isBlank() } ?: return false
        return creatorUid == uid
    }

    private fun PermissionUser?.normalizedChurch(): String? =
        this?.church?.trim()?.takeUnless { it.isBlank() }

    private val UserRole.permissions: Set<AppPermission>
        get() = when (this) {
            UserRole.Guest -> setOf(
                AppPermission.ViewPublishedCampings,
                AppPermission.ViewAnnouncements,
                AppPermission.ViewSongbook,
            )

            UserRole.User -> setOf(
                AppPermission.ViewPublishedCampings,
                AppPermission.RegisterForCampings,
                AppPermission.ViewAnnouncements,
                AppPermission.ViewSongbook,
                AppPermission.ManageFamilyRegistrations
            )

            UserRole.Adult -> setOf(
                AppPermission.ViewPublishedCampings,
                AppPermission.RegisterForCampings,
                AppPermission.ViewAnnouncements,
                AppPermission.ViewSongbook,
                AppPermission.ManageFamilyRegistrations
            )

            UserRole.YouthDirector -> setOf(
                AppPermission.ViewPublishedCampings,
                AppPermission.RegisterForCampings,
                AppPermission.ApproveRegistrations,
                AppPermission.CreateOwnChurchCampings,
                AppPermission.CancelOwnChurchCampings,
                AppPermission.ViewAnnouncements,
                AppPermission.CreateAnnouncements,
                AppPermission.EditAnnouncements,
                AppPermission.EditOwnChurchCampings,
                AppPermission.ViewSongbook,
                AppPermission.ManageSchedule,
                AppPermission.ManageTeams,
                AppPermission.ManageGames,
                AppPermission.AssignPoints,
                AppPermission.AwardAchievements,
                AppPermission.ManageOwnChurchCheckIns,
                AppPermission.ManageOwnChurchTransportation,
                AppPermission.AssignOwnChurchRoles,
                AppPermission.ViewParticipantProfiles,
                AppPermission.EditOwnChurchGuidelines,
                AppPermission.ManageFamilyRegistrations
            )

            UserRole.Pastor -> setOf(
                AppPermission.ViewPublishedCampings,
                AppPermission.RegisterForCampings,
                AppPermission.CreateOwnChurchCampings,
                AppPermission.EditOwnChurchCampings,
                AppPermission.CancelOwnChurchCampings,
                AppPermission.ViewAnnouncements,
                AppPermission.CreateAnnouncements,
                AppPermission.EditAnnouncements,
                AppPermission.ViewSongbook,
                AppPermission.ManageSchedule,
                AppPermission.ManageGames,
                AppPermission.AwardAchievements,
                AppPermission.ManageOwnChurchCheckIns,
                AppPermission.ManageOwnChurchTransportation,
                AppPermission.AssignOwnChurchRoles,
                AppPermission.ViewParticipantProfiles,
                AppPermission.EditOwnChurchGuidelines,
                AppPermission.ManageFamilyRegistrations
            )

            UserRole.GameMaster -> setOf(
                AppPermission.ViewPublishedCampings,
                AppPermission.RegisterForCampings,
                AppPermission.ViewAnnouncements,
                AppPermission.ViewSongbook,
                AppPermission.ManageTeams,
                AppPermission.ManageGames,
                AppPermission.AssignPoints,
                AppPermission.RevealWinners,
                AppPermission.AwardAchievements,
                AppPermission.ViewParticipantProfiles,
                AppPermission.ManageFamilyRegistrations
            )

            UserRole.Leader -> setOf(
                AppPermission.ViewPublishedCampings,
                AppPermission.RegisterForCampings,
                AppPermission.ApproveRegistrations,
                AppPermission.ViewAnnouncements,
                AppPermission.CreateAnnouncements,
                AppPermission.EditAnnouncements,
                AppPermission.ViewSongbook,
                AppPermission.ManageSchedule,
                AppPermission.ManageTeams,
                AppPermission.ManageGames,
                AppPermission.AssignPoints,
                AppPermission.AwardAchievements,
                AppPermission.ManageOwnChurchCheckIns,
                AppPermission.ManageOwnChurchTransportation,
                AppPermission.ViewParticipantProfiles,
                AppPermission.EditOwnChurchGuidelines,
                AppPermission.ManageFamilyRegistrations
            )

            UserRole.Photographer -> setOf(
                AppPermission.ViewPublishedCampings,
                AppPermission.RegisterForCampings,
                AppPermission.ViewAnnouncements,
                AppPermission.ViewSongbook,
                AppPermission.ManageAlbumMedia,
                AppPermission.ManageFamilyRegistrations
            )

            UserRole.Admin -> AppPermission.entries.toSet()
        }
}

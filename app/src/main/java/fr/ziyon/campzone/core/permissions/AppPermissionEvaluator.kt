package fr.ziyon.campzone.core.permissions

data class PermissionUser(
    val role: UserRole,
    val church: String?,
)

data class CampingPermissionContext(
    val organizerLevelType: String?,
    val organizerLevelValue: String?,
)

class AppPermissionEvaluator {
    fun hasPermission(
        user: PermissionUser?,
        permission: AppPermission,
        camping: CampingPermissionContext? = null,
    ): Boolean {
        val role = user?.role ?: UserRole.Guest
        if (role.isAdmin) return true

        return when (permission) {
            AppPermission.ViewPublicContent -> true
            AppPermission.RegisterForCampings -> role in setOf(
                UserRole.User,
                UserRole.Adult,
                UserRole.YouthDirector,
                UserRole.Pastor,
                UserRole.GameMaster,
                UserRole.Leader,
                UserRole.Photographer,
            )

            AppPermission.ManageFamilyRegistrations -> role == UserRole.Adult

            AppPermission.CreateCamping,
            AppPermission.EditCamping,
            AppPermission.CancelCamping,
            -> role in setOf(UserRole.YouthDirector, UserRole.Pastor) &&
                user.isOwnChurchCamping(camping)

            AppPermission.ApproveRegistrations -> role in setOf(
                UserRole.YouthDirector,
                UserRole.Leader,
            ) && user.isOwnChurchCamping(camping)

            AppPermission.ManageSchedule,
            AppPermission.ManageFoodMenu,
            AppPermission.ManageCheckIns,
            AppPermission.ManageTransportation,
            AppPermission.ManageGuidelines,
            -> role in setOf(UserRole.YouthDirector, UserRole.Pastor, UserRole.Leader) &&
                user.isOwnChurchCamping(camping)

            AppPermission.ManageTeams -> role in setOf(
                UserRole.YouthDirector,
                UserRole.GameMaster,
                UserRole.Leader,
            ) && user.isOwnChurchCamping(camping)

            AppPermission.ManageGames,
            AppPermission.AwardAchievements,
            AppPermission.ViewParticipantProfiles,
            -> role in setOf(
                UserRole.YouthDirector,
                UserRole.Pastor,
                UserRole.GameMaster,
                UserRole.Leader,
            ) && user.isOwnChurchCamping(camping)

            AppPermission.AssignPoints -> role in setOf(
                UserRole.YouthDirector,
                UserRole.GameMaster,
                UserRole.Leader,
            ) && user.isOwnChurchCamping(camping)

            AppPermission.RevealWinners -> role == UserRole.GameMaster &&
                user.isOwnChurchCamping(camping)

            AppPermission.ManageAlbumMedia -> role == UserRole.Photographer &&
                user.isOwnChurchCamping(camping)

            AppPermission.RevokeAchievements,
            AppPermission.AssignLeadershipRoles,
            AppPermission.ViewAdminTools,
            AppPermission.DeleteAnnouncements,
            AppPermission.ManageSongs,
            -> false

            AppPermission.EditAnnouncements,
            AppPermission.ModerateContent,
            -> role in setOf(UserRole.YouthDirector, UserRole.Pastor, UserRole.Leader)

            AppPermission.AssignOwnChurchRoles -> role in setOf(
                UserRole.YouthDirector,
                UserRole.Pastor,
            ) && user.isOwnChurchCamping(camping)

            AppPermission.ManagePolls,
            AppPermission.ModerateCampingChat,
            -> hasPermission(user, AppPermission.EditCamping, camping) ||
                hasPermission(user, AppPermission.EditAnnouncements, camping)

            AppPermission.ModerateTeamChat -> hasPermission(user, AppPermission.EditCamping, camping) ||
                hasPermission(user, AppPermission.ManageTeams, camping) ||
                hasPermission(user, AppPermission.ManageGames, camping)
        }
    }

    fun canAssignRole(
        assigner: PermissionUser?,
        targetRole: UserRole,
        camping: CampingPermissionContext? = null,
    ): Boolean {
        if (assigner?.role == UserRole.Admin) return true

        return targetRole.isSelfAssignable &&
            hasPermission(assigner, AppPermission.AssignOwnChurchRoles, camping)
    }

    private fun PermissionUser?.isOwnChurchCamping(camping: CampingPermissionContext?): Boolean {
        val church = this?.church?.trim()?.takeUnless { it.isBlank() } ?: return false
        val organizerType = camping?.organizerLevelType?.trim() ?: return false
        val organizerValue = camping.organizerLevelValue?.trim()?.takeUnless { it.isBlank() }
            ?: return false

        return organizerType.equals("church", ignoreCase = true) &&
            organizerValue.equals(church, ignoreCase = true)
    }
}

package fr.ziyon.campzone.core.permissions

sealed interface AppPermission {
    data object ViewPublicContent : AppPermission
    data object RegisterForCampings : AppPermission
    data object ManageFamilyRegistrations : AppPermission
    data object CreateCamping : AppPermission
    data object EditCamping : AppPermission
    data object CancelCamping : AppPermission
    data object ApproveRegistrations : AppPermission
    data object ManageSchedule : AppPermission
    data object ManageFoodMenu : AppPermission
    data object ManageTeams : AppPermission
    data object ManageGames : AppPermission
    data object AssignPoints : AppPermission
    data object RevealWinners : AppPermission
    data object ManageAlbumMedia : AppPermission
    data object ManageCheckIns : AppPermission
    data object ManageTransportation : AppPermission
    data object AwardAchievements : AppPermission
    data object RevokeAchievements : AppPermission
    data object ViewParticipantProfiles : AppPermission
    data object EditAnnouncements : AppPermission
    data object DeleteAnnouncements : AppPermission
    data object ModerateContent : AppPermission
    data object ManageGuidelines : AppPermission
    data object AssignOwnChurchRoles : AppPermission
    data object AssignLeadershipRoles : AppPermission
    data object ViewAdminTools : AppPermission
    data object ManageSongs : AppPermission
    data object ManagePolls : AppPermission
    data object ModerateCampingChat : AppPermission
    data object ModerateTeamChat : AppPermission

    companion object {
        val entries: List<AppPermission> = listOf(
            ViewPublicContent,
            RegisterForCampings,
            ManageFamilyRegistrations,
            CreateCamping,
            EditCamping,
            CancelCamping,
            ApproveRegistrations,
            ManageSchedule,
            ManageFoodMenu,
            ManageTeams,
            ManageGames,
            AssignPoints,
            RevealWinners,
            ManageAlbumMedia,
            ManageCheckIns,
            ManageTransportation,
            AwardAchievements,
            RevokeAchievements,
            ViewParticipantProfiles,
            EditAnnouncements,
            DeleteAnnouncements,
            ModerateContent,
            ManageGuidelines,
            AssignOwnChurchRoles,
            AssignLeadershipRoles,
            ViewAdminTools,
            ManageSongs,
            ManagePolls,
            ModerateCampingChat,
            ModerateTeamChat,
        )
    }
}

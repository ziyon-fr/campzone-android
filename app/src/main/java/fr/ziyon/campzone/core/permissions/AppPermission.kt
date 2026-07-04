package fr.ziyon.campzone.core.permissions

sealed interface AppPermission {
    data object ViewPublishedCampings : AppPermission
    data object RegisterForCampings : AppPermission
    data object ApproveRegistrations : AppPermission
    data object CreateCampings : AppPermission
    data object EditCampings : AppPermission
    data object CancelCampings : AppPermission
    data object CreateOwnChurchCampings : AppPermission
    data object EditOwnChurchCampings : AppPermission
    data object CancelOwnChurchCampings : AppPermission
    data object ViewAnnouncements : AppPermission
    data object CreateAnnouncements : AppPermission
    data object EditAnnouncements : AppPermission
    data object DeleteAnnouncements : AppPermission
    data object ViewSongbook : AppPermission
    data object ManageSongbook : AppPermission
    data object ManageSchedule : AppPermission
    data object ManageTeams : AppPermission
    data object ManageGames : AppPermission
    data object AssignPoints : AppPermission
    data object RevealWinners : AppPermission
    data object ManageAlbumMedia : AppPermission
    data object ManageAlbumSettings : AppPermission
    data object ManageTransportation : AppPermission
    data object ManageOwnChurchTransportation : AppPermission
    data object AwardAchievements : AppPermission
    data object RevokeAchievements : AppPermission
    data object ManageCheckIns : AppPermission
    data object ManageOwnChurchCheckIns : AppPermission
    data object ViewParticipantProfiles : AppPermission
    data object AssignLeadershipRoles : AppPermission
    data object AssignOwnChurchRoles : AppPermission
    data object ViewAdminTools : AppPermission
    data object ManageFamilyRegistrations : AppPermission
    data object EditGuidelines : AppPermission
    data object EditOwnChurchGuidelines : AppPermission

    companion object {
        val entries: List<AppPermission> = listOf(
            ViewPublishedCampings,
            RegisterForCampings,
            ApproveRegistrations,
            CreateCampings,
            EditCampings,
            CancelCampings,
            CreateOwnChurchCampings,
            EditOwnChurchCampings,
            CancelOwnChurchCampings,
            ViewAnnouncements,
            CreateAnnouncements,
            EditAnnouncements,
            DeleteAnnouncements,
            ViewSongbook,
            ManageSongbook,
            ManageSchedule,
            ManageTeams,
            ManageGames,
            AssignPoints,
            RevealWinners,
            ManageAlbumMedia,
            ManageAlbumSettings,
            ManageTransportation,
            ManageOwnChurchTransportation,
            AwardAchievements,
            RevokeAchievements,
            ManageCheckIns,
            ManageOwnChurchCheckIns,
            ViewParticipantProfiles,
            AssignLeadershipRoles,
            AssignOwnChurchRoles,
            ViewAdminTools,
            ManageFamilyRegistrations,
            EditGuidelines,
            EditOwnChurchGuidelines,
        )
    }
}

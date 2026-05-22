package fr.ziyon.campzone.core.permissions

enum class UserRole(val rawValue: String) {
    Guest("guest"),
    User("user"),
    Adult("adult"),
    YouthDirector("youth_director"),
    Pastor("pastor"),
    GameMaster("game_master"),
    Leader("leader"),
    Photographer("photographer"),
    Admin("admin");

    val isAdmin: Boolean
        get() = this == Admin

    val isLeadership: Boolean
        get() = this in leadershipRoles

    val isSelfAssignable: Boolean
        get() = this in selfAssignableRoles

    companion object {
        val allWireRoles = entries
        val selfAssignableRoles = setOf(Guest, User, Adult)
        val leadershipRoles = setOf(
            YouthDirector,
            Pastor,
            GameMaster,
            Leader,
            Photographer,
            Admin,
        )

        fun fromWire(rawValue: String?): UserRole = when (rawValue?.trim()?.lowercase()) {
            "guest" -> Guest
            "user", "senior", "youth" -> User
            "adult" -> Adult
            "youth_director" -> YouthDirector
            "pastor" -> Pastor
            "game_master" -> GameMaster
            "leader" -> Leader
            "photographer" -> Photographer
            "admin" -> Admin
            else -> Guest
        }
    }
}

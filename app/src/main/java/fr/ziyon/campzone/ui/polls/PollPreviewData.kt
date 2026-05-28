package fr.ziyon.campzone.ui.polls

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.Poll
import fr.ziyon.campzone.data.model.PollOption
import java.util.Date

/** Shared sample data for the poll `@Preview`s in this package. */
internal fun pollPreviewUser() = AuthenticatedUser(
    uid = "admin",
    email = "admin@campzone.local",
    displayName = "Camp Office",
    photoUrl = null,
    role = UserRole.Admin,
    church = "Central SDA",
    age = 35,
    preferredLanguage = "en",
    gender = null,
    onboardingCompleted = true,
)

internal fun pollPreviewCamping() = Camping(
    id = "preview-camp",
    title = "Summer Camp 2026",
    description = "",
    startDate = Date(),
    endDate = Date(System.currentTimeMillis() + 5L * 24 * 3_600_000),
    organizerLevel = OrganizerLevel(OrganizerType.Church, "Central SDA"),
    location = "Lakeview",
    registrationStatus = CampingRegistrationStatus.Open,
)

internal fun previewActivePoll() = Poll(
    id = "p1",
    campingId = "preview-camp",
    question = "What should the Saturday breakfast be?",
    description = "Vote before 7am — service starts at 8.",
    options = listOf(
        PollOption("a", "Pancakes", 12),
        PollOption("b", "Continental spread", 7),
        PollOption("c", "Fresh fruit & granola", 15),
    ),
    isOpen = true,
    createdByName = "Camp Office",
    createdAt = Date(),
    closesAt = Date(System.currentTimeMillis() + 5L * 3_600_000),
)

internal fun previewClosedPoll() = Poll(
    id = "p2",
    campingId = "preview-camp",
    question = "Best memory of last year?",
    options = listOf(
        PollOption("a", "Friday bonfire", 22),
        PollOption("b", "Lake hike", 14),
        PollOption("c", "Worship night", 31),
    ),
    isOpen = false,
    createdByName = "Camp Office",
    createdAt = Date(),
)

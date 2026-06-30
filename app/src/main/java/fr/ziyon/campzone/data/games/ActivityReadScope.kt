package fr.ziyon.campzone.data.games

import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus

/**
 * Strongest activity-ledger query the current viewer may issue. Firestore
 * rules are not filters: participant listeners must carry
 * `visibility == immediate`, while unauthorized viewers start no listener.
 */
enum class ActivityReadScope {
    None,
    Immediate,
    All;

    companion object {
        fun resolve(
            camping: Camping?,
            userId: String?,
            canReadFullLedger: Boolean,
        ): ActivityReadScope {
            camping ?: return None
            if (canReadFullLedger) return All

            val registration = camping.directRegistrationForAuthenticatedUser(userId)
            if (registration?.registrationStatus != RegistrationApprovalStatus.Approved) return None

            return if (camping.winnerRevealPolicy?.hasRevealFired() == true) All else Immediate
        }
    }
}

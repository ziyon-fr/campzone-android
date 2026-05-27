package fr.ziyon.campzone.ui.games

import fr.ziyon.campzone.data.model.PointRuleTarget
import fr.ziyon.campzone.data.model.PointRuleVisibility

val PointRuleTarget.displayName: String
    get() = when (this) {
        PointRuleTarget.Team -> "Team"
        PointRuleTarget.User -> "Participant"
        PointRuleTarget.Any -> "Team or participant"
    }

val PointRuleVisibility.displayName: String
    get() = when (this) {
        PointRuleVisibility.Immediate -> "Visible immediately"
        PointRuleVisibility.AfterReveal -> "Hidden until reveal"
    }

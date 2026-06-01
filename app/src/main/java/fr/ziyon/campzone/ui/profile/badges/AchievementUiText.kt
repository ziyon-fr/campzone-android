package fr.ziyon.campzone.ui.profile.badges

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.ziyon.campzone.R
import fr.ziyon.campzone.data.model.AchievementRarity

@Composable
internal fun AchievementRarity.localizedDisplayName(): String = stringResource(
    when (this) {
        AchievementRarity.Common -> R.string.achievement_rarity_common
        AchievementRarity.Uncommon -> R.string.achievement_rarity_uncommon
        AchievementRarity.Rare -> R.string.achievement_rarity_rare
        AchievementRarity.Epic -> R.string.achievement_rarity_epic
        AchievementRarity.Legendary -> R.string.achievement_rarity_legendary
    },
)

@Composable
internal fun AchievementRarity.localizedMaterialName(): String = stringResource(
    when (this) {
        AchievementRarity.Common -> R.string.achievement_material_silver
        AchievementRarity.Uncommon -> R.string.achievement_material_gold
        AchievementRarity.Rare -> R.string.achievement_material_platinum
        AchievementRarity.Epic -> R.string.achievement_material_diamond
        AchievementRarity.Legendary -> R.string.achievement_material_painite
    },
)

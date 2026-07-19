package fr.ziyon.campzone.ui.profile

// Icons Imports
// Foundations
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AssignmentInd
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import fr.ziyon.campzone.BuildConfig
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzBadgeTone
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.ThemePicker
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import java.util.Locale

@Composable
fun ProfileSettingsScreen(
    authenticatedUser: AuthenticatedUser,
    onEditProfile: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenFamilyParticipants: () -> Unit,
    onOpenMyVehicles: () -> Unit,
    onOpenAdminTools: () -> Unit,
    onOpenDataExport: () -> Unit,
    onOpenSupport: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val permissionUser = remember(authenticatedUser.role, authenticatedUser.church) {
        PermissionUser(
            role = authenticatedUser.role,
            userId = authenticatedUser.uid,
            church = authenticatedUser.church,
        )
    }
    val permissions = remember { AppPermissionEvaluator() }
    val canViewAdminUi = permissions.canViewAdminTools(permissionUser) ||
        permissions.canModerateContent(permissionUser)
    val canManageFamily = permissions.hasPermission(
        permissionUser,
        AppPermission.ManageFamilyRegistrations,
    )
    val notificationsEnabled = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(CzSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                modifier = Modifier.weight(1f),
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineLarge,
            )

            CzButton(
                text = stringResource(R.string.profile_sign_out),
                onClick = onSignOut,
                variant = CzButtonVariant.Destructive,
            )
        }

        ProfileSettingsCard(
            user = authenticatedUser,
            onClick = {
                haptics.profileImpact()
                onEditProfile()
            },
        )

        ProfileSettingsSection(
            title = stringResource(R.string.profile_settings_section),
            icon = Icons.Rounded.Settings,
        ) {
            ProfileSettingsActionRow(
                icon = Icons.Rounded.WorkspacePremium,
                title = stringResource(R.string.profile_my_achievements),
                onClick = {
                    haptics.profileImpact()
                    onOpenAchievements()
                },
            )
            ProfileSettingsActionRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.profile_notifications),
                value = if (notificationsEnabled) {
                    stringResource(R.string.common_on)
                } else {
                    stringResource(R.string.common_off)
                },
                onClick = {
                    haptics.profileImpact()
                    onOpenNotifications()
                },
            )
            if (canManageFamily) {
                ProfileSettingsActionRow(
                    icon = Icons.Rounded.FamilyRestroom,
                    title = stringResource(R.string.profile_family_participants),
                    onClick = {
                        haptics.profileImpact()
                        onOpenFamilyParticipants()
                    },
                )
            }
            ProfileSettingsActionRow(
                icon = Icons.Rounded.DirectionsCar,
                title = stringResource(R.string.profile_my_vehicles),
                onClick = {
                    haptics.profileImpact()
                    onOpenMyVehicles()
                },
            )
            ProfileSettingsActionRow(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.profile_language),
                value = currentLanguageName(),
                onClick = {
                    haptics.profileImpact()
                    context.startActivity(languageSettingsIntent(context.packageName))
                },
            )
            ProfileSettingsInfoRow(
                icon = Icons.Rounded.DownloadForOffline,
                title = stringResource(R.string.profile_offline_content),
                note = stringResource(R.string.profile_available),
            )
        }

        ProfileSettingsSection(
            title = stringResource(R.string.profile_appearance),
            icon = Icons.Rounded.Palette,
            footer = stringResource(R.string.profile_theme_footer),
        ) {
            ThemePicker()
        }

        if (canViewAdminUi) {
            ProfileSettingsSection(
                title = stringResource(R.string.profile_admin),
                icon = Icons.Rounded.AdminPanelSettings,
            ) {
                ProfileSettingsActionRow(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = stringResource(R.string.profile_admin_tools),
                    onClick = {
                        haptics.profileImpact()
                        onOpenAdminTools()
                    },
                )
                ProfileSettingsInfoRow(
                    icon = Icons.Rounded.AssignmentInd,
                    title = stringResource(R.string.profile_role_assignment),
                    note = stringResource(R.string.profile_firebase),
                )
                ProfileSettingsInfoRow(
                    icon = Icons.Rounded.VerifiedUser,
                    title = stringResource(R.string.profile_security_rules),
                    note = stringResource(R.string.profile_firebase),
                )
            }
        }

        ProfileSettingsSection(
            title = stringResource(R.string.profile_account),
            icon = Icons.Rounded.CheckCircle,
        ) {
            ProfileSettingsStatusRow(
                isComplete = authenticatedUser.onboardingCompleted,
            )
            ProfileSettingsInfoRow(
                icon = Icons.Rounded.Badge,
                title = stringResource(R.string.profile_role),
                note = authenticatedUser.role.localizedName(),
            )
            ProfileSettingsInfoRow(
                icon = Icons.Rounded.Key,
                title = stringResource(R.string.profile_sign_in),
                note = authenticatedUser.providerIds
                    .takeUnless { it.isEmpty() }
                    ?.joinToString(", ")
                    ?: stringResource(R.string.profile_unknown_provider),
            )
            ProfileSettingsActionRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.profile_export_my_data),
                onClick = {
                    haptics.profileImpact()
                    onOpenDataExport()
                },
            )
        }

        ProfileSettingsSection(
            title = stringResource(R.string.profile_about),
            icon = Icons.Rounded.Info,
            footer = stringResource(R.string.profile_about_footer),
        ) {
            ProfileSettingsInfoRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.profile_title),
                note = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            ProfileSettingsInfoRow(
                icon = Icons.Rounded.Gavel,
                title = stringResource(R.string.profile_developer),
                note = stringResource(R.string.profile_developer_value),
            )
            ProfileSettingsActionRow(
                icon = Icons.Rounded.Favorite,
                title = stringResource(R.string.profile_support_campzone),
                onClick = {
                    haptics.profileImpact()
                    onOpenSupport()
                },
            )
            ProfileSettingsInfoRow(
                icon = Icons.Rounded.Translate,
                title = stringResource(R.string.profile_translator),
                note = stringResource(R.string.profile_translator_value),
            )
            ProfileSettingsInfoRow(
                icon = Icons.Rounded.Groups,
                title = stringResource(R.string.profile_community),
                note = stringResource(R.string.profile_community_value),
            )
            ProfileSettingsInfoRow(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.profile_built_with),
                note = stringResource(R.string.profile_built_with_value),
            )
        }

        Spacer(modifier = Modifier.height(CzSpacing.md))
    }
}

@Composable
private fun ProfileSettingsCard(
    user: AuthenticatedUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CzRadius.lg),
        color = colors.surface,
        contentColor = colors.textPrimary,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.base),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CzAvatar(
                imageUrl = user.photoUrl,
                contentDescription = user.preferredDisplayName,
                initials = user.preferredDisplayName,
                size = CzAvatarSize.Medium,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = user.preferredDisplayName,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.email.ifBlank {
                        stringResource(R.string.profile_complete_after_sign_in)
                    },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.role.localizedName(),
                    color = colors.accent,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary
            )
        }
    }
}

@Composable
private fun ProfileSettingsSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // Null is acceptable here since the adjacent Text element provides the accessibility context
                tint = colors.accent,
                modifier = Modifier.size(18.dp) // Adjusted to align proportionally with labelLarge typography height
            )
            Text(
                text = title,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CzRadius.lg),
            color = colors.surface,
            contentColor = colors.textPrimary,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                content = content,
            )
        }
        if (footer != null) {
            Text(
                text = footer,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProfileSettingsActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    ProfileSettingsBaseRow(
        icon = icon,
        title = title,
        value = value,
        modifier = modifier.clickable(onClick = onClick),
        trailing = Icons.Default.ChevronRight,
    )
}

@Composable
private fun ProfileSettingsInfoRow(
    icon: ImageVector,
    title: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    ProfileSettingsBaseRow(
        icon = icon,
        title = title,
        value = note,
        modifier = modifier,
    )
}

@Composable
private fun ProfileSettingsStatusRow(
    isComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    val badgeTone = if (isComplete) CzBadgeTone.Success else CzBadgeTone.Warning

    val rowIcon: ImageVector = if (isComplete) {
        Icons.Rounded.CheckCircle
    } else {
        Icons.Rounded.Warning
    }

    ProfileSettingsBaseRow(
        icon = rowIcon,
        title = stringResource(R.string.profile_title),
        modifier = modifier,
        valueContent = {
            CzBadge(
                text = if (isComplete) {
                    stringResource(R.string.profile_complete)
                } else {
                    stringResource(R.string.profile_incomplete)
                },
                tone = badgeTone,
            )
        },
    )
}

@Composable
private fun ProfileSettingsBaseRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    trailing: ImageVector? = null,
    valueContent: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CzSpacing.minTouchTarget)
            .padding(vertical = CzSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // Null is acceptable here since the adjacent Text element provides the accessibility context
            tint = colors.accent,
            modifier = Modifier.size(18.dp) // Adjusted to align proportionally with labelLarge typography height
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (valueContent != null) {
            valueContent()
        } else if (value != null) {
            Text(
                text = value,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Icon(
                imageVector = trailing,
                null,
                tint = colors.textSecondary
            )
        }
    }
}

@Composable
private fun UserRole.localizedName(): String =
    stringResource(
        when (this) {
            UserRole.User -> R.string.role_user
            UserRole.Adult -> R.string.role_adult
            UserRole.YouthDirector -> R.string.role_youth_director
            UserRole.Pastor -> R.string.role_pastor
            UserRole.GameMaster -> R.string.role_game_master
            UserRole.Leader -> R.string.role_leader
            UserRole.Photographer -> R.string.role_photographer
            UserRole.Admin -> R.string.role_admin
        },
    )

private fun currentLanguageName(): String {
    val locale = Locale.getDefault()
    return locale.getDisplayLanguage(locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        .ifBlank { locale.toLanguageTag() }
}

private fun languageSettingsIntent(packageName: String): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    } else {
        Intent(Settings.ACTION_LOCALE_SETTINGS)
    }

private fun androidx.compose.ui.hapticfeedback.HapticFeedback.profileImpact() {
    performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
}

@Preview(showBackground = true)
@Composable
private fun ProfileSettingsScreenPreview() {
    CampzoneTheme {
        ProfileSettingsScreen(
            authenticatedUser = AuthenticatedUser(
                uid = "preview",
                email = "preview@example.com",
                displayName = "Preview Camper",
                photoUrl = null,
                role = UserRole.Admin,
                church = "Paris Central SDA",
                age = 22,
                preferredLanguage = "fr",
                gender = null,
                onboardingCompleted = true,
                providerIds = listOf("google.com", "password"),
            ),
            onEditProfile = {},
            onOpenAchievements = {},
            onOpenNotifications = {},
            onOpenFamilyParticipants = {},
            onOpenMyVehicles = {},
            onOpenAdminTools = {},
            onOpenDataExport = {},
            onOpenSupport = {},
            onSignOut = {},
        )
    }
}

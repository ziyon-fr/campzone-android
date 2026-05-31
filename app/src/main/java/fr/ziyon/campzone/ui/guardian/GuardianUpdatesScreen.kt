package fr.ziyon.campzone.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.guardian.GuardianChildUpdate
import fr.ziyon.campzone.data.model.CheckInMethod
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMemberRole
import fr.ziyon.campzone.ui.schedule.icon
import fr.ziyon.campzone.ui.schedule.programTimeText
import fr.ziyon.campzone.ui.teams.symbolIcon
import java.text.DateFormat

@Composable
fun GuardianUpdatesRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuardianUpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
    }

    GuardianUpdatesScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardianUpdatesScreen(
    state: GuardianUpdatesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.guardian_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GuardianUpdatesUiState.Loading -> CzLoadingView(
                    message = stringResource(R.string.guardian_loading),
                    modifier = Modifier.fillMaxSize(),
                )

                is GuardianUpdatesUiState.Error -> CzErrorState(
                    title = stringResource(R.string.guardian_error_title),
                    message = stringResource(R.string.guardian_error_message),
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                GuardianUpdatesUiState.Empty -> CzEmptyState(
                    title = stringResource(R.string.guardian_empty_title),
                    message = stringResource(R.string.guardian_empty_message),
                    icon = {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.textSecondary,
                            modifier = Modifier.size(42.dp),
                        )
                    },
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is GuardianUpdatesUiState.Loaded -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(CzSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
                ) {
                    items(state.children, key = { it.id }) { GuardianChildDetailCard(it) }
                    item("program") {
                        GuardianProgramCard(
                            current = state.currentPrograms,
                            next = state.upcomingProgram,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GuardianChildDetailCard(update: GuardianChildUpdate) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CzAvatar(
                    imageUrl = update.attendee.photoUrl,
                    contentDescription = update.attendee.displayName,
                    initials = initialsOf(update.attendee.displayName),
                    size = CzAvatarSize.Medium,
                )
                Column {
                    Text(
                        text = update.attendee.displayName,
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.guardian_child_age_church,
                            update.attendee.age,
                            update.attendee.church,
                        ),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.czColors.divider)

            CheckInRow(update)
            TeamRow(update)
        }
    }
}

@Composable
private fun CheckInRow(update: GuardianChildUpdate) {
    StatusRow(
        icon = if (update.isCheckedIn) Icons.Filled.CheckCircle else Icons.Filled.HelpOutline,
        iconTint = if (update.isCheckedIn) MaterialTheme.czColors.success else MaterialTheme.czColors.textSecondary,
        label = stringResource(R.string.guardian_checkin_label),
        value = checkInStatusText(update),
        trailing = update.checkIn?.method?.let { method ->
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (method == CheckInMethod.Qr) Icons.Filled.QrCode else Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(
                            if (method == CheckInMethod.Qr) R.string.guardian_method_qr else R.string.guardian_method_manual,
                        ),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
    )
}

@Composable
private fun TeamRow(update: GuardianChildUpdate) {
    val team = update.team
    if (team == null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = null,
                tint = MaterialTheme.czColors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.guardian_not_on_team),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        StatusRow(
            icon = symbolIcon(team.symbolName),
            iconTint = MaterialTheme.czColors.ember,
            label = stringResource(R.string.guardian_team_label),
            value = teamLine(team, update),
            trailing = {
                val score = update.personalScore
                if (score != null) {
                    Text(
                        text = stringResource(R.string.guardian_score_pts, score),
                        color = MaterialTheme.czColors.ember,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                } else if (update.teamMember != null) {
                    Text(
                        text = stringResource(R.string.guardian_scores_hidden),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
        )
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = value,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        trailing?.invoke()
    }
}

@Composable
internal fun GuardianProgramCard(current: List<Program>, next: Program?) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                )
                Column {
                    Text(
                        text = stringResource(R.string.guardian_today_title),
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.guardian_today_subtitle),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (current.isEmpty() && next == null) {
                Text(
                    text = stringResource(R.string.guardian_nothing_scheduled),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                if (current.isNotEmpty()) {
                    ProgramSectionLabel(stringResource(R.string.guardian_happening_now))
                    current.forEach { ProgramRow(it, highlighted = true) }
                }
                if (next != null) {
                    ProgramSectionLabel(stringResource(R.string.guardian_up_next))
                    ProgramRow(next, highlighted = false)
                }
            }
        }
    }
}

@Composable
private fun ProgramSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.czColors.textSecondary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ProgramRow(program: Program, highlighted: Boolean) {
    Surface(
        color = if (highlighted) MaterialTheme.czColors.ember.copy(alpha = 0.08f) else MaterialTheme.czColors.background,
        shape = RoundedCornerShape(CzRadius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = program.type.icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.czColors.ember else MaterialTheme.czColors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = program.title,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = buildString {
                        append(program.startDate.programTimeText())
                        if (program.location.isNotBlank()) append(" · ${program.location}")
                    },
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

// region helpers

@Composable
private fun checkInStatusText(update: GuardianChildUpdate): String {
    val checkIn = update.checkIn ?: return stringResource(R.string.guardian_not_checked_in)
    val at = checkIn.checkedInAt
        ?: return stringResource(R.string.guardian_checked_in)
    val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(at)
    return stringResource(R.string.guardian_checked_in_at, formatted)
}

@Composable
private fun teamLine(team: Team, update: GuardianChildUpdate): String {
    val role = update.teamMember?.role
    val suffix = when (role) {
        TeamMemberRole.Captain -> " · " + stringResource(R.string.guardian_role_captain)
        TeamMemberRole.ViceCaptain -> " · " + stringResource(R.string.guardian_role_vice_captain)
        else -> ""
    }
    return team.name + suffix
}

internal fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")

// endregion

internal fun guardianChildPreview(): GuardianChildUpdate = GuardianChildUpdate(
    attendee = fr.ziyon.campzone.data.model.CampingAttendee(
        id = "c1",
        userId = "c1",
        displayName = "Ana Silva",
        church = "Paris Central SDA",
        age = 9,
        languages = listOf("fr"),
        registrationStatus = fr.ziyon.campzone.data.model.RegistrationApprovalStatus.Approved,
        participantKind = fr.ziyon.campzone.data.model.RegistrationParticipantKind.Child,
        guardianId = "g1",
    ),
    checkIn = null,
    team = null,
    teamMember = null,
    scoresVisible = true,
)

@Preview
@Composable
private fun GuardianUpdatesScreenPreview() {
    CampzoneTheme {
        GuardianUpdatesScreen(
            state = GuardianUpdatesUiState.Loaded(
                children = listOf(guardianChildPreview()),
                currentPrograms = emptyList(),
                upcomingProgram = null,
            ),
            onBack = {},
            onRetry = {},
        )
    }
}

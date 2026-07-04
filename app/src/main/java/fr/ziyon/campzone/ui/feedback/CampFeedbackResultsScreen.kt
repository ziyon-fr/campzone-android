package fr.ziyon.campzone.ui.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.CampFeedback
import fr.ziyon.campzone.data.model.ProgramFeedback
import kotlin.math.roundToInt

@Composable
fun CampFeedbackResultsRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedbackResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
    }

    CampFeedbackResultsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampFeedbackResultsScreen(
    state: FeedbackResultsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.feedback_results_title)) },
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
                FeedbackResultsUiState.Loading -> CzLoadingView(
                    message = stringResource(R.string.feedback_results_loading),
                    modifier = Modifier.fillMaxSize(),
                )

                FeedbackResultsUiState.Restricted -> CzEmptyState(
                    title = stringResource(R.string.feedback_results_restricted_title),
                    message = stringResource(R.string.feedback_results_restricted_message),
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is FeedbackResultsUiState.Error -> CzErrorState(
                    title = stringResource(R.string.feedback_results_error_title),
                    message = state.message,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is FeedbackResultsUiState.Empty -> CzEmptyState(
                    title = stringResource(R.string.feedback_results_empty_title),
                    message = stringResource(R.string.feedback_results_empty_message),
                    icon = {
                        Icon(
                            Icons.Filled.StarBorder,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.ember,
                            modifier = Modifier.size(42.dp),
                        )
                    },
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is FeedbackResultsUiState.Loaded -> ResultsContent(state)
            }
        }
    }
}

@Composable
private fun ResultsContent(state: FeedbackResultsUiState.Loaded) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item("summary") { SummaryCard(state) }

        if (state.programAverages.isNotEmpty()) {
            item("sessions-header") {
                ResultsSectionHeader(
                    title = stringResource(R.string.feedback_section_sessions),
                    icon = Icons.Filled.Schedule,
                )
            }
            items(state.programAverages, key = { it.title }) { program ->
                ProgramAverageRow(program)
            }
        }

        item("comments-header") {
            ResultsSectionHeader(
                title = stringResource(R.string.feedback_results_comments, state.comments.size),
                icon = Icons.AutoMirrored.Filled.Chat,
            )
        }
        if (state.comments.isEmpty()) {
            item("comments-empty") {
                Surface(
                    color = MaterialTheme.czColors.surface,
                    shape = RoundedCornerShape(CzRadius.lg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.feedback_results_no_comments),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
                    )
                }
            }
        } else {
            items(state.comments, key = { it.id }) { CommentCard(it) }
        }
    }
}

@Composable
private fun SummaryCard(state: FeedbackResultsUiState.Loaded) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            if (!state.campLogoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = state.campLogoUrl,
                    contentDescription = state.campTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .border(3.dp, MaterialTheme.czColors.ember.copy(alpha = 0.45f), CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .border(3.dp, MaterialTheme.czColors.ember.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.campTitle.trim().take(1).uppercase(),
                        color = MaterialTheme.czColors.ember,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = state.campTitle,
                color = MaterialTheme.czColors.ember,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                AverageStars(average = state.averageOverall, starSize = 24.dp)
                Text(
                    text = formatRating(state.averageOverall),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SummaryStat(
                    value = state.responseCount.toString(),
                    label = stringResource(R.string.feedback_results_stat_responses),
                )
                HorizontalDivider(
                    modifier = Modifier
                        .padding(horizontal = CzSpacing.lg)
                        .height(32.dp)
                        .width(1.dp),
                    color = MaterialTheme.czColors.divider,
                )
                SummaryStat(
                    value = stringResource(R.string.feedback_results_percent, state.wouldReturnPercent),
                    label = stringResource(R.string.feedback_results_stat_would_return),
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ProgramAverageRow(program: ProgramAverage) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = program.title,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.feedback_results_rating_count,
                        program.count,
                        program.count,
                    ),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            AverageStars(average = program.average, starSize = 14.dp)
            Text(
                text = formatRating(program.average),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CommentCard(feedback: CampFeedback) {
    val author = if (feedback.isAnonymous) {
        stringResource(R.string.feedback_anonymous_author)
    } else {
        feedback.displayName
    }
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = author,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ExactStars(rating = feedback.overallRating, starSize = 14.dp)
            }
            if (feedback.highlights.isNotBlank()) {
                CommentLine(
                    label = stringResource(R.string.feedback_results_enjoyed),
                    text = feedback.highlights,
                    color = MaterialTheme.czColors.success,
                )
            }
            if (feedback.improvements.isNotBlank()) {
                CommentLine(
                    label = stringResource(R.string.feedback_results_improve),
                    text = feedback.improvements,
                    color = MaterialTheme.czColors.warning,
                )
            }
        }
    }
}

@Composable
private fun CommentLine(label: String, text: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = 2.dp),
            )
        }
        Text(
            text = text,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ResultsSectionHeader(title: String, icon: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.czColors.ember,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = title,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Star row that rounds to the nearest half (matches the iOS `average + 0.5` fill). */
@Composable
private fun AverageStars(average: Double, starSize: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (star in 1..5) {
            Icon(
                imageVector = if (star <= average + 0.5) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

/** Exact integer star row (used for a single response's overall rating). */
@Composable
private fun ExactStars(rating: Int, starSize: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (star in 1..5) {
            Icon(
                imageVector = if (star <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

private fun formatRating(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()

@Preview
@Composable
private fun CampFeedbackResultsScreenPreview() {
    CampzoneTheme {
        CampFeedbackResultsScreen(
            state = FeedbackResultsUiState.Loaded(
                campTitle = "Summer Pathfinder Camp",
                campLogoUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg",
                responseCount = 12,
                averageOverall = 4.3,
                wouldReturnPercent = 92,
                programAverages = listOf(
                    ProgramAverage("Morning Worship", 4.8, 11),
                    ProgramAverage("Team Games", 4.1, 9),
                ),
                comments = listOf(
                    CampFeedback(
                        id = "u1",
                        campingId = "c1",
                        userId = "u1",
                        displayName = "Maria",
                        overallRating = 5,
                        wouldReturn = true,
                        isAnonymous = false,
                        programFeedback = listOf(ProgramFeedback("p1", "Worship", 5)),
                        highlights = "Loved the worship nights.",
                        improvements = "More free time would help.",
                    ),
                    CampFeedback(
                        id = "u2",
                        campingId = "c1",
                        userId = "u2",
                        displayName = "Anon",
                        overallRating = 4,
                        wouldReturn = true,
                        isAnonymous = true,
                        improvements = "Food could be warmer.",
                    ),
                ),
            ),
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun CampFeedbackResultsEmptyPreview() {
    CampzoneTheme {
        CampFeedbackResultsScreen(
            state = FeedbackResultsUiState.Empty("Summer Camp"),
            onBack = {},
            onRetry = {},
        )
    }
}

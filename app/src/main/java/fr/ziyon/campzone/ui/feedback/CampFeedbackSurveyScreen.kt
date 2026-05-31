package fr.ziyon.campzone.ui.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.ProgramFeedback
import java.text.DateFormat

@Composable
fun CampFeedbackSurveyRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedbackSurveyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
    }

    CampFeedbackSurveyScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSetOverallRating = viewModel::setOverallRating,
        onSetHighlights = viewModel::setHighlights,
        onSetImprovements = viewModel::setImprovements,
        onSetWouldReturn = viewModel::setWouldReturn,
        onSetAnonymous = viewModel::setAnonymous,
        onSetProgramRating = viewModel::setProgramRating,
        onSetProgramComment = viewModel::setProgramComment,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampFeedbackSurveyScreen(
    state: FeedbackSurveyUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSetOverallRating: (Int) -> Unit,
    onSetHighlights: (String) -> Unit,
    onSetImprovements: (String) -> Unit,
    onSetWouldReturn: (Boolean) -> Unit,
    onSetAnonymous: (Boolean) -> Unit,
    onSetProgramRating: (String, Int) -> Unit,
    onSetProgramComment: (String, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.feedback_survey_title)) },
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
        bottomBar = {
            if (state is FeedbackSurveyUiState.Editing) {
                SurveySubmitBar(state = state, onSubmit = onSubmit)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                FeedbackSurveyUiState.Loading -> CzLoadingView(
                    message = stringResource(R.string.feedback_survey_loading),
                    modifier = Modifier.fillMaxSize(),
                )

                is FeedbackSurveyUiState.Error -> CzErrorState(
                    title = stringResource(R.string.feedback_survey_error_title),
                    message = state.message,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is FeedbackSurveyUiState.NotAvailable -> CzEmptyState(
                    title = stringResource(R.string.feedback_survey_not_available_title),
                    message = stringResource(
                        R.string.feedback_survey_not_available_message,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(state.opensOn),
                    ),
                    icon = { StateIcon(Icons.Filled.Schedule) },
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is FeedbackSurveyUiState.Expired -> CzEmptyState(
                    title = stringResource(R.string.feedback_survey_closed_title),
                    message = stringResource(R.string.feedback_survey_closed_message),
                    icon = { StateIcon(Icons.Filled.LockClock) },
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is FeedbackSurveyUiState.Submitted -> ThanksContent(
                    rating = state.overallRating,
                    onDone = onBack,
                )

                is FeedbackSurveyUiState.Editing -> SurveyForm(
                    state = state,
                    onSetOverallRating = onSetOverallRating,
                    onSetHighlights = onSetHighlights,
                    onSetImprovements = onSetImprovements,
                    onSetWouldReturn = onSetWouldReturn,
                    onSetAnonymous = onSetAnonymous,
                    onSetProgramRating = onSetProgramRating,
                    onSetProgramComment = onSetProgramComment,
                )
            }
        }
    }
}

@Composable
private fun SurveyForm(
    state: FeedbackSurveyUiState.Editing,
    onSetOverallRating: (Int) -> Unit,
    onSetHighlights: (String) -> Unit,
    onSetImprovements: (String) -> Unit,
    onSetWouldReturn: (Boolean) -> Unit,
    onSetAnonymous: (Boolean) -> Unit,
    onSetProgramRating: (String, Int) -> Unit,
    onSetProgramComment: (String, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
    ) {
        item("header") { SurveyHeaderCard(state.campTitle) }

        item("overall") {
            SurveySection(
                title = stringResource(R.string.feedback_section_overall),
                icon = Icons.Filled.Star,
            ) {
                SurveyCard {
                    Text(
                        text = stringResource(R.string.feedback_overall_prompt),
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    StarRatingPicker(
                        rating = state.overallRating,
                        onRate = onSetOverallRating,
                        contentDescription = stringResource(R.string.feedback_overall_prompt),
                    )
                }
            }
        }

        if (state.programFeedback.isNotEmpty()) {
            item("sessions-header") {
                SurveySectionHeader(
                    title = stringResource(R.string.feedback_section_sessions),
                    icon = Icons.Filled.Schedule,
                )
            }
            items(state.programFeedback, key = { it.id }) { program ->
                ProgramFeedbackRow(
                    program = program,
                    onRate = { onSetProgramRating(program.id, it) },
                    onComment = { onSetProgramComment(program.id, it) },
                )
            }
        }

        item("highlights") {
            SurveySection(
                title = stringResource(R.string.feedback_section_highlights),
                icon = Icons.Filled.WbSunny,
            ) {
                SurveyTextAreaCard(
                    prompt = stringResource(R.string.feedback_highlights_prompt),
                    value = state.highlights,
                    onValueChange = onSetHighlights,
                    label = stringResource(R.string.feedback_section_highlights),
                )
            }
        }

        item("improvements") {
            SurveySection(
                title = stringResource(R.string.feedback_section_improvements),
                icon = Icons.Filled.Lightbulb,
            ) {
                SurveyTextAreaCard(
                    prompt = stringResource(R.string.feedback_improvements_prompt),
                    value = state.improvements,
                    onValueChange = onSetImprovements,
                    label = stringResource(R.string.feedback_section_improvements),
                )
            }
        }

        item("return") {
            ToggleCard(
                title = stringResource(R.string.feedback_would_return),
                subtitle = null,
                checked = state.wouldReturn,
                onCheckedChange = onSetWouldReturn,
            )
        }

        item("privacy") {
            ToggleCard(
                title = stringResource(R.string.feedback_anonymous_title),
                subtitle = stringResource(R.string.feedback_anonymous_subtitle),
                checked = state.isAnonymous,
                onCheckedChange = onSetAnonymous,
            )
        }
    }
}

@Composable
private fun SurveyHeaderCard(campTitle: String) {
    SurveyCard(spacing = CzSpacing.xs) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.feedback_header_eyebrow),
                color = MaterialTheme.czColors.ember,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = campTitle,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.feedback_header_subtitle),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SurveyTextAreaCard(
    prompt: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    SurveyCard(spacing = CzSpacing.sm) {
        Text(
            text = prompt,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        CzTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            singleLine = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
    }
}

@Composable
private fun ToggleCard(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SurveyCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.czColors.surface,
                    checkedTrackColor = MaterialTheme.czColors.ember,
                ),
            )
        }
    }
}

@Composable
private fun ProgramFeedbackRow(
    program: ProgramFeedback,
    onRate: (Int) -> Unit,
    onComment: (String) -> Unit,
) {
    SurveyCard(spacing = CzSpacing.sm) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = program.programTitle,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            StarRatingPicker(
                rating = program.rating,
                onRate = onRate,
                starSize = 22.dp,
                contentDescription = program.programTitle,
            )
        }
        AnimatedVisibility(visible = program.rating > 0) {
            CzTextField(
                value = program.comment,
                onValueChange = onComment,
                label = stringResource(R.string.feedback_program_comment_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ThanksContent(
    rating: Int,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(CzSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Filled.RateReview,
            contentDescription = null,
            tint = MaterialTheme.czColors.ember,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = stringResource(R.string.feedback_thanks_title),
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.feedback_thanks_message),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        StarRatingDisplay(rating = rating, starSize = 24.dp)
        CzButton(
            text = stringResource(R.string.feedback_thanks_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SurveySubmitBar(
    state: FeedbackSurveyUiState.Editing,
    onSubmit: () -> Unit,
) {
    Surface(color = MaterialTheme.czColors.surface, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.operationError != null) {
                Text(
                    text = state.operationError,
                    color = MaterialTheme.czColors.error,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            } else if (state.overallRating == 0) {
                Text(
                    text = stringResource(R.string.feedback_submit_hint),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
            CzButton(
                text = stringResource(R.string.feedback_submit),
                onClick = onSubmit,
                enabled = state.canSubmit,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// region small building blocks

@Composable
private fun SurveySection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        SurveySectionHeader(title = title, icon = icon)
        content()
    }
}

@Composable
private fun SurveySectionHeader(
    title: String,
    icon: ImageVector,
) {
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

@Composable
private fun SurveyCard(
    spacing: androidx.compose.ui.unit.Dp = CzSpacing.md,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

@Composable
private fun StateIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.czColors.ember,
        modifier = Modifier.size(42.dp),
    )
}

/** Interactive 1–5 star picker; tapping a filled star toggles it off. */
@Composable
private fun StarRatingPicker(
    rating: Int,
    onRate: (Int) -> Unit,
    contentDescription: String,
    starSize: androidx.compose.ui.unit.Dp = 28.dp,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
        for (star in 1..5) {
            val selected = star <= rating
            Icon(
                imageVector = if (selected) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = stringResource(R.string.feedback_star_rate, star, contentDescription),
                tint = if (selected) MaterialTheme.czColors.ember else MaterialTheme.czColors.textSecondary,
                modifier = Modifier
                    .size(starSize)
                    .clickable { onRate(if (rating == star) star - 1 else star) },
            )
        }
    }
}

/** Read-only star row used on the thank-you screen. */
@Composable
private fun StarRatingDisplay(
    rating: Int,
    starSize: androidx.compose.ui.unit.Dp = 18.dp,
) {
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

// endregion

@Preview
@Composable
private fun CampFeedbackSurveyScreenPreview() {
    CampzoneTheme {
        CampFeedbackSurveyScreen(
            state = FeedbackSurveyUiState.Editing(
                campTitle = "Summer Pathfinder Camp",
                overallRating = 4,
                programFeedback = listOf(
                    ProgramFeedback(id = "p1", programTitle = "Morning Worship", rating = 5),
                    ProgramFeedback(id = "p2", programTitle = "Team Games", rating = 0),
                ),
                highlights = "The bonfire night was unforgettable.",
            ),
            onBack = {},
            onRetry = {},
            onSetOverallRating = {},
            onSetHighlights = {},
            onSetImprovements = {},
            onSetWouldReturn = {},
            onSetAnonymous = {},
            onSetProgramRating = { _, _ -> },
            onSetProgramComment = { _, _ -> },
            onSubmit = {},
        )
    }
}

@Preview
@Composable
private fun CampFeedbackSurveyThanksPreview() {
    CampzoneTheme {
        CampFeedbackSurveyScreen(
            state = FeedbackSurveyUiState.Submitted(campTitle = "Summer Camp", overallRating = 5),
            onBack = {},
            onRetry = {},
            onSetOverallRating = {},
            onSetHighlights = {},
            onSetImprovements = {},
            onSetWouldReturn = {},
            onSetAnonymous = {},
            onSetProgramRating = { _, _ -> },
            onSetProgramComment = { _, _ -> },
            onSubmit = {},
        )
    }
}

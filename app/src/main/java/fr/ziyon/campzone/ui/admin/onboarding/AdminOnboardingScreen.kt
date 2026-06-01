package fr.ziyon.campzone.ui.admin.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tour
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors

private enum class StepNavTarget { None, CreateCamping, ComposeAnnouncement }

private data class AdminOnboardingStep(
    val id: AdminOnboardingStepId,
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
    val navTarget: StepNavTarget,
)

private val adminOnboardingSteps: List<AdminOnboardingStep> = listOf(
    AdminOnboardingStep(
        id = AdminOnboardingStepId.Camping,
        titleRes = R.string.admin_onboarding_step_camping_title,
        descriptionRes = R.string.admin_onboarding_step_camping_desc,
        icon = Icons.Rounded.Tour,
        navTarget = StepNavTarget.CreateCamping,
    ),
    AdminOnboardingStep(
        id = AdminOnboardingStepId.Announcement,
        titleRes = R.string.admin_onboarding_step_announcement_title,
        descriptionRes = R.string.admin_onboarding_step_announcement_desc,
        icon = Icons.Rounded.Campaign,
        navTarget = StepNavTarget.ComposeAnnouncement,
    ),
    AdminOnboardingStep(
        id = AdminOnboardingStepId.Roles,
        titleRes = R.string.admin_onboarding_step_roles_title,
        descriptionRes = R.string.admin_onboarding_step_roles_desc,
        icon = Icons.Rounded.Groups,
        navTarget = StepNavTarget.None,
    ),
    AdminOnboardingStep(
        id = AdminOnboardingStepId.Rules,
        titleRes = R.string.admin_onboarding_step_rules_title,
        descriptionRes = R.string.admin_onboarding_step_rules_desc,
        icon = Icons.Rounded.Security,
        navTarget = StepNavTarget.None,
    ),
    AdminOnboardingStep(
        id = AdminOnboardingStepId.Notifications,
        titleRes = R.string.admin_onboarding_step_notifications_title,
        descriptionRes = R.string.admin_onboarding_step_notifications_desc,
        icon = Icons.Rounded.Notifications,
        navTarget = StepNavTarget.None,
    ),
)

@Composable
fun AdminOnboardingRoute(
    onBack: () -> Unit,
    onCreateCamping: () -> Unit,
    onComposeAnnouncement: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminOnboardingViewModel = hiltViewModel(),
) {
    val completed by viewModel.completed.collectAsState()

    AdminOnboardingScreen(
        completed = completed,
        onBack = onBack,
        onStepTap = { step ->
            when (step.navTarget) {
                StepNavTarget.None -> viewModel.toggle(step.id)
                StepNavTarget.CreateCamping -> {
                    viewModel.markComplete(step.id)
                    onCreateCamping()
                }
                StepNavTarget.ComposeAnnouncement -> {
                    viewModel.markComplete(step.id)
                    onComposeAnnouncement()
                }
            }
        },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminOnboardingScreen(
    completed: Set<AdminOnboardingStepId>,
    onBack: () -> Unit,
    onStepTap: (AdminOnboardingStep) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val total = adminOnboardingSteps.size
    val done = adminOnboardingSteps.count { it.id in completed }
    val progress = if (total == 0) 0f else done.toFloat() / total.toFloat()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.admin_onboarding_title), color = colors.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            item(key = "progress") {
                ProgressCard(done = done, total = total, progress = progress)
            }

            items(adminOnboardingSteps, key = { it.id.name }) { step ->
                StepRow(
                    step = step,
                    done = step.id in completed,
                    onTap = { onStepTap(step) },
                )
            }

            item(key = "reset") {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.Restore,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(CzSpacing.base),
                    )
                    Spacer(Modifier.size(CzSpacing.xs))
                    Text(
                        text = stringResource(R.string.admin_onboarding_reset),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int, progress: Float) {
    val colors = MaterialTheme.czColors
    val complete = done == total && total > 0
    val animatedProgress by animateFloatAsState(progress, label = "progress")
    val gradient = Brush.horizontalGradient(listOf(colors.ember, colors.warning))

    CzCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (complete) {
                                R.string.admin_onboarding_complete
                            } else {
                                R.string.admin_onboarding_getting_started
                            },
                        ),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.admin_onboarding_steps_done, done, total),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ProgressRing(progress = animatedProgress)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(CzRadius.full))
                    .background(colors.divider),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(CzRadius.full))
                        .background(gradient),
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(progress: Float) {
    val colors = MaterialTheme.czColors
    val gradient = Brush.linearGradient(listOf(colors.ember, colors.warning))
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(
                color = colors.divider,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = gradient,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${(progress * 100).toInt()}%",
            color = colors.ember,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StepRow(
    step: AdminOnboardingStep,
    done: Boolean,
    onTap: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    CzCard(
        onClick = onTap,
        contentDescription = stringResource(step.titleRes),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(CzSpacing.sm))
                    .background(
                        if (done) colors.success.copy(alpha = 0.15f) else colors.ember.copy(alpha = 0.10f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    step.icon,
                    contentDescription = null,
                    tint = if (done) colors.success else colors.ember,
                    modifier = Modifier.size(CzSpacing.lg),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(step.titleRes),
                    color = if (done) colors.textSecondary else colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                )
                Text(
                    text = stringResource(step.descriptionRes),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            when {
                done -> Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.admin_onboarding_step_done),
                    tint = colors.success,
                )
                step.navTarget != StepNavTarget.None -> Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
                else -> Icon(
                    Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = stringResource(R.string.admin_onboarding_step_not_done),
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun AdminOnboardingScreenPreview() {
    CampzoneTheme {
        AdminOnboardingScreen(
            completed = setOf(AdminOnboardingStepId.Camping, AdminOnboardingStepId.Announcement),
            onBack = {},
            onStepTap = {},
            onReset = {},
        )
    }
}

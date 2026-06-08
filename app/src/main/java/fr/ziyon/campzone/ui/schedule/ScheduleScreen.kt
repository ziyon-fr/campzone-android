package fr.ziyon.campzone.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ModeNight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.Program

@Composable
fun ScheduleRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenProgram: (String) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDayId by viewModel.selectedDayId.collectAsState()
    val canManage by viewModel.canManageSchedule.collectAsState()

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId, authenticatedUser) }

    ScheduleScreen(
        uiState = uiState,
        selectedDayId = selectedDayId,
        canManageSchedule = canManage,
        onDaySelected = viewModel::setSelectedDayId,
        onBack = onBack,
        onOpenEditor = onOpenEditor,
        onOpenProgram = onOpenProgram,
        onRetry = { viewModel.load(campingId, authenticatedUser) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    selectedDayId: String?,
    canManageSchedule: Boolean,
    onDaySelected: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenProgram: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val editScheduleDescription = stringResource(R.string.schedule_edit_cd)

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.schedule_title),
                        style = CzTypeScale.headline,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = colors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (canManageSchedule) {
                        IconButton(
                            onClick = onOpenEditor,
                            modifier = Modifier.semantics { contentDescription = editScheduleDescription },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = colors.ember,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                ),
                windowInsets = WindowInsets()
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState) {
                is ScheduleUiState.Loading -> CzLoadingView(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(R.string.schedule_loading),
                )

                is ScheduleUiState.Empty -> CzEmptyState(
                    title = stringResource(R.string.schedule_empty_title),
                    message = stringResource(R.string.schedule_empty_message),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                is ScheduleUiState.Error -> CzErrorState(
                    title = stringResource(R.string.schedule_error_title),
                    message = uiState.message,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                is ScheduleUiState.Loaded -> ScheduleLoadedContent(
                    schedule = uiState.schedule,
                    selectedDayId = selectedDayId,
                    canManageSchedule = canManageSchedule,
                    onDaySelected = onDaySelected,
                    onOpenProgram = onOpenProgram,
                )
            }
        }
    }
}

@Composable
private fun ScheduleLoadedContent(
    schedule: CampingSchedule,
    selectedDayId: String?,
    canManageSchedule: Boolean,
    onDaySelected: (String?) -> Unit,
    onOpenProgram: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    val selectedDay = selectedDayId
        ?.let { id -> schedule.sortedDays.firstOrNull { it.id == id } }
        ?: schedule.sortedDays.firstOrNull()

    LazyColumn(
        contentPadding = PaddingValues(
            start = CzSpacing.base,
            end = CzSpacing.base,
            top = CzSpacing.md,
            bottom = CzSpacing.base,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        if (canManageSchedule) {
            item {
                ReminderBadge(reminderText = stringResource(schedule.reminderTiming.displayNameRes))
            }
        }
        item {
            DayPickerRow(
                days = schedule.sortedDays,
                selectedDayId = selectedDay?.id,
                onDaySelected = onDaySelected,
            )
        }
        item {
            HorizontalDivider(color = colors.divider)
        }
        if (selectedDay != null) {
            if (selectedDay.programs.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CzRadius.md))
                            .background(colors.surface)
                            .padding(CzSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ModeNight,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.schedule_no_programs_for_day),
                            style = CzTypeScale.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
            } else {
                items(selectedDay.programs.sortedBy { it.startDate }, key = { it.id }) { program ->
                    ProgramTimelineCard(
                        program = program,
                        onClick = { onOpenProgram(program.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderBadge(reminderText: String) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .background(colors.surface)
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = null,
            tint = colors.amber,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.schedule_reminders_label),
            style = CzTypeScale.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = reminderText,
            style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun DayPickerRow(
    days: List<CampDay>,
    selectedDayId: String?,
    onDaySelected: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        contentPadding = PaddingValues(horizontal = CzSpacing.xs),
    ) {
        items(days, key = { it.id }) { day ->
            val isSelected = day.id == selectedDayId
            DayChip(day = day, isSelected = isSelected, onClick = { onDaySelected(day.id) })
        }
    }
}

@Composable
private fun DayChip(
    day: CampDay,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val bgColor = if (isSelected) colors.ember else colors.surface
    val fgColor = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.textPrimary
    val secondaryFg = if (isSelected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f) else colors.textSecondary
    val dayDescription = "${day.weekdayText()}, ${day.dayNumberText()}, ${day.title}"

    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(CzRadius.md))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm)
            .semantics { contentDescription = dayDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = day.weekdayText(),
            style = CzTypeScale.caption.copy(fontWeight = FontWeight.Bold),
            color = fgColor,
        )
        Text(
            text = day.dayNumberText(),
            style = CzTypeScale.body.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Default,
            ),
            color = fgColor,
        )
        Text(
            text = day.title,
            style = CzTypeScale.caption,
            color = secondaryFg,
            maxLines = 1,
        )
    }
}

@Composable
fun ProgramTimelineCard(
    program: Program,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val accent = program.resolvedAccentColor

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .clickable(
                onClick = onClick,
                onClickLabel = "${program.title}, ${program.startDate.programTimeText()}, ${program.location}",
            ),
        color = colors.surface,
        shape = RoundedCornerShape(CzRadius.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .width(50.dp)
                    .padding(start = CzSpacing.sm, end = CzSpacing.md),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = program.startDate.programTimeText(),
                    style = CzTypeScale.caption.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    ),
                    color = colors.textPrimary,
                )
                Text(
                    text = program.endDate.programTimeText(),
                    style = CzTypeScale.caption.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(accent, RoundedCornerShape(CzRadius.full)),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                Icon(
                    imageVector = program.resolvedIcon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = program.title,
                        style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = program.location,
                            style = CzTypeScale.caption,
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleScreenLoadingPreview() {
    CampzoneTheme {
        ScheduleScreen(
            uiState = ScheduleUiState.Loading,
            selectedDayId = null,
            canManageSchedule = false,
            onDaySelected = {},
            onBack = {},
            onOpenEditor = {},
            onOpenProgram = {},
            onRetry = {},
        )
    }
}

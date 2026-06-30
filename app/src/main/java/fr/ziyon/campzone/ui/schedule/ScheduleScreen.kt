package fr.ziyon.campzone.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.schedule.AndroidCalendarExportLauncher
import fr.ziyon.campzone.data.schedule.CalendarExportLabels
import fr.ziyon.campzone.data.schedule.ScheduleCalendarExportPlanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val camping by viewModel.camping.collectAsState()

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId, authenticatedUser) }

    ScheduleScreen(
        uiState = uiState,
        campingTitle = camping?.title ?: stringResource(R.string.calendar_default_camping_title),
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
    campingTitle: String,
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val editScheduleDescription = stringResource(R.string.schedule_edit_cd)
    val addToCalendarDescription = stringResource(R.string.schedule_add_to_calendar_cd)
    val calendarChooserTitle = stringResource(R.string.schedule_calendar_chooser)
    val calendarHandoffMessage = stringResource(R.string.schedule_calendar_handoff)
    val calendarNoAppMessage = stringResource(R.string.schedule_calendar_no_app)
    val calendarNoProgramsMessage = stringResource(R.string.schedule_calendar_no_programs)
    val calendarLabels = CalendarExportLabels(
        camping = stringResource(R.string.calendar_notes_camping_label),
        type = stringResource(R.string.calendar_notes_type_label),
    )
    val programTypeNames = ProgramType.entries.associateWith { stringResource(it.displayNameRes) }

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
                    val schedule = (uiState as? ScheduleUiState.Loaded)?.schedule
                    if (schedule != null) {
                        IconButton(
                            enabled = schedule.allPrograms.isNotEmpty(),
                            onClick = {
                                val drafts = ScheduleCalendarExportPlanner.drafts(
                                    schedule = schedule,
                                    campingTitle = campingTitle,
                                    labels = calendarLabels,
                                    typeName = { program ->
                                        program.customType?.trimmedName
                                            ?: programTypeNames.getValue(program.type)
                                    },
                                )
                                val message = when {
                                    drafts.isEmpty() -> calendarNoProgramsMessage
                                    AndroidCalendarExportLauncher.shareSchedule(
                                        context = context,
                                        drafts = drafts,
                                        campingTitle = campingTitle,
                                        chooserTitle = calendarChooserTitle,
                                    ) -> calendarHandoffMessage
                                    else -> calendarNoAppMessage
                                }
                                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EventAvailable,
                                contentDescription = addToCalendarDescription,
                                tint = colors.ember,
                            )
                        }
                    }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                item(key = "program-timeline-${selectedDay.id}") {
                    val programs = selectedDay.programs.sortedBy { it.startDate }
                    val now by produceState(initialValue = java.util.Date()) {
                        while (true) {
                            value = java.util.Date()
                            delay(30_000)
                        }
                    }
                    Column {
                        programs.forEachIndexed { index, program ->
                            ProgramTimelineRow(
                                program = program,
                                now = now,
                                isFirst = index == 0,
                                isLast = index == programs.lastIndex,
                                onClick = { onOpenProgram(program.id) },
                            )
                        }
                    }
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
    LaunchedEffect(days, selectedDayId) {
        val index = days.indexOfFirst { it.id == selectedDayId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
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

private enum class ProgramTimelineState { Past, Now, Upcoming }

@Composable
fun ProgramTimelineRow(
    program: Program,
    now: java.util.Date,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val accent = program.resolvedAccentColor
    val state = when {
        now.before(program.startDate) -> ProgramTimelineState.Upcoming
        now.after(program.endDate) -> ProgramTimelineState.Past
        else -> ProgramTimelineState.Now
    }
    val isNow = state == ProgramTimelineState.Now
    val rowHeight = if (isNow) 126.dp else 58.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickable(
                onClick = onClick,
                onClickLabel = "${program.title}, ${program.startDate.programTimeText()}, ${program.location}",
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = program.startDate.programTimeText(),
            style = CzTypeScale.subhead.copy(fontWeight = if (isNow) FontWeight.Bold else FontWeight.SemiBold),
            color = if (state == ProgramTimelineState.Past) colors.textTertiary else colors.textPrimary,
            modifier = Modifier.width(50.dp).padding(top = if (isNow) 16.dp else 8.dp),
        )
        Box(
            modifier = Modifier.width(24.dp).height(rowHeight),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isFirst) Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(if (isNow) 21.dp else 15.dp)
                    .background(colors.textSecondary.copy(alpha = 0.25f)),
            )
            if (!isLast) Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(rowHeight - if (isNow) 21.dp else 15.dp)
                    .padding(top = if (isNow) 21.dp else 15.dp)
                    .background(colors.textSecondary.copy(alpha = 0.25f))
                    .align(Alignment.BottomCenter),
            )
            Box(
                modifier = Modifier
                    .padding(top = if (isNow) 14.dp else 9.dp)
                    .size(if (isNow) 16.dp else 12.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            ProgramTimelineState.Past -> accent.copy(alpha = 0.5f)
                            ProgramTimelineState.Now -> accent
                            ProgramTimelineState.Upcoming -> colors.background
                        },
                    )
                    .then(if (state == ProgramTimelineState.Upcoming) Modifier.border(2.dp, accent, CircleShape) else Modifier),
            )
        }
        if (isNow) {
            val duration = (program.endDate.time - program.startDate.time).coerceAtLeast(1L)
            val fraction = ((now.time - program.startDate.time).toFloat() / duration).coerceIn(0f, 1f)
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(CzRadius.xl),
                modifier = Modifier.weight(1f).padding(start = CzSpacing.md, bottom = CzSpacing.md),
            ) {
                Column(Modifier.fillMaxWidth().padding(CzSpacing.md), verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(accent))
                        Text(stringResource(R.string.poll_live).uppercase(), style = CzTypeScale.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp), color = accent)
                    }
                    Text(
                        text = program.title,
                        style = CzTypeScale.subhead.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                    Text(program.location, style = CzTypeScale.caption, color = colors.textTertiary, maxLines = 1)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(CzRadius.full)),
                        color = accent,
                        trackColor = colors.textTertiary.copy(alpha = 0.25f),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).padding(start = CzSpacing.md, top = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(program.title, style = CzTypeScale.subhead, color = colors.textPrimary)
                if (program.location.isNotBlank()) Text(program.location, style = CzTypeScale.caption, color = colors.textTertiary)
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
            campingTitle = "Summer Camp",
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

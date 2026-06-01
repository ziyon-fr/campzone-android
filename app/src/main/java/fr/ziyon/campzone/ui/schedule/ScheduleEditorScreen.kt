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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ScheduleReminderTiming

@Composable
fun ScheduleEditorScreen(
    viewModel: ScheduleViewModel,
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenProgramEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDayId by viewModel.selectedDayId.collectAsState()
    val reminderTiming by viewModel.reminderTiming.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    var deletingProgram by remember { mutableStateOf<Program?>(null) }

    LaunchedEffect(campingId) { viewModel.normalizeSchedule(campingId, authenticatedUser) }

    if (deletingProgram != null) {
        AlertDialog(
            onDismissRequest = { deletingProgram = null },
            title = { Text(stringResource(R.string.schedule_delete_program_title)) },
            text = { Text(stringResource(R.string.schedule_delete_program_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = deletingProgram ?: return@TextButton
                        deletingProgram = null
                        viewModel.deleteProgram(p.id, campingId)
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.czColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProgram = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    ScheduleEditorContent(
        uiState = uiState,
        selectedDayId = selectedDayId,
        reminderTiming = reminderTiming,
        isSaving = isSaving,
        operationError = operationError,
        operationMessage = operationMessage,
        onBack = onBack,
        onDaySelected = viewModel::setSelectedDayId,
        onReminderTimingChanged = viewModel::setReminderTiming,
        onSaveReminderTiming = { viewModel.saveReminderTiming(campingId) },
        onEditProgram = { program ->
            viewModel.prepareEditingProgram(program)
            onOpenProgramEditor()
        },
        onDeleteProgram = { program -> deletingProgram = program },
        onAddProgram = {
            viewModel.prepareNewProgram(campingId, selectedDayId)
            onOpenProgramEditor()
        },
        onClearError = viewModel::clearOperationError,
        onClearMessage = viewModel::clearOperationMessage,
        onRetry = { viewModel.load(campingId, authenticatedUser) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorContent(
    uiState: ScheduleUiState,
    selectedDayId: String?,
    reminderTiming: ScheduleReminderTiming,
    isSaving: Boolean,
    operationError: String?,
    operationMessage: String?,
    onBack: () -> Unit,
    onDaySelected: (String?) -> Unit,
    onReminderTimingChanged: (ScheduleReminderTiming) -> Unit,
    onSaveReminderTiming: () -> Unit,
    onEditProgram: (Program) -> Unit,
    onDeleteProgram: (Program) -> Unit,
    onAddProgram: () -> Unit,
    onClearError: () -> Unit,
    onClearMessage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.schedule_edit_title),
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

                is ScheduleUiState.Empty -> EmptyEditorBody(
                    onAddProgram = onAddProgram,
                    modifier = Modifier.fillMaxSize(),
                )

                is ScheduleUiState.Error -> CzErrorState(
                    title = stringResource(R.string.schedule_error_title),
                    message = uiState.message,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                is ScheduleUiState.Loaded -> EditorLoadedBody(
                    schedule = uiState.schedule,
                    selectedDayId = selectedDayId,
                    reminderTiming = reminderTiming,
                    isSaving = isSaving,
                    operationError = operationError,
                    onDaySelected = onDaySelected,
                    onReminderTimingChanged = onReminderTimingChanged,
                    onSaveReminderTiming = onSaveReminderTiming,
                    onEditProgram = onEditProgram,
                    onDeleteProgram = onDeleteProgram,
                    onAddProgram = onAddProgram,
                    onClearError = onClearError,
                )
            }
        }
    }
}

@Composable
private fun EmptyEditorBody(
    onAddProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(CzSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CzEmptyState(
            title = stringResource(R.string.schedule_no_days_title),
            message = stringResource(R.string.schedule_no_days_message),
        )
        Spacer(modifier = Modifier.height(CzSpacing.lg))
        CzButton(
            text = stringResource(R.string.schedule_add_program),
            onClick = onAddProgram,
            variant = CzButtonVariant.Primary,
        )
    }
}

@Composable
private fun EditorLoadedBody(
    schedule: CampingSchedule,
    selectedDayId: String?,
    reminderTiming: ScheduleReminderTiming,
    isSaving: Boolean,
    operationError: String?,
    onDaySelected: (String?) -> Unit,
    onReminderTimingChanged: (ScheduleReminderTiming) -> Unit,
    onSaveReminderTiming: () -> Unit,
    onEditProgram: (Program) -> Unit,
    onDeleteProgram: (Program) -> Unit,
    onAddProgram: () -> Unit,
    onClearError: () -> Unit,
) {
    val selectedDay = selectedDayId
        ?.let { id -> schedule.sortedDays.firstOrNull { it.id == id } }
        ?: schedule.sortedDays.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.czColors.background,
        bottomBar = {
            AddProgramBar(
                selectedDay = selectedDay,
                onAddProgram = onAddProgram,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = CzSpacing.base,
                end = CzSpacing.base,
                top = CzSpacing.base,
                bottom = innerPadding.calculateBottomPadding() + CzSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            item { ScheduleOverviewCard(schedule = schedule) }
            item {
                EditorDayPickerSection(
                    schedule = schedule,
                    selectedDayId = selectedDay?.id,
                    onDaySelected = onDaySelected,
                )
            }
            item {
                EditorProgramsSection(
                    selectedDay = selectedDay,
                    operationError = operationError,
                    onEditProgram = onEditProgram,
                    onDeleteProgram = onDeleteProgram,
                    onClearError = onClearError,
                )
            }
            item {
                ReminderSection(
                    reminderTiming = reminderTiming,
                    isSaving = isSaving,
                    onTimingChanged = onReminderTimingChanged,
                    onSave = onSaveReminderTiming,
                )
            }
        }
    }
}

@Composable
private fun ScheduleOverviewCard(schedule: CampingSchedule) {
    val colors = MaterialTheme.czColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.base),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = colors.ember,
                modifier = Modifier.size(CzSpacing.xl),
            )
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                val dayCount = schedule.sortedDays.count { it.programs.isNotEmpty() }
                val programCount = schedule.allPrograms.count()
                Text(
                    text = stringResource(R.string.schedule_overview_summary, dayCount, programCount),
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EditorDayPickerSection(
    schedule: CampingSchedule,
    selectedDayId: String?,
    onDaySelected: (String?) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        EditorSectionLabel(text = stringResource(R.string.schedule_days), icon = Icons.Rounded.CalendarMonth)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            contentPadding = PaddingValues(horizontal = CzSpacing.xs),
        ) {
            items(schedule.sortedDays, key = { it.id }) { day ->
                val isSelected = day.id == selectedDayId
                EditorDayChip(
                    day = day,
                    isSelected = isSelected,
                    programCount = day.programs.size,
                    onClick = { onDaySelected(day.id) },
                )
            }
        }
    }
}

@Composable
private fun EditorDayChip(
    day: CampDay,
    isSelected: Boolean,
    programCount: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val bgColor = if (isSelected) colors.ember else colors.surface
    val titleColor = if (isSelected) Color.White else colors.textPrimary
    val dateColor = if (isSelected) Color.White.copy(alpha = 0.86f) else colors.textSecondary
    val countColor = if (isSelected) Color.White else colors.ember
    val chipDescription = stringResource(
        R.string.schedule_day_program_count_cd,
        day.title,
        day.dateTitle(),
        programCount,
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(CzRadius.md))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm)
            .semantics {
                contentDescription = chipDescription
            },
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = day.title,
            style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = titleColor,
        )
        Text(
            text = day.dateTitle(),
            style = CzTypeScale.caption,
            color = dateColor,
        )
        Text(
            text = if (programCount == 1) "1 program" else "$programCount programs",
            style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
            color = countColor,
        )
    }
}

@Composable
private fun EditorProgramsSection(
    selectedDay: CampDay?,
    operationError: String?,
    onEditProgram: (Program) -> Unit,
    onDeleteProgram: (Program) -> Unit,
    onClearError: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        EditorSectionLabel(text = stringResource(R.string.schedule_programs), icon = Icons.Rounded.CalendarMonth)

        if (selectedDay == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = RoundedCornerShape(CzRadius.lg),
            ) {
                CzEmptyState(
                    title = stringResource(R.string.schedule_no_days_title),
                    message = stringResource(R.string.schedule_no_days_message),
                    modifier = Modifier.padding(CzSpacing.base),
                )
            }
            return@Column
        }

        SelectedDayHeader(day = selectedDay)

        if (operationError != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.error.copy(alpha = 0.08f),
                shape = RoundedCornerShape(CzRadius.md),
            ) {
                Row(
                    modifier = Modifier.padding(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = operationError,
                        style = CzTypeScale.caption,
                        color = colors.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearError) {
                        Text(stringResource(R.string.common_dismiss), style = CzTypeScale.caption, color = colors.error)
                    }
                }
            }
        }

        if (selectedDay.programs.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = RoundedCornerShape(CzRadius.lg),
            ) {
                Column(
                    modifier = Modifier.padding(CzSpacing.base),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.schedule_no_programs_date),
                        style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.schedule_no_programs_hint, selectedDay.dateTitle()),
                        style = CzTypeScale.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = RoundedCornerShape(CzRadius.lg),
            ) {
                Column {
                    selectedDay.programs.sortedBy { it.startDate }.forEachIndexed { index, program ->
                        ProgramEditorRow(
                            program = program,
                            onEdit = { onEditProgram(program) },
                            onDelete = { onDeleteProgram(program) },
                        )
                        if (index < selectedDay.programs.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = CzSpacing.xxxl),
                                color = MaterialTheme.czColors.divider,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDayHeader(day: CampDay) {
    val colors = MaterialTheme.czColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(CzRadius.md),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                Text(
                    text = day.title,
                    style = CzTypeScale.headline,
                    color = colors.textPrimary,
                )
                Text(
                    text = day.dateTitle(),
                    style = CzTypeScale.subhead,
                    color = colors.textSecondary,
                )
            }
            Surface(
                color = colors.ember.copy(alpha = 0.12f),
                shape = RoundedCornerShape(CzRadius.full),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.schedule_program_count,
                        day.programs.size,
                        day.programs.size,
                    ),
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ember,
                    modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun ProgramEditorRow(
    program: Program,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val accent = program.type.accentColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CzSpacing.sm, horizontal = CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(
            text = program.startDate.programTimeText(),
            style = CzTypeScale.caption,
            color = colors.textSecondary,
            modifier = Modifier.width(CzSpacing.xxxl),
        )

        Box(
            modifier = Modifier
                .width(CzSpacing.xs)
                .height(36.dp)
                .background(accent, RoundedCornerShape(CzRadius.full)),
        )

        Icon(
            imageVector = program.type.icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(CzSpacing.base + 4.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = program.title,
                style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = program.location,
                style = CzTypeScale.caption,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            Box(
                modifier = Modifier
                    .size(CzSpacing.xxl + CzSpacing.md)
                    .clip(CircleShape)
                    .background(colors.ember.copy(alpha = 0.1f))
                    .clickable(
                        onClick = onEdit,
                        onClickLabel = "Edit ${program.title}",
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = colors.ember,
                    modifier = Modifier.size(14.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(CzSpacing.xxl + CzSpacing.md)
                    .clip(CircleShape)
                    .background(colors.error.copy(alpha = 0.1f))
                    .clickable(
                        onClick = onDelete,
                        onClickLabel = "Delete ${program.title}",
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ReminderSection(
    reminderTiming: ScheduleReminderTiming,
    isSaving: Boolean,
    onTimingChanged: (ScheduleReminderTiming) -> Unit,
    onSave: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var showMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        EditorSectionLabel(text = stringResource(R.string.schedule_reminder_timing), icon = Icons.Rounded.Notifications)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(CzRadius.lg),
        ) {
            Column(modifier = Modifier.padding(CzSpacing.md)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = colors.amber,
                        modifier = Modifier.size(CzSpacing.xl),
                    )
                    Box {
                        TextButton(onClick = { showMenu = true }) {
                            Text(
                                text = stringResource(reminderTiming.displayNameRes),
                                style = CzTypeScale.body,
                                color = colors.ember,
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            ScheduleReminderTiming.entries.forEach { timing ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(timing.displayNameRes),
                                            style = CzTypeScale.body,
                                            color = if (timing == reminderTiming) colors.ember else colors.textPrimary,
                                        )
                                    },
                                    onClick = {
                                        onTimingChanged(timing)
                                        showMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.divider)

                Spacer(modifier = Modifier.height(CzSpacing.md))

                CzButton(
                    text = if (isSaving) "Saving…" else "Save Reminder",
                    onClick = onSave,
                    enabled = !isSaving,
                    loading = isSaving,
                    variant = CzButtonVariant.Ghost,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun AddProgramBar(
    selectedDay: CampDay?,
    onAddProgram: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.background,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                start = CzSpacing.base,
                end = CzSpacing.base,
                top = CzSpacing.md,
                bottom = CzSpacing.base,
            ),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            if (selectedDay != null) {
                Text(
                    text = stringResource(R.string.schedule_selected_date, selectedDay.dateTitle()),
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                )
            }
            CzButton(
                text = stringResource(R.string.schedule_add_program),
                onClick = onAddProgram,
                enabled = selectedDay != null,
                variant = CzButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditorSectionLabel(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val colors = MaterialTheme.czColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.ember,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = CzTypeScale.callout,
            color = colors.textSecondary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleEditorLoadingPreview() {
    CampzoneTheme {
        ScheduleEditorContent(
            uiState = ScheduleUiState.Loading,
            selectedDayId = null,
            reminderTiming = ScheduleReminderTiming.None,
            isSaving = false,
            operationError = null,
            operationMessage = null,
            onBack = {},
            onDaySelected = {},
            onReminderTimingChanged = {},
            onSaveReminderTiming = {},
            onEditProgram = {},
            onDeleteProgram = {},
            onAddProgram = {},
            onClearError = {},
            onClearMessage = {},
            onRetry = {},
        )
    }
}

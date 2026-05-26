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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.ProgramType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ProgramEditorScreen(
    viewModel: ScheduleViewModel,
    campingId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.editorForm.collectAsState()
    val editingProgramId by viewModel.editingProgramId.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val isEditing = editingProgramId != null

    val schedule = viewModel.schedule(campingId)
    val scheduleDays = schedule?.sortedDays ?: emptyList()
    val selectedDay = scheduleDays.firstOrNull { it.id == form.dayId } ?: scheduleDays.firstOrNull()

    ProgramEditorContent(
        form = form,
        isEditing = isEditing,
        scheduleDays = scheduleDays,
        selectedDay = selectedDay,
        validationErrors = validationErrors,
        isSaving = isSaving,
        operationError = operationError,
        onTitleChanged = { viewModel.updateEditorForm { f -> f.copy(title = it) } },
        onLocationChanged = { viewModel.updateEditorForm { f -> f.copy(location = it) } },
        onDescriptionChanged = { viewModel.updateEditorForm { f -> f.copy(description = it) } },
        onTypeSelected = { viewModel.updateEditorForm { f -> f.copy(type = it) } },
        onDaySelected = { day ->
            viewModel.setSelectedDayId(day.id)
            viewModel.updateEditorForm { f -> f.copy(dayId = day.id) }
        },
        onStartTimeChanged = { date ->
            viewModel.updateEditorForm { f -> f.copy(startDate = date) }
        },
        onEndTimeChanged = { date ->
            viewModel.updateEditorForm { f -> f.copy(endDate = date) }
        },
        onSave = {
            viewModel.saveProgram(campingId) { onSaved() }
        },
        onBack = onBack,
        onClearError = viewModel::clearOperationError,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEditorContent(
    form: ProgramForm,
    isEditing: Boolean,
    scheduleDays: List<CampDay>,
    selectedDay: CampDay?,
    validationErrors: List<ProgramValidationError>,
    isSaving: Boolean,
    operationError: String?,
    onTitleChanged: (String) -> Unit,
    onLocationChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onTypeSelected: (ProgramType) -> Unit,
    onDaySelected: (CampDay) -> Unit,
    onStartTimeChanged: (Date) -> Unit,
    onEndTimeChanged: (Date) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Program" else "New Program",
                        style = CzTypeScale.headline,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Cancel",
                            tint = colors.textPrimary,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !isSaving) {
                        Text(
                            text = if (isSaving) "Saving…" else "Save",
                            style = CzTypeScale.headline,
                            color = if (isSaving) colors.textSecondary else colors.ember,
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
        LazyColumn(
            contentPadding = PaddingValues(
                start = CzSpacing.base,
                end = CzSpacing.base,
                top = innerPadding.calculateTopPadding(),
                bottom = CzSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            if (validationErrors.isNotEmpty()) {
                item { ValidationBanner(errors = validationErrors) }
            }
            if (operationError != null) {
                item {
                    OperationErrorBanner(error = operationError, onDismiss = onClearError)
                }
            }
            item {
                BasicInfoSection(
                    title = form.title,
                    location = form.location,
                    onTitleChanged = onTitleChanged,
                    onLocationChanged = onLocationChanged,
                )
            }
            item {
                DescriptionSection(
                    description = form.description,
                    onDescriptionChanged = onDescriptionChanged,
                )
            }
            if (scheduleDays.isNotEmpty()) {
                item {
                    DateContextSection(
                        selectedDay = selectedDay,
                        scheduleDays = scheduleDays,
                        onDaySelected = onDaySelected,
                    )
                }
            }
            item {
                TimeSection(
                    startDate = form.startDate,
                    endDate = form.endDate,
                    selectedDay = selectedDay,
                    onStartTimeChanged = onStartTimeChanged,
                    onEndTimeChanged = onEndTimeChanged,
                )
            }
            item {
                TypeSelectorSection(
                    selectedType = form.type,
                    onTypeSelected = onTypeSelected,
                )
            }
        }
    }
}

@Composable
private fun ValidationBanner(errors: List<ProgramValidationError>) {
    val colors = MaterialTheme.czColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.error.copy(alpha = 0.08f),
        shape = RoundedCornerShape(CzRadius.md),
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Please fix the following:",
                    style = CzTypeScale.subhead,
                    color = colors.error,
                )
            }
            errors.forEach { error ->
                Text(
                    text = "• ${error.message}",
                    style = CzTypeScale.caption,
                    color = colors.error,
                )
            }
        }
    }
}

@Composable
private fun OperationErrorBanner(error: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.czColors
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
            Text(
                text = error,
                style = CzTypeScale.caption,
                color = colors.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss", style = CzTypeScale.caption, color = colors.error)
            }
        }
    }
}

@Composable
private fun FormSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = MaterialTheme.czColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(14.dp))
        Text(text = title, style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)
    }
}

@Composable
private fun BasicInfoSection(
    title: String,
    location: String,
    onTitleChanged: (String) -> Unit,
    onLocationChanged: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        FormSectionHeader("Details", icon = Icons.Rounded.TextFields)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(CzRadius.lg),
        ) {
            Column(
                modifier = Modifier.padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                CzTextField(
                    value = title,
                    onValueChange = onTitleChanged,
                    label = "Title",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.TextFields,
                            contentDescription = null,
                            tint = colors.ember,
                        )
                    },
                )
                CzTextField(
                    value = location,
                    onValueChange = onLocationChanged,
                    label = "Location",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = colors.ember,
                        )
                    },
                )
                Text(
                    text = "Pick a spot from the venue map or type a custom location.",
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun DescriptionSection(
    description: String,
    onDescriptionChanged: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        FormSectionHeader("Description", icon = Icons.Rounded.TextFields)
        CzTextField(
            value = description,
            onValueChange = onDescriptionChanged,
            label = "Add a description…",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )
        Text(
            text = "Optional. Visible to all registered participants.",
            style = CzTypeScale.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun DateContextSection(
    selectedDay: CampDay?,
    scheduleDays: List<CampDay>,
    onDaySelected: (CampDay) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        FormSectionHeader("Program Date", icon = Icons.Rounded.CalendarMonth)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(CzRadius.lg),
        ) {
            Column(
                modifier = Modifier.padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                if (selectedDay != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = colors.ember,
                            modifier = Modifier.size(CzSpacing.xl),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                            Text(
                                text = selectedDay.title,
                                style = CzTypeScale.headline,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = selectedDay.dateTitle(),
                                style = CzTypeScale.subhead,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
                if (scheduleDays.size > 1) {
                    HorizontalDivider(color = colors.divider)
                    Text(
                        text = "Change day:",
                        style = CzTypeScale.caption,
                        color = colors.textSecondary,
                    )
                    scheduleDays.forEach { day ->
                        val isSelected = day.id == selectedDay?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CzRadius.sm))
                                .background(if (isSelected) colors.ember.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable { onDaySelected(day) }
                                .padding(CzSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${day.title} · ${day.dateTitle()}",
                                style = CzTypeScale.body,
                                color = if (isSelected) colors.ember else colors.textPrimary,
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.ember,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val timeDisplayFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSection(
    startDate: Date,
    endDate: Date,
    selectedDay: CampDay?,
    onStartTimeChanged: (Date) -> Unit,
    onEndTimeChanged: (Date) -> Unit,
) {
    val colors = MaterialTheme.czColors
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val startCal = remember(startDate) { Calendar.getInstance().apply { time = startDate } }
    val endCal = remember(endDate) { Calendar.getInstance().apply { time = endDate } }

    if (showStartPicker) {
        TimePickerDialog(
            initialHour = startCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = startCal.get(Calendar.MINUTE),
            onDismiss = { showStartPicker = false },
            onConfirm = { hour, minute ->
                showStartPicker = false
                val newDate = Calendar.getInstance().apply {
                    time = startDate
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                onStartTimeChanged(newDate)
            },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialHour = endCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = endCal.get(Calendar.MINUTE),
            onDismiss = { showEndPicker = false },
            onConfirm = { hour, minute ->
                showEndPicker = false
                val newDate = Calendar.getInstance().apply {
                    time = endDate
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                onEndTimeChanged(newDate)
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        FormSectionHeader("Time", icon = Icons.Rounded.CalendarMonth)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(CzRadius.lg),
        ) {
            Column(modifier = Modifier.padding(CzSpacing.md)) {
                TimeRow(
                    label = "Start",
                    time = timeDisplayFormatter.format(startDate),
                    onClick = { showStartPicker = true },
                )
                HorizontalDivider(color = colors.divider)
                TimeRow(
                    label = "End",
                    time = timeDisplayFormatter.format(endDate),
                    onClick = { showEndPicker = true },
                )
                if (selectedDay != null) {
                    Spacer(modifier = Modifier.height(CzSpacing.sm))
                    Text(
                        text = "Start and end times are scheduled on ${selectedDay.dateTitle()}.",
                        style = CzTypeScale.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, time: String, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = CzTypeScale.body, color = colors.textPrimary)
        Text(
            text = time,
            style = CzTypeScale.body.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ember,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    val colors = MaterialTheme.czColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(CzRadius.xl),
            color = colors.background,
        ) {
            Column(
                modifier = Modifier.padding(CzSpacing.base),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CzSpacing.base),
            ) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = colors.surface,
                        selectorColor = colors.ember,
                        containerColor = colors.background,
                        periodSelectorBorderColor = colors.divider,
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = colors.textPrimary,
                        timeSelectorSelectedContainerColor = colors.ember.copy(alpha = 0.12f),
                        timeSelectorUnselectedContainerColor = colors.surface,
                        timeSelectorSelectedContentColor = colors.ember,
                        timeSelectorUnselectedContentColor = colors.textPrimary,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.size(CzSpacing.sm))
                    CzButton(
                        text = "OK",
                        onClick = { onConfirm(state.hour, state.minute) },
                        variant = CzButtonVariant.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeSelectorSection(
    selectedType: ProgramType,
    onTypeSelected: (ProgramType) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        FormSectionHeader("Program Type", icon = Icons.Rounded.CheckCircle)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            modifier = Modifier.height(((ProgramType.entries.size / 3 + 1) * 80 + 8).dp),
            userScrollEnabled = false,
        ) {
            items(ProgramType.entries) { type ->
                ProgramTypeChip(
                    type = type,
                    isSelected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                )
            }
        }
    }
}

@Composable
private fun ProgramTypeChip(
    type: ProgramType,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val accent = type.accentColor
    val bgColor = if (isSelected) accent else colors.background
    val iconColor = if (isSelected) Color.White else accent
    val textColor = if (isSelected) Color.White else colors.textPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .background(bgColor)
            .then(
                if (!isSelected) Modifier.border(1.dp, colors.divider, RoundedCornerShape(CzRadius.md))
                else Modifier
            )
            .clickable(
                onClick = onClick,
                onClickLabel = type.displayName,
            )
            .padding(vertical = CzSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = type.displayName,
                style = CzTypeScale.caption,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgramTypeChipPreview() {
    CampzoneTheme {
        Column(
            modifier = Modifier.padding(CzSpacing.base),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            ProgramTypeChip(type = ProgramType.Preaching, isSelected = true, onClick = {})
            ProgramTypeChip(type = ProgramType.Games, isSelected = false, onClick = {})
        }
    }
}

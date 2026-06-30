package fr.ziyon.campzone.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.FoodMenuEntry
import fr.ziyon.campzone.data.model.FoodMenuProgramSync
import fr.ziyon.campzone.data.model.Game
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.schedule.AndroidCalendarExportLauncher
import fr.ziyon.campzone.data.schedule.CalendarExportLabels
import fr.ziyon.campzone.data.schedule.ScheduleCalendarExportPlanner
import fr.ziyon.campzone.ui.schedule.food.FoodMenuUiState
import fr.ziyon.campzone.ui.schedule.food.FoodMenuViewModel
import fr.ziyon.campzone.ui.schedule.food.MealMenuCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun ProgramDetailScreen(
    viewModel: ScheduleViewModel,
    campingId: String,
    programId: String,
    canManageAttendance: Boolean,
    onBack: () -> Unit,
    onOpenFoodMenu: () -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenAttendance: () -> Unit,
    modifier: Modifier = Modifier,
    foodMenuViewModel: FoodMenuViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId) {
        viewModel.loadIfNeeded(campingId)
        foodMenuViewModel.loadIfNeeded(campingId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val games by viewModel.games.collectAsState()
    val foodMenuState by foodMenuViewModel.uiState.collectAsState()
    val program = (uiState as? ScheduleUiState.Loaded)
        ?.schedule
        ?.allPrograms
        ?.firstOrNull { it.id == programId }
        ?: viewModel.program(programId)
    val foodMenuEntry = if (foodMenuState is FoodMenuUiState.Loaded && program != null) {
        foodMenuViewModel.entryFor(program)
    } else {
        null
    }
    val linkedGame = program?.linkedGameId?.let { id -> games.firstOrNull { it.id == id } }
    ProgramDetailContent(
        program = program,
        campingTitle = camping?.title ?: stringResource(R.string.calendar_default_camping_title),
        foodMenuEntry = foodMenuEntry,
        linkedGame = linkedGame,
        canManageAttendance = canManageAttendance,
        onBack = onBack,
        onOpenFoodMenu = onOpenFoodMenu,
        onOpenGame = onOpenGame,
        onOpenAttendance = onOpenAttendance,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramDetailContent(
    program: Program?,
    campingTitle: String,
    foodMenuEntry: FoodMenuEntry?,
    linkedGame: Game?,
    canManageAttendance: Boolean,
    onBack: () -> Unit,
    onOpenFoodMenu: () -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenAttendance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val addToCalendarDescription = stringResource(R.string.program_add_to_calendar_cd)
    val calendarNoAppMessage = stringResource(R.string.schedule_calendar_no_app)
    val calendarLabels = CalendarExportLabels(
        camping = stringResource(R.string.calendar_notes_camping_label),
        type = stringResource(R.string.calendar_notes_type_label),
    )
    val programTypeName = program?.let { stringResource(it.type.displayNameRes) }.orEmpty()

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.program_navigation_title),
                        style = CzTypeScale.headline,
                        color = colors.textPrimary,
                        maxLines = 1,
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
                    if (program != null) {
                        IconButton(
                            onClick = {
                                val draft = ScheduleCalendarExportPlanner.draft(
                                    program = program,
                                    campingTitle = campingTitle,
                                    typeName = program.customType?.trimmedName
                                        ?: programTypeName,
                                    labels = calendarLabels,
                                )
                                if (!AndroidCalendarExportLauncher.openProgram(context, draft)) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(calendarNoAppMessage)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EventAvailable,
                                contentDescription = addToCalendarDescription,
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
        if (program == null) {
            CzEmptyState(
                title = stringResource(R.string.program_not_found_title),
                message = stringResource(R.string.program_not_found_message),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CzSpacing.xl),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = CzSpacing.base,
                    end = CzSpacing.base,
                    top = innerPadding.calculateTopPadding() + CzSpacing.md,
                    bottom = CzSpacing.base,
                ),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
            ) {
                item { ProgramHeader(program = program) }
                item { DetailsSection(program = program) }
                if (program.description.isNotBlank()) {
                    item { AboutSection(description = program.description) }
                }
                if (FoodMenuProgramSync.mealKind(program.type) != null) {
                    item {
                        FoodSection(
                            entry = foodMenuEntry,
                            onOpenFoodMenu = onOpenFoodMenu,
                        )
                    }
                }
                if (linkedGame != null) {
                    item {
                        ProgramGameSection(
                            game = linkedGame,
                            onOpenGame = { onOpenGame(linkedGame.id) },
                        )
                    }
                }
                if (canManageAttendance) {
                    item {
                        ProgramAttendanceSection(onOpenAttendance = onOpenAttendance)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramHeader(program: Program) {
    val accent = program.resolvedAccentColor
    val typeName = program.customType?.trimmedName ?: stringResource(program.type.displayNameRes)
    Column(
        modifier = Modifier.padding(top = CzSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(accent, accent.copy(alpha = 0.6f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = program.resolvedIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(CzRadius.full),
            ) {
                Text(
                    text = typeName,
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                    modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        Text(
            text = program.title,
            style = CzTypeScale.title2,
            color = MaterialTheme.czColors.textPrimary,
        )
    }
}

@Composable
private fun DetailsSection(program: Program) {
    val colors = MaterialTheme.czColors
    val accent = program.resolvedAccentColor
    val isDark = isSystemInDarkTheme()
    val isMeal = FoodMenuProgramSync.mealKind(program.type) != null
    val isRest = program.type == ProgramType.Rest || program.type == ProgramType.Break
    val hasLocation = program.location.isNotBlank()
    val rowSurface = if (isDark) colors.surface else Color.White
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        ProgramSectionHeader(title = stringResource(R.string.program_details), icon = Icons.Rounded.Schedule)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = rowSurface,
            shape = RoundedCornerShape(CzRadius.xl),
        ) {
            Column(modifier = Modifier.padding(vertical = CzSpacing.sm)) {
                if (isRest) {
                    ProgramInfoRow(
                        label = stringResource(R.string.program_duration),
                        value = durationText(program.startDate, program.endDate),
                        icon = Icons.Rounded.Timer,
                        color = accent,
                    )
                    ProgramRowDivider()
                    ProgramInfoRow(
                        label = stringResource(R.string.program_start),
                        value = fullDateTimeText(program.startDate),
                        icon = Icons.Rounded.Schedule,
                        color = accent,
                    )
                    ProgramRowDivider()
                    ProgramInfoRow(
                        label = stringResource(R.string.program_end),
                        value = fullDateTimeText(program.endDate),
                        icon = Icons.Rounded.Schedule,
                        color = accent,
                    )
                } else {
                    ProgramInfoRow(
                        label = stringResource(R.string.program_start),
                        value = fullDateTimeText(program.startDate),
                        icon = Icons.Rounded.Schedule,
                        color = accent,
                    )
                    ProgramRowDivider()
                    ProgramInfoRow(
                        label = stringResource(R.string.program_end),
                        value = fullDateTimeText(program.endDate),
                        icon = Icons.Rounded.Schedule,
                        color = accent,
                    )
                    ProgramRowDivider()
                    ProgramInfoRow(
                        label = stringResource(R.string.program_duration),
                        value = durationText(program.startDate, program.endDate),
                        icon = Icons.Rounded.Timer,
                        color = accent,
                    )
                }
                if (hasLocation) {
                    ProgramRowDivider()
                    ProgramInfoRow(
                        label = stringResource(if (isMeal) R.string.program_venue_label else R.string.program_location_label),
                        value = program.location,
                        icon = if (isMeal) Icons.Rounded.Restaurant else Icons.Rounded.LocationOn,
                        color = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.czColors.divider,
        modifier = Modifier.padding(start = CzSpacing.base + 34.dp + CzSpacing.md, end = CzSpacing.base),
    )
}

@Composable
private fun ProgramSectionHeader(title: String, icon: ImageVector) {
    val colors = MaterialTheme.czColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(14.dp))
        Text(text = title, style = CzTypeScale.callout, color = colors.textSecondary)
    }
}

@Composable
private fun ProgramInfoRow(label: String, value: String, icon: ImageVector, color: Color) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.base, vertical = CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(CzRadius.sm))
                .background(color.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(text = label, style = CzTypeScale.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(text = value, style = CzTypeScale.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
    }
}

@Composable
private fun AboutSection(description: String) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        ProgramSectionHeader(title = stringResource(R.string.program_about), icon = Icons.Rounded.Schedule)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isSystemInDarkTheme()) colors.surface else Color.White,
            shape = RoundedCornerShape(CzRadius.xl),
        ) {
            Text(
                text = description,
                style = CzTypeScale.body,
                color = colors.textSecondary,
                lineHeight = CzTypeScale.body.lineHeight,
                modifier = Modifier.padding(CzSpacing.base),
            )
        }
    }
}

@Composable
private fun FoodSection(
    entry: FoodMenuEntry?,
    onOpenFoodMenu: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        ProgramSectionHeader(title = stringResource(R.string.program_todays_menu), icon = Icons.Rounded.Restaurant)
        if (entry != null) {
            MealMenuCard(
                entry = entry,
                canManage = false,
                onEdit = {},
                onDelete = {},
                onSeeAll = onOpenFoodMenu,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shape = RoundedCornerShape(CzRadius.xl),
            ) {
                Row(
                    modifier = Modifier.padding(CzSpacing.base),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.program_menu_not_published),
                        style = CzTypeScale.caption,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenFoodMenu) {
                        Text(stringResource(R.string.program_see_all), color = colors.ember)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramGameSection(
    game: Game,
    onOpenGame: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        ProgramSectionHeader(title = stringResource(R.string.program_game), icon = Icons.Rounded.CheckCircle)
        Surface(
            onClick = onOpenGame,
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(CzRadius.xl),
        ) {
            Row(
                modifier = Modifier.padding(CzSpacing.base),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.ember,
                    modifier = Modifier.size(32.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.name,
                        style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.program_game_helper),
                        style = CzTypeScale.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramAttendanceSection(onOpenAttendance: () -> Unit) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        ProgramSectionHeader(
            title = stringResource(R.string.program_attendance_section),
            icon = Icons.Rounded.CheckCircle,
        )
        Surface(
            onClick = onOpenAttendance,
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = RoundedCornerShape(CzRadius.xl),
        ) {
            Row(
                modifier = Modifier.padding(CzSpacing.base),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.ember,
                    modifier = Modifier.size(32.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.program_attendance_title),
                        style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.program_attendance_entry_subtitle),
                        style = CzTypeScale.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

private val fullDateTimeFormatter = SimpleDateFormat("EEEE, MMMM d · HH:mm", Locale.getDefault())

private fun fullDateTimeText(date: Date): String = fullDateTimeFormatter.format(date)

private fun durationText(start: Date, end: Date): String {
    val minutes = abs(end.time - start.time).toInt() / 60_000
    if (minutes <= 0) return "-"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgramDetailNotFoundPreview() {
    CampzoneTheme {
        ProgramDetailContent(
            program = null,
            campingTitle = "Summer Camp",
            foodMenuEntry = null,
            linkedGame = null,
            canManageAttendance = false,
            onBack = {},
            onOpenFoodMenu = {},
            onOpenGame = {},
            onOpenAttendance = {},
        )
    }
}

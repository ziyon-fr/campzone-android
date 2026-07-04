package fr.ziyon.campzone.ui.schedule.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.FoodMealKind
import fr.ziyon.campzone.data.model.FoodMenuEntry
import fr.ziyon.campzone.data.model.FoodMenuItem
import fr.ziyon.campzone.data.schedule.FakeFoodMenuService
import fr.ziyon.campzone.data.profile.CommonAllergy
import fr.ziyon.campzone.ui.common.AllergyChips
import fr.ziyon.campzone.ui.common.allergyDisplayName
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun FoodMenuRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenEditor: (entryId: String?) -> Unit,
    onOpenAttendee: (String) -> Unit = {},
    viewModel: FoodMenuViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val canManage by viewModel.canManageFoodMenu.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val participantAllergies by viewModel.participantAllergies.collectAsState()
    val foodAllergies = authenticatedUser.allergies.filter { CommonAllergy.fromWire(it)?.isFood != false }

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId, authenticatedUser) }

    FoodMenuScreen(
        uiState = uiState,
        canManageFoodMenu = canManage,
        daySections = viewModel.daySections(),
        userAllergies = foodAllergies,
        participantAllergies = participantAllergies,
        operationError = operationError,
        operationMessage = operationMessage,
        onBack = onBack,
        onAddEntry = {
            viewModel.prepareNew(campingId)
            onOpenEditor(null)
        },
        onEditEntry = { entry ->
            viewModel.prepareEdit(entry)
            onOpenEditor(entry.id)
        },
        onDeleteEntry = { entryId -> viewModel.deleteEntry(entryId, campingId) },
        onOpenAttendee = onOpenAttendee,
        onRetry = { viewModel.load(campingId, authenticatedUser) },
        onClearError = viewModel::clearOperationError,
        onClearMessage = viewModel::clearOperationMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodMenuScreen(
    uiState: FoodMenuUiState,
    canManageFoodMenu: Boolean,
    daySections: List<FoodMenuDaySection>,
    userAllergies: List<String> = emptyList(),
    participantAllergies: List<ParticipantAllergySummary> = emptyList(),
    operationError: String?,
    operationMessage: String?,
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (FoodMenuEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onOpenAttendee: (String) -> Unit = {},
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val addMenuDescription = stringResource(R.string.schedule_add_menu_cd)
    val participantAllergyDescription = stringResource(R.string.food_menu_participant_allergies)
    val snackbarHostState = remember { SnackbarHostState() }
    var showParticipantAllergies by remember { mutableStateOf(false) }

    if (showParticipantAllergies) {
        ParticipantAllergyDialog(
            summaries = participantAllergies,
            onOpenAttendee = onOpenAttendee,
            onDismiss = { showParticipantAllergies = false },
        )
    }

    LaunchedEffect(operationMessage) {
        if (operationMessage != null) {
            snackbarHostState.showSnackbar(operationMessage, duration = SnackbarDuration.Short)
            onClearMessage()
        }
    }
    LaunchedEffect(operationError) {
        if (operationError != null) {
            snackbarHostState.showSnackbar(operationError, duration = SnackbarDuration.Long)
            onClearError()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.food_camp_menu),
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
                    if (canManageFoodMenu) {
                        IconButton(
                            onClick = { showParticipantAllergies = true },
                            modifier = Modifier.semantics {
                                contentDescription = participantAllergyDescription
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = colors.amber,
                            )
                        }
                        IconButton(
                            onClick = onAddEntry,
                            modifier = Modifier.semantics { contentDescription = addMenuDescription },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
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
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState) {
                is FoodMenuUiState.Loading -> CzLoadingView(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(R.string.schedule_menu_loading),
                )

                is FoodMenuUiState.Error -> CzErrorState(
                    title = stringResource(R.string.schedule_menu_error_title),
                    message = uiState.message,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                is FoodMenuUiState.Empty -> CzEmptyState(
                    title = stringResource(R.string.schedule_menu_empty_title),
                    message = stringResource(
                        if (canManageFoodMenu) {
                            R.string.food_menu_empty_manager_message
                        } else {
                            R.string.food_menu_empty_participant_message
                        },
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                is FoodMenuUiState.Loaded -> FoodMenuContent(
                    daySections = daySections,
                    userAllergies = userAllergies,
                    participantAllergies = participantAllergies,
                    canManage = canManageFoodMenu,
                    onEditEntry = onEditEntry,
                    onDeleteEntry = onDeleteEntry,
                )
            }
        }
    }
}

@Composable
private fun FoodMenuContent(
    daySections: List<FoodMenuDaySection>,
    userAllergies: List<String>,
    participantAllergies: List<ParticipantAllergySummary>,
    canManage: Boolean,
    onEditEntry: (FoodMenuEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    var deletingEntry by remember { mutableStateOf<FoodMenuEntry?>(null) }
    var selectedDayId by remember(daySections) { mutableStateOf(daySections.firstOrNull()?.id) }
    val selectedSection = daySections.firstOrNull { it.id == selectedDayId } ?: daySections.firstOrNull()

    if (deletingEntry != null) {
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text(stringResource(R.string.schedule_delete_menu_title)) },
            text = { Text(stringResource(R.string.schedule_delete_menu_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val e = deletingEntry ?: return@TextButton
                        deletingEntry = null
                        onDeleteEntry(e.id)
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.czColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (userAllergies.isNotEmpty()) {
            Surface(
                color = MaterialTheme.czColors.amber.copy(alpha = 0.08f),
                shape = RoundedCornerShape(CzRadius.lg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
            ) {
                Column(
                    modifier = Modifier.padding(CzSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.food_menu_your_allergies),
                        color = MaterialTheme.czColors.amber,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AllergyChips(tokens = userAllergies)
                }
            }
        }

        if (canManage && participantAllergies.isNotEmpty()) {
            Surface(
                color = MaterialTheme.czColors.amber.copy(alpha = 0.08f),
                shape = RoundedCornerShape(CzRadius.lg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.padding(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.czColors.amber)
                    Text(
                        text = pluralStringResource(
                            R.plurals.food_menu_participant_allergy_count,
                            participantAllergies.size,
                            participantAllergies.size,
                        ),
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            modifier = Modifier.padding(top = CzSpacing.xs, bottom = CzSpacing.sm),
        ) {
            items(daySections, key = { it.id }) { section ->
                FoodMenuDayChip(
                    section = section,
                    selected = section.id == selectedSection?.id,
                    onClick = { selectedDayId = section.id },
                )
            }
        }

        selectedSection?.let { section ->
            DaySectionHeader(date = section.date, mealCount = section.entries.size)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = CzSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                items(section.entries, key = { it.id }) { entry ->
                    MealMenuCard(
                        entry = entry,
                        userAllergies = userAllergies,
                        canManage = canManage,
                        onEdit = { onEditEntry(entry) },
                        onDelete = { deletingEntry = entry },
                        modifier = Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantAllergyDialog(
    summaries: List<ParticipantAllergySummary>,
    onOpenAttendee: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.food_menu_participant_allergies)) },
        text = {
            if (summaries.isEmpty()) {
                Text(
                    text = stringResource(R.string.food_menu_no_participant_allergies),
                    color = MaterialTheme.czColors.textSecondary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    items(summaries, key = { it.attendeeId }) { summary ->
                        ParticipantAllergyRow(
                            summary = summary,
                            onClick = {
                                onDismiss()
                                onOpenAttendee(summary.attendeeId)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_done))
            }
        },
    )
}

@Composable
private fun ParticipantAllergyRow(
    summary: ParticipantAllergySummary,
    onClick: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .clickable(onClick = onClick)
            .background(MaterialTheme.czColors.surface)
            .padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.czColors.amber.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = summary.attendeeName.trim().take(1).uppercase(locale),
                color = MaterialTheme.czColors.amber,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Text(
                text = summary.attendeeName,
                color = MaterialTheme.czColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            AllergyChips(tokens = summary.allergies)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.czColors.textSecondary)
    }
}

@Composable
private fun FoodMenuDayChip(
    section: FoodMenuDaySection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val weekday = remember(section.date) { SimpleDateFormat("EEE", Locale.getDefault()).format(section.date) }
    val day = remember(section.date) { SimpleDateFormat("dd", Locale.getDefault()).format(section.date) }
    Surface(
        onClick = onClick,
        color = if (selected) colors.accent else colors.surface,
        shape = RoundedCornerShape(CzRadius.md),
    ) {
        Column(
            modifier = Modifier.width(60.dp).padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(weekday, style = CzTypeScale.caption, color = if (selected) Color.White else colors.textPrimary)
            Text(
                day,
                style = CzTypeScale.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) Color.White else colors.textPrimary,
            )
        }
    }
}

@Composable
private fun DaySectionHeader(date: java.util.Date, mealCount: Int) {
    val colors = MaterialTheme.czColors
    val dayTitle = remember(date) { SimpleDateFormat("EEEE, MMM yyyy", Locale.getDefault()).format(date) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = dayTitle.uppercase(),
            style = CzTypeScale.caption.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            ),
            color = colors.textPrimary,
        )
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = CzSpacing.xs),
            color = colors.divider,
        )
        Text(
            text = pluralStringResource(R.plurals.food_menu_meal_count, mealCount, mealCount),
            style = CzTypeScale.caption,
            color = colors.accent,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealMenuCard(
    entry: FoodMenuEntry,
    userAllergies: List<String> = emptyList(),
    canManage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var menuExpanded by remember { mutableStateOf(false) }
    val mealName = stringResource(entry.meal.displayNameRes)
    val menuOptionsDescription = stringResource(R.string.schedule_menu_options_cd, mealName)
    val itemCount = if (entry.items.size == 1) {
        stringResource(R.string.food_menu_item_count, entry.items.size)
    } else {
        stringResource(R.string.food_menu_item_count_plural, entry.items.size)
    }
    val hasAllergyWarning = entry.items.any { it.matchedAllergens(userAllergies).isNotEmpty() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                // Header row: icon + meal name + date + item count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.ember.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = entry.meal.icon,
                            contentDescription = null,
                            tint = colors.ember,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mealName,
                            style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                        Text(
                            text = entry.dateText(),
                            style = CzTypeScale.caption,
                            color = colors.textSecondary,
                        )
                    }
                    Text(
                        text = itemCount,
                        style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textSecondary,
                    )
                    if (hasAllergyWarning) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = stringResource(R.string.food_menu_matches_profile_allergies),
                            tint = colors.amber,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (canManage) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .semantics { contentDescription = menuOptionsDescription },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_edit)) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onEdit()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_delete), color = colors.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = colors.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }

                // Structured dishes with per-item allergy warnings.
                if (entry.items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        entry.items.forEach { item ->
                            FoodMenuDishRow(item = item, userAllergies = userAllergies)
                        }
                    }
                }

                // Notes row
                if (entry.notes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp).padding(top = 1.dp),
                        )
                        Text(
                            text = entry.notes,
                            style = CzTypeScale.caption,
                            color = colors.textSecondary,
                        )
                    }
                }

                if (onSeeAll != null) {
                    TextButton(
                        onClick = onSeeAll,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.food_see_all_menus),
                            style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ember,
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodMenuDishRow(
    item: FoodMenuItem,
    userAllergies: List<String>,
) {
    val colors = MaterialTheme.czColors
    val matched = item.matchedAllergens(userAllergies)
    val matchedSet = matched.toSet()
    val warning = matched.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.sm))
            .background(if (warning) colors.amber.copy(alpha = 0.08f) else Color.Transparent)
            .padding(if (warning) CzSpacing.xs else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        if (warning) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = colors.amber,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(colors.ember),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.name,
                style = CzTypeScale.caption.copy(
                    fontWeight = if (warning) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (warning) colors.amber else colors.textPrimary,
            )
            item.details?.takeUnless(String::isBlank)?.let { details ->
                Text(details, style = CzTypeScale.caption, color = colors.textSecondary)
            }
            if (item.allergens.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.allergens.forEach { token ->
                        val isMatch = token in matchedSet
                        Surface(
                            color = if (isMatch) colors.amber else colors.textSecondary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = allergyDisplayName(token),
                                style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isMatch) Color.White else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = CzSpacing.xs, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            item.note?.takeUnless(String::isBlank)?.let { note ->
                Text(note, style = CzTypeScale.caption, color = colors.textSecondary)
            }
        }
    }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

private val entryDateFormatter = java.text.SimpleDateFormat(
    "EEEE, MMMM d",
    java.util.Locale.getDefault(),
)

private fun FoodMenuEntry.dateText(): String = entryDateFormatter.format(date)

// ── Meal kind display extensions ──────────────────────────────────────────────

val FoodMealKind.displayNameRes: Int
    get() = when (this) {
        FoodMealKind.Breakfast -> R.string.food_meal_breakfast
        FoodMealKind.Lunch -> R.string.food_meal_lunch
        FoodMealKind.Dinner -> R.string.food_meal_dinner
        FoodMealKind.Snack -> R.string.food_meal_snack
    }

val FoodMealKind.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        FoodMealKind.Breakfast -> Icons.Rounded.FreeBreakfast
        FoodMealKind.Lunch -> Icons.Rounded.Restaurant
        FoodMealKind.Dinner -> Icons.Rounded.DinnerDining
        FoodMealKind.Snack -> Icons.Rounded.LocalCafe
    }

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun FoodMenuLoadingPreview() {
    CampzoneTheme {
        FoodMenuScreen(
            uiState = FoodMenuUiState.Loading,
            canManageFoodMenu = false,
            daySections = emptyList(),
            operationError = null,
            operationMessage = null,
            onBack = {},
            onAddEntry = {},
            onEditEntry = {},
            onDeleteEntry = {},
            onRetry = {},
            onClearError = {},
            onClearMessage = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FoodMenuEmptyAdminPreview() {
    CampzoneTheme {
        FoodMenuScreen(
            uiState = FoodMenuUiState.Empty,
            canManageFoodMenu = true,
            daySections = emptyList(),
            operationError = null,
            operationMessage = null,
            onBack = {},
            onAddEntry = {},
            onEditEntry = {},
            onDeleteEntry = {},
            onRetry = {},
            onClearError = {},
            onClearMessage = {},
        )
    }
}

package fr.ziyon.campzone.ui.schedule.food

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
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
import fr.ziyon.campzone.data.schedule.FakeFoodMenuService

@Composable
fun FoodMenuRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenEditor: (entryId: String?) -> Unit,
    viewModel: FoodMenuViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val canManage by viewModel.canManageFoodMenu.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId, authenticatedUser) }

    FoodMenuScreen(
        uiState = uiState,
        canManageFoodMenu = canManage,
        daySections = viewModel.daySections(),
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
    operationError: String?,
    operationMessage: String?,
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (FoodMenuEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val addMenuDescription = stringResource(R.string.schedule_add_menu_cd)
    val snackbarHostState = remember { SnackbarHostState() }

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
                        text = "Camp Menu",
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
                    message = if (canManageFoodMenu)
                        "Tap + to create the first meal entry."
                    else
                        "Camp organizers will publish meals here soon.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                is FoodMenuUiState.Loaded -> FoodMenuContent(
                    daySections = daySections,
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
    canManage: Boolean,
    onEditEntry: (FoodMenuEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    var deletingEntry by remember { mutableStateOf<FoodMenuEntry?>(null) }

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

    LazyColumn(
        contentPadding = PaddingValues(bottom = CzSpacing.xxl, top = CzSpacing.sm),
    ) {
        daySections.forEach { section ->
            stickyHeader(key = "header-${section.id}") {
                DaySectionHeader(section.dayTitle)
            }
            items(section.entries, key = { it.id }) { entry ->
                MealMenuCard(
                    entry = entry,
                    canManage = canManage,
                    onEdit = { onEditEntry(entry) },
                    onDelete = { deletingEntry = entry },
                    modifier = Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun DaySectionHeader(dayTitle: String) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(
            imageVector = Icons.Rounded.CalendarMonth,
            contentDescription = null,
            tint = colors.ember,
            modifier = Modifier.size(12.dp),
        )
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
    }
}

@Composable
fun MealMenuCard(
    entry: FoodMenuEntry,
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
    val itemCount = if (entry.dishes.size == 1) {
        stringResource(R.string.food_menu_item_count, entry.dishes.size)
    } else {
        stringResource(R.string.food_menu_item_count_plural, entry.dishes.size)
    }

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

                // Dish bullet list
                if (entry.dishes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        entry.dishes.forEach { dish ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(colors.ember),
                                )
                                Text(
                                    text = dish,
                                    style = CzTypeScale.caption,
                                    color = colors.textPrimary,
                                )
                            }
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
                            text = "See all menus",
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

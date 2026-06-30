package fr.ziyon.campzone.ui.schedule.food

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.FoodMealKind
import fr.ziyon.campzone.data.profile.AllergyFormatter
import fr.ziyon.campzone.data.profile.CommonAllergy
import fr.ziyon.campzone.ui.common.allergyDisplayName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodMenuEditorScreen(
    viewModel: FoodMenuViewModel,
    campingId: String,
    isEditing: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenAttendee: (String) -> Unit = {},
) {
    val form by viewModel.editorForm.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val dateRange by viewModel.campingDateRange.collectAsState()
    val participantAllergies by viewModel.participantAllergies.collectAsState()
    var showAllergyPanel by remember { mutableStateOf(false) }
    var editingAllergensFor by remember { mutableStateOf<String?>(null) }
    val allergyCounts = remember(participantAllergies) {
        participantAllergies
            .flatMap { it.allergies }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    }

    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val dishesDescription = stringResource(R.string.food_menu_dishes_cd)

    val allergenDraft = form.items.firstOrNull { it.id == editingAllergensFor }
    if (allergenDraft != null) {
        FoodAllergenDialog(
            selected = allergenDraft.allergens,
            onSelectedChange = { selected ->
                viewModel.updateDish(allergenDraft.id) { it.copy(allergens = selected) }
            },
            onDismiss = { editingAllergensFor = null },
        )
    }

    val datePickerDialog = remember(form.date, dateRange) {
        val cal = Calendar.getInstance().apply { time = form.date }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    time = form.date
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                viewModel.updateForm { it.copy(date = picked) }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).apply {
            dateRange?.let { (start, end) ->
                datePicker.minDate = start.time
                datePicker.maxDate = end.time
            }
        }
    }

    val timePickerDialog = remember(form.date) {
        val cal = Calendar.getInstance().apply { time = form.date }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val picked = Calendar.getInstance().apply {
                    time = form.date
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                viewModel.updateForm { it.copy(date = picked) }
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true,
        )
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(if (isEditing) R.string.food_menu_edit_title else R.string.food_menu_new_title),
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
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.base, vertical = CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            // ── Section: When ─────────────────────────────────────────────────
            FormSectionHeader(title = stringResource(R.string.food_menu_when), icon = Icons.Rounded.CalendarMonth)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.lg))
                    .background(colors.surface),
            ) {
                // Date picker row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                        .padding(horizontal = CzSpacing.base, vertical = CzSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.food_menu_date), style = CzTypeScale.body, color = colors.textPrimary)
                    Text(
                        text = form.date.formattedDate(),
                        style = CzTypeScale.body,
                        color = colors.ember,
                    )
                }

                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = CzSpacing.base),
                    color = colors.divider,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { timePickerDialog.show() }
                        .padding(horizontal = CzSpacing.base, vertical = CzSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.food_menu_time), style = CzTypeScale.body, color = colors.textPrimary)
                    Text(
                        text = form.date.formattedTime(),
                        style = CzTypeScale.body,
                        color = colors.ember,
                    )
                }

                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = CzSpacing.base),
                    color = colors.divider,
                )

                // Meal picker - horizontal row of 4 chips
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CzSpacing.base),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.food_menu_meal),
                        style = CzTypeScale.caption,
                        color = colors.textSecondary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FoodMealKind.entries.forEach { meal ->
                            MealChip(
                                meal = meal,
                                selected = form.meal == meal,
                                onClick = { viewModel.selectMeal(meal) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── Section: Dishes ───────────────────────────────────────────────
            FormSectionHeader(title = stringResource(R.string.food_menu_dishes), icon = Icons.Rounded.Restaurant)

            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                form.items.forEachIndexed { index, draft ->
                    FoodMenuItemEditorCard(
                        draft = draft,
                        index = index,
                        onChange = { updated ->
                            viewModel.updateDish(draft.id) { updated }
                        },
                        onEditAllergens = { editingAllergensFor = draft.id },
                        onDelete = { viewModel.removeDish(draft.id) },
                        modifier = Modifier.semantics { contentDescription = dishesDescription },
                    )
                }
                CzButton(
                    text = stringResource(R.string.food_menu_add_dish),
                    onClick = viewModel::addDish,
                    variant = CzButtonVariant.Secondary,
                    leadingIcon = {
                        Icon(Icons.Rounded.AddCircle, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Section: Notes ────────────────────────────────────────────────
            FormSectionHeader(title = stringResource(R.string.food_menu_notes), icon = Icons.Rounded.LocalCafe)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.lg))
                    .background(colors.surface),
            ) {
                TextField(
                    value = form.notes,
                    onValueChange = { viewModel.updateForm { f -> f.copy(notes = it) } },
                    placeholder = {
                        Text(
                            stringResource(R.string.food_menu_notes_placeholder),
                            style = CzTypeScale.body,
                            color = colors.textSecondary,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = colors.ember,
                    ),
                )
            }

            // ── Validation / operation errors ─────────────────────────────────
            val errorText = operationError
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = CzTypeScale.caption,
                    color = colors.error,
                )
            }

            // ── Save button ───────────────────────────────────────────────────
            Spacer(Modifier.height(CzSpacing.xs))
            CzButton(
                text = stringResource(if (isEditing) R.string.food_menu_save_changes else R.string.food_menu_add_menu),
                onClick = { viewModel.saveEntry(campingId, onSaved) },
                variant = CzButtonVariant.Primary,
                loading = isSaving,
                enabled = form.isValid && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            FormSectionHeader(
                title = stringResource(R.string.food_menu_kitchen_awareness),
                icon = Icons.Rounded.Restaurant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.lg))
                    .background(colors.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAllergyPanel = !showAllergyPanel }
                        .padding(CzSpacing.base),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = colors.amber,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.food_menu_participant_allergies),
                        style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (participantAllergies.isNotEmpty()) {
                        Text(
                            text = participantAllergies.size.toString(),
                            style = CzTypeScale.caption.copy(fontWeight = FontWeight.Bold),
                            color = colors.amber,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.amber.copy(alpha = 0.12f))
                                .padding(horizontal = CzSpacing.sm, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = if (showAllergyPanel) "⌃" else "⌄",
                        style = CzTypeScale.body,
                        color = colors.textSecondary,
                    )
                }

                if (showAllergyPanel) {
                    androidx.compose.material3.HorizontalDivider(color = colors.divider)
                    if (participantAllergies.isEmpty()) {
                        Text(
                            text = stringResource(R.string.food_menu_no_participant_allergies),
                            style = CzTypeScale.caption,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(CzSpacing.base),
                        )
                    } else {
                        allergyCounts.forEach { (token, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                            ) {
                                Icon(
                                    Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = colors.amber,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = allergyDisplayName(token),
                                    style = CzTypeScale.subhead,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.food_menu_people_count,
                                        count,
                                        count,
                                    ),
                                    style = CzTypeScale.caption,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(
                            color = colors.divider,
                            modifier = Modifier.padding(horizontal = CzSpacing.base),
                        )
                        participantAllergies.forEach { summary ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAttendee(summary.attendeeId) }
                                    .padding(CzSpacing.base),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                            ) {
                                if (!summary.photoUrl.isNullOrBlank()) {
                                    coil.compose.AsyncImage(
                                        model = summary.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                } else {
                                    Text(
                                        text = summary.attendeeName.trim().take(1).uppercase(),
                                        style = CzTypeScale.subhead.copy(fontWeight = FontWeight.Bold),
                                        color = colors.accent,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(colors.accent.copy(alpha = 0.12f))
                                            .padding(CzSpacing.xs),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = summary.attendeeName,
                                        style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = localizedAllergySummary(summary.allergies),
                                        style = CzTypeScale.caption,
                                        color = colors.amber,
                                        maxLines = 1,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(CzSpacing.xxl))
        }
    }
}

@Composable
private fun localizedAllergySummary(tokens: List<String>): String {
    val names = mutableListOf<String>()
    for (token in tokens) names += allergyDisplayName(token)
    return names.joinToString(", ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodMenuItemEditorCard(
    draft: FoodMenuItemDraft,
    index: Int,
    onChange: (FoodMenuItemDraft) -> Unit,
    onEditAllergens: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(colors.surface)
            .padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.accent,
                )
            }
            TextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                placeholder = { Text(stringResource(R.string.food_menu_dish_name)) },
                singleLine = true,
                textStyle = CzTypeScale.body.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
                colors = compactFieldColors(),
            )
            if (draft.allergens.isNotEmpty()) {
                Text(
                    text = draft.allergens.size.toString(),
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.amber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.amber.copy(alpha = 0.12f))
                        .padding(horizontal = CzSpacing.xs, vertical = 2.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.food_menu_remove_dish, index + 1),
                    tint = colors.error,
                )
            }
        }

        androidx.compose.material3.HorizontalDivider(color = colors.divider)
        DishTextField(
            label = stringResource(R.string.food_menu_dish_description),
            placeholder = stringResource(R.string.food_menu_dish_description_placeholder),
            value = draft.details,
            onValueChange = { onChange(draft.copy(details = it)) },
        )
        DishTextField(
            label = stringResource(R.string.food_menu_dish_note),
            placeholder = stringResource(R.string.food_menu_dish_note_placeholder),
            value = draft.note,
            onValueChange = { onChange(draft.copy(note = it)) },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.food_menu_allergens).uppercase(),
                style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEditAllergens) {
                Icon(
                    if (draft.allergens.isEmpty()) Icons.Rounded.AddCircle else Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(CzSpacing.xs))
                Text(
                    stringResource(
                        if (draft.allergens.isEmpty()) R.string.food_menu_add_allergens else R.string.common_edit,
                    ),
                )
            }
        }
        if (draft.allergens.isEmpty()) {
            Text(
                text = stringResource(R.string.food_menu_no_allergens),
                style = CzTypeScale.caption,
                color = colors.textSecondary,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                draft.allergens.forEach { token ->
                    InputChip(
                        selected = true,
                        onClick = {
                            onChange(draft.copy(allergens = draft.allergens.filterNot { it == token }))
                        },
                        label = { Text(allergyDisplayName(token)) },
                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.food_menu_allergens_helper),
            style = CzTypeScale.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun DishTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
        Text(
            text = label.uppercase(),
            style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            minLines = 1,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            colors = compactFieldColors(),
        )
    }
}

@Composable
private fun compactFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.czColors.background,
    unfocusedContainerColor = MaterialTheme.czColors.background,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.czColors.ember,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodAllergenDialog(
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var customText by remember { mutableStateOf("") }
    val foodNames = mutableMapOf<CommonAllergy, String>()
    CommonAllergy.foodAllergies.forEach { allergy ->
        foodNames[allergy] = allergyDisplayName(allergy.wireValue)
    }

    fun add(token: String) {
        if (selected.none { it.equals(token, ignoreCase = true) }) {
            onSelectedChange(AllergyFormatter.cleaned(selected + token))
        }
    }

    fun addCustom() {
        val token = AllergyFormatter.normalizedToken(customText) { allergy ->
            foodNames[allergy] ?: allergy.wireValue
        } ?: return
        add(token)
        customText = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.food_menu_allergens)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                if (selected.isEmpty()) {
                    Text(
                        stringResource(R.string.food_menu_no_allergens),
                        color = MaterialTheme.czColors.textSecondary,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                    ) {
                        selected.forEach { token ->
                            InputChip(
                                selected = true,
                                onClick = { onSelectedChange(selected.filterNot { it == token }) },
                                label = { Text(allergyDisplayName(token)) },
                                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.food_menu_common_allergens).uppercase(),
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.czColors.textSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    CommonAllergy.foodAllergies
                        .filter { allergy -> selected.none { it.equals(allergy.wireValue, ignoreCase = true) } }
                        .forEach { allergy ->
                            FilterChip(
                                selected = false,
                                onClick = { add(allergy.wireValue) },
                                label = { Text(foodNames.getValue(allergy)) },
                                leadingIcon = { Icon(Icons.Rounded.AddCircle, contentDescription = null) },
                            )
                        }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    TextField(
                        value = customText,
                        onValueChange = { customText = it },
                        placeholder = { Text(stringResource(R.string.allergies_add_specific)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (customText.isNotBlank()) {
                        TextButton(onClick = ::addCustom) { Text(stringResource(R.string.common_add)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    )
}

// ── Meal chip ─────────────────────────────────────────────────────────────────

@Composable
private fun MealChip(
    meal: FoodMealKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val bg = if (selected) colors.ember else colors.background
    val fg = if (selected) Color.White else colors.textSecondary
    val mealName = stringResource(meal.displayNameRes)
    val mealDescription = if (selected) {
        stringResource(R.string.food_menu_meal_selected_cd, mealName)
    } else {
        mealName
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CzRadius.md))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = CzSpacing.sm)
            .semantics { contentDescription = mealDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = meal.icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = mealName,
            style = CzTypeScale.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = fg,
        )
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun FormSectionHeader(title: String, icon: ImageVector) {
    val colors = MaterialTheme.czColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.ember,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = title.uppercase(),
            style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
    }
}

// ── Date formatter ────────────────────────────────────────────────────────────

private val editorDateFormatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
private val editorTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun Date.formattedDate(): String = editorDateFormatter.format(this)
private fun Date.formattedTime(): String = editorTimeFormatter.format(this)

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun FoodMenuEditorNewPreview() {
    CampzoneTheme {
        // Hilt ViewModel wired at runtime; no preview body needed
    }
}

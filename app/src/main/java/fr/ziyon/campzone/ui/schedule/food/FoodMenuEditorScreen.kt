package fr.ziyon.campzone.ui.schedule.food

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.FoodMealKind
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
) {
    val form by viewModel.editorForm.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val dateRange by viewModel.campingDateRange.collectAsState()

    val colors = MaterialTheme.czColors
    val context = LocalContext.current

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
                        text = if (isEditing) "Edit Menu" else "New Menu",
                        style = CzTypeScale.headline,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
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
            FormSectionHeader(title = "When", icon = Icons.Rounded.CalendarMonth)

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
                    Text("Date", style = CzTypeScale.body, color = colors.textPrimary)
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
                    Text("Time", style = CzTypeScale.body, color = colors.textPrimary)
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
                        text = "Meal",
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
            FormSectionHeader(title = "Dishes", icon = Icons.Rounded.Restaurant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.lg))
                    .background(colors.surface),
            ) {
                TextField(
                    value = form.dishesText,
                    onValueChange = { viewModel.updateForm { f -> f.copy(dishesText = it) } },
                    placeholder = {
                        Text(
                            "One dish per line, e.g.\nGranola bowl\nFresh fruit",
                            style = CzTypeScale.body,
                            color = colors.textSecondary,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Dishes for this meal, one per line" },
                    minLines = 4,
                    maxLines = 10,
                    textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = colors.ember,
                    ),
                )
                Text(
                    text = "Add one dish per line, or separate with commas.",
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(
                        start = CzSpacing.base,
                        end = CzSpacing.base,
                        bottom = CzSpacing.sm,
                    ),
                )
            }

            // ── Section: Notes ────────────────────────────────────────────────
            FormSectionHeader(title = "Notes", icon = Icons.Rounded.LocalCafe)

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
                            "Allergens, vegan station…",
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
            val validationError = form.validationError
            if (validationError != null) {
                Text(
                    text = validationError,
                    style = CzTypeScale.caption,
                    color = colors.amber,
                )
            }
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
                text = if (isEditing) "Save changes" else "Add menu",
                onClick = { viewModel.saveEntry(campingId, onSaved) },
                variant = CzButtonVariant.Primary,
                loading = isSaving,
                enabled = form.isValid && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(CzSpacing.xxl))
        }
    }
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

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CzRadius.md))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = CzSpacing.sm)
            .semantics { contentDescription = meal.displayName + if (selected) ", selected" else "" },
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
            text = meal.displayName,
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

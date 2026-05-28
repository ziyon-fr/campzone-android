package fr.ziyon.campzone.ui.polls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.PollForm
import fr.ziyon.campzone.data.model.PollFormError
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollEditorRoute(
    pollId: String?,
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PollViewModel = hiltViewModel(),
) {
    val colors = MaterialTheme.czColors
    val form by viewModel.form.collectAsState()
    val formError by viewModel.formError.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()

    LaunchedEffect(pollId, campingId) { viewModel.startEditor(pollId, campingId) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(if (pollId == null) R.string.poll_new_title else R.string.poll_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            // Question
            SectionLabel(stringResource(R.string.poll_question_header))
            OutlinedTextField(
                value = form.question,
                onValueChange = { v -> viewModel.updateForm { it.copy(question = v) } },
                label = { Text(stringResource(R.string.poll_question_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
                label = { Text(stringResource(R.string.poll_description_label)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Options
            SectionLabel(stringResource(R.string.poll_options_header))
            form.optionLabels.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { v ->
                            viewModel.updateForm { f ->
                                f.copy(optionLabels = f.optionLabels.toMutableList().also { it[index] = v })
                            }
                        },
                        label = { Text(stringResource(R.string.poll_option_label, index + 1)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    if (form.optionLabels.size > PollForm.MIN_OPTIONS) {
                        IconButton(onClick = {
                            viewModel.updateForm { f ->
                                f.copy(optionLabels = f.optionLabels.filterIndexed { i, _ -> i != index })
                            }
                        }) {
                            Icon(Icons.Rounded.RemoveCircle, stringResource(R.string.poll_remove_option), tint = colors.error)
                        }
                    }
                }
            }
            if (form.optionLabels.size < PollForm.MAX_OPTIONS) {
                TextButton(onClick = {
                    viewModel.updateForm { it.copy(optionLabels = it.optionLabels + "") }
                }) {
                    Icon(Icons.Rounded.Add, null, tint = colors.ember)
                    Spacer(Modifier.width(CzSpacing.xs))
                    Text(stringResource(R.string.poll_add_option), color = colors.ember)
                }
            }
            Text(
                stringResource(R.string.poll_options_footer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            // Settings
            SectionLabel(stringResource(R.string.poll_settings_header))
            SwitchRow(stringResource(R.string.poll_setting_multiple), form.allowsMultiple) { v ->
                viewModel.updateForm { it.copy(allowsMultiple = v) }
            }
            SwitchRow(stringResource(R.string.poll_setting_show_results), form.showsResultsBeforeClose) { v ->
                viewModel.updateForm { it.copy(showsResultsBeforeClose = v) }
            }
            SwitchRow(stringResource(R.string.poll_setting_open), form.isOpen) { v ->
                viewModel.updateForm { it.copy(isOpen = v) }
            }
            SwitchRow(stringResource(R.string.poll_setting_autoclose), form.hasCloseDate) { v ->
                viewModel.updateForm { it.copy(hasCloseDate = v) }
            }
            if (form.hasCloseDate) {
                CloseDatePickerField(
                    closesAt = form.closesAt,
                    onPicked = { picked -> viewModel.updateForm { it.copy(closesAt = picked) } },
                )
            }

            formError?.let { error ->
                Text(formErrorMessage(error), color = colors.warning, style = MaterialTheme.typography.bodySmall)
            }
            operationError?.let { error ->
                Text(error, color = colors.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { viewModel.savePoll(campingId, authenticatedUser, onSaved) },
                enabled = form.isValid && !isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.ember),
            ) {
                Text(stringResource(if (pollId == null) R.string.poll_create_button else R.string.poll_save_button))
            }
            Spacer(Modifier.padding(CzSpacing.md))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.czColors.textSecondary,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloseDatePickerField(closesAt: Date, onPicked: (Date) -> Unit) {
    val colors = MaterialTheme.czColors
    val formatter = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf(closesAt.time) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.poll_closes_label), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Text(formatter.format(closesAt), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
        TextButton(onClick = { showDate = true }) {
            Text(stringResource(R.string.poll_change_date), color = colors.ember)
        }
    }

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = closesAt.time)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { pendingDateMillis = it }
                    showDate = false
                    showTime = true
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) { DatePicker(state = dateState) }
    }

    if (showTime) {
        val current = remember { Calendar.getInstance().apply { time = closesAt } }
        val timeState = rememberTimePickerState(
            initialHour = current.get(Calendar.HOUR_OF_DAY),
            initialMinute = current.get(Calendar.MINUTE),
            is24Hour = true,
        )
        Dialog(onDismissRequest = { showTime = false }) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    fr.ziyon.campzone.core.designsystem.CzRadius.lg,
                ),
                color = MaterialTheme.czColors.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CzSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    TimePicker(state = timeState)
                    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                        TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.common_cancel)) }
                        TextButton(onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = pendingDateMillis
                                set(Calendar.HOUR_OF_DAY, timeState.hour)
                                set(Calendar.MINUTE, timeState.minute)
                                set(Calendar.SECOND, 0)
                            }
                            onPicked(cal.time)
                            showTime = false
                        }) { Text(stringResource(R.string.common_ok)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun formErrorMessage(error: PollFormError): String = stringResource(
    when (error) {
        PollFormError.QuestionRequired -> R.string.poll_form_error_question
        PollFormError.NotEnoughOptions -> R.string.poll_form_error_options
        PollFormError.DuplicateOptions -> R.string.poll_form_error_duplicate
        PollFormError.InvalidCloseDate -> R.string.poll_form_error_close_date
    },
)

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun PollEditorRoutePreview() {
    fr.ziyon.campzone.core.designsystem.CampzoneTheme {
        PollEditorRoute(
            pollId = null,
            campingId = "preview-camp",
            authenticatedUser = pollPreviewUser(),
            onBack = {},
            onSaved = {},
            viewModel = PollViewModel(
                fr.ziyon.campzone.data.polls.FakePollService(),
                fr.ziyon.campzone.data.polls.FakePollNotificationDispatcher(),
            ),
        )
    }
}

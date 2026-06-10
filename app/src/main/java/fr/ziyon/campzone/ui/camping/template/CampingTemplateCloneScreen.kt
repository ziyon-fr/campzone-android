package fr.ziyon.campzone.ui.camping.template

import android.app.DatePickerDialog as AndroidDatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingTemplateCloneForm
import fr.ziyon.campzone.data.model.CampingTemplateCloneValidationError
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingTemplateCloneRoute(
    sourceCampingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampingTemplateCloneViewModel = hiltViewModel(),
) {
    LaunchedEffect(sourceCampingId, authenticatedUser.uid) {
        viewModel.load(sourceCampingId, authenticatedUser)
    }

    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()
    val availableCopies by viewModel.availableCopies.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val createdCampingId by viewModel.createdCampingId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(operationError) {
        operationError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeOperationError()
        }
    }
    LaunchedEffect(createdCampingId) {
        val id = createdCampingId ?: return@LaunchedEffect
        viewModel.consumeCreatedCampingId()
        onCreated(id)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.camping_template_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.czColors.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val state = uiState) {
                CampingTemplateCloneUiState.Loading -> CzLoadingView(
                    message = stringResource(R.string.camping_template_loading),
                    modifier = Modifier.fillMaxSize(),
                )

                CampingTemplateCloneUiState.Restricted -> CzEmptyState(
                    title = stringResource(R.string.camping_template_restricted_title),
                    message = stringResource(R.string.camping_template_restricted_message),
                    icon = {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.warning,
                            modifier = Modifier.size(42.dp),
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.lg),
                )

                is CampingTemplateCloneUiState.Error -> CzErrorState(
                    title = stringResource(R.string.camping_template_error_title),
                    message = state.message,
                    onRetry = { viewModel.retry(sourceCampingId, authenticatedUser) },
                    retryLabel = stringResource(R.string.common_retry),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.lg),
                )

                is CampingTemplateCloneUiState.Ready -> CampingTemplateCloneContent(
                    source = state.source,
                    form = form,
                    availableCopies = availableCopies,
                    validationErrors = validationErrors,
                    isSaving = isSaving,
                    onTitleChange = viewModel::updateTitle,
                    onStartDateChange = viewModel::updateStartDate,
                    onEndDateChange = viewModel::updateEndDate,
                    onRegistrationStatusChange = viewModel::updateRegistrationStatus,
                    onToggleSchedule = viewModel::toggleSchedule,
                    onToggleTeams = viewModel::toggleTeams,
                    onToggleSongbook = viewModel::toggleSongbook,
                    onToggleGuidelines = viewModel::toggleGuidelines,
                    onCreate = { viewModel.cloneTemplate(sourceCampingId) },
                )
            }
        }
    }
}

@Composable
private fun CampingTemplateCloneContent(
    source: Camping,
    form: CampingTemplateCloneForm,
    availableCopies: CampingTemplateClonePermissions,
    validationErrors: List<CampingTemplateCloneValidationError>,
    isSaving: Boolean,
    onTitleChange: (String) -> Unit,
    onStartDateChange: (Date) -> Unit,
    onEndDateChange: (Date) -> Unit,
    onRegistrationStatusChange: (CampingRegistrationStatus) -> Unit,
    onToggleSchedule: (Boolean) -> Unit,
    onToggleTeams: (Boolean) -> Unit,
    onToggleSongbook: (Boolean) -> Unit,
    onToggleGuidelines: (Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.czColors.background),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item("source") {
            SourceTemplateCard(source)
        }
        if (validationErrors.isNotEmpty()) {
            item("errors") {
                ValidationCard(validationErrors)
            }
        }
        item("new-camp") {
            Surface(
                color = MaterialTheme.czColors.surface,
                shape = RoundedCornerShape(CzRadius.xl),
            ) {
                Column(
                    modifier = Modifier.padding(CzSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    TemplateSectionHeader(
                        title = stringResource(R.string.camping_template_new_camp),
                        icon = Icons.Filled.CalendarMonth,
                    )
                    OutlinedTextField(
                        value = form.title,
                        onValueChange = onTitleChange,
                        label = { Text(stringResource(R.string.camping_template_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DateActionRow(
                        label = stringResource(R.string.camping_editor_date_start),
                        date = form.startDate,
                        onDateSelected = onStartDateChange,
                    )
                    DateActionRow(
                        label = stringResource(R.string.camping_editor_date_end),
                        date = form.endDate,
                        onDateSelected = onEndDateChange,
                    )
                    RegistrationStatusSelector(
                        selected = form.registrationStatus,
                        onSelected = onRegistrationStatusChange,
                    )
                }
            }
        }
        item("copy") {
            Surface(
                color = MaterialTheme.czColors.surface,
                shape = RoundedCornerShape(CzRadius.xl),
            ) {
                Column(
                    modifier = Modifier.padding(CzSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    TemplateSectionHeader(
                        title = stringResource(R.string.camping_template_copy_from),
                        icon = Icons.Filled.ListAlt,
                    )
                    TemplateCopyRow(
                        title = stringResource(R.string.camping_template_copy_schedule),
                        subtitle = stringResource(R.string.camping_template_copy_schedule_subtitle),
                        enabledSubtitle = stringResource(R.string.camping_template_copy_disabled),
                        icon = Icons.Filled.Schedule,
                        checked = form.options.includeSchedule,
                        enabled = availableCopies.canCopySchedule,
                        onCheckedChange = onToggleSchedule,
                    )
                    TemplateCopyRow(
                        title = stringResource(R.string.camping_template_copy_teams),
                        subtitle = stringResource(R.string.camping_template_copy_teams_subtitle),
                        enabledSubtitle = stringResource(R.string.camping_template_copy_disabled),
                        icon = Icons.Filled.Groups,
                        checked = form.options.includeTeams,
                        enabled = availableCopies.canCopyTeams,
                        onCheckedChange = onToggleTeams,
                    )
                    TemplateCopyRow(
                        title = stringResource(R.string.camping_template_copy_songbook),
                        subtitle = stringResource(R.string.camping_template_copy_songbook_subtitle),
                        enabledSubtitle = stringResource(R.string.camping_template_copy_disabled),
                        icon = Icons.Filled.LibraryMusic,
                        checked = form.options.includeSongbook,
                        enabled = availableCopies.canCopySongbook,
                        onCheckedChange = onToggleSongbook,
                    )
                    TemplateCopyRow(
                        title = stringResource(R.string.camping_template_copy_guidelines),
                        subtitle = stringResource(R.string.camping_template_copy_guidelines_subtitle),
                        enabledSubtitle = stringResource(R.string.camping_template_copy_disabled),
                        icon = Icons.Filled.CheckCircle,
                        checked = form.options.includeGuidelines,
                        enabled = availableCopies.canCopyGuidelines,
                        onCheckedChange = onToggleGuidelines,
                    )
                }
            }
        }
        item("save") {
            CzButton(
                text = if (isSaving) {
                    stringResource(R.string.camping_template_creating)
                } else {
                    stringResource(R.string.camping_template_create)
                },
                onClick = onCreate,
                enabled = !isSaving,
                variant = CzButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SourceTemplateCard(source: Camping) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.camping_template_source),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = source.title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.location,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ValidationCard(errors: List<CampingTemplateCloneValidationError>) {
    Surface(
        color = MaterialTheme.czColors.error.copy(alpha = 0.10f),
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.error,
                )
                Text(
                    text = stringResource(R.string.camping_template_fix_errors),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            errors.forEach { error ->
                Text(
                    text = error.message(),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TemplateSectionHeader(title: String, icon: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DateActionRow(
    label: String,
    date: Date,
    onDateSelected: (Date) -> Unit,
) {
    val context = LocalContext.current
    val formatted = remember(date) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(date)
    }
    val calendar = remember(date) {
        Calendar.getInstance().apply { time = date }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = formatted,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        TextButton(
            onClick = {
                AndroidDatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onDateSelected(
                            Calendar.getInstance().apply {
                                time = date
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                            }.time,
                        )
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
        ) {
            Text(stringResource(R.string.camping_template_change_date))
        }
    }
}

@Composable
private fun RegistrationStatusSelector(
    selected: CampingRegistrationStatus,
    onSelected: (CampingRegistrationStatus) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Text(
            text = stringResource(R.string.camping_editor_reg_status),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            listOf(CampingRegistrationStatus.Closed, CampingRegistrationStatus.Open).forEach { status ->
                FilterChip(
                    selected = selected == status,
                    onClick = { onSelected(status) },
                    label = { Text(status.label()) },
                )
            }
        }
    }
}

@Composable
private fun TemplateCopyRow(
    title: String,
    subtitle: String,
    enabledSubtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.czColors.ember else MaterialTheme.czColors.textSecondary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (enabled) subtitle else enabledSubtitle,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun CampingRegistrationStatus.label(): String = when (this) {
    CampingRegistrationStatus.Open -> stringResource(R.string.camping_status_short_open)
    CampingRegistrationStatus.Closed -> stringResource(R.string.camping_status_short_closed)
    CampingRegistrationStatus.Cancelled -> stringResource(R.string.camping_status_cancelled)
}

@Composable
private fun CampingTemplateCloneValidationError.message(): String = when (this) {
    CampingTemplateCloneValidationError.TitleRequired ->
        stringResource(R.string.camping_template_error_title_required)
    CampingTemplateCloneValidationError.EndDateBeforeStartDate ->
        stringResource(R.string.camping_template_error_end_after_start)
    CampingTemplateCloneValidationError.ContentRequired ->
        stringResource(R.string.camping_template_error_content_required)
}

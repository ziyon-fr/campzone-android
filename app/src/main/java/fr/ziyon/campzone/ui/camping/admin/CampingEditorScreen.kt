package fr.ziyon.campzone.ui.camping.admin

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.CampingAgePrice
import fr.ziyon.campzone.data.model.CampingPriceItem
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingTransportationOption
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.ui.common.ChurchPickerBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import android.app.DatePickerDialog as AndroidDatePickerDialog

@Composable
fun CampingEditorRoute(
    campingId: String?,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampingAdminViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionEvaluator = remember { AppPermissionEvaluator() }
    val permissionUser = remember(authenticatedUser) {
        PermissionUser(
            role = authenticatedUser.role,
            userId = authenticatedUser.uid,
            church = authenticatedUser.church,
        )
    }
    val campingContext = remember(state.existingCamping) {
        state.existingCamping?.let {
            CampingPermissionContext(
                organizerLevelType = it.organizerLevel.type.wireValue,
                organizerLevelValue = it.organizerLevel.value,
                createdByUid = it.createdByUid,
            )
        }
    }
    val proposedOrganizerLevel = state.form.organizerLevel
    val proposedCampingContext = remember(proposedOrganizerLevel) {
        CampingPermissionContext(
            organizerLevelType = proposedOrganizerLevel.type.wireValue,
            organizerLevelValue = proposedOrganizerLevel.value,
        )
    }
    val canManage = if (campingId == null) {
        permissionEvaluator.canCreateAnyCamping(permissionUser)
    } else {
        permissionEvaluator.canEditCamping(permissionUser, campingContext)
    }
    val canSave = permissionEvaluator.canSaveCamping(
        user = permissionUser,
        currentCamping = campingContext,
        proposedCamping = proposedCampingContext,
    )
    val canDelete = campingId != null &&
        (permissionUser.role.isAdmin || state.existingCamping?.createdByUid == authenticatedUser.uid)
    val canCancel = campingId != null &&
        permissionEvaluator.canCancelCamping(permissionUser, campingContext)

    LaunchedEffect(campingId) {
        val defaultChurch = if (campingId == null
            && permissionEvaluator.canCreateCamping(
                permissionUser,
                CampingPermissionContext("church", authenticatedUser.church),
            )
            && !permissionUser.role.isAdmin
        ) authenticatedUser.church else null
        viewModel.prepareEditor(campingId, defaultChurch)
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    CampingEditorScreen(
        campingId = campingId,
        state = state,
        canManage = canManage,
        canSave = canSave,
        canCancel = canCancel,
        canDelete = canDelete,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onFormUpdate = viewModel::updateForm,
        onSave = { if (canSave) viewModel.saveEditorForm(campingId) { onBack() } },
        onCancel = { campingId?.let { viewModel.cancelCamping(it) { onBack() } } },
        onDelete = { campingId?.let { viewModel.deleteCamping(it) { onBack() } } },
        onUploadLogo = viewModel::uploadLogo,
        onRemoveLogo = viewModel::removeLogo,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingEditorScreen(
    campingId: String?,
    state: CampingAdminUiState,
    canManage: Boolean,
    canSave: Boolean,
    canCancel: Boolean,
    canDelete: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onFormUpdate: (CampingEditorForm) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onUploadLogo: (ByteArray, String, String, String?) -> Unit,
    onRemoveLogo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val form = state.form
    val isCreating = campingId == null
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showChurchPicker by remember { mutableStateOf(false) }
    var editingPriceItem by remember { mutableStateOf<CampingPriceItem?>(null) }
    var editingAgePrice by remember { mutableStateOf<CampingAgePrice?>(null) }
    var editingTransport by remember { mutableStateOf<CampingTransportationOption?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            onUploadLogo(bytes, mimeType, ext, campingId)
        }
    }

    if (showStartDatePicker) {
        val cal = Calendar.getInstance().apply { time = form.startDate }
        AndroidDatePickerDialog(
            context,
            { _, y, m, d ->
                val newDate = Calendar.getInstance().apply { set(y, m, d) }.time
                onFormUpdate(form.copy(startDate = newDate))
                showStartDatePicker = false
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).also { it.setOnCancelListener { } }.show()
    }

    if (showEndDatePicker) {
        val cal = Calendar.getInstance().apply { time = form.endDate }
        AndroidDatePickerDialog(
            context,
            { _, y, m, d ->
                val newDate = Calendar.getInstance().apply { set(y, m, d) }.time
                onFormUpdate(form.copy(endDate = newDate))
                showEndDatePicker = false
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).also { it.setOnCancelListener { showEndDatePicker = false } }.show()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.czColors.error) },
            title = { Text(stringResource(R.string.camping_editor_delete_title)) },
            text = { Text(stringResource(R.string.camping_editor_delete_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.czColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.camping_editor_cancel_camping_title)) },
            text = { Text(stringResource(R.string.camping_editor_cancel_camping_message)) },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false; onCancel() }) {
                    Text(stringResource(R.string.camping_editor_cancel_camping_confirm), color = MaterialTheme.czColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showChurchPicker) {
        ChurchPickerBottomSheet(
            selectedChurch = form.organizerName,
            onSelectChurch = { onFormUpdate(form.copy(organizerName = it)); showChurchPicker = false },
            onDismiss = { showChurchPicker = false },
        )
    }

    CampingPriceItemEditorSheet(
        item = editingPriceItem,
        onSave = { saved ->
            val updated = form.priceItems.toMutableList()
            val idx = updated.indexOfFirst { it.id == saved.id }
            if (idx >= 0) updated[idx] = saved else updated += saved
            onFormUpdate(form.copy(priceItems = updated))
            editingPriceItem = null
        },
        onDismiss = { editingPriceItem = null },
    )

    CampingAgePriceEditorSheet(
        tier = editingAgePrice,
        currency = form.feeCurrency,
        onSave = { saved ->
            val updated = form.agePrices.toMutableList()
            val idx = updated.indexOfFirst { it.id == saved.id }
            if (idx >= 0) updated[idx] = saved else updated += saved
            onFormUpdate(form.copy(agePrices = updated.sortedBy { it.minAge }))
            editingAgePrice = null
        },
        onDismiss = { editingAgePrice = null },
    )

    CampingTransportationOptionEditorSheet(
        option = editingTransport,
        onSave = { saved ->
            val updated = form.transportationOptions.toMutableList()
            val idx = updated.indexOfFirst { it.id == saved.id }
            if (idx >= 0) updated[idx] = saved else updated += saved
            onFormUpdate(form.copy(transportationOptions = updated))
            editingTransport = null
        },
        onDismiss = { editingTransport = null },
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.czColors.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (isCreating) R.string.camping_editor_create_title else R.string.camping_editor_edit_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.czColors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.czColors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (state.isSaving || state.isCancelling || state.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = CzSpacing.md),
                            color = MaterialTheme.czColors.ember,
                            strokeWidth = 2.dp,
                        )
                    } else if (canSave) {
                        TextButton(onClick = onSave) {
                            Text(
                                text = stringResource(R.string.common_save),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.czColors.ember,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.czColors.background,
                ),
                windowInsets = WindowInsets(0.dp),
            )
        },
    ) { innerPadding ->
        if (state.isLoadingCamping) {
            CzLoadingView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                message = stringResource(R.string.camping_loading),
            )
            return@Scaffold
        }

        if (!canManage) {
            CzEmptyState(
                title = stringResource(R.string.camping_editor_restricted_title),
                message = stringResource(R.string.camping_editor_restricted_message),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = CzSpacing.xxxl),
        ) {
            // Validation errors
            if (state.validationErrors.isNotEmpty()) {
                item {
                    ValidationBanner(errors = state.validationErrors)
                }
            }

            // MARK: Basics
            item {
                EditorSection(
                    icon = "🏕",
                    title = stringResource(R.string.camping_editor_section_basics),
                ) {
                    CzTextField(
                        value = form.title,
                        onValueChange = { onFormUpdate(form.copy(title = it)) },
                        placeholder = stringResource(R.string.camping_editor_title_hint),
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.camping_editor_title_label),
                    )
                    Spacer(Modifier.height(CzSpacing.sm))
                    CzTextField(
                        value = form.description,
                        onValueChange = { onFormUpdate(form.copy(description = it)) },
                        placeholder = stringResource(R.string.camping_editor_description_hint),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        label = stringResource(R.string.camping_editor_description_label),
                    )
                }
            }

            // MARK: Logo
            item {
                EditorSection(
                    icon = "🖼",
                    title = stringResource(R.string.camping_editor_section_logo),
                ) {
                    LogoEditorRow(
                        logoUrl = form.logoUrl,
                        isUploading = state.isUploadingLogo,
                        onPickLogo = { logoLauncher.launch("image/*") },
                        onRemoveLogo = onRemoveLogo,
                    )
                }
            }

            // MARK: Location
            item {
                EditorSection(
                    icon = "📍",
                    title = stringResource(R.string.camping_editor_section_location),
                ) {
                    CampingLocationPickerSection(
                        location = form.location,
                        onLocationSelected = { name, lat, lng ->
                            onFormUpdate(form.copy(location = name, locationLatitude = lat, locationLongitude = lng))
                        },
                    )
                }
            }

            // MARK: Timing
            item {
                EditorSection(
                    icon = "📅",
                    title = stringResource(R.string.camping_editor_section_timing),
                ) {
                    DateRow(
                        label = stringResource(R.string.camping_editor_date_start),
                        date = form.startDate,
                        onClick = { showStartDatePicker = true },
                    )
                    val durationDays = durationDays(form.startDate, form.endDate)
                    if (durationDays > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.czColors.amber)
                            DurationBadge(days = durationDays)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.czColors.amber)
                        }
                    }
                    DateRow(
                        label = stringResource(R.string.camping_editor_date_end),
                        date = form.endDate,
                        onClick = { },
                    )
                }
            }

            // MARK: Organization
            item {
                EditorSection(
                    icon = "🏛",
                    title = stringResource(R.string.camping_editor_section_organization),
                ) {
                    OrganizerLevelRow(
                        selected = form.organizerType,
                        onSelect = { type ->
                            onFormUpdate(form.copy(organizerType = type, organizerName = ""))
                        },
                    )
                    Spacer(Modifier.height(CzSpacing.sm))
                    if (form.organizerType == OrganizerType.Church) {
                        ChurchSelectorRow(
                            value = form.organizerName,
                            onClick = { showChurchPicker = true },
                        )
                    } else {
                        CzTextField(
                            value = form.organizerName,
                            label = "label",
                            modifier = Modifier.fillMaxWidth(),
                            onValueChange = { onFormUpdate(form.copy(organizerName = it)) },
                            placeholder = organizerNamePlaceholder(form.organizerType)
                        )
                    }
                    Spacer(Modifier.height(CzSpacing.sm))
                    RegistrationStatusRow(
                        selected = form.registrationStatus,
                        onSelect = { onFormUpdate(form.copy(registrationStatus = it)) },
                    )
                    Spacer(Modifier.height(CzSpacing.sm))
                    CapacityRow(
                        capacityText = form.participantCapacityText,
                        onCapacityChange = { onFormUpdate(form.copy(participantCapacityText = it)) },
                    )
                }
            }

            // MARK: Fees
            item {
                EditorSection(
                    icon = "💳",
                    title = stringResource(R.string.camping_editor_section_fees),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CzTextField(
                            value = form.registrationFeeText,
                            onValueChange = { onFormUpdate(form.copy(registrationFeeText = it)) },
                            placeholder = "0.00",
                            label = stringResource(R.string.camping_editor_fee_amount),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        CzTextField(
                            value = form.feeCurrency,
                            onValueChange = { onFormUpdate(form.copy(feeCurrency = it.uppercase())) },
                            placeholder = "EUR",
                            label = stringResource(R.string.camping_editor_fee_currency),
                            modifier = Modifier.width(88.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Characters,
                            ),
                        )
                    }

                    if (form.agePrices.isNotEmpty()) {
                        Spacer(Modifier.height(CzSpacing.sm))
                        EditorLabel(stringResource(R.string.camping_editor_age_pricing))
                    }
                    form.agePrices.forEach { tier ->
                        AgePriceSummaryRow(
                            tier = tier,
                            currency = form.feeCurrency,
                            onEdit = { editingAgePrice = tier },
                            onDelete = { onFormUpdate(form.copy(agePrices = form.agePrices - tier)) },
                        )
                    }
                    TextButton(
                        onClick = {
                            editingAgePrice = CampingAgePrice(
                                id = UUID.randomUUID().toString(),
                                label = "",
                                minAge = 0,
                                amountCents = 0,
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(CzSpacing.xs))
                        Text(stringResource(R.string.camping_editor_add_age_price), color = MaterialTheme.czColors.ember)
                    }

                    if (form.priceItems.isNotEmpty()) {
                        Spacer(Modifier.height(CzSpacing.xs))
                        HorizontalDivider(color = MaterialTheme.czColors.divider)
                        Spacer(Modifier.height(CzSpacing.xs))
                    }
                    form.priceItems.forEach { item ->
                        PriceItemSummaryRow(
                            item = item,
                            onEdit = { editingPriceItem = item },
                            onDelete = { onFormUpdate(form.copy(priceItems = form.priceItems - item)) },
                        )
                    }
                    TextButton(
                        onClick = {
                            editingPriceItem = CampingPriceItem(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                details = "",
                                amountCents = 0,
                                currency = form.feeCurrency.ifBlank { "EUR" },
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(CzSpacing.xs))
                        Text(stringResource(R.string.camping_editor_add_price_item), color = MaterialTheme.czColors.ember)
                    }
                }
            }

            // MARK: Transportation
            item {
                EditorSection(
                    icon = "🚌",
                    title = stringResource(R.string.camping_editor_section_transport),
                ) {
                    form.transportationOptions.forEach { option ->
                        TransportOptionSummaryRow(
                            option = option,
                            onEdit = { editingTransport = option },
                            onDelete = { onFormUpdate(form.copy(transportationOptions = form.transportationOptions - option)) },
                        )
                    }
                    TextButton(
                        onClick = {
                            editingTransport = CampingTransportationOption(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                mode = fr.ziyon.campzone.data.model.TransportationMode.Coach,
                                details = "",
                                currency = form.feeCurrency.ifBlank { "EUR" },
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(CzSpacing.xs))
                        Text(stringResource(R.string.camping_editor_add_transport), color = MaterialTheme.czColors.ember)
                    }
                }
            }

            // MARK: Danger zone (cancel / delete)
            if (campingId != null && (canCancel || canDelete)) {
                item {
                    EditorSection(
                        icon = "⚠️",
                        title = stringResource(R.string.camping_editor_section_danger),
                    ) {
                        if (canCancel) {
                            TextButton(onClick = { showCancelDialog = true }) {
                                Text(
                                    text = stringResource(R.string.camping_editor_cancel_camping_action),
                                    color = MaterialTheme.czColors.warning,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (canDelete) {
                            TextButton(onClick = { showDeleteDialog = true }) {
                                Text(
                                    text = stringResource(R.string.camping_editor_delete_action),
                                    color = MaterialTheme.czColors.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Section container

@Composable
internal fun EditorSection(
    icon: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.xl, vertical = CzSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Text(icon, style = MaterialTheme.typography.labelMedium)
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.czColors.textSecondary,
            )
        }
        Spacer(Modifier.height(CzSpacing.sm))
        Surface(
            shape = RoundedCornerShape(CzRadius.lg),
            color = MaterialTheme.czColors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun EditorLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.czColors.textSecondary,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

// MARK: - Validation banner

@Composable
private fun ValidationBanner(errors: List<String>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CzSpacing.xl),
        shape = RoundedCornerShape(CzRadius.md),
        color = MaterialTheme.czColors.error.copy(alpha = 0.08f),
    ) {
        Column(modifier = Modifier.padding(CzSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.czColors.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(CzSpacing.xs))
                Text(
                    text = stringResource(R.string.camping_editor_fix_errors),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.czColors.error,
                )
            }
            errors.forEach { error ->
                Text(
                    text = "• $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.czColors.error,
                    modifier = Modifier.padding(top = CzSpacing.xs),
                )
            }
        }
    }
}

// MARK: - Logo editor row

@Composable
private fun LogoEditorRow(
    logoUrl: String?,
    isUploading: Boolean,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(CzRadius.md))
                .background(colors.background),
            contentAlignment = Alignment.Center,
        ) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = stringResource(R.string.camping_editor_logo_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.Festival, contentDescription = null, tint = colors.ember, modifier = Modifier.size(28.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
            Text(
                text = if (logoUrl != null) stringResource(R.string.camping_editor_logo_selected) else stringResource(R.string.camping_editor_logo_none),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.camping_editor_logo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
    Spacer(Modifier.height(CzSpacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        if (isUploading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.czColors.ember,
                strokeWidth = 2.dp,
            )
        } else {
            TextButton(onClick = onPickLogo) {
                Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(CzSpacing.xs))
                Text(
                    text = if (logoUrl == null) stringResource(R.string.camping_editor_logo_choose) else stringResource(R.string.camping_editor_logo_replace),
                    color = MaterialTheme.czColors.ember,
                )
            }
            if (logoUrl != null) {
                TextButton(onClick = onRemoveLogo) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.czColors.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(CzSpacing.xs))
                    Text(stringResource(R.string.common_remove), color = MaterialTheme.czColors.error)
                }
            }
        }
    }
}

// MARK: - Date row

@Composable
private fun DateRow(label: String, date: Date, onClick: () -> Unit) {
    val formatted = remember(date) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(date)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = CzSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.czColors.textSecondary)
        Text(
            formatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.czColors.ember,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun DurationBadge(days: Int) {
    Surface(
        shape = RoundedCornerShape(CzRadius.full),
        color = MaterialTheme.czColors.amber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.czColors.amber.copy(alpha = 0.25f)),
    ) {
        Text(
            text = "🌙 $days ${if (days == 1) "day" else "days"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.czColors.amber,
            modifier = Modifier.padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
        )
    }
}

// MARK: - Organizer level row

@Composable
private fun OrganizerLevelRow(
    selected: OrganizerType,
    onSelect: (OrganizerType) -> Unit,
) {
    Column {
        EditorLabel(stringResource(R.string.camping_editor_organizer_level))
        Spacer(Modifier.height(CzSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            OrganizerType.entries.forEach { type ->
                LevelChip(
                    label = type.displayLabel(),
                    isSelected = selected == type,
                    onTap = { onSelect(type) },
                )
            }
        }
    }
}

@Composable
private fun LevelChip(label: String, isSelected: Boolean, onTap: () -> Unit) {
    val colors = MaterialTheme.czColors
    Surface(
        onClick = onTap,
        shape = RoundedCornerShape(CzRadius.full),
        color = if (isSelected) colors.ember else colors.surface,
        border = BorderStroke(1.dp, if (isSelected) colors.ember else colors.divider),
    ) {
        Text(
            text = label,
            style = if (isSelected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            color = if (isSelected) colors.onPrimary else colors.textSecondary,
            modifier = Modifier.padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        )
    }
}

// MARK: - Church selector row

@Composable
private fun ChurchSelectorRow(value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = CzSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.camping_editor_church_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.czColors.textSecondary)
        Text(
            text = value.ifBlank { stringResource(R.string.camping_editor_church_hint) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isBlank()) MaterialTheme.czColors.textSecondary else MaterialTheme.czColors.ember,
            textDecoration = if (value.isBlank()) null else TextDecoration.Underline,
        )
    }
}

// MARK: - Registration status row

@Composable
private fun RegistrationStatusRow(
    selected: CampingRegistrationStatus,
    onSelect: (CampingRegistrationStatus) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.camping_editor_reg_status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.czColors.textSecondary,
        )
        Spacer(Modifier.height(CzSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
            CampingRegistrationStatus.entries.forEach { status ->
                StatusChip(
                    status = status,
                    isSelected = selected == status,
                    onTap = { onSelect(status) },
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    status: CampingRegistrationStatus,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val statusColor = when (status) {
        CampingRegistrationStatus.Open -> colors.success
        CampingRegistrationStatus.Closed -> colors.amber
        CampingRegistrationStatus.Cancelled -> colors.error
    }
    Surface(
        onClick = onTap,
        shape = RoundedCornerShape(CzRadius.full),
        color = if (isSelected) statusColor.copy(alpha = 0.15f) else colors.surface,
        border = BorderStroke(1.dp, if (isSelected) statusColor else colors.divider),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            modifier = Modifier.padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(statusColor, shape = CircleShape),
            )
            Text(
                text = status.displayLabel(),
                style = if (isSelected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                color = if (isSelected) statusColor else colors.textSecondary,
            )
        }
    }
}

// MARK: - Capacity row

@Composable
private fun CapacityRow(capacityText: String, onCapacityChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.camping_editor_capacity), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.czColors.textSecondary)
            Text(
                text = capacityText.ifBlank { stringResource(R.string.camping_editor_capacity_unlimited) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.czColors.textPrimary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            CapacityStepButton(icon = "−") {
                val current = capacityText.toIntOrNull() ?: 0
                onCapacityChange(if (current <= 1) "" else "${current - 1}")
            }
            CzTextField(
                value = capacityText,
                onValueChange = onCapacityChange,
                label = "∞",
                modifier = Modifier.width(64.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            CapacityStepButton(icon = "+") {
                val current = capacityText.toIntOrNull() ?: 0
                onCapacityChange("${current + 1}")
            }
        }
    }
}

@Composable
private fun CapacityStepButton(icon: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CzRadius.full),
        color = MaterialTheme.czColors.ember.copy(alpha = 0.12f),
        modifier = Modifier.size(30.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(icon, color = MaterialTheme.czColors.ember, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// MARK: - Summary rows for fees and transport

@Composable
private fun AgePriceSummaryRow(
    tier: CampingAgePrice,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tier.label.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.czColors.textPrimary)
            Text(
                text = "${tier.minAge}${tier.maxAge?.let { "–$it" } ?: "+"} yrs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.czColors.textSecondary,
            )
        }
        Text(
            text = "${"%.2f".format(tier.amountCents / 100.0)} ${currency.uppercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.czColors.textPrimary,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.czColors.error, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PriceItemSummaryRow(
    item: CampingPriceItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.czColors.textPrimary)
            if (item.isMandatory) {
                Text(stringResource(R.string.camping_editor_price_item_required), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.czColors.ember)
            }
        }
        Text(
            text = "${"%.2f".format(item.amountCents / 100.0)} ${item.currency.uppercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.czColors.textPrimary,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.czColors.error, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TransportOptionSummaryRow(
    option: CampingTransportationOption,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(option.name.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.czColors.textPrimary)
            Text(
                text = option.details.ifBlank { option.mode.wireValue },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.czColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        option.feeCents?.let { fee ->
            if (fee > 0) {
                Text(
                    text = "${"%.2f".format(fee / 100.0)} ${option.currency.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.czColors.textPrimary,
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.czColors.error, modifier = Modifier.size(16.dp))
        }
    }
}

// MARK: - Helpers

private fun durationDays(start: Date, end: Date): Int {
    val diff = end.time - start.time
    if (diff <= 0) return 0
    return TimeUnit.MILLISECONDS.toDays(diff).toInt()
}

@Composable
private fun organizerNamePlaceholder(type: OrganizerType): String = when (type) {
    OrganizerType.Church -> stringResource(R.string.camping_editor_organizer_church_hint)
    OrganizerType.Regional -> stringResource(R.string.camping_editor_organizer_regional_hint)
    OrganizerType.International -> stringResource(R.string.camping_editor_organizer_intl_hint)
    OrganizerType.Custom -> stringResource(R.string.camping_editor_organizer_custom_hint)
}

private fun OrganizerType.displayLabel(): String = when (this) {
    OrganizerType.Church -> "Church"
    OrganizerType.Regional -> "Regional"
    OrganizerType.International -> "International"
    OrganizerType.Custom -> "Custom"
}

private fun CampingRegistrationStatus.displayLabel(): String = when (this) {
    CampingRegistrationStatus.Open -> "Open"
    CampingRegistrationStatus.Closed -> "Closed"
    CampingRegistrationStatus.Cancelled -> "Cancelled"
}

// MARK: - Preview

@Preview(showBackground = true)
@Composable
private fun CampingEditorScreenPreview() {
    CampzoneTheme {
        CampingEditorScreen(
            campingId = null,
            state = CampingAdminUiState(),
            canManage = true,
            canSave = true,
            canCancel = false,
            canDelete = false,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onFormUpdate = {},
            onSave = {},
            onCancel = {},
            onDelete = {},
            onUploadLogo = { _, _, _, _ -> },
            onRemoveLogo = {},
        )
    }
}

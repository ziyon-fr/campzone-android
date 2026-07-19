package fr.ziyon.campzone.ui.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingStaffRole
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.StaffCapability
import fr.ziyon.campzone.data.model.StaffRoleKind
import fr.ziyon.campzone.data.model.StaffRoleMember
import fr.ziyon.campzone.ui.camping.registrations.permissionContext

@Composable
fun StaffRolesRoute(
    campingId: String,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StaffRoleViewModel = hiltViewModel(),
) {
    if (camping == null) {
        CzLoadingView(modifier = modifier.fillMaxSize(), message = stringResource(R.string.staff_roles_loading))
        return
    }
    val canManage = canManageStaffRoles(camping, authenticatedUser)
    val state by viewModel.uiState.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid, canManage) {
        viewModel.start(campingId, authenticatedUser.uid, canManage)
    }

    StaffRolesScreen(
        state = state,
        canManage = canManage,
        operationError = operationError,
        operationMessage = operationMessage,
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        onCreate = { onOpenEditor(null) },
        onRefresh = { viewModel.refresh(campingId, authenticatedUser.uid, canManage) },
        onClearError = viewModel::clearOperationError,
        onClearMessage = viewModel::clearOperationMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffRolesScreen(
    state: StaffRolesUiState,
    canManage: Boolean,
    operationError: String?,
    operationMessage: StaffRoleOperationMessage?,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.staff_roles_operation_error)
    LaunchedEffect(operationError) {
        if (operationError != null) {
            snackbar.showSnackbar(genericError)
            onClearError()
        }
    }
    val savedMessage = stringResource(R.string.staff_role_saved_message)
    val deletedMessage = stringResource(R.string.staff_role_deleted_message)
    LaunchedEffect(operationMessage) {
        operationMessage?.let { message ->
            snackbar.showSnackbar(
                when (message) {
                    StaffRoleOperationMessage.Saved -> savedMessage
                    StaffRoleOperationMessage.Deleted -> deletedMessage
                },
            )
            onClearMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.staff_roles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = onCreate) {
                            Icon(Icons.Outlined.Add, stringResource(R.string.staff_role_create))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        when (state) {
            StaffRolesUiState.Loading -> CzLoadingView(
                modifier = Modifier.padding(padding).fillMaxSize(),
                message = stringResource(R.string.staff_roles_loading),
            )
            StaffRolesUiState.Empty -> CzEmptyState(
                modifier = Modifier.padding(padding).fillMaxSize(),
                title = stringResource(R.string.staff_roles_empty_title),
                message = stringResource(
                    if (canManage) R.string.staff_roles_empty_manager_message
                    else R.string.staff_roles_empty_member_message,
                ),
                icon = { Icon(Icons.Outlined.Groups, contentDescription = null) },
                action = if (canManage) {
                    { Button(onClick = onCreate) { Text(stringResource(R.string.staff_role_create)) } }
                } else null,
            )
            is StaffRolesUiState.Error -> CzErrorState(
                modifier = Modifier.padding(padding).fillMaxSize(),
                title = stringResource(R.string.staff_roles_load_error_title),
                message = stringResource(R.string.staff_roles_load_error_message),
                onRetry = onRefresh,
                retryLabel = stringResource(R.string.common_retry),
            )
            is StaffRolesUiState.Loaded -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.staff_roles_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = CzSpacing.sm),
                    )
                }
                items(state.roles, key = { it.id }) { role ->
                    StaffRoleCard(role = role, onClick = { onOpenDetail(role.id) })
                }
            }
        }
    }
}

@Composable
private fun StaffRoleCard(role: CampingStaffRole, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    val roleColor = role.colorHex.toComposeColor() ?: colors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(colors.surface)
            .border(1.dp, colors.divider.copy(alpha = 0.7f), RoundedCornerShape(CzRadius.lg))
            .clickable(onClick = onClick)
            .padding(CzSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        RoleIcon(role.kind, roleColor, Modifier.size(44.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = role.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${staffRoleKindLabel(role.kind)} · ${pluralStringResource(R.plurals.staff_role_member_count, role.members.size, role.members.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (role.chatEnabled) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.Lock,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
    }
}

@Composable
fun StaffRoleDetailRoute(
    campingId: String,
    staffRoleId: String,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StaffRoleViewModel = hiltViewModel(),
) {
    if (camping == null) {
        CzLoadingView(modifier = modifier.fillMaxSize(), message = stringResource(R.string.staff_roles_loading))
        return
    }
    val canManage = canManageStaffRoles(camping, authenticatedUser)
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(campingId, authenticatedUser.uid, canManage) {
        viewModel.start(campingId, authenticatedUser.uid, canManage)
    }
    val role = when (state) {
        is StaffRolesUiState.Loaded -> viewModel.role(staffRoleId)
        else -> null
    }
    when {
        state is StaffRolesUiState.Loading -> CzLoadingView(
            modifier = modifier.fillMaxSize(),
            message = stringResource(R.string.staff_roles_loading),
        )
        role == null -> CzErrorState(
            modifier = modifier.fillMaxSize(),
            title = stringResource(R.string.staff_role_not_found_title),
            message = stringResource(R.string.staff_role_not_found_message),
            onRetry = onBack,
            retryLabel = stringResource(R.string.common_back),
        )
        !canManage && !role.containsUser(authenticatedUser.uid) -> CzErrorState(
            modifier = modifier.fillMaxSize(),
            title = stringResource(R.string.staff_role_restricted_title),
            message = stringResource(R.string.staff_role_restricted_message),
            onRetry = onBack,
            retryLabel = stringResource(R.string.common_back),
        )
        else -> StaffRoleDetailScreen(
            role = role,
            canManage = canManage,
            currentUserId = authenticatedUser.uid,
            onBack = onBack,
            onEdit = onEdit,
            onOpenChat = onOpenChat,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffRoleDetailScreen(
    role: CampingStaffRole,
    canManage: Boolean,
    currentUserId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val roleColor = role.colorHex.toComposeColor() ?: colors.primary
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(role.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                RoleIcon(role.kind, roleColor, Modifier.size(56.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(staffRoleKindLabel(role.kind), style = MaterialTheme.typography.labelLarge, color = roleColor)
                    Text(
                        pluralStringResource(R.plurals.staff_role_member_count, role.members.size, role.members.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            if (role.description.isNotBlank()) {
                StaffRoleSection(stringResource(R.string.staff_role_about)) {
                    Text(role.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (role.chatEnabled) {
                OutlinedButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Spacer(Modifier.size(CzSpacing.sm))
                    Text(stringResource(R.string.staff_role_open_private_chat))
                }
            }
            if (role.capabilities.isNotEmpty()) {
                StaffRoleSection(stringResource(R.string.staff_role_responsibilities)) {
                    role.capabilities.forEach { capability ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = CzSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, tint = roleColor, modifier = Modifier.size(18.dp))
                            Text(staffCapabilityLabel(capability), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            StaffRoleSection(stringResource(R.string.staff_role_members)) {
                if (role.members.isEmpty()) {
                    Text(stringResource(R.string.staff_role_no_members), color = colors.textSecondary)
                } else {
                    role.members.forEachIndexed { index, member ->
                        StaffRoleMemberRow(member, isCurrentUser = member.userId == currentUserId)
                        if (index != role.members.lastIndex) HorizontalDivider(color = colors.divider.copy(alpha = 0.65f))
                    }
                }
            }
            Spacer(Modifier.height(CzSpacing.xl))
        }
    }
}

@Composable
fun StaffRoleEditorRoute(
    campingId: String,
    staffRoleId: String?,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    approvedAttendees: List<CampingAttendee>,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StaffRoleViewModel = hiltViewModel(),
) {
    if (camping == null) {
        CzLoadingView(modifier = modifier.fillMaxSize(), message = stringResource(R.string.staff_roles_loading))
        return
    }
    val canManage = canManageStaffRoles(camping, authenticatedUser)
    val state by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    var prepared by rememberSaveable(staffRoleId) { mutableStateOf(false) }

    LaunchedEffect(campingId, authenticatedUser.uid, canManage) {
        viewModel.start(campingId, authenticatedUser.uid, canManage)
    }
    val editingRole = staffRoleId?.let { id ->
        if (state is StaffRolesUiState.Loaded) viewModel.role(id) else null
    }
    LaunchedEffect(staffRoleId, editingRole) {
        if (!prepared && staffRoleId == null) {
            viewModel.prepareNew()
            prepared = true
        } else if (!prepared && editingRole != null) {
            viewModel.prepareEdit(editingRole)
            prepared = true
        }
    }

    when {
        !canManage -> CzErrorState(
            modifier = modifier.fillMaxSize(),
            title = stringResource(R.string.staff_role_restricted_title),
            message = stringResource(R.string.staff_role_manage_restricted_message),
            onRetry = onBack,
            retryLabel = stringResource(R.string.common_back),
        )
        staffRoleId != null && !prepared && state is StaffRolesUiState.Loading -> CzLoadingView(
            modifier = modifier.fillMaxSize(),
            message = stringResource(R.string.staff_roles_loading),
        )
        staffRoleId != null && !prepared && editingRole == null -> CzErrorState(
            modifier = modifier.fillMaxSize(),
            title = stringResource(R.string.staff_role_not_found_title),
            message = stringResource(R.string.staff_role_not_found_message),
            onRetry = onBack,
            retryLabel = stringResource(R.string.common_back),
        )
        else -> StaffRoleEditorScreen(
            form = form,
            isEditing = staffRoleId != null,
            isSaving = isSaving,
            operationError = operationError,
            attendees = approvedAttendees.filter {
                it.registrationStatus == RegistrationApprovalStatus.Approved
            },
            onBack = onBack,
            onUpdateForm = viewModel::updateForm,
            onSetMember = viewModel::setMember,
            onUpdateMemberTitle = viewModel::updateMemberTitle,
            onSave = {
                viewModel.save(campingId, authenticatedUser.uid) { saved -> onSaved(saved.id) }
            },
            onDelete = {
                viewModel.delete(form.id, campingId, onDeleted)
            },
            onClearError = viewModel::clearOperationError,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StaffRoleEditorScreen(
    form: StaffRoleForm,
    isEditing: Boolean,
    isSaving: Boolean,
    operationError: String?,
    attendees: List<CampingAttendee>,
    onBack: () -> Unit,
    onUpdateForm: ((StaffRoleForm) -> StaffRoleForm) -> Unit,
    onSetMember: (CampingAttendee, Boolean) -> Unit,
    onUpdateMemberTitle: (String, String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.staff_roles_operation_error)
    val selectColorDescription = stringResource(R.string.staff_role_select_color)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var memberQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(operationError) {
        if (operationError != null) {
            snackbar.showSnackbar(genericError)
            onClearError()
        }
    }
    val filteredAttendees = remember(attendees, memberQuery) {
        attendees.sortedBy { it.displayName.lowercase() }.filter {
            memberQuery.isBlank() || it.displayName.contains(memberQuery, ignoreCase = true) ||
                it.church.contains(memberQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(if (isEditing) R.string.staff_role_edit_title else R.string.staff_role_create_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = form.isValid && !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.common_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
        ) {
            StaffRoleSection(stringResource(R.string.staff_role_details)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { value -> onUpdateForm { it.copy(name = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.staff_role_name)) },
                    singleLine = true,
                    isError = form.name.isBlank(),
                )
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { value -> onUpdateForm { it.copy(description = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.staff_role_description)) },
                    minLines = 3,
                    maxLines = 5,
                )
                Text(stringResource(R.string.staff_role_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    StaffRoleKind.entries.forEach { kind ->
                        FilterChip(
                            selected = form.kind == kind,
                            onClick = {
                                onUpdateForm {
                                    it.copy(kind = kind, symbolName = kind.defaultSymbol())
                                }
                            },
                            label = { Text(staffRoleKindLabel(kind)) },
                            leadingIcon = {
                                Icon(staffRoleKindIcon(kind), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }

            StaffRoleSection(stringResource(R.string.staff_role_appearance)) {
                Text(stringResource(R.string.staff_role_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CzSpacing.md), verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                    staffRoleColors.forEach { hex ->
                        val color = hex.toComposeColor() ?: colors.primary
                        val isSelected = form.colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) colors.textPrimary
                                    else colors.divider,
                                    shape = CircleShape,
                                )
                                .clickable { onUpdateForm { it.copy(colorHex = hex) } }
                                .semantics {
                                    contentDescription = selectColorDescription
                                    selected = isSelected
                                },
                        )
                    }
                }
            }

            StaffRoleSection(stringResource(R.string.staff_role_responsibilities)) {
                Text(
                    stringResource(R.string.staff_role_responsibilities_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                StaffCapability.entries.forEach { capability ->
                    val selected = capability in form.capabilities
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onUpdateForm {
                                it.copy(
                                    capabilities = if (selected) it.capabilities - capability
                                    else (it.capabilities + capability).distinct(),
                                )
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Text(staffCapabilityLabel(capability), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            StaffRoleSection(stringResource(R.string.staff_role_members)) {
                Text(
                    stringResource(R.string.staff_role_members_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                if (attendees.size > 8) {
                    OutlinedTextField(
                        value = memberQuery,
                        onValueChange = { memberQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.staff_role_search_members)) },
                        singleLine = true,
                    )
                }
                if (filteredAttendees.isEmpty()) {
                    Text(stringResource(R.string.staff_role_no_approved_members), color = colors.textSecondary)
                }
                filteredAttendees.forEach { attendee ->
                    val selectedMember = form.members.firstOrNull { it.userId == attendee.userId }
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CzSpacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSetMember(attendee, selectedMember == null)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        ) {
                            Checkbox(checked = selectedMember != null, onCheckedChange = null)
                            CzAvatar(
                                imageUrl = attendee.photoUrl,
                                contentDescription = attendee.displayName,
                                initials = attendee.displayName.initials(),
                                size = CzAvatarSize.Small,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(attendee.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                if (attendee.church.isNotBlank()) {
                                    Text(attendee.church, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                }
                            }
                        }
                        if (selectedMember != null) {
                            OutlinedTextField(
                                value = selectedMember.title,
                                onValueChange = { onUpdateMemberTitle(attendee.userId, it) },
                                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = CzSpacing.xs),
                                label = { Text(stringResource(R.string.staff_role_member_title)) },
                                placeholder = { Text(stringResource(R.string.staff_role_member_title_hint)) },
                                singleLine = true,
                            )
                        }
                    }
                }
            }

            StaffRoleSection(stringResource(R.string.staff_role_private_chat)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.staff_role_chat_enabled), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.staff_role_chat_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = form.chatEnabled,
                        onCheckedChange = { enabled -> onUpdateForm { it.copy(chatEnabled = enabled) } },
                    )
                }
            }

            Button(onClick = onSave, enabled = form.isValid && !isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.staff_role_save))
            }
            if (isEditing) {
                TextButton(onClick = { confirmDelete = true }, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = colors.error)
                    Spacer(Modifier.size(CzSpacing.sm))
                    Text(stringResource(R.string.staff_role_delete), color = colors.error)
                }
            }
            Spacer(Modifier.height(CzSpacing.xl))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.staff_role_delete_title)) },
            text = { Text(stringResource(R.string.staff_role_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.common_delete), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun StaffRoleSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun StaffRoleMemberRow(member: StaffRoleMember, isCurrentUser: Boolean) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        CzAvatar(
            imageUrl = member.photoUrl,
            contentDescription = member.displayName,
            initials = member.displayName.initials(),
            size = CzAvatarSize.Small,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(member.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (isCurrentUser) {
                    Text(stringResource(R.string.staff_role_you), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
            val subtitle = member.title.ifBlank { member.church }
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun RoleIcon(kind: StaffRoleKind, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(staffRoleKindIcon(kind), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
    }
}

private fun staffRoleKindIcon(kind: StaffRoleKind): ImageVector = when (kind) {
    StaffRoleKind.Games -> Icons.Outlined.SportsEsports
    StaffRoleKind.Kitchen -> Icons.Outlined.Restaurant
    StaffRoleKind.Cleaning -> Icons.Outlined.CleaningServices
    StaffRoleKind.Reception -> Icons.Outlined.HowToReg
    StaffRoleKind.Worship -> Icons.Outlined.MusicNote
    StaffRoleKind.Logistics -> Icons.Outlined.LocalShipping
    StaffRoleKind.Media -> Icons.Outlined.PhotoCamera
    StaffRoleKind.Safety -> Icons.Outlined.HealthAndSafety
    StaffRoleKind.Prayer -> Icons.Outlined.VolunteerActivism
    StaffRoleKind.Custom -> Icons.Outlined.Groups
}

private fun StaffRoleKind.defaultSymbol(): String = when (this) {
    StaffRoleKind.Games -> "gamecontroller.fill"
    StaffRoleKind.Kitchen -> "fork.knife"
    StaffRoleKind.Cleaning -> "sparkles"
    StaffRoleKind.Reception -> "person.crop.circle.badge.checkmark"
    StaffRoleKind.Worship -> "music.note"
    StaffRoleKind.Logistics -> "shippingbox.fill"
    StaffRoleKind.Media -> "camera.fill"
    StaffRoleKind.Safety -> "cross.case.fill"
    StaffRoleKind.Prayer -> "hands.sparkles.fill"
    StaffRoleKind.Custom -> CampingStaffRole.DEFAULT_SYMBOL
}

@Composable
private fun staffRoleKindLabel(kind: StaffRoleKind): String = stringResource(
    when (kind) {
        StaffRoleKind.Games -> R.string.staff_role_kind_games
        StaffRoleKind.Kitchen -> R.string.staff_role_kind_kitchen
        StaffRoleKind.Cleaning -> R.string.staff_role_kind_cleaning
        StaffRoleKind.Reception -> R.string.staff_role_kind_reception
        StaffRoleKind.Worship -> R.string.staff_role_kind_worship
        StaffRoleKind.Logistics -> R.string.staff_role_kind_logistics
        StaffRoleKind.Media -> R.string.staff_role_kind_media
        StaffRoleKind.Safety -> R.string.staff_role_kind_safety
        StaffRoleKind.Prayer -> R.string.staff_role_kind_prayer
        StaffRoleKind.Custom -> R.string.staff_role_kind_custom
    },
)

@Composable
private fun staffCapabilityLabel(capability: StaffCapability): String = stringResource(
    when (capability) {
        StaffCapability.ManageGames -> R.string.staff_capability_games
        StaffCapability.ManageFoodMenu -> R.string.staff_capability_food_menu
        StaffCapability.ManageSchedule -> R.string.staff_capability_schedule
        StaffCapability.ManageCheckIns -> R.string.staff_capability_check_ins
        StaffCapability.ManageAlbumMedia -> R.string.staff_capability_album
        StaffCapability.ManageAnnouncements -> R.string.staff_capability_announcements
        StaffCapability.ManageTransportation -> R.string.staff_capability_transportation
    },
)

private fun canManageStaffRoles(camping: Camping, user: AuthenticatedUser): Boolean =
    AppPermissionEvaluator().canManageStaffRoles(
        PermissionUser(user.role, user.uid, user.church),
        camping.permissionContext(),
    )

private fun String.initials(): String = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    .take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifBlank { "?" }

private val staffRoleColors = listOf(
    "#4F7CAC", "#2A9D8F", "#D9432F", "#E9C46A",
    "#8338EC", "#118AB2", "#2E7D32", "#EF476F",
)

@Preview(showBackground = true)
@Composable
private fun StaffRolesPreview() {
    CampzoneTheme {
        StaffRolesScreen(
            state = StaffRolesUiState.Loaded(
                listOf(
                    CampingStaffRole(
                        id = "worship",
                        campingId = "camp",
                        name = "Worship team",
                        kind = StaffRoleKind.Worship,
                        members = listOf(StaffRoleMember("1", "1", "Ana Silva", title = "Coordinator")),
                    ),
                ),
            ),
            canManage = true,
            operationError = null,
            operationMessage = null,
            onBack = {},
            onOpenDetail = {},
            onCreate = {},
            onRefresh = {},
            onClearError = {},
            onClearMessage = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StaffRoleDetailPreview() {
    CampzoneTheme {
        StaffRoleDetailScreen(
            role = CampingStaffRole(
                id = "kitchen",
                campingId = "camp",
                name = "Kitchen team",
                kind = StaffRoleKind.Kitchen,
                description = "Coordinates meals, serving, and dietary requirements.",
                capabilities = listOf(StaffCapability.ManageFoodMenu),
                members = listOf(StaffRoleMember("1", "1", "Ana Silva", title = "Coordinator")),
            ),
            canManage = true,
            currentUserId = "1",
            onBack = {},
            onEdit = {},
            onOpenChat = {},
        )
    }
}

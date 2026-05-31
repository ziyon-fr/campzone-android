package fr.ziyon.campzone.ui.admin.role

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.admin.ManagedUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser

@Composable
fun RoleManagementRoute(
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoleManagementViewModel = hiltViewModel(),
) {
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = PermissionUser(
        role = authenticatedUser.role,
        userId = authenticatedUser.uid,
        church = authenticatedUser.church,
    )
    val canAssignAnyRole = evaluator.canAssignAnyRole(permissionUser)
    val canAssignLeadership = evaluator.can(permissionUser, AppPermission.AssignLeadershipRoles)
    val assignableRoles = evaluator.assignableRoles(permissionUser)
    // Admins see every church; church-scoped leaders are limited to their own.
    val churchFilter = if (canAssignLeadership) null else authenticatedUser.church
    // Only admins may write fields beyond {role, updatedAt}; the `id` stamp keeps
    // the iOS admin list decoder happy for Android-touched docs.
    val writeIdField = authenticatedUser.role.isAdmin

    val uiState by viewModel.uiState.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val operationError by viewModel.operationError.collectAsState()

    LaunchedEffect(canAssignAnyRole, churchFilter) {
        if (canAssignAnyRole) viewModel.loadIfNeeded(churchFilter)
    }

    RoleManagementScreen(
        canAssignAnyRole = canAssignAnyRole,
        scopedChurch = churchFilter,
        assignableRoles = assignableRoles,
        canAssignRoleForChurch = { targetChurch ->
            evaluator.canAssignRole(permissionUser, targetChurch)
        },
        uiState = uiState,
        searchText = searchText,
        isSaving = isSaving,
        operationMessage = operationMessage,
        operationError = operationError,
        onSearchChange = viewModel::onSearchChange,
        onBack = onBack,
        onRetry = { viewModel.load(churchFilter) },
        onAssignRole = { user, role -> viewModel.updateRole(user, role, writeIdField) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleManagementScreen(
    canAssignAnyRole: Boolean,
    scopedChurch: String?,
    assignableRoles: List<UserRole>,
    canAssignRoleForChurch: (String?) -> Boolean,
    uiState: RoleManagementUiState,
    searchText: String,
    isSaving: Boolean,
    operationMessage: String?,
    operationError: String?,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAssignRole: (ManagedUser, UserRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.role_management_title), color = colors.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
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
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        if (!canAssignAnyRole) {
            CzEmptyState(
                title = stringResource(R.string.role_management_restricted_title),
                message = stringResource(R.string.role_management_restricted_message),
                modifier = contentModifier,
                icon = {
                    Icon(
                        Icons.Rounded.Groups,
                        contentDescription = null,
                        tint = colors.textSecondary,
                    )
                },
            )
            return@Scaffold
        }

        when (uiState) {
            RoleManagementUiState.Loading -> CzLoadingView(
                modifier = contentModifier,
                message = stringResource(R.string.role_management_loading),
            )

            is RoleManagementUiState.Error -> CzErrorState(
                title = stringResource(R.string.role_management_error_title),
                message = uiState.message,
                modifier = contentModifier,
                onRetry = onRetry,
                retryLabel = stringResource(R.string.common_retry),
            )

            is RoleManagementUiState.Loaded -> LoadedContent(
                state = uiState,
                scopedChurch = scopedChurch,
                assignableRoles = assignableRoles,
                canAssignRoleForChurch = canAssignRoleForChurch,
                searchText = searchText,
                isSaving = isSaving,
                operationMessage = operationMessage,
                operationError = operationError,
                onSearchChange = onSearchChange,
                onAssignRole = onAssignRole,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun LoadedContent(
    state: RoleManagementUiState.Loaded,
    scopedChurch: String?,
    assignableRoles: List<UserRole>,
    canAssignRoleForChurch: (String?) -> Boolean,
    searchText: String,
    isSaving: Boolean,
    operationMessage: String?,
    operationError: String?,
    onSearchChange: (String) -> Unit,
    onAssignRole: (ManagedUser, UserRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = CzSpacing.lg,
            end = CzSpacing.lg,
            top = CzSpacing.md,
            bottom = CzSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        if (scopedChurch != null) {
            item(key = "scope") {
                ScopeChip(church = scopedChurch)
            }
        }

        item(key = "search") {
            CzTextField(
                value = searchText,
                onValueChange = onSearchChange,
                label = stringResource(R.string.role_management_search_label),
                placeholder = stringResource(R.string.role_management_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSecondary)
                },
            )
        }

        if (operationMessage != null) {
            item(key = "op-message") {
                OperationLabel(
                    icon = Icons.Rounded.CheckCircle,
                    text = stringResource(R.string.role_management_role_updated, operationMessage),
                    tint = colors.success,
                )
            }
        }
        if (operationError != null) {
            item(key = "op-error") {
                OperationLabel(
                    icon = Icons.Rounded.ErrorOutline,
                    text = operationError,
                    tint = colors.error,
                )
            }
        }

        if (state.groups.isEmpty()) {
            item(key = "empty") {
                CzEmptyState(
                    title = stringResource(R.string.role_management_empty_title),
                    message = stringResource(R.string.role_management_empty_message),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CzSpacing.xxl),
                    icon = {
                        Icon(Icons.Rounded.Groups, contentDescription = null, tint = colors.textSecondary)
                    },
                )
            }
        } else {
            state.groups.forEach { group ->
                val isExpanded = expanded[group.church] ?: true
                item(key = "header-${group.church}") {
                    ChurchHeader(
                        church = group.church,
                        count = group.users.size,
                        expanded = isExpanded,
                        onToggle = { expanded[group.church] = !isExpanded },
                    )
                }
                if (isExpanded) {
                    items(group.users, key = { it.id }) { user ->
                        UserRoleRow(
                            user = user,
                            assignableRoles = assignableRoles,
                            canEdit = canAssignRoleForChurch(user.church),
                            isSaving = isSaving,
                            onAssignRole = { role -> onAssignRole(user, role) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeChip(church: String) {
    val colors = MaterialTheme.czColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(
            Icons.Rounded.Apartment,
            contentDescription = null,
            tint = colors.ember,
            modifier = Modifier.size(CzSpacing.base),
        )
        Text(
            text = stringResource(R.string.role_management_scoped_to, church),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun OperationLabel(icon: ImageVector, text: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(CzSpacing.base))
        Text(text = text, color = tint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChurchHeader(
    church: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val label = church.ifBlank { stringResource(R.string.role_management_unassigned_church) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(
            Icons.Rounded.Apartment,
            contentDescription = null,
            tint = colors.ember,
            modifier = Modifier.size(CzSpacing.base),
        )
        Text(
            text = label.uppercase(),
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier
                .size(CzSpacing.lg)
                .rotate(rotation),
        )
    }
}

@Composable
private fun UserRoleRow(
    user: ManagedUser,
    assignableRoles: List<UserRole>,
    canEdit: Boolean,
    isSaving: Boolean,
    onAssignRole: (UserRole) -> Unit,
) {
    val colors = MaterialTheme.czColors
    CzCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            CzAvatar(
                imageUrl = user.photoUrl,
                contentDescription = user.displayName,
                initials = user.displayName,
                size = CzAvatarSize.Small,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName.ifBlank { stringResource(R.string.role_management_unnamed_user) },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.email.ifBlank { user.church },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(CzSpacing.xs))
            if (canEdit && assignableRoles.isNotEmpty()) {
                RolePicker(
                    currentRole = user.role,
                    assignableRoles = assignableRoles,
                    enabled = !isSaving,
                    onAssignRole = onAssignRole,
                )
            } else {
                Text(
                    text = user.role.localizedName(),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RolePicker(
    currentRole: UserRole,
    assignableRoles: List<UserRole>,
    enabled: Boolean,
    onAssignRole: (UserRole) -> Unit,
) {
    val colors = MaterialTheme.czColors
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val pickerLabel = stringResource(R.string.role_management_change_role, currentRole.localizedName())

    Box {
        Row(
            modifier = Modifier
                .background(
                    color = colors.ember.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(CzRadius.full),
                )
                .clickable(enabled = enabled) { menuOpen = true }
                .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs)
                .semantics { contentDescription = pickerLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Text(
                text = currentRole.localizedName(),
                color = colors.ember,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.Rounded.UnfoldMore,
                contentDescription = null,
                tint = colors.ember,
                modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            assignableRoles.forEach { role ->
                DropdownMenuItem(
                    text = { Text(role.localizedName()) },
                    onClick = {
                        menuOpen = false
                        onAssignRole(role)
                    },
                    trailingIcon = if (role == currentRole) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.ember,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun UserRole.localizedName(): String =
    stringResource(
        when (this) {
            UserRole.Guest -> R.string.role_guest
            UserRole.User -> R.string.role_user
            UserRole.Adult -> R.string.role_adult
            UserRole.YouthDirector -> R.string.role_youth_director
            UserRole.Pastor -> R.string.role_pastor
            UserRole.GameMaster -> R.string.role_game_master
            UserRole.Leader -> R.string.role_leader
            UserRole.Photographer -> R.string.role_photographer
            UserRole.Admin -> R.string.role_admin
        },
    )

@Preview
@Composable
private fun RoleManagementScreenPreview() {
    CampzoneTheme {
        RoleManagementScreen(
            canAssignAnyRole = true,
            scopedChurch = null,
            assignableRoles = UserRole.entries,
            canAssignRoleForChurch = { true },
            uiState = RoleManagementUiState.Loaded(
                groups = listOf(
                    ChurchGroup(
                        church = "Lausanne Adventist Church",
                        users = listOf(
                            ManagedUser("u1", "Léa Müller", "lea@example.org", "Lausanne Adventist Church", UserRole.User, null, null),
                            ManagedUser("u2", "Marc Dupont", "marc@example.org", "Lausanne Adventist Church", UserRole.YouthDirector, null, null),
                        ),
                    ),
                ),
                hasUsers = true,
            ),
            searchText = "",
            isSaving = false,
            operationMessage = null,
            operationError = null,
            onSearchChange = {},
            onBack = {},
            onRetry = {},
            onAssignRole = { _, _ -> },
        )
    }
}

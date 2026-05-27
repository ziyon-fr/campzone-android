package fr.ziyon.campzone.ui.games

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.PreviewCampingService
import fr.ziyon.campzone.data.games.FakeGameService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.WinnerRevealPolicy
import fr.ziyon.campzone.data.teams.FakeTeamService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun WinnerRevealRoute(
    campingId: String,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    viewModel: GameViewModel,
    onBack: () -> Unit,
) {
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = PermissionUser(
        role = authenticatedUser.role,
        userId = authenticatedUser.uid,
        church = authenticatedUser.church,
    )
    val campingCtx = camping?.let { c ->
        CampingPermissionContext(
            organizerLevelType = c.organizerLevel.type.wireValue,
            organizerLevelValue = c.organizerLevel.value,
            createdByUid = c.createdByUid,
        )
    }
    val canReveal = campingCtx != null && evaluator.canRevealWinners(permissionUser, campingCtx)

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId) }
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState is GamesUiState.Loading -> CzLoadingView(modifier = Modifier.fillMaxSize())
        else -> WinnerRevealScreen(
            campingId = campingId,
            camping = camping,
            canReveal = canReveal,
            authenticatedUser = authenticatedUser,
            viewModel = viewModel,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WinnerRevealScreen(
    campingId: String,
    camping: Camping?,
    canReveal: Boolean,
    authenticatedUser: AuthenticatedUser,
    viewModel: GameViewModel,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val policy = camping?.winnerRevealPolicy
    val endDate = camping?.endDate ?: Date()

    // Effective hide date fallback: endDate − 24h
    val effectiveHideDate = remember(policy, endDate) {
        policy?.hideDate ?: Date(endDate.time - 24 * 60 * 60 * 1000L)
    }

    var customizeHide by remember { mutableStateOf(policy?.hideDate != null) }
    var hideDate by remember { mutableStateOf(if (policy?.hideDate != null) policy.hideDate!! else effectiveHideDate) }
    var customizeReveal by remember { mutableStateOf(policy?.revealDate != null) }
    var revealDate by remember { mutableStateOf(policy?.revealDate ?: endDate) }

    var showRevealConfirm by remember { mutableStateOf(false) }
    var showUnrevealConfirm by remember { mutableStateOf(false) }

    val isRevealed = policy?.let {
        it.isRevealed || (it.revealDate?.let { rd -> rd <= Date() } ?: false)
    } ?: false
    val scoresHidden = policy != null && !isRevealed &&
        (policy.hideDate?.let { it <= Date() } ?: (Date() >= effectiveHideDate))

    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

    if (showRevealConfirm) {
        AlertDialog(
            onDismissRequest = { showRevealConfirm = false },
            title = { Text(stringResource(R.string.reveal_confirm_title)) },
            text = { Text(stringResource(R.string.reveal_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRevealConfirm = false
                    scope.launch {
                        viewModel.reveal(campingId, policy, authenticatedUser)
                        onBack()
                    }
                }) { Text(stringResource(R.string.reveal_action_reveal), color = colors.ember) }
            },
            dismissButton = {
                TextButton(onClick = { showRevealConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showUnrevealConfirm) {
        AlertDialog(
            onDismissRequest = { showUnrevealConfirm = false },
            title = { Text(stringResource(R.string.reveal_unreveal_confirm_title)) },
            text = { Text(stringResource(R.string.reveal_unreveal_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showUnrevealConfirm = false
                    scope.launch { viewModel.unreveal(campingId, policy) }
                }) { Text(stringResource(R.string.reveal_action_unreveal), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUnrevealConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.reveal_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back), tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (viewModel.isUpdatingReveal) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = CzSpacing.sm),
                            color = colors.ember,
                            strokeWidth = 2.dp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            // Current state card
            Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                    Text(stringResource(R.string.reveal_section_current_state), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                    HorizontalDivider(color = colors.divider)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reveal_status_label), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        Text(
                            if (scoresHidden) stringResource(R.string.reveal_status_hidden)
                            else stringResource(R.string.reveal_status_visible),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            ),
                            color = if (scoresHidden) colors.warning else colors.success,
                        )
                    }
                    if (policy?.isRevealed == true) {
                        policy.revealedByName?.let { name ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.reveal_revealed_by), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(name, style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
                                    policy.revealedAt?.let { at ->
                                        Text(fmt.format(at), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.reveal_default_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            // Hide date settings
            Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                    Text(stringResource(R.string.reveal_section_hide_date), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                    HorizontalDivider(color = colors.divider)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reveal_custom_hide_date), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        Switch(
                            checked = customizeHide,
                            onCheckedChange = { customizeHide = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.ember),
                        )
                    }
                    if (customizeHide) {
                        DateTimePickerRow(
                            label = stringResource(R.string.reveal_scores_hide_on),
                            date = hideDate,
                            onDatePicked = { hideDate = it },
                            fmt = fmt,
                            colors = colors,
                            context = context,
                        )
                    }
                }
            }

            // Auto reveal settings
            Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                    Text(stringResource(R.string.reveal_section_auto_reveal), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                    HorizontalDivider(color = colors.divider)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reveal_schedule_reveal), style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                        Switch(
                            checked = customizeReveal,
                            onCheckedChange = { customizeReveal = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.ember),
                        )
                    }
                    if (customizeReveal) {
                        DateTimePickerRow(
                            label = stringResource(R.string.reveal_reveal_on),
                            date = revealDate,
                            onDatePicked = { revealDate = it },
                            fmt = fmt,
                            colors = colors,
                            context = context,
                        )
                    }
                }
            }

            viewModel.operationError?.let { err ->
                Surface(color = colors.error.copy(alpha = 0.1f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(CzSpacing.md))
                }
            }
            viewModel.operationMessage?.let { msg ->
                Surface(color = colors.success.copy(alpha = 0.1f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = colors.success, modifier = Modifier.padding(CzSpacing.md))
                }
            }

            // Save settings
            if (canReveal) {
                Button(
                    onClick = {
                        val newPolicy = (policy ?: WinnerRevealPolicy(isRevealed = false)).copy(
                            hideDate = if (customizeHide) hideDate else null,
                            revealDate = if (customizeReveal) revealDate else null,
                        )
                        scope.launch { viewModel.updateRevealPolicy(campingId, newPolicy) }
                    },
                    enabled = !viewModel.isUpdatingReveal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.ember),
                ) {
                    Text(stringResource(R.string.reveal_save_settings))
                }

                // Reveal / Unreveal action
                if (!isRevealed) {
                    OutlinedButton(
                        onClick = { showRevealConfirm = true },
                        enabled = !viewModel.isUpdatingReveal,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ember),
                    ) {
                        Icon(Icons.Outlined.EmojiEvents, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(CzSpacing.sm))
                        Text(stringResource(R.string.reveal_action_reveal))
                    }
                } else {
                    OutlinedButton(
                        onClick = { showUnrevealConfirm = true },
                        enabled = !viewModel.isUpdatingReveal,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                    ) {
                        Text(stringResource(R.string.reveal_action_unreveal))
                    }
                }
            }

            Spacer(Modifier.height(CzSpacing.xxl))
        }
    }
}

@Composable
private fun DateTimePickerRow(
    label: String,
    date: Date,
    onDatePicked: (Date) -> Unit,
    fmt: SimpleDateFormat,
    colors: fr.ziyon.campzone.core.designsystem.CzColorPalette,
    context: android.content.Context,
) {
    val cal = remember(date) { Calendar.getInstance().also { it.time = date } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        TextButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val newCal = Calendar.getInstance()
                                newCal.set(year, month, day, hour, minute, 0)
                                onDatePicked(newCal.time)
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true,
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
        ) {
            Text(fmt.format(date), color = colors.ember, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WinnerRevealScreenPreview() {
    CampzoneTheme {
        WinnerRevealRoute(
            campingId = "preview-camp",
            camping = null,
            authenticatedUser = AuthenticatedUser(
                uid = "uid", email = "a@b.com", displayName = "Admin", photoUrl = null,
                role = UserRole.Admin, church = "Central SDA", age = 30,
                preferredLanguage = "en", gender = null, onboardingCompleted = true,
            ),
            viewModel = GameViewModel(FakeGameService(), FakeTeamService(), PreviewCampingService()),
            onBack = {},
        )
    }
}

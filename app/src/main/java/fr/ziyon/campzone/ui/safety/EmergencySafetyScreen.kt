package fr.ziyon.campzone.ui.safety

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.safety.EmergencyContact
import fr.ziyon.campzone.data.safety.EmergencySafetyHub

@Composable
fun EmergencySafetyRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    viewModel: EmergencySafetyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(campingId, authenticatedUser.uid) { viewModel.load(campingId, authenticatedUser) }
    EmergencySafetyScreen(
        state = state,
        onBack = onBack,
        onSave = viewModel::save,
        onBroadcast = { title, body -> viewModel.broadcast(authenticatedUser, title, body) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencySafetyScreen(
    state: EmergencySafetyUiState,
    onBack: () -> Unit,
    onSave: (EmergencySafetyHub) -> Unit,
    onBroadcast: (String, String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var broadcasting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.safety_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
                actions = {
                    if (state.canManage) IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.common_edit)) }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            state.hub == null -> CzErrorState(
                title = stringResource(R.string.safety_error_title),
                message = state.error,
                onRetry = {},
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
            ) {
                state.error?.let { FeedbackCard(it, MaterialTheme.czColors.error) }
                state.message?.let { FeedbackCard(it, MaterialTheme.czColors.success) }
                SafetySection(stringResource(R.string.safety_emergency_contacts), Icons.Rounded.Call) {
                    state.hub.emergencyContacts.forEach { contact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = contact.dialablePhoneNumber.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.dialablePhoneNumber}")))
                            }.padding(vertical = CzSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(contact.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.czColors.textPrimary)
                                Text(contact.subtitle.ifBlank { contact.phoneNumber }, color = MaterialTheme.czColors.textSecondary)
                            }
                            Icon(Icons.Rounded.Call, null, tint = MaterialTheme.czColors.success)
                        }
                    }
                }
                SafetySection(stringResource(R.string.safety_emergency_instructions), Icons.Rounded.Security) {
                    Text(state.hub.emergencyInstructions, color = MaterialTheme.czColors.textPrimary)
                }
                SafetySection(stringResource(R.string.safety_first_aid), Icons.Rounded.LocalHospital) {
                    Text(state.hub.firstAidInfo, color = MaterialTheme.czColors.textPrimary)
                }
                if (state.canBroadcast) {
                    CzButton(
                        text = stringResource(R.string.safety_send_alert),
                        onClick = { broadcasting = true },
                        variant = CzButtonVariant.Destructive,
                        loading = state.broadcasting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    state.hub?.takeIf { editing }?.let { hub ->
        SafetyEditorDialog(hub, state.saving, { editing = false }) {
            onSave(it)
            editing = false
        }
    }
    if (broadcasting) UrgentBroadcastDialog(
        onDismiss = { broadcasting = false },
        onSend = { title, body -> broadcasting = false; onBroadcast(title, body) },
    )
}

@Composable
private fun SafetySection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.lg)) {
        Column(Modifier.fillMaxWidth().padding(CzSpacing.md), verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.czColors.accent)
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.czColors.textPrimary)
            }
            content()
        }
    }
}

@Composable
private fun FeedbackCard(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, color = color, modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.08f), RoundedCornerShape(CzRadius.md)).padding(CzSpacing.md))
}

@Composable
private fun SafetyEditorDialog(hub: EmergencySafetyHub, saving: Boolean, onDismiss: () -> Unit, onSave: (EmergencySafetyHub) -> Unit) {
    var draft by remember(hub) { mutableStateOf(hub) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.safety_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                OutlinedTextField(draft.emergencyInstructions, { draft = draft.copy(emergencyInstructions = it) }, label = { Text(stringResource(R.string.safety_emergency_instructions)) }, minLines = 3)
                OutlinedTextField(draft.firstAidInfo, { draft = draft.copy(firstAidInfo = it) }, label = { Text(stringResource(R.string.safety_first_aid)) }, minLines = 3)
                draft.emergencyContacts.forEachIndexed { index, contact ->
                    Surface(color = MaterialTheme.czColors.background, shape = RoundedCornerShape(CzRadius.md)) {
                        Column(Modifier.padding(CzSpacing.sm), verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                            OutlinedTextField(contact.name, { value -> draft = draft.withContact(index, contact.copy(name = value)) }, label = { Text(stringResource(R.string.safety_contact_name)) })
                            OutlinedTextField(contact.role, { value -> draft = draft.withContact(index, contact.copy(role = value)) }, label = { Text(stringResource(R.string.safety_contact_role)) })
                            OutlinedTextField(contact.phoneNumber, { value -> draft = draft.withContact(index, contact.copy(phoneNumber = value)) }, label = { Text(stringResource(R.string.safety_contact_phone)) })
                            OutlinedTextField(contact.note, { value -> draft = draft.withContact(index, contact.copy(note = value)) }, label = { Text(stringResource(R.string.safety_contact_note)) })
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(contact.isPrimary, { checked ->
                                    draft = draft.copy(emergencyContacts = draft.emergencyContacts.mapIndexed { i, item -> item.copy(isPrimary = checked && i == index) })
                                })
                                Text(stringResource(R.string.safety_primary_contact), modifier = Modifier.weight(1f))
                                IconButton(onClick = { draft = draft.copy(emergencyContacts = draft.emergencyContacts.filterIndexed { i, _ -> i != index }) }) { Icon(Icons.Rounded.Delete, null) }
                            }
                        }
                    }
                }
                TextButton(onClick = { draft = draft.copy(emergencyContacts = draft.emergencyContacts + EmergencyContact()) }) {
                    Icon(Icons.Rounded.Add, null)
                    Text(stringResource(R.string.safety_add_contact))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }, enabled = !saving) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun EmergencySafetyHub.withContact(index: Int, contact: EmergencyContact) =
    copy(emergencyContacts = emergencyContacts.mapIndexed { i, current -> if (i == index) contact else current })

@Composable
private fun UrgentBroadcastDialog(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.NotificationsActive, null) },
        title = { Text(stringResource(R.string.safety_alert_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.safety_alert_subject)) })
            OutlinedTextField(body, { body = it }, label = { Text(stringResource(R.string.safety_alert_details)) }, minLines = 4)
        } },
        confirmButton = { TextButton(onClick = { onSend(title, body) }, enabled = title.isNotBlank() && body.isNotBlank()) { Text(stringResource(R.string.safety_send_alert)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

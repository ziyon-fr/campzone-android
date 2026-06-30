package fr.ziyon.campzone.ui.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.support.SponsorAcknowledgement
import fr.ziyon.campzone.data.support.SupportExternalLink
import fr.ziyon.campzone.data.support.SupportHub
import fr.ziyon.campzone.data.support.SupportLinkKind

@Composable
fun AppSupportRoute(user: AuthenticatedUser, onBack: () -> Unit, viewModel: SupportHubViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(user.uid) { viewModel.loadApp(user) }
    SupportHubScreen(state, onBack, viewModel::save)
}

@Composable
fun CampSupportRoute(campingId: String, user: AuthenticatedUser, onBack: () -> Unit, viewModel: SupportHubViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(campingId, user.uid) { viewModel.loadCamp(campingId, user) }
    SupportHubScreen(state, onBack, viewModel::save)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportHubScreen(state: SupportHubUiState, onBack: () -> Unit, onSave: (SupportHub) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(if (state.mode == SupportHubMode.App) R.string.support_app_title else R.string.support_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
                actions = { if (state.canManage) IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.support_edit)) } },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.hub == null -> CzErrorState(
                title = stringResource(R.string.support_unavailable),
                message = state.error,
                onRetry = {},
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
            ) {
                Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.lg)) {
                    Row(Modifier.fillMaxWidth().padding(CzSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                        Box(Modifier.size(52.dp).background(MaterialTheme.czColors.accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Handyman, null, tint = MaterialTheme.czColors.accent) }
                        Column { Text(state.title, fontWeight = FontWeight.SemiBold); Text(stringResource(if (state.mode == SupportHubMode.App) R.string.support_app_development else R.string.support_and_sponsors), color = MaterialTheme.czColors.textSecondary) }
                    }
                }
                Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.lg)) {
                    Column(Modifier.fillMaxWidth().padding(CzSpacing.md), verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                        Text(state.hub.intro, color = MaterialTheme.czColors.textPrimary)
                        if (state.hub.impactNote.isNotBlank()) Text(state.hub.impactNote, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.czColors.textSecondary)
                    }
                }
                Text(
                    stringResource(R.string.support_compliance_notice),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.czColors.pine.copy(alpha = 0.08f), RoundedCornerShape(CzRadius.md)).padding(CzSpacing.md),
                )
                Text(stringResource(if (state.mode == SupportHubMode.App) R.string.support_app_development_action else R.string.support_this_camp), fontWeight = FontWeight.SemiBold)
                if (state.hub.externalLinks.isEmpty()) {
                    Text(stringResource(R.string.support_no_links), color = MaterialTheme.czColors.textSecondary, modifier = Modifier.fillMaxWidth().background(MaterialTheme.czColors.surface, RoundedCornerShape(CzRadius.lg)).padding(CzSpacing.md))
                } else Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
                    Column { state.hub.externalLinks.forEachIndexed { index, link ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = link.normalizedUrl != null) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.normalizedUrl))) }.padding(CzSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                        ) {
                            Icon(Icons.Rounded.Link, null, tint = MaterialTheme.czColors.accent)
                            Column(Modifier.weight(1f)) { Text(link.title, fontWeight = FontWeight.SemiBold); if (link.subtitle.isNotBlank()) Text(link.subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.czColors.textSecondary) }
                            if (link.isPrimary) Icon(Icons.Rounded.Star, null, tint = MaterialTheme.czColors.amber)
                            Icon(Icons.Rounded.OpenInBrowser, null, tint = MaterialTheme.czColors.textSecondary)
                        }
                        if (index < state.hub.externalLinks.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    } }
                }
                if (state.hub.visibleSponsors.isNotEmpty()) {
                    Text(stringResource(R.string.support_sponsors), fontWeight = FontWeight.SemiBold)
                    Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) { Column {
                        state.hub.visibleSponsors.forEachIndexed { index, sponsor ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = sponsor.normalizedUrl != null) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sponsor.normalizedUrl))) }.padding(CzSpacing.md),
                                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md), verticalAlignment = Alignment.CenterVertically,
                            ) { Icon(Icons.Rounded.Star, null, tint = MaterialTheme.czColors.amber); Column(Modifier.weight(1f)) { Text(sponsor.name, fontWeight = FontWeight.SemiBold); if (sponsor.note.isNotBlank()) Text(sponsor.note, color = MaterialTheme.czColors.textSecondary) } }
                            if (index < state.hub.visibleSponsors.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                        }
                    } }
                }
                state.message?.let { Text(it, color = MaterialTheme.czColors.success) }
                state.error?.let { Text(it, color = MaterialTheme.czColors.error) }
            }
        }
    }
    if (editing && state.hub != null) SupportEditorDialog(state.hub, state.saving, { editing = false }) { onSave(it); editing = false }
}

@Composable
private fun SupportEditorDialog(initial: SupportHub, saving: Boolean, onDismiss: () -> Unit, onSave: (SupportHub) -> Unit) {
    var hub by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.support_information)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
            OutlinedTextField(hub.intro, { hub = hub.copy(intro = it) }, label = { Text(stringResource(R.string.support_message)) }, minLines = 3)
            OutlinedTextField(hub.impactNote, { hub = hub.copy(impactNote = it) }, label = { Text(stringResource(R.string.support_impact_note)) }, minLines = 2)
            Text(stringResource(R.string.support_external_links), fontWeight = FontWeight.SemiBold)
            hub.links.forEachIndexed { index, link ->
                Surface(color = MaterialTheme.czColors.background, shape = RoundedCornerShape(CzRadius.md)) { Column(Modifier.padding(CzSpacing.sm)) {
                    OutlinedTextField(link.title, { hub = hub.withLink(index, link.copy(title = it)) }, label = { Text(stringResource(R.string.common_title)) })
                    OutlinedTextField(link.subtitle, { hub = hub.withLink(index, link.copy(subtitle = it)) }, label = { Text(stringResource(R.string.support_subtitle)) })
                    OutlinedTextField(link.urlString, { hub = hub.withLink(index, link.copy(urlString = it)) }, label = { Text(stringResource(R.string.support_external_url)) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(link.isPrimary, { checked -> hub = hub.copy(links = hub.links.mapIndexed { i, value -> value.copy(isPrimary = checked && i == index) }) })
                        Text(stringResource(R.string.support_primary_link), modifier = Modifier.weight(1f))
                        IconButton(onClick = { hub = hub.copy(links = hub.links.filterIndexed { i, _ -> i != index }) }) { Icon(Icons.Rounded.Delete, null) }
                    }
                } }
            }
            TextButton(onClick = { hub = hub.copy(links = hub.links + SupportExternalLink(kind = SupportLinkKind.AppDevelopment, isPrimary = hub.links.isEmpty())) }) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.support_add_link)) }
            Text(stringResource(R.string.support_sponsor_acknowledgements), fontWeight = FontWeight.SemiBold)
            hub.sponsors.forEachIndexed { index, sponsor ->
                Surface(color = MaterialTheme.czColors.background, shape = RoundedCornerShape(CzRadius.md)) { Column(Modifier.padding(CzSpacing.sm)) {
                    OutlinedTextField(sponsor.name, { hub = hub.withSponsor(index, sponsor.copy(name = it)) }, label = { Text(stringResource(R.string.support_sponsor_name)) })
                    OutlinedTextField(sponsor.note, { hub = hub.withSponsor(index, sponsor.copy(note = it)) }, label = { Text(stringResource(R.string.support_note)) })
                    OutlinedTextField(sponsor.urlString, { hub = hub.withSponsor(index, sponsor.copy(urlString = it)) }, label = { Text(stringResource(R.string.support_sponsor_url)) })
                    IconButton(onClick = { hub = hub.copy(sponsors = hub.sponsors.filterIndexed { i, _ -> i != index }) }) { Icon(Icons.Rounded.Delete, null) }
                } }
            }
            TextButton(onClick = { hub = hub.copy(sponsors = hub.sponsors + SponsorAcknowledgement()) }) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.support_add_sponsor)) }
        } },
        confirmButton = { TextButton(onClick = { onSave(hub) }, enabled = !saving) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun SupportHub.withLink(index: Int, link: SupportExternalLink) = copy(links = links.mapIndexed { i, value -> if (i == index) link else value })
private fun SupportHub.withSponsor(index: Int, sponsor: SponsorAcknowledgement) = copy(sponsors = sponsors.mapIndexed { i, value -> if (i == index) sponsor else value })

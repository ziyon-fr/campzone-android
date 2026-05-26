package fr.ziyon.campzone.ui.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Announcement

// ── Route ─────────────────────────────────────────────────────────────────────

@Composable
fun AnnouncementsRoute(
    viewModel: AnnouncementViewModel,
    authenticatedUser: AuthenticatedUser,
    permissionUser: PermissionUser?,
    evaluator: AppPermissionEvaluator,
    onOpenDetail: (String) -> Unit,
    onOpenComposer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val campings by viewModel.campings.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    LaunchedEffect(campings, authenticatedUser.uid) {
        viewModel.configureVisibility(authenticatedUser, campings, permissionUser, evaluator)
    }

    val canCompose = remember(permissionUser, campings) {
        viewModel.canComposeAnnouncements(permissionUser, evaluator)
    }

    AnnouncementsScreen(
        uiState = uiState,
        searchQuery = searchQuery,
        canCompose = canCompose,
        onSearchChange = viewModel::updateSearch,
        onOpenDetail = onOpenDetail,
        onOpenComposer = {
            viewModel.prepareNew()
            onOpenComposer()
        },
        onRetry = viewModel::load,
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(
    uiState: AnnouncementsUiState,
    searchQuery: String,
    canCompose: Boolean,
    onSearchChange: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenComposer: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var searchActive by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Announcements",
                            style = CzTypeScale.headline,
                            color = colors.textPrimary,
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = colors.background,
                        scrolledContainerColor = colors.background,
                    ),
                    windowInsets = WindowInsets(),
                )
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = onSearchChange,
                            onSearch = { searchActive = false },
                            expanded = searchActive,
                            onExpandedChange = { searchActive = it },
                            placeholder = {
                                Text("Search announcements", style = CzTypeScale.body, color = colors.textSecondary)
                            },
                        )
                    },
                    expanded = searchActive,
                    onExpandedChange = { searchActive = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CzSpacing.base, vertical = CzSpacing.xs),
                    colors = SearchBarDefaults.colors(
                        containerColor = colors.surface,
                        dividerColor = colors.divider,
                    ),
                    content = {},
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
        ) {
            item { Spacer(Modifier.height(CzSpacing.md)) }

            if (canCompose) {
                item {
                    ComposeActionCard(onClick = onOpenComposer)
                }
            }

            when (uiState) {
                is AnnouncementsUiState.Loading -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        ) {
                            Icon(
                                Icons.Rounded.Campaign,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                "Loading announcements…",
                                style = CzTypeScale.caption,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                is AnnouncementsUiState.Empty -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        ) {
                            Icon(
                                Icons.Rounded.Campaign,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                if (uiState.searchActive) "No results" else "No announcements",
                                style = CzTypeScale.body,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (uiState.searchActive) "Try a different search term."
                                else "Camp-wide updates will appear here as leaders publish them.",
                                style = CzTypeScale.caption,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                is AnnouncementsUiState.Error -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .clickable(onClick = onRetry)
                            .padding(CzSpacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        ) {
                            Text("Failed to load", style = CzTypeScale.body, color = colors.error)
                            Text(uiState.message, style = CzTypeScale.caption, color = colors.textSecondary)
                            Text("Tap to retry", style = CzTypeScale.caption, color = colors.ember)
                        }
                    }
                }

                is AnnouncementsUiState.Loaded -> item {
                    AnnouncementsSection(
                        announcements = uiState.announcements,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }

            item { Spacer(Modifier.height(CzSpacing.xxl)) }
        }
    }
}

// ── Compose action card ───────────────────────────────────────────────────────

@Composable
private fun ComposeActionCard(onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(vertical = CzSpacing.sm, horizontal = CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = null,
            tint = colors.ember,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Compose Announcement",
            style = CzTypeScale.subhead,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = CzTypeScale.caption, color = colors.textSecondary)
    }
}

// ── Announcements section ─────────────────────────────────────────────────────

@Composable
private fun AnnouncementsSection(
    announcements: List<Announcement>,
    onOpenDetail: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Icon(
                Icons.Rounded.Campaign,
                contentDescription = null,
                tint = colors.ember,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Latest",
                style = CzTypeScale.caption,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surface)
                    .padding(horizontal = CzSpacing.xs, vertical = 2.dp),
            ) {
                Text(
                    text = "${announcements.size}",
                    style = CzTypeScale.caption2.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                )
            }
        }

        // Rows in surface card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface),
        ) {
            announcements.forEachIndexed { idx, ann ->
                AnnouncementTimelineRow(
                    announcement = ann,
                    onClick = { onOpenDetail(ann.id) },
                )
                if (idx < announcements.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = colors.divider,
                    )
                }
            }
        }
    }
}

// ── Timeline row ──────────────────────────────────────────────────────────────

@Composable
private fun AnnouncementTimelineRow(
    announcement: Announcement,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = CzSpacing.sm, horizontal = CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        // Amber circle icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.amber.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Campaign,
                contentDescription = null,
                tint = colors.amber,
                modifier = Modifier.size(18.dp),
            )
        }

        // Content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = announcement.title,
                    style = CzTypeScale.subhead,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(CzSpacing.sm))
                Text(
                    text = announcement.createdDateText,
                    style = CzTypeScale.caption2,
                    color = colors.textSecondary,
                )
            }

            if (announcement.summary.isNotEmpty()) {
                Text(
                    text = announcement.summary,
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (announcement.attachments.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "📎",
                        style = CzTypeScale.caption2,
                    )
                    Text(
                        text = "${announcement.attachments.size} attachment",
                        style = CzTypeScale.caption2.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.ember,
                    )
                }
            }

            val audienceText = when {
                announcement.targetCampingId != null || announcement.notificationTargetRole != null ->
                    announcement.audienceText
                else -> null
            }
            if (audienceText != null) {
                Text(
                    text = audienceText,
                    style = CzTypeScale.caption2.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text("›", style = CzTypeScale.caption.copy(fontSize = 16.sp), color = colors.textSecondary)
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun AnnouncementsScreenPreview() {
    CampzoneTheme {
        AnnouncementsScreen(
            uiState = AnnouncementsUiState.Loading,
            searchQuery = "",
            canCompose = true,
            onSearchChange = {},
            onOpenDetail = {},
            onOpenComposer = {},
            onRetry = {},
        )
    }
}

package fr.ziyon.campzone.ui.songbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.songbook.CantusArtist
import fr.ziyon.campzone.data.songbook.CantusSong
import fr.ziyon.campzone.data.songbook.CantusSongbook
import kotlinx.coroutines.launch

@Composable
fun CantusImportRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    songbookViewModel: SongbookViewModel,
    onBack: () -> Unit,
    onOpenManual: () -> Unit,
    viewModel: CantusImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val songbooks by viewModel.songbooks.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val selectedSongbook by viewModel.selectedSongbook.collectAsState()
    val selectedLanguageCode by viewModel.selectedLanguageCode.collectAsState()
    val selectedSlugs by viewModel.selectedSlugs.collectAsState()
    val isLoadingNextPage by viewModel.isLoadingNextPage.collectAsState()
    val catalogOperationError by viewModel.operationError.collectAsState()
    val songbookUiState by songbookViewModel.uiState.collectAsState()
    val songbookOperationError by songbookViewModel.operationError.collectAsState()
    val songbookOperationMessage by songbookViewModel.operationMessage.collectAsState()
    val isSaving by songbookViewModel.isSaving.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val importedSlugs = remember(songbookUiState, campingId) {
        songbookViewModel.importedCantusSlugs(campingId)
    }

    LaunchedEffect(campingId, authenticatedUser.uid) {
        songbookViewModel.loadIfNeeded(campingId, authenticatedUser)
        viewModel.loadInitial()
    }
    LaunchedEffect(catalogOperationError) {
        catalogOperationError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearOperationError()
        }
    }
    LaunchedEffect(songbookOperationError) {
        songbookOperationError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            songbookViewModel.clearOperationError()
        }
    }
    LaunchedEffect(songbookOperationMessage) {
        songbookOperationMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            songbookViewModel.clearOperationMessage()
        }
    }
    LaunchedEffect(importError) {
        importError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            importError = null
        }
    }

    CantusImportScreen(
        uiState = uiState,
        songs = songs,
        artists = artists,
        songbooks = songbooks,
        languageOptions = viewModel.languageOptions(),
        searchText = searchText,
        selectedArtist = selectedArtist,
        selectedSongbook = selectedSongbook,
        selectedLanguageCode = selectedLanguageCode,
        selectedSlugs = selectedSlugs,
        importedSlugs = importedSlugs,
        totalMatchCount = viewModel.totalMatchCount,
        hasActiveFilters = viewModel.hasActiveFilters,
        isLoadingNextPage = isLoadingNextPage,
        isImporting = isImporting || isSaving,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenManual = onOpenManual,
        onSearchChange = viewModel::updateSearch,
        onApplySearch = viewModel::applySearch,
        onSelectArtist = viewModel::selectArtist,
        onSelectSongbook = viewModel::selectSongbook,
        onSelectLanguage = viewModel::selectLanguage,
        onClearFilters = viewModel::clearFilters,
        onToggleSelection = viewModel::toggleSelection,
        onLoadNextPage = viewModel::loadNextPageIfNeeded,
        onRetry = { viewModel.reload(forceRefresh = true) },
        onImportSelected = {
            if (isImporting || isSaving) return@CantusImportScreen
            isImporting = true
            coroutineScope.launch {
                runCatching { viewModel.fetchSelectedDetails() }
                    .onSuccess { details ->
                        val currentImports = songbookViewModel.importedCantusSlugs(campingId)
                        val startIndex = songbookViewModel.songCount(campingId)
                        val drafts = details
                            .filterNot { it.slug in currentImports }
                            .mapIndexed { index, song -> song.songDraft(campingId, startIndex + index) }
                        songbookViewModel.importCantusDrafts(drafts, campingId) {
                            isImporting = false
                            viewModel.clearSelection()
                        }
                    }
                    .onFailure { error ->
                        isImporting = false
                        importError = viewModel.importErrorMessage(error)
                    }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CantusImportScreen(
    uiState: CantusImportUiState,
    songs: List<CantusSong>,
    artists: List<CantusArtist>,
    songbooks: List<CantusSongbook>,
    languageOptions: List<Pair<String, String>>,
    searchText: String,
    selectedArtist: CantusArtist?,
    selectedSongbook: CantusSongbook?,
    selectedLanguageCode: String?,
    selectedSlugs: Set<String>,
    importedSlugs: Set<String>,
    totalMatchCount: Int,
    hasActiveFilters: Boolean,
    isLoadingNextPage: Boolean,
    isImporting: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenManual: () -> Unit,
    onSearchChange: (String) -> Unit,
    onApplySearch: () -> Unit,
    onSelectArtist: (CantusArtist?) -> Unit,
    onSelectSongbook: (CantusSongbook?) -> Unit,
    onSelectLanguage: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onToggleSelection: (CantusSong) -> Unit,
    onLoadNextPage: (CantusSong) -> Unit,
    onRetry: () -> Unit,
    onImportSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val selectedCount = selectedSlugs.size

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.songbook_catalog_title),
                        style = CzTypeScale.headline,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenManual) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.songbook_add_manually), tint = colors.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
        bottomBar = {
            if (selectedCount > 0) {
                CatalogImportBar(
                    selectedCount = selectedCount,
                    isImporting = isImporting,
                    onImportSelected = onImportSelected,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            CatalogSearchAndFilters(
                searchText = searchText,
                artists = artists,
                songbooks = songbooks,
                languageOptions = languageOptions,
                selectedArtist = selectedArtist,
                selectedSongbook = selectedSongbook,
                selectedLanguageCode = selectedLanguageCode,
                hasActiveFilters = hasActiveFilters,
                onSearchChange = onSearchChange,
                onApplySearch = onApplySearch,
                onSelectArtist = onSelectArtist,
                onSelectSongbook = onSelectSongbook,
                onSelectLanguage = onSelectLanguage,
                onClearFilters = onClearFilters,
                modifier = Modifier.padding(horizontal = CzSpacing.lg),
            )

            when (uiState) {
                CantusImportUiState.Loading -> CzLoadingView(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(R.string.songbook_catalog_loading),
                )

                is CantusImportUiState.Error -> CzErrorState(
                    title = stringResource(R.string.songbook_catalog_load_failed_title),
                    message = uiState.message,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                CantusImportUiState.Empty -> CzEmptyState(
                    title = stringResource(R.string.songbook_catalog_no_songs_title),
                    message = stringResource(R.string.songbook_catalog_no_songs_message),
                    action = if (hasActiveFilters || searchText.isNotBlank()) {
                        {
                            CzButton(
                                text = stringResource(R.string.songbook_catalog_clear_filters),
                                onClick = onClearFilters,
                                variant = CzButtonVariant.Outline,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                is CantusImportUiState.Loaded -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = CzSpacing.lg,
                        end = CzSpacing.lg,
                        top = CzSpacing.xs,
                        bottom = if (selectedCount > 0) 112.dp else CzSpacing.xxxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        CatalogResultHeader(
                            totalMatchCount = totalMatchCount,
                            selectionCount = selectedCount,
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CzRadius.xxl))
                                .background(colors.card)
                                .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(CzRadius.xxl)),
                        ) {
                            songs.forEachIndexed { index, song ->
                                if (index == songs.lastIndex) {
                                    LaunchedEffect(song.slug) {
                                        onLoadNextPage(song)
                                    }
                                }
                                CatalogSongRow(
                                    song = song,
                                    isSelected = song.slug in selectedSlugs,
                                    isImported = song.slug in importedSlugs,
                                    onToggleSelection = { onToggleSelection(song) },
                                )
                                if (index != songs.lastIndex) {
                                    HorizontalDivider(
                                        color = colors.divider,
                                        modifier = Modifier.padding(start = CzSpacing.base + 28.dp + CzSpacing.md),
                                    )
                                }
                            }
                        }
                    }
                    if (isLoadingNextPage) {
                        item {
                            Text(
                                text = stringResource(R.string.songbook_catalog_load_more),
                                style = CzTypeScale.caption,
                                color = colors.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(CzSpacing.md),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSearchAndFilters(
    searchText: String,
    artists: List<CantusArtist>,
    songbooks: List<CantusSongbook>,
    languageOptions: List<Pair<String, String>>,
    selectedArtist: CantusArtist?,
    selectedSongbook: CantusSongbook?,
    selectedLanguageCode: String?,
    hasActiveFilters: Boolean,
    onSearchChange: (String) -> Unit,
    onApplySearch: () -> Unit,
    onSelectArtist: (CantusArtist?) -> Unit,
    onSelectSongbook: (CantusSongbook?) -> Unit,
    onSelectLanguage: (String?) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        TextField(
            value = searchText,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.songbook_catalog_search), color = colors.textSecondary) },
            textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onApplySearch() }),
            trailingIcon = {
                if (searchText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onSearchChange("")
                            onApplySearch()
                        },
                    ) {
                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.common_clear), tint = colors.textSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(CzRadius.md),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = colors.accent,
            ),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CatalogFilterMenu(
                label = selectedArtist?.name ?: stringResource(R.string.songbook_catalog_all_artists),
                options = listOf(null to stringResource(R.string.songbook_catalog_all_artists)) +
                    artists.map { artist -> artist to artist.name },
                onSelect = onSelectArtist,
                modifier = Modifier.weight(1f),
            )
            CatalogFilterMenu(
                label = languageOptions.firstOrNull { it.first == selectedLanguageCode }?.second
                    ?: stringResource(R.string.songbook_catalog_all_languages),
                options = listOf(null to stringResource(R.string.songbook_catalog_all_languages)) +
                    languageOptions.map { it.first to it.second },
                onSelect = onSelectLanguage,
                modifier = Modifier.weight(1f),
            )
            CatalogFilterMenu(
                label = selectedSongbook?.title ?: stringResource(R.string.songbook_catalog_all_songbooks),
                options = listOf(null to stringResource(R.string.songbook_catalog_all_songbooks)) +
                    songbooks.map { songbook -> songbook to songbook.title },
                onSelect = onSelectSongbook,
                modifier = Modifier.weight(1f),
            )
            if (hasActiveFilters || searchText.isNotBlank()) {
                IconButton(onClick = onClearFilters) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.songbook_catalog_clear_filters), tint = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun <T> CatalogFilterMenu(
    label: String,
    options: List<Pair<T?, String>>,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CzRadius.full))
                .background(colors.surface)
                .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(CzRadius.full))
                .clickable { expanded = true }
                .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = CzTypeScale.caption,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogResultHeader(
    totalMatchCount: Int,
    selectionCount: Int,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.songbook_catalog_song_count,
                totalMatchCount,
                totalMatchCount,
            ),
            style = CzTypeScale.caption2.copy(fontWeight = FontWeight.Bold),
            color = colors.textTertiary,
        )
        if (selectionCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.songbook_catalog_selected_count,
                    selectionCount,
                    selectionCount,
                ),
                style = CzTypeScale.caption2.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun CatalogSongRow(
    song: CantusSong,
    isSelected: Boolean,
    isImported: Boolean,
    onToggleSelection: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val enabled = !isImported
    val subtitle = listOfNotNull(
        song.artistName.takeUnless { it.isBlank() },
        song.languageDisplayName().takeUnless { it.isBlank() },
        song.songbooks.firstOrNull()?.let { book ->
            book.title?.takeUnless { it.isBlank() }?.let { title ->
                book.number?.let { "$title - $it" } ?: title
            }
        },
    ).joinToString(" - ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggleSelection)
            .padding(horizontal = CzSpacing.base, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected || isImported) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (isImported) colors.success else colors.accent,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isImported) colors.success.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.12f))
                        .padding(4.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.5.dp, colors.textTertiary), CircleShape),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = song.title,
                style = CzTypeScale.callout.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = CzTypeScale.caption,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isImported) {
            CatalogStatusPill(
                text = stringResource(R.string.songbook_catalog_added),
                toneColor = colors.success,
            )
        } else {
            song.key?.takeUnless { it.isBlank() }?.let { key ->
                CatalogStatusPill(
                    text = key,
                    toneColor = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun CatalogStatusPill(
    text: String,
    toneColor: Color,
) {
    val colors = MaterialTheme.czColors
    Text(
        text = text,
        style = CzTypeScale.caption2.copy(fontWeight = FontWeight.SemiBold),
        color = toneColor,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(CzRadius.full))
            .background(colors.surface)
            .padding(horizontal = CzSpacing.sm, vertical = 3.dp),
    )
}

@Composable
private fun CatalogImportBar(
    selectedCount: Int,
    isImporting: Boolean,
    onImportSelected: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .border(BorderStroke(1.dp, colors.divider.copy(alpha = 0.7f)))
            .navigationBarsPadding()
            .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
    ) {
        CzButton(
            text = pluralStringResource(
                R.plurals.songbook_catalog_add_count,
                selectedCount,
                selectedCount,
            ),
            onClick = onImportSelected,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedCount > 0,
            loading = isImporting,
            leadingIcon = {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
            },
        )
    }
}

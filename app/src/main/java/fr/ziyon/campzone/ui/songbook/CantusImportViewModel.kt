package fr.ziyon.campzone.ui.songbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.data.songbook.CantusArtist
import fr.ziyon.campzone.data.songbook.CantusPagination
import fr.ziyon.campzone.data.songbook.CantusService
import fr.ziyon.campzone.data.songbook.CantusSong
import fr.ziyon.campzone.data.songbook.CantusSongQuery
import fr.ziyon.campzone.data.songbook.CantusSongbook
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CantusImportUiState {
    data object Loading : CantusImportUiState
    data class Loaded(val songs: List<CantusSong>) : CantusImportUiState
    data object Empty : CantusImportUiState
    data class Error(val message: String) : CantusImportUiState
}

@HiltViewModel
class CantusImportViewModel @Inject constructor(
    private val service: CantusService,
    private val strings: StringProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CantusImportUiState>(CantusImportUiState.Loading)
    val uiState: StateFlow<CantusImportUiState> = _uiState.asStateFlow()

    private val _songs = MutableStateFlow<List<CantusSong>>(emptyList())
    val songs: StateFlow<List<CantusSong>> = _songs.asStateFlow()

    private val _artists = MutableStateFlow<List<CantusArtist>>(emptyList())
    val artists: StateFlow<List<CantusArtist>> = _artists.asStateFlow()

    private val _songbooks = MutableStateFlow<List<CantusSongbook>>(emptyList())
    val songbooks: StateFlow<List<CantusSongbook>> = _songbooks.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _selectedArtist = MutableStateFlow<CantusArtist?>(null)
    val selectedArtist: StateFlow<CantusArtist?> = _selectedArtist.asStateFlow()

    private val _selectedSongbook = MutableStateFlow<CantusSongbook?>(null)
    val selectedSongbook: StateFlow<CantusSongbook?> = _selectedSongbook.asStateFlow()

    private val _selectedLanguageCode = MutableStateFlow<String?>(null)
    val selectedLanguageCode: StateFlow<String?> = _selectedLanguageCode.asStateFlow()

    private val _selectedSlugs = MutableStateFlow<Set<String>>(emptySet())
    val selectedSlugs: StateFlow<Set<String>> = _selectedSlugs.asStateFlow()

    private val _isLoadingNextPage = MutableStateFlow(false)
    val isLoadingNextPage: StateFlow<Boolean> = _isLoadingNextPage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private var pagination: CantusPagination? = null
    private var loadedOnce = false

    val hasActiveFilters: Boolean
        get() = _selectedArtist.value != null || _selectedSongbook.value != null || _selectedLanguageCode.value != null

    val selectionCount: Int get() = _selectedSlugs.value.size
    val totalMatchCount: Int get() = pagination?.total ?: _songs.value.size

    fun languageOptions(locale: Locale = Locale.getDefault()): List<Pair<String, String>> {
        val codes = (_songbooks.value.mapNotNull { it.language } + _songs.value.mapNotNull { it.language })
            .filterNot { it.isBlank() }
            .distinct()
        return codes
            .map { code -> code to Locale.forLanguageTag(code).getDisplayLanguage(locale).replaceFirstChar(Char::titlecase) }
            .sortedBy { it.second }
    }

    fun loadInitial() {
        if (loadedOnce) return
        reload()
    }

    fun reload(forceRefresh: Boolean = false) {
        loadCatalog(forceRefresh = forceRefresh, showLoading = true, isRefresh = false)
    }

    fun refresh() {
        if (_isRefreshing.value) return
        loadCatalog(forceRefresh = true, showLoading = false, isRefresh = true)
    }

    private fun loadCatalog(
        forceRefresh: Boolean,
        showLoading: Boolean,
        isRefresh: Boolean,
    ) {
        viewModelScope.launch {
            if (showLoading) _uiState.value = CantusImportUiState.Loading
            if (isRefresh) _isRefreshing.value = true
            _operationError.value = null
            try {
                runCatching {
                    val page = service.songs(query(page = 1), forceRefresh)
                    loadFiltersIfNeeded(forceRefresh)
                    _songs.value = page.data
                    pagination = page.pagination
                    loadedOnce = true
                    publish()
                }.onFailure { error ->
                    val message = error.message ?: strings.get(R.string.songbook_catalog_load_failed)
                    if (showLoading || _songs.value.isEmpty()) {
                        _uiState.value = CantusImportUiState.Error(message)
                    } else {
                        _operationError.value = message
                    }
                }
            } finally {
                if (isRefresh) _isRefreshing.value = false
            }
        }
    }

    fun loadNextPageIfNeeded(currentSong: CantusSong) {
        if (_songs.value.lastOrNull()?.slug != currentSong.slug) return
        val nextPage = pagination?.nextPage ?: return
        if (_isLoadingNextPage.value) return

        viewModelScope.launch {
            _isLoadingNextPage.value = true
            runCatching {
                val page = service.songs(query(page = nextPage), forceRefresh = false)
                val known = _songs.value.map { it.slug }.toSet()
                _songs.value = _songs.value + page.data.filterNot { it.slug in known }
                pagination = page.pagination
                publish()
            }.onFailure { error ->
                _operationError.value = error.message ?: strings.get(R.string.songbook_catalog_load_failed)
            }
            _isLoadingNextPage.value = false
        }
    }

    fun updateSearch(text: String) {
        _searchText.value = text
    }

    fun applySearch() {
        reload()
    }

    fun selectArtist(artist: CantusArtist?) {
        if (_selectedArtist.value?.slug == artist?.slug) return
        _selectedArtist.value = artist
        reload()
    }

    fun selectSongbook(songbook: CantusSongbook?) {
        if (_selectedSongbook.value?.slug == songbook?.slug) return
        _selectedSongbook.value = songbook
        reload()
    }

    fun selectLanguage(code: String?) {
        if (_selectedLanguageCode.value == code) return
        _selectedLanguageCode.value = code
        reload()
    }

    fun clearFilters() {
        if (!hasActiveFilters && _searchText.value.isBlank()) return
        _searchText.value = ""
        _selectedArtist.value = null
        _selectedSongbook.value = null
        _selectedLanguageCode.value = null
        reload()
    }

    fun toggleSelection(song: CantusSong) {
        _selectedSlugs.value = if (song.slug in _selectedSlugs.value) {
            _selectedSlugs.value - song.slug
        } else {
            _selectedSlugs.value + song.slug
        }
    }

    fun clearSelection() {
        _selectedSlugs.value = emptySet()
    }

    fun selectedSongs(): List<CantusSong> =
        _songs.value.filter { it.slug in _selectedSlugs.value }

    suspend fun fetchSelectedDetails(): List<CantusSong> {
        val details = mutableListOf<CantusSong>()
        var lastError: Throwable? = null
        selectedSongs().forEach { song ->
            runCatching {
                service.songDetail(song.slug)
            }.onSuccess(details::add)
                .onFailure { lastError = it }
        }
        if (details.isEmpty() && lastError != null) {
            throw IllegalStateException(lastError?.message ?: strings.get(R.string.songbook_catalog_import_failed))
        }
        return details
    }

    fun clearOperationError() {
        _operationError.value = null
    }

    fun importErrorMessage(error: Throwable): String =
        error.message ?: strings.get(R.string.songbook_catalog_import_failed)

    private suspend fun loadFiltersIfNeeded(forceRefresh: Boolean) {
        if (!forceRefresh && _artists.value.isNotEmpty() && _songbooks.value.isNotEmpty()) return
        runCatching {
            _artists.value = service.artists(forceRefresh)
            _songbooks.value = service.songbooks(forceRefresh)
        }
    }

    private fun query(page: Int): CantusSongQuery =
        CantusSongQuery(
            searchText = _searchText.value,
            languageCode = _selectedLanguageCode.value,
            artistSlug = _selectedArtist.value?.slug,
            songbookSlug = _selectedSongbook.value?.slug,
            page = page,
        )

    private fun publish() {
        _uiState.value = if (_songs.value.isEmpty()) {
            CantusImportUiState.Empty
        } else {
            CantusImportUiState.Loaded(_songs.value)
        }
    }
}

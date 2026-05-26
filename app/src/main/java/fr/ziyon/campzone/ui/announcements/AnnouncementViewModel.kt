package fr.ziyon.campzone.ui.announcements

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.announcements.AnnouncementNotificationDispatcher
import fr.ziyon.campzone.data.announcements.AnnouncementService
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Announcement
import fr.ziyon.campzone.data.model.AnnouncementAttachment
import fr.ziyon.campzone.data.model.AnnouncementAttachmentKind
import fr.ziyon.campzone.data.model.AnnouncementAudienceScope
import fr.ziyon.campzone.data.model.AnnouncementDraft
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.PendingAnnouncementAttachment
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── UI state ─────────────────────────────────────────────────────────────────

sealed interface AnnouncementsUiState {
    data object Loading : AnnouncementsUiState
    data class Loaded(val announcements: List<Announcement>) : AnnouncementsUiState
    data class Empty(val searchActive: Boolean) : AnnouncementsUiState
    data class Error(val message: String) : AnnouncementsUiState
}

// ── Composer form ─────────────────────────────────────────────────────────────

data class AnnouncementComposerForm(
    val title: String = "",
    val body: String = "",
    val audienceScopeRawValue: String = AnnouncementAudienceScope.App.rawValue,
    val campingId: String? = null,
    val campingTitle: String? = null,
    val notificationTargetRoleRawValue: String? = null,
    val existingAttachments: List<AnnouncementAttachment> = emptyList(),
    val pendingAttachments: List<PendingAnnouncementAttachment> = emptyList(),
) {
    val audienceScope: AnnouncementAudienceScope
        get() = AnnouncementAudienceScope.fromWire(audienceScopeRawValue)

    val validationErrors: List<String>
        get() {
            val errors = mutableListOf<String>()
            if (title.isBlank()) errors.add("Title is required.")
            if (body.isBlank()) errors.add("Body is required.")
            if (audienceScope == AnnouncementAudienceScope.Camping && campingId.isNullOrBlank()) {
                errors.add("Select a camping for this announcement.")
            }
            return errors
        }

    val isValid: Boolean get() = validationErrors.isEmpty()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val announcementService: AnnouncementService,
    private val campingService: CampingService,
    private val notificationDispatcher: AnnouncementNotificationDispatcher,
) : ViewModel() {

    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("announcements_prefs", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow<AnnouncementsUiState>(AnnouncementsUiState.Loading)
    val uiState: StateFlow<AnnouncementsUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(AnnouncementComposerForm())
    val form: StateFlow<AnnouncementComposerForm> = _form.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _editingId = MutableStateFlow<String?>(null)
    val editingId: StateFlow<String?> = _editingId.asStateFlow()

    private val _campings = MutableStateFlow<List<Camping>>(emptyList())
    val campings: StateFlow<List<Camping>> = _campings.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _lastSeenAt = MutableStateFlow(0L)
    val lastSeenAt: StateFlow<Long> = _lastSeenAt.asStateFlow()

    val isEditing: Boolean get() = _editingId.value != null

    private var allAnnouncements: List<Announcement> = emptyList()
    private var hasLoaded = false
    private var campingsLoaded = false

    // Visibility context (mirrors iOS AnnouncementObserver.VisibilityContext)
    private var userRoleRawValue: String? = null
    private var visibleCampingIds: Set<String> = emptySet()
    private var canViewAll = false
    private var visibilityConfigured = false

    // ── Load ─────────────────────────────────────────────────────────────────

    fun loadIfNeeded() {
        if (!hasLoaded) load()
        if (!campingsLoaded) loadCampings()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = AnnouncementsUiState.Loading
            _operationError.value = null
            try {
                announcementService.loadAnnouncements().collect { list ->
                    allAnnouncements = list
                    hasLoaded = true
                    publishAnnouncements()
                }
            } catch (e: Exception) {
                _uiState.value = AnnouncementsUiState.Error(
                    e.message ?: "Failed to load announcements."
                )
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            _operationError.value = null
            try {
                val list = announcementService.loadAnnouncements().first()
                allAnnouncements = list
                hasLoaded = true
                publishAnnouncements()
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Failed to refresh."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun loadCampings() {
        campingsLoaded = true
        viewModelScope.launch {
            campingService.observeCampings().collect { list ->
                _campings.value = list
            }
        }
    }

    // ── Visibility ────────────────────────────────────────────────────────────

    fun configureVisibility(
        currentUser: AuthenticatedUser?,
        campings: List<Camping>,
        permissionUser: PermissionUser?,
        evaluator: AppPermissionEvaluator,
    ) {
        val newRoleRaw = permissionUser?.role?.rawValue
        val newCanViewAll = permissionUser?.role?.isAdmin == true
        val newCampingIds = campings.mapNotNull { camping ->
            val ctx = campingPermissionContext(camping)
            when {
                evaluator.canManageAnnouncements(permissionUser, ctx) -> camping.id
                currentUser != null && camping.attendees.any { it.userId == currentUser.uid } -> camping.id
                else -> null
            }
        }.toSet()

        if (visibilityConfigured &&
            newRoleRaw == userRoleRawValue &&
            newCanViewAll == canViewAll &&
            newCampingIds == visibleCampingIds
        ) return

        userRoleRawValue = newRoleRaw
        canViewAll = newCanViewAll
        visibleCampingIds = newCampingIds
        visibilityConfigured = true
        publishAnnouncements()
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun updateSearch(query: String) {
        _searchQuery.value = query
        publishAnnouncements()
    }

    // ── Announcement lookup ───────────────────────────────────────────────────

    fun announcementById(id: String): Announcement? =
        visibleAnnouncements().firstOrNull { it.id == id }

    fun canComposeAnnouncements(
        permissionUser: PermissionUser?,
        evaluator: AppPermissionEvaluator,
    ): Boolean {
        if (evaluator.can(permissionUser, fr.ziyon.campzone.core.permissions.AppPermission.CreateAnnouncements)) return true
        return _campings.value.any { camping ->
            evaluator.canManageAnnouncements(permissionUser, campingPermissionContext(camping))
        }
    }

    fun canManageAnnouncement(
        announcement: Announcement,
        permissionUser: PermissionUser?,
        evaluator: AppPermissionEvaluator,
    ): Boolean {
        if (evaluator.can(permissionUser, fr.ziyon.campzone.core.permissions.AppPermission.CreateAnnouncements)) return true
        val campingId = announcement.targetCampingId ?: return false
        val camping = _campings.value.firstOrNull { it.id == campingId } ?: return false
        return evaluator.canManageAnnouncements(permissionUser, campingPermissionContext(camping))
    }

    fun availableCampingsForCompose(
        permissionUser: PermissionUser?,
        evaluator: AppPermissionEvaluator,
    ): List<Camping> = _campings.value
        .filter { camping -> evaluator.canManageAnnouncements(permissionUser, campingPermissionContext(camping)) }
        .sortedByDescending { it.startDate }

    // ── Prepare ───────────────────────────────────────────────────────────────

    fun prepareNew() {
        _form.value = AnnouncementComposerForm()
        _editingId.value = null
        _operationError.value = null
    }

    fun prepareEdit(announcement: Announcement) {
        _form.value = AnnouncementComposerForm(
            title = announcement.title,
            body = announcement.body,
            audienceScopeRawValue = announcement.audienceScopeRawValue,
            campingId = announcement.campingId,
            campingTitle = announcement.campingTitle,
            notificationTargetRoleRawValue = announcement.notificationTargetRole?.rawValue,
            existingAttachments = announcement.attachments,
            pendingAttachments = emptyList(),
        )
        _editingId.value = announcement.id
        _operationError.value = null
    }

    // ── Form mutations ────────────────────────────────────────────────────────

    fun updateForm(update: (AnnouncementComposerForm) -> AnnouncementComposerForm) {
        _form.value = update(_form.value)
    }

    fun removeExistingAttachment(id: String) {
        _form.value = _form.value.copy(
            existingAttachments = _form.value.existingAttachments.filter { it.id != id }
        )
    }

    fun removePendingAttachment(id: String) {
        _form.value = _form.value.copy(
            pendingAttachments = _form.value.pendingAttachments.filter { it.id != id }
        )
    }

    fun addAttachmentFromUri(
        uri: android.net.Uri,
        kind: AnnouncementAttachmentKind,
        displayName: String,
    ) {
        viewModelScope.launch {
            val bytes = try {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            val contentType = when (kind) {
                AnnouncementAttachmentKind.Image -> "image/jpeg"
                AnnouncementAttachmentKind.Pdf -> "application/pdf"
            }
            val pending = PendingAnnouncementAttachment(
                id = UUID.randomUUID().toString(),
                kind = kind,
                fileName = displayName,
                contentType = contentType,
                bytes = bytes,
            )
            _form.value = _form.value.copy(
                pendingAttachments = _form.value.pendingAttachments + pending
            )
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveAnnouncement(
        currentUser: AuthenticatedUser?,
        onSuccess: () -> Unit,
    ) {
        val f = _form.value
        if (!f.isValid) return
        val campings = _campings.value

        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val scope = AnnouncementAudienceScope.fromWire(f.audienceScopeRawValue)
                val campingId = if (scope == AnnouncementAudienceScope.Camping) f.campingId else null
                val campingTitle = campingId?.let { id ->
                    campings.firstOrNull { it.id == id }?.title ?: f.campingTitle
                }
                val draft = AnnouncementDraft(
                    id = _editingId.value ?: UUID.randomUUID().toString(),
                    title = f.title.trim(),
                    body = f.body.trim(),
                    audienceScopeRawValue = scope.rawValue,
                    campingId = campingId?.trim()?.takeUnless { it.isBlank() },
                    campingTitle = campingTitle?.trim()?.takeUnless { it.isBlank() },
                    notificationTargetRoleRawValue = if (scope == AnnouncementAudienceScope.Camping) {
                        f.notificationTargetRoleRawValue
                    } else null,
                    authorId = currentUser?.uid ?: "system",
                    authorName = currentUser?.displayName ?: Announcement.DEFAULT_AUTHOR,
                    authorPhotoUrl = currentUser?.photoUrl,
                    existingAttachments = f.existingAttachments,
                    pendingAttachments = f.pendingAttachments,
                )
                val saved = announcementService.saveAnnouncement(draft)
                allAnnouncements = (allAnnouncements.filter { it.id != saved.id } + saved)
                    .sortedByDescending { it.createdAt?.time ?: 0 }
                publishAnnouncements()
                try {
                    notificationDispatcher.dispatchAnnouncement(saved)
                } catch (e: Exception) {
                    _operationError.value = "Announcement published, but notification dispatch failed."
                }
                _form.value = AnnouncementComposerForm()
                _editingId.value = null
                onSuccess()
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not save announcement. Please try again."
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun deleteAnnouncement(id: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val paths = allAnnouncements.firstOrNull { it.id == id }
                    ?.attachments?.map { it.storagePath } ?: emptyList()
                announcementService.deleteAnnouncement(id, paths)
                allAnnouncements = allAnnouncements.filter { it.id != id }
                publishAnnouncements()
                onSuccess()
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not delete announcement."
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ── Unread ────────────────────────────────────────────────────────────────

    fun markAnnouncementsSeen() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SEEN_AT, now).apply()
        _lastSeenAt.value = now
        _unreadCount.value = 0
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun visibleAnnouncements(): List<Announcement> =
        if (!visibilityConfigured) allAnnouncements
        else allAnnouncements.filter {
            it.isVisible(userRoleRawValue, visibleCampingIds, canViewAll)
        }

    private fun publishAnnouncements() {
        val query = _searchQuery.value.trim()
        val visible = visibleAnnouncements()
        val filtered = if (query.isEmpty()) visible
        else visible.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.body.contains(query, ignoreCase = true) ||
                it.authorName.contains(query, ignoreCase = true)
        }

        _uiState.value = if (filtered.isEmpty()) {
            AnnouncementsUiState.Empty(searchActive = query.isNotEmpty())
        } else {
            AnnouncementsUiState.Loaded(filtered)
        }

        updateUnreadCount()
    }

    private fun updateUnreadCount() {
        val lastSeen = prefs.getLong(KEY_LAST_SEEN_AT, 0L)
        _lastSeenAt.value = lastSeen
        _unreadCount.value = allAnnouncements.count { ann ->
            (ann.createdAt?.time ?: 0L) > lastSeen
        }
    }

    private fun campingPermissionContext(camping: Camping): CampingPermissionContext =
        CampingPermissionContext(
            organizerLevelType = camping.organizerLevel.type.wireValue,
            organizerLevelValue = camping.organizerLevel.value,
            createdByUid = camping.createdByUid,
        )

    private companion object {
        const val KEY_LAST_SEEN_AT = "last_seen_at"
    }
}

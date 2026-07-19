package fr.ziyon.campzone.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.PreferredLanguage
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.church.ChurchDirectory
import fr.ziyon.campzone.data.church.ChurchGroup
import fr.ziyon.campzone.data.church.SDAChurch
import fr.ziyon.campzone.data.profile.UserProfile
import fr.ziyon.campzone.data.profile.UserProfileRepository
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val loadedUser: UserProfile? = null,
    val form: ProfileFormState = ProfileFormState(),
    val validationErrors: List<ProfileValidationError> = emptyList(),
    val canEditRole: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val photoError: String? = null,
    val saveMessage: String? = null,
    val errorMessage: String? = null,
    val isProcessingDeletion: Boolean = false,
    val deletionMessage: String? = null,
    val deletionError: String? = null,
    val churches: List<SDAChurch> = emptyList(),
    val isLoadingChurches: Boolean = false,
    val churchError: String? = null,
    val churchQuery: String = "",
) {
    val isPendingDeletion: Boolean
        get() = loadedUser?.isPendingDeletion == true

    val deletionDaysRemaining: Int?
        get() {
            val graceEnds = loadedUser?.deletionGraceEnds ?: return null
            val seconds = (graceEnds.time - System.currentTimeMillis()) / 1000.0
            return maxOf(0, ceil(seconds / (24 * 60 * 60)).toInt())
        }

    val filteredChurchGroups: List<ChurchGroup>
        get() {
            val query = churchQuery.trim()
            val filtered = if (query.isBlank()) {
                churches
            } else {
                churches.filter { church ->
                    church.name.contains(query, ignoreCase = true) ||
                        church.city.contains(query, ignoreCase = true) ||
                        church.region.contains(query, ignoreCase = true) ||
                        church.country.contains(query, ignoreCase = true)
                }
            }
            return filtered
                .groupBy { it.country.ifBlank { "Other" } }
                .map { (country, churches) ->
                    ChurchGroup(
                        country = country,
                        churches = churches.sortedBy { it.name },
                    )
                }
                .sortedBy { it.country }
        }
}

data class ProfileFormState(
    val displayName: String = "",
    val ageText: String = "",
    val gender: UserGender? = UserGender.PreferNotToSay,
    val church: String = "",
    val skillsText: String = "",
    val allergies: List<String> = emptyList(),
    val profession: String = "",
    val education: String = "",
    val pathfinderRank: String = "",
    val phone: String = "",
    val email: String = "",
    val preferredLanguageCode: String = "",
    val languageCodes: List<String> = emptyList(),
    val role: UserRole = UserRole.User,
) {
    val displayLanguages: String
        get() = languageCodes.joinToString(", ")
}

enum class ProfileValidationError {
    DisplayNameRequired,
    AgeRequired,
    AgeOutOfRange,
    ChurchRequired,
    GenderRequired,
    EmailRequired,
    EmailInvalid,
    PreferredLanguageRequired,
    LanguageRequired,
    RoleNotSelfAssignable,
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: UserProfileRepository,
    private val churchDirectory: ChurchDirectory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun load(authenticatedUser: AuthenticatedUser) {
        if (_uiState.value.loadedUser?.uid == authenticatedUser.uid && !_uiState.value.isLoading) {
            return
        }

        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)
            runCatching {
                repository.fetchUser(
                    uid = authenticatedUser.uid,
                    fallback = authenticatedUser,
                )
            }.onSuccess { user ->
                _uiState.value = ProfileUiState(
                    isLoading = false,
                    loadedUser = user,
                    form = ProfileFormState(user),
                    canEditRole = user.role.isSelfAssignable,
                )
            }.onFailure { error ->
                _uiState.value = ProfileUiState(
                    isLoading = false,
                    errorMessage = error.friendlyProfileMessage(),
                )
            }
        }
    }

    fun updateDisplayName(value: String) = updateForm { copy(displayName = value) }
    fun updateAgeText(value: String) = updateForm { copy(ageText = value.filter(Char::isDigit).take(3)) }
    fun updateGender(value: UserGender?) = updateForm { copy(gender = value) }
    fun updateChurch(value: String) = updateForm { copy(church = value) }
    fun updateSkillsText(value: String) = updateForm { copy(skillsText = value) }
    fun updateAllergies(value: List<String>) = updateForm { copy(allergies = value) }
    fun updateProfession(value: String) = updateForm { copy(profession = value) }
    fun updateEducation(value: String) = updateForm { copy(education = value) }
    fun updatePathfinderRank(value: String) = updateForm { copy(pathfinderRank = value) }
    fun updatePhone(value: String) = updateForm { copy(phone = value) }
    fun updateEmail(value: String) = updateForm { copy(email = value) }

    fun updateChurchQuery(value: String) {
        _uiState.value = _uiState.value.copy(churchQuery = value)
    }

    fun loadChurches() {
        val state = _uiState.value
        if (state.churches.isNotEmpty() || state.isLoadingChurches) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingChurches = true,
                churchError = null,
            )
            runCatching { churchDirectory.loadChurches() }
                .onSuccess { churches ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingChurches = false,
                        churches = churches,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingChurches = false,
                        churchError = error.friendlyChurchMessage(),
                    )
                }
        }
    }

    fun updatePreferredLanguage(code: String) = updateForm {
        val nextLanguages = if (languageCodes.contains(code)) languageCodes else listOf(code) + languageCodes
        copy(
            preferredLanguageCode = code,
            languageCodes = nextLanguages.distinct(),
        )
    }

    fun toggleLanguage(code: String) = updateForm {
        val nextLanguages = if (languageCodes.contains(code)) {
            languageCodes - code
        } else {
            languageCodes + code
        }
        val nextPreferred = when {
            nextLanguages.contains(preferredLanguageCode) -> preferredLanguageCode
            nextLanguages.isNotEmpty() -> nextLanguages.first()
            else -> ""
        }
        copy(
            preferredLanguageCode = nextPreferred,
            languageCodes = nextLanguages.distinct(),
        )
    }

    fun updateRole(role: UserRole) = updateForm {
        copy(role = role)
    }

    fun save() {
        val loadedUser = _uiState.value.loadedUser ?: return
        val validationErrors = validate(_uiState.value.form, _uiState.value.canEditRole)
        if (validationErrors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                validationErrors = validationErrors,
                saveMessage = null,
                errorMessage = null,
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                saveMessage = null,
                errorMessage = null,
                validationErrors = emptyList(),
            )
            val user = _uiState.value.form.toUserProfile(
                existingUser = loadedUser,
                canEditRole = _uiState.value.canEditRole,
            )

            runCatching { repository.saveUser(user) }
                .onSuccess { savedUser ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        loadedUser = savedUser,
                        form = ProfileFormState(savedUser),
                        canEditRole = savedUser.role.isSelfAssignable,
                        saveMessage = "Profile changes saved",
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = error.friendlyProfileMessage(),
                    )
                }
        }
    }

    fun uploadPhoto(
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ) {
        val loadedUser = _uiState.value.loadedUser ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploadingPhoto = true,
                photoError = null,
                saveMessage = null,
            )
            runCatching {
                repository.uploadProfilePhoto(
                    user = loadedUser,
                    bytes = bytes,
                    mimeType = mimeType,
                    fileExtension = fileExtension,
                )
            }.onSuccess { savedUser ->
                _uiState.value = _uiState.value.copy(
                    isUploadingPhoto = false,
                    loadedUser = savedUser,
                    form = ProfileFormState(savedUser),
                    canEditRole = savedUser.role.isSelfAssignable,
                    saveMessage = "Profile changes saved",
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isUploadingPhoto = false,
                    photoError = error.friendlyProfileMessage(),
                )
            }
        }
    }

    fun reportPhotoError(message: String) {
        _uiState.value = _uiState.value.copy(photoError = message)
    }

    fun requestAccountDeletion(onSuccess: () -> Unit) {
        val uid = _uiState.value.loadedUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessingDeletion = true,
                deletionError = null,
                deletionMessage = null,
            )
            runCatching { repository.requestAccountDeletion(uid) }
                .onSuccess { updated ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingDeletion = false,
                        loadedUser = updated,
                        deletionMessage = "Account scheduled for deletion. You have 30 days to reactivate.",
                    )
                    onSuccess()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingDeletion = false,
                        deletionError = error.friendlyProfileMessage(),
                    )
                }
        }
    }

    fun cancelAccountDeletion() {
        val uid = _uiState.value.loadedUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessingDeletion = true,
                deletionError = null,
                deletionMessage = null,
            )
            runCatching { repository.cancelAccountDeletion(uid) }
                .onSuccess { updated ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingDeletion = false,
                        loadedUser = updated,
                        deletionMessage = "Account reactivated.",
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingDeletion = false,
                        deletionError = error.friendlyProfileMessage(),
                    )
                }
        }
    }

    fun dismissMessages() {
        _uiState.value = _uiState.value.copy(
            saveMessage = null,
            errorMessage = null,
            photoError = null,
            deletionMessage = null,
            deletionError = null,
        )
    }

    private fun updateForm(update: ProfileFormState.() -> ProfileFormState) {
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.update(),
            validationErrors = emptyList(),
            saveMessage = null,
        )
    }

    private fun validate(
        form: ProfileFormState,
        canEditRole: Boolean,
    ): List<ProfileValidationError> {
        val errors = mutableListOf<ProfileValidationError>()
        val ageText = form.ageText.trim()
        val email = form.email.trim()

        if (form.displayName.trim().isEmpty()) errors += ProfileValidationError.DisplayNameRequired
        if (ageText.isEmpty()) {
            errors += ProfileValidationError.AgeRequired
        } else if (ageText.toIntOrNull()?.let { it !in 10..120 } != false) {
            errors += ProfileValidationError.AgeOutOfRange
        }
        if (form.church.trim().isEmpty()) errors += ProfileValidationError.ChurchRequired
        if (form.gender == null) errors += ProfileValidationError.GenderRequired
        if (email.isEmpty()) {
            errors += ProfileValidationError.EmailRequired
        } else if (!email.isValidEmail()) {
            errors += ProfileValidationError.EmailInvalid
        }
        if (form.preferredLanguageCode.trim().isEmpty()) {
            errors += ProfileValidationError.PreferredLanguageRequired
        }
        if (form.normalizedLanguageCodes().isEmpty()) {
            errors += ProfileValidationError.LanguageRequired
        }
        if (canEditRole && !form.role.isSelfAssignable) {
            errors += ProfileValidationError.RoleNotSelfAssignable
        }

        return errors
    }

    private fun Throwable.friendlyProfileMessage(): String =
        message?.takeUnless { it.isBlank() } ?: "Profile could not be saved. Please try again."

    private fun Throwable.friendlyChurchMessage(): String =
        message?.takeUnless { it.isBlank() } ?: "Churches could not be loaded. Please try again."
}

private fun ProfileFormState(user: UserProfile): ProfileFormState =
    ProfileFormState(
        displayName = user.displayName,
        ageText = user.age?.toString().orEmpty(),
        gender = user.gender ?: UserGender.PreferNotToSay,
        church = user.church,
        skillsText = user.skills.joinToString(", "),
        allergies = user.allergies,
        profession = user.profession,
        education = user.education,
        pathfinderRank = user.pathfinderRank,
        phone = user.phone,
        email = user.email,
        preferredLanguageCode = user.preferredLanguage
            .takeUnless { it.isBlank() }
            ?: user.languages.firstOrNull()
            ?: PreferredLanguage.defaultForLocale(Locale.getDefault()).wireValue,
        languageCodes = user.languages.ifEmpty {
            user.preferredLanguage.takeUnless { it.isBlank() }?.let(::listOf).orEmpty()
        },
        role = user.role,
    )

private fun ProfileFormState.toUserProfile(
    existingUser: UserProfile,
    canEditRole: Boolean,
): UserProfile {
    val languageCodes = normalizedLanguageCodes()
    val preferredLanguage = preferredLanguageCode.trim()
        .takeUnless { it.isBlank() }
        ?: languageCodes.firstOrNull()
        ?: ""

    return existingUser.copy(
        displayName = displayName.trim(),
        age = ageText.trim().toIntOrNull(),
        gender = gender,
        church = church.trim(),
        skills = skillsText.cleanedCsv(),
        allergies = fr.ziyon.campzone.data.profile.AllergyFormatter.cleaned(allergies),
        profession = profession.trim(),
        education = education.trim(),
        pathfinderRank = pathfinderRank.trim(),
        phone = phone.trim(),
        email = email.trim(),
        preferredLanguage = preferredLanguage,
        languages = if (languageCodes.isEmpty() && preferredLanguage.isNotBlank()) {
            listOf(preferredLanguage)
        } else {
            languageCodes
        },
        role = if (canEditRole) role else existingUser.role,
        onboardingCompleted = true,
    )
}

private fun ProfileFormState.normalizedLanguageCodes(): List<String> {
    val typedCodes = displayLanguages.cleanedCsv()
    val selectedCodes = languageCodes.mapNotNull { code ->
        code.trim().takeUnless { it.isBlank() }
    }
    val base = if (selectedCodes.isEmpty()) typedCodes else selectedCodes
    val preferred = preferredLanguageCode.trim().takeUnless { it.isBlank() }
    return base.distinct().sortedWith { lhs, rhs ->
        when {
            lhs == preferred -> -1
            rhs == preferred -> 1
            else -> lhs.compareTo(rhs)
        }
    }
}

private fun String.cleanedCsv(): List<String> =
    split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

private fun String.isValidEmail(): Boolean {
    val parts = split("@")
    return parts.size == 2 && parts[1].contains(".")
}

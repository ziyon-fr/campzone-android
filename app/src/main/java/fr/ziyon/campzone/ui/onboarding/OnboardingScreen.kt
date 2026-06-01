package fr.ziyon.campzone.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.OnboardingProfile
import fr.ziyon.campzone.data.auth.PreferredLanguage
import fr.ziyon.campzone.data.auth.UserGender

private val OnboardingNight = Color(0xFF070E1A)
private val OnboardingTwilight = Color(0xFF1A0E30)
private val OnboardingBottom = Color(0xFF2D1005)
private val OnboardingCream = Color(0xFFFFF4E0)
private val OnboardingAmber = Color(0xFFFFB347)
private val OnboardingEmber = Color(0xFFFF6B35)
private val OnboardingDivider = Color(0x1FFFFFFF)

@Composable
fun OnboardingScreen(
    user: AuthenticatedUser,
    isCompleting: Boolean,
    errorMessage: String?,
    onComplete: (OnboardingProfile) -> Unit,
    onSignOut: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var ageText by rememberSaveable { mutableStateOf(user.age?.toString().orEmpty()) }
    var church by rememberSaveable { mutableStateOf(user.church) }
    var preferredLanguageCode by rememberSaveable {
        mutableStateOf(
            user.preferredLanguage
                .takeUnless { it.isBlank() }
                ?: PreferredLanguage.defaultForLocale().wireValue,
        )
    }
    var genderWire by rememberSaveable {
        mutableStateOf((user.gender ?: UserGender.PreferNotToSay).wireValue)
    }
    val focusManager = LocalFocusManager.current
    val age = ageText.trim().toIntOrNull()
    val isAgeValid = age?.let { it in 10..120 } == true
    val isChurchValid = church.trim().isNotEmpty()
    val selectedLanguage = PreferredLanguage.fromWire(preferredLanguageCode)
        ?: PreferredLanguage.defaultForLocale()
    val selectedGender = UserGender.fromWire(genderWire) ?: UserGender.PreferNotToSay
    val canAdvance = if (step == 0) isAgeValid else isChurchValid && !isCompleting

    Box(modifier = modifier.fillMaxSize()) {
        OnboardingBackground(modifier = Modifier.fillMaxSize())

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = CzSpacing.xl, vertical = CzSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OnboardingHeader(step = step)

                AnimatedContent(
                    targetState = step,
                    modifier = Modifier
                        .padding(top = CzSpacing.xxl)
                        .widthIn(max = 430.dp)
                        .fillMaxWidth(),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith
                            fadeOut(animationSpec = tween(120))
                    },
                    label = "onboarding-step",
                ) { targetStep ->
                    OnboardingCard {
                        if (targetStep == 0) {
                            AboutYouStep(
                                ageText = ageText,
                                onAgeChange = { value ->
                                    ageText = value.filter(Char::isDigit).take(3)
                                },
                                ageGroup = age?.let(CampingAgeGroup::fromAge),
                                selectedGender = selectedGender,
                                onGenderChange = { genderWire = it.wireValue },
                                onDone = { focusManager.clearFocus() },
                            )
                        } else {
                            CommunityStep(
                                church = church,
                                onChurchChange = { church = it },
                                selectedLanguage = selectedLanguage,
                                onLanguageChange = { preferredLanguageCode = it.wireValue },
                                errorMessage = errorMessage,
                                onDismissError = onDismissError,
                            )
                        }
                    }
                }

                BottomBar(
                    step = step,
                    canAdvance = canAdvance,
                    isCompleting = isCompleting,
                    onPrimary = {
                        focusManager.clearFocus()
                        if (step == 0) {
                            step = 1
                        } else {
                            onComplete(
                                OnboardingProfile(
                                    age = age,
                                    church = church,
                                    preferredLanguage = selectedLanguage.wireValue,
                                    gender = selectedGender,
                                ),
                            )
                        }
                    },
                    onBack = { step = 0 },
                    onSignOut = onSignOut,
                    modifier = Modifier
                        .padding(top = CzSpacing.xl)
                        .widthIn(max = 430.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OnboardingBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(OnboardingNight)) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to OnboardingNight,
                0.5f to OnboardingTwilight,
                1f to OnboardingBottom,
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(OnboardingEmber.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width / 2f, size.height * 0.78f),
                radius = size.minDimension * 0.62f,
            ),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width / 2f, size.height * 0.78f),
        )
    }
}

@Composable
private fun OnboardingHeader(step: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Text(
                text = if (step == 0) "Sobre você" else "Sua comunidade",
                color = OnboardingAmber,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (step == 0) {
                    "Usaremos isso para colocar você no grupo certo"
                } else {
                    "Conecte-se com sua família da igreja no camp"
                },
                color = OnboardingAmber.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .width(if (index == step) 28.dp else 8.dp)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(if (index <= step) OnboardingEmber else OnboardingDivider),
                )
            }
        }
    }
}

@Composable
private fun OnboardingCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(CzRadius.xl)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f), shape)
            .border(BorderStroke(1.dp, OnboardingDivider), shape)
            .padding(CzSpacing.xl),
    ) {
        content()
    }
}

@Composable
private fun AboutYouStep(
    ageText: String,
    onAgeChange: (String) -> Unit,
    ageGroup: CampingAgeGroup?,
    selectedGender: UserGender,
    onGenderChange: (UserGender) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        OnboardingField(title = stringResource(R.string.onboarding_age)) {
            OutlinedTextField(
                value = ageText,
                onValueChange = onAgeChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.onboarding_age_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                leadingIcon = { Text("#", color = OnboardingAmber) },
                trailingIcon = {
                    if (ageGroup != null) {
                        Text(stringResource(R.string.common_ok), color = OnboardingAmber, style = MaterialTheme.typography.labelMedium)
                    }
                },
                shape = RoundedCornerShape(CzRadius.md),
                colors = onboardingTextFieldColors(),
            )
        }

        AgeGroupRow(ageGroup = ageGroup)

        OnboardingField(title = stringResource(R.string.onboarding_gender)) {
            GenderSegmentedControl(
                selectedGender = selectedGender,
                onGenderChange = onGenderChange,
            )
        }
    }
}

@Composable
private fun AgeGroupRow(ageGroup: CampingAgeGroup?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text("⚙", color = OnboardingAmber, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.onboarding_age_group),
            color = OnboardingAmber,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(OnboardingEmber.copy(alpha = if (ageGroup != null) 0.14f else 0f))
                .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
        ) {
            Text(
                text = ageGroup?.displayName ?: "Digite a idade acima",
                color = if (ageGroup != null) OnboardingEmber else OnboardingAmber.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
/*
 review@ziyon.fr
 @ReviewTeam2026
 */
@Composable
private fun GenderSegmentedControl(
    selectedGender: UserGender,
    onGenderChange: (UserGender) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, OnboardingDivider, RoundedCornerShape(CzRadius.md))
            .padding(CzSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        UserGender.entries.forEach { gender ->
            val selected = gender == selectedGender
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(CzRadius.sm))
                    .background(if (selected) OnboardingEmber else Color.Transparent)
                    .clickable { onGenderChange(gender) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (gender) {
                        UserGender.Female -> "Fem."
                        UserGender.Male -> "Masc."
                        UserGender.PreferNotToSay -> "Prefiro"
                    },
                    color = if (selected) Color.White else OnboardingCream,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CommunityStep(
    church: String,
    onChurchChange: (String) -> Unit,
    selectedLanguage: PreferredLanguage,
    onLanguageChange: (PreferredLanguage) -> Unit,
    errorMessage: String?,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        OnboardingField(title = stringResource(R.string.onboarding_church)) {
            OutlinedTextField(
                value = church,
                onValueChange = onChurchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.onboarding_church_placeholder)) },
                singleLine = true,
                leadingIcon = { Text("⌂", color = OnboardingEmber) },
                trailingIcon = {
                    if (church.trim().isNotEmpty()) {
                        Text(stringResource(R.string.common_ok), color = OnboardingEmber, style = MaterialTheme.typography.labelMedium)
                    }
                },
                shape = RoundedCornerShape(CzRadius.md),
                colors = onboardingTextFieldColors(),
            )
        }

        OnboardingField(title = stringResource(R.string.onboarding_preferred_language)) {
            LanguageMenu(
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
            )
        }

        if (errorMessage != null) {
            OnboardingErrorBanner(message = errorMessage, onDismiss = onDismissError)
        }
    }
}

@Composable
private fun LanguageMenu(
    selectedLanguage: PreferredLanguage,
    onLanguageChange: (PreferredLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(CzRadius.md))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, OnboardingDivider, RoundedCornerShape(CzRadius.md))
                .clickable { expanded = true }
                .padding(CzSpacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLanguage.displayName,
                modifier = Modifier.weight(1f),
                color = OnboardingCream,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("⌄", color = OnboardingAmber, style = MaterialTheme.typography.titleSmall)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 320.dp)
                .background(OnboardingNight),
        ) {
            PreferredLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language.displayName,
                            color = OnboardingCream,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        onLanguageChange(language)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OnboardingField(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = title.uppercase(),
            color = OnboardingAmber,
            style = MaterialTheme.typography.labelSmall,
        )
        content()
    }
}

@Composable
private fun BottomBar(
    step: Int,
    canAdvance: Boolean,
    isCompleting: Boolean,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Button(
            onClick = onPrimary,
            enabled = canAdvance && !isCompleting,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OnboardingEmber,
                contentColor = Color.White,
                disabledContainerColor = OnboardingEmber.copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.7f),
            ),
        ) {
            if (isCompleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (step == 0) "Próximo" else "Começar",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.width(CzSpacing.sm))
                    Text(if (step == 0) "→" else "▲")
                }
            }
        }

        AnimatedVisibility(visible = step > 0) {
            TextButton(
                onClick = onBack,
                enabled = !isCompleting,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = OnboardingAmber.copy(alpha = 0.72f),
                    disabledContentColor = OnboardingAmber.copy(alpha = 0.35f),
                ),
            ) {
                Text(stringResource(R.string.onboarding_back), style = MaterialTheme.typography.bodyMedium)
            }
        }

        AnimatedVisibility(visible = step == 0) {
            TextButton(
                onClick = onSignOut,
                enabled = !isCompleting,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.czColors.error.copy(alpha = 0.7f),
                    disabledContentColor = MaterialTheme.czColors.error.copy(alpha = 0.35f),
                ),
            ) {
                Text(stringResource(R.string.onboarding_sign_out), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun OnboardingErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.sm))
            .background(MaterialTheme.czColors.error.copy(alpha = 0.12f))
            .padding(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text("!", color = MaterialTheme.czColors.error, style = MaterialTheme.typography.labelLarge)
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.czColors.error,
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(
            onClick = onDismiss,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.czColors.error),
        ) {
            Text(stringResource(R.string.common_ok), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun onboardingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OnboardingCream,
    unfocusedTextColor = OnboardingCream,
    disabledTextColor = OnboardingCream.copy(alpha = 0.45f),
    focusedContainerColor = Color.White.copy(alpha = 0.09f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
    disabledContainerColor = Color.White.copy(alpha = 0.04f),
    cursorColor = OnboardingAmber,
    focusedBorderColor = OnboardingEmber.copy(alpha = 0.65f),
    unfocusedBorderColor = OnboardingDivider,
    focusedLabelColor = OnboardingAmber,
    unfocusedLabelColor = OnboardingAmber,
    focusedPlaceholderColor = OnboardingAmber.copy(alpha = 0.55f),
    unfocusedPlaceholderColor = OnboardingAmber.copy(alpha = 0.55f),
)

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    CampzoneTheme(darkTheme = true) {
        OnboardingScreen(
            user = AuthenticatedUser(
                uid = "preview",
                email = "preview@example.com",
                displayName = "Preview Camper",
                photoUrl = null,
                role = UserRole.Guest,
                church = "",
                age = null,
                preferredLanguage = "",
                gender = null,
                onboardingCompleted = false,
            ),
            isCompleting = false,
            errorMessage = null,
            onComplete = {},
            onSignOut = {},
            onDismissError = {},
        )
    }
}

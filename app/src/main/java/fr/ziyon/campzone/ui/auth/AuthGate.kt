package fr.ziyon.campzone.ui.auth

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.navigation.CampzoneNavigationShell
import fr.ziyon.campzone.core.navigation.DeepLinkInbox
import fr.ziyon.campzone.data.auth.AuthState
import fr.ziyon.campzone.ui.notifications.NotificationBootstrapViewModel
import fr.ziyon.campzone.ui.onboarding.OnboardingScreen

private val AuthNight = Color(0xFF070E1A)
private val AuthTwilight = Color(0xFF1A0E30)
private val AuthCampfireBottom = Color(0xFF2D1005)
private val AuthCream = Color(0xFFFFF4E0)
private val AuthAmber = Color(0xFFFFB347)
private val AuthEmber = Color(0xFFFF6B35)
private val AuthPine = Color(0xFF243824)
private val AuthLeaf = Color(0xFF4A7C59)
private val AuthDivider = Color(0x1FFFFFFF)
private const val CampzonePrivacyUrl = "https://campzone-web.vercel.app/privacy"

@Composable
fun AuthGate(
    deepLinkInbox: DeepLinkInbox,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current.findActivity()

    when (authState) {
        AuthState.SignedOut -> AuthScreen(
            uiState = uiState,
            onGoogleSignIn = { activity?.let(viewModel::signInWithGoogle) },
            onAppleSignIn = { activity?.let(viewModel::signInWithApple) },
            onEmailSignIn = viewModel::signInWithEmail,
            onEmailSignUp = viewModel::signUpWithEmail,
            onPasswordReset = viewModel::sendPasswordReset,
            onDismissError = viewModel::dismissError,
            onDismissEmailResetMessage = viewModel::dismissEmailResetMessage,
            modifier = modifier,
        )

        is AuthState.OnboardingIncomplete -> {
            val onboardingState = authState as AuthState.OnboardingIncomplete
            OnboardingScreen(
                user = onboardingState.user,
                isCompleting = uiState.isCompletingOnboarding,
                errorMessage = uiState.errorMessage,
                onComplete = { profile ->
                    viewModel.completeOnboarding(
                        user = onboardingState.user,
                        profile = profile,
                    )
                },
                onSignOut = viewModel::signOut,
                onDismissError = viewModel::dismissError,
                modifier = modifier,
            )
        }

        is AuthState.SignedIn -> {
            val signedInState = authState as AuthState.SignedIn
            RequestNotificationPermissionAfterOnboarding()
            val bootstrapViewModel: NotificationBootstrapViewModel = hiltViewModel()
            LaunchedEffect(signedInState.user.uid) {
                bootstrapViewModel.registerDevice(
                    uid = signedInState.user.uid,
                    role = signedInState.user.role,
                )
            }
            CampzoneNavigationShell(
                deepLinkInbox = deepLinkInbox,
                authenticatedUser = signedInState.user,
                onSignOut = viewModel::signOut,
                modifier = modifier.fillMaxSize(),
                authReady = true,
            )
        }
    }
}

@Composable
private fun RequestNotificationPermissionAfterOnboarding() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(permission)
        }
    }
}

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    onEmailSignIn: (String, String) -> Unit,
    onEmailSignUp: (String, String, String?) -> Unit,
    onPasswordReset: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismissEmailResetMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AuthNightBackground(modifier = Modifier.fillMaxSize())

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
                    .padding(horizontal = CzSpacing.xl, vertical = CzSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AuthBrandSection()

                AuthGlassPanel(
                    uiState = uiState,
                    onGoogleSignIn = onGoogleSignIn,
                    onAppleSignIn = onAppleSignIn,
                    onEmailSignIn = onEmailSignIn,
                    onEmailSignUp = onEmailSignUp,
                    onPasswordReset = onPasswordReset,
                    onDismissError = onDismissError,
                    onDismissEmailResetMessage = onDismissEmailResetMessage,
                    modifier = Modifier.padding(top = CzSpacing.xxl),
                )
            }
        }
    }
}

@Composable
private fun AuthNightBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(AuthNight)) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to AuthNight,
                0.5f to AuthTwilight,
                1f to AuthCampfireBottom,
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AuthEmber.copy(alpha = 0.2f), Color.Transparent),
                center = Offset(size.width / 2f, size.height),
                radius = size.minDimension * 0.95f,
            ),
            radius = size.minDimension * 0.95f,
            center = Offset(size.width / 2f, size.height),
        )
        authStars.forEach { star ->
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.radius,
                center = Offset(star.x * size.width, star.y * size.height),
            )
        }
    }
}

@Composable
private fun AuthBrandSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = "Campzone",
            color = AuthCream,
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Bem-vindo(a)! A aventura espera.",
            color = AuthAmber.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AuthGlassPanel(
    uiState: AuthUiState,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    onEmailSignIn: (String, String) -> Unit,
    onEmailSignUp: (String, String, String?) -> Unit,
    onPasswordReset: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismissEmailResetMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CzRadius.xl)
    val isProviderBusy = uiState.isSigningInWithGoogle || uiState.isSigningInWithApple
    val isEmailBusy = uiState.isSigningInWithEmail || uiState.isSendingPasswordReset

    Column(
        modifier = modifier
            .widthIn(max = 430.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f), shape)
            .border(BorderStroke(1.dp, AuthDivider), shape)
            .padding(CzSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        if (uiState.errorMessage != null) {
            AuthMessageBanner(
                message = uiState.errorMessage,
                kind = AuthBannerKind.Error,
                actionLabel = "OK",
                onAction = onDismissError,
            )
        }

        AuthProviderButton(
            title = "Continuar com Apple",
            mark = "A",
            onClick = onAppleSignIn,
            isLoading = uiState.isSigningInWithApple,
            enabled = !uiState.isSigningInWithGoogle && !isEmailBusy,
            containerColor = Color.White,
            contentColor = Color(0xFF111111),
            borderColor = Color.White.copy(alpha = 0.7f),
        )

        AuthProviderButton(
            title = "Continuar com Google",
            mark = "G",
            onClick = onGoogleSignIn,
            isLoading = uiState.isSigningInWithGoogle,
            enabled = !uiState.isSigningInWithApple && !isEmailBusy,
            containerColor = AuthPine.copy(alpha = 0.72f),
            contentColor = AuthCream,
            borderColor = AuthLeaf.copy(alpha = 0.45f),
        )

        AuthEmailDivider()

        EmailAuthForm(
            enabled = !isProviderBusy,
            isBusy = uiState.isSigningInWithEmail,
            isSendingReset = uiState.isSendingPasswordReset,
            notice = uiState.emailResetMessage,
            onSignIn = onEmailSignIn,
            onSignUp = onEmailSignUp,
            onPasswordReset = onPasswordReset,
            onClearNotice = onDismissEmailResetMessage,
        )

        AuthLegalText()
    }
}

@Composable
private fun AuthProviderButton(
    title: String,
    mark: String,
    onClick: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(CzRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.55f),
        ),
        border = BorderStroke(1.dp, borderColor),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            } else {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = if (contentColor == Color.Black) 0.08f else 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mark,
                            color = contentColor,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.width(CzSpacing.sm))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthEmailDivider(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthAmber.copy(alpha = 0.7f)),
        )
        Text(
            text = "ou use e-mail",
            color = AuthAmber,
            style = MaterialTheme.typography.labelMedium,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthAmber.copy(alpha = 0.7f)),
        )
    }
}

private enum class EmailMode {
    SignIn,
    SignUp,
}

@Composable
private fun EmailAuthForm(
    enabled: Boolean,
    isBusy: Boolean,
    isSendingReset: Boolean,
    notice: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String?) -> Unit,
    onPasswordReset: (String) -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(EmailMode.SignIn) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val canSubmit = email.trim().isNotEmpty() && password.isNotBlank()
    val fieldsEnabled = enabled && !isBusy && !isSendingReset
    val submit = {
        if (canSubmit && fieldsEnabled) {
            focusManager.clearFocus()
            onClearNotice()
            if (mode == EmailMode.SignIn) {
                onSignIn(email, password)
            } else {
                onSignUp(
                    email,
                    password,
                    displayName.trim().takeUnless { it.isBlank() },
                )
            }
        }
    }

    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        AnimatedVisibility(visible = mode == EmailMode.SignUp) {
            AuthTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "Nome completo",
                placeholder = "Nome de exibição",
                glyph = FieldGlyph.Person,
                enabled = fieldsEnabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
        }

        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            placeholder = "you@ exemplo.com",
            glyph = FieldGlyph.Email,
            enabled = fieldsEnabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
        )

        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = "Senha",
            placeholder = "Sua senha",
            glyph = FieldGlyph.Lock,
            enabled = fieldsEnabled,
            visualTransformation = if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingContent = {
                IconButton(
                    onClick = { isPasswordVisible = !isPasswordVisible },
                    enabled = fieldsEnabled,
                    modifier = Modifier.semantics {
                        contentDescription = if (isPasswordVisible) {
                            "Ocultar senha"
                        } else {
                            "Mostrar senha"
                        }
                    },
                ) {
                    PasswordVisibilityGlyph(
                        visible = isPasswordVisible,
                        color = AuthAmber.copy(alpha = 0.7f),
                    )
                }
            },
        )

        Button(
            onClick = submit,
            enabled = canSubmit && fieldsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(CzRadius.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuthEmber,
                contentColor = Color.White,
                disabledContainerColor = AuthEmber.copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.72f),
            ),
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (mode == EmailMode.SignIn) "Entrar" else "Criar conta",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        if (mode == EmailMode.SignIn) {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onClearNotice()
                    onPasswordReset(email)
                },
                enabled = fieldsEnabled && !isSendingReset && email.trim().isNotEmpty(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = AuthAmber.copy(alpha = 0.72f),
                    disabledContentColor = AuthAmber.copy(alpha = 0.32f),
                ),
            ) {
                Text(
                    text = if (isSendingReset) "Enviando..." else "Esqueceu a senha?",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        AuthModeToggle(
            mode = mode,
            enabled = fieldsEnabled,
            onModeChange = { nextMode ->
                onClearNotice()
                mode = nextMode
            },
        )

        if (notice != null) {
            AuthMessageBanner(
                message = notice,
                kind = AuthBannerKind.Notice,
            )
        }
    }
}

@Composable
private fun AuthModeToggle(
    mode: EmailMode,
    enabled: Boolean,
    onModeChange: (EmailMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (mode == EmailMode.SignIn) "Não tem uma conta?" else "Já tem uma conta?",
            color = AuthAmber.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(
            onClick = {
                onModeChange(if (mode == EmailMode.SignIn) EmailMode.SignUp else EmailMode.SignIn)
            },
            enabled = enabled,
            colors = ButtonDefaults.textButtonColors(
                contentColor = AuthAmber,
                disabledContentColor = AuthAmber.copy(alpha = 0.4f),
            ),
        ) {
            Text(
                text = if (mode == EmailMode.SignIn) "Criar um" else "Entrar",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private enum class FieldGlyph {
    Person,
    Email,
    Lock,
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    glyph: FieldGlyph,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(CzRadius.xxl)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) AuthEmber.copy(alpha = 0.65f) else AuthDivider
    val borderWidth = if (isFocused) 1.5.dp else 1.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = label.uppercase(),
            color = AuthAmber,
            style = MaterialTheme.typography.labelSmall,
        )
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = if (isFocused) 0.09f else 0.06f), shape)
                .border(borderWidth, borderColor, shape)
                .padding(start = CzSpacing.base, end = CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldGlyphIcon(glyph = glyph, color = AuthAmber)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = CzSpacing.sm),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = AuthCream.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuthCream),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    cursorBrush = SolidColor(AuthAmber),
                )
            }
            trailingContent?.invoke()
        }
    }
}

@Composable
private fun FieldGlyphIcon(glyph: FieldGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val width = size.width
        val height = size.height

        when (glyph) {
            FieldGlyph.Person -> {
                drawCircle(
                    color = color,
                    radius = width * 0.17f,
                    center = Offset(width / 2f, height * 0.32f),
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(width * 0.2f, height * 0.48f),
                    size = Size(width * 0.6f, height * 0.5f),
                    style = stroke,
                )
            }

            FieldGlyph.Email -> {
                val topLeft = Offset(width * 0.14f, height * 0.24f)
                val glyphSize = Size(width * 0.72f, height * 0.52f)
                drawRoundRect(
                    color = color,
                    topLeft = topLeft,
                    size = glyphSize,
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = topLeft,
                    end = Offset(width / 2f, height * 0.55f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(topLeft.x + glyphSize.width, topLeft.y),
                    end = Offset(width / 2f, height * 0.55f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            FieldGlyph.Lock -> {
                val bodyTop = height * 0.44f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(width * 0.22f, bodyTop),
                    size = Size(width * 0.56f, height * 0.38f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                drawPath(
                    path = Path().apply {
                        moveTo(width * 0.34f, bodyTop)
                        cubicTo(
                            width * 0.34f,
                            height * 0.18f,
                            width * 0.66f,
                            height * 0.18f,
                            width * 0.66f,
                            bodyTop,
                        )
                    },
                    color = color,
                    style = stroke,
                )
            }
        }
    }
}

@Composable
private fun PasswordVisibilityGlyph(
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        drawOval(
            color = color,
            topLeft = Offset(size.width * 0.1f, size.height * 0.27f),
            size = Size(size.width * 0.8f, size.height * 0.46f),
            style = stroke,
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.12f,
            center = center,
            style = stroke,
        )
        if (visible) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.16f, size.height * 0.84f),
                end = Offset(size.width * 0.86f, size.height * 0.14f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private enum class AuthBannerKind {
    Error,
    Notice,
}

@Composable
private fun AuthMessageBanner(
    message: String,
    kind: AuthBannerKind,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val color = when (kind) {
        AuthBannerKind.Error -> MaterialTheme.czColors.error
        AuthBannerKind.Notice -> AuthAmber
    }

    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.sm))
            .background(color.copy(alpha = 0.12f))
            .padding(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text(
            text = if (kind == AuthBannerKind.Error) "!" else "i",
            color = color,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = color),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AuthLegalText(modifier: Modifier = Modifier) {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = AuthAmber),
    )
    val text = buildAnnotatedString {
        append("Ao continuar, você concorda com nossos ")
        withLink(LinkAnnotation.Url(CampzonePrivacyUrl, linkStyle)) {
            append("Termos de Serviço")
        }
        append(" e ")
        withLink(LinkAnnotation.Url(CampzonePrivacyUrl, linkStyle)) {
            append("Política de Privacidade")
        }
        append(".")
    }

    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = AuthAmber.copy(alpha = 0.58f),
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
    )
}

private data class AuthStar(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
)

private val authStars = listOf(
    AuthStar(0.04f, 0.04f, 1.4f, 0.85f),
    AuthStar(0.12f, 0.02f, 0.8f, 0.65f),
    AuthStar(0.22f, 0.06f, 1.6f, 0.9f),
    AuthStar(0.33f, 0.02f, 0.7f, 0.7f),
    AuthStar(0.44f, 0.05f, 1.9f, 1f),
    AuthStar(0.56f, 0.03f, 0.8f, 0.75f),
    AuthStar(0.67f, 0.07f, 1.3f, 0.85f),
    AuthStar(0.78f, 0.02f, 1f, 0.8f),
    AuthStar(0.89f, 0.06f, 1.5f, 0.75f),
    AuthStar(0.96f, 0.04f, 0.9f, 0.6f),
    AuthStar(0.08f, 0.14f, 0.8f, 0.55f),
    AuthStar(0.18f, 0.19f, 1.2f, 0.65f),
    AuthStar(0.30f, 0.17f, 0.7f, 0.5f),
    AuthStar(0.42f, 0.21f, 1.1f, 0.7f),
    AuthStar(0.55f, 0.15f, 0.9f, 0.6f),
    AuthStar(0.70f, 0.20f, 1.3f, 0.65f),
    AuthStar(0.82f, 0.16f, 0.7f, 0.55f),
    AuthStar(0.93f, 0.22f, 1f, 0.5f),
    AuthStar(0.06f, 0.28f, 1f, 0.45f),
    AuthStar(0.25f, 0.32f, 0.8f, 0.5f),
    AuthStar(0.50f, 0.27f, 1.4f, 0.55f),
    AuthStar(0.64f, 0.31f, 0.7f, 0.45f),
    AuthStar(0.80f, 0.29f, 1.1f, 0.5f),
    AuthStar(0.95f, 0.35f, 0.8f, 0.4f),
    AuthStar(0.15f, 0.40f, 0.9f, 0.35f),
    AuthStar(0.38f, 0.38f, 1.2f, 0.4f),
    AuthStar(0.72f, 0.42f, 0.8f, 0.35f),
    AuthStar(0.88f, 0.38f, 1f, 0.4f),
)

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    CampzoneTheme {
        AuthScreen(
            uiState = AuthUiState(),
            onGoogleSignIn = {},
            onAppleSignIn = {},
            onEmailSignIn = { _, _ -> },
            onEmailSignUp = { _, _, _ -> },
            onPasswordReset = {},
            onDismissError = {},
            onDismissEmailResetMessage = {},
        )
    }
}

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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.navigation.CampzoneNavigationShell
import fr.ziyon.campzone.core.navigation.DeepLinkInbox
import fr.ziyon.campzone.data.auth.AuthState
import fr.ziyon.campzone.ui.notifications.NotificationBootstrapViewModel
import fr.ziyon.campzone.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.delay
import kotlin.math.sin

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
    var showSplash by rememberSaveable { mutableStateOf(true) }

    if (showSplash) {
        CampzoneSplash(onFinished = { showSplash = false }, modifier = modifier)
    } else when (authState) {
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
private fun CampzoneSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    var sceneReady by remember { mutableStateOf(false) }
    var brandReady by remember { mutableStateOf(false) }
    var fireLit by remember { mutableStateOf(false) }

    // Continuous seconds since the first frame — drives the sun pulse and fire
    // flicker exactly like iOS's TimelineView(.animation).
    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                time = (now - start) / 1_000_000_000f
            }
        }
    }

    val sceneAlpha by animateFloatAsState(
        targetValue = if (sceneReady) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash-scene-alpha",
    )
    val sceneOffset by animateFloatAsState(
        targetValue = if (sceneReady) 0f else 16f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash-scene-offset",
    )
    val sunOffset by animateFloatAsState(
        targetValue = if (sceneReady) 0f else -32f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash-sun-offset",
    )
    val brandAlpha by animateFloatAsState(
        targetValue = if (brandReady) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "splash-brand-alpha",
    )
    val brandOffset by animateFloatAsState(
        targetValue = if (brandReady) 0f else 14f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "splash-brand-offset",
    )

    LaunchedEffect(Unit) {
        sceneReady = true
        delay(540)
        fireLit = true
        delay(260)
        brandReady = true
        delay(1_600)
        onFinished()
    }

    Box(modifier.fillMaxSize()) {
        SplashBackground(dark = dark, modifier = Modifier.fillMaxSize())

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sceneHeight = maxHeight * 0.44f
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    // Keep the camp scene clear of Android's (opaque) navigation
                    // bar — iOS only has to clear a thin transparent home
                    // indicator, so the bottom-anchored 44% scene would otherwise
                    // be cropped by the nav bar on Android.
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CzSpacing.xxxl),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    SplashCelestialBody(
                        dark = dark,
                        time = time,
                        modifier = Modifier
                            .padding(end = CzSpacing.xxl)
                            .size(72.dp)
                            .graphicsLayer {
                                alpha = sceneAlpha
                                translationY = sunOffset
                            },
                    )
                }

                Spacer(Modifier.weight(1f))

                SplashBranding(
                    dark = dark,
                    modifier = Modifier.graphicsLayer {
                        alpha = brandAlpha
                        translationY = brandOffset
                    },
                )

                Spacer(Modifier.weight(1f))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sceneHeight)
                        .graphicsLayer {
                            alpha = sceneAlpha
                            translationY = sceneOffset
                        },
                ) {
                    drawCampScene(dark = dark, fireLit = fireLit, time = time)
                }
            }
        }
    }
}

@Composable
private fun SplashBackground(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = if (dark) arrayOf(
                    0f to Color(0xFF060914),
                    0.28f to Color(0xFF100826),
                    0.52f to Color(0xFF29103E),
                    0.76f to Color(0xFF38160C),
                    1f to Color(0xFF160C06),
                ) else arrayOf(
                    0f to Color(0xFFB0D6F2),
                    0.28f to Color(0xFFD4E8F8),
                    0.55f to Color(0xFFF5D8A8),
                    0.76f to Color(0xFFE8C47E),
                    1f to Color(0xFFC4DEB4),
                ),
            ),
        )
        // Warm horizon bloom centred on the tree line (iOS SplashBackground).
        val bloomCenter = Offset(size.width * 0.5f, size.height * 0.62f)
        val bloomRadius = size.minDimension * 0.82f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    (if (dark) Color(0xFFFF6000) else Color(0xFFFFA030)).copy(alpha = 0.30f),
                    Color.Transparent,
                ),
                center = bloomCenter,
                radius = bloomRadius,
            ),
            radius = bloomRadius,
            center = bloomCenter,
        )
        if (dark) {
            authStars.forEach { star ->
                drawCircle(
                    color = Color.White.copy(alpha = star.alpha),
                    radius = star.radius,
                    center = Offset(star.x * size.width, star.y * size.height),
                )
            }
        }
    }
}

@Composable
private fun SplashCelestialBody(dark: Boolean, time: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (dark) {
            val pulse = 0.88f + sin((time * 0.55f).toDouble()).toFloat() * 0.12f
            val cx = w * 0.5f
            val cy = h * 0.5f
            val r = w * 0.34f
            drawCircle(
                color = Color(0xFFFFF5CC).copy(alpha = 0.92f * pulse),
                radius = r,
                center = Offset(cx, cy),
            )
            // Crescent bite.
            val br = r * 0.82f
            drawCircle(
                color = Color(0xFF100826),
                radius = br,
                center = Offset(cx + r * 0.46f, cy - r * 0.06f),
            )
        } else {
            val pulse = 0.86f + sin((time * 0.44f).toDouble()).toFloat() * 0.14f
            val cx = w * 0.5f
            val cy = h * 0.5f
            val r = w * 0.28f
            val glowRadius = r * 2.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFD070).copy(alpha = 0.26f * pulse),
                        Color(0xFFFFA030).copy(alpha = 0.08f * pulse),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = Offset(cx, cy),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFDE2), Color(0xFFFFD050)),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }
}

@Composable
private fun SplashBranding(dark: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text(
            text = "Campzone",
            style = MaterialTheme.typography.displayLarge.copy(
                brush = Brush.linearGradient(
                    colors = if (dark) {
                        listOf(AuthCream, AuthAmber)
                    } else {
                        listOf(Color(0xFF7A3808), Color(0xFFC46018))
                    },
                ),
            ),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.auth_tagline),
            color = if (dark) AuthCream.copy(alpha = 0.6f) else Color(0xFF583010).copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
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
    val dark = isSystemInDarkTheme()
    Canvas(modifier = modifier.background(if (dark) AuthNight else Color(0xFFD4E8F8))) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = if (dark) arrayOf(
                    0f to Color(0xFF060914),
                    0.28f to Color(0xFF100826),
                    0.52f to Color(0xFF29103E),
                    0.76f to Color(0xFF38160C),
                    1f to Color(0xFF160C06),
                ) else arrayOf(
                    0f to Color(0xFFB0D6F2),
                    0.28f to Color(0xFFD4E8F8),
                    0.55f to Color(0xFFF5D8A8),
                    0.76f to Color(0xFFE8C47E),
                    1f to Color(0xFFC4DEB4),
                ),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf((if (dark) AuthEmber else Color(0xFFFFA030)).copy(alpha = if (dark) 0.2f else 0.28f), Color.Transparent),
                center = if (dark) Offset(size.width / 2f, size.height) else Offset(size.width * 0.72f, size.height * 0.06f),
                radius = size.minDimension * 0.95f,
            ),
            radius = size.minDimension * 0.95f,
            center = if (dark) Offset(size.width / 2f, size.height) else Offset(size.width * 0.72f, size.height * 0.06f),
        )
        if (dark) {
            authStars.forEach { star ->
                drawCircle(
                    color = Color.White.copy(alpha = star.alpha),
                    radius = star.radius,
                    center = Offset(star.x * size.width, star.y * size.height),
                )
            }
        }
    }
}

@Composable
private fun AuthBrandSection(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = "Campzone",
            color = if (dark) AuthCream else Color(0xFF7A3808),
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.auth_sign_in_tagline),
            color = if (dark) AuthAmber.copy(alpha = 0.72f) else Color(0xFF5A3010).copy(alpha = 0.72f),
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
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(CzRadius.xl)
    val isProviderBusy = uiState.isSigningInWithGoogle || uiState.isSigningInWithApple
    val isEmailBusy = uiState.isSigningInWithEmail || uiState.isSendingPasswordReset
    val appleContainerColor = if (dark) Color.White else Color.Black
    val appleContentColor = if (dark) Color.Black else Color.White

    Column(
        modifier = modifier
            .widthIn(max = 430.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (dark) 0.08f else 0.50f), shape)
            .border(BorderStroke(1.dp, AuthDivider), shape)
            .padding(CzSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        if (uiState.errorMessage != null) {
            AuthMessageBanner(
                message = uiState.errorMessage,
                kind = AuthBannerKind.Error,
            )
        }

        AuthProviderButton(
            title = stringResource(R.string.auth_continue_apple),
            mark = "A",
            onClick = onAppleSignIn,
            isLoading = uiState.isSigningInWithApple,
            enabled = !uiState.isSigningInWithGoogle && !isEmailBusy,
            containerColor = appleContainerColor,
            contentColor = appleContentColor,
            borderColor = appleContainerColor.copy(alpha = 0.7f),
            markStyle = ProviderMarkStyle.Plain,
        )

        AuthProviderButton(
            title = stringResource(R.string.auth_continue_google),
            mark = "g",
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
    markStyle: ProviderMarkStyle = ProviderMarkStyle.Circle,
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
                    when (markStyle) {
                        ProviderMarkStyle.Circle -> {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = mark,
                                    color = contentColor,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }

                        ProviderMarkStyle.Plain -> {
                            Text(
                                text = mark,
                                modifier = Modifier.width(24.dp),
                                color = contentColor,
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
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

private enum class ProviderMarkStyle {
    Circle,
    Plain,
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
            text = stringResource(R.string.auth_or_use_email),
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
    val hidePasswordDescription = stringResource(R.string.auth_hide_password)
    val showPasswordDescription = stringResource(R.string.auth_show_password)
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
                label = stringResource(R.string.auth_full_name),
                placeholder = stringResource(R.string.auth_display_name_placeholder),
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
            label = stringResource(R.string.profile_email),
            placeholder = stringResource(R.string.auth_email_placeholder),
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
            label = stringResource(R.string.auth_password),
            placeholder = stringResource(R.string.auth_password_placeholder),
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
                            hidePasswordDescription
                        } else {
                            showPasswordDescription
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
                    text = stringResource(if (mode == EmailMode.SignIn) R.string.auth_sign_in_cta else R.string.auth_create_account),
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
                    text = stringResource(if (isSendingReset) R.string.auth_sending else R.string.auth_forgot_password),
                    style = MaterialTheme.typography.labelMedium,
                    textDecoration = TextDecoration.Underline,
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
            text = stringResource(if (mode == EmailMode.SignIn) R.string.auth_no_account else R.string.auth_already_account),
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
                text = stringResource(if (mode == EmailMode.SignIn) R.string.auth_create_one else R.string.auth_sign_in),
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
    val dark = isSystemInDarkTheme()
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
                .background(
                    if (dark) Color.White.copy(alpha = if (isFocused) 0.09f else 0.06f)
                    else Color.Black.copy(alpha = if (isFocused) 0.07f else 0.04f),
                    shape,
                )
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
                        color = (if (dark) AuthCream else MaterialTheme.czColors.textPrimary).copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (dark) AuthCream else MaterialTheme.czColors.textPrimary),
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
    val prefix = stringResource(R.string.auth_legal_prefix)
    val terms = stringResource(R.string.auth_terms)
    val conjunction = stringResource(R.string.auth_legal_and)
    val privacy = stringResource(R.string.auth_privacy)
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = AuthAmber),
    )
    val text = buildAnnotatedString {
        append(prefix)
        append(" ")
        withLink(LinkAnnotation.Url(CampzonePrivacyUrl, linkStyle)) {
            append(terms)
        }
        append(" ")
        append(conjunction)
        append(" ")
        withLink(LinkAnnotation.Url(CampzonePrivacyUrl, linkStyle)) {
            append(privacy)
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

// MARK: - Splash camp scene (ported from iOS SplashView.CampScene)

/**
 * Draws the full layered camp illustration into the bottom strip of the splash:
 * soft displacement-mapped mountain ridges, horizon mist, an elliptical
 * clearing, a mid-ground forest, an A-frame tent, the campfire bloom + flames,
 * and the large flanking foreground pines. Coordinates are relative to the
 * scene canvas (its own height ≈ 44% of the screen), matching iOS exactly.
 */
private fun DrawScope.drawCampScene(dark: Boolean, fireLit: Boolean, time: Float) {
    drawDistantMountains(dark)
    drawNearMountains(dark)
    drawHorizonMist(dark)
    drawGround(dark)
    drawMidForest(dark)
    drawTent(dark, fireLit, time)
    drawCampfireGlow(fireLit, time)
    drawFireLogs()
    drawFire(fireLit, time)
    drawForegroundPines(dark)
}

private fun DrawScope.drawDistantMountains(dark: Boolean) {
    val w = size.width
    val h = size.height
    val ridge = mountainRidgePath(w, h, baseline = h * 0.36f, roughness = 0.16f, seed = 42L)
    drawPath(
        ridge,
        brush = Brush.linearGradient(
            colors = if (dark) {
                listOf(Color(0xFF1C2640), Color(0xFF0C1420))
            } else {
                listOf(Color(0xFF8AAABE), Color(0xFF6888A0))
            },
            start = Offset(w * 0.5f, h * 0.04f),
            end = Offset(w * 0.5f, h),
        ),
    )
    if (dark) {
        drawPath(
            ridge,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.58f),
                    0.22f to Color.White.copy(alpha = 0.20f),
                    0.44f to Color.Transparent,
                ),
                start = Offset(w * 0.5f, h * 0.04f),
                end = Offset(w * 0.5f, h * 0.46f),
            ),
        )
    }
}

private fun DrawScope.drawNearMountains(dark: Boolean) {
    val w = size.width
    val h = size.height
    val ridge = mountainRidgePath(w, h, baseline = h * 0.57f, roughness = 0.20f, seed = 99L)
    drawPath(
        ridge,
        brush = Brush.linearGradient(
            colors = if (dark) {
                listOf(Color(0xFF182E1C), Color(0xFF0A1510))
            } else {
                listOf(Color(0xFF507A58), Color(0xFF345438))
            },
            start = Offset(w * 0.5f, h * 0.30f),
            end = Offset(w * 0.5f, h),
        ),
    )
}

private fun DrawScope.drawHorizonMist(dark: Boolean) {
    val w = size.width
    val h = size.height
    val y0 = h * 0.50f
    val mist = if (dark) Color(0xFF8888CC).copy(alpha = 0.13f) else Color.White.copy(alpha = 0.22f)
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.38f to mist,
                0.62f to mist,
                1f to Color.Transparent,
            ),
            startY = y0,
            endY = y0 + h * 0.10f,
        ),
        topLeft = Offset(0f, y0),
        size = Size(w, h * 0.10f),
    )
}

private fun DrawScope.drawGround(dark: Boolean) {
    val w = size.width
    val h = size.height
    val topLeft = Offset(w * 0.03f, h * 0.72f)
    val ovalSize = Size(w * 0.94f, h * 0.24f)
    drawOval(
        brush = Brush.linearGradient(
            colors = if (dark) {
                listOf(Color(0xFF1C3018), Color(0xFF0C1A0A))
            } else {
                listOf(Color(0xFF5A8848), Color(0xFF385830))
            },
            start = Offset(topLeft.x + ovalSize.width / 2f, topLeft.y),
            end = Offset(topLeft.x + ovalSize.width / 2f, topLeft.y + ovalSize.height),
        ),
        topLeft = topLeft,
        size = ovalSize,
    )
}

private fun DrawScope.drawMidForest(dark: Boolean) {
    val w = size.width
    val h = size.height
    val col = if (dark) Color(0xFF0E1E18) else Color(0xFF284830)
    val trees = listOf(
        Triple(0.03f, 0.78f, 0.24f), Triple(0.09f, 0.80f, 0.28f), Triple(0.15f, 0.78f, 0.22f),
        Triple(0.21f, 0.80f, 0.26f), Triple(0.27f, 0.77f, 0.20f), Triple(0.33f, 0.79f, 0.23f),
        Triple(0.67f, 0.79f, 0.23f), Triple(0.73f, 0.77f, 0.20f), Triple(0.79f, 0.80f, 0.26f),
        Triple(0.85f, 0.78f, 0.22f), Triple(0.91f, 0.80f, 0.28f), Triple(0.97f, 0.78f, 0.24f),
    )
    trees.forEach { (xF, yF, hF) ->
        val tH = h * hF
        drawPineTree(w * xF, h * yF, tH * 0.40f, tH, col)
    }
}

private fun DrawScope.drawForegroundPines(dark: Boolean) {
    val w = size.width
    val h = size.height
    val col = if (dark) Color(0xFF080E0B) else Color(0xFF183020)
    val trees = listOf(
        Triple(0.00f, 0.96f, 0.50f), Triple(0.07f, 1.00f, 0.58f), Triple(0.15f, 0.94f, 0.42f),
        Triple(1.00f, 0.96f, 0.50f), Triple(0.93f, 1.00f, 0.58f), Triple(0.85f, 0.94f, 0.42f),
    )
    trees.forEach { (xF, yF, hF) ->
        val tH = h * hF
        drawPineTree(w * xF, h * yF, tH * 0.44f, tH, col)
    }
}

private fun DrawScope.drawPineTree(baseX: Float, baseY: Float, width: Float, height: Float, color: Color) {
    val trunkH = height * 0.10f
    val trunkW = maxOf(2f, width * 0.07f)
    drawRect(
        color = Color(0xFF3A2010).copy(alpha = 0.75f),
        topLeft = Offset(baseX - trunkW / 2f, baseY - trunkH),
        size = Size(trunkW, trunkH),
    )
    // Tier 1 — widest (bottom)
    drawTreeTier(
        apex = Offset(baseX, baseY - height * 0.52f),
        left = Offset(baseX - width * 0.50f, baseY - trunkH),
        right = Offset(baseX + width * 0.50f, baseY - trunkH),
        color = color,
    )
    // Tier 2 — middle
    drawTreeTier(
        apex = Offset(baseX, baseY - height * 0.76f),
        left = Offset(baseX - width * 0.34f, baseY - height * 0.42f),
        right = Offset(baseX + width * 0.34f, baseY - height * 0.42f),
        color = color,
    )
    // Tier 3 — narrowest (top)
    drawTreeTier(
        apex = Offset(baseX, baseY - height),
        left = Offset(baseX - width * 0.20f, baseY - height * 0.66f),
        right = Offset(baseX + width * 0.20f, baseY - height * 0.66f),
        color = color,
    )
}

private fun DrawScope.drawTreeTier(apex: Offset, left: Offset, right: Offset, color: Color) {
    val tri = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(tri, color = color)
}

private fun DrawScope.drawTent(dark: Boolean, fireLit: Boolean, time: Float) {
    val w = size.width
    val h = size.height
    val cx = w * 0.62f
    val baseY = h * 0.84f
    val tW = w * 0.27f
    val tH = h * 0.26f
    val peak = Offset(cx, baseY - tH)
    val lBase = Offset(cx - tW * 0.52f, baseY)
    val rBase = Offset(cx + tW * 0.52f, baseY)

    // Inner warm glow behind the tent body.
    if (fireLit) {
        val glowPulse = 0.76f + sin((time * 1.60f).toDouble()).toFloat() * 0.24f
        val glowRadius = tW * 0.56f
        val glowCenter = Offset(cx, baseY - tH * 0.32f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFF8C20).copy(alpha = 0.58f * glowPulse),
                    0.55f to Color(0xFFFF6000).copy(alpha = 0.22f * glowPulse),
                    1f to Color.Transparent,
                ),
                center = glowCenter,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = glowCenter,
        )
    }

    val body = Path().apply {
        moveTo(peak.x, peak.y)
        lineTo(lBase.x, lBase.y)
        lineTo(rBase.x, rBase.y)
        close()
    }
    drawPath(
        body,
        brush = Brush.linearGradient(
            colors = if (dark) {
                listOf(Color(0xFF2A2010), Color(0xFF1A1508))
            } else {
                listOf(Color(0xFF947028), Color(0xFF6A5018))
            },
            start = peak,
            end = Offset(cx, baseY),
        ),
    )

    // Left-face shadow for depth.
    val leftFace = Path().apply {
        moveTo(peak.x, peak.y)
        lineTo(lBase.x, lBase.y)
        lineTo(cx, baseY)
        close()
    }
    drawPath(leftFace, color = Color.Black.copy(alpha = 0.20f))

    // Door arch.
    val dW = tW * 0.26f
    val dH = tH * 0.46f
    val door = Path().apply {
        moveTo(cx, baseY - tH * 0.06f)
        cubicTo(cx - dW * 0.08f, baseY - dH * 0.18f, cx - dW * 0.46f, baseY - dH * 0.40f, cx - dW * 0.46f, baseY)
        lineTo(cx + dW * 0.46f, baseY)
        cubicTo(cx + dW * 0.46f, baseY - dH * 0.40f, cx + dW * 0.08f, baseY - dH * 0.18f, cx, baseY - tH * 0.06f)
        close()
    }
    drawPath(
        door,
        color = if (fireLit) Color(0xFFFF8020).copy(alpha = 0.68f) else Color.Black.copy(alpha = 0.55f),
    )

    // Outline.
    drawPath(body, color = Color(0xFF504020).copy(alpha = 0.65f), style = Stroke(width = 1f))

    // Guy lines.
    listOf(-1f, 1f).forEach { signX ->
        drawLine(
            color = Color(0xFF907048).copy(alpha = 0.42f),
            start = peak,
            end = Offset(cx + signX * tW * 0.72f, baseY + h * 0.022f),
            strokeWidth = 0.8f,
        )
    }
}

private fun DrawScope.drawCampfireGlow(fireLit: Boolean, time: Float) {
    if (!fireLit) return
    val w = size.width
    val h = size.height
    val f1 = 0.72f + sin((time * 9.1f).toDouble()).toFloat() * 0.15f + sin((time * 5.3f).toDouble()).toFloat() * 0.13f
    val f2 = 0.78f + sin((time * 6.7f).toDouble()).toFloat() * 0.12f + sin((time * 3.9f).toDouble()).toFloat() * 0.10f
    val fire = Offset(w * 0.44f, h * 0.885f)
    // Each node: dx, dy, radius, opacity — offset from `fire`.
    val nodes = listOf(
        floatArrayOf(0f, h * 0.030f, w * 0.34f, 0.48f * f1),
        floatArrayOf(-w * 0.040f, h * 0.018f, w * 0.24f, 0.30f * f1),
        floatArrayOf(w * 0.035f, h * 0.022f, w * 0.22f, 0.26f * f1),
        floatArrayOf(0f, -h * 0.020f, w * 0.18f, 0.42f * f2),
        floatArrayOf(0f, -h * 0.080f, w * 0.12f, 0.32f * f2),
        floatArrayOf(w * 0.008f, -h * 0.150f, w * 0.08f, 0.18f * f2),
    )
    nodes.forEach { node ->
        val c = Offset(fire.x + node[0], fire.y + node[1])
        val radius = node[2]
        val opacity = node[3].coerceIn(0f, 1f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFF8C20).copy(alpha = opacity),
                    0.50f to Color(0xFFFF5500).copy(alpha = opacity * 0.40f),
                    1f to Color.Transparent,
                ),
                center = c,
                radius = radius,
            ),
            radius = radius,
            center = c,
        )
    }
}

private fun DrawScope.drawFireLogs() {
    val w = size.width
    val h = size.height
    val center = Offset(w * 0.44f, h * 0.905f)
    val logW = w * 0.14f
    val logH = h * 0.021f
    listOf(-14f, 14f).forEach { angle ->
        withTransform({
            translate(center.x, center.y)
            rotate(angle, pivot = Offset.Zero)
        }) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF8B4A20), Color(0xFF4D2411)),
                    start = Offset(-logW / 2f, 0f),
                    end = Offset(logW / 2f, 0f),
                ),
                topLeft = Offset(-logW / 2f, -logH / 2f),
                size = Size(logW, logH),
                cornerRadius = CornerRadius(logH / 2f, logH / 2f),
            )
        }
    }
}

private fun DrawScope.drawFire(fireLit: Boolean, time: Float) {
    if (!fireLit) return
    val w = size.width
    val h = size.height
    val base = Offset(w * 0.44f, h * 0.885f)
    val flicker = sin((time * 10.5f).toDouble()).toFloat() * h * 0.010f
    val sideShift = sin((time * 6.8f).toDouble()).toFloat() * w * 0.008f

    // Outer flame: ember → amber.
    drawFlame(
        base = base,
        width = w * 0.090f,
        height = h * 0.175f + flicker,
        xOffset = sideShift,
        colors = listOf(Color(0xFFFF4500), Color(0xFFFF9020)),
    )
    // Inner flame: amber → cream.
    drawFlame(
        base = Offset(base.x, base.y + h * 0.008f),
        width = w * 0.054f,
        height = h * 0.105f - flicker * 0.4f,
        xOffset = -sideShift * 0.5f,
        colors = listOf(Color(0xFFFFC040), Color(0xFFFFFACC)),
    )
}

private fun DrawScope.drawFlame(base: Offset, width: Float, height: Float, xOffset: Float, colors: List<Color>) {
    val flame = Path().apply {
        moveTo(base.x, base.y - height)
        cubicTo(
            base.x - width * 0.42f + xOffset, base.y - height * 0.70f,
            base.x - width * 0.72f, base.y - height * 0.36f,
            base.x - width * 0.54f, base.y - height * 0.14f,
        )
        cubicTo(
            base.x - width * 0.14f, base.y + height * 0.04f,
            base.x + width * 0.14f, base.y + height * 0.04f,
            base.x + width * 0.54f, base.y - height * 0.14f,
        )
        cubicTo(
            base.x + width * 0.74f, base.y - height * 0.40f,
            base.x + width * 0.36f + xOffset, base.y - height * 0.72f,
            base.x, base.y - height,
        )
        close()
    }
    drawPath(
        flame,
        brush = Brush.linearGradient(
            colors = colors,
            start = Offset(base.x, base.y - height),
            end = base,
        ),
    )
}

/**
 * Builds a smooth mountain silhouette via midpoint-displacement (4 iterations)
 * smoothed with quadratic-bezier joins, so every peak is rounded rather than a
 * sharp angle — the iOS `mountainRidge` algorithm. Peaks never rise above 4% of
 * the canvas height. Deterministic for a given `seed`.
 */
private fun mountainRidgePath(w: Float, h: Float, baseline: Float, roughness: Float, seed: Long): Path {
    val rng = SplashRng(seed)
    var pts = mutableListOf(Offset(-60f, baseline), Offset(w + 60f, baseline))
    repeat(4) {
        val next = ArrayList<Offset>(pts.size * 2)
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            next.add(a)
            val disp = rng.nextSigned() * (b.x - a.x) * roughness
            val rawY = (a.y + b.y) / 2f + disp
            next.add(Offset((a.x + b.x) / 2f, maxOf(h * 0.04f, rawY)))
        }
        next.add(pts.last())
        pts = next
    }
    return Path().apply {
        moveTo(pts.first().x, h)
        lineTo(pts[0].x, pts[0].y)
        for (i in 0 until pts.size - 1) {
            val cur = pts[i]
            val nxt = pts[i + 1]
            quadraticBezierTo(cur.x, cur.y, (cur.x + nxt.x) / 2f, (cur.y + nxt.y) / 2f)
        }
        lineTo(pts.last().x, pts.last().y)
        lineTo(pts.last().x, h)
        close()
    }
}

/** Deterministic LCG matching iOS `SplashRNG` (same constants and bit shift). */
private class SplashRng(seed: Long) {
    private var state: Long = seed

    private fun next(): Long {
        state = 6364136223846793005L * state + 1442695040888963407L
        return state
    }

    /** Uniform value in [-1, 1). */
    fun nextSigned(): Float = (next() ushr 40).toFloat() / (1 shl 24).toFloat() * 2f - 1f
}

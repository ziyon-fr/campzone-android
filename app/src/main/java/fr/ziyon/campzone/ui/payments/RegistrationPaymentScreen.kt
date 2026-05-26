package fr.ziyon.campzone.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun CampingRegistrationPaymentRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegistrationPaymentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val paymentSheet = rememberPaymentSheet { result ->
        when (result) {
            is PaymentSheetResult.Completed -> viewModel.confirmPreparedPayment(authenticatedUser)
            is PaymentSheetResult.Canceled -> viewModel.cancelPreparedPayment()
            is PaymentSheetResult.Failed -> viewModel.failPreparedPayment(result.error.localizedMessage)
        }
    }

    LaunchedEffect(campingId, authenticatedUser) {
        viewModel.load(campingId, authenticatedUser)
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    val preparedPayment = state.preparedPayment
    LaunchedEffect(preparedPayment?.sheetIntent?.paymentIntentId, preparedPayment?.hasBeenPresented) {
        if (preparedPayment != null && !preparedPayment.hasBeenPresented) {
            val sheetIntent = preparedPayment.sheetIntent
            PaymentConfiguration.init(context, sheetIntent.publishableKey)
            paymentSheet.presentWithPaymentIntent(
                paymentIntentClientSecret = sheetIntent.paymentIntentClientSecret,
                configuration = PaymentSheet.Configuration.Builder("Campzone")
                    .customer(
                        PaymentSheet.CustomerConfiguration(
                            id = sheetIntent.customerId,
                            ephemeralKeySecret = sheetIntent.ephemeralKeySecret,
                        ),
                    )
                    .allowsDelayedPaymentMethods(false)
                    .build(),
            )
            viewModel.markPaymentSheetPresented()
        }
    }

    RegistrationPaymentScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onDone = onDone,
        onPayNow = viewModel::prepareCurrentPayment,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationPaymentScreen(
    state: RegistrationPaymentUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onPayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.czColors.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.registration_payment_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.czColors.background,
                ),
                windowInsets = WindowInsets(0.dp),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> CzLoadingView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                message = stringResource(R.string.registration_payment_loading),
            )

            state.isComplete -> PaymentCompleteContent(
                title = state.campingTitle,
                onDone = onDone,
                modifier = Modifier.padding(innerPadding),
            )

            else -> PaymentContent(
                state = state,
                onPayNow = onPayNow,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun PaymentContent(
    state: RegistrationPaymentUiState,
    onPayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentItem = state.currentItem

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CzSpacing.lg,
            top = CzSpacing.sm,
            end = CzSpacing.lg,
            bottom = CzSpacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item {
            PaymentHeader(
                title = state.campingTitle,
                pendingCount = state.items.size,
            )
        }
        if (currentItem != null) {
            item {
                CurrentPaymentCard(
                    item = currentItem,
                    isLoading = state.isPreparingPayment || state.isConfirmingPayment,
                    onPayNow = onPayNow,
                )
            }
        }
        if (state.items.size > 1) {
            item {
                PendingParticipantsCard(items = state.items.drop(1))
            }
        }
    }
}

@Composable
private fun PaymentHeader(
    title: String,
    pendingCount: Int,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text(
            text = title.ifBlank { stringResource(R.string.registration_payment_title) },
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.registration_payment_pending_count, pendingCount),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CurrentPaymentCard(
    item: RegistrationPaymentItem,
    isLoading: Boolean,
    onPayNow: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.czColors.surface,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CzRadius.md),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.registration_payment_pay_for, item.participantName),
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatMoney(item.request.amountCents, item.request.currency),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            CzButton(
                text = if (isLoading) {
                    stringResource(R.string.registration_payment_processing)
                } else {
                    stringResource(R.string.registration_payment_pay_now)
                },
                onClick = onPayNow,
                enabled = !isLoading,
                loading = isLoading,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Filled.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun PendingParticipantsCard(items: List<RegistrationPaymentItem>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.czColors.surface,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CzRadius.md),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.People,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.registration_payment_up_next),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items.forEach { item ->
                Text(
                    text = stringResource(
                        R.string.registration_payment_queue_item,
                        item.participantName,
                        formatMoney(item.request.amountCents, item.request.currency),
                    ),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PaymentCompleteContent(
    title: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CzEmptyState(
            title = stringResource(R.string.registration_payment_complete_title),
            message = title.takeUnless { it.isBlank() }
                ?: stringResource(R.string.registration_payment_complete_message),
            icon = {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.success,
                    modifier = Modifier.size(40.dp),
                )
            },
            action = {
                CzButton(
                    text = stringResource(R.string.registration_payment_done),
                    onClick = onDone,
                    variant = CzButtonVariant.Primary,
                )
            },
        )
    }
}

private fun formatMoney(amountCents: Int, currencyCode: String): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching {
        formatter.currency = Currency.getInstance(currencyCode.uppercase(Locale.US))
    }
    return formatter.format(amountCents / 100.0)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun RegistrationPaymentScreenPreview() {
    CampzoneTheme {
        RegistrationPaymentScreen(
            state = RegistrationPaymentUiState(
                isLoading = false,
                campingTitle = "Summer Camp",
                items = listOf(
                    RegistrationPaymentItem(
                        participantId = "uid-1",
                        participantName = "Maria Santos",
                        request = fr.ziyon.campzone.data.payments.PaymentRequest(
                            kind = fr.ziyon.campzone.data.model.PaymentKind.Registration,
                            campingId = "camp-1",
                            referenceId = "uid-1",
                            amountCents = 2500,
                        ),
                    ),
                ),
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onDone = {},
            onPayNow = {},
        )
    }
}

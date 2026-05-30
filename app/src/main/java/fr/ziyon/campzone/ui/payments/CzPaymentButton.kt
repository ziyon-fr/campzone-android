package fr.ziyon.campzone.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.PaymentKind
import fr.ziyon.campzone.data.payments.PaymentRequest

/**
 * Reusable "pay this fee" CTA mirroring the iOS `PaymentButton`. Runs the full
 * Stripe PaymentSheet flow through [PaymentButtonViewModel]: tap -> mint intent
 * (`/payments/intent`) -> present the native sheet -> verify the charge
 * (`/payments/confirm`) -> [onPaid]. The Stripe secret never leaves the
 * backend; the client only ever holds the publishable key + ephemeral secret
 * returned by the intent call.
 *
 * When the [request] carries no amount it renders a "no payment due" badge
 * instead of a button (a registration that is fee-exempt, etc.).
 *
 * Each instance is scoped to its own [PaymentButtonViewModel] keyed by the
 * request, so the CTA works correctly when many appear in one list (one per
 * booking / price item).
 */
@Composable
fun CzPaymentButton(
    request: PaymentRequest,
    onPaid: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    viewModel: PaymentButtonViewModel = hiltViewModel(
        key = "cz-pay-${request.kind.wireValue}-${request.referenceId}",
    ),
) {
    if (request.amountCents <= 0) {
        NoPaymentDueBadge(kind = request.kind, modifier = modifier)
        return
    }

    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val paymentSheet = rememberPaymentSheet { result ->
        when (result) {
            is PaymentSheetResult.Completed -> viewModel.confirm(request)
            is PaymentSheetResult.Canceled -> viewModel.cancel()
            is PaymentSheetResult.Failed -> viewModel.fail(result.error.localizedMessage)
        }
    }

    // Present exactly once per minted intent. Keying on the intent id means a
    // fresh `pay()` re-presents while cancel/confirm (which clear the intent)
    // simply relaunch into a no-op.
    val preparedIntent = state.preparedIntent
    LaunchedEffect(preparedIntent?.paymentIntentId) {
        if (preparedIntent != null) {
            PaymentConfiguration.init(context, preparedIntent.publishableKey)
            paymentSheet.presentWithPaymentIntent(
                paymentIntentClientSecret = preparedIntent.paymentIntentClientSecret,
                configuration = PaymentSheet.Configuration.Builder(MERCHANT_DISPLAY_NAME)
                    .customer(
                        PaymentSheet.CustomerConfiguration(
                            id = preparedIntent.customerId,
                            ephemeralKeySecret = preparedIntent.ephemeralKeySecret,
                        ),
                    )
                    .allowsDelayedPaymentMethods(false)
                    .build(),
            )
        }
    }

    LaunchedEffect(state.paid) {
        if (state.paid) {
            onPaid()
            viewModel.consumePaid()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        CzButton(
            text = if (state.isBusy) {
                stringResource(R.string.payment_processing)
            } else {
                stringResource(
                    R.string.payment_pay_amount,
                    formatPaymentAmount(request.amountCents, request.currency),
                )
            },
            onClick = { viewModel.pay(request) },
            enabled = enabled && !state.isBusy,
            loading = state.isBusy,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.CreditCard,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.czColors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NoPaymentDueBadge(kind: PaymentKind, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Surface(
        modifier = modifier,
        color = colors.success.copy(alpha = 0.12f),
        shape = RoundedCornerShape(CzRadius.full),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(
                    if (kind == PaymentKind.Registration) {
                        R.string.payment_exempt
                    } else {
                        R.string.payment_none_due
                    },
                ),
                color = colors.success,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private const val MERCHANT_DISPLAY_NAME = "Campzone"

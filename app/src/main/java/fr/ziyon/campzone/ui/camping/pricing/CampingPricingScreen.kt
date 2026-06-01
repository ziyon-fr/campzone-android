package fr.ziyon.campzone.ui.camping.pricing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzBadgeTone
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.CampingPriceItem
import fr.ziyon.campzone.data.model.PaymentKind
import fr.ziyon.campzone.data.model.installmentAmountCents
import fr.ziyon.campzone.data.model.offersBankTransfer
import fr.ziyon.campzone.data.model.offersCardOneTime
import fr.ziyon.campzone.data.model.offersInstallments
import fr.ziyon.campzone.data.model.resolvedCurrency
import fr.ziyon.campzone.data.payments.PaymentProof
import fr.ziyon.campzone.data.payments.PaymentRequest
import fr.ziyon.campzone.ui.payments.CzPaymentButton
import fr.ziyon.campzone.ui.payments.PaymentReceiptPdf
import fr.ziyon.campzone.ui.payments.formatPaymentAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CampingPricingRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampingPricingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
    }

    CampingPricingScreen(
        state = state,
        onBack = onBack,
        onRetry = { viewModel.retry(campingId, authenticatedUser) },
        onPaid = viewModel::reload,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingPricingScreen(
    state: CampingPricingUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPaid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val ibanCopied = stringResource(R.string.price_item_iban_copied)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.czColors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) } },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.camping_fees_payments),
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
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        when (state) {
            CampingPricingUiState.Loading -> CzLoadingView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                message = stringResource(R.string.registration_payment_loading),
            )

            is CampingPricingUiState.Error -> CzErrorState(
                title = stringResource(R.string.camping_fees_payments),
                message = state.message,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(CzSpacing.xl),
            )

            is CampingPricingUiState.Loaded -> {
                if (state.isEmpty) {
                    CzEmptyState(
                        title = stringResource(R.string.fees_empty_title),
                        message = stringResource(R.string.fees_empty_message),
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(CzSpacing.xl),
                    )
                } else {
                    PricingContent(
                        state = state,
                        onPaid = onPaid,
                        onCopyIban = { iban ->
                            clipboard.setText(AnnotatedString(iban))
                            scope.launch { snackbarHostState.showSnackbar(ibanCopied) }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun PricingContent(
    state: CampingPricingUiState.Loaded,
    onPaid: () -> Unit,
    onCopyIban: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CzSpacing.lg,
            top = CzSpacing.sm,
            end = CzSpacing.lg,
            bottom = CzSpacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item("title") {
            Text(
                text = state.campingTitle,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (state.registrationFees.isNotEmpty()) {
            item("registration-header") {
                SectionHeader(stringResource(R.string.fees_registration_section))
            }
            items(state.registrationFees, key = { "reg-${it.attendeeId}" }) { row ->
                RegistrationFeeCard(row = row, campingId = state.campingId, onPaid = onPaid)
            }
        }

        if (state.priceItems.isNotEmpty()) {
            item("addons-header") {
                SectionHeader(stringResource(R.string.fees_addons_section))
            }
            items(state.priceItems, key = { "item-${it.id}" }) { item ->
                PriceItemCard(
                    item = item,
                    campingId = state.campingId,
                    onPaid = onPaid,
                    onCopyIban = onCopyIban,
                )
            }
        }

        if (state.proofs.isNotEmpty()) {
            item("receipts-header") {
                SectionHeader(stringResource(R.string.fees_receipts_section))
            }
            items(state.proofs, key = { "proof-${it.id}" }) { proof ->
                PaymentProofCard(proof = proof, campingTitle = state.campingTitle)
            }
        }
    }
}

@Composable
private fun PaymentProofCard(proof: PaymentProof, campingTitle: String) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }
    val title = proof.displayTitle(stringResource(R.string.receipt_default_title))

    CzCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        formatPaymentAmount(proof.amountCents, proof.currency),
                        proof.invoiceNumber,
                    ).joinToString(" · "),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            CzBadge(
                text = stringResource(R.string.fees_receipt_paid),
                tone = CzBadgeTone.Success,
            )
            CzButton(
                text = stringResource(R.string.fees_receipt_pdf),
                onClick = {
                    if (isSharing) return@CzButton
                    isSharing = true
                    scope.launch {
                        runCatching {
                            val file = withContext(Dispatchers.IO) {
                                PaymentReceiptPdf.write(context, proof, campingTitle)
                            }
                            PaymentReceiptPdf.share(context, file)
                        }
                        isSharing = false
                    }
                },
                enabled = !isSharing,
                variant = CzButtonVariant.Outline,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.czColors.textSecondary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun RegistrationFeeCard(row: FeesRegistrationRow, campingId: String, onPaid: () -> Unit) {
    PricingCardShell {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CzAvatar(
                imageUrl = null,
                contentDescription = row.participantName,
                initials = row.participantName.firstOrNull()?.toString(),
                size = CzAvatarSize.Medium,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.participantName,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (row.isSelf) R.string.fees_participant_self else R.string.fees_participant_family,
                    ),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = formatPaymentAmount(row.amountCents, row.currency),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        CzPaymentButton(
            request = PaymentRequest(
                kind = PaymentKind.Registration,
                campingId = campingId,
                referenceId = row.attendeeId,
                amountCents = row.amountCents,
                currency = row.currency,
            ),
            onPaid = onPaid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PriceItemCard(
    item: CampingPriceItem,
    campingId: String,
    onPaid: () -> Unit,
    onCopyIban: (String) -> Unit,
) {
    PricingCardShell {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.name,
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (item.isMandatory) {
                        CzBadge(
                            text = stringResource(R.string.price_item_required),
                            tone = CzBadgeTone.Primary,
                        )
                    }
                }
                if (item.details.isNotBlank()) {
                    Text(
                        text = item.details,
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                text = formatPaymentAmount(item.amountCents, item.resolvedCurrency),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        when {
            item.amountCents <= 0 -> NoChargeRow()

            else -> {
                if (item.offersCardOneTime) {
                    CzPaymentButton(
                        request = PaymentRequest(
                            kind = PaymentKind.PriceItem,
                            campingId = campingId,
                            referenceId = item.id,
                            amountCents = item.amountCents,
                            currency = item.resolvedCurrency,
                        ),
                        onPaid = onPaid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (item.offersInstallments && item.installmentAmountCents > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                        CzPaymentButton(
                            request = PaymentRequest(
                                kind = PaymentKind.PriceItem,
                                campingId = campingId,
                                referenceId = item.id,
                                amountCents = item.installmentAmountCents,
                                currency = item.resolvedCurrency,
                            ),
                            onPaid = onPaid,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(
                                R.string.price_item_installment_hint,
                                formatPaymentAmount(item.installmentAmountCents, item.resolvedCurrency),
                                fr.ziyon.campzone.data.model.CAMPING_PRICE_ITEM_INSTALLMENT_COUNT,
                            ),
                            color = MaterialTheme.czColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (item.offersBankTransfer) {
                    IbanTransferRow(item = item, onCopyIban = onCopyIban)
                }
            }
        }
    }
}

@Composable
private fun NoChargeRow() {
    Surface(
        color = MaterialTheme.czColors.success.copy(alpha = 0.12f),
        shape = RoundedCornerShape(CzRadius.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.czColors.success,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.price_item_no_charge),
                color = MaterialTheme.czColors.success,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun IbanTransferRow(item: CampingPriceItem, onCopyIban: (String) -> Unit) {
    val colors = MaterialTheme.czColors
    val iban = item.iban.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AccountBalance,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.price_item_bank_transfer),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item.ibanHolder?.takeUnless { it.isBlank() }?.let { holder ->
            Text(
                text = holder,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Surface(
            color = colors.background,
            shape = RoundedCornerShape(CzRadius.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(CzSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = iban,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onCopyIban(iban) }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.price_item_iban_copy),
                        modifier = Modifier.padding(start = CzSpacing.xs),
                    )
                }
            }
        }
        Text(
            text = stringResource(
                R.string.price_item_bank_transfer_hint,
                formatPaymentAmount(item.amountCents, item.resolvedCurrency),
            ),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PricingCardShell(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.czColors.surface),
        shape = RoundedCornerShape(CzRadius.md),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            content = content,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun CampingPricingScreenPreview() {
    CampzoneTheme {
        CampingPricingScreen(
            state = CampingPricingUiState.Loaded(
                campingId = "camp-1",
                campingTitle = "Summer Pathfinder Camp",
                registrationFees = listOf(
                    FeesRegistrationRow("uid-1", "Maria Santos", isSelf = true, amountCents = 4500, currency = "eur"),
                ),
                priceItems = listOf(
                    CampingPriceItem(
                        id = "lodging",
                        name = "Lodging upgrade",
                        details = "Private cabin for the week",
                        amountCents = 6000,
                        currency = "EUR",
                        paymentOptions = listOf(
                            fr.ziyon.campzone.data.model.CampingPaymentOption.CardOneTime,
                            fr.ziyon.campzone.data.model.CampingPaymentOption.CardInstallments,
                            fr.ziyon.campzone.data.model.CampingPaymentOption.BankTransfer,
                        ),
                        iban = "FR76 3000 4000 0500 0000 0000 123",
                        ibanHolder = "Campzone Association",
                        isMandatory = false,
                    ),
                ),
            ),
            onBack = {},
            onRetry = {},
            onPaid = {},
        )
    }
}

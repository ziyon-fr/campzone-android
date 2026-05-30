package fr.ziyon.campzone.ui.payments

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats an integer-cents amount as localized currency. Mirrors the iOS
 * `PaymentFormatting.amount` helper - money is always stored in the minor unit
 * (cents) and the ISO code is normalized to upper-case before formatting.
 */
internal fun formatPaymentAmount(amountCents: Int, currencyCode: String): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val normalized = currencyCode.trim().uppercase(Locale.US).ifBlank { "EUR" }
    runCatching { formatter.currency = Currency.getInstance(normalized) }
    return formatter.format(amountCents / 100.0)
}

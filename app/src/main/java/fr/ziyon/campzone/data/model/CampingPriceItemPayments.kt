package fr.ziyon.campzone.data.model

import java.util.Locale

/**
 * Payment-related helpers for [CampingPriceItem], ported verbatim from the iOS
 * `CampingPriceItem` computed properties so the participant-facing pricing UI
 * resolves the same installment split, currency, and offered payment means.
 */

/** Installments are a fixed 3-payment split by rule (matches iOS). */
const val CAMPING_PRICE_ITEM_INSTALLMENT_COUNT: Int = 3

/**
 * The "pay now" slice for the installment plan. The remainder is absorbed by
 * later payments so the rounded slice never exceeds the total; `0` when the
 * item itself is free.
 */
val CampingPriceItem.installmentAmountCents: Int
    get() {
        if (amountCents <= 0) return 0
        return maxOf(1, Math.round(amountCents.toDouble() / CAMPING_PRICE_ITEM_INSTALLMENT_COUNT).toInt())
    }

/** ISO 4217 code, normalized upper-case, defaulting to `EUR` when blank. */
val CampingPriceItem.resolvedCurrency: String
    get() = currency.trim().uppercase(Locale.US).ifBlank { "EUR" }

/** One-off Stripe charge (card / wallet) is offered. */
val CampingPriceItem.offersCardOneTime: Boolean
    get() = CampingPaymentOption.CardOneTime in paymentOptions

/** A 3-payment Stripe installment plan is offered. */
val CampingPriceItem.offersInstallments: Boolean
    get() = CampingPaymentOption.CardInstallments in paymentOptions

/** A manual bank transfer is offered AND a usable IBAN is present. */
val CampingPriceItem.offersBankTransfer: Boolean
    get() = CampingPaymentOption.BankTransfer in paymentOptions && !iban.isNullOrBlank()

/** True when this item carries any in-app (Stripe) payment means. */
val CampingPriceItem.offersCardPayment: Boolean
    get() = offersCardOneTime || offersInstallments

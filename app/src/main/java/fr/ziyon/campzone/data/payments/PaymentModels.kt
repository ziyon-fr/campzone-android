package fr.ziyon.campzone.data.payments

import fr.ziyon.campzone.data.model.PaymentKind
import java.util.Date
import java.util.UUID

/**
 * One line on a receipt/invoice (mirrors iOS `PaymentLineItem`). A registration
 * bundle carries one row per family participant; a single payment uses one row.
 * `kind` is a per-line override — `null` means "use the parent request's kind",
 * which lets one Stripe charge carry mixed kinds (registration + transport).
 */
data class PaymentLineItem(
    val id: String = UUID.randomUUID().toString(),
    val referenceId: String,
    val title: String,
    val amountCents: Int,
    val kind: PaymentKind? = null,
)

/** Backend-written payment audit doc (`campings/{cid}/payments/{id}`), read-only. */
data class PaymentRecord(
    val id: String,
    val uid: String,
    val kind: PaymentKind?,
    val campingId: String,
    val referenceId: String,
    val referenceIds: List<String>,
    val amountCents: Int,
    val currency: String,
    val status: String,
    val paid: Boolean,
    val summary: String?,
    val lineItems: List<PaymentLineItem>,
    val createdAt: Date?,
    val updatedAt: Date?,
)

/** Client-written receipt (`campings/{cid}/invoices/{paymentIntentId}`). */
data class PaymentInvoice(
    val id: String,
    val invoiceNumber: String,
    val paymentId: String,
    val uid: String,
    val kind: PaymentKind?,
    val campingId: String,
    val referenceId: String,
    val referenceIds: List<String>,
    val lineItems: List<PaymentLineItem>,
    val amountCents: Int,
    val currency: String,
    val status: String,
    val issuedAt: Date?,
    val paidAt: Date?,
)

/** A payment record + its optional invoice, merged for the history/receipts UI. */
data class PaymentProof(
    val id: String,
    val payment: PaymentRecord?,
    val invoice: PaymentInvoice?,
) {
    val amountCents: Int get() = invoice?.amountCents ?: payment?.amountCents ?: 0
    val currency: String get() = invoice?.currency ?: payment?.currency ?: "eur"
    val lineItems: List<PaymentLineItem>
        get() = (invoice?.lineItems ?: payment?.lineItems).orEmpty()
    val invoiceNumber: String? get() = invoice?.invoiceNumber
    val status: String get() = invoice?.status ?: payment?.status.orEmpty()
    val paid: Boolean get() = invoice?.status == "paid" || payment?.paid == true
    val issuedAt: Date? get() = invoice?.issuedAt ?: payment?.updatedAt ?: payment?.createdAt
    val referenceIds: List<String>
        get() = (invoice?.referenceIds ?: payment?.referenceIds).orEmpty()

    /** Best human label for a one-line summary. */
    fun displayTitle(fallback: String): String =
        invoice?.lineItems?.firstOrNull()?.title
            ?: payment?.summary
            ?: payment?.lineItems?.firstOrNull()?.title
            ?: fallback
}

/** Deterministic, human-friendly invoice number (mirrors iOS `CZ-<last8>`). */
fun invoiceNumberFor(paymentIntentId: String): String =
    "CZ-${paymentIntentId.takeLast(8).uppercase()}"

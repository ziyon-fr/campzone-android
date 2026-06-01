package fr.ziyon.campzone.data.payments

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.PaymentKind
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Reads payment history and writes client receipts. The backend Admin SDK writes
 * the `campings/{cid}/payments` audit docs (read-only here); after a paid charge
 * the client persists a matching `campings/{cid}/invoices/{paymentIntentId}` so
 * the user can pull a PDF receipt. Both reads are RBAC-scoped to own `uid` (or a
 * participant-profile viewer); the invoice write is gated to a paid payment doc
 * the caller owns (`isOwnPaidPayment` + `invoiceMatchesPaidPayment`).
 */
interface PaymentProofService {
    /** Persists the receipt for a paid charge; returns the written invoice. */
    suspend fun recordInvoice(paymentIntentId: String, request: PaymentRequest): PaymentInvoice

    /** Past payments + invoices for [userId] in this camp, newest first. */
    suspend fun loadProofs(campingId: String, userId: String): List<PaymentProof>
}

@Singleton
class FirestorePaymentProofService @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : PaymentProofService {

    override suspend fun recordInvoice(
        paymentIntentId: String,
        request: PaymentRequest,
    ): PaymentInvoice {
        val uid = auth.currentUser?.uid ?: error("There is no signed-in user.")
        val campingId = request.campingId?.trim()?.takeUnless { it.isBlank() }
            ?: error("A camping id is required to record an invoice.")
        val invoiceNumber = invoiceNumberFor(paymentIntentId)
        val lineItems = request.resolvedLineItems

        val payload = mapOf(
            "id" to paymentIntentId,
            "invoiceNumber" to invoiceNumber,
            "paymentID" to paymentIntentId,
            "uid" to uid,
            "kind" to request.kind.wireValue,
            "campingID" to campingId,
            "referenceID" to request.referenceId,
            "referenceIDs" to request.referenceIds,
            "lineItems" to lineItems.map(::lineItemMap),
            // `amount` must equal the payment doc's `amount` (RBAC check).
            "amount" to request.amountCents,
            "amountCents" to request.amountCents,
            "currency" to request.normalizedCurrency,
            "status" to "paid",
            "issuedAt" to FieldValue.serverTimestamp(),
            "paidAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        invoicesCollection(campingId).document(paymentIntentId)
            .set(payload, SetOptions.merge())
            .await()

        val now = Date()
        return PaymentInvoice(
            id = paymentIntentId,
            invoiceNumber = invoiceNumber,
            paymentId = paymentIntentId,
            uid = uid,
            kind = request.kind,
            campingId = campingId,
            referenceId = request.referenceId,
            referenceIds = request.referenceIds,
            lineItems = lineItems,
            amountCents = request.amountCents,
            currency = request.normalizedCurrency,
            status = "paid",
            issuedAt = now,
            paidAt = now,
        )
    }

    override suspend fun loadProofs(campingId: String, userId: String): List<PaymentProof> {
        val records = runCatching {
            paymentsCollection(campingId)
                .whereEqualTo("uid", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toPaymentRecord(it.id) }
        }.getOrDefault(emptyList())

        val invoices = runCatching {
            invoicesCollection(campingId)
                .whereEqualTo("uid", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toPaymentInvoice(it.id) }
        }.getOrDefault(emptyList())

        return merge(records, invoices)
    }

    private fun merge(records: List<PaymentRecord>, invoices: List<PaymentInvoice>): List<PaymentProof> {
        val invoiceByPaymentId = invoices.associateBy { it.paymentId }
        val recordIds = records.map { it.id }.toSet()
        val proofs = records.map { record ->
            PaymentProof(id = record.id, payment = record, invoice = invoiceByPaymentId[record.id])
        } + invoices
            .filter { it.paymentId !in recordIds }
            .map { PaymentProof(id = it.paymentId, payment = null, invoice = it) }
        return proofs.sortedByDescending { it.issuedAt ?: Date(0) }
    }

    private fun paymentsCollection(campingId: String) =
        db.collection("campings").document(campingId).collection("payments")

    private fun invoicesCollection(campingId: String) =
        db.collection("campings").document(campingId).collection("invoices")
}

private fun lineItemMap(item: PaymentLineItem): Map<String, Any?> = buildMap {
    put("id", item.id)
    put("referenceID", item.referenceId)
    put("title", item.title)
    put("amountCents", item.amountCents)
    item.kind?.let { put("kind", it.wireValue) }
}

internal fun Map<String, Any?>.toPaymentRecord(documentId: String): PaymentRecord? {
    val uid = this["uid"] as? String ?: return null
    val referenceId = (this["referenceID"] as? String).orEmpty()
    val amount = intField("amount") ?: intField("amountCents") ?: 0
    val items = lineItemsFrom(this["lineItems"])
    return PaymentRecord(
        id = documentId,
        uid = uid,
        kind = PaymentKind.fromWire(this["kind"] as? String),
        campingId = (this["campingID"] as? String).orEmpty(),
        referenceId = referenceId,
        referenceIds = stringListField("referenceIDs").ifEmpty { listOfNotNull(referenceId.takeUnless { it.isBlank() }) },
        amountCents = amount,
        currency = (this["currency"] as? String) ?: "eur",
        status = (this["status"] as? String).orEmpty(),
        paid = this["paid"] as? Boolean ?: false,
        summary = this["summary"] as? String,
        lineItems = items.ifEmpty { fallbackLineItems(referenceId, this["summary"] as? String, amount) },
        createdAt = dateField("createdAt"),
        updatedAt = dateField("updatedAt"),
    )
}

internal fun Map<String, Any?>.toPaymentInvoice(documentId: String): PaymentInvoice? {
    val uid = this["uid"] as? String ?: return null
    val paymentId = (this["paymentID"] as? String) ?: documentId
    val referenceId = (this["referenceID"] as? String).orEmpty()
    val amount = intField("amount") ?: intField("amountCents") ?: 0
    val items = lineItemsFrom(this["lineItems"])
    return PaymentInvoice(
        id = documentId,
        invoiceNumber = (this["invoiceNumber"] as? String) ?: invoiceNumberFor(paymentId),
        paymentId = paymentId,
        uid = uid,
        kind = PaymentKind.fromWire(this["kind"] as? String),
        campingId = (this["campingID"] as? String).orEmpty(),
        referenceId = referenceId,
        referenceIds = stringListField("referenceIDs").ifEmpty { listOfNotNull(referenceId.takeUnless { it.isBlank() }) },
        lineItems = items.ifEmpty { fallbackLineItems(referenceId, null, amount) },
        amountCents = amount,
        currency = (this["currency"] as? String) ?: "eur",
        status = (this["status"] as? String).orEmpty(),
        issuedAt = dateField("issuedAt"),
        paidAt = dateField("paidAt"),
    )
}

private fun fallbackLineItems(referenceId: String, title: String?, amount: Int): List<PaymentLineItem> =
    listOf(PaymentLineItem(referenceId = referenceId, title = title ?: "Payment", amountCents = amount))

private fun lineItemsFrom(value: Any?): List<PaymentLineItem> {
    val rows = value as? List<*> ?: return emptyList()
    return rows.mapNotNull { row ->
        val map = row as? Map<*, *> ?: return@mapNotNull null
        val referenceId = map["referenceID"] as? String ?: return@mapNotNull null
        val title = map["title"] as? String ?: return@mapNotNull null
        val amount = (map["amountCents"] as? Number)?.toInt()
            ?: (map["amount"] as? Number)?.toInt()
            ?: 0
        PaymentLineItem(
            id = map["id"] as? String ?: referenceId,
            referenceId = referenceId,
            title = title,
            amountCents = amount,
            kind = PaymentKind.fromWire(map["kind"] as? String),
        )
    }
}

private fun Map<String, Any?>.intField(key: String): Int? = (this[key] as? Number)?.toInt()

private fun Map<String, Any?>.stringListField(key: String): List<String> =
    (this[key] as? List<*>)?.mapNotNull { it as? String }.orEmpty()

private fun Map<String, Any?>.dateField(key: String): Date? = when (val value = this[key]) {
    is Timestamp -> value.toDate()
    is Date -> value
    else -> null
}

class FakePaymentProofService(
    initial: List<PaymentProof> = emptyList(),
) : PaymentProofService {
    private val proofs = initial.toMutableList()

    override suspend fun recordInvoice(paymentIntentId: String, request: PaymentRequest): PaymentInvoice {
        val invoice = PaymentInvoice(
            id = paymentIntentId,
            invoiceNumber = invoiceNumberFor(paymentIntentId),
            paymentId = paymentIntentId,
            uid = "preview-user",
            kind = request.kind,
            campingId = request.campingId.orEmpty(),
            referenceId = request.referenceId,
            referenceIds = request.referenceIds,
            lineItems = request.resolvedLineItems,
            amountCents = request.amountCents,
            currency = request.normalizedCurrency,
            status = "paid",
            issuedAt = Date(),
            paidAt = Date(),
        )
        proofs.add(PaymentProof(id = paymentIntentId, payment = null, invoice = invoice))
        return invoice
    }

    override suspend fun loadProofs(campingId: String, userId: String): List<PaymentProof> =
        proofs.filter { (it.invoice?.campingId ?: it.payment?.campingId) == campingId }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentProofBindings {
    @Binds
    abstract fun bindPaymentProofService(impl: FirestorePaymentProofService): PaymentProofService
}

package fr.ziyon.campzone.data.payments

import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.BuildConfig
import fr.ziyon.campzone.data.model.PaymentKind
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PaymentRequest(
    val kind: PaymentKind,
    val campingId: String?,
    val referenceId: String,
    val amountCents: Int,
    val currency: String = "eur",
    /** Human label shown on the receipt and PaymentSheet line. */
    val summary: String = "",
    /**
     * Receipt rows. Empty → a single row mirroring [summary]/[referenceId]. A
     * bundle (e.g. registration + transport in one charge) carries one row per
     * kind, each with its own [PaymentLineItem.kind].
     */
    val lineItems: List<PaymentLineItem> = emptyList(),
) {
    val normalizedCurrency: String
        get() = currency.trim().lowercase(Locale.US).ifBlank { "eur" }

    /** Positive-amount rows, defaulting to a single row when none were given. */
    val resolvedLineItems: List<PaymentLineItem>
        get() = lineItems.filter { it.amountCents > 0 }.ifEmpty {
            listOf(
                PaymentLineItem(
                    referenceId = referenceId,
                    title = summary.ifBlank { referenceId },
                    amountCents = amountCents,
                ),
            )
        }

    val referenceIds: List<String>
        get() = resolvedLineItems.map { it.referenceId }.toSortedSet().toList()

    /** Distinct kinds carried by the line items, primary kind first. */
    val kindsInLineItems: List<PaymentKind>
        get() = buildList {
            add(kind)
            resolvedLineItems.forEach { item ->
                val resolved = item.kind ?: kind
                if (resolved !in this) add(resolved)
            }
        }

    /**
     * A copy restricted to the line items whose effective kind is [forKind] —
     * used to build the follow-up `confirm()` that flips a single Firestore
     * sub-collection (registrations OR transportationBookings, never both).
     */
    fun subrequest(forKind: PaymentKind): PaymentRequest? {
        val filtered = resolvedLineItems.filter { (it.kind ?: kind) == forKind }
        if (filtered.isEmpty()) return null
        val subtotal = filtered.sumOf { it.amountCents }
        val reference = if (filtered.size == 1) {
            filtered.first().referenceId
        } else {
            "bundle-" + filtered.joinToString("|") { it.referenceId }
        }
        return copy(
            kind = forKind,
            referenceId = reference,
            amountCents = subtotal,
            lineItems = filtered,
        )
    }
}

data class PaymentSheetIntent(
    val paymentIntentId: String,
    val paymentIntentClientSecret: String,
    val ephemeralKeySecret: String,
    val customerId: String,
    val publishableKey: String,
    val amountCents: Int,
    val currency: String,
)

data class PaymentConfirmation(
    val paid: Boolean,
    val status: String,
    val kind: PaymentKind?,
    val campingId: String?,
    val referenceId: String?,
)

interface PaymentService {
    suspend fun createPaymentIntent(request: PaymentRequest): PaymentSheetIntent

    suspend fun confirmPayment(
        paymentIntentId: String,
        kind: PaymentKind,
        campingId: String?,
        referenceId: String,
    ): PaymentConfirmation

    /**
     * Confirms a charge carrying the full request (referenceIDs/summary/line
     * items) so bundled, multi-kind charges flip the right Firestore records.
     * Defaults to the field-based [confirmPayment] for fakes that don't override.
     */
    suspend fun confirmPayment(
        paymentIntentId: String,
        request: PaymentRequest,
    ): PaymentConfirmation =
        confirmPayment(paymentIntentId, request.kind, request.campingId, request.referenceId)
}

@Singleton
class BackendPaymentService @Inject constructor(
    private val auth: FirebaseAuth,
) : PaymentService {

    override suspend fun createPaymentIntent(request: PaymentRequest): PaymentSheetIntent {
        val token = auth.idTokenOrThrow()
        return withContext(Dispatchers.IO) {
            val response = postJson(
                url = "${BuildConfig.BACKEND_BASE_URL}/payments/intent",
                bearerToken = token,
                body = PaymentPayload.intentPayload(request),
                failureMessage = "Payment could not be prepared.",
            )
            PaymentPayload.parseIntentResponse(response)
        }
    }

    override suspend fun confirmPayment(
        paymentIntentId: String,
        kind: PaymentKind,
        campingId: String?,
        referenceId: String,
    ): PaymentConfirmation {
        val token = auth.idTokenOrThrow()
        return withContext(Dispatchers.IO) {
            val response = postJson(
                url = "${BuildConfig.BACKEND_BASE_URL}/payments/confirm",
                bearerToken = token,
                body = PaymentPayload.confirmPayload(
                    paymentIntentId = paymentIntentId,
                    kind = kind,
                    campingId = campingId,
                    referenceId = referenceId,
                ),
                failureMessage = "Payment could not be confirmed.",
            )
            PaymentPayload.parseConfirmationResponse(response)
        }
    }

    override suspend fun confirmPayment(
        paymentIntentId: String,
        request: PaymentRequest,
    ): PaymentConfirmation {
        val token = auth.idTokenOrThrow()
        return withContext(Dispatchers.IO) {
            val response = postJson(
                url = "${BuildConfig.BACKEND_BASE_URL}/payments/confirm",
                bearerToken = token,
                body = PaymentPayload.confirmPayload(request, paymentIntentId),
                failureMessage = "Payment could not be confirmed.",
            )
            PaymentPayload.parseConfirmationResponse(response)
        }
    }

    private suspend fun FirebaseAuth.idTokenOrThrow(): String =
        currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: error("There is no signed-in user.")

    private fun postJson(
        url: String,
        bearerToken: String,
        body: JSONObject,
        failureMessage: String,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { output ->
            output.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(response.ifBlank { failureMessage })
        }
        return response
    }
}

internal object PaymentPayload {
    private const val StripeVersion = "2024-06-20"

    fun intentPayload(request: PaymentRequest): JSONObject {
        require(request.amountCents > 0) { "Payment amount must be positive." }
        require(request.referenceId.isNotBlank()) { "Payment reference is required." }

        return JSONObject()
            .put("amount", request.amountCents)
            .put("currency", request.normalizedCurrency)
            .put("kind", request.kind.wireValue)
            .put("referenceID", request.referenceId)
            .put("referenceIDs", JSONArray(request.referenceIds))
            .put("summary", request.summary)
            .put("lineItems", lineItemsArray(request.resolvedLineItems))
            .put("stripeVersion", StripeVersion)
            .apply {
                request.campingId?.trim()?.takeUnless { it.isBlank() }
                    ?.let { put("campingID", it) }
            }
    }

    fun confirmPayload(
        paymentIntentId: String,
        kind: PaymentKind,
        campingId: String?,
        referenceId: String,
    ): JSONObject {
        require(paymentIntentId.isNotBlank()) { "Payment intent id is required." }
        require(referenceId.isNotBlank()) { "Payment reference is required." }

        return JSONObject()
            .put("paymentIntentId", paymentIntentId)
            .put("kind", kind.wireValue)
            .put("referenceID", referenceId)
            .apply {
                campingId?.trim()?.takeUnless { it.isBlank() }
                    ?.let { put("campingID", it) }
            }
    }

    fun confirmPayload(request: PaymentRequest, paymentIntentId: String): JSONObject {
        require(paymentIntentId.isNotBlank()) { "Payment intent id is required." }
        require(request.referenceId.isNotBlank()) { "Payment reference is required." }

        return JSONObject()
            .put("paymentIntentId", paymentIntentId)
            .put("kind", request.kind.wireValue)
            .put("referenceID", request.referenceId)
            .put("referenceIDs", JSONArray(request.referenceIds))
            .put("summary", request.summary)
            .put("lineItems", lineItemsArray(request.resolvedLineItems))
            .apply {
                request.campingId?.trim()?.takeUnless { it.isBlank() }
                    ?.let { put("campingID", it) }
            }
    }

    private fun lineItemsArray(items: List<PaymentLineItem>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
                .put("id", item.id)
                .put("referenceID", item.referenceId)
                .put("title", item.title)
                .put("amountCents", item.amountCents)
            item.kind?.let { obj.put("kind", it.wireValue) }
            array.put(obj)
        }
        return array
    }

    fun parseIntentResponse(response: String): PaymentSheetIntent {
        val data = JSONObject(response).dataObject()
        return PaymentSheetIntent(
            paymentIntentId = data.requiredString("paymentIntentId"),
            paymentIntentClientSecret = data.requiredString("paymentIntentClientSecret"),
            ephemeralKeySecret = data.requiredString("ephemeralKeySecret"),
            customerId = data.requiredString("customerId"),
            publishableKey = data.requiredString("publishableKey"),
            amountCents = data.optInt("amount"),
            currency = data.optString("currency", "eur"),
        )
    }

    fun parseConfirmationResponse(response: String): PaymentConfirmation {
        val data = JSONObject(response).dataObject()
        return PaymentConfirmation(
            paid = data.optBoolean("paid"),
            status = data.optString("status"),
            kind = PaymentKind.fromWire(data.optString("kind")),
            campingId = data.optString("campingID").takeUnless { it.isBlank() },
            referenceId = data.optString("referenceID").takeUnless { it.isBlank() },
        )
    }

    private fun JSONObject.dataObject(): JSONObject =
        optJSONObject("data") ?: this

    private fun JSONObject.requiredString(name: String): String =
        optString(name).takeUnless { it.isBlank() }
            ?: error("Payment response is missing `$name`.")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentBindings {
    @Binds
    abstract fun bindPaymentService(service: BackendPaymentService): PaymentService
}

package fr.ziyon.campzone.ui.payments

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import fr.ziyon.campzone.R
import fr.ziyon.campzone.data.payments.PaymentProof
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a one-page A4 PDF receipt for a [PaymentProof] (invoice number, camp,
 * date, line items, total) into the app cache and shares it via the existing
 * `${applicationId}.fileprovider` (file_paths.xml already exposes the cache).
 * Client-side only — mirrors the iOS `PaymentInvoicePDFView` receipt.
 */
object PaymentReceiptPdf {

    private const val PageWidth = 595 // A4 @ 72dpi
    private const val PageHeight = 842
    private const val Margin = 48f

    fun write(context: Context, proof: PaymentProof, campTitle: String): File {
        val document = PdfDocument()
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(PageWidth, PageHeight, 1).create(),
        )
        val canvas = page.canvas

        val title = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val label = Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            isAntiAlias = true
        }
        val bodyBold = Paint(body).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val rightEdge = PageWidth - Margin

        var y = Margin + 8f
        canvas.drawText(context.getString(R.string.receipt_pdf_title), Margin, y, title)
        y += 26f
        canvas.drawText(campTitle, Margin, y, body)
        y += 18f
        proof.invoiceNumber?.let {
            canvas.drawText(context.getString(R.string.receipt_pdf_number, it), Margin, y, label)
            y += 16f
        }
        val dateText = proof.issuedAt?.let { dateFormatter.format(it) }
            ?: dateFormatter.format(Date())
        canvas.drawText(context.getString(R.string.receipt_pdf_date, dateText), Margin, y, label)
        y += 16f
        canvas.drawText(
            context.getString(R.string.receipt_pdf_status, proof.status.replaceFirstChar { it.uppercase() }),
            Margin,
            y,
            label,
        )

        y += 28f
        canvas.drawLine(Margin, y, rightEdge, y, rule)
        y += 22f

        proof.lineItems.forEach { item ->
            canvas.drawText(item.title, Margin, y, body)
            val amount = formatPaymentAmount(item.amountCents, proof.currency)
            canvas.drawText(amount, rightEdge - body.measureText(amount), y, body)
            y += 20f
        }

        y += 6f
        canvas.drawLine(Margin, y, rightEdge, y, rule)
        y += 22f
        val totalLabel = context.getString(R.string.receipt_pdf_total)
        canvas.drawText(totalLabel, Margin, y, bodyBold)
        val total = formatPaymentAmount(proof.amountCents, proof.currency)
        canvas.drawText(total, rightEdge - bodyBold.measureText(total), y, bodyBold)

        val footer = Paint().apply { color = Color.GRAY; textSize = 10f; isAntiAlias = true }
        canvas.drawText(context.getString(R.string.receipt_pdf_footer), Margin, PageHeight - Margin, footer)

        document.finishPage(page)

        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "campzone-receipt-${proof.id}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.receipt_pdf_share)),
        )
    }

    private val dateFormatter = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
}

package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `contentReports/{reportId}` (`02-firestore-schema.md` §6.4). Submit is a full
 * `set` (no merge). This is a **brittle read**: [toContentReport] throws on a
 * missing required field or unknown enum so the admin list aborts entirely
 * (matching iOS) rather than silently dropping one record. `target` is
 * camelCase `chatMessage`; `note` is written even when `""`.
 */
data class ContentReport(
    val id: String,
    val target: ContentReportTarget,
    val contentId: String,
    val reporterId: String,
    val reason: ContentReportReason,
    val note: String,
    val status: ContentReportStatus,
    val createdAt: Date,
    val reviewedById: String? = null,
    val reviewedAt: Date? = null,
)

/** Brittle decode - throws [IllegalArgumentException] on any missing required field or unknown enum. */
internal fun Map<String, Any?>.toContentReport(documentId: String): ContentReport {
    val target = ContentReportTarget.fromWire(stringValue("target"))
        ?: throw IllegalArgumentException("contentReport $documentId: missing/unknown target")
    val contentId = stringValue("contentID")
        ?: throw IllegalArgumentException("contentReport $documentId: missing contentID")
    val reporterId = stringValue("reporterID")
        ?: throw IllegalArgumentException("contentReport $documentId: missing reporterID")
    val reason = ContentReportReason.fromWire(stringValue("reason"))
        ?: throw IllegalArgumentException("contentReport $documentId: missing/unknown reason")
    val note = rawStringValue("note")
        ?: throw IllegalArgumentException("contentReport $documentId: missing note")
    val status = ContentReportStatus.fromWire(stringValue("status"))
        ?: throw IllegalArgumentException("contentReport $documentId: missing/unknown status")
    val createdAt = dateValue("createdAt")
        ?: throw IllegalArgumentException("contentReport $documentId: missing createdAt")
    return ContentReport(
        id = stringValue("id") ?: documentId,
        target = target,
        contentId = contentId,
        reporterId = reporterId,
        reason = reason,
        note = note,
        status = status,
        createdAt = createdAt,
        reviewedById = stringValue("reviewedByID"),
        reviewedAt = dateValue("reviewedAt"),
    )
}

internal object ContentReportPayload {

    /** Full-set submit. All required fields present; `note` written even when `""`. */
    fun reportPayload(
        report: ContentReport,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        require(report.id.isNotBlank()) { "contentReport id is required." }
        require(report.contentId.isNotBlank()) { "contentReport contentID is required." }
        require(report.reporterId.isNotBlank()) { "contentReport reporterID is required." }

        return linkedMapOf(
            "id" to report.id,
            "target" to report.target.wireValue,
            "contentID" to report.contentId,
            "reporterID" to report.reporterId,
            "reason" to report.reason.wireValue,
            "note" to report.note,
            "status" to ContentReportStatus.Pending.wireValue,
            "createdAt" to serverTimestamp,
        )
    }

    fun statusUpdatePayload(
        status: ContentReportStatus,
        reviewedById: String,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        require(status == ContentReportStatus.Dismissed || status == ContentReportStatus.Resolved) {
            "contentReport status update must dismiss or resolve."
        }
        require(reviewedById.isNotBlank()) { "contentReport reviewerID is required." }

        return linkedMapOf(
            "status" to status.wireValue,
            "reviewedByID" to reviewedById,
            "reviewedAt" to serverTimestamp,
        )
    }
}

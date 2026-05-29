package fr.ziyon.campzone.data.moderation

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.ContentReport
import fr.ziyon.campzone.data.model.ContentReportPayload
import fr.ziyon.campzone.data.model.ContentReportReason
import fr.ziyon.campzone.data.model.ContentReportStatus
import fr.ziyon.campzone.data.model.ContentReportTarget
import fr.ziyon.campzone.data.model.toContentReport
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface ContentReportService {
    suspend fun submitReport(report: ContentReport): ContentReport
    suspend fun loadReports(): List<ContentReport>
    suspend fun updateStatus(
        reportId: String,
        status: ContentReportStatus,
        reviewerId: String,
    )
}

@Singleton
class FirestoreContentReportService @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ContentReportService {

    override suspend fun submitReport(report: ContentReport): ContentReport {
        val currentUserId = auth.currentUser?.uid
        require(currentUserId == report.reporterId) { "You can only submit reports as yourself." }

        reportsCollection()
            .document(report.id)
            .set(ContentReportPayload.reportPayload(report, FieldValue.serverTimestamp()))
            .await()
        return report.copy(createdAt = report.createdAt)
    }

    override suspend fun loadReports(): List<ContentReport> {
        val snapshot = reportsCollection()
            .orderBy(Field.CreatedAt, Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.map { document ->
            @Suppress("UNCHECKED_CAST")
            val data = (document.data as? Map<String, Any?>)
                ?: throw IllegalArgumentException("contentReport ${document.id}: missing data")
            data.toContentReport(document.id)
        }
    }

    override suspend fun updateStatus(
        reportId: String,
        status: ContentReportStatus,
        reviewerId: String,
    ) {
        val currentUserId = auth.currentUser?.uid
        require(currentUserId == reviewerId) { "You can only review reports as yourself." }
        require(reportId.isNotBlank()) { "contentReport id is required." }

        reportsCollection()
            .document(reportId)
            .update(ContentReportPayload.statusUpdatePayload(status, reviewerId, FieldValue.serverTimestamp()))
            .await()
    }

    private fun reportsCollection() = db.collection(Collection.ContentReports)

    private object Collection {
        const val ContentReports = "contentReports"
    }

    private object Field {
        const val CreatedAt = "createdAt"
    }
}

class FakeContentReportService(
    initialReports: List<ContentReport> = previewContentReports(),
    var shouldFail: Boolean = false,
) : ContentReportService {
    private val reports = initialReports.toMutableList()

    override suspend fun submitReport(report: ContentReport): ContentReport {
        failIfNeeded()
        ContentReportPayload.reportPayload(report, Date())
        reports.add(report.copy(status = ContentReportStatus.Pending, createdAt = report.createdAt))
        return report
    }

    override suspend fun loadReports(): List<ContentReport> {
        failIfNeeded()
        return reports.sortedByDescending { it.createdAt }
    }

    override suspend fun updateStatus(
        reportId: String,
        status: ContentReportStatus,
        reviewerId: String,
    ) {
        failIfNeeded()
        ContentReportPayload.statusUpdatePayload(status, reviewerId, Date())
        val index = reports.indexOfFirst { it.id == reportId }
        if (index >= 0) {
            reports[index] = reports[index].copy(
                status = status,
                reviewedById = reviewerId,
                reviewedAt = Date(),
            )
        }
    }

    private fun failIfNeeded() {
        if (shouldFail) throw IllegalStateException("FakeContentReportService configured to fail.")
    }
}

fun previewContentReports(): List<ContentReport> =
    listOf(
        ContentReport(
            id = "report-1",
            target = ContentReportTarget.Announcement,
            contentId = "packing-list",
            reporterId = "user-abc",
            reason = ContentReportReason.Misinformation,
            note = "The packing list has incorrect equipment details.",
            status = ContentReportStatus.Pending,
            createdAt = Date(System.currentTimeMillis() - 3_600_000),
        ),
        ContentReport(
            id = "report-2",
            target = ContentReportTarget.Camping,
            contentId = "summer-camp-2026",
            reporterId = "user-xyz",
            reason = ContentReportReason.Inappropriate,
            note = "",
            status = ContentReportStatus.Pending,
            createdAt = Date(System.currentTimeMillis() - 7_200_000),
        ),
        ContentReport(
            id = "report-3",
            target = ContentReportTarget.ChatMessage,
            contentId = "message-99",
            reporterId = "user-def",
            reason = ContentReportReason.Spam,
            note = "",
            status = ContentReportStatus.Resolved,
            createdAt = Date(System.currentTimeMillis() - 86_400_000),
            reviewedById = "admin-1",
            reviewedAt = Date(System.currentTimeMillis() - 43_200_000),
        ),
    )

@Module
@InstallIn(SingletonComponent::class)
abstract class ModerationBindings {
    @Binds
    @Singleton
    abstract fun bindContentReportService(impl: FirestoreContentReportService): ContentReportService
}

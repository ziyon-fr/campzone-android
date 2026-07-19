package fr.ziyon.campzone.data.songbook

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

enum class SongDocumentKind(
    val fallbackExtension: String,
    val mimeType: String,
) {
    SheetPdf("pdf", "application/pdf"),
    Slides("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
}

data class SongDocumentItem(
    val kind: SongDocumentKind,
    val remoteUrl: String,
    val title: String,
) {
    val id: String get() = "${kind.name}-$remoteUrl"
}

interface SongDocumentDownloading {
    suspend fun localFile(remoteUrl: String, kind: SongDocumentKind): File
}

class SongDocumentDownloader(
    context: Context,
    private val directory: File = File(context.applicationContext.cacheDir, "SongDocuments"),
) : SongDocumentDownloading {

    init {
        directory.mkdirs()
    }

    override suspend fun localFile(remoteUrl: String, kind: SongDocumentKind): File = withContext(Dispatchers.IO) {
        require(remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://")) {
            "Only HTTP(S) song documents can be opened."
        }
        directory.mkdirs()

        val destination = File(directory, cachedFileName(remoteUrl, kind))
        if (destination.exists() && destination.length() > 0L) {
            return@withContext destination
        }

        val temp = File.createTempFile("song-document-", ".tmp", directory)
        try {
            val connection = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    error("Document download failed with HTTP $status.")
                }
                connection.inputStream.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }

            if (destination.exists()) destination.delete()
            check(temp.renameTo(destination)) { "Could not cache the song document." }
            destination
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    companion object {
        internal fun cachedFileName(remoteUrl: String, kind: SongDocumentKind): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(remoteUrl.toByteArray(Charsets.UTF_8))
                .take(12)
                .joinToString("") { "%02x".format(it) }
            return "$digest.${extensionFor(remoteUrl, kind)}"
        }

        private fun extensionFor(remoteUrl: String, kind: SongDocumentKind): String {
            val fileName = runCatching { URI(remoteUrl).path.orEmpty() }
                .getOrDefault("")
                .substringAfterLast('/')
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
                .takeIf { it.isNotBlank() && it.length <= 12 }
            return extension ?: kind.fallbackExtension
        }
    }
}

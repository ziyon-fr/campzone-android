package fr.ziyon.campzone.data.songbook

import fr.ziyon.campzone.data.model.ChordSheet
import fr.ziyon.campzone.data.model.SongAudio
import fr.ziyon.campzone.data.model.SongAudioKind
import fr.ziyon.campzone.data.model.SongAudioTrackType
import fr.ziyon.campzone.data.model.SongLyricsPart
import fr.ziyon.campzone.data.model.SongLyricsPartKind
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

data class CantusArtistRef(
    val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
)

data class CantusSongbookRef(
    val id: String? = null,
    val slug: String? = null,
    val title: String? = null,
    val number: Int? = null,
)

data class CantusMedia(
    val sheetPdfUrl: String? = null,
    val powerpointUrl: String? = null,
    val audioUrl: String? = null,
    val audioFiles: List<CantusAudioFile> = emptyList(),
    val musicXmlUrl: String? = null,
)

data class CantusAudioFile(
    val id: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val storagePath: String? = null,
    val downloadUrl: String? = null,
    val kind: String? = null,
    val duration: Double? = null,
    val fileSize: Long? = null,
    val voiceType: String? = null,
    val type: String? = null,
    val displayName: String? = null,
)

data class CantusSong(
    val id: String,
    val slug: String,
    val title: String,
    val artist: CantusArtistRef? = null,
    val key: String? = null,
    val bpm: Double? = null,
    val timeSignature: String? = null,
    val language: String? = null,
    val themes: List<String> = emptyList(),
    val songbooks: List<CantusSongbookRef> = emptyList(),
    val media: CantusMedia? = null,
    val chordedLyrics: String? = null,
    val author: String? = null,
    val copyright: String? = null,
) {
    val artistName: String get() = artist?.name.orEmpty()

    fun languageDisplayName(locale: Locale = Locale.getDefault()): String =
        language?.takeUnless { it.isBlank() }
            ?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale).replaceFirstChar(Char::titlecase) }
            .orEmpty()

    fun songDraft(campingId: String, orderIndex: Int): SongDraft {
        val chordedText = chordedLyrics.orEmpty()
        val parsedSheet = if (chordedText.isBlank()) {
            ChordSheet(id = UUID.randomUUID().toString())
        } else {
            ChordProParser.parse(chordedText)
        }
        val sheet = parsedSheet.copy(
            originalKey = ChordProParser.keySelectionFromApiKey(key) ?: parsedSheet.originalKey,
            tempo = bpm?.takeIf { it > 0 }?.let { kotlin.math.round(it).toInt() },
            timeSignature = timeSignature?.takeUnless { it.isBlank() },
        )
        val parts = lyricsPartsFrom(sheet.lines)
        val lyricsText = parts.joinToString("\n\n") { part ->
            "[${part.displayTitle()}]\n${part.text}"
        }

        val audioFiles = remoteAudioFiles

        return SongDraft(
            id = UUID.randomUUID().toString(),
            campingId = campingId,
            title = title,
            artist = artistName,
            composer = author.orEmpty(),
            lyrics = lyricsText,
            chords = ChordProParser.legacyText(sheet).trim(),
            chordedLyrics = ChordProParser.serialize(sheet).trim(),
            cantusSlug = slug,
            existingAudio = audioFiles.firstOrNull { it.trackType == SongAudioTrackType.MainSong }
                ?: audioFiles.firstOrNull(),
            existingAudioFiles = audioFiles,
            pendingAudioFiles = emptyList(),
            lyricsParts = parts,
            chordSheet = sheet,
            youtubeLink = "",
            pdfLink = media?.sheetPdfUrl.orEmpty(),
            pptxLink = media?.powerpointUrl.orEmpty(),
            orderIndex = orderIndex,
            isPinnedTheme = false,
            favoriteUserIds = emptyList(),
        )
    }

    private val remoteAudioFiles: List<SongAudio>
        get() {
            val mapped = media?.audioFiles
                .orEmpty()
                .mapIndexedNotNull { index, audio -> audio.toSongAudio(index) }
            if (mapped.isNotEmpty()) return mapped
            return remoteAudio?.let(::listOf).orEmpty()
        }

    private val remoteAudio: SongAudio?
        get() {
            val raw = media?.audioUrl?.takeUnless { it.isBlank() } ?: return null
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            val fileName = uri.path?.substringAfterLast('/')?.takeUnless { it.isBlank() } ?: "$slug.mp3"
            val (kind, contentType) = when (fileName.substringAfterLast('.', "").lowercase()) {
                "mp3" -> SongAudioKind.Mp3 to "audio/mpeg"
                "m4a" -> SongAudioKind.M4a to "audio/mp4"
                "aac" -> SongAudioKind.Aac to "audio/aac"
                "wav" -> SongAudioKind.Wav to "audio/wav"
                else -> return null
            }
            return SongAudio(
                id = UUID.randomUUID().toString(),
                fileName = fileName,
                contentType = contentType,
                downloadUrl = raw,
                kind = kind,
                voiceType = SongAudioTrackType.MainSong.wireValue,
            )
        }

    private fun CantusAudioFile.toSongAudio(index: Int): SongAudio? {
        val raw = downloadUrl?.takeUnless { it.isBlank() } ?: return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val fallbackName = uri.path?.substringAfterLast('/')?.takeUnless { it.isBlank() }
            ?: "$slug-${index + 1}.${kind ?: "mp3"}"
        val resolvedFileName = fileName?.takeUnless { it.isBlank() } ?: fallbackName
        val resolvedKind = SongAudioKind.fromCantus(kind, resolvedFileName, contentType, raw) ?: return null

        return SongAudio(
            id = id?.takeUnless { it.isBlank() } ?: "$slug-audio-${index + 1}",
            fileName = resolvedFileName,
            contentType = contentType?.takeUnless { it.isBlank() } ?: resolvedKind.defaultContentType(),
            storagePath = storagePath.orEmpty(),
            downloadUrl = raw,
            kind = resolvedKind,
            duration = duration?.coerceAtLeast(0.0) ?: 0.0,
            fileSize = fileSize?.coerceAtLeast(0L) ?: 0L,
            voiceType = trackType().wireValue,
            displayName = displayName?.trim().orEmpty(),
        )
    }

    private fun CantusAudioFile.trackType(): SongAudioTrackType {
        val rawType = voiceType?.trim()?.takeUnless { it.isBlank() }
            ?: type?.trim()?.takeUnless { it.isBlank() }
        SongAudioTrackType.fromWireOrNull(rawType)?.let { return it }
        val labels = listOfNotNull(
            displayName?.trim()?.takeUnless { it.isBlank() },
            fileName?.trim()?.takeUnless { it.isBlank() },
        )
        if (labels.isEmpty() && rawType == null) return SongAudioTrackType.MainSong

        val normalized = labels
            .joinToString(" ")
            .lowercase(Locale.ROOT)
            .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            .replace(Regex("\\p{M}+"), "")
            .filter { it.isLetterOrDigit() }

        return when {
            normalized.contains("main") || normalized.contains("principal") -> SongAudioTrackType.MainSong
            normalized.contains("playback") ||
                normalized.contains("backingtrack") ||
                normalized.contains("acompanhamento") -> SongAudioTrackType.Playback
            normalized.contains("instrumental") -> SongAudioTrackType.Instrumental
            normalized.contains("mezzosoprano") -> SongAudioTrackType.MezzoSoprano
            normalized.contains("soprano") -> SongAudioTrackType.Soprano
            normalized.contains("contralto") -> SongAudioTrackType.Contralto
            normalized.contains("baritone") ||
                normalized.contains("baritono") ||
                normalized.contains("baryton") -> SongAudioTrackType.Baritone
            normalized.contains("tenor") -> SongAudioTrackType.Tenor
            normalized.contains("bass") ||
                normalized.contains("baixo") ||
                normalized.contains("basse") -> SongAudioTrackType.Bass
            normalized.contains("alto") -> SongAudioTrackType.Alto
            else -> SongAudioTrackType.Other
        }
    }
}

private fun SongAudioKind.Companion.fromCantus(
    rawKind: String?,
    fileName: String,
    contentType: String?,
    url: String,
): SongAudioKind? {
    rawKind?.takeUnless { it.isBlank() }?.let { raw ->
        SongAudioKind.entries.firstOrNull { it.wireValue == raw }?.let { return it }
    }
    val haystack = "$fileName ${contentType.orEmpty()} $url".lowercase(Locale.ROOT)
    return when {
        ".mp3" in haystack || "mpeg" in haystack -> SongAudioKind.Mp3
        ".m4a" in haystack || "mp4" in haystack -> SongAudioKind.M4a
        ".aac" in haystack -> SongAudioKind.Aac
        ".wav" in haystack || "wave" in haystack -> SongAudioKind.Wav
        "audio/" in haystack -> SongAudioKind.Other
        else -> null
    }
}

private fun SongAudioKind.defaultContentType(): String =
    when (this) {
        SongAudioKind.Mp3 -> "audio/mpeg"
        SongAudioKind.M4a -> "audio/mp4"
        SongAudioKind.Aac -> "audio/aac"
        SongAudioKind.Wav -> "audio/wav"
        SongAudioKind.Other -> "audio/*"
    }

data class CantusArtist(
    val id: String,
    val slug: String,
    val name: String,
    val languages: List<String> = emptyList(),
    val songCount: Int? = null,
)

data class CantusSongbook(
    val id: String,
    val slug: String,
    val title: String,
    val language: String? = null,
    val publisher: String? = null,
    val songCount: Int? = null,
)

data class CantusPagination(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val page: Int = 1,
    val nextPage: Int? = null,
)

data class CantusPage<Item>(
    val data: List<Item>,
    val pagination: CantusPagination,
)

data class CantusSongQuery(
    val searchText: String = "",
    val languageCode: String? = null,
    val artistSlug: String? = null,
    val songbookSlug: String? = null,
    val page: Int = 1,
) {
    val hasActiveFilters: Boolean
        get() = languageCode != null || artistSlug != null || songbookSlug != null

    val queryItems: List<Pair<String, String>>
        get() = buildList {
            searchText.trim().takeUnless { it.isBlank() }?.let { add("q" to it) }
            languageCode?.takeUnless { it.isBlank() }?.let { add("language" to it) }
            artistSlug?.takeUnless { it.isBlank() }?.let { add("artist" to it) }
            songbookSlug?.takeUnless { it.isBlank() }?.let { add("songbook" to it) }
            add("page" to page.toString())
        }

    val cacheKey: String
        get() = listOf(
            "songs",
            "q=${searchText.trim().lowercase()}",
            "lang=${languageCode.orEmpty()}",
            "artist=${artistSlug.orEmpty()}",
            "book=${songbookSlug.orEmpty()}",
            "page=$page",
        ).joinToString("&")
}

private fun lyricsPartsFrom(lines: List<fr.ziyon.campzone.data.model.ChordLine>): List<SongLyricsPart> {
    val parts = mutableListOf<SongLyricsPart>()
    var currentTitle: String? = null
    val currentText = mutableListOf<String>()

    fun flush() {
        val text = currentText.joinToString("\n").trim()
        val title = currentTitle
        currentTitle = null
        currentText.clear()
        if (text.isBlank()) return
        val descriptor = sectionDescriptor(title)
        parts += SongLyricsPart(
            id = UUID.randomUUID().toString(),
            kind = descriptor.kind,
            number = descriptor.number,
            title = descriptor.customTitle,
            text = text,
        )
    }

    lines.forEach { line ->
        if (line.isSectionHeader) {
            flush()
            currentTitle = line.text
        } else {
            currentText += line.text
        }
    }
    flush()
    return parts
}

private data class SectionDescriptor(
    val kind: SongLyricsPartKind,
    val number: Int,
    val customTitle: String = "",
)

private fun sectionDescriptor(title: String?): SectionDescriptor {
    if (title.isNullOrBlank()) return SectionDescriptor(SongLyricsPartKind.Verse, 1)
    val lowered = title.lowercase()
    val number = title.filter(Char::isDigit).toIntOrNull()?.coerceAtLeast(1) ?: 1
    return when {
        "pre-chorus" in lowered || "pré-refrão" in lowered || "pre-refrain" in lowered ->
            SectionDescriptor(SongLyricsPartKind.PreChorus, number)
        "chorus" in lowered || "refrão" in lowered || "refrao" in lowered || "coro" in lowered ->
            SectionDescriptor(SongLyricsPartKind.Chorus, number)
        "verse" in lowered || "verso" in lowered || "estrofe" in lowered ->
            SectionDescriptor(SongLyricsPartKind.Verse, number)
        "bridge" in lowered || "ponte" in lowered ->
            SectionDescriptor(SongLyricsPartKind.Bridge, number)
        "intro" in lowered ->
            SectionDescriptor(SongLyricsPartKind.Intro, number)
        "outro" in lowered || "final" in lowered ->
            SectionDescriptor(SongLyricsPartKind.Outro, number)
        "instrumental" in lowered ->
            SectionDescriptor(SongLyricsPartKind.Instrumental, number)
        else -> SectionDescriptor(SongLyricsPartKind.Custom, number, title)
    }
}

private fun SongLyricsPart.displayTitle(): String {
    if (kind == SongLyricsPartKind.Custom && title.isNotBlank()) return title
    val base = when (kind) {
        SongLyricsPartKind.Intro -> "Intro"
        SongLyricsPartKind.Verse -> "Verse"
        SongLyricsPartKind.PreChorus -> "Pre-chorus"
        SongLyricsPartKind.Chorus -> "Chorus"
        SongLyricsPartKind.Bridge -> "Bridge"
        SongLyricsPartKind.Instrumental -> "Instrumental"
        SongLyricsPartKind.Outro -> "Outro"
        SongLyricsPartKind.Custom -> "Custom"
    }
    return when (kind) {
        SongLyricsPartKind.Intro,
        SongLyricsPartKind.Bridge,
        SongLyricsPartKind.Instrumental,
        SongLyricsPartKind.Outro,
        -> if (number > 1) "$base $number" else base
        SongLyricsPartKind.Verse,
        SongLyricsPartKind.PreChorus,
        SongLyricsPartKind.Chorus,
        SongLyricsPartKind.Custom,
        -> "$base $number"
    }
}

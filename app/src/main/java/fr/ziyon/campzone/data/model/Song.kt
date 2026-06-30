package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{id}/songs/{songId}` (`02-firestore-schema.md` §7.7). `audio` is
 * delete-when-empty; `audioFiles` falls back to `[audio]` on read. `chordSheet`
 * is always written (parsed/empty). The stored `originalKey` is **lossy** - only
 * a known set decodes back, everything else collapses to C major.
 */
data class Song(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val composer: String = "",
    val lyrics: String = "",
    val chords: String = "",
    val lyricsParts: List<SongLyricsPart> = emptyList(),
    val chordSheet: ChordSheet = ChordSheet(id = ""),
    val audio: SongAudio? = null,
    val audioFiles: List<SongAudio> = emptyList(),
    val youtubeLink: String = "",
    val pdfLink: String = "",
    val orderIndex: Int = 0,
    val isPinnedTheme: Boolean = false,
    val favoriteUserIds: List<String> = emptyList(),
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    fun isFavoritedBy(uid: String): Boolean = favoriteUserIds.contains(uid)

    /** Audio tracks normalized for legacy documents and ordered like iOS Voice Kits. */
    val normalizedAudioFiles: List<SongAudio>
        get() = normalizeSongAudioFiles(audioFiles.ifEmpty { audio?.let(::listOf).orEmpty() })

    val orderedAudioFiles: List<SongAudio>
        get() = normalizedAudioFiles.sortedWith(
            compareBy<SongAudio> { it.trackType.sortOrder }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.fileName },
        )

    /** Canonical default take. Alternative voice parts never become default playback. */
    val mainAudio: SongAudio?
        get() = normalizedAudioFiles.firstOrNull { it.trackType == SongAudioTrackType.MainSong }
            ?: audio
            ?: normalizedAudioFiles.firstOrNull()

    val alternativeAudioFiles: List<SongAudio>
        get() {
            val mainId = mainAudio?.id ?: return orderedAudioFiles
            return orderedAudioFiles.filterNot { it.id == mainId }
        }

    val hasAlternativeAudio: Boolean get() = alternativeAudioFiles.isNotEmpty()
}

data class SongLyricsPart(
    val id: String,
    val kind: SongLyricsPartKind,
    val number: Int,
    val title: String = "",
    val text: String = "",
)

data class ChordSheet(
    val id: String,
    val originalKey: String = "C",
    val tempo: Int? = null,
    val timeSignature: String? = null,
    val capo: Int? = null,
    val lines: List<ChordLine> = emptyList(),
    val updatedAt: Date? = null,
)

data class ChordLine(
    val id: String,
    val text: String = "",
    val isSectionHeader: Boolean = false,
    val chords: List<Chord> = emptyList(),
)

data class Chord(
    val id: String,
    val chord: String,
    val position: Int = 0,
    val lane: Int = 0,
    val freeX: Double? = null,
)

data class SongAudio(
    val id: String,
    val fileName: String = "Song audio",
    val contentType: String = "audio/mpeg",
    val storagePath: String = "",
    val downloadUrl: String = "",
    val kind: SongAudioKind = SongAudioKind.Mp3,
    val duration: Double = 0.0,
    val fileSize: Long = 0L,
    val voiceType: String = SongAudioTrackType.MainSong.wireValue,
    val displayName: String = "",
) {
    val trackType: SongAudioTrackType get() = SongAudioTrackType.fromWire(voiceType)

    fun withTrackType(type: SongAudioTrackType, customName: String = ""): SongAudio = copy(
        voiceType = type.wireValue,
        displayName = if (type.allowsCustomName) customName.trim() else "",
    )
}

enum class SongAudioTrackType(val wireValue: String, val sortOrder: Int) {
    MainSong("mainSong", 0),
    Playback("playback", 1),
    Instrumental("instrumental", 2),
    Soprano("soprano", 3),
    MezzoSoprano("mezzoSoprano", 4),
    Alto("alto", 5),
    Tenor("tenor", 6),
    Baritone("baritone", 7),
    Bass("bass", 8),
    Other("other", 9);

    val allowsMultiple: Boolean get() = this == Other
    val allowsCustomName: Boolean get() = this == Other

    companion object {
        fun fromWire(value: String?): SongAudioTrackType =
            entries.firstOrNull { it.wireValue == value } ?: Other
    }
}

internal fun normalizeSongAudioFiles(files: List<SongAudio>): List<SongAudio> {
    if (files.isEmpty() || files.any { it.trackType == SongAudioTrackType.MainSong }) return files
    return files.mapIndexed { index, audio ->
        if (index == 0) audio.copy(voiceType = SongAudioTrackType.MainSong.wireValue) else audio
    }
}

/** Keys that survive a round-trip; everything else collapses to C major (lossy, like iOS). */
private val DECODABLE_KEYS = mapOf(
    "G" to "G", "D" to "D", "A" to "A", "F" to "F",
    "Bb" to "Bb", "B♭" to "Bb",
    "Am" to "Am", "A minor" to "Am",
    "Em" to "Em", "E minor" to "Em",
)

internal fun decodeOriginalKey(raw: String?): String =
    raw?.let { DECODABLE_KEYS[it.trim()] } ?: "C"

// region decode

internal fun Map<String, Any?>.toSongOrNull(documentId: String): Song {
    val primaryAudio = mapValue("audio")?.toSongAudioOrNull()
    val audioFiles = normalizeSongAudioFiles(
        mapListValue("audioFiles").mapNotNull { it.toSongAudioOrNull() }
            .ifEmpty { primaryAudio?.let(::listOf).orEmpty() },
    )
    val mainAudio = audioFiles.firstOrNull { it.trackType == SongAudioTrackType.MainSong }
        ?: audioFiles.firstOrNull()
    return Song(
        id = documentId,
        title = rawStringValue("title").orEmpty(),
        artist = rawStringValue("artist").orEmpty(),
        composer = rawStringValue("composer").orEmpty(),
        lyrics = rawStringValue("lyrics").orEmpty(),
        chords = rawStringValue("chords").orEmpty(),
        lyricsParts = mapListValue("lyricsParts").mapNotNull { it.toSongLyricsPartOrNull() },
        chordSheet = mapValue("chordSheet")?.toChordSheet(documentId) ?: ChordSheet(id = documentId),
        audio = mainAudio,
        audioFiles = audioFiles,
        youtubeLink = rawStringValue("youtubeLink").orEmpty(),
        pdfLink = rawStringValue("pdfLink").orEmpty(),
        orderIndex = intValue("orderIndex") ?: 0,
        isPinnedTheme = boolValue("isPinnedTheme") ?: false,
        favoriteUserIds = stringListValue("favoriteUserIDs"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

internal fun Map<String, Any?>.toSongLyricsPartOrNull(): SongLyricsPart? {
    val id = stringValue("id") ?: return null
    return SongLyricsPart(
        id = id,
        kind = SongLyricsPartKind.fromWire(stringValue("kind")),
        number = (intValue("number") ?: 1).coerceAtLeast(1),
        title = rawStringValue("title").orEmpty(),
        text = rawStringValue("text").orEmpty(),
    )
}

internal fun Map<String, Any?>.toChordSheet(fallbackId: String): ChordSheet =
    ChordSheet(
        id = stringValue("id") ?: fallbackId,
        originalKey = decodeOriginalKey(stringValue("originalKey")),
        tempo = intValue("tempo"),
        timeSignature = stringValue("timeSignature"),
        capo = intValue("capo"),
        lines = mapListValue("lines").mapNotNull { it.toChordLineOrNull() },
        updatedAt = dateValue("updatedAt"),
    )

internal fun Map<String, Any?>.toChordLineOrNull(): ChordLine? {
    val id = stringValue("id") ?: return null
    return ChordLine(
        id = id,
        text = rawStringValue("text").orEmpty(),
        isSectionHeader = boolValue("isSectionHeader") ?: false,
        chords = mapListValue("chords").mapNotNull { it.toChordOrNull() },
    )
}

internal fun Map<String, Any?>.toChordOrNull(): Chord? {
    val id = stringValue("id") ?: return null
    val chord = stringValue("chord") ?: return null // dropped if unparseable/blank
    return Chord(
        id = id,
        chord = chord,
        position = intValue("position") ?: 0,
        lane = intValue("lane") ?: 0,
        freeX = doubleValue("freeX"),
    )
}

internal fun Map<String, Any?>.toSongAudioOrNull(): SongAudio? {
    val id = stringValue("id") ?: return null
    return SongAudio(
        id = id,
        fileName = stringValue("fileName") ?: "Song audio",
        contentType = stringValue("contentType") ?: "audio/mpeg",
        storagePath = rawStringValue("storagePath").orEmpty(),
        downloadUrl = rawStringValue("downloadURL").orEmpty(),
        kind = SongAudioKind.fromWire(stringValue("kind")),
        duration = doubleValue("duration") ?: 0.0,
        fileSize = longValue("fileSize") ?: 0L,
        voiceType = rawStringValue("voiceType").orEmpty(),
        displayName = rawStringValue("displayName").orEmpty(),
    )
}

// endregion

internal object SongPayload {
    fun songPayload(
        song: Song,
        serverTimestamp: Any,
        deleteField: Any,
        rawDate: Date,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val audioFiles = song.normalizedAudioFiles
        val mainAudio = audioFiles.firstOrNull { it.trackType == SongAudioTrackType.MainSong }
        val payload = linkedMapOf<String, Any?>(
            "title" to song.title.trim(),
            "artist" to song.artist.trim(),
            "composer" to song.composer.trim(),
            "lyrics" to song.lyrics,
            "chords" to song.chords,
            "lyricsParts" to song.lyricsParts.map(::lyricsPartMap),
            "chordSheet" to chordSheetMap(song.chordSheet, rawDate),
            "audioFiles" to audioFiles.map(::audioMap),
            "youtubeLink" to song.youtubeLink.trim(),
            "pdfLink" to song.pdfLink.trim(),
            "orderIndex" to song.orderIndex,
            "isPinnedTheme" to song.isPinnedTheme,
            "favoriteUserIDs" to song.favoriteUserIds,
            "updatedAt" to serverTimestamp,
        )
        payload["audio"] = mainAudio?.let(::audioMap) ?: deleteField
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }

    fun lyricsPartMap(part: SongLyricsPart): Map<String, Any?> =
        linkedMapOf(
            "id" to part.id,
            "kind" to part.kind.wireValue,
            "number" to part.number.coerceAtLeast(1),
            "title" to part.title,
            "text" to part.text,
        )

    fun chordSheetMap(sheet: ChordSheet, rawDate: Date): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to sheet.id,
            "originalKey" to sheet.originalKey,
            "lines" to sheet.lines.map(::chordLineMap),
            "updatedAt" to (sheet.updatedAt ?: rawDate),
        )
        sheet.tempo?.let { map["tempo"] = it }
        sheet.timeSignature?.trim()?.takeUnless { it.isBlank() }?.let { map["timeSignature"] = it }
        sheet.capo?.let { map["capo"] = it }
        return map
    }

    fun chordLineMap(line: ChordLine): Map<String, Any?> =
        linkedMapOf(
            "id" to line.id,
            "text" to line.text,
            "isSectionHeader" to line.isSectionHeader,
            "chords" to line.chords.map(::chordMap),
        )

    fun chordMap(chord: Chord): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to chord.id,
            "chord" to chord.chord,
            "position" to chord.position,
            "lane" to chord.lane,
        )
        chord.freeX?.let { map["freeX"] = it }
        return map
    }

    fun audioMap(audio: SongAudio): Map<String, Any?> =
        linkedMapOf(
            "id" to audio.id,
            "fileName" to audio.fileName,
            "contentType" to audio.contentType,
            "storagePath" to audio.storagePath,
            "downloadURL" to audio.downloadUrl,
            "kind" to audio.kind.wireValue,
            "duration" to audio.duration,
            "fileSize" to audio.fileSize,
            "voiceType" to audio.trackType.wireValue,
            "displayName" to audio.displayName,
        )
}

package fr.ziyon.campzone.data.songbook

import fr.ziyon.campzone.data.model.ChordLine
import fr.ziyon.campzone.data.model.Song
import fr.ziyon.campzone.data.model.SongLyricsPartKind

data class SongPresentationSlide(
    val id: Int,
    val kind: SongPresentationSlideKind,
    val lines: List<String>,
)

sealed interface SongPresentationSlideKind {
    data class Title(val subtitle: String) : SongPresentationSlideKind
    data class Lyrics(val label: String?) : SongPresentationSlideKind
}

object SongPresentationDeckBuilder {
    fun deck(song: Song): List<SongPresentationSlide> {
        val lyricsSlides = lyricsSlides(song)
        if (lyricsSlides.isEmpty()) return emptyList()

        return buildList {
            add(
                SongPresentationSlide(
                    id = 0,
                    kind = SongPresentationSlideKind.Title(subtitle(song)),
                    lines = listOf(song.title),
                ),
            )
            lyricsSlides.forEachIndexed { index, slide ->
                add(
                    SongPresentationSlide(
                        id = index + 1,
                        kind = SongPresentationSlideKind.Lyrics(slide.label),
                        lines = slide.lines,
                    ),
                )
            }
        }
    }

    private fun lyricsSlides(song: Song): List<LyricsSlideDraft> {
        if (song.chordSheet.lines.isNotEmpty()) {
            return slidesFromChordLines(song.chordSheet.lines)
        }
        if (song.lyricsParts.isNotEmpty()) {
            return song.lyricsParts.mapNotNull { part ->
                val lines = part.text.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (lines.isEmpty()) return@mapNotNull null
                LyricsSlideDraft(label = part.displayTitle(), lines = lines)
            }
        }
        val fallbackLines = song.lyrics.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (fallbackLines.isEmpty()) emptyList() else listOf(LyricsSlideDraft(label = null, lines = fallbackLines))
    }

    private fun slidesFromChordLines(lines: List<ChordLine>): List<LyricsSlideDraft> {
        val slides = mutableListOf<LyricsSlideDraft>()
        var current: LyricsSlideDraft? = null

        fun flush() {
            current?.takeIf { it.lines.isNotEmpty() }?.let(slides::add)
            current = null
        }

        lines.forEach { line ->
            if (line.isSectionHeader) {
                flush()
                current = LyricsSlideDraft(label = cleanLabel(line.text), lines = emptyList())
                return@forEach
            }

            val text = line.text.trim()
            if (text.isEmpty()) {
                val slide = current
                if (slide != null && slide.lines.isNotEmpty() && slide.label == null) {
                    flush()
                }
                return@forEach
            }

            val next = current ?: LyricsSlideDraft(label = null, lines = emptyList())
            current = next.copy(lines = next.lines + text)
        }
        flush()

        return slides
    }

    private fun subtitle(song: Song): String = buildList {
        if (song.artist.isNotBlank()) add(song.artist)
        if (song.chordSheet.lines.isNotEmpty() && song.chordSheet.lines.any { it.chords.isNotEmpty() }) {
            add(song.chordSheet.originalKey.ifBlank { "C" })
        }
    }.joinToString("  ·  ")

    private fun cleanLabel(raw: String): String? {
        var label = raw.trim()
        if (
            (label.startsWith("[") && label.endsWith("]")) ||
            (label.startsWith("{") && label.endsWith("}"))
        ) {
            label = label.drop(1).dropLast(1).trim()
        }
        return label.takeIf { it.isNotEmpty() }
    }

    private data class LyricsSlideDraft(
        val label: String?,
        val lines: List<String>,
    )
}

private fun fr.ziyon.campzone.data.model.SongLyricsPart.displayTitle(): String? {
    val customTitle = title.trim()
    if (kind == SongLyricsPartKind.Custom && customTitle.isNotEmpty()) return customTitle

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

package fr.ziyon.campzone.data.songbook

import fr.ziyon.campzone.data.model.Chord
import fr.ziyon.campzone.data.model.ChordLine
import fr.ziyon.campzone.data.model.ChordSheet
import java.util.UUID

/**
 * Parses and serializes the cross-platform `chordedLyrics` format used by the
 * Cantus catalog:
 *
 * ```
 * {Verse 1}
 * A[G]mazing grace! how [G7]sweet the [C]sound
 * ```
 *
 * The same entry point still accepts the older chord-over-lyrics sheets so
 * pasted legacy charts and existing Firestore rows keep rendering.
 */
object ChordProParser {
    private val bracketRegex = Regex("""\[(.+?)]""")
    private val leadingSectionRegex = Regex("""^\s*\[([^\]]+)]""")
    private val sectionWords = setOf(
        "intro", "verse", "verso", "estrofe", "pre-chorus", "prechorus",
        "pre-refrain", "pre-refrão", "chorus", "refrain", "refrão", "coro",
        "bridge", "ponte", "instrumental", "outro", "final", "tag", "ending",
    )

    fun parse(text: String, existingId: String = UUID.randomUUID().toString()): ChordSheet {
        val lines = if (looksLikeChordPro(text)) {
            parseLines(text)
        } else {
            text.lines().flatMap(::parseLegacyLine)
        }
        return ChordSheet(
            id = existingId,
            originalKey = detectOriginalKey(lines),
            lines = lines,
        )
    }

    fun parseLines(text: String): List<ChordLine> =
        text.lines().map(::parseChordedLyricsLine)

    fun looksLikeChordPro(text: String): Boolean {
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("{") && line.endsWith("}") && line.length > 2) return true
            val parsed = parseChordedLyricsLine(raw)
            if (parsed.chords.isNotEmpty() && parsed.text.isNotBlank()) return true
        }
        return false
    }

    fun serialize(sheet: ChordSheet): String = serializeLines(sheet.lines)

    fun serializeLines(lines: List<ChordLine>): String =
        lines.joinToString("\n") { serializeLine(it) }

    /** Legacy text for older clients: chord rows above lyric rows. */
    fun legacyText(sheet: ChordSheet): String {
        if (sheet.lines.isEmpty()) return ""
        return buildString {
            sheet.lines.forEach { line ->
                if (line.isSectionHeader) {
                    appendLine(wrappedHeaderTitle(line.text))
                } else {
                    if (line.chords.isNotEmpty()) appendLine(chordLineFor(line))
                    appendLine(line.text)
                }
            }
        }.trimEnd()
    }

    fun renderedChordLine(line: ChordLine): String = chordLineFor(line)

    fun keySelectionFromApiKey(rawValue: String?): String? {
        if (rawValue.isNullOrBlank()) return null
        var value = rawValue.trim()
            .replace("♯", "#")
            .replace("♭", "b")
        val letter = value.firstOrNull()?.uppercaseChar() ?: return null
        if (letter !in 'A'..'G') return null
        value = value.drop(1)

        val accidental = when {
            value.startsWith("#") -> {
                value = value.drop(1)
                "#"
            }
            value.startsWith("b") -> {
                value = value.drop(1)
                "b"
            }
            else -> ""
        }

        val suffix = value.trim().lowercase()
        val minor = when (suffix) {
            "", "maj", "major" -> false
            "m", "min", "minor" -> true
            else -> return null
        }
        return "$letter$accidental${if (minor) "m" else ""}"
    }

    private fun parseChordedLyricsLine(raw: String): ChordLine {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ChordLine(id = UUID.randomUUID().toString())

        sectionTitle(trimmed)?.let { title ->
            return ChordLine(
                id = UUID.randomUUID().toString(),
                text = title,
                isSectionHeader = true,
            )
        }

        val output = StringBuilder()
        val chords = mutableListOf<Chord>()
        var cursor = 0
        bracketRegex.findAll(raw).forEach { match ->
            output.append(raw.substring(cursor, match.range.first))
            val token = match.groupValues[1].trim()
            val parsedChord = ChordSymbolParser.parseOrNull(token)
            if (parsedChord != null) {
                chords += Chord(
                    id = UUID.randomUUID().toString(),
                    chord = parsedChord.canonicalSymbol,
                    position = output.length,
                )
            } else {
                output.append(match.value)
            }
            cursor = match.range.last + 1
        }
        output.append(raw.substring(cursor))
        val text = output.toString()
        val maxPosition = text.length
        return ChordLine(
            id = UUID.randomUUID().toString(),
            text = text,
            chords = chords.map { it.copy(position = it.position.coerceIn(0, maxPosition)) },
        )
    }

    private fun parseLegacyLine(raw: String): List<ChordLine> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return listOf(ChordLine(id = UUID.randomUUID().toString()))

        val onlyBracket = bracketRegex.matchEntire(trimmed)
        if (onlyBracket != null) {
            val value = onlyBracket.groupValues[1].trim()
            if (isSectionHeader(value)) {
                return listOf(
                    ChordLine(
                        id = UUID.randomUUID().toString(),
                        text = "[$value]",
                        isSectionHeader = true,
                    ),
                )
            }
        }

        val leadingSection = leadingSectionRegex.find(raw)
        if (leadingSection != null && isSectionHeader(leadingSection.groupValues[1])) {
            val header = ChordLine(
                id = UUID.randomUUID().toString(),
                text = "[${leadingSection.groupValues[1].trim()}]",
                isSectionHeader = true,
            )
            val trailingStart = leadingSection.range.last + 1
            val trailingText = raw.substring(trailingStart)
            val trailingTokens = ChordSymbolParser.chordTokensIn(trailingText)
            if (trailingTokens.isEmpty()) return listOf(header)

            return listOf(
                header,
                ChordLine(
                    id = UUID.randomUUID().toString(),
                    text = "",
                    chords = trailingTokens.map { token ->
                        Chord(
                            id = UUID.randomUUID().toString(),
                            chord = token.symbol,
                            position = trailingStart + token.start,
                        )
                    },
                ),
            )
        }

        val inline = parseLegacyInlineChordPro(raw)
        if (inline != null) return inline

        if (looksLikeChordLine(trimmed)) {
            val chords = ChordSymbolParser.chordTokensIn(raw).map { token ->
                Chord(
                    id = UUID.randomUUID().toString(),
                    chord = token.symbol,
                    position = token.start,
                )
            }
            return listOf(ChordLine(id = UUID.randomUUID().toString(), chords = chords))
        }

        return listOf(ChordLine(id = UUID.randomUUID().toString(), text = raw))
    }

    private fun parseLegacyInlineChordPro(raw: String): List<ChordLine>? {
        val matches = bracketRegex.findAll(raw).toList()
        if (matches.isEmpty()) return null

        val output = StringBuilder()
        val chords = mutableListOf<Chord>()
        var cursor = 0
        matches.forEach { match ->
            output.append(raw.substring(cursor, match.range.first))
            val token = match.groupValues[1].trim()
            val parsedChord = ChordSymbolParser.parseOrNull(token)
            if (parsedChord != null) {
                chords += Chord(
                    id = UUID.randomUUID().toString(),
                    chord = parsedChord.canonicalSymbol,
                    position = output.length,
                )
            } else if (output.toString().isBlank() && isSectionHeader(token)) {
                output.append("[$token]")
            } else {
                output.append(match.value)
            }
            cursor = match.range.last + 1
        }
        output.append(raw.substring(cursor))

        val rendered = output.toString()
        if (chords.isEmpty() && !isSectionHeader(rendered.removeSurrounding("[", "]"))) return null

        if (chords.isEmpty()) {
            return listOf(
                ChordLine(
                    id = UUID.randomUUID().toString(),
                    text = rendered,
                    isSectionHeader = true,
                ),
            )
        }

        return listOf(
            ChordLine(
                id = UUID.randomUUID().toString(),
                text = rendered,
                chords = chords,
            ),
        )
    }

    private fun serializeLine(line: ChordLine): String {
        if (line.isSectionHeader) {
            val title = strippedHeaderTitle(line.text)
            return if (title.isBlank()) "" else "{$title}"
        }

        if (line.chords.isEmpty()) return line.text

        val sorted = line.chords.sortedWith(compareBy<Chord> { it.position }.thenBy { it.chord })
        val output = StringBuilder()
        var consumed = 0
        sorted.forEach { chord ->
            val position = chord.position.coerceIn(consumed, line.text.length)
            if (position > consumed) {
                output.append(line.text.substring(consumed, position))
                consumed = position
            }
            output.append("[${chord.chord.asciiChordSymbol()}]")
        }
        output.append(line.text.substring(consumed))
        return output.toString()
    }

    private fun chordLineFor(line: ChordLine): String {
        val sorted = line.chords.sortedWith(compareBy<Chord> { it.position }.thenBy { it.chord })
        val output = StringBuilder()
        var cursor = 0
        sorted.forEach { chord ->
            val target = chord.position.coerceAtLeast(cursor)
            if (target > cursor) {
                output.append(" ".repeat(target - cursor))
            } else if (output.isNotEmpty()) {
                output.append(' ')
            }
            output.append(chord.chord)
            cursor = output.length
        }
        return output.toString()
    }

    private fun looksLikeChordLine(text: String): Boolean {
        val semanticTokens = text.split(Regex("""\s+"""))
            .map { stripChartPunctuation(it) }
            .filter { it.isNotBlank() }
        if (semanticTokens.isEmpty()) return false

        val chordTokens = ChordSymbolParser.chordTokensIn(text)
        if (chordTokens.isEmpty()) return false

        val validChordTokens = semanticTokens.count { isChord(it) }
        val chordRatio = validChordTokens.toDouble() / semanticTokens.size
        val musicRatio = semanticTokens.sumOf(::countMusicCharacters).toDouble() / text.trim().length.coerceAtLeast(1)

        return chordRatio >= 0.65 || (chordRatio >= 0.45 && musicRatio >= 0.18)
    }

    private fun sectionTitle(trimmedLine: String): String? {
        if (trimmedLine.startsWith("{") && trimmedLine.endsWith("}") && trimmedLine.length >= 2) {
            return trimmedLine.drop(1).dropLast(1).trim().takeUnless { it.isBlank() }
        }

        if (trimmedLine.startsWith("[") &&
            trimmedLine.endsWith("]") &&
            trimmedLine.length >= 2 &&
            !trimmedLine.drop(1).contains("[")
        ) {
            val title = trimmedLine.drop(1).dropLast(1).trim()
            if (title.isNotBlank() && ChordSymbolParser.parseOrNull(title) == null) return title
        }
        return null
    }

    private fun isSectionHeader(text: String): Boolean {
        val normalized = text.trim()
            .lowercase()
            .replace(Regex("""\s+\d+$"""), "")
        return normalized in sectionWords
    }

    private fun isChord(text: String): Boolean =
        ChordSymbolParser.isChord(stripChartPunctuation(text))

    private fun stripChartPunctuation(token: String): String {
        var stripped = token.trim().trim { it in "|:[]{};," }
        if (stripped.length > 2 && stripped.first() == '(' && stripped.last() == ')') {
            val inner = stripped.substring(1, stripped.lastIndex)
            if (ChordSymbolParser.isChord(inner)) stripped = inner
        }
        return stripped
    }

    private fun countMusicCharacters(text: String): Int =
        text.count { char ->
            char.isDigit() || char in setOf('#', '♯', 'b', '♭', '/', '°', 'º', 'ø', 'Δ', '+', '-', '(', ')', ',')
        }

    private fun detectOriginalKey(lines: List<ChordLine>): String =
        lines.asSequence()
            .flatMap { it.chords.asSequence() }
            .map { it.chord.trim() }
            .firstOrNull { ChordSymbolParser.parseOrNull(it) != null }
            ?.let { chord ->
                val parsed = ChordSymbolParser.parseOrNull(chord) ?: return@let null
                parsed.root.flatName.takeIf { it.contains("b") } ?: parsed.root.sharpName
            }
            ?: "C"

    private fun strippedHeaderTitle(text: String): String {
        var title = text.trim()
        if ((title.startsWith("[") && title.endsWith("]")) ||
            (title.startsWith("{") && title.endsWith("}"))
        ) {
            title = title.drop(1).dropLast(1).trim()
        }
        return title
    }

    private fun wrappedHeaderTitle(text: String): String {
        val title = strippedHeaderTitle(text)
        return if (title.isBlank()) "" else "[$title]"
    }

    private fun String.asciiChordSymbol(): String =
        replace("♯", "#")
            .replace("♭", "b")
}

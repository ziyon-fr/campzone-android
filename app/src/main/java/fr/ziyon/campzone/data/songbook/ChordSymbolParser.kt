package fr.ziyon.campzone.data.songbook

import java.util.Locale

internal data class ParsedChordToken(
    val symbol: String,
    val start: Int,
)

internal data class ParsedChordSymbol(
    val root: PitchSymbol,
    val suffix: String,
    val bass: PitchSymbol?,
    val canonicalSymbol: String,
)

internal data class PitchSymbol(
    val index: Int,
    val sharpName: String,
    val flatName: String,
)

internal object ChordSymbolParser {
    private val tokenRegex = Regex(
        """[A-G][#b♯♭]?(?:maj(?:or)?|min(?:or)?|dim|aug|sus|add|dom|alt|omit|no|[A-GmMΔ∆ø°º+\-0-9#b♯♭(),/])*""",
    )

    private val pitchByName = mapOf(
        "C" to PitchSymbol(0, "C", "C"),
        "B#" to PitchSymbol(0, "C", "C"),
        "C#" to PitchSymbol(1, "C#", "Db"),
        "Db" to PitchSymbol(1, "C#", "Db"),
        "D" to PitchSymbol(2, "D", "D"),
        "D#" to PitchSymbol(3, "D#", "Eb"),
        "Eb" to PitchSymbol(3, "D#", "Eb"),
        "E" to PitchSymbol(4, "E", "E"),
        "Fb" to PitchSymbol(4, "E", "E"),
        "E#" to PitchSymbol(5, "F", "F"),
        "F" to PitchSymbol(5, "F", "F"),
        "F#" to PitchSymbol(6, "F#", "Gb"),
        "Gb" to PitchSymbol(6, "F#", "Gb"),
        "G" to PitchSymbol(7, "G", "G"),
        "G#" to PitchSymbol(8, "G#", "Ab"),
        "Ab" to PitchSymbol(8, "G#", "Ab"),
        "A" to PitchSymbol(9, "A", "A"),
        "A#" to PitchSymbol(10, "A#", "Bb"),
        "Bb" to PitchSymbol(10, "A#", "Bb"),
        "B" to PitchSymbol(11, "B", "B"),
        "Cb" to PitchSymbol(11, "B", "B"),
    )

    private val sharpPitches = pitchByName.values
        .distinctBy { it.index }
        .sortedBy { it.index }
        .associateBy { it.index }

    fun parseOrNull(input: String): ParsedChordSymbol? {
        val source = input.trim()
        if (source.isEmpty()) return null

        val compact = normalizedSymbol(source)
        if (compact.isEmpty() || isNoChord(compact)) return null

        val (body, bass) = splitSlashBass(compact) ?: return null
        val (root, suffix) = parsePitchPrefix(body) ?: return null
        if (!isSuffixValid(suffix)) return null

        return ParsedChordSymbol(
            root = root,
            suffix = suffix,
            bass = bass,
            canonicalSymbol = canonicalSymbol(source),
        )
    }

    fun isChord(input: String): Boolean = parseOrNull(input) != null

    fun chordTokensIn(line: String): List<ParsedChordToken> {
        val tokens = mutableListOf<ParsedChordToken>()
        tokenRegex.findAll(line).forEach { match ->
            if (!hasChordBoundaries(line, match.range)) return@forEach
            val symbol = match.value
            val parsed = parseOrNull(symbol) ?: return@forEach
            tokens += ParsedChordToken(
                symbol = parsed.canonicalSymbol,
                start = match.range.first,
            )
        }
        return tokens
    }

    fun transpose(symbol: String, semitones: Int, originalKey: String): String {
        if (semitones == 0) return symbol
        val parsed = parseOrNull(symbol) ?: return symbol
        val preferFlats = prefersFlats(originalKey)
        val root = transposePitch(parsed.root, semitones, preferFlats)
        val bass = parsed.bass?.let { transposePitch(it, semitones, preferFlats) }
        return buildString {
            append(root)
            append(parsed.suffix)
            if (bass != null) append('/').append(bass)
        }
    }

    private fun normalizedSymbol(input: String): String =
        input
            .trim()
            .replace("♯", "#")
            .replace("♭", "b")
            .replace("𝄪", "##")
            .replace("𝄫", "bb")
            .replace("−", "-")
            .replace("–", "-")
            .replace("∆", "Δ")
            .filterNot { it.isWhitespace() }

    private fun canonicalSymbol(input: String): String =
        input.trim().filterNot { it.isWhitespace() }

    private fun isNoChord(input: String): Boolean {
        val lowered = input.lowercase(Locale.US)
        return lowered == "n.c." || lowered == "n.c" || lowered == "nc" || lowered == "no-chord"
    }

    private fun splitSlashBass(input: String): Pair<String, PitchSymbol?>? {
        val slash = input.lastIndexOf('/')
        if (slash == -1) return input to null
        val bassText = input.substring(slash + 1)
        if (bassText.isEmpty()) return null
        if (!startsWithPitchLetter(bassText)) return input to null
        if (input.substring(0, slash).contains('/')) return null

        val bass = parsePitchExact(bassText) ?: return null
        val body = input.substring(0, slash)
        if (body.isEmpty()) return null
        return body to bass
    }

    private fun startsWithPitchLetter(input: String): Boolean =
        input.firstOrNull()?.uppercaseChar() in 'A'..'G'

    private fun parsePitchExact(input: String): PitchSymbol? {
        val (pitch, remainder) = parsePitchPrefix(input) ?: return null
        return pitch.takeIf { remainder.isEmpty() }
    }

    private fun parsePitchPrefix(input: String): Pair<PitchSymbol, String>? {
        val first = input.firstOrNull() ?: return null
        val letter = first.uppercaseChar()
        if (letter !in 'A'..'G') return null

        var index = 1
        val accidental = if (index < input.length && (input[index] == '#' || input[index] == 'b')) {
            input[index++].toString()
        } else {
            ""
        }
        if (index < input.length && (input[index] == '#' || input[index] == 'b')) return null

        val pitch = pitchByName["$letter$accidental"] ?: return null
        return pitch to input.substring(index)
    }

    private fun isSuffixValid(suffix: String): Boolean {
        val scanner = SymbolScanner(suffix)
        if (scanner.isAtEnd) return true

        while (!scanner.isAtEnd) {
            when {
                consumeParenthesizedBlock(scanner) -> Unit
                consumeKnownQuality(scanner) -> Unit
                consumeSuspension(scanner) -> Unit
                consumeAdd(scanner) -> Unit
                consumeNoOrOmit(scanner) -> Unit
                consumeAlt(scanner) -> Unit
                consumeAlteration(scanner) -> Unit
                consumeBareExtension(scanner) -> Unit
                scanner.consume("/") -> Unit
                else -> return false
            }
        }
        return true
    }

    private fun consumeParenthesizedBlock(scanner: SymbolScanner): Boolean {
        if (!scanner.consume("(")) return false
        val content = scanner.readUntil(')') ?: return false
        return content.split(',')
            .map { it.trim() }
            .all { it.isNotEmpty() && isSuffixValid(it) }
    }

    private fun consumeKnownQuality(scanner: SymbolScanner): Boolean {
        val wordTokens = listOf(
            "minormaj7", "minorMaj7", "major13", "major11", "major9", "major7",
            "maj13", "maj11", "maj9", "maj7", "major", "maj",
            "minor7", "min7", "minor", "min",
            "dim7", "dim", "aug7", "aug", "dom",
        )
        for (token in wordTokens) {
            if (scanner.consumeCaseInsensitive(token)) return true
        }

        val exactTokens = listOf(
            "mMaj7", "mM7", "mΔ7", "m7b5",
            "M13", "M11", "M9", "M7",
            "Δ13", "Δ11", "Δ9", "Δ7",
            "+7", "ø7", "°7", "º7", "o7",
            "M", "Δ", "+", "ø", "°", "º", "o",
            "m7", "m", "-7", "-",
        )
        for (token in exactTokens) {
            if (scanner.consume(token)) return true
        }
        return false
    }

    private fun consumeSuspension(scanner: SymbolScanner): Boolean {
        if (!scanner.consumeCaseInsensitive("sus")) return false
        scanner.consume("2") || scanner.consume("4")
        return true
    }

    private fun consumeAdd(scanner: SymbolScanner): Boolean {
        if (!scanner.consumeCaseInsensitive("add")) return false
        return scanner.consumeNumber()
    }

    private fun consumeNoOrOmit(scanner: SymbolScanner): Boolean {
        val consumed = scanner.consumeCaseInsensitive("no") || scanner.consumeCaseInsensitive("omit")
        return consumed && scanner.consumeNumber()
    }

    private fun consumeAlt(scanner: SymbolScanner): Boolean =
        scanner.consumeCaseInsensitive("alt")

    private fun consumeAlteration(scanner: SymbolScanner): Boolean {
        val mark = scanner.peek()
        if (mark == '#' || mark == 'b' || mark == '+' || mark == '-') {
            scanner.advance()
            return scanner.consumeNumber()
        }
        return false
    }

    private fun consumeBareExtension(scanner: SymbolScanner): Boolean {
        if (!scanner.consumeNumber()) return false
        scanner.consume("+") || scanner.consume("-")
        return true
    }

    private fun hasChordBoundaries(line: String, range: IntRange): Boolean {
        val before = range.first - 1
        val after = range.last + 1
        if (before >= 0 && line[before].isLetterOrDigit()) return false
        if (after < line.length && line[after].isLetterOrDigit()) return false
        return true
    }

    private fun prefersFlats(originalKey: String): Boolean {
        val normalized = originalKey.replace("♭", "b")
        return normalized.contains("b", ignoreCase = true) ||
            normalized in setOf("F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb")
    }

    private fun transposePitch(pitch: PitchSymbol, semitones: Int, preferFlats: Boolean): String {
        val next = (pitch.index + semitones).floorMod(12)
        val transposed = sharpPitches[next] ?: return if (preferFlats) pitch.flatName else pitch.sharpName
        return if (preferFlats) transposed.flatName else transposed.sharpName
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
}

private class SymbolScanner(private val text: String) {
    private var index = 0
    val isAtEnd: Boolean get() = index >= text.length
    fun peek(): Char? = text.getOrNull(index)
    fun advance() {
        if (!isAtEnd) index += 1
    }

    fun consume(value: String): Boolean {
        if (!text.startsWith(value, index)) return false
        index += value.length
        return true
    }

    fun consumeCaseInsensitive(value: String): Boolean {
        if (index + value.length > text.length) return false
        if (!text.regionMatches(index, value, 0, value.length, ignoreCase = true)) return false
        index += value.length
        return true
    }

    fun consumeNumber(): Boolean {
        val start = index
        while (index < text.length && text[index].isDigit()) {
            index += 1
        }
        return index > start
    }

    fun readUntil(terminator: Char): String? {
        val start = index
        while (index < text.length && text[index] != terminator) {
            index += 1
        }
        if (index >= text.length || text[index] != terminator) return null
        val content = text.substring(start, index)
        index += 1
        return content
    }
}

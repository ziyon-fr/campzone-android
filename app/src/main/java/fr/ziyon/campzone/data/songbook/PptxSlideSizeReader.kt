package fr.ziyon.campzone.data.songbook

import java.io.File
import java.util.zip.ZipInputStream

data class PptxSlideSize(
    val widthPoints: Double,
    val heightPoints: Double,
)

object PptxSlideSizeReader {
    private const val EMUS_PER_POINT = 12_700.0
    private val SIDE_BOUNDS = 72.0..4_500.0
    private val slideSizeTagRegex = Regex("""<[a-zA-Z0-9]*:?sldSz\b[^>]*>""")

    fun slideSize(file: File): PptxSlideSize? {
        val xml = presentationXml(file) ?: return null
        return slideSizeFromPresentationXml(xml)
    }

    fun slideSizeFromPresentationXml(xml: String): PptxSlideSize? {
        val tag = slideSizeTagRegex.find(xml)?.value ?: return null
        val cx = emuAttribute("cx", tag) ?: return null
        val cy = emuAttribute("cy", tag) ?: return null
        val width = cx / EMUS_PER_POINT
        val height = cy / EMUS_PER_POINT
        if (width !in SIDE_BOUNDS || height !in SIDE_BOUNDS) return null
        return PptxSlideSize(widthPoints = width, heightPoints = height)
    }

    private fun presentationXml(file: File): String? {
        if (!file.exists() || file.length() <= 0L) return null
        ZipInputStream(file.inputStream().buffered()).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: return null
                if (entry.name == "ppt/presentation.xml") {
                    return archive.readBytes().toString(Charsets.UTF_8)
                }
                archive.closeEntry()
            }
        }
    }

    private fun emuAttribute(name: String, tag: String): Double? {
        val range = Regex("$name=\"([0-9]+)\"").find(tag) ?: return null
        return range.groupValues[1].toDoubleOrNull()
    }
}

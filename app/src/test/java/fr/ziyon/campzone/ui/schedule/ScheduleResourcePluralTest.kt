package fr.ziyon.campzone.ui.schedule

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleResourcePluralTest {

    @Test
    fun scheduleOverviewUsesDayAndProgramPluralsInEveryLocale() {
        listOf("values", "values-fr", "values-pt", "values-pt-rBR").forEach { dir ->
            val text = stringsFile(dir).readText()

            assertTrue("$dir is missing schedule_day_count", text.contains("""<plurals name="schedule_day_count">"""))
            assertTrue("$dir is missing schedule_program_count", text.contains("""<plurals name="schedule_program_count">"""))
            assertTrue(
                "$dir overview must accept pre-pluralized strings",
                text.contains("""<string name="schedule_overview_summary">%1${'$'}s · %2${'$'}s</string>"""),
            )
            assertFalse("$dir overview must not hard-code day singular", text.contains("schedule_overview_summary\">%1${'$'}d day"))
            assertFalse("$dir overview must not hard-code program singular", text.contains("schedule_overview_summary\">%1${'$'}d dia"))
            assertFalse("$dir overview must not hard-code French singular", text.contains("schedule_overview_summary\">%1${'$'}d jour"))
        }
    }

    private fun stringsFile(dir: String): File {
        val candidates = listOf(
            File("src/main/res/$dir/strings.xml"),
            File("app/src/main/res/$dir/strings.xml"),
        )
        return candidates.firstOrNull { it.exists() } ?: error("Missing strings.xml for $dir")
    }
}

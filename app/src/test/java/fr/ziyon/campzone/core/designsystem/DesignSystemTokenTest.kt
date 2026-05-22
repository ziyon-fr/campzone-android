package fr.ziyon.campzone.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignSystemTokenTest {
    @Test
    fun colorTokensMatchDesignSpec() {
        assertEquals(Color(0xFFF8F4EE), CzColors.BackgroundLight)
        assertEquals(Color(0xFF070E1A), CzColors.BackgroundDark)
        assertEquals(Color(0xFFFF6B35), CzColors.PrimaryLight)
        assertEquals(Color(0xFFFF7A47), CzColors.PrimaryDark)
        assertEquals(Color(0xFF3A6248), CzColors.SecondaryLight)
        assertEquals(Color(0xFF4A7C59), CzColors.SecondaryDark)
        assertEquals(Color(0xFFD97706), CzColors.AccentLight)
        assertEquals(Color(0xFFFFB347), CzColors.AccentDark)
        assertEquals(Color(0xFF1C1917), CzColors.TextPrimaryLight)
        assertEquals(Color(0xFFFFF4E0), CzColors.TextPrimaryDark)
        assertEquals(Color(0xFF6B6052), CzColors.TextSecondaryLight)
        assertEquals(Color(0xFFC4A875), CzColors.TextSecondaryDark)
        assertEquals(Color(0xFF16A34A), CzColors.SuccessLight)
        assertEquals(Color(0xFF66BB6A), CzColors.SuccessDark)
        assertEquals(Color(0xFFDC2626), CzColors.ErrorLight)
        assertEquals(Color(0xFFFF6B6B), CzColors.ErrorDark)
    }

    @Test
    fun layoutTokensMatchDesignSpec() {
        assertEquals(4f, CzSpacing.xs.value)
        assertEquals(8f, CzSpacing.sm.value)
        assertEquals(12f, CzSpacing.md.value)
        assertEquals(16f, CzSpacing.base.value)
        assertEquals(20f, CzSpacing.lg.value)
        assertEquals(24f, CzSpacing.xl.value)
        assertEquals(32f, CzSpacing.xxl.value)
        assertEquals(48f, CzSpacing.xxxl.value)

        assertEquals(4f, CzRadius.xs.value)
        assertEquals(8f, CzRadius.sm.value)
        assertEquals(12f, CzRadius.md.value)
        assertEquals(16f, CzRadius.lg.value)
        assertEquals(20f, CzRadius.xl.value)
        assertEquals(24f, CzRadius.xxl.value)
        assertEquals(999f, CzRadius.full.value)
    }

    @Test
    fun typographyTokensMatchDesignSpec() {
        assertEquals(34f, CzTypeScale.largeTitle.fontSize.value)
        assertEquals(FontWeight.Bold, CzTypeScale.largeTitle.fontWeight)
        assertEquals(28f, CzTypeScale.title.fontSize.value)
        assertEquals(FontWeight.Bold, CzTypeScale.title.fontWeight)
        assertEquals(22f, CzTypeScale.title2.fontSize.value)
        assertEquals(FontWeight.SemiBold, CzTypeScale.title2.fontWeight)
        assertEquals(20f, CzTypeScale.title3.fontSize.value)
        assertEquals(FontWeight.SemiBold, CzTypeScale.title3.fontWeight)
        assertEquals(17f, CzTypeScale.headline.fontSize.value)
        assertEquals(FontWeight.SemiBold, CzTypeScale.headline.fontWeight)
        assertEquals(17f, CzTypeScale.body.fontSize.value)
        assertEquals(FontWeight.Normal, CzTypeScale.body.fontWeight)
        assertEquals(16f, CzTypeScale.callout.fontSize.value)
        assertEquals(FontWeight.Normal, CzTypeScale.callout.fontWeight)
        assertEquals(15f, CzTypeScale.subhead.fontSize.value)
        assertEquals(FontWeight.Medium, CzTypeScale.subhead.fontWeight)
        assertEquals(12f, CzTypeScale.caption.fontSize.value)
        assertEquals(FontWeight.Normal, CzTypeScale.caption.fontWeight)
        assertEquals(11f, CzTypeScale.caption2.fontSize.value)
        assertEquals(FontWeight.Medium, CzTypeScale.caption2.fontWeight)
    }
}

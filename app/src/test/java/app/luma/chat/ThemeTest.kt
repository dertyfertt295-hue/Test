package app.luma.chat

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLumaApplication::class)
class ThemeTest {
    @Test fun newAccentsAreDistinctReadableAndBlackOnlyAffectsDarkMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accents = listOf("lavender", "mint", "peach", "blue", "rose", "amber", "teal", "graphite")
        assertEquals(8, accents.map { accentColor(it) }.toSet().size)
        for (accent in accents) for (dark in listOf(false, true)) {
            val colors = lumaColorScheme(context, accent, dark)
            fun contrast(a: Color, b: Color): Float = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
            assertTrue(contrast(colors.primary, colors.onPrimary) >= 4.5f)
            assertTrue(contrast(colors.surface, colors.onSurface) >= 4.5f)
            val black = lumaColorScheme(context, accent, dark, true)
            assertEquals(if (dark) Color.Black else colors.surface, black.surface)
            assertEquals(colors.primary, black.primary)
        }
    }
}

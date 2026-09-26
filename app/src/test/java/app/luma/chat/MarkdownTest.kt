package app.luma.chat

import android.app.Application
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import io.noties.markwon.core.spans.StrongEmphasisSpan
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLumaApplication::class)
class MarkdownTest {
    private fun render(source: String) = markdownRenderer(ApplicationProvider.getApplicationContext<Context>()).toMarkdown(source)
    @Test fun tablesKeepFormattingAlignmentEscapesAndClipboardContents() {
        val renderer = markdownRenderer(ApplicationProvider.getApplicationContext<Context>())
        val source = "Вступление\n\n| Модель | Описание |\n| :--- | ---: |\n| **A-4.5** | Быстрая \\| точная |\n| S-4.5 | `код` |\n\nПосле таблицы"
        val parts = markdownParts(renderer, source)
        assertEquals(3, parts.size)
        val table = parts[1] as MarkdownPart.Grid
        assertTrue(table.rows.first().header)
        assertEquals(listOf("Модель", "Описание"), table.rows.first().cells.map { it.content.toString() })
        assertEquals(3, table.rows.size)
        assertEquals("Быстрая | точная", table.rows[1].cells[1].content.toString())
        assertEquals(org.commonmark.ext.gfm.tables.TableCell.Alignment.RIGHT, table.rows[1].cells[1].alignment)
        val boldCell = table.rows[1].cells[0].content
        assertEquals(1, boldCell.getSpans(0, boldCell.length, StrongEmphasisSpan::class.java).size)
        val copied = renderer.toMarkdown(source).toString()
        assertTrue(copied.contains("A-4.5\tБыстрая | точная"))
        assertTrue(copied.contains("После таблицы"))
    }
    @Test fun fencedTableExamplesStayCodeAndIncompleteTablesStayReadable() {
        val renderer = markdownRenderer(ApplicationProvider.getApplicationContext<Context>())
        val parts = markdownParts(renderer, "```text\n| A | B |\n| --- | --- |\n| 1 | 2 |\n```")
        assertTrue(parts.none { it is MarkdownPart.Grid })
        assertTrue((parts.single() as MarkdownPart.Text).content.toString().contains("| 1 | 2 |"))
        assertTrue(markdownParts(renderer, "| A | B |").isNotEmpty())
    }
    @Test fun boldJokeTitleIsStyledAndDialogueBreaksArePreserved() {
        val rendered = render("**Про математика:**\n— Сколько будет 2 + 2?\n— 4.")
        assertEquals("Про математика:\n— Сколько будет 2 + 2?\n— 4.", rendered.toString())
        val bold = rendered.getSpans(0, rendered.length, StrongEmphasisSpan::class.java).single()
        assertEquals("Про математика:", rendered.subSequence(rendered.getSpanStart(bold), rendered.getSpanEnd(bold)).toString())
    }
    @Test fun formattingAndCodeAreDifferent() {
        val rendered = render("## Заголовок\n\n*Курсив* и **жирный**\n\n```text\n**это код**\n```\n\n~~Удалено~~")
        assertFalse(rendered.toString().contains("##"))
        assertFalse(rendered.toString().contains("*Курсив*"))
        assertFalse(rendered.toString().contains("~~"))
        assertTrue(rendered.toString().contains("**это код**"))
    }
    @Test fun linksInSelectableAnswersOpenWhenTapped() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = TextView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            selectableWithLinks()
        }
        markdownRenderer(context).setMarkdown(view, "[Источник](https://developer.android.com)")
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertTrue(view.isTextSelectable)
        val x = view.layout.getPrimaryHorizontal(3)
        val y = view.layout.getLineBaseline(0) - 2f
        view.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0))
        view.onTouchEvent(MotionEvent.obtain(0, 60, MotionEvent.ACTION_UP, x, y, 0))
        assertEquals("https://developer.android.com", shadowOf(context).nextStartedActivity?.dataString)
    }
    @Test fun listsLinksAndEscapedSymbolsKeepTheirContent() {
        val rendered = render("1. Первый\n2. Второй\n\n[Сайт](https://example.com) и \\*звёздочка\\*")
        assertTrue(rendered.toString().contains("Первый"))
        assertTrue(rendered.toString().contains("Второй"))
        assertTrue(rendered.toString().contains("Сайт и *звёздочка*"))
        assertFalse(rendered.toString().contains("](https"))
    }
}

package app.luma.chat

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.TableLayout
import android.widget.TableRow as AndroidTableRow
import android.net.Uri
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Node
import org.commonmark.node.Document
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.*

internal fun Node.childNodes(): List<Node> = buildList {
    var child = firstChild
    while (child != null) { add(child); child = child.next }
}
internal fun TableBlock.tableRows(): List<Node> = childNodes().flatMap {
    if (it is TableBody || it is TableHead) it.childNodes() else listOf(it)
}

internal sealed interface MarkdownPart {
    data class Text(val content: Spanned) : MarkdownPart
    data class Cell(val content: Spanned, val alignment: TableCell.Alignment?)
    data class Row(val header: Boolean, val cells: List<Cell>)
    data class Grid(val rows: List<Row>) : MarkdownPart
}

internal fun markdownParts(renderer: Markwon, markdown: String): List<MarkdownPart> = buildList {
    val document = renderer.parse(markdown)
    var text = Document()
    fun flush() {
        if (text.firstChild != null) add(MarkdownPart.Text(renderer.render(text)))
        text = Document()
    }
    for (node in document.childNodes()) {
        if (node is TableBlock) {
            flush()
            add(MarkdownPart.Grid(node.tableRows().map { row ->
                MarkdownPart.Row(row.parent is TableHead, row.childNodes().filterIsInstance<TableCell>().map { cell ->
                    MarkdownPart.Cell(renderer.render(cell), cell.alignment)
                })
            }))
        } else { node.unlink(); text.appendChild(node) }
    }
    flush()
}

internal fun markdownRenderer(context: Context, accent: Int = 0xFF5F578E.toInt(), surface: Int = 0xFFE9E7EF.toInt(), foreground: Int = 0xFF1C1B20.toInt()): Markwon {
    val density = context.resources.displayMetrics.density
    fun dp(value: Float) = (density * value).toInt()
    return Markwon.builder(context)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureParser(builder: Parser.Builder) {
                builder.extensions(listOf(TablesExtension.create()))
            }
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.linkColor(accent).codeTextColor(foreground).codeBlockTextColor(foreground)
                    .codeBackgroundColor(surface).codeBlockBackgroundColor(surface)
                    .blockQuoteColor(accent).headingBreakHeight(0)
                    // Material 3 leans on spacing rather than rules to separate blocks.
                    .blockMargin(dp(14f)).blockQuoteWidth(dp(3f))
                    .bulletWidth(dp(5f)).listItemColor(accent)
                    .codeBlockMargin(dp(10f)).thematicBreakColor(accent).thematicBreakHeight(dp(1f))
                    .headingTextSizeMultipliers(floatArrayOf(1.4f, 1.25f, 1.12f, 1.04f, 1f, 1f))
            }
            override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                // Keep dialogue, poems and deliberate line breaks readable, as in the source answer.
                builder.on(SoftLineBreak::class.java) { visitor, _ -> visitor.forceNewLine() }
                builder.on(TableCell::class.java) { visitor, cell -> visitor.visitChildren(cell) }
                // Plain-text rendering keeps all cells for Copy and for nested tables.
                builder.on(TableBlock::class.java) { visitor, table ->
                    visitor.blockStart(table)
                    table.tableRows().forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) visitor.forceNewLine()
                        row.childNodes().forEachIndexed { columnIndex, cell ->
                            if (columnIndex > 0) visitor.builder().append("\t")
                            visitor.visitChildren(cell)
                        }
                    }
                    visitor.blockEnd(table)
                }
            }
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, destination ->
                    val uri = Uri.parse(destination)
                    if (uri.scheme?.lowercase() in setOf("https", "http")) {
                        try { view.context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (_: android.content.ActivityNotFoundException) { Toast.makeText(view.context, R.string.error_link_open, Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        }).build()
}

/**
 * Selectable text that still opens links. Making a TextView selectable installs
 * a movement method that ignores links, and Markwon only adds its own when there
 * is none — so without this no link in an answer, O5's sources included, can be
 * tapped. Long-press selection keeps working alongside it.
 */
internal fun TextView.selectableWithLinks() {
    setTextIsSelectable(true)
    movementMethod = LinkMovementMethod.getInstance()
}

@Composable fun MarkdownBody(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary.toArgb()
    val background = colors.surfaceContainerHighest.toArgb()
    val textColor = colors.onSurface.toArgb()
    val renderer = remember(context, accent, background, textColor) { markdownRenderer(context, accent, background, textColor) }
    val parts = remember(renderer, markdown) { markdownParts(renderer, markdown) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        parts.forEach { part ->
            when (part) {
                is MarkdownPart.Text -> MarkdownText(part.content, renderer)
                is MarkdownPart.Grid -> MarkdownTable(part, renderer)
            }
        }
    }
}

@Composable private fun MarkdownText(rendered: Spanned, renderer: Markwon, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    val textPx = with(density) { style.fontSize.toPx() }
    val linePx = with(density) { style.lineHeight.toPx() }
    val textColor = colors.onSurface.toArgb()
    val accent = colors.primary.toArgb()
    val background = colors.surfaceContainerHighest.toArgb()
    AndroidView(modifier = modifier.fillMaxWidth(), factory = { ctx ->
        TextView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            includeFontPadding = false
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(0, 0, 0, 0)
            selectableWithLinks()
        }
    }, update = { view ->
        view.setTextColor(textColor)
        view.setLinkTextColor(accent)
        view.highlightColor = (accent and 0x00FFFFFF) or 0x55000000
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        view.setLineSpacing((linePx - (view.paint.fontMetrics.descent - view.paint.fontMetrics.ascent)).coerceAtLeast(0f), 1f)
        if (view.tag !== rendered) {
            renderer.setParsedMarkdown(view, rendered)
            view.tag = rendered
        }
    })
}

@Composable private fun MarkdownTable(table: MarkdownPart.Grid, renderer: Markwon) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodyLarge
    val textSize = with(density) { style.fontSize.toPx() }
    val padding = with(density) { 12.dp.roundToPx() }
    val border = colors.outlineVariant.toArgb()
    val textColor = colors.onSurface.toArgb()
    val accent = colors.primary.toArgb()
    val headerColor = colors.surfaceContainerHighest.toArgb()
    val rowColor = colors.surfaceContainerLow.toArgb()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = table.rows.maxOfOrNull { it.cells.size }?.coerceAtLeast(1) ?: 1
        val cellWidth = with(density) { (maxWidth / columns).coerceAtLeast(136.dp * fontScale).roundToPx() }
        AndroidView(modifier = Modifier.fillMaxWidth().clipToBounds(), factory = { ctx ->
            HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = true
                isFillViewport = true
                addView(TableLayout(ctx))
            }
        }, update = { scroll ->
            val layout = scroll.getChildAt(0) as TableLayout
            layout.removeAllViews()
            table.rows.forEach { row ->
                val rowView = AndroidTableRow(scroll.context)
                row.cells.forEach { cell ->
                    val cellView = TextView(scroll.context).apply {
                        layoutParams = AndroidTableRow.LayoutParams(cellWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                        minHeight = padding * 4
                        setPadding(padding, padding, padding, padding)
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                        setTextColor(textColor); setLinkTextColor(accent)
                        typeface = Typeface.create("sans-serif", if (row.header) Typeface.BOLD else Typeface.NORMAL)
                        gravity = Gravity.TOP or when (cell.alignment) {
                            TableCell.Alignment.CENTER -> Gravity.CENTER_HORIZONTAL
                            TableCell.Alignment.RIGHT -> Gravity.RIGHT
                            else -> Gravity.LEFT
                        }
                        background = GradientDrawable().apply {
                            setColor(if (row.header) headerColor else rowColor)
                            setStroke(1, border)
                        }
                        selectableWithLinks()
                    }
                    renderer.setParsedMarkdown(cellView, cell.content)
                    rowView.addView(cellView)
                }
                layout.addView(rowView)
            }
        })
    }
}

package app.luma.chat

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.*
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import kotlin.math.abs
import kotlin.math.min

/** Real text glyphs sampled from our Material expressive polygons, cached per size. */
@Composable internal fun AsciiShapeField(modifier: Modifier = Modifier, tint: Color, warmTint: Color) {
    val flight = if (motionEnabled()) rememberInfiniteTransition(label = "asciiFlight").animateFloat(
        initialValue = 0f, targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "flightPhase") else null
    Box(modifier.clearAndSetSemantics { }.drawWithCache {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 8.dp.toPx()
            textAlign = Paint.Align.CENTER
        }
        val cellWidth = 5.dp.toPx()
        val cellHeight = 8.dp.toPx()
        val diameter = min(size.width * .62f, 260.dp.toPx())
        data class Glyph(val text: String, val x: Float, val y: Float, val color: Int, val group: Int)
        val glyphs = mutableListOf<Glyph>()
        val centers = listOf(.16f to .12f, .88f to .23f, .10f to .82f, .82f to .96f)
        val shapes = listOf(LumaShapes.Flower, LumaShapes.Clover4Leaf, LumaShapes.Cookie9Sided, LumaShapes.SoftBurst)
        centers.forEachIndexed { index, (cx, cy) ->
            val path = shapes[index].toPath()
            val bounds = RectF().also { path.computeBounds(it, true) }
            val matrix = Matrix().apply {
                setRectToRect(bounds, RectF(0f, 0f, diameter, diameter), Matrix.ScaleToFit.CENTER)
            }
            path.transform(matrix)
            val region = Region().apply { setPath(path, Region(0, 0, diameter.toInt() + 1, diameter.toInt() + 1)) }
            val rows = (diameter / cellHeight).toInt()
            val cols = (diameter / cellWidth).toInt()
            for (row in 0..rows) for (col in 0..cols) {
                val px = col * cellWidth
                val py = row * cellHeight
                if (!region.contains(px.toInt(), py.toInt())) continue
                val edge = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).any { (dx, dy) ->
                    !region.contains((px + dx * cellWidth).toInt(), (py + dy * cellHeight).toInt())
                }
                val x = cx * size.width - diameter / 2 + px
                val y = cy * size.height - diameter / 2 + py
                // Leave a quiet area behind the sign-in content at every screen size.
                val distance = abs(y / size.height - .52f)
                val quiet = ((distance - .19f) / .12f).coerceIn(.08f, 1f)
                val color = (if (index % 2 == 0) tint else warmTint).copy(alpha = (if (edge) .65f else .32f) * quiet)
                val char = if (edge) "+" else if ((row + col) % 5 == 0) "*" else "."
                glyphs += Glyph(char, x, y, color.toArgb(), index)
            }
        }
        onDrawBehind {
            val phase = flight?.value ?: 0f
            val canvas = drawContext.canvas.nativeCanvas
            centers.forEachIndexed { index, (cx, cy) ->
                canvas.save()
                if (flight != null) {
                    canvas.translate(sin(phase + index * 1.7f) * 22.dp.toPx(), cos(phase + index * 1.3f) * 30.dp.toPx())
                    canvas.rotate(sin(phase + index) * 12f, cx * size.width, cy * size.height)
                }
                glyphs.forEach { glyph -> if (glyph.group == index) {
                    paint.color = glyph.color
                    canvas.drawText(glyph.text, glyph.x, glyph.y, paint)
                } }
                canvas.restore()
            }
        }
    })
}

package app.luma.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.max
import kotlin.math.min

/**
 * The Material 3 expressive shape family.
 *
 * Material ships these as `MaterialShapes`, but that landed in material3 1.5,
 * which needs a newer Android Gradle plugin than this project runs. They are
 * rebuilt here on `androidx.graphics:graphics-shapes` — the same library the
 * official set is defined with, using the same star/polygon construction — so
 * they morph into one another exactly the same way.
 */
object LumaShapes {
    val Circle: RoundedPolygon = RoundedPolygon.circle(numVertices = 12)

    /** Scalloped disc — the "cookie" of the family. */
    val Cookie9Sided: RoundedPolygon =
        RoundedPolygon.star(9, innerRadius = .82f, rounding = CornerRounding(.45f))

    val SoftBurst: RoundedPolygon =
        RoundedPolygon.star(10, innerRadius = .72f, rounding = CornerRounding(.30f), innerRounding = CornerRounding(.22f))

    val Sunny: RoundedPolygon =
        RoundedPolygon.star(8, innerRadius = .78f, rounding = CornerRounding(.22f))

    val Flower: RoundedPolygon =
        RoundedPolygon.star(8, innerRadius = .58f, rounding = CornerRounding(.52f), innerRounding = CornerRounding(.12f))

    val PuffyDiamond: RoundedPolygon =
        RoundedPolygon.star(4, innerRadius = .76f, rounding = CornerRounding(.42f))

    val Clover4Leaf: RoundedPolygon =
        RoundedPolygon.star(4, innerRadius = .42f, rounding = CornerRounding(.72f), innerRounding = CornerRounding(0f))

    val Pentagon: RoundedPolygon = RoundedPolygon(numVertices = 5, rounding = CornerRounding(.35f))

    /**
     * The order Luma morphs through. Neighbours are kept close in point count —
     * 9, 10, 8, 8, 4, 4 — because a morph matches features between the two
     * shapes, and a big jump in count is what makes an in-between look lopsided.
     */
    val cycle: List<RoundedPolygon> = listOf(Cookie9Sided, SoftBurst, Sunny, Flower, PuffyDiamond, Clover4Leaf)
}

private fun Rect.union(other: Rect) = Rect(
    min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom)
)

/**
 * Fits a path into the space it is given, from a *fixed* source rectangle.
 *
 * The source is measured once up front rather than per frame. Measuring each
 * frame would re-centre a morph continuously, which reads as the shape drifting
 * and breathing; holding one reference box keeps it planted and evenly centred.
 */
private fun Path.fitInto(size: Size, source: Rect): Path = apply {
    // On the first measure pass a container can still report a zero size. Left
    // alone, the transform collapses the shape onto the origin and it visibly
    // springs out of the top-left corner on the next frame.
    if (size.width <= 0f || size.height <= 0f) { reset(); return@apply }
    if (source.width <= 0f || source.height <= 0f) return@apply
    val scale = min(size.width / source.width, size.height / source.height)
    val matrix = Matrix()
    // Applied to a point right to left: move to the origin, scale, then centre.
    matrix.translate((size.width - source.width * scale) / 2f, (size.height - source.height * scale) / 2f)
    matrix.scale(scale, scale)
    matrix.translate(-source.left, -source.top)
    transform(matrix)
}

/** A single expressive shape, usable anywhere Compose wants a [Shape]. */
class PolygonShape(polygon: RoundedPolygon) : Shape {
    private val base = polygon.toPath().asComposePath()
    private val bounds = base.getBounds()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addPath(base) }.fitInto(size, bounds))
}

/** A frozen frame of a morph, fitted from the whole cycle's reference box. */
class MorphShape(private val morph: Morph, private val progress: Float, private val bounds: Rect) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morph.toPath(progress).asComposePath().fitInto(size, bounds))
}

/**
 * The box every frame of every morph in a cycle fits inside.
 *
 * An in-between of two shapes can reach past both of them, so the extent is
 * sampled along each morph rather than taken from the endpoints.
 */
private fun List<Morph>.travelBounds(samples: Int = 12): Rect {
    var box: Rect? = null
    forEach { morph ->
        for (step in 0..samples) {
            val frame = morph.toPath(step / samples.toFloat()).asComposePath().getBounds()
            box = box?.union(frame) ?: frame
        }
    }
    return box ?: Rect.Zero
}

/**
 * Walks the shape cycle forever, morphing each shape into the next.
 *
 * With [animated] false the shape settles on the first of the cycle, so the
 * system-wide "remove animations" setting leaves a still — and still
 * expressive — shape.
 */
@Composable fun rememberMorphingShape(
    shapes: List<RoundedPolygon> = LumaShapes.cycle,
    stepMillis: Int = 2400,
    animated: Boolean = true
): Shape {
    val morphs = remember(shapes) { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val bounds = remember(morphs) { morphs.travelBounds() }
    if (!animated) return remember(shapes) { PolygonShape(shapes.first()) }
    val travel = rememberInfiniteTransition(label = "shapeCycle").animateFloat(
        initialValue = 0f,
        targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(stepMillis * morphs.size, easing = LinearEasing)),
        label = "shapeTravel"
    ).value
    val index = travel.toInt().coerceIn(0, morphs.size - 1)
    // Smoothstep each hand-off so the shape rests on a recognisable form
    // instead of sliding between them at a constant rate.
    val step = travel - index
    return MorphShape(morphs[index], step * step * (3f - 2f * step), bounds)
}

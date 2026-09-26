package app.luma.chat

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.sin

/** Honours the system-wide "remove animations" switch. */
@Composable fun motionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

/**
 * The four-pointed sparkle Material uses to mark something generated: points on
 * the axes, sides pulled towards the centre so the arms read as slivers rather
 * than a diamond. Unit radius, centred on the origin, so one path can be reused
 * for every star through the draw transform.
 */
private fun unitSparkle(): Path = Path().apply {
    val pull = .16f
    moveTo(0f, -1f)
    cubicTo(0f, -pull, pull, 0f, 1f, 0f)
    cubicTo(pull, 0f, 0f, pull, 0f, 1f)
    cubicTo(0f, pull, -pull, 0f, -1f, 0f)
    cubicTo(-pull, 0f, 0f, -pull, 0f, -1f)
    close()
}

/**
 * `x`/`y` are fractions of the field, `size` a fraction of its smaller side.
 * `phase` and `speed` stagger the twinkle so no two stars pulse together, and
 * `weight` keeps the ones nearest the text quiet enough to read through.
 */
private class Spark(
    val x: Float, val y: Float, val size: Float,
    val phase: Float, val speed: Float, val weight: Float, val warm: Boolean = false
)

private val sparks = listOf(
    Spark(.14f, .09f, .030f, .00f, 1.0f, .85f),
    Spark(.83f, .13f, .042f, .35f, .7f, 1.0f, warm = true),
    Spark(.50f, .05f, .022f, .62f, 1.3f, .70f),
    Spark(.28f, .19f, .017f, .18f, 1.6f, .55f),
    Spark(.68f, .24f, .026f, .80f, .9f, .60f),
    Spark(.06f, .31f, .034f, .47f, 1.1f, .75f, warm = true),
    Spark(.94f, .36f, .020f, .05f, 1.4f, .55f),
    Spark(.19f, .47f, .015f, .70f, 1.8f, .35f),
    Spark(.88f, .55f, .024f, .28f, 1.0f, .40f),
    Spark(.09f, .64f, .028f, .55f, .8f, .70f),
    Spark(.77f, .71f, .036f, .12f, 1.2f, .90f, warm = true),
    Spark(.33f, .78f, .019f, .88f, 1.5f, .60f),
    Spark(.58f, .84f, .030f, .40f, .9f, .80f),
    Spark(.16f, .89f, .023f, .66f, 1.3f, .65f),
    Spark(.91f, .93f, .017f, .22f, 1.7f, .50f),
    Spark(.44f, .96f, .026f, .93f, 1.0f, .70f, warm = true)
)

/**
 * A slow field of twinkling sparkles. One animation drives every star; each
 * derives its own pulse from a sine at its own phase, which keeps this to a
 * single running animation and one draw pass.
 */
@Composable fun SparkleField(modifier: Modifier = Modifier, tint: Color, warmTint: Color, animated: Boolean = true) {
    val phase = if (animated) {
        val transition = rememberInfiniteTransition(label = "sparkles")
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
            label = "phase"
        ).value
    } else .32f
    val star = remember { unitSparkle() }
    Canvas(modifier) {
        val unit = size.minDimension
        sparks.forEach { spark ->
            val cycle = (phase * spark.speed + spark.phase) % 1f
            val pulse = (sin(cycle * 2f * PI.toFloat()) + 1f) / 2f
            val center = Offset(spark.x * size.width, spark.y * size.height)
            withTransform({
                translate(center.x, center.y)
                rotate(cycle * 90f, Offset.Zero)
                val radius = spark.size * unit * (.70f + .38f * pulse)
                scale(radius, radius, Offset.Zero)
            }) {
                drawPath(star, if (spark.warm) warmTint else tint, alpha = (.22f + .50f * pulse) * spark.weight)
            }
        }
    }
}

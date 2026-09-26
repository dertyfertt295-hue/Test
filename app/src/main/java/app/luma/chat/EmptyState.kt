package app.luma.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How long each line of the empty chat's headline stays up. */
private const val ThoughtMillis = 4500L

/**
 * The empty chat's headline. It opens on "Where shall we start?", then every
 * few seconds the line slides down and away while another drops in from above:
 * one of the short philosophical thoughts in `R.array.empty_thoughts`, in a
 * random order that gives each its turn before any comes back.
 *
 * It holds still while the keyboard is up, so nothing moves while someone
 * types, and when the system has animations turned off.
 */
@Composable internal fun RotatingHeadline(style: TextStyle, paused: Boolean, modifier: Modifier = Modifier) {
    val anchor = stringResource(R.string.empty_title)
    val thoughts = stringArrayResource(R.array.empty_thoughts).toList()
    // Keyed on the text, so switching language starts over in the new one.
    val order = remember(anchor, thoughts) { listOf(anchor) + thoughts.shuffled() }
    var index by remember(order) { mutableIntStateOf(0) }
    val motion = motionEnabled()
    LaunchedEffect(order, paused, motion) {
        if (paused || !motion) return@LaunchedEffect
        while (true) {
            delay(ThoughtMillis)
            index = (index + 1) % order.size
        }
    }
    // Two lines are kept for every thought, so the mark and the subtitle stay
    // put as the lines change length; the rare longer one grows the space smoothly.
    val twoLines = with(LocalDensity.current) { (style.lineHeight * 2).toDp() }
    AnimatedContent(order[index], modifier.fillMaxWidth().heightIn(min = twoLines), contentAlignment = Alignment.Center,
        transitionSpec = {
            (slideInVertically(spring(dampingRatio = .8f, stiffness = 240f)) { -it / 2 } + fadeIn(tween(320, delayMillis = 90))) togetherWith
                (slideOutVertically(tween(300, easing = EaseInCubic)) { it / 2 } + fadeOut(tween(220)) + scaleOut(tween(300), targetScale = .94f)) using
                SizeTransform(clip = false)
        }, label = "headline") { line ->
        // The minimum height reaches the text too; wrapping keeps a one-line
        // thought centred in the space rather than stuck to its top.
        Text(line, Modifier.fillMaxWidth().wrapContentHeight(), style = style, textAlign = TextAlign.Center)
    }
}

/**
 * Taps that come quickly one after another. [tap] says when [needed] have
 * landed in a row, each within [gapMillis] of the last, and then starts over.
 */
internal class TapStreak(private val needed: Int = 5, private val gapMillis: Long = 700) {
    private var count = 0
    private var last = -1L
    fun tap(now: Long): Boolean {
        count = if (last >= 0 && now - last <= gapMillis) count + 1 else 1
        last = now
        if (count < needed) return false
        count = 0
        return true
    }
}

/** A fan spinning up hard and winding down slowly. */
private val FanEasing = CubicBezierEasing(.35f, 0f, .1f, 1f)
private const val FanMillis = 3200
// Kept under an eighth of a turn per frame at full speed: the four petals
// would otherwise seem to crawl backwards, the way wheels do on film.
private const val FanTurns = 8

/**
 * Luma's mark on the empty chat.
 *
 * A tap squeezes the shape and gives the mark a quarter turn — with four
 * petals that lands it looking as it started, so taps never leave it askew.
 * Five quick taps set it spinning like a fan while the shape warms to the
 * tertiary colour, then it winds down and settles back.
 */
@Composable internal fun HeroMark(pop: Float, tick: (HapticFeedbackType) -> Unit) {
    val scope = rememberCoroutineScope()
    val motion = motionEnabled()
    val squeeze = remember { Animatable(1f) }
    val turn = remember { Animatable(0f) }
    val streak = remember { TapStreak() }
    var spinning by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (spinning) colors.tertiaryContainer else colors.primaryContainer, tween(600), label = "heroColor")
    val ink by animateColorAsState(if (spinning) colors.onTertiaryContainer else colors.onPrimaryContainer, tween(600), label = "heroInk")
    val onTap by rememberUpdatedState<(Long) -> Unit> { now ->
        when {
            spinning -> Unit
            !motion -> tick(HapticFeedbackType.ContextClick)
            streak.tap(now) -> {
                spinning = true
                tick(HapticFeedbackType.LongPress)
                scope.launch {
                    squeeze.animateTo(.86f, tween(80))
                    squeeze.animateTo(1.1f, spring(dampingRatio = .45f, stiffness = 300f))
                }
                scope.launch {
                    // Ends on a quarter too, whatever angle the last tap left.
                    val from = turn.value
                    turn.animateTo((from / 90f).roundToInt() * 90f + FanTurns * 360f, tween(FanMillis, easing = FanEasing))
                    turn.snapTo(turn.value % 360f)
                    spinning = false
                    tick(HapticFeedbackType.Confirm)
                    squeeze.animateTo(1f, spring(dampingRatio = .4f, stiffness = 320f))
                }
            }
            else -> {
                tick(HapticFeedbackType.ContextClick)
                scope.launch {
                    squeeze.animateTo(.86f, tween(80))
                    squeeze.animateTo(1f, spring(dampingRatio = .35f, stiffness = 480f))
                }
                scope.launch {
                    turn.animateTo((turn.targetValue / 90f).roundToInt() * 90f + 90f, spring(dampingRatio = .45f, stiffness = 260f))
                    turn.snapTo(turn.value % 360f)
                }
            }
        }
    }
    // A still expressive shape here: this screen has to reach idle for
    // screenshot tests, so the morph is kept to the sign-in screen.
    val hero = remember { PolygonShape(LumaShapes.Cookie9Sided) }
    Surface(
        color = container, shape = hero,
        modifier = Modifier
            .graphicsLayer { val scale = pop * squeeze.value; scaleX = scale; scaleY = scale }
            .testTag("luma-hero")
            .pointerInput(Unit) {
                // The event's own time, so a streak is judged by when the taps
                // really happened rather than by when they were handled.
                awaitEachGesture {
                    awaitFirstDown()
                    waitForUpOrCancellation()?.let { up -> onTap(up.uptimeMillis) }
                }
            }
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            LumaMark(Modifier.size(42.dp).graphicsLayer { rotationZ = turn.value }, ink)
        }
    }
}

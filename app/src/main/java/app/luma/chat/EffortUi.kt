package app.luma.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// The dial's arc opens at the bottom, like a speedometer. Angles run clockwise
// from three o'clock, as Compose draws them: Instant points down-left, High
// straight up and Ultra down-right.
private const val DialStart = 150f
private const val DialSweep = 240f
/** Instant still shows a sliver of colour, so the dial never reads as switched off. */
private const val DialMinSweep = 14f

/**
 * A dial whose needle points at the chosen [Effort].
 *
 * The needle springs to its new place and overshoots a touch before it settles,
 * the way a real gauge does. The arc behind it fills up to the needle and warms
 * from the primary towards the tertiary colour as the effort climbs.
 */
@Composable internal fun EffortGauge(level: Effort, modifier: Modifier = Modifier) {
    val target = level.ordinal / Effort.values().lastIndex.toFloat()
    val fraction by animateFloatAsState(target,
        if (motionEnabled()) spring(dampingRatio = .42f, stiffness = 260f) else snap(), label = "effortNeedle")
    val ink = LocalContentColor.current
    val cool = MaterialTheme.colorScheme.primary
    val warm = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val stroke = size.minDimension * .11f
        val inset = stroke / 2 + size.minDimension * .04f
        val box = Size(size.width - 2 * inset, size.height - 2 * inset)
        val corner = Offset(inset, inset)
        val line = Stroke(stroke, cap = StrokeCap.Round)
        drawArc(ink.copy(alpha = .32f), DialStart, DialSweep, false, corner, box, style = line)
        drawArc(lerp(cool, warm, fraction.coerceIn(0f, 1f) * .8f), DialStart,
            (DialSweep * fraction).coerceIn(DialMinSweep, DialSweep), false, corner, box, style = line)
        val angle = Math.toRadians((DialStart + DialSweep * fraction).toDouble())
        val reach = box.minDimension / 2 * .6f
        val tip = Offset(center.x + cos(angle).toFloat() * reach, center.y + sin(angle).toFloat() * reach)
        drawLine(ink, center, tip, strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(ink, radius = stroke, center = center)
    }
}

/** The dial in the composer; opens [EffortOverlay]. */
@Composable internal fun EffortButton(level: Effort, onClick: () -> Unit, enabled: Boolean = true) {
    val description = stringResource(R.string.effort_change, level.label)
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp).semantics { contentDescription = description }) {
        EffortGauge(level, Modifier.size(22.dp))
    }
}

/**
 * The effort picker. The chat fades back and the levels come up as a slider
 * where the composer sits, the way the big assistants do it; the keyboard is
 * left alone, so typing carries on after. A choice applies at once, and a tap
 * outside or Back puts the picker away.
 */
@Composable internal fun EffortOverlay(
    open: Boolean, level: Effort, model: LumaModel, tick: (HapticFeedbackType) -> Unit,
    onSelect: (Effort) -> Unit, onDismiss: () -> Unit
) {
    val motion = motionEnabled()
    BackHandler(open, onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open,
            enter = if (motion) fadeIn(tween(220)) else EnterTransition.None,
            exit = if (motion) fadeOut(tween(200)) else ExitTransition.None) {
            // Denser towards the bottom, so the composer and its chips do not show
            // through right under the picker, while the chat above stays faintly in view.
            val veil = MaterialTheme.colorScheme.surface
            Box(Modifier.fillMaxSize()
                .background(Brush.verticalGradient(0f to veil.copy(alpha = .8f), .55f to veil.copy(alpha = .88f), .8f to veil.copy(alpha = .97f), 1f to veil.copy(alpha = .98f)))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } })
        }
        AnimatedVisibility(open, Modifier.align(Alignment.BottomCenter),
            // Each part brings its own entrance, staggered below; they leave together.
            enter = EnterTransition.None,
            exit = if (motion) fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 6 } else ExitTransition.None) {
            Column(
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .widthIn(max = 560.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EffortTitle(level, Modifier.animateEnterExit(enter = rise(motion, 0) { it / 2 }))
                val reasoning = LumaModel.values().filter { it.reasons }.joinToString(", ") { it.label }
                val note = if (model.reasons) stringResource(level.note)
                    else stringResource(R.string.effort_unsupported, model.label, reasoning)
                AnimatedContent(note, Modifier.padding(top = 6.dp, bottom = 18.dp).animateEnterExit(enter = rise(motion, 50) { it / 2 }),
                    transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) }, label = "effortNote") { text ->
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
                EffortSlider(level, onSelect, tick, Modifier.animateEnterExit(
                    enter = rise(motion, 90) { it } + if (motion) scaleIn(tween(460, 90, EaseOutBack), initialScale = .9f) else EnterTransition.None))
            }
        }
    }
}

/** Fade up into place after [delay], overshooting a little on arrival. */
private fun rise(motion: Boolean, delay: Int, distance: (Int) -> Int): EnterTransition =
    if (!motion) EnterTransition.None
    else fadeIn(tween(240, delayMillis = delay)) + slideInVertically(tween(460, delay, EaseOutBack), distance)

/** "<Level> effort", with the level in the accent colour rolling sideways as it changes. */
@Composable private fun EffortTitle(level: Effort, modifier: Modifier = Modifier) {
    // The level's place in the sentence depends on the language, so the
    // template is split around it rather than assumed to lead or trail.
    val template = stringResource(R.string.effort_title)
    val before = template.substringBefore("%1\$s")
    val after = template.substringAfter("%1\$s", "")
    val style = MaterialTheme.typography.titleLarge
    val motion = motionEnabled()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        EffortGauge(level, Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        if (before.isNotBlank()) Text(before, style = style)
        AnimatedContent(level, transitionSpec = {
            // Rolls the way the thumb moved: up the scale from the right, down it from the left.
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            if (!motion) EnterTransition.None togetherWith ExitTransition.None
            else (slideInHorizontally(spring(dampingRatio = .78f, stiffness = 520f)) { direction * it / 2 } + fadeIn(tween(160))) togetherWith
                (slideOutHorizontally(tween(140)) { -direction * it / 2 } + fadeOut(tween(110))) using SizeTransform(clip = false)
        }, label = "effortName") { shown ->
            Text(shown.label, style = style, color = MaterialTheme.colorScheme.primary)
        }
        if (after.isNotBlank()) Text(after, style = style)
    }
}

/**
 * A pill with one stop per level. The thumb follows the finger, lands on the
 * nearest stop with a spring, and ticks as it passes each one; a tap anywhere
 * on the track goes straight to that stop.
 */
@Composable private fun EffortSlider(level: Effort, onSelect: (Effort) -> Unit, tick: (HapticFeedbackType) -> Unit, modifier: Modifier = Modifier) {
    val levels = Effort.values()
    val last = levels.lastIndex.toFloat()
    val motion = motionEnabled()
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(level)
    val select by rememberUpdatedState(onSelect)
    val position = remember { Animatable(level.ordinal.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val thumb = 52.dp
    val inset = 8.dp
    val density = LocalDensity.current
    val thumbPx = with(density) { thumb.toPx() }
    val insetPx = with(density) { inset.toPx() }
    val stepPx = ((width - 2 * insetPx - thumbPx) / last).coerceAtLeast(1f)
    fun stopAt(x: Float) = ((x - insetPx - thumbPx / 2) / stepPx).coerceIn(0f, last)
    fun land(at: Float) = scope.launch {
        val stop = at.roundToInt().toFloat()
        if (motion) position.animateTo(stop, spring(dampingRatio = .6f, stiffness = 420f)) else position.snapTo(stop)
    }
    // A level chosen by tap (or anywhere else) moves the thumb; during a drag
    // the finger does.
    LaunchedEffect(level) { if (!dragging) land(level.ordinal.toFloat()) }
    val press by animateFloatAsState(if (dragging) 1.1f else 1f,
        if (motion) spring(dampingRatio = .5f, stiffness = 600f) else snap(), label = "effortThumbPress")

    val colors = MaterialTheme.colorScheme
    // A light thumb on the dark track, a white one on the light track, ringed in the accent.
    val thumbFill = if (colors.surface.luminance() < .5f) colors.onSurface else colors.surfaceContainerLowest
    val trail = colors.primary.copy(alpha = .16f)
    val stop = colors.onSurfaceVariant.copy(alpha = .55f)
    val reached = colors.primary
    val description = stringResource(R.string.effort_slider)
    Box(
        modifier.fillMaxWidth().height(thumb + inset * 2)
            .background(colors.surfaceContainerHigh, CircleShape)
            .border(1.dp, colors.outlineVariant.copy(alpha = .5f), CircleShape)
            .onSizeChanged { width = it.width }
            .semantics {
                contentDescription = description
                stateDescription = level.label
                progressBarRangeInfo = ProgressBarRangeInfo(level.ordinal.toFloat(), 0f..last, steps = levels.size - 2)
                setProgress { value -> select(levels[value.roundToInt().coerceIn(0, levels.lastIndex)]); true }
            }
            .pointerInput(stepPx) {
                detectTapGestures { offset ->
                    val chosen = levels[stopAt(offset.x).roundToInt()]
                    if (chosen != current) { tick(HapticFeedbackType.SegmentTick); select(chosen) }
                }
            }
            .pointerInput(stepPx) {
                var at = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        at = stopAt(offset.x)
                        scope.launch { position.snapTo(at) }
                    },
                    onDragEnd = { dragging = false; land(at) },
                    onDragCancel = { dragging = false; land(at) }
                ) { change, amount ->
                    change.consume()
                    at = (at + amount / stepPx).coerceIn(0f, last)
                    scope.launch { position.snapTo(at) }
                    val nearest = levels[at.roundToInt()]
                    if (nearest != current) { tick(HapticFeedbackType.SegmentFrequentTick); select(nearest) }
                }
            }
            .drawBehind {
                // Read here rather than in composition, so a moving thumb only redraws.
                val x = insetPx + position.value * stepPx
                drawRoundRect(trail, Offset(insetPx, insetPx), Size(x + thumbPx - insetPx, thumbPx), CornerRadius(thumbPx / 2))
                val radius = 4.dp.toPx()
                levels.indices.forEach { index ->
                    drawCircle(if (index <= position.value + .01f) reached else stop, radius,
                        Offset(insetPx + thumbPx / 2 + index * stepPx, size.height / 2))
                }
            }
    ) {
        Box(
            Modifier.offset { IntOffset((insetPx + position.value * stepPx).roundToInt(), insetPx.roundToInt()) }
                .size(thumb)
                .graphicsLayer { scaleX = press; scaleY = press }
                .shadow(if (dragging) 8.dp else 3.dp, CircleShape)
                .background(thumbFill, CircleShape)
                .border(3.dp, colors.primary, CircleShape)
        )
    }
}

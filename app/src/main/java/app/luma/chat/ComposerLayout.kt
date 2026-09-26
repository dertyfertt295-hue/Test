package app.luma.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * Keep the editor in one composition slot when the IME opens, preserving focus and selection.
 * [effort], when there is one, sits just before the microphone in both layouts.
 */
@Composable internal fun ComposerLayout(
    expanded: Boolean, photo: (@Composable () -> Unit)?, editor: @Composable () -> Unit,
    add: @Composable () -> Unit, model: @Composable () -> Unit,
    mic: @Composable () -> Unit, send: @Composable () -> Unit, effort: (@Composable () -> Unit)? = null
) {
    val motion = motionEnabled()
    Surface(shape = RoundedCornerShape(if (expanded) 28.dp else 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
        Layout(modifier = Modifier.fillMaxWidth().then(if (motion) Modifier.animateContentSize(tween(220)) else Modifier),
            content = {
                Box { photo?.invoke() }
                Box { editor() }
                Box(contentAlignment = Alignment.Center) { add() }
                Box { model() }
                Box(contentAlignment = Alignment.Center) { mic() }
                Box(contentAlignment = Alignment.Center) { send() }
                Box(contentAlignment = Alignment.Center) { effort?.invoke() }
            }) { nodes, constraints ->
            val width = constraints.maxWidth
            val pad = 8.dp.roundToPx()
            val button = 44.dp.roundToPx()
            val tools = listOf(2, 4, 5).associateWith { nodes[it].measure(Constraints.fixed(button, button)) }
            val gauge = if (effort != null) nodes[6].measure(Constraints.fixed(button, button)) else null
            // Room left between the add button and the trailing buttons.
            val free = (width - (if (gauge != null) 4 else 3) * button - 2 * pad).coerceAtLeast(0)
            val image = nodes[0].measure(Constraints(maxWidth = (width - 4 * pad).coerceAtLeast(0)))
            val label = nodes[3].measure(Constraints(maxWidth = free, maxHeight = if (expanded) button else 0))
            val inputWidth = if (expanded) (width - 4 * pad).coerceAtLeast(0) else free
            val input = nodes[1].measure(Constraints(minWidth = inputWidth, maxWidth = inputWidth))
            val imageHeight = if (image.height > 0) image.height + pad else 0
            val height = if (expanded) 3 * pad + imageHeight + input.height + button else maxOf(button, input.height) + 2 * pad
            layout(width, height) {
                val toolsY = height - button - pad
                image.placeRelative(2 * pad, pad)
                input.placeRelative(if (expanded) 2 * pad else pad + button, if (expanded) pad + imageHeight else (height - input.height) / 2)
                tools.getValue(2).placeRelative(pad, toolsY)
                if (expanded) label.placeRelative(pad + button, toolsY + (button - label.height) / 2)
                gauge?.placeRelative(width - pad - 3 * button, toolsY)
                tools.getValue(4).placeRelative(width - pad - 2 * button, toolsY)
                tools.getValue(5).placeRelative(width - pad - button, toolsY)
            }
        }
    }
}

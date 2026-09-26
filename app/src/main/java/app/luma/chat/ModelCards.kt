package app.luma.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable internal fun ModelCards(selected: LumaModel, onSelect: (LumaModel) -> Unit) {
    val motion = motionEnabled()
    var entered by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { entered = true }
    Column(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LumaModel.values().forEachIndexed { index, model ->
            val chosen = selected == model
            val interactions = remember { MutableInteractionSource() }
            val pressed by interactions.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) .97f else 1f, if (motion) spring(dampingRatio = .65f) else snap(), label = "modelPress")
            val entrance by animateFloatAsState(if (entered) 1f else 0f, if (motion) tween(320, index * 55, EaseOutCubic) else snap(), label = "modelEntrance")
            val color by animateColorAsState(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                if (motion) tween(220) else snap(), label = "modelSelection")
            val foreground = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            Surface(shape = RoundedCornerShape(24.dp), color = color,
                border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = .5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f)),
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; alpha = entrance; translationY = (1f - entrance) * 24.dp.toPx() }
                    .selectable(chosen, interactionSource = interactions, indication = ripple(), role = Role.RadioButton, onClick = { onSelect(model) })) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val shape = remember(model) { PolygonShape(when (model) {
                        LumaModel.A45 -> LumaShapes.Cookie9Sided
                        LumaModel.V441 -> LumaShapes.SoftBurst
                        LumaModel.S45 -> LumaShapes.Clover4Leaf
                    }) }
                    Surface(shape = shape, color = MaterialTheme.colorScheme.surface.copy(alpha = .65f)) {
                        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                            Text(model.label.take(1), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(model.label, style = MaterialTheme.typography.titleMedium, color = foreground)
                        Text(stringResource(model.note), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = .8f))
                    }
                    AnimatedVisibility(chosen, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        Icon(Icons.Outlined.Check, null, tint = foreground, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

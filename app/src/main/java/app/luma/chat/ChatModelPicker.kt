package app.luma.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Chat-only switcher. The compact input remains a single row beneath it. */
@Composable internal fun ChatModelPicker(model: LumaModel, open: Boolean, enabled: Boolean, onToggle: () -> Unit, onSelect: (LumaModel) -> Unit) {
    val motion = motionEnabled()
    val changeLabel = stringResource(R.string.model_change)
    val turn by animateFloatAsState(if (open) 180f else 0f, if (motion) spring(dampingRatio = .65f) else snap(), label = "modelChevron")
    Column(Modifier.fillMaxWidth()) {
        AnimatedVisibility(open, enter = if (motion) fadeIn(tween(180)) + expandVertically(spring(dampingRatio = .85f), expandFrom = Alignment.Bottom) else EnterTransition.None,
            exit = if (motion) fadeOut(tween(140)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Bottom) else ExitTransition.None) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)), modifier = Modifier.padding(bottom = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.model_sheet_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LumaModel.values().forEach { option ->
                            val chosen = model == option
                            val color by animateColorAsState(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                if (motion) tween(240) else snap(), label = "chatModelColor")
                            val lift by animateFloatAsState(if (chosen) -3f else 0f, if (motion) spring(dampingRatio = .55f) else snap(), label = "chatModelLift")
                            Surface(shape = RoundedCornerShape(20.dp), color = color,
                                modifier = Modifier.weight(1f).graphicsLayer { translationY = lift.dp.toPx() }
                                    .selectable(chosen, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option) })) {
                                Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val shape = remember(option) { PolygonShape(when (option) {
                                        LumaModel.A45 -> LumaShapes.Cookie9Sided
                                        LumaModel.V441 -> LumaShapes.SoftBurst
                                        LumaModel.S45 -> LumaShapes.Clover4Leaf
                                    }) }
                                    Surface(shape = shape, color = MaterialTheme.colorScheme.surface) {
                                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                            AnimatedContent(chosen, transitionSpec = { (fadeIn(tween(if (motion) 180 else 0)) + scaleIn(initialScale = .65f)) togetherWith fadeOut(tween(if (motion) 100 else 0)) }, label = "chatModelCheck") { checked ->
                                                if (checked) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                                else Text(option.label.take(1), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                    AnimatedContent(model, modifier = Modifier.padding(top = 12.dp), transitionSpec = {
                        (fadeIn(tween(if (motion) 220 else 0)) + slideInVertically { if (motion) it / 3 else 0 }) togetherWith fadeOut(tween(if (motion) 100 else 0))
                    }, label = "chatModelDescription") { selection ->
                        Text(stringResource(selection.note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Surface(onClick = onToggle, enabled = enabled, shape = CircleShape,
            color = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).semantics { contentDescription = changeLabel }) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                AnimatedContent(model, transitionSpec = {
                    (fadeIn(tween(if (motion) 200 else 0)) + slideInVertically { if (motion) it else 0 }) togetherWith
                        (fadeOut(tween(if (motion) 120 else 0)) + slideOutVertically { if (motion) -it else 0 })
                }, label = "chatModelName") { selection -> Text(selection.label, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge) }
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp).graphicsLayer { rotationZ = turn })
            }
        }
    }
}

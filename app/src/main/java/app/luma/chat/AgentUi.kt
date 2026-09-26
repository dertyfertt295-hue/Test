@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.luma.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable internal fun ModeSwitcher(agent: Boolean, enabled: Boolean, onAgent: (Boolean) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.width(180.dp).padding(4.dp).selectableGroup()) {
            listOf(stringResource(R.string.mode_chat), "O5").forEachIndexed { index, label ->
                val selected = agent == (index == 1)
                val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh, label = "modeColor")
                Surface(shape = CircleShape, color = color, modifier = Modifier.weight(1f)
                    .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onAgent(index == 1) })
                    .semantics { contentDescription = label }) {
                    Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .5f))
                    }
                }
            }
        }
    }
}

@Composable internal fun AgentActivity(steps: List<AgentStep>, running: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val current = steps.lastOrNull() ?: AgentStep("plan")
    val motion = motionEnabled()
    Surface(onClick = { expanded = !expanded }, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.then(if (motion) Modifier.animateContentSize() else Modifier).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    if (motion) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                } else Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                AnimatedContent(if (running) current.kind else "done", modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn(tween(if (motion) 220 else 0)) togetherWith fadeOut(tween(if (motion) 120 else 0)) }, label = "agentAction") { kind ->
                    Text(stepLabel(kind), style = MaterialTheme.typography.labelLarge)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    stringResource(R.string.o5_steps), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (running && !expanded && current.detail.isNotBlank()) Text(current.detail,
                Modifier.padding(start = 28.dp, top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) steps.forEach { step ->
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (step.kind == "failed") Icons.Outlined.Info else if (step.done) Icons.Outlined.Check else Icons.Outlined.MoreHoriz,
                        null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stepLabel(step.kind), style = MaterialTheme.typography.labelMedium)
                        if (step.detail.isNotBlank()) Text(step.detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable private fun stepLabel(kind: String) = stringResource(when (kind) {
    "search" -> R.string.o5_search
    "read" -> R.string.o5_read
    "review" -> R.string.o5_review
    "done" -> R.string.o5_done
    "failed" -> R.string.o5_failed
    else -> R.string.o5_plan
})

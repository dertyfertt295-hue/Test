@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.luma.chat

import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The drawer's floating bottom row, laid out the way the big assistants do it:
 * a new conversation on the left and, on the right, the account's avatar, which
 * opens settings. The list scrolls underneath and fades out behind them, and
 * each time the drawer opens they rise into place one after the other.
 */
@Composable internal fun DrawerActions(
    account: LumaAccount?, open: Boolean, onNew: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier
) {
    val motion = motionEnabled()
    val backdrop = MaterialTheme.colorScheme.surfaceContainerLow
    Row(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to backdrop.copy(alpha = 0f), .45f to backdrop))
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Built on the plain FAB rather than the extended one, which hides its
        // label from semantics: here the label is the only name the button has.
        FloatingActionButton(
            onClick = onNew, shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.rise(open, motion, 0)
        ) {
            Row(Modifier.padding(start = 18.dp, end = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.chat_new), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.weight(1f))
        AccountAvatar(account, onSettings, Modifier.rise(open, motion, 90))
    }
}

/** Rises into place whenever [open] turns true, after [delay]; drops straight away when it turns false. */
@Composable private fun Modifier.rise(open: Boolean, motion: Boolean, delay: Int): Modifier {
    val progress by animateFloatAsState(if (open) 1f else 0f, when {
        !motion -> snap()
        open -> tween(460, delay, EaseOutBack)
        else -> tween(120)
    }, label = "drawerAction")
    val lift = with(LocalDensity.current) { 24.dp.toPx() }
    return graphicsLayer {
        alpha = progress.coerceIn(0f, 1f)
        translationY = lift * (1f - progress)
        val scale = .85f + .15f * progress
        scaleX = scale; scaleY = scale
    }
}

/** The account's initial on Luma's clover — the same avatar settings open on. */
@Composable private fun AccountAvatar(account: LumaAccount?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clover = remember { PolygonShape(LumaShapes.Clover4Leaf) }
    val label = stringResource(R.string.settings)
    Surface(
        onClick = onClick, shape = clover,
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier.size(56.dp).semantics { contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(account?.initial.orEmpty(), style = MaterialTheme.typography.titleLarge)
        }
    }
}

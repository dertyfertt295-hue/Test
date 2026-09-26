package app.luma.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One step of the staggered entrance: fade up into place after `delay`. */
@Composable private fun Modifier.entrance(visible: Boolean, delay: Int, rise: Dp = 20.dp): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(460, delayMillis = delay, easing = EaseOutCubic),
        label = "entrance$delay"
    )
    val density = LocalDensity.current
    return this.graphicsLayer {
        alpha = progress
        translationY = with(density) { rise.toPx() } * (1f - progress)
    }
}

@Composable fun SignInScreen(onSignedIn: (LumaAccount) -> Unit) {
    var problem by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val motion = motionEnabled()
    LaunchedEffect(Unit) { visible = true }

    val signIn = rememberGoogleSignIn { result ->
        busy = false
        result.fold(
            onSuccess = { problem = null; onSignedIn(it) },
            onFailure = { problem = (it as? SignInFailure)?.messageRes ?: R.string.signin_failed }
        )
    }

    // The mark sits on a halo that breathes, so the screen is never quite still.
    val halo = if (motion) {
        rememberInfiniteTransition(label = "halo").animateFloat(
            initialValue = .96f, targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(3200, easing = EaseOutCubic), RepeatMode.Reverse),
            label = "haloScale"
        ).value
    } else 1f
    val markScale by animateFloatAsState(
        targetValue = if (visible) 1f else .82f,
        animationSpec = spring(dampingRatio = .52f, stiffness = Spring.StiffnessLow),
        label = "markScale"
    )
    // The container walks the Material expressive shape family, turning slowly
    // so each new form arrives at a different angle.
    val morphing = rememberMorphingShape(animated = motion)
    val spin = if (motion) {
        rememberInfiniteTransition(label = "spin").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing)),
            label = "spinAngle"
        ).value
    } else 0f

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Box(Modifier.fillMaxSize()) {
            AsciiShapeField(
                Modifier.fillMaxSize().padding(padding),
                tint = MaterialTheme.colorScheme.primary,
                warmTint = MaterialTheme.colorScheme.tertiary
            )
            Column(
                Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val glow = MaterialTheme.colorScheme.primary
                // The halo box is deliberately larger than the shape inside it:
                // a child wider than its parent gets clamped by the parent's
                // constraints, which is what knocked the shape off centre.
                Box(
                    Modifier
                        .size(136.dp)
                        .graphicsLayer { scaleX = markScale; scaleY = markScale }
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(glow.copy(alpha = .20f), glow.copy(alpha = 0f)),
                                    center = center,
                                    radius = size.minDimension * .58f * halo
                                ),
                                radius = size.minDimension * .58f * halo
                            )
                        }
                        .entrance(visible, 0),
                    contentAlignment = Alignment.Center
                ) {
                    // Only the container turns; the mark inside stays upright.
                    Box(
                        Modifier.size(104.dp)
                            .graphicsLayer { rotationZ = spin }
                            .clip(morphing)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.graphicsLayer { rotationZ = -spin }, contentAlignment = Alignment.Center) {
                            LumaMark(Modifier.size(44.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(R.string.app_name),
                    Modifier.entrance(visible, 90),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.signin_subtitle),
                    Modifier.entrance(visible, 160),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(40.dp))
                Button(
                    onClick = { if (!busy) { busy = true; problem = null; signIn() } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp).entrance(visible, 240),
                    shape = MaterialTheme.shapes.large
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Outlined.AccountCircle, null, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(if (busy) R.string.signin_busy else R.string.signin_button), style = MaterialTheme.typography.labelLarge)
                }
                AnimatedVisibility(problem != null, enter = fadeIn(tween(220)) + expandVertically(spring(dampingRatio = .75f))) {
                    Surface(
                        Modifier.padding(top = 20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp))
                            Text(problem?.let { stringResource(it) }.orEmpty(), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(R.string.signin_privacy),
                    Modifier.entrance(visible, 320),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

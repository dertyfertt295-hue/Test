@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.luma.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val factory = remember { ViewModelProvider.AndroidViewModelFactory(application) }
            val vm: ChatViewModel = viewModel(factory = factory)
            LifecycleStartEffect(vm) {
                vm.visible(true)
                onStopOrDispose { vm.visible(false) }
            }
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = isLumaDark(state.preferences)
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val account by vm.account.collectAsStateWithLifecycle()
            LumaLocale(state.preferences.language) {
                LumaTheme(state.preferences) {
                    // The gate: no account, no assistant.
                    if (account == null) SignInScreen(vm::signIn) else LumaApp(vm, state)
                }
            }
        }
    }
}

/**
 * Haptics, routed through the preference.
 *
 * Material asks for a feedback type that matches the action rather than one
 * buzz for everything: a confirm on send, a tick on a choice, a toggle on a
 * switch. Devices without the right actuator fall back on their own.
 */
@Composable fun rememberHaptics(enabled: Boolean): (HapticFeedbackType) -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics, enabled) {
        { type -> if (enabled) runCatching { haptics.performHapticFeedback(type) } }
    }
}

@Composable fun LumaApp(vm: ChatViewModel, state: ChatState) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var manageId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val pending by vm.pending.collectAsStateWithLifecycle()
    val agentSteps by vm.agentSteps.collectAsStateWithLifecycle()
    val failures by vm.failures.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val arriving by vm.arriving.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val actionSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val tick = rememberHaptics(state.preferences.haptics)
    fun closeDrawer() { focus.clearFocus(); keyboard?.hide(); scope.launch { drawer.close() } }
    fun newChat() { tick(HapticFeedbackType.ContextClick); vm.newChat(); search = ""; closeDrawer() }
    BackHandler(drawer.isOpen || settingsOpen) { if (drawer.isOpen) closeDrawer() else settingsOpen = false }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(context.getString(it)); vm.dismissError() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !settingsOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(.88f).widthIn(max = 380.dp),
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                windowInsets = WindowInsets(0)
            ) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            LumaMark(Modifier.size(28.dp))
                            Text(stringResource(R.string.app_name), Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.headlineSmall)
                            IconButton(onClick = ::closeDrawer) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.drawer_close)) }
                        }
                        if (state.chats.isNotEmpty()) {
                            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text(stringResource(R.string.search_chats)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.search_clear)) } },
                                shape = MaterialTheme.shapes.large, textStyle = MaterialTheme.typography.bodyMedium,
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
                            Spacer(Modifier.height(20.dp))
                        }
                        if (state.chats.isNotEmpty()) Text(stringResource(R.string.your_chats), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
                        val filtered = state.chats.filter { it.title.contains(search, true) || it.messages.any { m -> m.text.contains(search, true) } }
                        if (state.chats.isEmpty()) {
                            Column(Modifier.weight(1f).fillMaxWidth().padding(top = 40.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.empty_chats_title), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.empty_chats_body), Modifier.padding(top = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            // Room at the end, so the last conversation can scroll clear of the floating buttons.
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (filtered.isEmpty()) item {
                                    Text(stringResource(R.string.nothing_found), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                items(filtered, key = { it.id }) { chat ->
                                    NavigationDrawerItem(
                                        label = { Text(chat.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                                        selected = state.activeId == chat.id,
                                        onClick = { tick(HapticFeedbackType.ContextClick); vm.open(chat.id); closeDrawer() },
                                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(20.dp)) },
                                        badge = { IconButton(onClick = { manageId = chat.id }) { Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.chat_actions, chat.title), Modifier.size(20.dp)) } },
                                        shape = MaterialTheme.shapes.large,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    DrawerActions(account, drawer.targetValue == DrawerValue.Open, ::newChat,
                        { tick(HapticFeedbackType.ContextClick); closeDrawer(); settingsOpen = true }, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    ) {
        AnimatedContent(settingsOpen, transitionSpec = {
            (fadeIn(tween(200)) + slideInHorizontally(spring(dampingRatio = .88f, stiffness = 380f)) { it / 8 }) togetherWith fadeOut(tween(120))
        }, label = "screen") { isSettings ->
            if (isSettings) {
                val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
                SettingsScreen(state.preferences, vm::settings, vm::clearHistory, account, vm::signOut, snackbar,
                    syncStatus, vm::syncNow, vm.syncAddress, vm::setSyncAddress) { settingsOpen = false }
            }
            else ChatScreen(state, pending.contains(state.activeId), failures[state.activeId], arriving, vm::revealed, snackbar, vm::retry, vm::draft, {
                tick(HapticFeedbackType.Confirm)
                vm.send(context.getString(R.string.photo_attached))
                focus.clearFocus(); keyboard?.hide()
            }, { state.activeId?.let(vm::stop) }, {
                tick(HapticFeedbackType.ContextClick); keyboard?.hide(); focus.clearFocus(); scope.launch { drawer.open() }
            }, ::newChat, { model -> tick(HapticFeedbackType.SegmentTick); vm.settings(state.preferences.copy(model = model.key)) },
                { text -> scope.launch { snackbar.showSnackbar(text) } }, vm::agent, agentSteps[state.activeId].orEmpty(), vm::photo,
                onEffort = vm::effort)
        }
    }
    val managed = state.chats.find { it.id == manageId }
    if (managed != null) ModalBottomSheet(onDismissRequest = { manageId = null }, sheetState = actionSheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(managed.title, Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        ListItem(headlineContent = { Text(stringResource(R.string.rename)) }, leadingContent = { Icon(Icons.Outlined.Edit, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { scope.launch { actionSheet.hide(); manageId = null; renameId = managed.id } }.padding(horizontal = 12.dp))
        ListItem(headlineContent = { Text(stringResource(R.string.delete_chat)) }, leadingContent = { Icon(Icons.Outlined.DeleteOutline, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.clickable { scope.launch { actionSheet.hide(); manageId = null; deleteId = managed.id } }.padding(horizontal = 12.dp))
        Spacer(Modifier.height(24.dp))
    }
    state.chats.find { it.id == renameId }?.let { chat ->
        var title by rememberSaveable(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(onDismissRequest = { renameId = null }, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(.9f), properties = DialogProperties(usePlatformDefaultWidth = false),
            icon = { Icon(Icons.Outlined.Edit, null) }, title = { Text(stringResource(R.string.rename_title)) }, text = {
                OutlinedTextField(title, { title = it.take(80) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.rename_label)) }, shape = MaterialTheme.shapes.medium)
            }, confirmButton = { TextButton(onClick = { vm.rename(chat.id, title); renameId = null }, enabled = title.isNotBlank()) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (deleteId != null) ConfirmDelete(stringResource(R.string.delete_chat_title), stringResource(R.string.delete_chat_body), { deleteId = null }) {
        deleteId?.let(vm::delete); deleteId = null
    }
}

@Composable fun ChatScreen(
    state: ChatState, pending: Boolean, failure: Int?, arriving: String?, onRevealed: (String) -> Unit,
    snackbar: SnackbarHostState, retry: () -> Unit, onDraft: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit,
    onMenu: () -> Unit, onNew: () -> Unit, onModel: (LumaModel) -> Unit, notify: (String) -> Unit, onAgent: (Boolean) -> Unit = {}, steps: List<AgentStep> = emptyList(), onPhoto: (String?) -> Unit = {},
    onEffort: (Effort) -> Unit = {}
) {
    val chat = state.active
    val assistant = state.preferences.assistant
    val list = rememberLazyListState()
    val context = LocalContext.current
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val appBar = TopAppBarDefaults.pinnedScrollBehavior()
    val tick = rememberHaptics(state.preferences.haptics)
    var effortOpen by rememberSaveable { mutableStateOf(false) }
    // O5 hides the dial, so switching to it puts an open picker away as well.
    LaunchedEffect(state.agent) { if (state.agent) effortOpen = false }
    LaunchedEffect(chat?.id, chat?.messages?.size, pending, failure, keyboardOpen) {
        if (chat != null) list.animateScrollToItem((chat.messages.size + if (pending || chat.messages.lastOrNull()?.fromUser == true) 1 else 0).coerceAtLeast(0))
    }
    val reveal = rememberWordReveal(arriving, chat, onRevealed, tick, motionEnabled() && state.preferences.animateReplies)
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(appBar.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { ModeSwitcher(state.agent, !pending, onAgent) },
                    navigationIcon = { FilledTonalIconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, stringResource(R.string.menu_open)) } },
                    actions = { FilledTonalIconButton(onClick = onNew) { Icon(Icons.Outlined.Edit, stringResource(R.string.chat_new)) } },
                    scrollBehavior = appBar,
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            },
            bottomBar = {
                key(state.activeId) {
                    Composer(state.draft, assistant, pending, chat == null && !keyboardOpen, onDraft, onSend, onStop, onModel, tick, state.agent, state.photo, onPhoto, notify,
                        effort = if (state.agent) null else state.preferences.effortLevel, effortOpen = effortOpen,
                        onEffortPicker = { tick(HapticFeedbackType.ContextClick); effortOpen = true })
                }
            }
        ) { padding ->
            if (chat == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    // The list is not composed in this branch, so nothing overlaps this entrance.
                    var arrived by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { arrived = true }
                    val rise by animateFloatAsState(
                        targetValue = if (arrived) 1f else 0f,
                        animationSpec = tween(420, easing = EaseOutCubic), label = "emptyState"
                    )
                    val density = LocalDensity.current
                    Column(
                        Modifier.padding(horizontal = 32.dp).padding(bottom = if (keyboardOpen) 0.dp else 24.dp)
                            .graphicsLayer {
                                alpha = rise
                                translationY = with(density) { 18.dp.toPx() } * (1f - rise)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!keyboardOpen) {
                            val pop by animateFloatAsState(
                                targetValue = if (arrived) 1f else .85f,
                                animationSpec = spring(dampingRatio = .5f, stiffness = Spring.StiffnessLow), label = "heroPop"
                            )
                            HeroMark(pop, tick)
                            Spacer(Modifier.height(28.dp))
                        }
                        val headline = if (keyboardOpen) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall
                        if (state.agent) {
                            Text(stringResource(R.string.o5_title), style = headline, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.o5_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        } else RotatingHeadline(headline, paused = keyboardOpen)
                    }
                }
            } else {
                val direction = LocalLayoutDirection.current
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + 20.dp,
                    end = padding.calculateEndPadding(direction) + 20.dp,
                    top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp
                ), verticalArrangement = Arrangement.spacedBy(if (state.preferences.compactChat) 14.dp else 26.dp)) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                                Text(stringResource(R.string.conversation_meta, conversationDate(chat.updatedAt)), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(chat.messages, key = { it.id }) { message ->
                        if (message.fromUser) {
                            Column(Modifier.fillMaxWidth().animateItem(placementSpec = spring(dampingRatio = .85f)),
                                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                message.photo?.let { PhotoPreview(it, inChat = true) }
                                if (message.text.isNotBlank()) Surface(Modifier.widthIn(max = 310.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(24.dp)) {
                                    SelectionContainer { Text(message.text, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge) }
                                }
                            }
                        } else Column(Modifier.fillMaxWidth().animateItem(placementSpec = spring(dampingRatio = .85f))) {
                            val revealing = reveal?.first == message.id
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (revealing) SpinningMark(Modifier.size(20.dp)) else LumaMark(Modifier.size(20.dp))
                                Text(if (message.agent) "O5" else stringResource(R.string.assistant_name), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleSmall)
                                val timingLabel = when {
                                    message.isDemo -> stringResource(R.string.message_demo)
                                    state.preferences.showResponseTime && message.responseDurationMs != null -> stringResource(R.string.response_duration,
                                        String.format(LocalConfiguration.current.locales[0], "%.1f", message.responseDurationMs / 1000.0))
                                    else -> null
                                }
                                if (timingLabel != null) Text(timingLabel, Modifier.padding(start = 10.dp),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (message.agent && message.steps.isNotEmpty()) AgentActivity(message.steps, false)
                            MarkdownBody(if (revealing) message.text.take(reveal.second) else message.text, Modifier.padding(top = 12.dp))
                            if (message.truncated && !revealing) Text(stringResource(R.string.message_truncated), Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            if (!revealing) {
                                val clipLabel = stringResource(R.string.assistant_name)
                                val copied = stringResource(R.string.copied)
                                IconButton(onClick = {
                                    tick(HapticFeedbackType.Confirm)
                                    val plainText = markdownRenderer(context).toMarkdown(message.text).toString()
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(clipLabel, plainText)); notify(copied)
                                }, modifier = Modifier.padding(top = 2.dp).size(44.dp).offset(x = (-10).dp)) {
                                    Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy_answer), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (pending) item {
                        if (chat.agent) AgentActivity(steps, true) else Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            SpinningMark(Modifier.size(22.dp))
                            Text(stringResource(R.string.assistant_thinking), Modifier.padding(start = 11.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!pending && chat.messages.lastOrNull()?.fromUser == true) item { RetryCard(failure, retry) }
                }
            }
        }
        EffortOverlay(effortOpen, state.preferences.effortLevel, assistant, tick, onEffort) { effortOpen = false }
    }
}

/**
 * Reveals a freshly arrived answer word by word.
 *
 * The answer already exists in full — this is presentation, not streaming. The
 * number of steps is capped so a long answer speeds up instead of dragging, and
 * because every step re-parses the Markdown, which is the real cost here.
 */
@Composable private fun rememberWordReveal(
    arriving: String?,
    chat: Conversation?,
    onRevealed: (String) -> Unit,
    tick: (HapticFeedbackType) -> Unit,
    animated: Boolean
): Pair<String, Int>? {
    var reveal by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(arriving, animated) {
        val id = arriving
        val full = id?.let { key -> chat?.messages?.firstOrNull { it.id == key }?.text }
        if (id == null || full.isNullOrEmpty() || !animated) {
            reveal = null
            id?.let(onRevealed)
            return@LaunchedEffect
        }
        val cuts = wordCuts(full)
        val chunk = max(1, (cuts.size + MaxRevealSteps - 1) / MaxRevealSteps)
        var index = 0
        while (index < cuts.size) {
            reveal = id to cuts[min(index, cuts.size - 1)]
            // Roughly eight ticks a second: enough to feel like typing, not a buzz.
            if ((index / chunk) % 3 == 0) tick(HapticFeedbackType.SegmentFrequentTick)
            delay(RevealStepMillis)
            index += chunk
        }
        reveal = null
        onRevealed(id)
    }
    return reveal
}

private const val MaxRevealSteps = 40
private const val RevealStepMillis = 45L

/** Index just past each word, so a prefix never cuts one in half. */
private fun wordCuts(text: String): List<Int> {
    val cuts = ArrayList<Int>()
    var index = 0
    while (index < text.length) {
        while (index < text.length && !text[index].isWhitespace()) index++
        while (index < text.length && text[index].isWhitespace()) index++
        cuts.add(index)
    }
    if (cuts.isEmpty() || cuts.last() != text.length) cuts.add(text.length)
    return cuts
}

/**
 * The mark turning like a fan while an answer is being written.
 *
 * Its four petals are 90° apart, so a quarter turn already looks like a full
 * cycle — the whole revolution is timed to read as one steady sweep rather than
 * four visible jumps.
 */
@Composable private fun SpinningMark(modifier: Modifier = Modifier) {
    val angle = if (motionEnabled()) {
        rememberInfiniteTransition(label = "generating").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
            label = "markSpin"
        ).value
    } else 0f
    LumaMark(modifier.graphicsLayer { rotationZ = angle })
}

@Composable private fun RetryCard(failure: Int?, retry: () -> Unit) {
    val problem = failure != null
    Surface(
        color = if (problem) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (problem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Icon(if (problem) Icons.Outlined.ErrorOutline else Icons.Outlined.HourglassEmpty, null, Modifier.padding(top = 4.dp).size(20.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(failure?.let { stringResource(it) } ?: stringResource(R.string.no_answer_yet), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = retry, modifier = Modifier.padding(top = 4.dp).offset(x = (-12).dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable private fun Composer(
    draft: String, assistant: LumaModel, pending: Boolean, showSuggestions: Boolean,
    onDraft: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit, onModel: (LumaModel) -> Unit,
    tick: (HapticFeedbackType) -> Unit, agent: Boolean = false, photo: String? = null, onPhoto: (String?) -> Unit = {}, notify: (String) -> Unit = {},
    /** Null hides the dial: O5 runs on its own fixed settings. */
    effort: Effort? = null, effortOpen: Boolean = false, onEffortPicker: () -> Unit = {}
) {
    val context = LocalContext.current
    var mediaBusy by remember { mutableStateOf(false) }
    var voiceOpen by remember { mutableStateOf(false) }
    val mediaScope = rememberCoroutineScope()
    val inputFocus = LocalFocusManager.current
    val inputKeyboard = LocalSoftwareKeyboardController.current
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var keyboardWasOpen by remember { mutableStateOf(false) }
    // With the effort picker up, Back belongs to the picker, keyboard or not.
    BackHandler(keyboardOpen && !voiceOpen && !effortOpen) {
        inputFocus.clearFocus(force = true)
        inputKeyboard?.hide()
    }
    LaunchedEffect(keyboardOpen) {
        // The system can consume Back to close the IME before BackHandler runs.
        // End editing then as well, so the compact layout cannot reopen it.
        if (keyboardWasOpen && !keyboardOpen) inputFocus.clearFocus(force = true)
        keyboardWasOpen = keyboardOpen
    }
    val openAttachments = rememberPhotoActions(onPhoto, { mediaBusy = it }, notify)
    if (voiceOpen) VoiceInput(onDismiss = { voiceOpen = false }, onText = { text -> onDraft(listOf(draft, text).filter { it.isNotBlank() }.joinToString(" ")); voiceOpen = false })
    var picker by rememberSaveable { mutableStateOf(false) }
    var modelCloseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    BackHandler(picker) { modelCloseJob?.cancel(); picker = false }
    val sendLabel = stringResource(R.string.action_send)
    val stopLabel = stringResource(R.string.action_stop)
    val hint = stringResource(R.string.composer_hint)
    val messageLabel = stringResource(R.string.composer_label)
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
        Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).padding(horizontal = 16.dp)) {
            // The effort picker's description sits right where the chips are.
            if (showSuggestions && !effortOpen) {
                val ideaDraft = stringResource(R.string.suggestion_idea_draft)
                val explainDraft = stringResource(R.string.suggestion_explain_draft)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Suggestion(Icons.Outlined.Lightbulb, stringResource(R.string.suggestion_idea)) { tick(HapticFeedbackType.ContextClick); onDraft(ideaDraft) }
                    Suggestion(Icons.Outlined.AutoAwesome, stringResource(R.string.suggestion_explain)) { tick(HapticFeedbackType.ContextClick); onDraft(explainDraft) }
                }
            }
            if (!agent) ChatModelPicker(assistant, picker, !pending, onToggle = {
                modelCloseJob?.cancel(); tick(HapticFeedbackType.ContextClick); picker = !picker
            }, onSelect = { model ->
                onModel(model)
                modelCloseJob?.cancel()
                modelCloseJob = mediaScope.launch { kotlinx.coroutines.delay(400); picker = false }
            })
            val expanded = keyboardOpen || photo != null
            var field by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
            // The draft also changes from outside the field — a suggestion, voice
            // input — and the cursor then belongs after the new text, not in the
            // middle of it where the next word would be typed.
            val fieldValue = if (field.text == draft) field else TextFieldValue(draft, TextRange(draft.length))
            ComposerLayout(expanded,
                photo = if (photo != null) ({ PhotoPreview(photo, Modifier.width(96.dp).height(120.dp), remove = { onPhoto(null) }) }) else null,
                editor = {                     BasicTextField(value = fieldValue, onValueChange = { next -> field = next; if (next.text != draft) onDraft(next.text) },
                        singleLine = false, maxLines = if (expanded) 6 else 1,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp, max = if (expanded) 144.dp else 28.dp).semantics { contentDescription = messageLabel },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            }
                        })
 },
                add = {                         IconButton(onClick = { inputFocus.clearFocus(); inputKeyboard?.hide(); openAttachments() }, enabled = !pending && !mediaBusy, modifier = Modifier.size(36.dp)) {
                            if (mediaBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Add, stringResource(R.string.photo_add), Modifier.size(22.dp))
                        }
 },
                model = {},
                mic = {                         IconButton(onClick = { voiceOpen = true }, enabled = !pending && !mediaBusy, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Mic, stringResource(R.string.voice_record), Modifier.size(22.dp))
                        }
 },
                send = {                         FilledIconButton(onClick = if (pending) onStop else onSend, enabled = pending || (!mediaBusy && (draft.isNotBlank() || photo != null)),
                            modifier = Modifier.size(48.dp).semantics { contentDescription = if (pending) stopLabel else sendLabel },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))) {
                            AnimatedContent(pending, transitionSpec = {
                                (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = .6f), initialScale = .55f)) togetherWith
                                    (fadeOut(tween(110)) + scaleOut(tween(110), targetScale = .55f))
                            }, label = "sendIcon") { stopping ->
                                Icon(if (stopping) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward, null, Modifier.size(24.dp))
                            }
                        }
 },
                effort = if (effort != null) ({ EffortButton(effort, onEffortPicker) }) else null
            )
            Text(stringResource(R.string.ai_disclaimer), Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun conversationDate(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    // The month name follows the language on screen, not the process default.
    val locale = LocalConfiguration.current.locales[0]
    return when (date) {
        LocalDate.now() -> stringResource(R.string.date_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
}

@Composable private fun Suggestion(icon: ImageVector, title: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(title) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
        shape = MaterialTheme.shapes.small,
        colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(40.dp)
    )
}

@Composable fun ConfirmDelete(title: String, body: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = confirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

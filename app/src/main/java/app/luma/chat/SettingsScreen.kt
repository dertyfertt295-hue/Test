@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.luma.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable fun SettingsScreen(prefs: Preferences, onChange: (Preferences) -> Unit, clearHistory: () -> Unit, account: LumaAccount?, signOut: () -> Unit, snackbar: SnackbarHostState, syncStatus: Int, syncNow: () -> Unit, syncAddress: String, onSyncAddress: (String) -> Unit, onBack: () -> Unit) {
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var signOutDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val appBar = TopAppBarDefaults.pinnedScrollBehavior()
    val tick = rememberHaptics(prefs.haptics)
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(appBar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.settings_back)) } },
                scrollBehavior = appBar,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_headline), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.settings_subtitle), Modifier.padding(top = 8.dp, bottom = 28.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                SettingsBlock(stringResource(R.string.section_account), Icons.Outlined.Person) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        val avatar = remember { PolygonShape(LumaShapes.Clover4Leaf) }
                        Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, avatar), contentAlignment = Alignment.Center) {
                            Text(account?.initial.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(account?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.account_google),
                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(account?.email.orEmpty(), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(syncStatus), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.sync_hint), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var address by remember { mutableStateOf(syncAddress) }
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it; onSyncAddress(it) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            singleLine = true,
                            label = { Text(stringResource(R.string.sync_address)) },
                            placeholder = { Text(SyncConfig.PUBLIC_URL) },
                            supportingText = { Text(stringResource(R.string.sync_address_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                            shape = MaterialTheme.shapes.medium
                        )
                        TextButton(onClick = syncNow, enabled = syncStatus != R.string.sync_working) { Text(stringResource(R.string.sync_now)) }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SettingsBlock(stringResource(R.string.section_model), Icons.Outlined.AutoAwesome, prefs.assistant.label) {
                    Spacer(Modifier.height(8.dp))
                    ModelOptions(prefs.assistant) { tick(HapticFeedbackType.SegmentTick); onChange(prefs.copy(model = it.key)) }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(24.dp))
                SettingsBlock(stringResource(R.string.section_language), Icons.Outlined.Language) {
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().selectableGroup()) {
                        LanguageRow(stringResource(R.string.language_system), prefs.language == LANGUAGE_SYSTEM) {
                            tick(HapticFeedbackType.SegmentTick); onChange(prefs.copy(language = LANGUAGE_SYSTEM))
                        }
                        LumaLanguage.values().forEach { language ->
                            LanguageRow(language.label, prefs.language == language.tag) {
                                tick(HapticFeedbackType.SegmentTick); onChange(prefs.copy(language = language.tag))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(24.dp))
                SettingsBlock(stringResource(R.string.section_appearance), Icons.Outlined.Palette) {
                    Row(Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Contrast, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.theme), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        val options = listOf(
                            Triple("dark", R.string.theme_dark, Icons.Outlined.DarkMode),
                            Triple("light", R.string.theme_light, Icons.Outlined.LightMode),
                            Triple("system", R.string.theme_system, Icons.Outlined.PhoneAndroid)
                        )
                        options.forEachIndexed { index, (key, label, icon) ->
                            SegmentedButton(
                                selected = prefs.theme == key,
                                onClick = { tick(HapticFeedbackType.SegmentTick); onChange(prefs.copy(theme = key)) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                icon = { SegmentedButtonDefaults.Icon(prefs.theme == key) { Icon(icon, null, Modifier.size(SegmentedButtonDefaults.IconSize)) } },
                                label = { Text(stringResource(label), maxLines = 1) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Palette, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.accent), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    AccentPicker(prefs, onChange, tick)
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ToggleSetting(Icons.Outlined.DarkMode, stringResource(R.string.black_background), stringResource(R.string.black_background_sub), prefs.blackBackground) {
                        onChange(prefs.copy(blackBackground = it))
                    }
                }

                Spacer(Modifier.height(24.dp))
                SettingsBlock(stringResource(R.string.section_comfort), Icons.Outlined.Tune) {
                    ToggleSetting(Icons.Outlined.ViewCompact, stringResource(R.string.compact_chat), stringResource(R.string.compact_chat_sub), prefs.compactChat) {
                        onChange(prefs.copy(compactChat = it))
                    }
                    ToggleSetting(Icons.Outlined.Animation, stringResource(R.string.animate_replies), stringResource(R.string.animate_replies_sub), prefs.animateReplies) {
                        onChange(prefs.copy(animateReplies = it))
                    }
                    ToggleSetting(Icons.Outlined.Timer, stringResource(R.string.show_response_time), stringResource(R.string.show_response_time_sub), prefs.showResponseTime) {
                        onChange(prefs.copy(showResponseTime = it))
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ToggleSetting(Icons.Outlined.TextFields, stringResource(R.string.large_text), stringResource(R.string.large_text_sub), prefs.largeText) {
                        tick(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff); onChange(prefs.copy(largeText = it))
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ToggleSetting(Icons.Outlined.Vibration, stringResource(R.string.haptics), stringResource(R.string.haptics_sub), prefs.haptics) {
                        // Only on the way in: turning haptics off should not buzz.
                        if (it) tick(HapticFeedbackType.ToggleOn)
                        onChange(prefs.copy(haptics = it))
                    }
                }

                Spacer(Modifier.height(24.dp))
                SettingsBlock(stringResource(R.string.section_data), Icons.Outlined.Storage) {
                    Row(Modifier.fillMaxWidth().clickable { clearDialog = true }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(stringResource(R.string.clear_history), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.clear_history_sub), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SettingsBlock(stringResource(R.string.about_title), Icons.Outlined.Info) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LumaMark(Modifier.size(20.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(stringResource(R.string.about_title), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(stringResource(R.string.about_body), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(28.dp))
                SettingsCard {
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { signOutDialog = true }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.sign_out), Modifier.weight(1f).padding(start = 14.dp),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
    if (clearDialog) ConfirmDelete(stringResource(R.string.clear_history_title), stringResource(R.string.clear_history_body), { clearDialog = false }) {
        clearHistory(); clearDialog = false
    }
    if (signOutDialog) AlertDialog(
        onDismissRequest = { signOutDialog = false },
        icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
        title = { Text(stringResource(R.string.sign_out_title)) },
        text = { Text(stringResource(R.string.sign_out_body)) },
        confirmButton = {
            TextButton(onClick = { signOutDialog = false; signOutOfGoogle(context); signOut() }) { Text(stringResource(R.string.sign_out_confirm)) }
        },
        dismissButton = { TextButton(onClick = { signOutDialog = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable private fun LanguageRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Shared by settings and the quick switcher under the composer. */
@Composable fun ModelOptions(selected: LumaModel, onSelect: (LumaModel) -> Unit) {
    ModelCards(selected, onSelect)
}

@Composable private fun AccentPicker(prefs: Preferences, onChange: (Preferences) -> Unit, tick: (HapticFeedbackType) -> Unit) {
    val dark = isLumaDark(prefs)
    val context = LocalContext.current
    val accents = buildList {
        add("lavender" to stringResource(R.string.accent_lavender))
        add("mint" to stringResource(R.string.accent_mint))
        add("peach" to stringResource(R.string.accent_peach))
        add("blue" to stringResource(R.string.accent_blue))
        add("rose" to stringResource(R.string.accent_rose))
        add("amber" to stringResource(R.string.accent_amber))
        add("teal" to stringResource(R.string.accent_teal))
        add("graphite" to stringResource(R.string.accent_graphite))
        if (dynamicColorSupported) add(ACCENT_DYNAMIC to stringResource(R.string.accent_dynamic))
    }
    val dynamicPreview = remember(context, dark) {
        if (dynamicColorSupported) lumaColorScheme(context, ACCENT_DYNAMIC, dark).primary else Color.Unspecified
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp).selectableGroup()) {
      accents.chunked(3).forEach { row ->
       Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        row.forEach { (key, label) ->
            val selected = prefs.accent == key
            val swatch = if (key == ACCENT_DYNAMIC) dynamicPreview else accentColor(key, dark)
            Column(
                Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                    .selectable(selected, role = Role.RadioButton, onClick = { tick(HapticFeedbackType.SegmentTick); onChange(prefs.copy(accent = key)) })
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // A selection ring with a gap, the way Material 3 marks a chosen colour.
                Box(
                    Modifier.size(48.dp)
                        .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .padding(4.dp).background(swatch, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.surface)
                    else if (key == ACCENT_DYNAMIC) Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.surface)
                }
                Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
       }
      }
    }
}

@Composable private fun SettingsBlock(title: String, icon: ImageVector, summary: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsCard {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Text(summary, Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable private fun ToggleSetting(icon: ImageVector, title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = change).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, thumbContent = {
            if (checked) Icon(Icons.Outlined.Check, null, Modifier.size(SwitchDefaults.IconSize))
        })
    }
}

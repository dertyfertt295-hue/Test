package app.luma.chat

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Message(val id: String = UUID.randomUUID().toString(), val text: String, val fromUser: Boolean, val isDemo: Boolean = false, val truncated: Boolean = false,
    /** Total request latency, including network and generation; not provider reasoning time. */
    val responseDurationMs: Long? = null, val agent: Boolean = false, val steps: List<AgentStep> = emptyList(), val photo: String? = null)
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val needsTitle: Boolean = false,
    val agent: Boolean = false,
    val photo: String? = null
)
data class Preferences(
    val theme: String = "dark",
    val accent: String = "lavender",
    val haptics: Boolean = true,
    val largeText: Boolean = false,
    val model: String = LumaModel.Default.key,
    /** "system", or a BCP-47 tag from [LumaLanguages]. */
    val language: String = LANGUAGE_SYSTEM,
    val blackBackground: Boolean = false,
    val compactChat: Boolean = false,
    val animateReplies: Boolean = true,
    val showResponseTime: Boolean = true,
    val agent: Boolean = false,
    /** An [Effort] key. */
    val effort: String = Effort.Default.key
) {
    val assistant: LumaModel get() = LumaModel.of(model)
    val effortLevel: Effort get() = Effort.of(effort)
}
data class ChatState(
    val chats: List<Conversation> = emptyList(),
    val activeId: String? = null,
    val newDraft: String = "",
    val preferences: Preferences = Preferences(),
    val newPhoto: String? = null
) {
    val active: Conversation? get() = chats.find { it.id == activeId }
    val draft: String get() = active?.draft ?: newDraft
    val photo: String? get() = if (active != null) active?.photo else newPhoto
    val agent: Boolean get() = active?.agent ?: preferences.agent
}

object StateCodec {
    fun encode(state: ChatState): String = JSONObject().apply {
        put("version", 7)
        put("activeId", state.activeId ?: JSONObject.NULL)
        put("newDraft", state.newDraft); put("newPhoto", state.newPhoto ?: JSONObject.NULL)
        put("preferences", JSONObject().apply {
            put("agent", state.preferences.agent)
            put("theme", state.preferences.theme); put("accent", state.preferences.accent)
            put("haptics", state.preferences.haptics); put("largeText", state.preferences.largeText)
            put("model", state.preferences.model); put("language", state.preferences.language)
            put("blackBackground", state.preferences.blackBackground); put("compactChat", state.preferences.compactChat)
            put("animateReplies", state.preferences.animateReplies); put("showResponseTime", state.preferences.showResponseTime)
            put("effort", state.preferences.effort)
        })
        put("chats", JSONArray().apply {
            state.chats.forEach { chat -> put(JSONObject().apply {
                put("id", chat.id); put("title", chat.title); put("draft", chat.draft); put("updatedAt", chat.updatedAt)
                put("photo", chat.photo ?: JSONObject.NULL)
                put("needsTitle", chat.needsTitle); put("agent", chat.agent)
                put("messages", JSONArray().apply { chat.messages.forEach { message -> put(JSONObject().apply {
                    put("id", message.id); put("text", message.text); put("fromUser", message.fromUser)
                    put("isDemo", message.isDemo); put("truncated", message.truncated)
                    put("responseDurationMs", message.responseDurationMs ?: JSONObject.NULL)
                    put("agent", message.agent); put("photo", message.photo ?: JSONObject.NULL)
                    put("steps", JSONArray().apply { message.steps.forEach { step ->
                        put(JSONObject().put("kind", step.kind).put("detail", step.detail).put("done", step.done))
                    } })
                }) } })
            }) }
        })
    }.toString()

    fun decode(value: String): ChatState {
        val root = JSONObject(value)
        val prefs = root.optJSONObject("preferences") ?: JSONObject()
        val chats = root.optJSONArray("chats") ?: JSONArray()
        val parsed = (0 until chats.length()).map { index ->
            val chat = chats.getJSONObject(index)
            val messages = chat.getJSONArray("messages")
            Conversation(chat.getString("id"), chat.getString("title"), (0 until messages.length()).map {
                val message = messages.getJSONObject(it)
                val fromUser = message.getBoolean("fromUser")
                Message(message.getString("id"), message.getString("text"), fromUser,
                    message.optBoolean("isDemo", root.optInt("version", 1) == 1 && !fromUser), message.optBoolean("truncated", false),
                    message.optLong("responseDurationMs", -1L).takeIf { it >= 0 }, message.optBoolean("agent", false),
                    (message.optJSONArray("steps") ?: JSONArray()).let { steps -> (0 until steps.length()).map { index ->
                        val step = steps.getJSONObject(index)
                        AgentStep(step.optString("kind"), step.optString("detail"), step.optBoolean("done", true))
                    } }, message.optString("photo").takeIf { it.startsWith("data:image/") })
            }, chat.optString("draft"), chat.optLong("updatedAt"), chat.optBoolean("needsTitle", false), chat.optBoolean("agent", false), chat.optString("photo").takeIf { it.startsWith("data:image/") })
        }
        return ChatState(parsed, root.optString("activeId").takeIf { id -> parsed.any { it.id == id } }, root.optString("newDraft"),
            Preferences(prefs.optString("theme", "dark"), prefs.optString("accent", "lavender"), prefs.optBoolean("haptics", true),
                prefs.optBoolean("largeText", false), LumaModel.of(prefs.optString("model", null)).key,
                prefs.optString("language", LANGUAGE_SYSTEM).ifBlank { LANGUAGE_SYSTEM },
                prefs.optBoolean("blackBackground", false), prefs.optBoolean("compactChat", false),
                prefs.optBoolean("animateReplies", true), prefs.optBoolean("showResponseTime", true), prefs.optBoolean("agent", false),
                Effort.of(prefs.optString("effort", null)).key), root.optString("newPhoto").takeIf { it.startsWith("data:image/") })
    }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LumaApplication
    private val initial = runCatching { app.history.load(app.accounts.current()?.id) }
    private var historyReady = initial.isSuccess
    private val _state = MutableStateFlow(initial.getOrDefault(ChatState()))
    val state = _state.asStateFlow()
    private val _error = MutableStateFlow(if (initial.isFailure) R.string.error_history_read else null)
    val error = _error.asStateFlow()
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending = _pending.asStateFlow()
    private val _agentSteps = MutableStateFlow<Map<String, List<AgentStep>>>(emptyMap())
    val agentSteps = _agentSteps.asStateFlow()
    private val _failures = MutableStateFlow<Map<String, Int>>(emptyMap())
    val failures = _failures.asStateFlow()
    private val _account = MutableStateFlow(app.accounts.current())
    val account = _account.asStateFlow()

    /**
     * The id of the answer that has just landed and has not been shown yet.
     * The UI reveals that one word by word; everything else is drawn whole, so
     * scrolling through history or restarting never replays the effect.
     */
    private val _arriving = MutableStateFlow<String?>(null)
    val arriving = _arriving.asStateFlow()
    fun revealed(id: String) { if (_arriving.value == id) _arriving.value = null }
    private val jobs = mutableMapOf<String, Job>()
    private val titleRequests = mutableSetOf<String>()
    private val syncRequests = Channel<String>(Channel.UNLIMITED)
    private val queuedSync = mutableSetOf<String>()
    private var syncDelay: Job? = null
    private var draftSave: Job? = null
    private var addressCheck: Job? = null
    private val visible = MutableStateFlow(false)
    private var accountSession = 0L
    private val _syncStatus = MutableStateFlow(R.string.sync_waiting)
    val syncStatus = _syncStatus.asStateFlow()
    private val gateway: ChatGateway = app.gateway
    init {
        // Started even when the first read failed: a later sign-in can recover
        // the history, and syncNow() itself waits for it to be readable.
        if (app.syncEnabled) {
            viewModelScope.launch {
                for (id in syncRequests) {
                    if (_account.value?.id == id) _syncStatus.value = R.string.sync_working
                    val success = try { withContext(Dispatchers.IO) { app.history.sync(id) }; true }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { false }
                    queuedSync.remove(id)
                    if (_account.value?.id == id) {
                        if (success) {
                            _state.value = app.history.load(id)
                            _syncStatus.value = if (app.history.isSaved(id)) R.string.sync_saved else R.string.sync_waiting
                        } else _syncStatus.value = R.string.sync_offline
                    }
                    if (success && !app.history.isSaved(id)) queueSync(id)
                }
            }
            viewModelScope.launch {
                // Only while the app is on screen: in the background this would
                // wake the radio every 15 seconds for as long as the process lives.
                // Changes made just before leaving still go out through syncDelay.
                visible.collectLatest { shown -> while (shown) { syncNow(); delay(15000) } }
            }
        }
    }
    private fun queueSync(id: String) { if (queuedSync.add(id)) syncRequests.trySend(id) }
    fun syncNow() { if (app.syncEnabled && historyReady) _account.value?.id?.let(::queueSync) }

    /** Whether the activity is started. Leaving the screen also writes down the draft. */
    fun visible(shown: Boolean) {
        visible.value = shown
        if (!shown) flushDraft()
    }

    /** The PC's address as typed on this phone; blank means use the ones built in. */
    val syncAddress: String get() = app.syncAddress.custom
    fun setSyncAddress(value: String) {
        app.syncAddress.custom = value
        _syncStatus.value = R.string.sync_waiting
        // Wait for typing to pause: syncing on every key would try each
        // half-typed address in turn and flash "PC unavailable" meanwhile.
        addressCheck?.cancel()
        addressCheck = viewModelScope.launch { delay(1500); syncNow() }
    }
    private fun update(durable: Boolean = true, transform: (ChatState) -> ChatState) {
        if (!historyReady) { _error.value = R.string.error_history_read; return }
        val id = _account.value?.id
        if (id == null) { _state.value = transform(_state.value); return }
        // Transformed inside the store rather than from _state, which can briefly
        // lag behind a sync that has just been written there.
        val next = try { app.history.update(id, durable, transform) }
            catch (_: Exception) { _error.value = R.string.error_save_failed; return }
        _state.value = next
        if (!durable) {
            draftSave?.cancel()
            draftSave = viewModelScope.launch { delay(600); draftSave = null; flushDraft(id) }
        }
        _syncStatus.value = R.string.sync_waiting
        syncDelay?.cancel()
        if (app.syncEnabled) syncDelay = viewModelScope.launch { delay(1200); queueSync(id) }
    }
    /** Writes a draft that so far lives only in memory to disk. */
    private fun flushDraft(id: String? = _account.value?.id) {
        draftSave?.cancel(); draftSave = null
        if (id == null) return
        try { app.history.flush(id) } catch (_: Exception) { _error.value = R.string.error_save_failed }
    }
    override fun onCleared() { flushDraft() }
    fun dismissError() { _error.value = null }
    fun signIn(account: LumaAccount) {
        val loaded = runCatching { app.history.load(account.id) }
        if (loaded.isFailure) { _error.value = R.string.error_history_read; return }
        accountSession++
        jobs.values.toList().forEach { it.cancel() }; jobs.clear()
        _agentSteps.value = emptyMap(); _pending.value = emptySet(); _failures.value = emptyMap(); _arriving.value = null
        app.accounts.save(account)
        _state.value = loaded.getOrThrow()
        historyReady = true
        _account.value = account
        _syncStatus.value = R.string.sync_waiting
        syncNow()
    }
    fun signOut() {
        flushDraft()
        syncNow()
        accountSession++
        jobs.keys.toList().forEach(::stop)
        app.accounts.clear()
        _account.value = null
        _state.value = ChatState(preferences = _state.value.preferences)
        _arriving.value = null
        _failures.value = emptyMap()
    }
    fun reportSignInFailure(@StringRes message: Int) { _error.value = message }
    fun draft(text: String) = update(durable = false) { state ->
        if (state.active == null) state.copy(newDraft = text)
        else state.copy(chats = state.chats.map { if (it.id == state.activeId) it.copy(draft = text) else it })
    }
    fun agent(enabled: Boolean) {
        if (_state.value.activeId in _pending.value) return
        update { state -> state.copy(preferences = state.preferences.copy(agent = enabled),
            chats = state.chats.map { if (it.id == state.activeId) it.copy(agent = enabled) else it }) }
    }
    fun photo(value: String?) = update { state ->
        if (state.active == null) state.copy(newPhoto = value)
        else state.copy(chats = state.chats.map { if (it.id == state.activeId) it.copy(photo = value) else it })
    }
    fun newChat() = update { it.copy(activeId = null) }
    fun open(id: String) = update { it.copy(activeId = id) }
    fun settings(preferences: Preferences) = update { it.copy(preferences = preferences) }
    /** Applies to the next request, like a change of model; answers already given stay as they are. */
    fun effort(level: Effort) {
        if (_state.value.preferences.effort != level.key) update { it.copy(preferences = it.preferences.copy(effort = level.key)) }
    }
    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        update { state -> state.copy(chats = state.chats.map { if (it.id == id) it.copy(title = name.trim().take(80), needsTitle = false) else it }) }
    }
    fun delete(id: String) {
        stop(id)
        _failures.value -= id
        update { it.copy(chats = it.chats.filterNot { chat -> chat.id == id }, activeId = it.activeId.takeUnless { active -> active == id }) }
    }
    fun clearHistory() {
        jobs.values.toList().forEach { it.cancel() }; jobs.clear(); _pending.value = emptySet(); _agentSteps.value = emptyMap()
        _failures.value = emptyMap()
        update { it.copy(chats = emptyList(), activeId = null, newDraft = "", newPhoto = null) }
    }
    fun stop(id: String) {
        jobs.remove(id)?.cancel(); _pending.value -= id; _agentSteps.value -= id
        _failures.value += (id to R.string.error_stopped)
    }
    /**
     * [photoTitle] names a chat that opens with a photo alone. The screen passes
     * it in, since only the screen knows the language picked in settings.
     */
    fun send(photoTitle: String = getApplication<Application>().getString(R.string.photo_attached)) {
        val current = _state.value
        val text = current.draft.trim()
        if ((text.isEmpty() && current.photo == null) || current.activeId in _pending.value) return
        if (_account.value == null) { _error.value = R.string.signin_required; return }
        val chat = current.active ?: Conversation(title = text.ifBlank { photoTitle }.replace(Regex("\\s+"), " ").take(48), needsTitle = true, agent = current.agent)
        val next = chat.copy(messages = chat.messages + Message(text = text, fromUser = true, photo = current.photo), draft = "", photo = null, updatedAt = System.currentTimeMillis())
        update { it.copy(chats = listOf(next) + it.chats.filterNot { c -> c.id == next.id }, activeId = next.id, newDraft = if (current.active == null) "" else it.newDraft, newPhoto = if (current.active == null) null else it.newPhoto) }
        requestReply(next)
    }
    fun retry() {
        val chat = _state.value.active ?: return
        if (_account.value == null) { _error.value = R.string.signin_required; return }
        if (chat.id !in _pending.value && chat.messages.lastOrNull()?.fromUser == true) requestReply(chat)
    }
    /**
     * Replaces the placeholder name — the trimmed first message — with one the
     * model writes.
     *
     * The name is only applied if it is still the placeholder, which is what
     * keeps a rename made while this was in flight from being overwritten. A
     * failure is swallowed on purpose: the fallback name is already reasonable,
     * and a missing title is not worth an error in front of the person.
     */
    private fun requestTitle(id: String, placeholder: String) {
        if (!titleRequests.add(id)) return
        val session = accountSession
        viewModelScope.launch {
            try {
            val chat = _state.value.chats.firstOrNull { it.id == id && it.needsTitle } ?: return@launch
            val generated = try { gateway.title(chat.messages) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { return@launch }
            if (generated.isBlank() || session != accountSession) return@launch
            update { state -> state.copy(chats = state.chats.map {
                if (it.id == id && it.needsTitle && it.title == placeholder) it.copy(title = generated, needsTitle = false) else it
            }) }
            } finally { titleRequests.remove(id) }
        }
    }

    private fun requestReply(chat: Conversation) {
        val session = accountSession
        _pending.value += chat.id
        _failures.value -= chat.id
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val startedAt = android.os.SystemClock.elapsedRealtime()
                val response = if (chat.agent) gateway.agent(chat.messages) { steps ->
                    if (session == accountSession && chat.id in _pending.value) _agentSteps.value += (chat.id to steps)
                } else _state.value.preferences.let { gateway.reply(chat.messages, it.assistant, it.effortLevel) }
                val durationMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0)
                coroutineContext.ensureActive()
                if (session != accountSession) return@launch
                val answer = Message(text = response.text, fromUser = false, truncated = response.truncated, responseDurationMs = durationMs, agent = chat.agent, steps = response.steps)
                _arriving.value = answer.id
                update { state -> state.copy(chats = state.chats.map {
                    if (it.id == chat.id) it.copy(messages = it.messages + answer) else it
                }) }
                // A failed naming request can be retried after the next reply.
                requestTitle(chat.id, chat.title)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _failures.value += (chat.id to (if (e is ChatFailure) e.messageRes else R.string.error_generic)) }
            finally {
                if (jobs[chat.id] == coroutineContext[Job]) { _pending.value -= chat.id; _agentSteps.value -= chat.id; jobs.remove(chat.id) }
            }
        }
        jobs[chat.id] = job
        job.start()
    }
}

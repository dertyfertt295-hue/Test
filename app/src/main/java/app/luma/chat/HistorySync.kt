package app.luma.chat

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal fun accountKey(id: String): String = MessageDigest.getInstance("SHA-256")
    .digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal data class RemoteHistory(val revision: Long, val state: ChatState)
internal class RevisionConflict : IOException("revision_conflict")
internal interface HistoryTransport {
    fun get(account: String): RemoteHistory
    fun put(account: String, revision: Long, state: ChatState): RemoteHistory
}

/** The private sync client trusts only this PC's certificate; chat API TLS is unchanged. */
internal class PcHistoryTransport(context: Context, private val addresses: () -> List<String>) : HistoryTransport {
    constructor(context: Context, urls: List<String>) : this(context, { urls })

    private val client: OkHttpClient
    private var lastUrl: String? = null
    init {
        val certificate = context.resources.openRawResource(R.raw.sync_server).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("pc", certificate) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            .trustManagers.single() as X509TrustManager
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        client = OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust)
            // The trust store holds exactly one certificate — this PC's. Nothing
            // else can complete the handshake, whatever address it is reached at,
            // so checking the name in the certificate would add no protection and
            // would instead break the moment the home IP changes.
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
    override fun get(account: String) = exchange(account, null)
    override fun put(account: String, revision: Long, state: ChatState) = exchange(account,
        JSONObject().put("revision", revision).put("state", JSONObject(StateCodec.encode(state))).toString())
    private fun exchange(account: String, body: String?): RemoteHistory {
        var failure: IOException? = null
        val candidates = addresses()
        if (candidates.isEmpty()) throw IOException("sync_no_address")
        // Whichever address answered last is tried first, so the common case is one request.
        for (url in (listOfNotNull(lastUrl) + candidates).distinct()) {
            try {
                val request = Request.Builder().url("$url/v1/accounts/${accountKey(account)}/state")
                    .header("Authorization", "Bearer ${SyncConfig.TOKEN}")
                if (body != null) request.put(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                client.newCall(request.build()).execute().use { response ->
                    if (response.code == 409) throw RevisionConflict()
                    if (!response.isSuccessful) throw IOException("sync_http_${response.code}")
                    val json = JSONObject(response.body!!.string())
                    lastUrl = url
                    return RemoteHistory(json.getLong("revision"), json.optJSONObject("state")?.let { StateCodec.decode(it.toString()) } ?: ChatState())
                }
            } catch (e: RevisionConflict) { throw e }
            catch (e: IOException) { failure = e }
        }
        throw failure ?: IOException("sync_offline")
    }
}

/** Apply local changes since base to remote. An unchanged empty install never deletes remote history. */
internal fun mergeHistory(base: ChatState, local: ChatState, remote: ChatState): ChatState {
    val old = base.chats.associateBy { it.id }
    val mine = local.chats.associateBy { it.id }
    val theirs = remote.chats.associateBy { it.id }
    val chats = (theirs.keys + mine.keys).mapNotNull { id ->
        val b = old[id]; val l = mine[id]; val r = theirs[id]
        when {
            b != null && (l == null || r == null) -> null // Explicit deletion wins over stale copies.
            l == null -> r
            r == null -> l
            l == b -> r
            else -> {
                val messages = (r.messages + l.messages).distinctBy { it.id }
                r.copy(title = if (l.title != b?.title) l.title else r.title,
                    needsTitle = if (l.needsTitle != b?.needsTitle) l.needsTitle else r.needsTitle,
                    photo = if (l.photo != b?.photo) l.photo else r.photo,
                    agent = if (l.agent != b?.agent) l.agent else r.agent,
                    draft = if (l.draft != b?.draft) l.draft else r.draft,
                    messages = messages, updatedAt = maxOf(l.updatedAt, r.updatedAt))
            }
        }
    }.sortedByDescending { it.updatedAt }
    val b = base.preferences; val l = local.preferences; val r = remote.preferences
    val prefs = Preferences(
        if (l.theme != b.theme) l.theme else r.theme,
        if (l.accent != b.accent) l.accent else r.accent,
        if (l.haptics != b.haptics) l.haptics else r.haptics,
        if (l.largeText != b.largeText) l.largeText else r.largeText,
        if (l.model != b.model) l.model else r.model,
        if (l.language != b.language) l.language else r.language,
        if (l.blackBackground != b.blackBackground) l.blackBackground else r.blackBackground,
        if (l.compactChat != b.compactChat) l.compactChat else r.compactChat,
        if (l.animateReplies != b.animateReplies) l.animateReplies else r.animateReplies,
        if (l.showResponseTime != b.showResponseTime) l.showResponseTime else r.showResponseTime,
        if (l.agent != b.agent) l.agent else r.agent,
        if (l.effort != b.effort) l.effort else r.effort)
    val active = if (local.activeId != base.activeId) local.activeId else remote.activeId
    return ChatState(chats, active?.takeIf { id -> chats.any { it.id == id } },
        if (local.newDraft != base.newDraft) local.newDraft else remote.newDraft, prefs,
        if (local.newPhoto != base.newPhoto) local.newPhoto else remote.newPhoto)
}

internal data class HistoryEntry(val state: ChatState, val base: ChatState = ChatState(), val lastSaved: Long = 0)

/** Local cache/outbox is partitioned by the stable Google account id, just like the server. */
internal class AccountHistory(context: Context, private val transport: HistoryTransport?) {
    private val prefs = context.getSharedPreferences("luma_account_history", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("luma_local", Context.MODE_PRIVATE)
    private val entries = mutableMapOf<String, HistoryEntry>()
    /** Accounts whose entry has changes held only in memory; see [update]. */
    private val unsaved = mutableSetOf<String>()
    private val syncLock = Any()

    @Synchronized private fun entry(id: String): HistoryEntry = entries.getOrPut(id) {
        val json = prefs.getString(accountKey(id), null)?.let(::JSONObject)
        if (json != null) HistoryEntry(StateCodec.decode(json.getJSONObject("state").toString()),
            StateCodec.decode(json.getJSONObject("base").toString()), json.optLong("lastSaved", 0))
        else HistoryEntry(ChatState())
    }
    @Synchronized fun load(id: String?): ChatState {
        if (id == null) return ChatState()
        // Assign pre-sync history once only, to the account using the app at migration.
        if (!prefs.getBoolean("legacyClaimed", false)) {
            val old = legacy.getString("state", null)?.let(StateCodec::decode)
            if (old != null && prefs.getString(accountKey(id), null) == null) persist(id, HistoryEntry(old))
            if (!prefs.edit().putBoolean("legacyClaimed", true).commit()) throw IOException("save_failed")
        }
        return entry(id).state
    }
    @Synchronized fun save(id: String, state: ChatState) { persist(id, entry(id).copy(state = state)) }

    /**
     * Applies [transform] to the stored state under the lock a sync takes to
     * write its result. An edit computed from a copy taken before the sync landed
     * would silently drop what the sync brought in — and the next sync would then
     * read those chats as deleted here and delete them on the PC as well.
     *
     * With [durable] false the change stays in memory until [flush] or the next
     * durable write: a draft should not rewrite the whole history on every key.
     */
    @Synchronized fun update(id: String, durable: Boolean = true, transform: (ChatState) -> ChatState): ChatState {
        val current = entry(id)
        val next = current.copy(state = transform(current.state))
        if (durable) persist(id, next) else { entries[id] = next; unsaved += id }
        return next.state
    }
    @Synchronized fun flush(id: String) { if (id in unsaved) persist(id, entry(id)) }
    @Synchronized fun isSaved(id: String): Boolean = entry(id).let { it.lastSaved > 0 && it.state == it.base }
    @Synchronized fun lastSaved(id: String): Long = entry(id).lastSaved
    @Synchronized private fun persist(id: String, value: HistoryEntry) {
        // Spliced as text: parsing each half back into a JSONObject only to
        // print it again cost every save two extra passes over the history.
        val json = """{"state":${StateCodec.encode(value.state)},"base":${StateCodec.encode(value.base)},"lastSaved":${value.lastSaved}}"""
        if (!prefs.edit().putString(accountKey(id), json).commit()) throw IOException("save_failed")
        entries[id] = value
        unsaved -= id
    }
    fun sync(id: String) = synchronized(syncLock) {
        val api = transport ?: return@synchronized
        repeat(4) {
            val remote = api.get(id)
            val snapshot = synchronized(this) { entry(id) }
            val combined = mergeHistory(snapshot.base, snapshot.state, remote.state)
            val confirmed = try {
                if (combined == remote.state) remote else api.put(id, remote.revision, combined)
            } catch (_: RevisionConflict) { return@repeat }
            synchronized(this) {
                // Preserve edits typed while the network request was in flight.
                val current = entry(id)
                val latest = mergeHistory(snapshot.state, current.state, confirmed.state)
                persist(id, HistoryEntry(latest, confirmed.state, System.currentTimeMillis()))
            }
            return@synchronized
        }
        throw IOException("sync_conflict_retry")
    }
}

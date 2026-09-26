package app.luma.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = TestLumaApplication::class, sdk = [35])
class HistorySyncTest {
    private lateinit var context: Context
    private class FakeServer : HistoryTransport {
        val accounts = mutableMapOf<String, RemoteHistory>()
        var offline = false
        var onPut: (() -> Unit)? = null
        override fun get(account: String): RemoteHistory {
            if (offline) throw IOException("offline")
            return accounts[account] ?: RemoteHistory(0, ChatState())
        }
        override fun put(account: String, revision: Long, state: ChatState): RemoteHistory {
            if (get(account).revision != revision) throw RevisionConflict()
            onPut?.invoke(); onPut = null
            return RemoteHistory(revision + 1, state).also { accounts[account] = it }
        }
    }
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("luma_account_history", 0).edit().clear().commit()
        context.getSharedPreferences("luma_local", 0).edit().clear().commit()
    }
    private fun sample() = ChatState(chats = listOf(Conversation(id = "c", title = "Тема", updatedAt = 1000, messages = listOf(
        Message(id = "m", text = "Привет", fromUser = true), Message(id = "r", text = "Ответ", fromUser = false, responseDurationMs = 3200)))),
        preferences = Preferences(theme = "light"), newDraft = "черновик")
    @Test fun freshInstallRestoresHistoryPreferencesAndTimingsWithoutUploadingEmptyState() {
        val server = FakeServer(); val first = AccountHistory(context, server)
        first.load("alice"); first.save("alice", sample()); first.sync("alice")
        assertTrue(first.isSaved("alice"))
        context.getSharedPreferences("luma_account_history", 0).edit().clear().commit()
        val reinstalled = AccountHistory(context, server)
        assertTrue(reinstalled.load("alice").chats.isEmpty())
        reinstalled.sync("alice")
        assertEquals(sample(), reinstalled.load("alice"))
        assertEquals(1L, server.accounts["alice"]!!.revision)
    }
    @Test fun accountsAreIsolatedAndLegacyHistoryIsClaimedOnce() {
        context.getSharedPreferences("luma_local", 0).edit().putString("state", StateCodec.encode(sample())).commit()
        val server = FakeServer(); val repo = AccountHistory(context, server)
        assertEquals(sample(), repo.load("alice")); repo.sync("alice")
        assertTrue(repo.load("bob").chats.isEmpty()); repo.sync("bob")
        assertEquals(sample(), repo.load("alice"))
        assertTrue(server.get("bob").state.chats.isEmpty())
    }
    @Test fun offlineEditsSurviveRestartAndDeletionDoesNotResurrect() {
        val server = FakeServer(); val repo = AccountHistory(context, server)
        repo.load("alice"); repo.save("alice", sample()); repo.sync("alice")
        server.offline = true
        repo.save("alice", sample().copy(chats = emptyList()))
        try { repo.sync("alice"); fail() } catch (_: IOException) { }
        assertFalse(repo.isSaved("alice"))
        val restarted = AccountHistory(context, server)
        server.offline = false; restarted.sync("alice")
        assertTrue(server.get("alice").state.chats.isEmpty())
    }
    @Test fun typingDuringUploadIsNotOverwritten() {
        val server = FakeServer(); val repo = AccountHistory(context, server)
        repo.load("alice"); repo.save("alice", sample())
        server.onPut = { repo.save("alice", sample().copy(newDraft = "набрано во время запроса")) }
        repo.sync("alice")
        assertEquals("набрано во время запроса", repo.load("alice").newDraft)
        assertFalse(repo.isSaved("alice")); repo.sync("alice"); assertTrue(repo.isSaved("alice"))
    }
    @Test fun anEditAfterASyncBuildsOnWhatTheSyncBroughtIn() {
        val server = FakeServer(); server.accounts["alice"] = RemoteHistory(1, sample())
        val repo = AccountHistory(context, server)
        assertTrue(repo.load("alice").chats.isEmpty())
        repo.sync("alice")
        repo.update("alice") { it.copy(newDraft = "новое") }
        repo.sync("alice")
        // Had the edit been made to the empty copy, this sync would have read the
        // restored chat as deleted here and deleted it on the PC too.
        assertEquals(listOf("c"), server.get("alice").state.chats.map { it.id })
        assertEquals("новое", server.get("alice").state.newDraft)
    }
    @Test fun aDraftWaitsInMemoryUntilFlushedAndASyncCarriesIt() {
        val server = FakeServer(); val repo = AccountHistory(context, server)
        repo.load("alice")
        repo.update("alice", durable = false) { it.copy(newDraft = "пишу") }
        assertEquals("пишу", repo.load("alice").newDraft)
        // A second store sees only what reached the disk, like the app after a restart.
        assertEquals("", AccountHistory(context, server).load("alice").newDraft)
        repo.flush("alice")
        assertEquals("пишу", AccountHistory(context, server).load("alice").newDraft)
        repo.update("alice", durable = false) { it.copy(newDraft = "пишу дальше") }
        repo.sync("alice")
        assertEquals("пишу дальше", server.get("alice").state.newDraft)
        assertEquals("пишу дальше", AccountHistory(context, server).load("alice").newDraft)
    }
    @Test fun aTypedAddressIsCompletedAndTriedBeforeTheBuiltInOnes() {
        assertEquals("https://1.2.3.4:8443", SyncAddress.normalise("1.2.3.4"))
        assertEquals("https://1.2.3.4:9000", SyncAddress.normalise("1.2.3.4:9000"))
        assertEquals("https://pc.example.com:8443", SyncAddress.normalise("  pc.example.com/ "))
        assertEquals("https://1.2.3.4:8443", SyncAddress.normalise("https://1.2.3.4:8443"))
        assertEquals("http://1.2.3.4:8443", SyncAddress.normalise("http://1.2.3.4"))
        assertEquals("", SyncAddress.normalise("   "))

        val address = SyncAddress(context)
        assertEquals(listOf(SyncConfig.PUBLIC_URL, SyncConfig.LAN_URL), address.urls())
        address.custom = "10.0.0.7"
        // A correction has to be tried first, or a stale built-in address would
        // keep answering — or keep timing out — ahead of it.
        assertEquals(listOf("https://10.0.0.7:8443", SyncConfig.PUBLIC_URL, SyncConfig.LAN_URL), address.urls())
        assertEquals("https://10.0.0.7:8443", SyncAddress(context).custom)
        address.custom = ""
        assertEquals(listOf(SyncConfig.PUBLIC_URL, SyncConfig.LAN_URL), address.urls())
    }
    @Test fun stalePhoneDoesNotReviveDeletedChatAndKeepsUnrelatedNewChats() {
        val base = sample()
        val local = base.copy(chats = base.chats + Conversation(id = "new", title = "Новый"))
        val merged = mergeHistory(base, local, base.copy(chats = emptyList()))
        assertEquals(listOf("new"), merged.chats.map { it.id })
    }
}

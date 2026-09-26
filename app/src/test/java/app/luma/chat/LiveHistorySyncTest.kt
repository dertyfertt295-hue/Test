package app.luma.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Opt-in contract test against the real Node HTTPS server, with isolated scratch data. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestLumaApplication::class, sdk = [35])
class LiveHistorySyncTest {
    @Test fun pinnedTlsClientUploadsAndRestoresFromRealServer() {
        assumeTrue(System.getenv("ISKRA_LIVE_SYNC_TEST") == "1")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val api = PcHistoryTransport(context, listOf("https://127.0.0.1:18443"))
        val id = "integration-" + UUID.randomUUID()
        val first = AccountHistory(context, api)
        first.load(id)
        val state = ChatState(chats = listOf(Conversation(title = "Настоящая синхронизация", messages = listOf(
            Message(text = "Проверка HTTPS", fromUser = true), Message(text = "Ответ", fromUser = false, responseDurationMs = 1234)))),
            preferences = Preferences(theme = "light"))
        first.save(id, state); first.sync(id)
        assertTrue(first.isSaved(id))
        context.getSharedPreferences("luma_account_history", 0).edit().clear().commit()
        val reinstalled = AccountHistory(context, api)
        reinstalled.load(id); reinstalled.sync(id)
        assertEquals(state, reinstalled.load(id))
        assertTrue(api.get("other-" + UUID.randomUUID()).state.chats.isEmpty())
    }
}

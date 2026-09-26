package app.luma.chat

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class O5AgentTest {
    private fun reply(text: String) = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", text))))
    private fun call(name: String, input: String) = JSONObject().put("choices", JSONArray().put(JSONObject().put("message",
        JSONObject().put("tool_calls", JSONArray().put(JSONObject().put("id", "call_1").put("type", "function")
            .put("function", JSONObject().put("name", name).put("arguments", JSONObject().put(if (name == "web_search") "query" else "url", input).toString())))))))

    @Test fun searchAndReadFeedEvidenceBackAndSaveOnlyActions() = runBlocking {
        val requests = mutableListOf<JSONObject>()
        val events = mutableListOf<List<AgentStep>>()
        val answers = ArrayDeque(listOf(call("web_search", "Android Material 3"), reply("Source https://developer.android.com"),
            call("read_page", "https://developer.android.com"), reply("Page evidence"), reply("Answer [source](https://developer.android.com)")))
        val result = O5Agent { body -> requests += JSONObject(body.toString()); answers.removeFirst() }
            .run(listOf(Message(text = "Research", fromUser = true))) { events += it }
        assertEquals(5, requests.size)
        assertTrue(requests.all { it.getString("model") == LumaModel.A45.endpoint })
        assertEquals("web", requests[1].getJSONArray("plugins").getJSONObject(0).getString("id"))
        assertEquals("openrouter:web_fetch", requests[3].getJSONArray("tools").getJSONObject(0).getString("type"))
        assertTrue(requests.last().getJSONArray("messages").toString().contains("Page evidence"))
        assertTrue(result.steps.all { it.done })
        assertTrue(events.any { it.last().kind == "search" && !it.last().done })
        assertTrue(result.text.contains("[source]"))
    }

    @Test fun greetingsNeedNoSearchAndOldHistoryStaysCompatible() = runBlocking {
        var count = 0
        val result = O5Agent { count++; reply("Hello") }.run(emptyList()) {}
        assertEquals(1, count)
        assertEquals("Hello", result.text)
        val chat = Conversation(title = "O5", agent = true, messages = listOf(Message(text = result.text, fromUser = false, agent = true, steps = result.steps)))
        val state = ChatState(chats = listOf(chat), activeId = chat.id, preferences = Preferences(agent = true))
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
        assertFalse(StateCodec.decode("""{"chats":[],"preferences":{}}""").agent)
    }

    @Test fun stopsAtBudgetAndCancellationStopsTheLoop() = runBlocking {
        var searches = 0
        val result = O5Agent { body ->
            when {
                body.has("plugins") -> { searches++; reply("evidence") }
                body.has("tools") -> call("web_search", "test")
                else -> reply("Partial result")
            }
        }.run(emptyList()) {}
        assertEquals(5, searches)
        assertEquals("Partial result", result.text)
        val began = CompletableDeferred<Unit>()
        var cancelled = false
        val job = launch { O5Agent { began.complete(Unit); try { awaitCancellation() } finally { cancelled = true } }.run(emptyList()) {} }
        began.await(); job.cancelAndJoin()
        assertTrue(cancelled)
    }

    @Test fun failedResearchIsEvidenceOfFailureAndUnsafeUrlsAreRejected() = runBlocking {
        var count = 0
        val result = O5Agent {
            when (++count) {
                1 -> call("web_search", "test")
                2 -> throw ChatFailure(R.string.error_service)
                else -> { assertTrue(it.toString().contains("Research failed")); reply("Could not verify") }
            }
        }.run(emptyList()) {}
        assertTrue(result.steps.any { it.kind == "failed" })
        listOf("file:///secret", "http://example.com", "https://127.0.0.1", "https://localhost", "https://user:pass@example.com").forEach { assertFalse(O5Agent.publicUrl(it)) }
    }
}

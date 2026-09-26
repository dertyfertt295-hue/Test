package app.luma.chat

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenRouterGatewayTest {
    @Test fun sendsExactModelHistoryAndBearerToEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Привет!"},"finish_reason":"stop"}]}"""))
            server.start()
            val gateway = OpenRouterGateway(MemoryCredentials(), endpoint = server.url("/chat/completions").toString())
            val result = gateway.reply(listOf(Message(text = "Привет", fromUser = true), Message(text = "Старый пример", fromUser = false, isDemo = true), Message(text = "Настоящий ответ", fromUser = false), Message(text = "Продолжим", fromUser = true)), LumaModel.V441)
            assertEquals("Привет!", result.text)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("Bearer sk-or-test-not-a-real-secret", request.getHeader("Authorization"))
            val json = JSONObject(request.body.readUtf8())
            assertEquals(LumaModel.V441.endpoint, json.getString("model"))
            assertEquals(4, json.getJSONArray("messages").length())
            assertEquals("system", json.getJSONArray("messages").getJSONObject(0).getString("role"))
            assertEquals("assistant", json.getJSONArray("messages").getJSONObject(2).getString("role"))
            assertFalse(json.getBoolean("stream"))
        }
    }
    @Test fun missingCredentialDoesNotMakeRequest() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val gateway = OpenRouterGateway(MemoryCredentials(null), endpoint = server.url("/").toString())
            try { gateway.reply(emptyList(), LumaModel.Default); fail("Expected credential error") } catch (e: ChatFailure) { assertEquals(R.string.error_not_configured, e.messageRes) }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun authRateLimitAndProviderErrorsAreSafeAndUseful() {
        listOf(401 to R.string.error_unauthorized, 429 to R.string.error_rate_limit, 503 to R.string.error_service).forEach { (code, expected) ->
            try { OpenRouterGateway.decode(code, """{"error":{"code":$code,"message":"private-provider-debug"}}"""); fail("Expected error") }
            catch (e: ChatFailure) { assertEquals(expected, e.messageRes) }
        }
    }
    @Test fun errorsInsideSuccessfulHttpResponseAreHandled() {
        try { OpenRouterGateway.decode(200, """{"error":{"code":429}}"""); fail("Expected error") }
        catch (e: ChatFailure) { assertEquals(R.string.error_rate_limit, e.messageRes) }
    }
    @Test fun emptyResponseIsAnErrorAndTruncationIsPreserved() {
        try { OpenRouterGateway.decode(200, """{"choices":[{"message":{"content":""}}]}"""); fail("Expected error") }
        catch (e: ChatFailure) { assertEquals(R.string.error_empty, e.messageRes) }
        assertTrue(OpenRouterGateway.decode(200, """{"choices":[{"message":{"content":"Часть"},"finish_reason":"length"}]}""").truncated)
    }
    @Test fun cancellationStopsAnInFlightRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); server.start()
            val client = OkHttpClient()
            val gateway = OpenRouterGateway(MemoryCredentials(), client, server.url("/").toString())
            val job = launch { gateway.reply(listOf(Message(text = "Hi", fromUser = true)), LumaModel.Default) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            job.cancelAndJoin()
            withTimeout(5000) { while (client.dispatcher.runningCallsCount() != 0) delay(20) }
            assertTrue(job.isCancelled)
        }
    }
    @Test fun everyAssistantKeepsItsOwnRouteAndNoneIsShared() {
        val routes = LumaModel.values().map { it.endpoint }
        assertEquals(routes.size, routes.toSet().size)
        LumaModel.values().forEach { model ->
            assertEquals(model.endpoint, OpenRouterGateway.payload(emptyList(), model).getString("model"))
        }
    }
    @Test fun switchingModelsUpdatesIdentityAndRouteDespiteEarlierIdentityInHistory() {
        val history = listOf(Message(text = "Я Iskra AI — Adrenaline 26.4.5 (A-4.5)", fromUser = false),
            Message(text = "А сейчас какая модель?", fromUser = true))
        val names = mapOf(LumaModel.A45 to "Adrenaline 26.4.5 (A-4.5)",
            LumaModel.V441 to "Velocine 26.4.4.1 (V-4.4.1)", LumaModel.S45 to "Symerine 26.4.5 (S-4.5)")
        names.forEach { (model, name) ->
            val payload = OpenRouterGateway.payload(history, model)
            assertEquals(model.endpoint, payload.getString("model"))
            val messages = payload.getJSONArray("messages")
            val system = messages.getJSONObject(0).getString("content")
            assertTrue(system.contains(ISKRA_REFERENCE))
            val selection = system.substringAfter("CURRENT SELECTION FOR THIS RESPONSE")
            assertTrue(selection.contains(name))
            names.filterKeys { it != model }.values.forEach { assertFalse(selection.contains(it)) }
            assertEquals(history.first().text, messages.getJSONObject(1).getString("content"))
        }
    }
    @Test fun everyRequestOpensWithTheIskraPersona() {
        val messages = OpenRouterGateway.payload(listOf(Message(text = "Привет", fromUser = true))).getJSONArray("messages")
        val system = messages.getJSONObject(0)
        assertEquals("system", system.getString("role"))
        assertTrue(system.getString("content").contains("Iskra AI"))
        // The persona hides the plumbing but never claims to be a person.
        assertTrue(system.getString("content").contains("artificial intelligence"))
    }
    @Test fun titlesAreAskedOfTheLightRouteWithOnlyTheOpeningExchange() {
        val history = listOf(
            Message(text = "Вопрос", fromUser = true),
            Message(text = "Ответ", fromUser = false),
            Message(text = "Ещё вопрос", fromUser = true)
        )
        val payload = OpenRouterGateway.titlePayload(history)
        assertEquals(TitleModel.endpoint, payload.getString("model"))
        assertEquals(128, payload.getInt("max_tokens"))
        val messages = payload.getJSONArray("messages")
        // End with a naming instruction, not an assistant message to continue.
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        val instruction = messages.getJSONObject(1).getString("content")
        assertTrue(instruction.contains("Вопрос"))
        assertTrue(instruction.contains("Ответ"))
        assertFalse(instruction.contains("Ещё вопрос"))
    }
    @Test fun pendingTitleSurvivesSavingAndLegacyNamesArePreserved() {
        val chat = Conversation(title = "Вопрос", needsTitle = true)
        assertTrue(StateCodec.decode(StateCodec.encode(ChatState(chats = listOf(chat)))).chats.single().needsTitle)
        val legacy = """{"version":3,"chats":[{"id":"c","title":"Моё имя","messages":[]}]}"""
        assertFalse(StateCodec.decode(legacy).chats.single().needsTitle)
    }
    @Test fun titlesAreStrippedOfTheDressingModelsAddThem() {
        assertEquals("Идея в привычку", OpenRouterGateway.cleanTitle("\"Идея в привычку\""))
        assertEquals("Идея в привычку", OpenRouterGateway.cleanTitle("Title: Идея в привычку."))
        assertEquals("Идея в привычку", OpenRouterGateway.cleanTitle("Название: «Идея в привычку»"))
        assertEquals("Morning tea habit", OpenRouterGateway.cleanTitle("**Morning tea habit**\nA conversation about habits"))
        assertEquals("Una buena idea", OpenRouterGateway.cleanTitle("  Título:  Una   buena  idea ,"))
        // A question mark belongs in a name, a full stop does not.
        assertEquals("Как начать?", OpenRouterGateway.cleanTitle("Как начать?"))
        assertEquals("", OpenRouterGateway.cleanTitle("   \n  "))
        assertTrue(OpenRouterGateway.cleanTitle("слово ".repeat(40)).length <= 48)
    }
    @Test fun effortSetsTheReasoningRoomOnlyWhereTheRouteCanThink() {
        val instant = OpenRouterGateway.payload(emptyList(), LumaModel.S45, Effort.Instant)
        assertFalse(instant.getJSONObject("reasoning").getBoolean("enabled"))
        assertEquals(4096, instant.getInt("max_tokens"))
        mapOf(Effort.Medium to "medium", Effort.High to "high", Effort.ExtraHigh to "xhigh", Effort.Ultra to "max").forEach { (effort, api) ->
            val payload = OpenRouterGateway.payload(emptyList(), LumaModel.S45, effort)
            assertEquals(api, payload.getJSONObject("reasoning").getString("effort"))
            // Spent, never returned: no private reasoning reaches the history.
            assertTrue(payload.getJSONObject("reasoning").getBoolean("exclude"))
            assertEquals(effort.maxTokens, payload.getInt("max_tokens"))
            assertTrue(payload.getJSONArray("messages").getJSONObject(0).getString("content")
                .contains("reasoning effort set in the app for this response is ${effort.label}."))
        }
        // Thinking and answer share one limit, so every step up gets more room and more time.
        assertEquals(Effort.values().toList(), Effort.values().sortedBy { it.maxTokens })
        assertEquals(Effort.values().toList(), Effort.values().sortedBy { it.waitSeconds })
        // A route that cannot reason is sent exactly what it was sent before levels existed.
        val light = OpenRouterGateway.payload(emptyList(), LumaModel.A45, Effort.Ultra)
        assertFalse(light.getJSONObject("reasoning").getBoolean("enabled"))
        assertEquals(4096, light.getInt("max_tokens"))
        assertTrue(light.getJSONArray("messages").getJSONObject(0).getString("content").contains("for this response is Instant."))
    }
    @Test fun thinkingThatSpendsTheWholeLimitSaysSo() {
        try {
            OpenRouterGateway.decode(200, """{"choices":[{"message":{"content":""},"finish_reason":"length"}],"usage":{"completion_tokens_details":{"reasoning_tokens":8000}}}""")
            fail("Expected error")
        } catch (e: ChatFailure) { assertEquals(R.string.error_thinking_limit, e.messageRes) }
    }
    @Test fun deeperEffortWaitsLongerThanAnInstantAnswer() = runBlocking {
        MockWebServer().use { server ->
            val slow = { MockResponse().setBody("""{"choices":[{"message":{"content":"Готово"}}]}""").setHeadersDelay(1500, TimeUnit.MILLISECONDS) }
            server.enqueue(slow()); server.enqueue(slow()); server.start()
            val client = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).callTimeout(1, TimeUnit.SECONDS).build()
            val gateway = OpenRouterGateway(MemoryCredentials(), client, server.url("/chat/completions").toString())
            val question = listOf(Message(text = "Подумай", fromUser = true))
            try { gateway.reply(question, LumaModel.S45, Effort.Instant); fail("Expected timeout") }
            catch (e: ChatFailure) { assertEquals(R.string.error_slow, e.messageRes) }
            assertEquals("Готово", gateway.reply(question, LumaModel.S45, Effort.Medium).text)
        }
    }
    @Test fun versionOneDemoMessagesAreMigratedAndExcluded() {
        val legacy = """{"version":1,"chats":[{"id":"c","title":"Test","messages":[{"id":"u","text":"Вопрос","fromUser":true},{"id":"a","text":"Демо","fromUser":false}]}]}"""
        val state = StateCodec.decode(legacy)
        assertTrue(state.chats.single().messages.last().isDemo)
        assertEquals(2, OpenRouterGateway.payload(state.chats.single().messages).getJSONArray("messages").length())
        assertTrue(StateCodec.decode(StateCodec.encode(state)).chats.single().messages.last().isDemo)
    }
}

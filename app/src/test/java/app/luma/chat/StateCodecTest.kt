package app.luma.chat

import org.junit.Assert.*
import org.junit.Test

class StateCodecTest {
    @Test fun agentModeAndActionsSurviveConcurrentSyncEdits() {
        val chat = Conversation(id = "agent-chat", title = "Research")
        val base = ChatState(chats = listOf(chat), activeId = chat.id)
        val answer = Message(text = "Answer", fromUser = false, agent = true,
            steps = listOf(AgentStep("search", "public query", true)))
        val local = base.copy(chats = listOf(chat.copy(agent = true, messages = listOf(answer))),
            preferences = Preferences(agent = true))
        val remote = base.copy(chats = listOf(chat.copy(title = "Renamed elsewhere")), preferences = Preferences(accent = "blue"))
        val merged = mergeHistory(base, local, remote)
        assertTrue(merged.agent)
        assertTrue(merged.preferences.agent)
        assertEquals("blue", merged.preferences.accent)
        assertEquals("Renamed elsewhere", merged.active!!.title)
        assertEquals(answer, merged.active!!.messages.single())
        assertEquals(merged, StateCodec.decode(StateCodec.encode(merged)))
    }
    @Test fun customizationRoundTripAndOldDefaults() {
        val prefs = Preferences(accent = "teal", blackBackground = true, compactChat = true,
            animateReplies = false, showResponseTime = false, effort = Effort.ExtraHigh.key)
        assertEquals(prefs, StateCodec.decode(StateCodec.encode(ChatState(preferences = prefs))).preferences)
        assertEquals(Preferences(), StateCodec.decode("""{"version":5,"chats":[],"preferences":{}}""").preferences)
        // Saved before levels existed, or by a newer build with a level this one lacks: Instant.
        assertEquals(Effort.Instant, StateCodec.decode("""{"chats":[],"preferences":{"effort":"nope"}}""").preferences.effortLevel)
        val merged = mergeHistory(ChatState(), ChatState(preferences = prefs),
            ChatState(preferences = Preferences(language = "ru")))
        assertEquals(prefs.copy(language = "ru"), merged.preferences)
    }
    @Test fun freshInstallIsEmptyAndDark() {
        val state = StateCodec.decode(StateCodec.encode(ChatState()))
        assertTrue(state.chats.isEmpty()); assertNull(state.activeId)
        assertEquals("dark", state.preferences.theme)
    }
    @Test fun historyDraftsAndSettingsSurviveRoundTrip() {
        val chat = Conversation("one", "Идея 🌿", listOf(Message("m1", "Строка\nс кавычками: \"привет\"", true), Message("m2", "Ответ", false)), "неотправленный черновик", 1000)
        val state = ChatState(listOf(chat), "one", "новый черновик", Preferences("light", "mint", false, true))
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
    }
    @Test fun deletedActiveConversationCannotBeRestored() {
        val state = StateCodec.decode(StateCodec.encode(ChatState(activeId = "missing", newDraft = "текст")))
        assertNull(state.activeId); assertEquals("текст", state.draft)
    }
    @Test fun responseTimingSurvivesRestartAndLegacyMessagesHaveNoTiming() {
        val messages = listOf(Message(text = "Вопрос", fromUser = true),
            Message(text = "Ответ", fromUser = false, responseDurationMs = 3250))
        val state = ChatState(chats = listOf(Conversation(title = "Чат", messages = messages)))
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
        val old = """{"version":4,"chats":[{"id":"c","title":"Чат","messages":[{"id":"m","text":"Ответ","fromUser":false}]}]}"""
        assertNull(StateCodec.decode(old).chats.single().messages.single().responseDurationMs)
    }
}

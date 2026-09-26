package app.luma.chat

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class MediaTest {
    @Test fun photoHistoryAndDraftRoundTripAndMerge() {
        val photo = "data:image/jpeg;base64,ZmFrZQ=="
        val chat = Conversation(id = "c", title = "Photo", photo = photo,
            messages = listOf(Message(text = "", fromUser = true, photo = photo)))
        val state = ChatState(chats = listOf(chat), activeId = chat.id, newPhoto = photo)
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
        val merged = mergeHistory(ChatState(), state, ChatState(preferences = Preferences(accent = "blue")))
        assertEquals(photo, merged.photo)
        assertEquals(photo, merged.newPhoto)
        val payload = OpenRouterGateway.payload(chat.messages).getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("image_url", payload.getJSONObject(1).getString("type"))
        assertEquals(photo, payload.getJSONObject(1).getJSONObject("image_url").getString("url"))
        assertNull(StateCodec.decode("""{"version":6,"chats":[]}""").photo)
    }

    @Test fun transcribesOnAudioEndpointUsingSelectedModelAndM4a() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"text":"Привет, мир"}"""))
            val gateway = OpenRouterGateway(MemoryCredentials(), endpoint = server.url("/api/v1/chat/completions").toString())
            assertEquals("Привет, мир", gateway.transcribe(byteArrayOf(1, 2, 3)))
            val request = server.takeRequest()
            assertEquals("/api/v1/audio/transcriptions", request.path)
            val json = JSONObject(request.body.readUtf8())
            assertEquals("openai/gpt-transcribe", json.getString("model"))
            assertEquals("m4a", json.getJSONObject("input_audio").getString("format"))
            assertEquals("AQID", json.getJSONObject("input_audio").getString("data"))
        }
    }
}

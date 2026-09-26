package app.luma.chat

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs on Android's own org.json rather than the desktop library the plain unit
 * tests use: only Android's `optString` turns a JSON null into the text "null".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLumaApplication::class)
class AndroidJsonTest {
    private val nullAnswer = """{"choices":[{"message":{"content":null}}]}"""

    @Test fun aNullAnswerIsAnEmptyAnswerNotTheWordNull() {
        try { OpenRouterGateway.decode(200, nullAnswer); fail("Expected error") }
        catch (e: ChatFailure) { assertEquals(R.string.error_empty, e.messageRes) }
        assertEquals("", JSONObject("""{"text":null}""").text("text"))
        assertEquals("", JSONObject("{}").text("text"))
        assertEquals("Привет", JSONObject("""{"text":"Привет"}""").text("text"))
    }

    @Test fun o5DoesNotAnswerWithTheWordNull() = runBlocking {
        try { O5Agent { JSONObject(nullAnswer) }.run(emptyList()) {}; fail("Expected error") }
        catch (e: ChatFailure) { assertEquals(R.string.error_empty, e.messageRes) }
    }
}

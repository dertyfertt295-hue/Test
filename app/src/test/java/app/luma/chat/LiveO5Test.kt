package app.luma.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLumaApplication::class)
class LiveO5Test {
    @Test fun realSearchAndRead() = runBlocking {
        assumeTrue(System.getenv("LUMA_LIVE_O5") == "1")
        val result = OpenRouterGateway(EmbeddedCredentials()).agent(listOf(Message(text =
            "Use web_search to find the official Android Material 3 documentation, then use read_page on https://developer.android.com/develop/ui/compose/designsystems/material3 . Summarize what Material 3 is in two Russian sentences with one source link.", fromUser = true))) {}
        assertTrue(result.steps.any { it.kind == "search" })
        assertTrue(result.steps.any { it.kind == "read" })
        assertFalse("Research endpoint failed", result.steps.any { it.kind == "failed" })
        assertTrue(result.text.contains("https://developer.android.com"))
        java.io.File("build/o5-live-result.txt").writeText(result.text + "\n" + result.steps.joinToString("\n"))
    }
}

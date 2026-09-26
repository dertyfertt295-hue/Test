package app.luma.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Sends every level to every route that can think, against the real service:
 * proves the routes accept each effort value and shows how long each one takes.
 * Costs real requests, so it only runs with LUMA_LIVE_EFFORT=1.
 */
class LiveEffortTest {
    @Test fun everyLevelIsAcceptedByTheRoutesThatThink() = runBlocking {
        assumeTrue(System.getenv("LUMA_LIVE_EFFORT") == "1")
        val gateway = OpenRouterGateway(EmbeddedCredentials())
        val question = listOf(Message(text = "A farmer has 17 sheep and all but 9 run away. How many are left? Reply with the number only.", fromUser = true))
        val report = StringBuilder()
        LumaModel.values().filter { it.reasons }.forEach { model ->
            Effort.values().forEach { effort ->
                val started = System.nanoTime()
                val answer = gateway.reply(question, model, effort)
                report.append("${model.label} ${effort.label}: ${(System.nanoTime() - started) / 1_000_000} ms, truncated=${answer.truncated}: ${answer.text.take(80)}\n")
                assertTrue("${model.label} ${effort.label}: ${answer.text}", answer.text.contains("9"))
            }
        }
        java.io.File("build/effort-live-result.txt").writeText(report.toString())
    }
}

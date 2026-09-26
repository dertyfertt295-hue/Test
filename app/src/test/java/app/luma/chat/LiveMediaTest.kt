package app.luma.chat

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestLumaApplication::class)
class LiveMediaTest {
    @Test fun realVisionAndTranscription() = runBlocking {
        assumeTrue(System.getenv("LUMA_LIVE_MEDIA") == "1")
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val data = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, this) }.toByteArray()
        bitmap.recycle()
        val photo = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(data)
        val gateway = OpenRouterGateway(EmbeddedCredentials())
        val answer = gateway.reply(listOf(Message(text = "Name the dominant image color in one English word.", fromUser = true, photo = photo)), LumaModel.A45)
        assertTrue(answer.text.contains("red", ignoreCase = true))
        val audio = java.io.File(System.getenv("LUMA_TEST_WAV")).readBytes()
        val text = gateway.transcribeAudio(audio, "wav")
        assertTrue(text.contains("hello", ignoreCase = true))
        java.io.File("build/media-live-result.txt").writeText("Vision: ${answer.text}\nTranscription: $text")
    }
}

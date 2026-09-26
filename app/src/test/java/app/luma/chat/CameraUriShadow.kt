package app.luma.chat

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import java.io.File

/** FileProvider's canonical-root check assumes '/' and fails on a Windows JVM.
 * Only substitute URI mapping in this camera flow test; Android uses the real provider.
 */
@Implements(FileProvider::class)
class CameraUriShadow {
    companion object {
        @JvmStatic @Implementation
        fun getUriForFile(context: Context, authority: String, file: File): Uri =
            Uri.Builder().scheme("content").authority(authority).appendPath("camera").appendPath(file.name).build()
    }
}

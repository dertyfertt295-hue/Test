@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.luma.chat

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File

internal class CapturePhoto : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        clipData = ClipData.newRawUri("photo", input)
    }
}

@Composable internal fun rememberPhotoActions(onPhoto: (String?) -> Unit, busy: (Boolean) -> Unit, notify: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val folder = remember(context) { File(context.cacheDir, "camera").apply { mkdirs() } }
    val latestPhoto by rememberUpdatedState(onPhoto)
    fun importPhoto(uri: Uri, temporary: File? = null) {
        scope.launch {
            busy(true)
            try { latestPhoto(loadPhoto(context, uri)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { notify(context.getString(R.string.photo_error)) }
            finally { temporary?.delete(); busy(false) }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) importPhoto(uri) }
    val camera = rememberLauncherForActivityResult(CapturePhoto()) { success ->
        val file = cameraFile?.let { File(folder, it) }
        cameraFile = null
        if (success && file != null && file.isFile) importPhoto(Uri.fromFile(file), file) else file?.delete()
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(stringResource(R.string.photo_add), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedCard(onClick = {
                open = false
                try {
                    folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
                    val file = File.createTempFile("capture-", ".jpg", folder)
                    cameraFile = file.name
                    camera.launch(FileProvider.getUriForFile(context, context.packageName + ".photos", file))
                } catch (_: Exception) {
                    cameraFile?.let { File(folder, it).delete() }; cameraFile = null
                    notify(context.getString(R.string.camera_unavailable))
                }
            }, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.photo_camera), style = MaterialTheme.typography.titleSmall)
                }
            }
            ElevatedCard(onClick = {
                open = false
                try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (_: Exception) { notify(context.getString(R.string.photo_error)) }
            }, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.photo_gallery), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    return { open = true }
}

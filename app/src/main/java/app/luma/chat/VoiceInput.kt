package app.luma.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import java.io.File

@Composable internal fun VoiceInput(onDismiss: () -> Unit, onText: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val audio = remember { File.createTempFile("voice-", ".m4a", context.cacheDir) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    fun release() { runCatching { recorder?.release() }; recorder = null; recording = false }
    fun start() {
        try {
            @Suppress("DEPRECATION")
            val next = MediaRecorder()
            recorder = next
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(44100)
            next.setAudioEncodingBitRate(96000)
            next.setOutputFile(audio.absolutePath)
            next.prepare(); next.start(); recording = true; error = null
        } catch (_: Exception) { release(); error = R.string.voice_error }
    }
    fun transcribe() {
        if (transcribing) return
        if (recording) {
            try { recorder?.stop(); recorded = audio.length() > 0 }
            catch (_: Exception) { recorded = false; error = R.string.voice_empty }
            finally { release() }
        }
        if (!recorded) return
        transcribing = true; error = null
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { audio.readBytes() }
                val result = (context.applicationContext as LumaApplication).gateway.transcribe(bytes)
                onText(result)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = (e as? ChatFailure)?.messageRes ?: R.string.voice_error }
            finally { transcribing = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else error = R.string.voice_permission
    }
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(recording) {
        while (recording && seconds < 120) { delay(1000); seconds++ }
        if (recording && seconds >= 120) transcribe()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recording) { release(); onDismiss() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); release(); audio.delete() }
    }
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Mic, null) },
        title = { Text(stringResource(if (transcribing) R.string.voice_transcribing else R.string.voice_record)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (transcribing) CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                else if (recording) Text("%d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(error ?: R.string.voice_hint))
            }
        },
        confirmButton = {
            if (!transcribing && (recording || recorded)) TextButton(onClick = { transcribe() }) {
                Text(stringResource(if (recorded && error != null) R.string.retry else R.string.voice_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

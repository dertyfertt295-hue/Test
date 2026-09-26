package app.luma.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

/** JPEG data travels with history, so attachments survive sign-out and restore from PC. */
internal suspend fun loadPhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    fun stream() = resolver.openInputStream(uri) ?: error("image_unavailable")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    stream().use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    var bitmap = stream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: error("image_unavailable")
    try {
        val orientation = runCatching { stream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        fun replace(next: Bitmap) { if (next !== bitmap) { bitmap.recycle(); bitmap = next } }
        replace(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true))
        val scale = minOf(1f, 1280f / maxOf(bitmap.width, bitmap.height))
        if (scale < 1f) replace(Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true))
        var bytes: ByteArray
        var quality = 85
        do {
            bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray() }
            quality -= 15
        } while (bytes.size > 250_000 && quality >= 40)
        require(bytes.size <= 500_000)
        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)
    } finally { bitmap.recycle() }
}

@Composable internal fun PhotoPreview(photo: String, modifier: Modifier = Modifier, remove: (() -> Unit)? = null, inChat: Boolean = false) {
    val bitmap by produceState<Bitmap?>(null, photo) {
        value = withContext(Dispatchers.Default) { runCatching {
            val bytes = Base64.getDecoder().decode(photo.substringAfter(','))
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() }
    }
    val frame = if (inChat) {
        val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: 1f
        modifier.width(minOf(240f, 320f * ratio).dp).aspectRatio(ratio).clip(RoundedCornerShape(24.dp))
    } else modifier
    Box(frame) {
        bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.photo_attached),
            Modifier.fillMaxSize().then(if (!inChat) Modifier.clip(RoundedCornerShape(18.dp)) else Modifier),
            contentScale = if (inChat) ContentScale.Fit else ContentScale.Crop) }
        if (remove != null) IconButton(onClick = remove, modifier = Modifier.align(Alignment.TopEnd).size(40.dp)) {
            Box(Modifier.size(24.dp).background(Color.Black.copy(alpha = .65f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, stringResource(R.string.photo_remove), Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

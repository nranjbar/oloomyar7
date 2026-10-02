package com.oloomyar.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.oloomyar.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WorkbookImage(asset: String, description: String, caption: String? = null) {
    val context = LocalContext.current
    val loaded by produceState<Pair<Bitmap?, Boolean>>(null to false, asset) {
        value = withContext(Dispatchers.IO) {
            val bitmap = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open("images/$asset").use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2200) inSampleSize *= 2
                }
                context.assets.open("images/$asset").use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
            bitmap to true
        }
    }
    var enlarged by remember { mutableStateOf(false) }
    val bitmap = loaded.first
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (bitmap != null) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                shadowElevation = 2.dp
            ) {
                Image(
                    bitmap.asImageBitmap(), description,
                    Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
                        .background(Color.White).padding(7.dp)
                        .clickable(role = Role.Button, onClickLabel = "بزرگ‌نمایی تصویر") { enlarged = true },
                    contentScale = ContentScale.Fit
                )
            }
            TextButton(onClick = { enlarged = true }, modifier = Modifier.align(Alignment.End)) {
                Text("بزرگ‌نمایی تصویر")
            }
        } else if (!loaded.second) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Text("تصویر بارگیری نشد؛ دوباره وارد سؤال شوید.", color = MaterialTheme.colorScheme.error)
        }
        caption?.let { WorkbookText(it, color = Muted) }
    }
    if (enlarged && bitmap != null) {
        var scale by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        Dialog(onDismissRequest = { enlarged = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color.White) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { enlarged = false }) { Text("بستن") }
                        Text(description, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        val width = constraints.maxWidth.toFloat()
                        val height = constraints.maxHeight.toFloat()
                        val transform = rememberTransformableState { zoom, offset, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val limitX = width * (scale - 1f) / 2f
                            val limitY = height * (scale - 1f) / 2f
                            pan = Offset((pan.x + offset.x).coerceIn(-limitX, limitX), (pan.y + offset.y).coerceIn(-limitY, limitY))
                        }
                        Image(bitmap.asImageBitmap(), description,
                            Modifier.fillMaxSize().transformable(transform).graphicsLayer {
                                scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
                            }, contentScale = ContentScale.Fit)
                    }
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton(onClick = { scale = (scale - .5f).coerceAtLeast(1f); pan = Offset.Zero }, enabled = scale > 1f) { Text("کوچک‌تر") }
                        TextButton(onClick = { scale = 1f; pan = Offset.Zero }) { Text("اندازهٔ اصلی") }
                        OutlinedButton(onClick = { scale = (scale + .5f).coerceAtMost(5f) }, enabled = scale < 5f) { Text("بزرگ‌تر") }
                    }
                    Text("با دو انگشت بزرگ کنید و تصویر را جابه‌جا کنید.", Modifier.padding(16.dp), color = Muted)
                }
            }
        }
    }
}


package com.oloomyar.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
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
internal fun WorkbookImage(asset: String, description: String, caption: String? = null, trimAnswerWhitespace: Boolean = false, compact: Boolean = false, onImageReady: (() -> Unit)? = null, maxImageHeight: androidx.compose.ui.unit.Dp? = null) {
    val context = LocalContext.current
    val loaded by produceState<Pair<Bitmap?, Boolean>>(null to false, asset, trimAnswerWhitespace) {
        value = withContext(Dispatchers.IO) {
            SourceBitmap.load(context, asset, trimAnswerWhitespace) to true
        }
    }
    var enlarged by remember { mutableStateOf(false) }
    val bitmap = loaded.first
    val readyCallback by rememberUpdatedState(onImageReady)
    LaunchedEffect(asset, bitmap) {
        if(bitmap!=null && readyCallback!=null) {
            // The decoded image changes this lazy item's measured height.
            // Notify its owner after layout, without moving source figures.
            withFrameNanos { }; withFrameNanos { }
            readyCallback?.invoke()
        }
    }
    val expectedRatio = remember(asset) { SourceBitmap.aspectRatio(context, asset) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (bitmap != null) {
            if(maxImageHeight==null) {
                WorkbookBitmapFigure(bitmap,description,Modifier.testTag("workbook-image-fit-$asset"),onEnlarge={enlarged=true})
            } else BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                val ratio=bitmap.width.toFloat()/bitmap.height
                val displayWidth=minOf(maxWidth,(maxImageHeight-14.dp).coerceAtLeast(1.dp)*ratio+14.dp)
                WorkbookBitmapFigure(bitmap,description,Modifier.width(displayWidth),onEnlarge={enlarged=true})
            }
        } else if (!loaded.second) {
            // Reserve the final source figure's geometry before decoding. Choices below
            // it must not jump while a student taps or an accessibility action runs.
            Surface(color=Color.White,shape=RoundedCornerShape(22.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)) {
                Box(Modifier.fillMaxWidth().padding(7.dp).aspectRatio(expectedRatio),contentAlignment=Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        } else {
            Text("تصویر بارگیری نشد؛ دوباره وارد سؤال شوید.", color = MaterialTheme.colorScheme.error)
        }
        if (compact) {
            TextButton(onClick={enlarged=true},enabled=bitmap!=null,contentPadding=PaddingValues(horizontal=2.dp),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workbook-image-enlarge-$asset")) {
                Text("بزرگ‌نمایی",style=MaterialTheme.typography.labelMedium)
            }
        } else OutlinedButton(onClick = { enlarged = true }, enabled=bitmap!=null,
            modifier = Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workbook-image-enlarge-$asset")) {
            Icon(Icons.Default.Add,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))
            Text(if(trimAnswerWhitespace) "مشاهدهٔ کامل و بزرگ‌نمایی پاسخ" else "بزرگ‌نمایی تصویر")
        }
        caption?.let { WorkbookText(it, color = Muted) }
    }
    if (enlarged && bitmap != null) {
        // Start with the whole width visible, including very wide source crops.
        // Zoom is explicit; no answer edge is hidden on opening the viewer.
        var scale by remember(asset) { mutableFloatStateOf(1f) }
        val vertical=rememberScrollState();val horizontal=rememberScrollState();val scope=rememberCoroutineScope()
        Dialog(onDismissRequest = { enlarged = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize().testTag("workbook-image-viewer"), color = Color.White) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { enlarged = false }) { Text("بستن") }
                        Text(description, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        val width=maxWidth
                        val transform=rememberTransformableState {zoom,offset,_->
                            scale=(scale*zoom).coerceIn(1f,5f)
                            scope.launch {horizontal.scrollBy(-offset.x);vertical.scrollBy(-offset.y)}
                        }
                        Box(Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal).transformable(transform)) {
                            Image(bitmap.asImageBitmap(),description,Modifier.width(width*scale).aspectRatio(bitmap.width.toFloat()/bitmap.height)
                                .testTag("workbook-viewer-bitmap"),contentScale=ContentScale.Fit)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={scale=(scale-.5f).coerceAtLeast(1f)},enabled=scale>1f,modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Remove,"کوچک‌تر")}
                        TextButton(onClick={scale=1f;scope.launch {horizontal.scrollTo(0);vertical.scrollTo(0)}},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("نمایش کامل")}
                        IconButton(onClick={scale=(scale+.5f).coerceAtMost(5f)},enabled=scale<5f,modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Add,"بزرگ‌تر")}
                    }
                    Text("با + یا دو انگشت، پاسخ را بزرگ کن. در حالت بزرگ‌نمایی، تصویر را برای خواندن همهٔ قسمت‌ها بکش.",Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=Muted)
                }
            }
        }
    }
}

/** No extra layout wrapper is introduced when a route uses the default image size. */
@Composable
private fun WorkbookBitmapFigure(bitmap:Bitmap,description:String,modifier:Modifier=Modifier,onEnlarge:()->Unit) {
    Surface(
        modifier=modifier,
        color=Color.White,
        shape=RoundedCornerShape(22.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,Border),
        shadowElevation=2.dp
    ) {
        val ratio=bitmap.width.toFloat()/bitmap.height
        val imageModifier=Modifier.fillMaxWidth().padding(7.dp).aspectRatio(ratio)
        Image(bitmap.asImageBitmap(),description,
            imageModifier.background(Color.White)
                .clickable(role=Role.Button,onClickLabel="بزرگ‌نمایی تصویر",onClick=onEnlarge),
            contentScale=ContentScale.Fit)
    }
}

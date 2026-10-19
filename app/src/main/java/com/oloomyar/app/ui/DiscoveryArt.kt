package com.oloomyar.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.oloomyar.app.ui.theme.LocalDisplaySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only the supplied final art; scientific content always comes from the workbook. */
@Composable
private fun SuppliedArtwork(asset: String, description: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, asset) {
        value = withContext(Dispatchers.IO) { SourceBitmap.load(context, asset) }
    }
    bitmap?.let { Image(it.asImageBitmap(), description, modifier, contentScale = ContentScale.Fit) }
        ?: Spacer(modifier)
}

@Composable
internal fun DiscoveryRobot(modifier: Modifier = Modifier, pose: String = "welcome") {
    val reduced = LocalDisplaySettings.current.reduceMotion
    val bob = if (reduced) 0f else {
        val transition = rememberInfiniteTransition(label = "supplied-robot")
        transition.animateFloat(-2f, 2f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gentle-float").value
    }
    SuppliedArtwork("mascot_${pose}_480w.webp", "ربات کاوشگر علوم‌یار", modifier.graphicsLayer { translationY = bob })
}

@Composable
internal fun ChapterArtwork(chapter: Int, modifier: Modifier = Modifier) {
    SuppliedArtwork("chapter_${chapter.toString().padStart(2, '0')}.webp", "جلد فصل $chapter", modifier)
}

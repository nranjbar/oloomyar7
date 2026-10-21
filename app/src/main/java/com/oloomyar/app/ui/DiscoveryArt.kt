package com.oloomyar.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
internal fun SuppliedArtwork(asset: String, description: String, modifier: Modifier) {
    key(asset) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, asset) {
        withContext(Dispatchers.Main.immediate) {
            value = withContext(Dispatchers.IO) { SourceBitmap.load(context, asset) }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), description, modifier, contentScale = ContentScale.Fit) }
        ?: Spacer(modifier)
    }
}

@Composable
internal fun DiscoveryRobot(modifier: Modifier = Modifier, pose: String = "welcome") {
    val reduced = LocalDisplaySettings.current.reduceMotion
    val bob = if (reduced) 0f else {
        val transition = rememberInfiniteTransition(label = "supplied-robot")
        transition.animateFloat(-2f, 2f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gentle-float").value
    }
    Surface(modifier.graphicsLayer { translationY = bob },shape=RoundedCornerShape(20.dp),color=Color.White,
        border=BorderStroke(1.dp,Color(0xFFE0EDF0))) {
        SuppliedArtwork("mascot_${pose}_480w.webp", "ربات کاوشگر علوم‌یار", Modifier.fillMaxSize().padding(4.dp))
    }
}

@Composable
internal fun ChapterArtwork(chapter: Int, modifier: Modifier = Modifier) {
    SuppliedArtwork("chapter_${chapter.toString().padStart(2, '0')}.webp", "جلد فصل $chapter", modifier)
}

/** Supplied state art beside wrapping text, with a bounded icon at large font sizes. */
@Composable
internal fun WorkbookArtLabel(kind: String, text: String, color: Color,
                              modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SuppliedArtwork("ui_icon_${kind}_320.webp", text,
            Modifier.size(42.dp).testTag("state-art-$kind"))
        Text(text, Modifier.weight(1f), color = color, style = style)
    }
}

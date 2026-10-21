package com.oloomyar.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.oloomyar.app.LaunchSplashController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun Grade7LaunchSplash(content: @Composable () -> Unit) {
    // Returning from an external app preserves the selected tab and review state.
    val screenState = rememberSaveableStateHolder()
    val ticket = LaunchSplashController.epoch
    if (LaunchSplashController.visible) key(ticket) {
        val context = LocalContext.current
        val loaded by produceState<Pair<Bitmap?,Boolean>>(null to false) {
            withContext(Dispatchers.Main.immediate) {
                value = withContext(Dispatchers.IO) {
                    runCatching { context.assets.open("ui_art/launch_grade7.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull() to true
                }
            }
        }
        LaunchedEffect(loaded.second) {
            if (loaded.second) {
                // The three seconds start once the artwork has decoded.
                if (loaded.first != null) delay(3000)
                LaunchSplashController.finish(ticket)
            }
        }
        Box(Modifier.fillMaxSize().background(Color(0xFFEFF9FC)).safeDrawingPadding().testTag("launch-splash"),
            contentAlignment = Alignment.Center) {
            loaded.first?.let { bitmap ->
                Image(bitmap.asImageBitmap(), "علوم‌یار هفتم؛ در حال ورود به برنامه",
                    Modifier.fillMaxSize().testTag("launch-artwork"), contentScale = ContentScale.Fit)
            }
        }
    } else screenState.SaveableStateProvider("screen") { content() }
}

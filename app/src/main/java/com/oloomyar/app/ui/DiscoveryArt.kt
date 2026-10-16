package com.oloomyar.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.oloomyar.app.ui.theme.*

/** Original vector artwork, drawn locally: no image download or bitmap animation. */
@Composable
internal fun DiscoveryRobot(modifier: Modifier = Modifier) {
    val reduced = LocalDisplaySettings.current.reduceMotion
    val bob = if (reduced) 0f else {
        val transition = rememberInfiniteTransition(label = "robot-idle")
        transition.animateFloat(-2f, 2f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gentle-float").value
    }
    Canvas(modifier.semantics { contentDescription = "ربات کاوشگر علوم‌یار" }) {
        scale(size.width / 200f, size.height / 200f, Offset.Zero) {
            drawCircle(Color(0xFFD7F0F2), 84f, Offset(100f, 99f))
            drawCircle(Color.White.copy(alpha = .7f), 65f, Offset(100f, 99f), style = Stroke(1.5f))
            drawOval(Ink.copy(alpha = .10f), Offset(52f, 179f), Size(96f, 13f))
            translate(top = bob) {
                drawLine(Purple, Offset(100f, 40f), Offset(108f, 23f), 5f, StrokeCap.Round)
                drawCircle(Yellow, 8f, Offset(110f, 20f))
                drawRoundRect(Purple, Offset(57f, 109f), Size(88f, 58f), CornerRadius(24f))
                drawRoundRect(Color(0xFFE5E1F8), Offset(71f, 120f), Size(60f, 35f), CornerRadius(12f))
                drawCircle(Blue, 12f, Offset(101f, 138f))
                drawLine(Color.White, Offset(97f, 138f), Offset(101f, 142f), 3f, StrokeCap.Round)
                drawLine(Color.White, Offset(101f, 142f), Offset(108f, 133f), 3f, StrokeCap.Round)
                drawLine(Purple, Offset(58f, 128f), Offset(37f, 114f), 12f, StrokeCap.Round)
                drawCircle(Blue, 10f, Offset(32f, 109f))
                drawLine(Purple, Offset(145f, 128f), Offset(164f, 107f), 12f, StrokeCap.Round)
                drawCircle(Blue, 10f, Offset(169f, 102f))
                drawRoundRect(BlueDark, Offset(69f, 162f), Size(25f, 15f), CornerRadius(7f))
                drawRoundRect(BlueDark, Offset(110f, 162f), Size(25f, 15f), CornerRadius(7f))
                drawRoundRect(Blue, Offset(39f, 67f), Size(18f, 29f), CornerRadius(9f))
                drawRoundRect(Blue, Offset(145f, 67f), Size(18f, 29f), CornerRadius(9f))
                drawRoundRect(Blue, Offset(49f, 43f), Size(104f, 73f), CornerRadius(27f))
                drawRoundRect(Color.White, Offset(55f, 46f), Size(92f, 61f), CornerRadius(23f))
                drawRoundRect(Navy, Offset(66f, 59f), Size(70f, 33f), CornerRadius(16f))
                drawCircle(Color(0xFF84ECDA), 6f, Offset(85f, 75f))
                drawCircle(Color(0xFF84ECDA), 6f, Offset(117f, 75f))
                drawArc(Blue, 15f, 150f, false, Offset(92f, 90f), Size(18f, 9f), style = Stroke(2.5f, cap = StrokeCap.Round))
                drawCircle(Yellow, 4f, Offset(173f, 46f))
                drawLine(Purple, Offset(25f, 56f), Offset(25f, 68f), 3f, StrokeCap.Round)
                drawLine(Purple, Offset(19f, 62f), Offset(31f, 62f), 3f, StrokeCap.Round)
            }
        }
    }
}

/** Decorative scenes are separate from all authoritative workbook figures. */
@Composable
internal fun ChapterArtwork(chapter: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "تصویر تزئینی فصل ${workbookFa(chapter)}" }) {
        scale(size.width / 120f, size.height / 120f, Offset.Zero) {
            val accent = when (chapter) { 2 -> RoyalBlue; 3 -> Purple; else -> Blue }
            drawRoundRect(accent.copy(alpha = .08f), Offset.Zero, Size(120f, 120f), CornerRadius(28f))
            drawCircle(Color.White.copy(alpha = .8f), 44f, Offset(60f, 58f))
            when (chapter) {
                1 -> {
                    val flask = Path().apply {
                        moveTo(45f, 27f); lineTo(65f, 27f); lineTo(65f, 53f); lineTo(86f, 88f)
                        quadraticTo(89f, 95f, 81f, 96f); lineTo(29f, 96f); quadraticTo(22f, 95f, 25f, 88f); lineTo(45f, 53f); close()
                    }
                    drawPath(flask, Color(0xFFD7F0F2)); drawPath(flask, accent, style = Stroke(3.5f, join = StrokeJoin.Round))
                    val liquid = Path().apply { moveTo(38f, 70f); quadraticTo(53f, 77f, 72f, 70f); lineTo(83f, 90f); lineTo(29f, 90f); close() }
                    drawPath(liquid, accent.copy(alpha = .75f))
                    drawCircle(Yellow, 5f, Offset(56f, 79f)); drawCircle(Color.White, 3f, Offset(63f, 87f))
                    drawCircle(Purple, 6f, Offset(85f, 37f)); drawCircle(Yellow, 4f, Offset(29f, 48f))
                    drawLine(accent, Offset(41f, 27f), Offset(69f, 27f), 4f, StrokeCap.Round)
                }
                2 -> {
                    rotate(-15f, Offset(60f, 60f)) {
                        drawRoundRect(Yellow, Offset(19f, 42f), Size(82f, 30f), CornerRadius(7f))
                        for (i in 0..8) drawLine(Ink, Offset(27f + i * 8f, 43f), Offset(27f + i * 8f, if (i % 2 == 0) 55f else 50f), 2f)
                    }
                    drawCircle(accent, 22f, Offset(73f, 82f)); drawCircle(Color.White, 16f, Offset(73f, 82f))
                    drawLine(accent, Offset(73f, 82f), Offset(73f, 70f), 3f, StrokeCap.Round)
                    drawLine(accent, Offset(73f, 82f), Offset(82f, 87f), 3f, StrokeCap.Round)
                    drawRoundRect(Purple, Offset(68f, 55f), Size(10f, 8f), CornerRadius(2f))
                }
                else -> {
                    for (angle in listOf(0f, 60f, 120f)) rotate(angle, Offset(60f, 60f)) {
                        drawOval(accent.copy(alpha = .7f), Offset(20f, 42f), Size(80f, 36f), style = Stroke(2.5f))
                    }
                    drawCircle(accent, 13f, Offset(60f, 60f)); drawCircle(Yellow, 6f, Offset(99f, 61f))
                    drawCircle(Blue, 5f, Offset(40f, 29f)); drawCircle(RoyalBlue, 5f, Offset(40f, 91f))
                    drawCircle(Color.White.copy(alpha = .7f), 4f, Offset(56f, 56f))
                }
            }
        }
    }
}

package com.oloomyar.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oloomyar.app.ui.theme.*

/** Shared visual language for every generation of workbook screens. */
@Composable
internal fun OloomScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF0F3FF), Background, Color(0xFFF8FBFF))
                )
            )
            .drawBehind {
                drawCircle(Blue.copy(alpha = .055f), size.minDimension * .43f, Offset(size.width * .94f, size.height * .04f))
                drawCircle(Purple.copy(alpha = .04f), size.minDimension * .34f, Offset(size.width * .03f, size.height * .58f))
                drawCircle(Aqua.copy(alpha = .035f), size.minDimension * .28f, Offset(size.width * .86f, size.height * .9f))
            },
        content = content
    )
}

@Composable
internal fun OloomHeroCard(
    eyebrow: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Science,
    startColor: Color = Navy,
    endColor: Color = Blue,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(startColor, endColor)))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = .10f), 120.dp.toPx(), Offset(size.width * .06f, size.height * .05f))
                drawCircle(Color.White.copy(alpha = .07f), 74.dp.toPx(), Offset(size.width * .86f, size.height * .96f))
            }
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = .16f), shape = RoundedCornerShape(18.dp)) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.padding(12.dp).size(28.dp))
                }
                Spacer(Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = .14f), shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFFD875), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(eyebrow, color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            Text(
                description,
                color = Color.White.copy(alpha = .82f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
            )
            footer?.let {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = Color.White.copy(alpha = .16f))
                Spacer(Modifier.height(14.dp))
                it()
            }
        }
    }
}

@Composable
internal fun OloomSectionHeader(
    title: String,
    subtitle: String? = null,
    color: Color = Blue,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(width = 5.dp, height = 28.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
        }
    }
}

@Composable
internal fun OloomCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Surface(
        modifier = modifier.fillMaxWidth().then(clickableModifier),
        color = color,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, accent?.copy(alpha = .15f) ?: Border.copy(alpha = .9f)),
        shadowElevation = 2.dp
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (accent != null) Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
internal fun OloomActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    soft: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OloomCard(modifier = modifier, accent = color, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = soft, shape = RoundedCornerShape(17.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(12.dp).size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
            Spacer(Modifier.width(8.dp))
            Surface(color = soft, shape = CircleShape) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = color, modifier = Modifier.padding(7.dp).size(17.dp))
            }
        }
    }
}

@Composable
internal fun OloomTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color.White.copy(alpha = .96f), shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "برگشت", tint = Blue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("برگشت", color = Blue, fontWeight = FontWeight.Bold)
            }
            Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Surface(color = BlueSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(horizontal = 13.dp)) {
                Icon(Icons.Default.Science, null, tint = Blue, modifier = Modifier.padding(7.dp).size(18.dp))
            }
        }
    }
}

@Composable
internal fun OloomPill(
    text: String,
    color: Color,
    soft: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(color = soft, shape = CircleShape, modifier = modifier, border = BorderStroke(1.dp, color.copy(alpha = .12f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
internal fun OloomProgressBar(progress: Float, color: Color = Blue, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
        color = color,
        trackColor = color.copy(alpha = .12f)
    )
}


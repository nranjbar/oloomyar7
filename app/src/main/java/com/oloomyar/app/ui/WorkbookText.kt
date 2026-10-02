package com.oloomyar.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import com.oloomyar.app.ui.theme.Ink

/** Isolate scientific runs so charges, subscripts and units retain their order in Persian. */
internal fun workbookBidi(text: String): String {
    val science = Regex("[A-Za-z0-9۰-۹٠-٩₀-₉⁰-⁹Δπ][A-Za-z0-9۰-۹٠-٩₀-₉⁰-⁹¹²³ₐ-ₜΔπ̄⁺⁻+−×÷=./٫٬()|–: ]*")
    return text.split('\n').joinToString("\n") { line ->
        if (line.contains('→') && !line.contains('←')) {
            // Keep the reaction direction while each Persian substance name remains RTL.
            "\u2066" + line.split(Regex("(?=[+→])|(?<=[+→])")).joinToString("") { token ->
                if (Regex("[آ-ی]").containsMatchIn(token)) "\u2067$token\u2069" else token
            } + "\u2069"
        } else science.replace(line) { match ->
            val token = match.value.trimEnd()
            "\u2066$token\u2069" + match.value.drop(token.length)
        }
    }
}

@Composable
internal fun WorkbookText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    bold: Boolean = false
) {
    Text(
        text = workbookBidi(text),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrRtl),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}


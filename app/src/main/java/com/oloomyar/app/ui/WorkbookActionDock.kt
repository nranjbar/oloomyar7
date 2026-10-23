package com.oloomyar.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.oloomyar.app.ui.theme.*

/** Keep the reading area large; the primary action stays next to the bottom edge. */
@Composable
internal fun WorkbookActionDock(
    finished: Boolean,
    canCheck: Boolean,
    canHint: Boolean,
    canReveal: Boolean,
    onCheck: () -> Unit,
    onChoices: () -> Unit,
    onHint: () -> Unit,
    onReveal: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onQuestionList: (() -> Unit)?,
) {
    val previous: @Composable () -> Unit = {
        IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null,
            modifier = Modifier.size(48.dp).testTag("previous-question")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "سؤال قبلی", tint = if (onPrevious != null) Blue else Muted.copy(alpha = .38f))
        }
    }
    val next: @Composable () -> Unit = {
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null,
            modifier = Modifier.size(48.dp).testTag("next-question")) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "سؤال بعدی", tint = if (onNext != null) Blue else Muted.copy(alpha = .38f))
        }
    }
    val navigator: @Composable () -> Unit = {
        if (onQuestionList != null) IconButton(onClick = onQuestionList,
            modifier = Modifier.size(48.dp).testTag("question-navigator")) {
            Icon(Icons.Default.FormatListBulleted, "فهرست سؤال‌ها", tint = Blue)
        }
    }
    val choices: @Composable () -> Unit = {
        IconButton(onClick = onChoices, modifier = Modifier.size(48.dp).testTag("return-to-choices")) {
            Icon(Icons.Default.VerticalAlignTop, "رفتن به انتخاب‌ها", tint = Blue)
        }
    }
    val hint: @Composable (Modifier) -> Unit = { modifier ->
        TextButton(onClick = onHint, enabled = canHint,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            modifier = modifier.heightIn(min = 48.dp).testTag("show-hints")) {
            Text("راهنما", style = MaterialTheme.typography.labelMedium)
        }
    }
    val reveal: @Composable (Modifier) -> Unit = { modifier ->
        TextButton(onClick = onReveal, enabled = canReveal,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            modifier = modifier.heightIn(min = 48.dp).testTag("reveal-answer")) {
            Text("دیدن پاسخ", style = MaterialTheme.typography.labelMedium)
        }
    }
    val check: @Composable (Modifier) -> Unit = { modifier ->
        Button(onClick = onCheck, enabled = finished || canCheck,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Navy),
            modifier = modifier.heightIn(min = 54.dp).testTag("check-answer")) {
            Text(if (finished) "سؤال بعدی" else "بررسی پاسخ", style = MaterialTheme.typography.labelLarge)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().testTag("question-actions")) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (maxWidth >= 600.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onQuestionList != null) previous()
                    check(Modifier.weight(2f))
                    if (onQuestionList != null) next()
                    navigator()
                    choices()
                    hint(Modifier.weight(1f))
                    reveal(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        choices()
                        hint(Modifier.weight(1f))
                        reveal(Modifier.weight(1f))
                        navigator()
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onQuestionList != null) previous()
                        check(Modifier.weight(1f))
                        if (onQuestionList != null) next()
                    }
                }
            }
        }
    }
}

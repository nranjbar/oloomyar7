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

/** Question navigation stays reachable without narrowing the main answer action. */
@Composable
internal fun WorkbookQuestionTopBar(
    index: Int,
    count: Int,
    onQuestionList: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.testTag("question-navigation"), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onQuestionList, modifier = Modifier.size(48.dp).testTag("question-navigator")) {
                Icon(Icons.Default.FormatListBulleted, "فهرست سؤال‌ها", tint = Blue)
            }
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null,
                modifier = Modifier.size(48.dp).testTag("previous-question")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "سؤال قبلی")
            }
            Text("سؤال ${workbookFa(index)} از ${workbookFa(count)}", style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null,
                modifier = Modifier.size(48.dp).testTag("next-question")) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "سؤال بعدی")
            }
        }
    }
}

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
) {
    val choices: @Composable () -> Unit = {
        IconButton(onClick = onChoices, modifier = Modifier.size(48.dp).testTag("return-to-choices")) {
            Icon(Icons.Default.VerticalAlignTop, "رفتن به انتخاب‌ها", tint = Blue)
        }
    }
    val hint: @Composable (Modifier) -> Unit = { modifier ->
        TextButton(onClick = onHint, enabled = canHint,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            modifier = modifier.heightIn(min = 48.dp).testTag("show-hints")) {
            Text("راهنما", style = MaterialTheme.typography.labelMedium)
        }
    }
    val reveal: @Composable (Modifier) -> Unit = { modifier ->
        TextButton(onClick = onReveal, enabled = canReveal,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
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
                    check(Modifier.weight(2f))
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
                    }
                    check(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

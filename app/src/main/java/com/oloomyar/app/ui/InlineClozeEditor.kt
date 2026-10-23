package com.oloomyar.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.*
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.theme.*
import kotlin.random.Random

/** All sentences stay in place. Opening or closing a blank never changes another blank. */
@Composable
internal fun InlineFillBlanksQuestion(
    step: LearningStep,
    values: List<String>,
    seed: Int,
    mark: (Int) -> ResponseMark,
    locked: (Int) -> Boolean,
    showInstructions: Boolean = true,
    nativeLayout: Boolean = false,
    compact: Boolean = false,
    onChange: (List<String>) -> Unit
) {
    val rows = if (step.kind == StepKind.MATCH) step.pairs.map { it.left } else step.classifyItems.map { it.text }
    var active by rememberSaveable(step.id) { mutableIntStateOf(-1) }
    val replace: (Int, String) -> Unit = { index, value ->
        if (active == index && !locked(index)) {
            onChange(List(rows.size) { if (it == index) value else values.getOrElse(it) { "" } })
        }
        active = -1
    }
    val chipStyle = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val blank: @Composable (Int, Modifier) -> Unit = { index, sizing ->
        val value = values.getOrElse(index) { "" }
        val state = mark(index)
        val ink = when (state) {
            ResponseMark.CORRECT -> Green
            ResponseMark.INCORRECT -> Danger
            ResponseMark.SELECTED -> SelectionInk
            else -> Blue
        }
        val fill = when (state) {
            ResponseMark.CORRECT -> GreenSoft
            ResponseMark.INCORRECT -> DangerSoft
            ResponseMark.SELECTED -> SelectionSoft
            else -> BlueSoft
        }
        Surface(
            modifier = sizing.testTag("blank-${step.id}-$index")
                .semantics {
                    contentDescription = "جای خالی ${workbookFa(index + 1)}"
                    stateDescription = when (state) {
                        ResponseMark.CORRECT -> "درست: $value"
                        ResponseMark.INCORRECT -> "اشتباه: $value"
                        ResponseMark.SELECTED -> "انتخاب شده: $value"
                        else -> "بی‌پاسخ"
                    }
                }.clickable(enabled = !locked(index), role = Role.Button) {
                    if (value.isNotBlank()) onChange(List(rows.size) { if (it == index) "" else values.getOrElse(it) { "" } })
                    active = index
                },
            color = fill, contentColor = ink,
            border = BorderStroke(if (state == ResponseMark.EMPTY) 1.dp else 2.dp, ink.copy(alpha = .65f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            val symbol = when(state) {
                ResponseMark.CORRECT -> Icons.Default.Check
                ResponseMark.INCORRECT -> Icons.Default.Close
                else -> Icons.Default.ExpandMore
            }
            if (compact) {
                // Give narrow portrait cells their full text width. A side icon
                // otherwise splits short Persian units into individual letters.
                Column(Modifier.padding(horizontal=4.dp,vertical=4.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(2.dp,Alignment.CenterVertically)) {
                    Text(workbookBidi(value.ifBlank { workbookFa(index + 1) }),
                        modifier=Modifier.fillMaxWidth(),style=chipStyle,fontWeight=FontWeight.Bold,
                        textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    Icon(symbol,null,Modifier.size(13.dp))
                }
            } else Row(Modifier.padding(horizontal=8.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.Center) {
                Text(workbookBidi(value.ifBlank { "انتخاب ${workbookFa(index + 1)}" }), modifier=Modifier.weight(1f,fill=false),style=chipStyle,fontWeight=FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Icon(symbol,null,Modifier.size(16.dp))
            }
        }
    }

    val template = step.clozeTemplate
    if (template != null) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)
        if (showInstructions) Text("برای انتخاب یا تغییر پاسخ، روی همان جای خالی بزن. لمس دوباره فقط انتخاب همان خانه را پاک می‌کند.",
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(bottom = 10.dp))
        val singleSlot = if (nativeLayout) Regex("""^\s*\{\{(\d+)\}\}\s*$""").matchEntire(template)?.groupValues?.get(1)?.toInt() else null
        if (singleSlot != null) {
            blank(singleSlot, Modifier.fillMaxWidth().heightIn(min = 48.dp))
        } else BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maximum = with(density) { (maxWidth - 12.dp).roundToPx() }.coerceAtLeast(1)
            val heights = mutableMapOf<Int, Int>()
            val placeholders = rows.indices.associate { index ->
                val value = values.getOrElse(index) { "" }.ifBlank { "انتخاب ${workbookFa(index + 1)}" }
                val measured = measurer.measure(workbookBidi(value), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    constraints = Constraints(maxWidth = (maximum - with(density) { 44.dp.roundToPx() }).coerceAtLeast(1)))
                val width = (measured.size.width + with(density) { 44.dp.roundToPx() })
                    .coerceIn(with(density) { 48.dp.roundToPx() }.coerceAtMost(maximum), maximum)
                val height = maxOf(measured.size.height + with(density) { 12.dp.roundToPx() }, with(density) { 48.dp.roundToPx() })
                heights[index] = height
                "blank$index" to InlineTextContent(Placeholder(with(density) { width.toSp() }, with(density) { height.toSp() }, PlaceholderVerticalAlign.Center)) { blank(index, Modifier.fillMaxSize()) }
            }
            // A placeholder is taller than the prose line. Give every printed
            // paragraph its own line height, including wrapped lines and long
            // selected answers, instead of painting chips across adjacent lines.
            Column(verticalArrangement = Arrangement.spacedBy(if (nativeLayout) 8.dp else 0.dp)) {
                (if (nativeLayout) template.split('\n') else listOf(template)).forEach { paragraph ->
                    val document = remember(step.id, paragraph) { ClozeDocument.parse(paragraph, step.id) }
                    val content = buildAnnotatedString {
                        document.tokens.forEach { token -> when (token) {
                            is ClozeToken.Text -> append(workbookBidi(token.value))
                            is ClozeToken.Blank -> appendInlineContent("blank${token.index}", "................")
                        } }
                    }
                    val tallest = document.tokens.filterIsInstance<ClozeToken.Blank>().maxOfOrNull { heights[it.index] ?: 0 } ?: 0
                    val paragraphStyle = if (nativeLayout && tallest > 0) style.copy(
                        lineHeight = maxOf(style.lineHeight.value, with(density) { (tallest + 8.dp.roundToPx()).toSp().value }).sp,
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            androidx.compose.ui.text.style.LineHeightStyle.Trim.None)
                    ) else style
                    Text(content, Modifier.fillMaxWidth(), style = paragraphStyle, inlineContent = placeholders)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEachIndexed { index, label ->
                Surface(color = SurfaceMuted, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkbookText(label)
                        Box(Modifier.widthIn(min = 130.dp, max = 280.dp).heightIn(min = 52.dp)) { blank(index, Modifier.fillMaxSize()) }
                    }
                }
            }
        }
    }
    val activeRow = active
    if (activeRow in rows.indices && !locked(activeRow)) {
        WordBankBottomSheet(step, activeRow, values, seed, onSelect = { replace(activeRow, it) }, onDismiss = { active = -1 })
    }
}

@Composable
internal fun WordBankBottomSheet(
    step: LearningStep,
    row: Int,
    values: List<String>,
    seed: Int,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val choices = if (step.kind == StepKind.MATCH) WorkbookAnswers.choices(step, row) else step.categories
    val order = remember(step.id, row, seed) { choices.shuffled(Random(seed xor step.id.hashCode())) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding().testTag("choice-sheet"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("جای خالی ${workbookFa(row + 1)}", style = MaterialTheme.typography.titleLarge) }
            item { WorkbookText(if (step.kind == StepKind.MATCH) step.pairs[row].left else step.classifyItems[row].text) }
            item { Text("یک گزینه را انتخاب کن", color = Muted) }
            items(order, key = { it }) { choice ->
                // Every choice remains reusable; scrolling the sheet never changes a blank.
                OutlinedButton(onClick = { onSelect(choice) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("cloze-choice-$choice"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (values.getOrNull(row) == choice) SelectionSoft else MaterialTheme.colorScheme.surface)) {
                    WorkbookText(choice, Modifier.weight(1f))
                }
            }
            item {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("بستن") }
                TextButton(onClick = { onSelect("") }, enabled = values.getOrNull(row)?.isNotBlank() == true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("پاک‌کردن این انتخاب") }
            }
        }
    }
}

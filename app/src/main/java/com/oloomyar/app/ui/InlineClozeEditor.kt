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
    onChange: (List<String>) -> Unit
) {
    val rows = if (step.kind == StepKind.MATCH) step.pairs.map { it.left } else step.classifyItems.map { it.text }
    var active by rememberSaveable(step.id) { mutableIntStateOf(-1) }
    val replace: (Int, String) -> Unit = { index, value ->
        if (!locked(index)) {
            onChange(List(rows.size) { if (it == index) value else values.getOrElse(it) { "" } })
        }
        active = -1
    }
    val blank: @Composable (Int) -> Unit = { index ->
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
            modifier = Modifier.fillMaxSize().testTag("blank-${step.id}-$index")
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
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(workbookBidi(value.ifBlank { "انتخاب ${workbookFa(index + 1)}" }), modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Icon(when(state) {
                    ResponseMark.CORRECT -> Icons.Default.Check
                    ResponseMark.INCORRECT -> Icons.Default.Close
                    else -> Icons.Default.ExpandMore
                }, null, Modifier.size(16.dp))
            }
        }
    }

    val template = step.clozeTemplate
    if (template != null) {
        val document = remember(step.id, template) { ClozeDocument.parse(template, step.id) }
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)
        if (showInstructions) Text("برای انتخاب یا تغییر پاسخ، روی همان جای خالی بزن. لمس دوباره فقط انتخاب همان خانه را پاک می‌کند.",
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(bottom = 10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maximum = with(density) { (maxWidth - 12.dp).roundToPx() }.coerceAtLeast(1)
            val placeholders = rows.indices.associate { index ->
                val value = values.getOrElse(index) { "" }.ifBlank { "انتخاب ${workbookFa(index + 1)}" }
                val measured = measurer.measure(workbookBidi(value), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    constraints = Constraints(maxWidth = (maximum - with(density) { 44.dp.roundToPx() }).coerceAtLeast(1)))
                val width = (measured.size.width + with(density) { 44.dp.roundToPx() })
                    .coerceIn(with(density) { 48.dp.roundToPx() }.coerceAtMost(maximum), maximum)
                val height = maxOf(measured.size.height + with(density) { 12.dp.roundToPx() }, with(density) { 48.dp.roundToPx() })
                "blank$index" to InlineTextContent(Placeholder(with(density) { width.toSp() }, with(density) { height.toSp() }, PlaceholderVerticalAlign.Center)) { blank(index) }
            }
            val content = buildAnnotatedString {
                document.tokens.forEach { token -> when (token) {
                    is ClozeToken.Text -> append(workbookBidi(token.value))
                    is ClozeToken.Blank -> appendInlineContent("blank${token.index}", "................")
                }
                }
            }
            Text(content, Modifier.fillMaxWidth(), style = style, inlineContent = placeholders)
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEachIndexed { index, label ->
                Surface(color = SurfaceMuted, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkbookText(label)
                        Box(Modifier.widthIn(min = 130.dp, max = 280.dp).heightIn(min = 52.dp)) { blank(index) }
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("جای خالی ${workbookFa(row + 1)}", style = MaterialTheme.typography.titleLarge)
            WorkbookText(if (step.kind == StepKind.MATCH) step.pairs[row].left else step.classifyItems[row].text,
                Modifier.padding(top = 8.dp))
            Text("یک گزینه را انتخاب کن", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp).testTag("choice-sheet"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(order, key = { it }) { choice ->
                    val capacity = if (step.allowTokenReuse || step.kind == StepKind.CLASSIFY) Int.MAX_VALUE else
                        step.pairs.count { it.right == choice }.coerceAtLeast(1)
                    val usedElsewhere = values.indices.count { it != row && values[it] == choice }
                    val available = usedElsewhere < capacity
                    OutlinedButton(onClick = { onSelect(choice) }, enabled = available,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("cloze-choice-$choice"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (values.getOrNull(row) == choice) SelectionSoft else MaterialTheme.colorScheme.surface)) {
                        WorkbookText(choice, Modifier.weight(1f))
                        if (!available) Text("استفاده شده", style = MaterialTheme.typography.labelSmall)
                    }
                }
                item {
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("بستن") }
                    TextButton(onClick = { onSelect("") }, enabled = values.getOrNull(row)?.isNotBlank() == true, modifier = Modifier.fillMaxWidth()) {
                        Text("پاک‌کردن این انتخاب")
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }
            }
        }
    }
}

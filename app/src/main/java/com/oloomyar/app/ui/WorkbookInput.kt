@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.oloomyar.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.theme.*

internal fun workbookFa(value: Int) = value.toString().map { if (it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it - '0'] else it }.joinToString("")

@Composable
internal fun WorkbookInput(step: LearningStep, answers: List<String>, locked: Boolean, onChange: (List<String>) -> Unit) {
    when (step.kind) {
        StepKind.SINGLE, StepKind.MULTI -> {
            if (step.kind == StepKind.MULTI) {
                Text("${workbookFa(step.pick)} گزینه انتخاب کن • ${workbookFa(answers.size)} انتخاب شده", color = Muted)
                if (answers.size == step.pick && !locked) Text("برای جایگزینی یک گزینه، ابتدا یکی از انتخاب‌ها را لغو کن.", color = Muted)
            }
            step.options.forEachIndexed { index, option ->
                val selected = index.toString() in answers
                ChoiceSurface(option.text, selected, !locked, multi = step.kind == StepKind.MULTI) {
                    if (step.kind == StepKind.SINGLE) onChange(listOf(index.toString()))
                    else if (selected) onChange(answers - index.toString())
                    else if (answers.size < step.pick) onChange(answers + index.toString())
                }
            }
        }
        StepKind.MATCH, StepKind.CLASSIFY -> {
            val isBlank = step.kind == StepKind.MATCH
            val rows = if (isBlank) step.pairs.map { it.left } else step.classifyItems.map { it.text }
            var active by rememberSaveable(step.id) { mutableIntStateOf(answers.indexOfFirst { it.isBlank() }.coerceAtLeast(0).coerceAtMost(rows.lastIndex)) }
            var showSummary by rememberSaveable(step.id) { mutableStateOf(false) }
            var lastFilled by rememberSaveable(step.id) { mutableIntStateOf(-1) }
            val label = if (isBlank) "جای خالی" else "عبارت"
            Text("${workbookFa(answers.count { it.isNotBlank() })} از ${workbookFa(rows.size)} پاسخ ثبت شده", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.indices.forEach { index ->
                    val value = answers.getOrElse(index) { "" }
                    FilterChip(
                        selected = active == index,
                        onClick = { active = index },
                        label = { Text(workbookFa(index + 1) + if (value.isNotBlank()) " ✓" else "") },
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "$label ${workbookFa(index + 1)}، ${value.ifBlank { "بی‌پاسخ" }}"
                        }
                    )
                }
            }
            Surface(color = BlueSoft, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Blue.copy(alpha = .14f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OloomPill("$label ${workbookFa(active + 1)} از ${workbookFa(rows.size)}", Blue, MaterialTheme.colorScheme.surface)
                    WorkbookText(rows[active], bold = true)
                    WorkbookText("انتخاب شما: ${answers.getOrElse(active) { "" }.ifBlank { "هنوز انتخاب نشده" }}")
                }
            }
            if (!locked) {
                Text(if (isBlank) "همهٔ گزینه‌ها • یک گزینه برای این جای خالی انتخاب کن" else "گروه مناسب این عبارت را انتخاب کن", style = MaterialTheme.typography.titleMedium)
                if (isBlank) Text("در صورت نیاز، انتخاب تکراری هم ممکن است.", color = Muted)
                val choices = if (isBlank) WorkbookAnswers.choices(step, active) else step.categories
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    choices.forEach { choice ->
                        val selected = answers.getOrElse(active) { "" } == choice
                        FilterChip(selected = selected, onClick = {
                            val updated = MutableList(rows.size) { answers.getOrElse(it) { "" } }
                            updated[active] = choice
                            lastFilled = active
                            onChange(updated)
                            val next = (active + 1 until rows.size).firstOrNull { updated[it].isBlank() }
                                ?: (0 until active).firstOrNull { updated[it].isBlank() }
                            if (next != null) active = next
                        }, label = { WorkbookText(choice) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                if (lastFilled >= 0) Text("پاسخ $label ${workbookFa(lastFilled + 1)} ثبت شد؛ برای تغییر، شمارهٔ آن را لمس کن.", color = Muted)
            }
            TextButton(onClick = { showSummary = !showSummary }) { Text(if (showSummary) "بستن مرور انتخاب‌ها" else "مرور همهٔ انتخاب‌ها") }
            if (showSummary || locked) rows.indices.forEach { index ->
                WorkbookText("${workbookFa(index + 1)}) ${answers.getOrElse(index) { "" }.ifBlank { "بی‌پاسخ" }}")
            }
        }
        StepKind.ORDER -> {
            val order = answers.mapNotNull { it.toIntOrNull() }.ifEmpty { step.orderItems.indices.toList() }
            Text("با دکمه‌های بالا و پایین، ترتیب را کامل کن.", color = Muted)
            order.forEachIndexed { position, item ->
                Surface(color = SurfaceMuted, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Border)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = PurpleSoft, shape = RoundedCornerShape(12.dp)) {
                            Text(workbookFa(position + 1), color = Purple, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            WorkbookText(step.orderItems[item])
                            Row {
                            TextButton(enabled = !locked && position > 0, onClick = {
                                val changed = order.toMutableList(); changed[position] = order[position - 1]; changed[position - 1] = item
                                onChange(changed.map { it.toString() })
                            }) { Text("بالا") }
                            TextButton(enabled = !locked && position < order.lastIndex, onClick = {
                                val changed = order.toMutableList(); changed[position] = order[position + 1]; changed[position + 1] = item
                                onChange(changed.map { it.toString() })
                            }) { Text("پایین") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceSurface(text: String, selected: Boolean, enabled: Boolean, multi: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
            selected = selected, enabled = enabled,
            role = if (multi) Role.Checkbox else Role.RadioButton, onClick = onClick
        ),
        color = if (selected) BlueSoft else SurfaceMuted,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Blue else Border),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (multi) Checkbox(selected, onCheckedChange = null, enabled = enabled)
            else RadioButton(selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            WorkbookText(text, Modifier.weight(1f))
        }
    }
}


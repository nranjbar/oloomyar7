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
internal fun WorkbookInput(step: LearningStep, answers: List<String>, locked: Boolean, practice: QuestionPractice? = null, onChange: (List<String>) -> Unit) {
    when (step.kind) {
        StepKind.SINGLE, StepKind.MULTI -> {
            if (step.kind == StepKind.MULTI) {
                Text("${workbookFa(step.pick)} گزینه انتخاب کن • ${workbookFa(answers.size)} انتخاب شده", color = Muted)
                if (answers.size == step.pick && !locked) Text("برای جایگزینی یک گزینه، ابتدا یکی از انتخاب‌ها را لغو کن.", color = Muted)
            }
            step.options.indices.shuffled(kotlin.random.Random((practice?.shuffleSeed ?: 0) xor step.id.hashCode())).forEach { index ->
                val option = step.options[index]
                val selected = index.toString() in answers
                ChoiceSurface(option.text, selected, !locked, multi = step.kind == StepKind.MULTI) {
                    if (step.kind == StepKind.SINGLE) onChange(listOf(index.toString()))
                    else if (selected) onChange(answers - index.toString())
                    else if (answers.size < step.pick) onChange(answers + index.toString())
                }
            }
        }
        StepKind.MATCH, StepKind.CLASSIFY -> InlineFillBlanksQuestion(
            step, answers, practice?.shuffleSeed ?: 0,
            mark = { row -> practice?.mark(step, row) ?: if (answers.getOrNull(row).isNullOrBlank()) ResponseMark.EMPTY else ResponseMark.SELECTED },
            locked = { row -> locked || practice?.locked(step, row) == true },
            onChange = onChange
        )
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

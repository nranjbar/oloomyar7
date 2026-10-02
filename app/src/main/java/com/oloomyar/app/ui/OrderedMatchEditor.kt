package com.oloomyar.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oloomyar.app.model.LearningStep
import com.oloomyar.app.ui.theme.Blue
import com.oloomyar.app.ui.theme.BlueSoft
import com.oloomyar.app.ui.theme.Border
import com.oloomyar.app.ui.theme.Green
import com.oloomyar.app.ui.theme.GreenSoft
import com.oloomyar.app.ui.theme.Ink
import com.oloomyar.app.ui.theme.Muted
import com.oloomyar.app.ui.theme.Orange
import com.oloomyar.app.ui.theme.OrangeSoft
import kotlin.random.Random

/**
 * One shared interaction for source word banks and ordinary matching questions.
 *
 * The learner works through rows in source order. The complete source word bank stays
 * visible for every row, while a row-specific `choices` list is used for workbook
 * questions that print two alternatives beside each blank.
 */
@Composable
internal fun OrderedMatchEditor(
    questionId: String,
    step: LearningStep,
    answers: Map<String, String>,
    locked: Boolean,
    showErrors: Boolean,
    onAnswersChange: (Map<String, String>) -> Unit
) {
    var activeIndex by remember(questionId, step.id) { mutableIntStateOf(0) }
    val isBlankQuestion = remember(step.id) {
        step.prompt.contains("جای خالی") || step.pairs.any { it.left.contains("جای خالی") }
    }
    val fallbackChoices = remember(questionId, step.id) {
        step.pairs.map { it.right }.distinct().shuffled(Random((questionId + step.id).hashCode()))
    }
    val activePair = step.pairs.getOrNull(activeIndex)
    val choices = when {
        activePair == null -> emptyList()
        activePair.choices.isNotEmpty() -> activePair.choices
        step.matchChoices.isNotEmpty() -> step.matchChoices
        else -> fallbackChoices
    }

    LaunchedEffect(showErrors, answers) {
        if (showErrors) {
            val firstWrong = step.pairs.indexOfFirst { answers[it.left] != it.right }
            if (firstWrong >= 0) activeIndex = firstWrong
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (step.pairs.size > 1) {
            Text(
                if (isBlankQuestion) {
                    "جای خالی ${toPersianNumber(activeIndex + 1)} از ${toPersianNumber(step.pairs.size)}"
                } else {
                    "مورد ${toPersianNumber(activeIndex + 1)} از ${toPersianNumber(step.pairs.size)}"
                },
                color = Blue,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
        }

        step.pairs.forEachIndexed { index, pair ->
            val selectedValue = answers[pair.left]
            val isWrong = showErrors && selectedValue != pair.right
            val isCorrect = locked && selectedValue == pair.right
            val isActive = index == activeIndex && !locked
            val borderColor = when {
                isWrong -> Orange
                isCorrect -> Green
                isActive -> Blue
                else -> Border
            }
            val background = when {
                isWrong -> OrangeSoft
                isCorrect -> GreenSoft
                isActive -> BlueSoft
                else -> Color.White
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clickable(enabled = !locked) { activeIndex = index },
                color = background,
                shape = RoundedCornerShape(17.dp),
                border = BorderStroke(if (isActive || isWrong || isCorrect) 1.6.dp else 1.dp, borderColor)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = when {
                            isWrong -> Orange
                            isCorrect -> Green
                            isActive -> Blue
                            else -> Color.White
                        },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Row(
                            Modifier.size(30.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                isWrong -> Icon(Icons.Default.Error, "نیاز به اصلاح", tint = Color.White, modifier = Modifier.size(18.dp))
                                isCorrect -> Icon(Icons.Default.Check, "درست", tint = Color.White, modifier = Modifier.size(18.dp))
                                else -> Text(toPersianNumber(index + 1), color = if (isActive) Color.White else Muted, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(pair.left, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, lineHeight = 23.sp)
                        Text(
                            selectedValue ?: "پاسخ را از گزینه‌های پایین انتخاب کن",
                            color = if (selectedValue == null) Muted else borderColor,
                            fontSize = 13.sp,
                            fontWeight = if (selectedValue == null) FontWeight.Normal else FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        if (isWrong) {
                            Text("این جای خالی نیاز به اصلاح دارد.", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            if (isBlankQuestion) "گزینه‌های جای خالی ${toPersianNumber(activeIndex + 1)}" else "گزینه‌های مورد انتخاب‌شده",
            color = Ink,
            fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right
        )

        choices.forEach { value ->
            val selectedHere = activePair?.let { answers[it.left] == value } == true
            val rowSpecificChoices = activePair?.choices?.isNotEmpty() == true
            val usesSingleUseWordBank = step.matchChoices.isNotEmpty()
            val usedByIndex = if (rowSpecificChoices || !usesSingleUseWordBank) {
                -1
            } else {
                step.pairs.indexOfFirst { it.left != activePair?.left && answers[it.left] == value }
            }
            val available = !locked && (usedByIndex < 0 || selectedHere)
            OutlinedButton(
                onClick = {
                    val pair = activePair ?: return@OutlinedButton
                    val updated = answers + (pair.left to value)
                    onAnswersChange(updated)
                    val nextUnanswered = step.pairs.indices.firstOrNull {
                        it > activeIndex && updated[step.pairs[it].left].isNullOrBlank()
                    }
                    activeIndex = nextUnanswered ?: (activeIndex + 1).coerceAtMost(step.pairs.lastIndex)
                },
                enabled = available,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(if (selectedHere) 1.6.dp else 1.dp, if (selectedHere) Blue else Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selectedHere) BlueSoft else Color.White,
                    contentColor = Ink,
                    disabledContainerColor = Color(0xFFF4F6F9),
                    disabledContentColor = Muted
                )
            ) {
                Icon(
                    if (selectedHere) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selectedHere) Blue else if (available) Border else Muted,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(value, textAlign = TextAlign.Right, lineHeight = 23.sp)
                    if (usedByIndex >= 0) {
                        Text(
                            "انتخاب‌شده برای ${if (isBlankQuestion) "جای خالی" else "مورد"} ${toPersianNumber(usedByIndex + 1)}",
                            color = Muted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Right
                        )
                    }
                }
            }
        }
    }
}

private fun toPersianNumber(value: Int): String = value.toString().map { digit ->
    when (digit) {
        '0' -> '۰'
        '1' -> '۱'
        '2' -> '۲'
        '3' -> '۳'
        '4' -> '۴'
        '5' -> '۵'
        '6' -> '۶'
        '7' -> '۷'
        '8' -> '۸'
        '9' -> '۹'
        else -> digit
    }
}.joinToString("")


@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.oloomyar.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.theme.*
import kotlin.random.Random

/** Native source presenter shared by the two reference-answer routes.
 * Scientific figures are isolated from prose; reference blocks have no controls.
 */
@Composable
internal fun NativeWorkbookContent(
    question: ChapterQuestion,
    blocks: List<SourceBlock>,
    practice: QuestionPractice? = null,
    onChange: (LearningStep, List<String>) -> Unit = { _, _ -> }
) {
    val embedded = remember(blocks) { mutableSetOf<Int>().apply {
        blocks.forEach { block ->
            if (block.type in setOf("cloze", "reaction", "flow", "input", "barChart")) add(block.step)
            block.rows.flatten().forEach { cell ->
                Regex("""\{\{(\d+)(?::\d+)?\}\}""").findAll(cell).forEach { add(it.groupValues[1].toInt()) }
            }
        }
    } }
    val display = practice
    Column(Modifier.fillMaxWidth().testTag(if (practice == null) "native-reference-answer" else "native-source-question"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        blocks.forEachIndexed { index, block ->
            key(question.id, index, block.type, block.asset) { when (block.type) {
                "text" -> WorkbookText(block.text, Modifier.fillMaxWidth())
                "figure" -> block.asset?.let { asset ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (block.text.isNotBlank()) Text(block.text, color = Muted, style = MaterialTheme.typography.bodySmall)
                        WorkbookImage(asset, block.text.ifBlank { "شکل سؤال ${workbookFa(question.sourceNumber)}" }, maxImageHeight = 300.dp)
                    }
                }
                "cloze" -> NativeWorkbookEdit(question, block.step, block.text, display, onChange)
                "input" -> NativeWorkbookEdit(question, block.step, null, display, onChange)
                "table" -> SourceTable(block, question, display, onChange)
                "math" -> SourceEquation(block.parts)
                "reaction", "flow" -> {
                    if(practice!=null) Text(if(block.type=="flow") "مسیر را از چپ به راست کامل کن." else "معادله را از چپ به راست کامل کن.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column {
                            FlowRow(Modifier.fillMaxWidth().testTag(if(block.type=="flow")"native-ltr-flow" else "native-reaction"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                block.text.split(Regex("(?=[+→])|(?<=[+→])")).forEach { fragment ->
                                    Box(Modifier.widthIn(min = 24.dp, max = 180.dp)) {
                                        if(practice==null)WorkbookText(Regex("""\{\{\d+\}\}""").replace(fragment.trim(),"................"))
                                        else editReactionFragment(fragment, block.step, question, display, onChange)
                                    }
                                }
                            }
                        }
                    }
                }
                "barChart" -> if (practice != null) NativeBarChart(question.steps[block.step], display, onChange)
                else -> error("Unknown source block ${block.type}")
            } }
        }
        if (practice != null) question.steps.forEachIndexed { index, step ->
            if (index !in embedded) {
                HorizontalDivider(color = Border)
                Text(if (question.steps.size == 1) "پاسخ تعاملی" else "پاسخ تعاملی • قسمت ${workbookFa(index + 1)}",
                    color = Blue, style = MaterialTheme.typography.titleMedium)
                WorkbookText(step.prompt)
                NativeWorkbookEdit(question, index, null, display, onChange)
            }
        }
    }
}

/** A static composable call site for each editor, scoped to its actual source ID.
 * Avoid retaining a captured composable lambda as heterogeneous source blocks
 * are removed/replaced during question navigation and screen disposal.
 */
@Composable
private fun NativeWorkbookEdit(question: ChapterQuestion, index: Int, template: String?, state: QuestionPractice?,
                               onChange: (LearningStep, List<String>) -> Unit) {
    val step = question.steps[index]
    key(question.id, step.id, template) {
        if (state == null) {
            if (template != null) WorkbookText(Regex("""\{\{\d+\}\}""").replace(template, "................"))
            else if (step.kind in setOf(StepKind.SINGLE, StepKind.MULTI)) step.options.forEach { WorkbookText(it.text) }
        } else if (template != null && step.kind in setOf(StepKind.MATCH, StepKind.CLASSIFY)) {
            InlineFillBlanksQuestion(step.copy(clozeTemplate = template), state.values(step), state.shuffleSeed,
                mark = { state.mark(step, it) }, locked = { state.locked(step, it) },
                showInstructions = false, nativeLayout = true) { onChange(step, it) }
        } else WorkbookInput(step, state.values(step),
            (state.revealed || (step.kind !in setOf(StepKind.MATCH, StepKind.CLASSIFY) && state.locked(step))),
            state) { onChange(step, it) }
    }
}

@Composable
private fun editReactionFragment(text: String, index: Int, question: ChapterQuestion, state: QuestionPractice?,
                                 onChange: (LearningStep, List<String>) -> Unit) {
    val step = question.steps[index]
    if (text.contains("{{")) InlineFillBlanksQuestion(step.copy(clozeTemplate = text.trim()), state?.values(step).orEmpty(), state?.shuffleSeed ?: 0,
        mark = { state?.mark(step, it) ?: ResponseMark.EMPTY }, locked = { (state?.revealed == true || state?.locked(step, it) == true) },
        showInstructions = false, nativeLayout = true) { onChange(step, it) }
    else WorkbookText(text.trim())
}

/** Fractions are true numerator/denominator layouts, never a reordered RTL slash. */
@Composable
private fun SourceEquation(parts: List<MathPart>) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        FlowRow(Modifier.fillMaxWidth().testTag("native-equation").padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            parts.forEachIndexed { index, part ->
                if (part.numerator != null && part.denominator != null) Column(Modifier.align(Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    WorkbookText(part.numerator)
                    HorizontalDivider(Modifier.widthIn(min = 38.dp).width(56.dp), color = Ink)
                    WorkbookText(part.denominator)
                } else Text(workbookBidi(part.text), Modifier.align(Alignment.CenterVertically).testTag("equation-token-$index"),
                    color = Ink, style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = if (Regex("[آ-ی]").containsMatchIn(part.text))
                            androidx.compose.ui.text.style.TextDirection.Rtl
                        else androidx.compose.ui.text.style.TextDirection.Ltr))
            }
        }
    }
}

@Composable
private fun SourceTable(block: SourceBlock, question: ChapterQuestion, state: QuestionPractice?,
                        onChange: (LearningStep, List<String>) -> Unit) {
    // A short, wide comparison is transposed without changing a cell or its
    // answer index. Every column then remains visible on a portrait phone.
    val table = remember(block) {
        if (block.headers.size > 4 && block.rows.size <= 3) block.copy(
            headers = listOf(block.headers.first()) + block.rows.map { it.first() },
            rows = block.headers.indices.drop(1).map { column -> listOf(block.headers[column]) + block.rows.map { it[column] } },
            widths = emptyList()
        ) else block
    }
    val cols = table.headers.size
    val weights = if (table.widths.size == cols) table.widths else List(cols) { 1f }
    if (state != null && table.rows.flatten().any { it.contains("{{") }) {
        Text("برای تکمیل جدول، خانه‌های شماره‌دار را لمس کن.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    Column(Modifier.fillMaxWidth().testTag("native-source-table").clip(RoundedCornerShape(12.dp)).border(1.dp, Border, RoundedCornerShape(12.dp))) {
            (listOf(table.headers) + table.rows).forEachIndexed { row, cells ->
                SourceTableRow(cells, weights, row) { _, cell ->
                                val token = Regex("""\{\{(\d+)(?::(\d+))?\}\}""").find(cell)
                                if (token == null || state == null) WorkbookText(Regex("""\{\{\d+(?::\d+)?\}\}""").replace(cell, "................"), bold = row == 0, style = MaterialTheme.typography.bodySmall)
                                else {
                                    val step = question.steps[token.groupValues[1].toInt()]
                                    if (step.kind in setOf(StepKind.MATCH, StepKind.CLASSIFY)) {
                                        val template = Regex("""\{\{\d+:(\d+)\}\}""").replace(cell) { "{{${it.groupValues[1]}}}" }
                                        InlineFillBlanksQuestion(step.copy(clozeTemplate = template), state.values(step), state.shuffleSeed,
                                            mark = { state.mark(step, it) }, locked = { (state.revealed || state.locked(step, it)) },
                                            showInstructions = false, nativeLayout = true, compact = true) { onChange(step, it) }
                                    } else NativeOptionCell(step, state) { onChange(step, it) }
                                }
                }
            }
        }
}

/** Measure each cell once. Draw the grid at the tallest cell's height so long
 * Persian labels keep every row aligned without intrinsic measurement of blanks.
 */
@Composable
private fun SourceTableRow(cells: List<String>, weights: List<Float>, row: Int, content: @Composable (Int,String) -> Unit) {
    val density=LocalDensity.current
    val header=row==0
    fun widths(total:Int):List<Int> {
        val minimum=minOf(with(density) { 56.dp.roundToPx() }, total/weights.size)
        val remaining=(total-minimum*weights.size).coerceAtLeast(0)
        val columns=weights.map { minimum+(remaining*it/weights.sum()).toInt() }.toMutableList()
        columns[columns.lastIndex]+=total-columns.sum()
        return columns
    }
    Layout(content = {
        cells.forEachIndexed { index,text -> Box(Modifier.testTag("native-table-cell-$row-$index").padding(horizontal=4.dp,vertical=8.dp)) { content(index,text) } }
    }, modifier = Modifier.fillMaxWidth().drawBehind {
        val pixels=widths(size.width.toInt())
        var offset=0f
        pixels.forEachIndexed { index,width ->
            val x=if(layoutDirection==LayoutDirection.Rtl)size.width-offset-width else offset
            drawRect(if(header)BlueSoft else if(index==0)SurfaceMuted else Color.White,Offset(x,0f),Size(width.toFloat(),size.height))
            drawLine(Border,Offset(x,0f),Offset(x,size.height),1.dp.toPx())
            offset+=width
        }
        drawLine(Border,Offset(0f,size.height),Offset(size.width,size.height),1.dp.toPx())
    }) { measurables,constraints ->
        val pixels=widths(constraints.maxWidth)
        val placeables=measurables.mapIndexed { index,m ->
            m.measure(Constraints(minWidth=pixels[index],maxWidth=pixels[index],maxHeight=constraints.maxHeight))
        }
        val height=maxOf(70.dp.roundToPx(),placeables.maxOf { it.height })
        layout(pixels.sum(),height) {
            var x=0
            placeables.forEachIndexed { index,p -> p.placeRelative(x,0);x+=pixels[index] }
        }
    }
}

@Composable
private fun NativeOptionCell(step: LearningStep, state: QuestionPractice, onChange: (List<String>) -> Unit) {
    var open by rememberSaveable(step.id) { mutableStateOf(false) }
    val selected = state.values(step).firstOrNull()?.toIntOrNull()
    val mark = state.mark(step, 0)
    val fill = when (mark) { ResponseMark.CORRECT -> GreenSoft; ResponseMark.INCORRECT -> DangerSoft; else -> SelectionSoft }
    OutlinedButton(enabled = (!state.revealed && !state.locked(step)), onClick = { onChange(emptyList()); open = true },
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected == null) BlueSoft else fill),
        contentPadding = PaddingValues(horizontal=4.dp,vertical=6.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("table-choice-${step.id}")) { WorkbookText(selected?.let { step.options[it].text } ?: "انتخاب", style = MaterialTheme.typography.labelMedium) }
    if (open) ModalBottomSheet(onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(20.dp).navigationBarsPadding()
            .testTag("table-choice-sheet-${step.id}"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("انتخاب پاسخ", style = MaterialTheme.typography.titleLarge) }
            items(WorkbookAnswers.optionOrder(step, state.shuffleSeed), key = { it }) { i ->
                OutlinedButton(onClick = { if (open) { open = false; onChange(listOf(i.toString())) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("table-option-${step.id}-$i")) {
                    WorkbookText(step.options[i].text)
                }
            }
            item { TextButton(onClick = { open = false }) { Text("بستن") } }
        }
    }
}

/** Student draws each bar by choosing its height; no completed chart is prefilled. */
@Composable
private fun NativeBarChart(step: LearningStep, state: QuestionPractice?,
                           onChange: (LearningStep, List<String>) -> Unit) {
    val values = state?.values(step).orEmpty()
    fun number(s: String) = s.map { if (it in '۰'..'۹') '0'+("۰۱۲۳۴۵۶۷۸۹".indexOf(it)) else it }.joinToString("").toFloatOrNull() ?: 0f
    val scale = number(values.getOrElse(0) { "" })
    Text("رسم تعاملی نمودار • ابتدا فاصلهٔ درجه‌های محور را انتخاب کن و سپس ارتفاع هر ستون را تنظیم کن.", color = Blue, style = MaterialTheme.typography.bodySmall)
    InlineFillBlanksQuestion(step.copy(clozeTemplate = "فاصلهٔ درجه‌های محور عمودی : {{0}}"), values, state?.shuffleSeed ?: 0,
        mark = { state?.mark(step, it) ?: ResponseMark.EMPTY }, locked = { (state?.revealed == true || state?.locked(step, it) == true) },
        showInstructions = false, nativeLayout = true) { onChange(step, it) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val labels = (4 downTo 0).map { if (scale > 0) workbookFa((scale*it).toInt()) else "—" }
        val labelStyle = MaterialTheme.typography.bodySmall
        val naturalWidth = with(density) { labels.maxOf { measurer.measure(it,style=labelStyle).size.width }.toDp() } + 8.dp
        val axisWidth = maxOf(52.dp,naturalWidth).coerceAtMost(maxWidth*.45f)
        val labelHeight = labels.sumOf { measurer.measure(it,style=labelStyle,
            constraints=Constraints(maxWidth=with(density) { axisWidth.roundToPx() })).size.height }
        val chartHeight = maxOf(210.dp,with(density) { labelHeight.toDp() }+16.dp)
        Row(Modifier.fillMaxWidth().height(chartHeight).testTag("student-bar-chart"), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.width(axisWidth).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                labels.forEach { Text(it, color = Muted, style = labelStyle) }
            }
            Row(Modifier.weight(1f).fillMaxHeight().border(1.dp, Border).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                (0..4).forEach { bar ->
                    val value = number(values.getOrElse(bar+2) { "" })
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        val fraction = if (scale > 0) (value/(scale*4)).coerceIn(0f,1f) else 0f
                        Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).background(when (state?.mark(step,bar+2)) {
                            ResponseMark.CORRECT -> Green; ResponseMark.INCORRECT -> Danger; else -> Yellow
                        }))
                    }
                }
            }
        }
        }
    }
    Text("تولید سیمان ( تن )", color = Muted, style = MaterialTheme.typography.bodySmall)
    for (i in 0..4) InlineFillBlanksQuestion(step.copy(clozeTemplate = "${listOf("۱۳۸۳","۱۳۸۹","۱۳۹۲","۱۳۹۴","۱۳۹۶")[i]} : {{${i+2}}}"), values, state?.shuffleSeed ?: 0,
        mark = { state?.mark(step, it) ?: ResponseMark.EMPTY }, locked = { (state?.revealed == true || state?.locked(step, it) == true) },
        showInstructions = false, nativeLayout = true) { onChange(step, it) }
}

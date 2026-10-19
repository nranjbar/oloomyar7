package com.oloomyar.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oloomyar.app.data.WorkbookProgress
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Shared source-first UI for audited workbook chapters and the independent conceptual collection. */
@Composable
fun AuditedWorkbookApp(
    title: String,
    questions: List<ChapterQuestion>,
    progress: WorkbookProgress,
    conceptual: Boolean = false,
    initialQuestionId: String? = null,
    initialSection: String? = null,
    continueSequence: Boolean = false,
    onExit: () -> Unit
) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var part by rememberSaveable { mutableStateOf(if (conceptual) "مفهومی" else "تمرین‌های اصلی") }
    var sessionIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var questionIndex by rememberSaveable { mutableIntStateOf(0) }
    var exam by rememberSaveable { mutableStateOf(false) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var total by rememberSaveable { mutableIntStateOf(0) }
    var completed by rememberSaveable { mutableIntStateOf(0) }
    var sessionToken by rememberSaveable { mutableIntStateOf(0) }
    var confirmExit by remember { mutableStateOf(false) }
    var handledInitial by rememberSaveable { mutableStateOf(false) }
    val parts = if (conceptual) {
        listOf("مفهومی")
    } else {
        listOf("تمرین‌های اصلی", "چهارگزینه‌ای").filter { section ->
            questions.any { it.section == section }
        }
    }
    val partQuestions = questions.filter { conceptual || it.section == part }
    val start: (List<ChapterQuestion>, Boolean) -> Unit = { chosen, isExam ->
        if (chosen.isNotEmpty()) {
            sessionIds = ArrayList(chosen.map { it.id })
            questionIndex = 0; exam = isExam; score = 0; completed = 0
            total = chosen.sumOf { it.steps.size }; sessionToken++
            screen = "question"
        }
    }
    LaunchedEffect(initialQuestionId, initialSection) {
        if (!handledInitial) {
            handledInitial = true
            val requested = questions.firstOrNull { it.id == initialQuestionId }
            if (requested != null) {
                part = requested.section
                val sectionQuestions = questions.filter { it.section == requested.section }
                start(sectionQuestions, false)
                questionIndex = sectionQuestions.indexOfFirst { it.id == requested.id }.coerceAtLeast(0)
            } else if (initialSection in parts) {
                part = initialSection!!; screen = "part"
            }
        }
    }
    val goBack: () -> Unit = {
        when (screen) {
            "home" -> onExit()
            "part" -> screen = "home"
            "question" -> if (exam) confirmExit = true else { screen = "part" }
            else -> screen = "part"
        }
    }
    BackHandler(onBack = goBack)
    OloomScreen(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                OloomTopBar(
                    if (screen == "home") title else if (exam && screen == "question") "آزمون • $part" else part,
                    goBack
                )
            }
        ) { padding ->
        when (screen) {
            "home" -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    val description = if (conceptual) {
                        val firstPage = questions.minOf { it.sourcePage }
                        val lastPage = questions.maxOf { it.sourcePage }
                        "${workbookFa(questions.size)} سؤال مستقل از بخش مفهومی کتاب؛ به ترتیب صفحه‌های ${workbookFa(firstPage)} تا ${workbookFa(lastPage)}."
                    } else {
                        "صورت کامل سؤال کتاب را بخوان و پاسخ را با راهنمای مرحله‌ای کامل کن."
                    }
                    OloomHeroCard(
                        eyebrow = if (conceptual) "چالش مفهومی مستقل" else "مسیر تمرین فصل",
                        title = title,
                        description = description,
                        icon = if (conceptual) Icons.Default.Psychology else Icons.Default.Science,
                        startColor = if (conceptual) Purple else Navy,
                        endColor = if (conceptual) Blue else Aqua
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OloomHeroFact("${workbookFa(questions.size)} سؤال", Icons.Default.Quiz, Modifier.weight(1f))
                            OloomHeroFact("تمرین و آزمون", Icons.Default.Assignment, Modifier.weight(1f))
                            OloomHeroFact("کاملاً آفلاین", Icons.Default.OfflineBolt, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    OloomSectionHeader(
                        title = if (conceptual) "مسیر سؤال‌های مفهومی" else "بخش‌های این فصل",
                        subtitle = "پیشرفت هر بخش جداگانه ذخیره می‌شود.",
                        color = if (conceptual) Purple else Blue,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(parts) { section ->
                    val selected = questions.filter { conceptual || it.section == section }
                    val done = selected.count { progress.done(it.id) }
                    WorkbookPanel(accent = if (conceptual) Purple else Blue) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = if (conceptual) PurpleSoft else BlueSoft, shape = RoundedCornerShape(17.dp)) {
                                Icon(
                                    if (conceptual) Icons.Default.AutoAwesome else if (section == "چهارگزینه‌ای") Icons.Default.FactCheck else Icons.Default.School,
                                    null,
                                    tint = if (conceptual) Purple else Blue,
                                    modifier = Modifier.padding(11.dp).size(25.dp)
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(if (conceptual) title else section, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Right)
                                Text("${workbookFa(selected.size)} سؤال • ${workbookFa(done)} تمرین‌شده", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            OloomPill("${workbookFa(done)}/${workbookFa(selected.size)}", if (conceptual) Purple else Blue, if (conceptual) PurpleSoft else BlueSoft)
                        }
                        OloomProgressBar(if (selected.isEmpty()) 0f else done.toFloat() / selected.size, if (conceptual) Purple else Blue)
                        Button(
                            onClick = { part = section; screen = "part" },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("ورود به این بخش")
                            Spacer(Modifier.width(7.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            "part" -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val done = partQuestions.count { progress.done(it.id) }
                    val mistakes = partQuestions.filter { progress.mistake(it.id) }
                    WorkbookPanel(accent = Purple, color = PurpleSoft.copy(alpha = .4f)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(if (conceptual) title else part, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Right)
                                Text("${workbookFa(done)} از ${workbookFa(partQuestions.size)} سؤال تمرین شده", color = Muted)
                            }
                            Surface(color = PurpleSoft, shape = RoundedCornerShape(18.dp)) {
                                Icon(Icons.Default.TrackChanges, null, tint = Purple, modifier = Modifier.padding(12.dp).size(28.dp))
                            }
                        }
                        OloomProgressBar(if (partQuestions.isEmpty()) 0f else done.toFloat() / partQuestions.size, Purple)
                        val pending = partQuestions.indexOfFirst { !progress.done(it.id) && progress.draft(it.id) != null }.takeIf { it >= 0 }
                            ?: partQuestions.indexOfFirst { !progress.done(it.id) }.coerceAtLeast(0)
                        Button(onClick = { start(partQuestions, false); questionIndex = pending }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (partQuestions.any { progress.done(it.id) || progress.draft(it.id) != null }) "ادامهٔ تمرین" else "شروع تمرین")
                        }
                        OutlinedButton(onClick = { start(mistakes, false) }, enabled = mistakes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مرور موارد نیازمند تمرین (${workbookFa(mistakes.size)})")
                        }
                        OutlinedButton(onClick = { start(partQuestions, true) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Assignment, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("آزمون همین بخش")
                        }
                        Text("در آزمون، نتیجه پس از پایان نمایش داده می‌شود. برای پاسخ تشریحی، سؤال را در مسیر تمرین با راهنما مرور کن.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        progress.lastExam(part)?.let { (correct, count) -> Text("آخرین آزمون: ${workbookFa(correct)} پاسخ درست از ${workbookFa(count)} مرحله", color = Muted) }
                    }
                }
                item { OloomSectionHeader("فهرست سؤال‌ها", "برای تمرین مستقل، هر سؤال را جدا باز کن.", Blue, Modifier.padding(top = 7.dp)) }
                items(partQuestions, key = { it.id }) { question ->
                    val draft = progress.draft(question.id) != null
                    val mistake = progress.mistake(question.id)
                    val done = progress.done(question.id)
                    val statusColor = when { mistake -> Orange; done -> Green; draft -> Purple; else -> Blue }
                    val statusSoft = when { mistake -> OrangeSoft; done -> GreenSoft; draft -> PurpleSoft; else -> BlueSoft }
                    OloomCard(accent = statusColor, onClick = { start(partQuestions, false); questionIndex = partQuestions.indexOf(question) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = statusSoft, shape = RoundedCornerShape(16.dp)) {
                                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                    if (mistake) Icon(Icons.Default.PriorityHigh, null, tint = Orange)
                                    else if (done) Icon(Icons.Default.CheckCircle, null, tint = Green)
                                    else Text(workbookFa(question.sourceNumber), color = statusColor, fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("سؤال ${workbookFa(question.sourceNumber)} • ${question.title}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Right)
                                Text("صفحهٔ ${workbookFa(question.sourcePage)}" + if (conceptual) " • موضوع فصل ${workbookFa(question.relatedChapter)}" else "", color = Muted, style = MaterialTheme.typography.bodySmall)
                                Text(when {
                                    mistake -> "نیازمند مرور"
                                    done -> "تمرین شده • لمس برای مرور"
                                    draft -> "تمرین نیمه‌تمام • ادامه از محل قبلی"
                                    else -> "آمادهٔ پاسخ‌دادن"
                                }, color = statusColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 3.dp))
                            }
                            Surface(color = statusSoft, shape = CircleShape) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = statusColor, modifier = Modifier.padding(7.dp).size(17.dp))
                            }
                        }
                    }
                }
            }
            "question" -> {
                val question = questions.first { it.id == sessionIds[questionIndex] }
                key(question.id, sessionToken) {
                    WholeWorkbookQuestion(
                        question, progress, conceptual, exam, sessionToken,
                        "سؤال ${workbookFa(questionIndex + 1)} از ${workbookFa(sessionIds.size)}",
                        Modifier.padding(padding),
                        onPrevious = if (!exam && questionIndex > 0) ({ questionIndex-- }) else null,
                        onNext = if (!exam && questionIndex < sessionIds.lastIndex) ({ questionIndex++ }) else null
                    ) { correct, perfect ->
                        score += correct; completed++
                        // WholeWorkbookQuestion records real checks/reveals immediately.
                        // Keep its draft and viewport; exam submissions never clear practice gates.
                        if (questionIndex < sessionIds.lastIndex) questionIndex++
                        else {
                            if (exam) progress.saveExam(part, score, total)
                            screen = "result"
                        }
                    }
                }
            }
            "result" -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    OloomHeroCard(
                        eyebrow = if (exam) "کارنامهٔ این نوبت" else "جلسه با موفقیت تمام شد",
                        title = if (exam) "نتیجهٔ آزمون" else "تمرین کامل شد",
                        description = if (exam) "${workbookFa(score)} پاسخ درست از ${workbookFa(total)} مرحله در ${workbookFa(completed)} سؤال" else "${workbookFa(completed)} سؤال را تمرین کردی.",
                        icon = if (exam) Icons.Default.Assessment else Icons.Default.EmojiEvents,
                        startColor = if (exam) Purple else Green,
                        endColor = if (exam) Blue else Aqua
                    ) {
                        Text("پاسخ با راهنمایی یا پس از اشتباه، برای مرور دوباره علامت می‌خورد.", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { screen = "part" },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = if (exam) Purple else Green)
                        ) { Text("بازگشت به فهرست سؤال‌ها") }
                    }
                }
                if (exam) {
                    item { OloomSectionHeader("مرور سؤال‌های آزمون", "برای یادگیری پاسخ، هر سؤال را با دو تلاش و راهنمای مرحله‌ای تمرین کن.", Purple) }
                    items(sessionIds) { id ->
                        val question = questions.first { it.id == id }
                        WorkbookPanel(accent = Purple) {
                            Text("سؤال ${workbookFa(question.sourceNumber)}", style = MaterialTheme.typography.titleLarge)
                            (question.promptVisual ?: question.sourceQuestionVisual)?.let { WorkbookImage(it, "صورت سؤال ${workbookFa(question.sourceNumber)} از کتاب") }
                            WorkbookQuestionVisuals(question, "تصویر سؤال ${workbookFa(question.sourceNumber)}")
                            OutlinedButton(onClick = { start(listOf(question), false) }, modifier = Modifier.fillMaxWidth()) {
                                Text("تمرین سؤال ${workbookFa(question.sourceNumber)} با راهنما")
                            }
                        }
                    }
                }
            }
        }
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("خروج از آزمون؟") },
        text = { Text("نتیجهٔ آزمون نیمه‌تمام ثبت نمی‌شود. می‌توانی به پاسخ‌دادن ادامه بدهی.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; screen = "part" }) { Text("خروج") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("ادامهٔ آزمون") } }
    )
}

@Composable
internal fun WorkbookQuestionVisuals(question: ChapterQuestion, description: String) {
    val visuals = buildList {
        question.visual?.let { add(QuestionVisual(it, question.visualCaption)) }
        addAll(question.visuals)
        if (isEmpty()) question.originalFigureVisual?.let { add(QuestionVisual(it)) }
    }
    if (question.relatedChapter == 3 && question.section == "تمرین‌های اصلی" && question.sourceNumber == 12 && visuals.size == 3) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            visuals.forEachIndexed { index, visual ->
                Box(Modifier.weight(1f)) { WorkbookImage(visual.asset, "$description، ${listOf("شکل سمت راست", "شکل وسط", "شکل سمت چپ")[index]}", compact = true) }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        visuals.forEachIndexed { index, visual ->
            val itemDescription = visual.caption?.let { "$description: $it" }
                ?: if (visuals.size == 1) description else "$description، تصویر ${workbookFa(index + 1)}"
            WorkbookImage(visual.asset, itemDescription, visual.caption)
        }
    }
}

@Composable
internal fun WorkbookPanel(
    accent: Color? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    OloomCard(accent = accent, color = color, content = content)
}

@Composable
private fun OloomHeroFact(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

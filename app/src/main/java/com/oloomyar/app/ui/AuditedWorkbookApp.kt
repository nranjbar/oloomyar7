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
            // Completed practice may be repeated; unfinished practice keeps its draft.
            if (!isExam) chosen.filter { progress.done(it.id) && progress.draft(it.id) == null }.forEach { progress.clearDraft(it.id) }
            screen = "question"
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
                        val pending = partQuestions.indexOfFirst { progress.draft(it.id) != null }.takeIf { it >= 0 }
                            ?: partQuestions.indexOfFirst { !progress.done(it.id) }.coerceAtLeast(0)
                        Button(onClick = { start(partQuestions.drop(pending), false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
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
                    OloomCard(accent = statusColor, onClick = { start(listOf(question), false) }) {
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
                                    draft -> "تمرین نیمه‌تمام • ادامه از محل قبلی"
                                    mistake -> "نیازمند مرور"
                                    done -> "تمرین شده • لمس برای تمرین دوباره"
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
                    WorkbookQuestion(
                        question, progress, conceptual, exam, sessionToken,
                        "سؤال ${workbookFa(questionIndex + 1)} از ${workbookFa(sessionIds.size)}",
                        Modifier.padding(padding)
                    ) { correct, perfect ->
                        score += correct; completed++
                        progress.save(question.id, perfect)
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
                            WorkbookText(question.bookPrompt.orEmpty())
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
private fun WorkbookQuestion(
    question: ChapterQuestion,
    progress: WorkbookProgress,
    conceptual: Boolean,
    exam: Boolean,
    sessionToken: Int,
    position: String,
    modifier: Modifier,
    onFinish: (Int, Boolean) -> Unit
) {
    var json by rememberSaveable(question.id, sessionToken) {
        mutableStateOf(if (exam) "{}" else upgradedPracticeDraft(progress.draft(question.id)))
    }
    val state = remember(json) { runCatching { JSONObject(json) }.getOrElse { JSONObject() } }
    val current = state.optInt("step").coerceIn(question.steps.indices)
    val step = question.steps[current]
    val answers = state.optJSONObject("answers")?.takeIf { it.has(step.id) }?.stringList(step.id)
        ?: if (step.kind == StepKind.ORDER) WorkbookAnswers.initialOrder(step) else emptyList()
    val failedAttempts = state.optJSONObject("attempts")?.optInt(step.id) ?: 0
    val hints = state.optJSONObject("hints")?.optInt(step.id) ?: 0
    val canReveal = PracticeAttempts.canReveal(failedAttempts, hints)
    val rawStatus = state.optJSONObject("status")?.optInt(step.id) ?: 0
    val status = if (rawStatus == 3 && !canReveal) 0 else rawStatus
    val locked = status >= 2
    val complete = WorkbookAnswers.isComplete(step, answers)
    val lastSubmitted = state.optJSONObject("lastSubmitted")?.takeIf { it.has(step.id) }?.stringList(step.id)
    val canSubmit = complete && (exam || PracticeAttempts.canSubmit(step, answers, lastSubmitted))
    val hintView = remember(step.id) { BringIntoViewRequester() }
    val answerView = remember(step.id) { BringIntoViewRequester() }
    val list = rememberLazyListState()
    val coroutine = rememberCoroutineScope()
    val update: (JSONObject.() -> Unit) -> Unit = { mutation ->
        val changed = JSONObject(json).apply(mutation)
        json = changed.toString()
        if (!exam) progress.saveDraft(question.id, json)
    }
    LaunchedEffect(step.id, hints, locked) {
        if (!exam && (hints > 0 || locked)) {
            // The interaction item may be off-screen when a draft is resumed.
            // Compose it and wait for layout before requesting a child anchor.
            list.scrollToItem(3)
            withFrameNanos { }
            if (locked) answerView.bringIntoView() else hintView.bringIntoView()
        }
    }
    val advance: () -> Unit = {
        if (current < question.steps.lastIndex) {
            update { put("step", current + 1) }
            coroutine.launch { list.animateScrollToItem(3) }
        } else {
            val final = JSONObject(json)
            val results = final.optJSONObject("status") ?: JSONObject()
            val hintCounts = final.optJSONObject("hints") ?: JSONObject()
            val correct = question.steps.count { results.optInt(it.id) == 2 }
            val perfect = correct == question.steps.size && final.stringList("wrong").isEmpty() &&
                question.steps.all { hintCounts.optInt(it.id) == 0 }
            onFinish(correct, perfect)
        }
    }
    val submit: () -> Unit = submit@{
        if (!locked) {
            val latest = JSONObject(json)
            val latestAnswers = latest.optJSONObject("answers")?.takeIf { it.has(step.id) }?.stringList(step.id) ?: answers
            val latestSubmitted = latest.optJSONObject("lastSubmitted")?.takeIf { it.has(step.id) }?.stringList(step.id)
            if (!exam && (latest.child("status").optInt(step.id) >= 2 || !PracticeAttempts.canSubmit(step, latestAnswers, latestSubmitted))) return@submit
            val correct = WorkbookAnswers.isCorrect(step, latestAnswers)
            update {
                child("answers").put(step.id, JSONArray(latestAnswers))
                child("status").put(step.id, if (correct) 2 else 1)
                if (!exam) {
                    child("lastSubmitted").put(step.id, JSONArray(PracticeAttempts.signature(step, latestAnswers)))
                    if (!correct) {
                        val count = child("attempts").optInt(step.id) + 1
                        child("attempts").put(step.id, count)
                        child("hints").put(step.id, PracticeAttempts.hintLevel(count))
                    }
                }
                if (!correct) put("wrong", JSONArray((stringList("wrong") + step.id).distinct()))
            }
            if (!correct && !exam) progress.markMistake(question.id)
            if (exam) advance()
        } else advance()
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                WorkbookPanel(accent = if (exam) Purple else Blue, color = if (exam) PurpleSoft.copy(alpha = .5f) else BlueSoft.copy(alpha = .45f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (exam) PurpleSoft else BlueSoft, shape = RoundedCornerShape(17.dp)) {
                            Icon(if (exam) Icons.Default.Assignment else Icons.Default.Quiz, null, tint = if (exam) Purple else Blue, modifier = Modifier.padding(11.dp).size(24.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            OloomPill(position, if (exam) Purple else Blue, if (exam) PurpleSoft else BlueSoft)
                            Text("سؤال ${workbookFa(question.sourceNumber)} • ${question.title}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                            Text(question.source, style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(onClick = { coroutine.launch { list.animateScrollToItem(3) } }, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("رفتن به پاسخ تعاملی")
                    }
                }
            }
            item {
                WorkbookPanel(accent = Purple) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = PurpleSoft, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.MenuBook, null, tint = Purple, modifier = Modifier.padding(8.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (conceptual) "صورت اصلی سؤال مفهومی" else "صورت اصلی سؤال کتاب",
                            style = MaterialTheme.typography.titleMedium,
                            color = Purple
                        )
                    }
                    WorkbookText(question.bookPrompt.orEmpty())
                }
            }
            item {
                WorkbookQuestionVisuals(question, "تصویر سؤال ${workbookFa(question.sourceNumber)}: ${question.title}")
            }
            item {
                WorkbookPanel(accent = Blue) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("پاسخ تعاملی", style = MaterialTheme.typography.titleMedium, color = Blue)
                            Text("مرحلهٔ ${workbookFa(current + 1)} از ${workbookFa(question.steps.size)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(color = BlueSoft, shape = CircleShape) {
                            Text(workbookFa(current + 1), color = Blue, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
                        }
                    }
                    OloomProgressBar((current + 1f) / question.steps.size)
                    WorkbookText(step.prompt, bold = true)
                    key(step.id) {
                        WorkbookInput(step, answers, locked && !exam) { changed ->
                            update {
                                child("answers").put(step.id, JSONArray(changed))
                                child("status").put(step.id, 0)
                            }
                        }
                    }
                    if (!exam) {
                        if (hints >= 1) Surface(color = OrangeSoft, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Orange.copy(alpha = .18f))) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(if (hints == 1) Modifier.bringIntoViewRequester(hintView) else Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lightbulb, null, tint = Orange, modifier = Modifier.size(19.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("راهنمایی ۱", color = Orange, fontWeight = FontWeight.Bold)
                                    }
                                    WorkbookText(step.hint1)
                                }
                                if (hints >= 2) {
                                    HorizontalDivider(color = Orange.copy(alpha = .18f))
                                    Column(Modifier.bringIntoViewRequester(hintView), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("راهنمایی ۲", color = Orange, fontWeight = FontWeight.Bold)
                                        WorkbookText(step.hint2)
                                    }
                                }
                            }
                        }
                        if (status == 1) Surface(color = Danger.copy(alpha = .08f), shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, null, tint = Danger, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(7.dp))
                                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                                    Text("پاسخ هنوز کامل درست نیست. راهنمایی ${workbookFa(hints)} را بخوان و انتخاب‌ها را بازبینی کن.", color = Danger)
                                    val rows = WorkbookAnswers.incorrectRows(step, answers)
                                    if (rows.isNotEmpty()) Text("در این مرحله، جای خالی‌های ${rows.joinToString("، ") { workbookFa(it + 1) }} نیاز به بازبینی دارند.", color = Danger)
                                }
                            }
                        }
                        if (locked) Surface(modifier = Modifier.bringIntoViewRequester(answerView), color = if (status == 2) GreenSoft else OrangeSoft, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, (if (status == 2) Green else Orange).copy(alpha = .2f))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (status == 2) Icons.Default.CheckCircle else Icons.Default.Lightbulb, null, tint = if (status == 2) Green else Orange)
                                    Spacer(Modifier.width(7.dp))
                                    Text(if (status == 2) "پاسخ درست است" else "پاسخ و توضیح", color = if (status == 2) Green else Orange, fontWeight = FontWeight.Black,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                }
                                WorkbookText(step.answer, bold = true)
                                WorkbookText(step.explanation)
                            }
                        }
                    }
                    val allPartsFinished = question.steps.all { s ->
                        val result = state.optJSONObject("status")?.optInt(s.id) ?: 0
                        result == 2 || (result == 3 && PracticeAttempts.canReveal(
                            state.optJSONObject("attempts")?.optInt(s.id) ?: 0,
                            state.optJSONObject("hints")?.optInt(s.id) ?: 0
                        ))
                    }
                    if (current == question.steps.lastIndex && locked && !exam && allPartsFinished) {
                        question.answerVisual?.let { WorkbookImage(it, "تصویر پاسخ سؤال ${workbookFa(question.sourceNumber)}", question.answerVisualCaption) }
                    }
                }
            }
            item {
                TextButton(onClick = { coroutine.launch { list.animateScrollToItem(1) } }) {
                    Text(if (conceptual) "بازگشت به صورت سؤال مفهومی" else "بازگشت به صورت سؤال کتاب")
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!exam && !locked) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(modifier = Modifier.weight(1f), onClick = { coroutine.launch { list.animateScrollToItem(3) } }) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("انتخاب‌ها")
                        }
                        TextButton(modifier = Modifier.weight(1f), enabled = hints > 0, onClick = {
                            coroutine.launch {
                                list.scrollToItem(3)
                                withFrameNanos { }
                                hintView.bringIntoView()
                            }
                        }) {
                            Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("راهنما")
                        }
                        TextButton(modifier = Modifier.weight(1f), enabled = canReveal, onClick = {
                            val latest = JSONObject(json)
                            if (latest.child("status").optInt(step.id) < 2 && PracticeAttempts.canReveal(latest.child("attempts").optInt(step.id), latest.child("hints").optInt(step.id))) {
                                update { child("status").put(step.id, 3) }
                                progress.markMistake(question.id)
                            }
                        }) {
                            Icon(if (canReveal) Icons.Default.Visibility else Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("دیدن پاسخ")
                        }
                    }
                }
                val help = when {
                    locked -> "این قسمت تکمیل شد؛ برای ادامه دکمهٔ پایین را بزن."
                    !complete -> when (step.kind) {
                        StepKind.MATCH -> "${workbookFa(answers.count { it.isNotBlank() })} از ${workbookFa(step.pairs.size)} جای خالی کامل شده؛ همه را کامل کن."
                        StepKind.CLASSIFY -> "${workbookFa(answers.count { it.isNotBlank() })} از ${workbookFa(step.classifyItems.size)} عبارت کامل شده؛ همه را کامل کن."
                        StepKind.MULTI -> "${workbookFa(answers.size)} از ${workbookFa(step.pick)} گزینه انتخاب شده؛ انتخاب‌ها را کامل کن."
                        else -> "برای بررسی، پاسخ این قسمت را انتخاب کن."
                    }
                    !exam && hints >= 2 -> "هر دو راهنما را داری؛ دوباره تلاش کن یا «دیدن پاسخ» را بزن."
                    !exam && !canSubmit -> "راهنمایی ۱ را بخوان؛ برای تلاش بعدی، دست‌کم یک انتخاب را تغییر بده."
                    else -> "انتخاب‌ها کامل شد؛ پاسخ را بررسی کن."
                }
                Text(help, color = Muted, style = MaterialTheme.typography.bodySmall)
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    enabled = locked || canSubmit,
                    shape = RoundedCornerShape(17.dp),
                    onClick = submit
                ) {
                    Text(when {
                        exam -> if (current == question.steps.lastIndex) "ثبت پاسخ این سؤال" else "ثبت و مرحلهٔ بعد"
                        locked -> if (current == question.steps.lastIndex) "ثبت تمرین و ادامه" else "مرحلهٔ بعد"
                        else -> "بررسی پاسخ"
                    })
                    Spacer(Modifier.width(7.dp))
                    Icon(if (locked || exam) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.CheckCircle, null, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkbookQuestionVisuals(question: ChapterQuestion, description: String) {
    val visuals = buildList {
        question.visual?.let { add(QuestionVisual(it, question.visualCaption)) }
        addAll(question.visuals)
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
private fun WorkbookPanel(
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

private fun JSONObject.child(key: String): JSONObject = optJSONObject(key) ?: JSONObject().also { put(key, it) }
private fun JSONObject.stringList(key: String): List<String> = optJSONArray(key)?.let { array -> List(array.length()) { array.getString(it) } } ?: emptyList()

private fun upgradedPracticeDraft(raw: String?): String {
    val data = runCatching { JSONObject(raw ?: "{}") }.getOrElse { JSONObject() }
    if (data.optInt("attemptPolicyVersion") != PracticeAttempts.VERSION) {
        // Old drafts could reveal answers or request hints before any attempt.
        // Preserve selections and successful steps; reset that old reveal path.
        data.optJSONObject("status")?.let { results ->
            results.keys().asSequence().toList().forEach { id ->
                if (results.optInt(id) == 3) results.put(id, 0)
            }
        }
        data.remove("attempts")
        data.remove("hints")
        data.remove("lastSubmitted")
        data.put("attemptPolicyVersion", PracticeAttempts.VERSION)
    }
    return data.toString()
}

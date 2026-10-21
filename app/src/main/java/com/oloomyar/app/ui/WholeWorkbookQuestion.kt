package com.oloomyar.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.oloomyar.app.data.QuestionDraftCodec
import com.oloomyar.app.data.WorkbookProgress
import com.oloomyar.app.data.LearningPreferences
import com.oloomyar.app.data.PracticeLocation
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun WholeWorkbookQuestion(
    question: ChapterQuestion,
    progress: WorkbookProgress,
    conceptual: Boolean,
    exam: Boolean,
    sessionToken: Int,
    position: String,
    modifier: Modifier,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onQuestionList: (() -> Unit)? = null,
    onFinish: (Int, Boolean) -> Unit
) {
    var json by rememberSaveable(question.id, sessionToken) {
        mutableStateOf(if (exam) QuestionDraftCodec.encode(QuestionPractice()) else
            progress.draft(question.id) ?: QuestionDraftCodec.encode(QuestionPractice()))
    }
    val state = remember(json) { QuestionDraftCodec.decode(json, question) }
    val initialScroll = remember { runCatching { JSONObject(json) }.getOrElse { JSONObject() } }
    val list = rememberLazyListState(initialScroll.optInt("scrollIndex"), initialScroll.optInt("scrollOffset"))
    val coroutine = rememberCoroutineScope()
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var pendingReferenceScroll by remember { mutableStateOf(false) }
    var bookmarked by remember(question.id) { mutableStateOf(progress.bookmarked(question.id)) }
    val settings = LocalDisplaySettings.current
    val jump: (Int) -> Unit = { index -> coroutine.launch { if (settings.reduceMotion) list.scrollToItem(index) else list.animateScrollToItem(index) } }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val update: (QuestionPractice) -> Unit = { changed ->
        json = JSONObject(QuestionDraftCodec.encode(changed)).apply {
            put("scrollIndex", list.firstVisibleItemIndex); put("scrollOffset", list.firstVisibleItemScrollOffset)
        }.toString()
        if (!exam) progress.saveDraft(question.id, json)
    }
    val latestJson by rememberUpdatedState(json)
    DisposableEffect(lifecycle, question.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !exam) progress.checkpoint(question.id,
                JSONObject(latestJson).apply { put("scrollIndex", list.firstVisibleItemIndex); put("scrollOffset", list.firstVisibleItemScrollOffset) }.toString())
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(question.id) {
        onDispose {
            if (!exam && progress.draft(question.id) != null) progress.saveDraft(question.id,
                JSONObject(latestJson).apply {
                    put("scrollIndex", list.firstVisibleItemIndex); put("scrollOffset", list.firstVisibleItemScrollOffset)
                }.toString())
        }
    }
    LaunchedEffect(question.id) {
        if (!exam) {
            update(state)
            if (question.relatedChapter in 1..15) LearningPreferences(context).remember(PracticeLocation(question.relatedChapter, question.id))
        }
    }
    LaunchedEffect(question.id, exam) {
        if (!exam) snapshotFlow { list.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) progress.saveDraft(question.id, JSONObject(latestJson).apply {
                put("scrollIndex", list.firstVisibleItemIndex); put("scrollOffset", list.firstVisibleItemScrollOffset)
            }.toString())
        }
    }
    val inlineStep = question.steps.singleOrNull()?.takeIf { it.clozeTemplate != null }
    val native = question.nativeQuestion.isNotEmpty()
    val hintedSteps = question.steps.filter { step ->
        !exam && state.hintLevel(question, step) > 0 &&
            state.checked[step.id]?.let { !WorkbookAnswers.isCorrect(step, it) } == true
    }
    val referenceIndex = if (native) 3 + (if (hintedSteps.isNotEmpty()) 1 else 0) else question.steps.size + 5
    val allCorrect = state.correct(question)
    val finished = !exam && (allCorrect || state.revealed)
    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("question-body"), state = list,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "heading") {
                WorkbookPanel(accent = Blue, color = BlueSoft) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (native) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(position, color = Blue, style = MaterialTheme.typography.bodySmall)
                            Text("سؤال ${workbookFa(question.sourceNumber)} • ${question.title}", style = MaterialTheme.typography.titleMedium)
                            Text("فصل ${workbookFa(question.relatedChapter)} • صفحهٔ ${workbookFa(question.sourcePage)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        } else OloomPill(position, Blue, MaterialTheme.colorScheme.surface)
                        if (!native) Spacer(Modifier.weight(1f))
                        IconToggleButton(checked = bookmarked, onCheckedChange = { bookmarked = it; progress.bookmark(question.id, it) }, modifier = Modifier.testTag("bookmark-question")) {
                            Icon(if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                if (bookmarked) "حذف نشان سؤال" else "نشان‌گذاری سؤال", tint = if (bookmarked) Purple else Muted)
                        }
                    }
                    if (!native) {
                        Text("سؤال ${workbookFa(question.sourceNumber)} • ${question.title}", style = MaterialTheme.typography.titleLarge)
                        Text(question.source, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    question.sourceReviewNote?.let { Text(it, color = Orange, style = MaterialTheme.typography.bodySmall) }
                    if (finished) TextButton(onClick = { update(QuestionPractice()) }, modifier = Modifier.testTag("restart-practice")) { Text("تمرین دوباره") }
                    if (inlineStep == null && !native) TextButton(onClick = { jump(3) }) {
                        Text("رفتن به پاسخ تعاملی")
                    }
                }
            }
            item(key = "source") {
                WorkbookPanel(accent = Purple) {
                    Text(if (native) "صورت سؤال" else "صورت اصلی سؤال کتاب", color = Purple, style = MaterialTheme.typography.titleMedium)
                    if (native) {
                        NativeWorkbookContent(question, question.nativeQuestion, state, exam) { step, values ->
                            update(state.change(step, values))
                        }
                    } else if (inlineStep != null) {
                        WorkbookInput(inlineStep, state.values(inlineStep), state.revealed && !exam,
                            practice = if (exam) state.copy(checked = emptyMap(), revealed = false) else state) {
                            update(state.change(inlineStep, it))
                        }
                    } else if (question.readablePrompt != null) {
                        WorkbookText(question.readablePrompt, Modifier.fillMaxWidth().testTag("readable-book-prompt"))
                    }
                    if (!native && (showOriginal || (inlineStep == null && question.readablePrompt == null))) {
                        (if (showOriginal) question.sourceQuestionVisual else question.promptVisual ?: question.sourceQuestionVisual)?.let { WorkbookImage(it, "صورت سؤال ${workbookFa(question.sourceNumber)} از کتاب") }
                            ?: WorkbookText(question.bookPrompt.orEmpty())
                    }
                    if (!native) TextButton(onClick = { showOriginal = !showOriginal }) {
                        Text(if (showOriginal) "بستن تصویر کتاب" else "مشاهدهٔ تصویر خود کتاب")
                    }
                }
            }
            if (!native) item(key = "figures") {
                if (!native) WorkbookQuestionVisuals(question, "تصویر سؤال ${workbookFa(question.sourceNumber)}: ${question.title}")
            }
            if (native && hintedSteps.isNotEmpty()) item(key = "native-hints") {
                WorkbookPanel(accent = Orange, modifier = Modifier.testTag("native-hints")) {
                    for (level in 1..2) {
                        val steps = hintedSteps.filter { state.hintLevel(question, it) >= level }
                        if (steps.isNotEmpty()) {
                            Surface(color = OrangeSoft, shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    WorkbookArtLabel("hint_lightbulb", "راهنمایی ${workbookFa(level)}", color = Orange,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.testTag("native-hint-$level"))
                                    steps.forEachIndexed { index, step ->
                                        if (index > 0) HorizontalDivider(color = Orange.copy(alpha = .15f))
                                        if (question.steps.size > 1) WorkbookText(step.prompt,
                                            bold = true, style = MaterialTheme.typography.bodySmall)
                                        WorkbookText(if (level == 1) step.hint1 else step.hint2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!native) itemsIndexed(question.steps, key = { _, step -> step.id }) { index, step ->
                WorkbookPanel(accent = Blue) {
                    if (inlineStep == null && !native) {
                        Text(if (question.steps.size == 1) "انتخاب پاسخ" else "قسمت ${workbookFa(index + 1)}", color = Blue, style = MaterialTheme.typography.titleMedium)
                        WorkbookText(step.prompt, bold = true)
                        val locked = !exam && (state.revealed || (step.kind != StepKind.MATCH && step.kind != StepKind.CLASSIFY && state.locked(step)))
                        WorkbookInput(step, state.values(step), locked,
                            practice = if (exam) state.copy(checked = emptyMap(), revealed = false) else state) {
                            update(state.change(step, it))
                        }
                    }
                    val hintLevel = state.hintLevel(question, step)
                    if (!exam && hintLevel > 0 && state.checked[step.id]?.let { !WorkbookAnswers.isCorrect(step, it) } == true) {
                        Surface(color = OrangeSoft, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                WorkbookArtLabel("hint_lightbulb", "راهنمایی ۱", color = Orange)
                                WorkbookText(step.hint1)
                                if (hintLevel >= 2) {
                                    HorizontalDivider()
                                    WorkbookArtLabel("hint_lightbulb", "راهنمایی ۲", color = Orange)
                                    WorkbookText(step.hint2)
                                }
                            }
                        }
                    }
                }
            }
            if (!native || (!exam && state.checked.isNotEmpty() && !state.revealed)) item(key = "feedback") {
                if (!exam && state.checked.isNotEmpty() && !state.revealed) {
                    val correctParts = question.steps.sumOf { step ->
                        when (step.kind) {
                            StepKind.MATCH -> step.pairs.indices.count { state.mark(step, it) == ResponseMark.CORRECT }
                            StepKind.CLASSIFY -> step.classifyItems.indices.count { state.mark(step, it) == ResponseMark.CORRECT }
                            else -> if (state.mark(step, 0) == ResponseMark.CORRECT) 1 else 0
                        }
                    }
                    Surface(color = if (allCorrect) GreenSoft else SelectionSoft, shape = RoundedCornerShape(16.dp)) {
                        WorkbookArtLabel(if (allCorrect) "correct" else "incorrect", if (allCorrect) { if (question.sourceReviewNote != null && question.practiceUsesPrintedKey) "پاسخ با کلید چاپی مطابقت دارد" else if (!question.practiceUsesPrintedKey) "پاسخ تمرین درست است" else "پاسخ درست است" }
                            else if (question.sourceReviewNote != null && question.practiceUsesPrintedKey) "انتخاب با کلید چاپی مطابقت ندارد؛ یادداشت بررسی مؤلف را بخوان."
                            else "${workbookFa(correctParts)} پاسخ درست؛ پاسخ‌های نادرست را اصلاح کن و دوباره بررسی کن.",
                            modifier = Modifier.fillMaxWidth().padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            color = if (allCorrect) Green else SelectionInk)
                    }
                }
            }
            item(key = "practice-status") {
                if (!exam) {
                    Text(when {
                        state.revealed -> "پاسخ کتاب نمایش داده شده؛ برای ادامه دکمهٔ پایین را بزن."
                        allCorrect -> "همهٔ پاسخ‌ها درست‌اند؛ می‌توانی پاسخ اصلی کتاب را ببینی یا ادامه بدهی."
                        !state.complete(question) -> "همهٔ قسمت‌ها را کامل کن؛ انتخاب‌ها پیش از بررسی زرد هستند."
                        !state.canCheck(question) -> "این پاسخ قبلاً بررسی شده؛ یکی از موارد اشتباه را تغییر بده."
                        state.canReveal(question) -> "دوباره تلاش کن یا «دیدن پاسخ» را بزن."
                        state.shownHints >= 2 -> "برای دیدن پاسخ، هر قسمت نادرست را دوبار با انتخاب‌های متفاوت امتحان کن."
                        else -> "انتخاب‌ها کامل است؛ پاسخ را بررسی کن."
                    }, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            item(key = "reference") {
                // Both reveal routes share this complete key, review note and owner art.
                if (!exam && state.revealed && state.canReveal(question)) {
                    WorkbookPanel(accent = Green, modifier = Modifier.testTag("workbook-reference-answer")) {
                        WorkbookArtLabel("science_book", "پاسخ کتاب‌کار", color = Green, style = MaterialTheme.typography.titleLarge)
                        WorkbookReferenceContent(question, onImageReady = {
                                if (pendingReferenceScroll) {
                                    pendingReferenceScroll = false
                                jump(referenceIndex)
                                }
                            })
                    }
                }
            }
            item(key = "footer") { Spacer(Modifier.height(8.dp)) }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!exam && onQuestionList != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("previous-question")) { Text("قبلی") }
                    TextButton(onClick = onQuestionList, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("question-navigator")) { Text("فهرست") }
                    TextButton(onClick = { onNext?.invoke() }, enabled = onNext != null, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("next-question")) { Text("بعدی") }
                }
                if (!exam) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { jump(if (native || inlineStep != null) 1 else 3) }, modifier = Modifier.weight(1f)) { Text("انتخاب‌ها") }
                        TextButton(enabled = if (native) hintedSteps.isNotEmpty() else state.shownHints > 0, onClick = {
                            val first = question.steps.indexOfFirst { s -> state.checked[s.id]?.let { !WorkbookAnswers.isCorrect(s, it) } == true }.coerceAtLeast(0)
                            jump(if (native) 2 else first + 3)
                        }, modifier = Modifier.weight(1f)) { Text("راهنما") }
                        TextButton(enabled = state.canReveal(question) && !state.revealed, modifier = Modifier.weight(1f).testTag("reveal-answer"), onClick = {
                            val latest = QuestionDraftCodec.decode(json, question)
                            if (latest.canReveal(question)) {
                                pendingReferenceScroll = true
                                update(latest.reveal(question)); progress.recordReveal(question.id, latest.correct(question))
                                // Only an explicit request to view the key moves the viewport.
                                // Selection and checking keep the current sentence in place.
                                // Compose the target first; final alignment waits for actual PDF/image layout.
                                coroutine.launch { withFrameNanos { }; list.scrollToItem(referenceIndex) }
                            }
                        }) {
                            Icon(if (state.canReveal(question)) Icons.Default.Visibility else Icons.Default.Lock, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("دیدن پاسخ")
                        }
                    }
                }
                Button(modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Navy),
                    enabled = if (exam) state.complete(question) else finished || state.canCheck(question), onClick = {
                        val latest = QuestionDraftCodec.decode(json, question)
                        if (exam || finished) {
                            val correct = question.steps.count { WorkbookAnswers.isCorrect(it, latest.values(it)) }
                            if (exam) progress.recordExam(question, latest)
                            onFinish(correct, !latest.revealed && latest.incorrectSubmissions.isEmpty() && correct == question.steps.size)
                        } else {
                            val checked = latest.submit(question)
                            update(checked)
                            progress.recordCheck(question, latest, checked)
                        }
                    }) { Text(if (exam) "ثبت پاسخ این سؤال" else if (finished) "سؤال بعدی" else "بررسی پاسخ") }
            }
        }
    }
}

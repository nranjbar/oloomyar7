package com.oloomyar.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oloomyar.app.BuildConfig
import com.oloomyar.app.data.*
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.theme.*

typealias OpenGrade7 = (Int, String?, String?, Boolean) -> Unit
private data class HubTab(val id: String, val label: String, val icon: ImageVector)
private val hubTabs = listOf(HubTab("home", "خانه", Icons.Default.Home), HubTab("chapters", "فصل‌ها", Icons.AutoMirrored.Filled.MenuBook),
    HubTab("progress", "پیشرفت", Icons.Default.Insights), HubTab("more", "بیشتر", Icons.Default.MoreHoriz))

@Composable
internal fun Grade7DiscoveryApp(catalog: Grade7Catalog, resumeRevision: Int, onOpen: OpenGrade7) {
    var tab by rememberSaveable { mutableStateOf("home") }
    var page by rememberSaveable { mutableStateOf("") }
    var reviewFilter by rememberSaveable { mutableStateOf("mistakes") }
    var localRevision by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val preferences = remember { LearningPreferences(context) }
    val summary = remember(resumeRevision, localRevision) { catalog.summary() }
    val last = remember(resumeRevision, localRevision) { catalog.validLocation(preferences.lastLocation()) }
    val review: (String) -> Unit = { reviewFilter = it; page = "review"; tab = "progress" }
    val title = when (page) {
        "review" -> "مرور و نشان‌گذاری"
        "settings" -> "تنظیمات"
        "about" -> "دربارهٔ علوم‌یار"
        "guide" -> "راهنمای استفاده"
        "privacy" -> "حریم خصوصی"
        else -> when (tab) { "chapters" -> "فصل‌های کتاب‌کار"; "progress" -> "پیشرفت من"; "more" -> "بیشتر"; else -> "علوم‌یار هفتم" }
    }
    BackHandler(enabled = page.isNotBlank() || tab != "home") { if (page.isNotBlank()) page = "" else tab = "home" }
    OloomScreen(Modifier.fillMaxSize()) {
        Scaffold(containerColor = Color.Transparent, topBar = {
            Surface(color = Background.copy(alpha = .97f)) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page.isNotBlank()) IconButton(onClick = { page = "" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "برگشت")
                    } else Surface(color = BlueSoft, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Science, null, Modifier.padding(11.dp).size(25.dp), tint = Blue)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (page.isBlank() && tab == "home") Text("دنیای کشف و آزمایش", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (page.isBlank() && tab == "home") Surface(shape = CircleShape, color = Color.White) {
                        Text("هفتم", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Blue, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }, bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                hubTabs.forEach { item ->
                    NavigationBarItem(selected = tab == item.id, onClick = { tab = item.id; page = "" },
                        icon = { Icon(item.icon, null) }, label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("nav-${item.id}"),
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue,
                            indicatorColor = BlueSoft, unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                key(tab, page) {
                    LazyColumn(Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth().testTag("hub-list"),
                        state = rememberLazyListState(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (page) {
                            "review" -> reviewItems(catalog, reviewFilter, { reviewFilter = it }, onOpen)
                            "settings" -> item { SettingsPanel(preferences, summary.bookmarks, onClearBookmarks = {
                                catalog.chapters.forEach { it.progress.clearBookmarks() }; localRevision++
                            }) }
                            "about" -> item { AboutPanel() }
                            "guide" -> item { GuidePanel() }
                            "privacy" -> item { PrivacyPanel() }
                            else -> when (tab) {
                                "home" -> {
                                    item { DiscoveryWelcome(last != null) {
                                        if (last == null) onOpen(1, catalog.chapters.first().questions.first().id, null, true)
                                        else onOpen(last.chapter, last.questionId, null, true)
                                    } }
                                    if (last != null) item {
                                        val chapter = catalog.chapters.first { it.id == last.chapter }
                                        val q = chapter.questions.first { it.id == last.questionId }
                                        ActionTile("آخرین تمرین", "فصل ${workbookFa(chapter.id)} • ${q.section} • سؤال ${workbookFa(q.sourceNumber)}",
                                            Icons.Default.History, Purple, PurpleSoft, Modifier.testTag("continue-last")) { onOpen(chapter.id, q.id, null, true) }
                                    }
                                    item { MiniProgress(summary) }
                                    item { OloomSectionHeader("مسیرهای کشف", "سه فصل موجودِ کتاب کار ۱۴۰۵", Blue) }
                                    items(catalog.chapters, key = { it.id }) { chapter -> DiscoveryChapterCard(chapter) { onOpen(chapter.id, null, null, false) } }
                                    item { ReviewShortcuts(summary, review) }
                                    item { BazaarCard() }
                                }
                                "chapters" -> {
                                    item { OloomSectionHeader("یک فصل، یک کشف تازه", "تمرین‌های اصلی و چهارگزینه‌ای، هرکدام در بخش خودشان.", Blue) }
                                    items(catalog.chapters, key = { it.id }) { chapter ->
                                        DiscoveryChapterCard(chapter, showSections = true, onSection = { onOpen(chapter.id, null, it, false) }) { onOpen(chapter.id, null, null, false) }
                                    }
                                    item { Text("در این نسخه، فصل‌های ۱ تا ۳ در دسترس‌اند.", color = Muted, style = MaterialTheme.typography.bodySmall) }
                                }
                                "progress" -> {
                                    item { ProgressPanel(summary) }
                                    items(catalog.chapters, key = { it.id }) { chapter ->
                                        val s = chapter.summary()
                                        OloomCard(accent = chapterColor(chapter.id)) {
                                            Text("فصل ${workbookFa(chapter.id)} • ${chapter.title}", style = MaterialTheme.typography.titleMedium)
                                            OloomProgressBar(s.fraction, chapterColor(chapter.id))
                                            Text("${workbookFa(s.completed)} از ${workbookFa(s.total)} سؤال کامل شده", color = Muted)
                                            Text("${workbookFa(s.independent)} حل مستقل • ${workbookFa(s.guided)} با راهنما • ${workbookFa(s.answerViewed)} پاسخ دیده‌شده", style = MaterialTheme.typography.bodySmall, color = Muted)
                                        }
                                    }
                                    item { ReviewShortcuts(summary, review) }
                                    item { BadgePanel(summary) }
                                }
                                "more" -> {
                                    item { ActionTile("دربارهٔ علوم‌یار", "کتاب کار، مؤلف و نسخهٔ برنامه", Icons.Default.Info, Blue, BlueSoft) { page = "about" } }
                                    item { ActionTile("راهنمای استفاده", "انتخاب، بررسی و یادگیری مرحله‌ای", Icons.Default.Explore, RoyalBlue, BlueSoft) { page = "guide" } }
                                    item { ActionTile("تنظیمات", "اندازهٔ متن و کاهش حرکت", Icons.Default.Tune, Purple, PurpleSoft) { page = "settings" } }
                                    item { ActionTile("حریم خصوصی", "ذخیرهٔ محلی و کارکرد آفلاین", Icons.Default.PrivacyTip, Blue, BlueSoft) { page = "privacy" } }
                                    item { BazaarCard() }
                                    item { Text("علوم‌یار هفتم • نسخهٔ ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(6.dp)) }
                    }
                }
            }
        }
    }
}

private fun chapterColor(id: Int) = when (id) { 2 -> RoyalBlue; 3 -> Purple; else -> Blue }

@Composable
private fun DiscoveryWelcome(returning: Boolean, onStart: () -> Unit) {
    Surface(shape = RoundedCornerShape(30.dp), border = BorderStroke(1.dp, Blue.copy(alpha = .12f)), color = Color.White) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFE2F4F5), Color(0xFFF4F0FC)))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("سلام، کاوشگر!", color = Blue, style = MaterialTheme.typography.labelLarge)
                    Text("هر سؤال،\nیک کشف تازه", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                    Text("با آرامش فکر کن، امتحان کن و یاد بگیر.", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 7.dp))
                }
                DiscoveryRobot(Modifier.size(112.dp))
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("home-start"), shape = RoundedCornerShape(17.dp)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text(if (returning) "ادامهٔ تمرین" else "شروع یادگیری")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.OfflineBolt, null, Modifier.size(17.dp), tint = Blue)
                Spacer(Modifier.width(5.dp)); Text("بدون اینترنت و بدون ثبت‌نام", color = BlueDark, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MiniProgress(summary: LearningSummary) {
    OloomCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("مسیر یادگیری تو", style = MaterialTheme.typography.titleMedium)
            Text("${workbookFa(summary.completed)} از ${workbookFa(summary.total)}", color = Blue, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("home-progress"))
        }
        OloomProgressBar(summary.fraction, Blue)
        Text(if (summary.completed == 0) "با اولین سؤال، مسیر کشفت را آغاز کن." else "هر تمرین کامل، یک قدم در مسیر یادگیری است.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DiscoveryChapterCard(chapter: ChapterCollection, showSections: Boolean = false, onSection: (String) -> Unit = {}, onOpen: () -> Unit) {
    val s = chapter.summary(); val color = chapterColor(chapter.id)
    OloomCard(accent = color, onClick = onOpen, modifier = Modifier.testTag("chapter-${chapter.id}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChapterArtwork(chapter.id, Modifier.size(74.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("فصل ${workbookFa(chapter.id)}", color = color, style = MaterialTheme.typography.labelMedium)
                Text(chapter.title, style = MaterialTheme.typography.titleMedium)
                Text("${workbookFa(chapter.questions.size)} سؤال • در دسترس", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        OloomProgressBar(s.fraction, color)
        Text("${workbookFa(s.completed)} سؤال کامل‌شده • ${workbookFa((s.fraction * 100).toInt())}٪", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (showSections) {
            chapter.questions.map { it.section }.distinct().forEach { section ->
                OutlinedButton(onClick = { onSection(section) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("$section (${workbookFa(chapter.questions.count { it.section == section })})")
                }
            }
        }
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, icon: ImageVector, color: Color, soft: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OloomCard(modifier = modifier, accent = color, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = soft, shape = RoundedCornerShape(15.dp)) { Icon(icon, null, Modifier.padding(12.dp).size(23.dp), tint = color) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp), tint = color)
        }
    }
}

@Composable
private fun ReviewShortcuts(summary: LearningSummary, onReview: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile("مرور اشتباهات", "${workbookFa(summary.mistakes)} سؤال برای تمرین دوباره", Icons.Default.Refresh, RoyalBlue, BlueSoft) { onReview("mistakes") }
        ActionTile("نشان‌گذاری‌ها", "${workbookFa(summary.bookmarks)} سؤال نشان‌شده", Icons.Default.Bookmarks, Purple, PurpleSoft) { onReview("bookmarks") }
    }
}

@Composable
private fun ProgressPanel(s: LearningSummary) {
    OloomCard(accent = Blue) {
        Text("یادگیری، قدم‌به‌قدم", style = MaterialTheme.typography.headlineSmall)
        OloomProgressBar(s.fraction, Blue)
        Text("${workbookFa(s.completed)} از ${workbookFa(s.total)} سؤال کامل شده", color = Blue, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("completed-count"))
        MetricLine("حل مستقل، بدون دیدن پاسخ یا راهنما", s.independent, Green)
        MetricLine("حل با راهنما، بدون دیدن پاسخ", s.guided, RoyalBlue)
        MetricLine("سؤال‌هایی که پاسخشان دیده شده", s.answerViewed, Purple)
        MetricLine("بررسی‌های کامل و معتبر", s.attempts, Blue)
        if (s.questions.any { it.kind == CompletionKind.LEGACY }) Text("تمرین‌های نسخهٔ قبلی در پیشرفت کل حفظ شده‌اند.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MetricLine(label: String, value: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(workbookFa(value), color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun BadgePanel(s: LearningSummary) {
    OloomCard(accent = Purple, color = PurpleSoft) {
        Text("کشف‌های کوچک تو", style = MaterialTheme.typography.titleMedium)
        if (s.badges.isEmpty()) Text("با حل سؤال‌ها، نشان‌های یادگیری اینجا ظاهر می‌شوند.", color = Muted)
        s.badges.forEach { badge -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WorkspacePremium, null, tint = Purple); Spacer(Modifier.width(9.dp)); Text(badge, style = MaterialTheme.typography.bodyLarge)
        } }
    }
}

private fun LazyListScope.reviewItems(catalog: Grade7Catalog, filter: String, setFilter: (String) -> Unit, onOpen: OpenGrade7) {
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == "mistakes", onClick = { setFilter("mistakes") }, label = { Text("اشتباهات") }, modifier = Modifier.testTag("review-mistakes"))
            FilterChip(selected = filter == "bookmarks", onClick = { setFilter("bookmarks") }, label = { Text("نشان‌شده‌ها") }, modifier = Modifier.testTag("review-bookmarks"))
        }
    }
    val entries = catalog.chapters.flatMap { c -> c.questions.filter { q ->
        if (filter == "bookmarks") c.progress.bookmarked(q.id) else c.progress.mistake(q.id)
    }.map { c to it } }
    if (entries.isEmpty()) item { OloomCard {
        Text(if (filter == "bookmarks") "هنوز سؤالی نشان‌گذاری نکرده‌ای." else "هنوز سؤالی برای مرور ثبت نشده است.", style = MaterialTheme.typography.titleMedium)
        Text(if (filter == "bookmarks") "با لمس نشان بالای هر سؤال، آن را برای بعد نگه دار." else "سؤال‌های دارای اشتباه و پاسخ دیده‌شده، اینجا برای تمرین دوباره در دسترس‌اند.", color = Muted)
    } }
    items(entries, key = { it.second.id }) { (c, q) ->
        ActionTile("سؤال ${workbookFa(q.sourceNumber)} • ${q.title}", "فصل ${workbookFa(c.id)} • ${q.section}",
            if (filter == "bookmarks") Icons.Default.Bookmark else Icons.Default.Refresh, chapterColor(c.id), BlueSoft,
            Modifier.testTag("review-${q.id}")) { onOpen(c.id, q.id, null, false) }
    }
}

@Composable
private fun BazaarCard() {
    val context = LocalContext.current
    OloomCard(color = Color(0xFFFFF8E6)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.StarOutline, null, tint = SelectionInk)
            Text("تجربه‌ات از علوم‌یار", style = MaterialTheme.typography.titleMedium)
        }
        Text("ثبت نظر پس از انتشار علوم‌یار هفتم در بازار امکان‌پذیر است.", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { openBazaarReview(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("bazaar-review")) { Text("ثبت نظر و امتیاز در بازار") }
    }
}

@Composable
private fun SettingsPanel(preferences: LearningPreferences, bookmarks: Int, onClearBookmarks: () -> Unit) {
    val settings = LocalDisplaySettings.current
    var confirm by remember { mutableStateOf(false) }
    OloomCard(accent = Purple) {
        Text("اندازهٔ متن", style = MaterialTheme.typography.titleLarge)
        Text("اندازه‌ای را انتخاب کن که خواندن سؤال‌ها برایت راحت‌تر است.", color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1f to "۱۰۰٪", 1.25f to "۱۲۵٪", 1.5f to "۱۵۰٪").forEach { (scale, label) ->
                FilterChip(selected = settings.textScale == scale, onClick = { preferences.textScale(scale) }, label = { Text(label) }, modifier = Modifier.testTag("font-$scale"))
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("کاهش حرکت", style = MaterialTheme.typography.titleMedium)
                Text("حرکت ربات و جابه‌جایی نرم صفحه متوقف می‌شود.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.reduceMotion, onCheckedChange = { preferences.reduceMotion(it) }, modifier = Modifier.testTag("reduce-motion"))
        }
        HorizontalDivider()
        Text("داده‌های روی دستگاه", style = MaterialTheme.typography.titleMedium)
        Text("پاسخ‌ها، پیشرفت و نشان‌گذاری‌ها روی همین دستگاه ذخیره می‌شوند و با به‌روزرسانی برنامه حفظ می‌شوند.", color = Muted)
        OutlinedButton(onClick = { confirm = true }, enabled = bookmarks > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("پاک‌کردن نشان‌گذاری‌ها (${workbookFa(bookmarks)})") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("پاک‌کردن همهٔ نشان‌گذاری‌ها؟") },
        text = { Text("نشان سؤال‌ها پاک می‌شود. پاسخ‌ها و پیشرفت تمرین حفظ می‌شوند.") },
        confirmButton = { TextButton(onClick = { onClearBookmarks(); confirm = false }) { Text("پاک‌کردن نشان‌ها") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } })
}

@Composable
private fun AboutPanel() {
    OloomCard(accent = Blue) {
        DiscoveryRobot(Modifier.size(100.dp).align(Alignment.CenterHorizontally))
        Text("علوم‌یار هفتم", style = MaterialTheme.typography.headlineSmall)
        Text("تمرین تعاملی علوم تجربی پایهٔ هفتم، همراه با انتخاب پاسخ و راهنمایی مرحله‌ای.")
        Text("منبع محتوا", color = Blue, style = MaterialTheme.typography.titleMedium)
        Text("کتاب کار علوم تجربی پایهٔ هفتم، چاپ ۱۴۰۵، تألیف علی رنجبر، همراه با پاسخنامهٔ همان کتاب.")
        Text("حقوق محتوای کتاب متعلق به مؤلف و صاحبان حق نشر است. صورت سؤال و پاسخ مرجع از خود کتاب و پاسخنامه نمایش داده می‌شود.", color = Muted)
        Text("نسخهٔ آزمایشی فصل‌های ۱ تا ۳ • ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GuidePanel() {
    OloomCard(accent = RoyalBlue) {
        listOf(
            "۱. صورت کامل سؤال را بخوان" to "تصویر کتاب و شکل‌ها با دکمهٔ بزرگ‌نمایی قابل بررسی‌اند.",
            "۲. پاسخ‌ها را انتخاب کن" to "روی جای خالی بزن. انتخابت در همان جمله زرد می‌شود و صفحه جابه‌جا نمی‌شود.",
            "۳. همهٔ قسمت‌ها را بررسی کن" to "پس از تکمیل سؤال، «بررسی پاسخ» را بزن. درست‌ها سبز و ثابت می‌شوند؛ اشتباه‌ها قرمز و قابل اصلاح‌اند.",
            "۴. از راهنما استفاده کن" to "اولین پاسخ کامل نادرست، راهنمای اول و دومین پاسخ کامل نادرستِ متفاوت، راهنمای دوم را باز می‌کند. ارسال همان پاسخ دوباره، تلاش تازه نیست.",
            "۵. دیدن پاسخ با تصمیم تو" to "قفل قسمت‌ها مستقل است. پس از دو تلاش متفاوت و راهنماها، پاسخ کتاب فقط با لمس «دیدن پاسخ» نشان داده می‌شود.",
            "۶. تمرینت را ادامه بده" to "برای ادامه خودت «سؤال بعدی» را بزن. انتخاب‌ها و محل مطالعه ذخیره می‌شود. سؤال‌های مهم را با نشان بالای صفحه نگه دار."
        ).forEach { (title, body) ->
            Text(title, style = MaterialTheme.typography.titleMedium, color = RoyalBlue)
            Text(body, color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PrivacyPanel() {
    OloomCard(accent = Blue) {
        Text("یادگیری روی دستگاه تو", style = MaterialTheme.typography.titleLarge)
        Text("برای تمرین در علوم‌یار ثبت‌نام لازم نیست. سؤال‌ها، تصاویر، پاسخنامه و راهنماها داخل برنامه‌اند و بدون اینترنت کار می‌کنند.")
        Text("انتخاب‌ها، تلاش‌ها، پیشرفت، نشان‌ها و تنظیمات فقط در حافظهٔ محلی برنامه ذخیره می‌شوند؛ این نسخه آن‌ها را به سرور برنامه ارسال نمی‌کند و ابزار تبلیغاتی یا ردیابی ندارد.")
        Text("با لمس ثبت نظر، صفحهٔ علوم‌یار هفتم در برنامهٔ بازار یا مرورگر باز می‌شود. آن صفحه خارج از علوم‌یار است و به اینترنت و سیاست حریم خصوصی بازار وابسته است.")
        Text("حذف برنامه، داده‌های محلی را پاک می‌کند. این نسخه پشتیبان‌گیری ابری خودکارِ برنامه را فعال نمی‌کند.", color = Muted)
    }
}

package com.oloomyar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oloomyar.app.ui.OloomCard
import com.oloomyar.app.ui.OloomHeroCard
import com.oloomyar.app.ui.OloomPill
import com.oloomyar.app.ui.OloomScreen
import com.oloomyar.app.ui.OloomSectionHeader
import com.oloomyar.app.ui.theme.*


private data class HomeChapter(
    val id: Int,
    val number: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val soft: Color,
    val icon: ImageVector
)

private val chapters = listOf(
    HomeChapter(1, "۱", "تجربه و تفکر", "۸ تمرین اصلی • پاسخ مرحله‌ای، راهنما و آزمون", Blue, BlueSoft, Icons.Default.Science),
    HomeChapter(2, "۲", "اندازه‌گیری در علوم و ابزارهای آن", "۱۵ تمرین اصلی و ۵ چهارگزینه‌ای • دو بخش جدا", Purple, PurpleSoft, Icons.Default.Straighten)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OloomYarTheme {
                OloomYarHomeScreen { id ->
                    val destination = when (id) {
                        1 -> Grade7Chapter1Activity::class.java
                        2 -> Grade7Chapter2Activity::class.java
                        else -> null
                    }
                    destination?.let { startActivity(Intent(this@MainActivity, it)) }
                }
            }
        }
    }
}

@Composable
internal fun OloomYarHomeScreen(onOpen: (Int) -> Unit) {
    OloomScreen(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = BlueSoft, shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Default.Science, null, tint = Blue, modifier = Modifier.padding(10.dp).size(23.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("علوم‌یار هفتم", style = MaterialTheme.typography.titleLarge)
                        Text("همراه هوشمند علوم هفتم", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    OloomPill("نسخهٔ ۱۴۰۵", Green, GreenSoft, icon = Icons.Default.Verified)
                }
            }
            item {
                OloomHeroCard(
                    eyebrow = "پایهٔ هفتم • فصل‌های ۱ و ۲",
                    title = "علوم را کشف کن، نه حفظ",
                    description = "صورت دقیق کتاب‌کار، پاسخ تعاملی، راهنمای مرحله‌ای و آزمون؛ همه در یک مسیر روشن و کاملاً آفلاین.",
                    icon = Icons.Default.RocketLaunch,
                    startColor = Navy,
                    endColor = Blue
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroMetric("۲", "فصل", Modifier.weight(1f))
                        HeroMetric("۲۸", "سؤال کتاب", Modifier.weight(1f))
                        HeroMetric("۱۰۰٪", "آفلاین", Modifier.weight(1f))
                    }
                }
            }
            item {
                OloomSectionHeader(
                    title = "فصل‌های کتاب‌کار",
                    subtitle = "یک فصل را انتخاب کن و تمرین را از همان‌جا ادامه بده.",
                    color = Blue,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }
            items(chapters, key = { it.id }) { chapter ->
                ChapterCard(
                    chapter = chapter,
                    unlocked = true,
                    free = true,
                    onClick = { onOpen(chapter.id) },
                )
            }
            item {
                Surface(color = AquaSoft, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Aqua.copy(alpha = .16f))) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.OfflineBolt, null, tint = Aqua)
                        Spacer(Modifier.width(9.dp))
                        Text("پیشرفت، تصاویر و تمرین‌ها روی دستگاه می‌مانند و بدون اینترنت در دسترس‌اند.", color = Aqua, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            item {
                AboutContentCard()
                Spacer(Modifier.height(10.dp))
            }
        }

    }
}

@Composable
private fun HeroMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = .7f), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChapterCard(
    chapter: HomeChapter,
    conceptual: Boolean = false,
    unlocked: Boolean,
    free: Boolean = false,
    onClick: () -> Unit,
) {
    OloomCard(accent = chapter.color, onClick = onClick, color = if (conceptual) chapter.soft.copy(alpha = .7f) else Color.White) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = chapter.soft, shape = RoundedCornerShape(18.dp)) {
                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    Icon(chapter.icon, null, tint = chapter.color, modifier = Modifier.size(25.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OloomPill(
                        when {
                            free -> "رایگان"
                            unlocked -> "فعال"
                            conceptual -> "مفهومی ${chapter.number}"
                            else -> "فصل ${chapter.number}"
                        },
                        if (unlocked) Green else chapter.color,
                        if (unlocked) GreenSoft else chapter.soft,
                        icon = when {
                            free -> Icons.Default.CardGiftcard
                            unlocked -> Icons.Default.Verified
                            else -> Icons.Default.Lock
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(chapter.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
                }
                Text(chapter.subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            Spacer(Modifier.width(8.dp))
            Surface(color = if (unlocked) GreenSoft else chapter.soft, shape = CircleShape) {
                Icon(
                    if (unlocked) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Lock,
                    if (unlocked) "ورود" else "خرید و فعال‌سازی",
                    tint = if (unlocked) Green else chapter.color,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutContentCard() {
    OloomCard(accent = Blue, color = BlueSoft.copy(alpha = .42f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, null, tint = Blue, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("دربارهٔ محتوا", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                Text(
                    "محتوای آموزشی و سؤالات این برنامه بر اساس کتاب کار علوم تجربی تألیف علی رنجبر تهیه و برای محیط تعاملی علوم‌یار بازطراحی شده است. استفاده با اجازه مؤلف.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                )
            }
        }
    }
}


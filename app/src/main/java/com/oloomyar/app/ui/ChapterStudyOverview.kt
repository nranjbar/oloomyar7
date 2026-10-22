package com.oloomyar.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oloomyar.app.ui.theme.*

/** The supplied chapter illustration is the cover, with real local progress. */
@Composable
internal fun ChapterStudyCover(chapter:Int,total:Int,done:Int) {
    Surface(Modifier.fillMaxWidth().testTag("chapter-study-cover"),
        shape=RoundedCornerShape(28.dp),color=Color.White,
        border=BorderStroke(1.dp,Border),shadowElevation=2.dp) {
        Column {
            ChapterArtwork(chapter,Modifier.fillMaxWidth().aspectRatio(1f))
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("پیشرفت این فصل",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                    Text("${workbookFa(done)} از ${workbookFa(total)} سؤال",color=Blue,
                        style=MaterialTheme.typography.labelLarge,modifier=Modifier.testTag("chapter-study-progress"))
                }
                OloomProgressBar(if(total==0)0f else done.toFloat()/total,Blue)

            }
        }
    }
}

@Composable
internal fun ChapterStudySection(section:String,total:Int,done:Int,onOpen:()->Unit) {
    val quiz=section=="چهارگزینه‌ای"
    val color=if(quiz)Purple else Blue
    val soft=if(quiz)PurpleSoft else BlueSoft
    Surface(Modifier.fillMaxWidth().testTag("chapter-section-$section"),shape=RoundedCornerShape(24.dp),
        color=Color.White,border=BorderStroke(1.dp,color.copy(alpha=.16f))) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(color=soft,shape=RoundedCornerShape(16.dp)) {
                    Icon(if(quiz)Icons.Default.FactCheck else Icons.Default.MenuBook,null,tint=color,
                        modifier=Modifier.padding(12.dp).size(24.dp))
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(section,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("${workbookFa(total)} سؤال • ${workbookFa(done)} تمرین‌شده",color=Muted,style=MaterialTheme.typography.bodySmall)
                }
            }
            OloomProgressBar(if(total==0)0f else done.toFloat()/total,color)
            OutlinedButton(onClick=onOpen,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(16.dp),
                colors=ButtonDefaults.outlinedButtonColors(containerColor=soft,contentColor=color),border=BorderStroke(1.dp,color.copy(alpha=.15f))) {
                Text("ورود به این بخش")
                Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Filled.ArrowBack,null,Modifier.size(18.dp))
            }
        }
    }
}

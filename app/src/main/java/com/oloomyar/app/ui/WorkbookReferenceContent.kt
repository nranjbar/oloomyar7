package com.oloomyar.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.oloomyar.app.model.ChapterQuestion
import com.oloomyar.app.ui.theme.*

/** One complete reference body for both explicitly authorized reveal routes. */
@Composable
internal fun WorkbookReferenceContent(question: ChapterQuestion, onImageReady: () -> Unit = {}) {
    if (question.nativeAnswer.isNotEmpty()) NativeWorkbookContent(question, question.nativeAnswer)
    else question.answerVisual?.let { WorkbookImage(it, "تصویر پاسخ سؤال ${workbookFa(question.sourceNumber)}",
        trimAnswerWhitespace = true, onImageReady = onImageReady) }
    question.answerReviewNote?.let { note ->
        HorizontalDivider(color = Border)
        Text("دقت علمی و توضیح تکمیلی", color = Green, style = MaterialTheme.typography.titleMedium)
        Text(workbookBidi(note), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("answer-science-review"))
    }
    question.answerStudyVisual?.let { asset ->
        HorizontalDivider(color = Border)
        Text("تصویر آموزشیِ پاسخ", color = Green, style = MaterialTheme.typography.titleMedium)
        WorkbookImage(asset, "تصویر آموزشی پاسخ سؤال ${workbookFa(question.sourceNumber)}",
            caption = question.answerStudyCaption, maxImageHeight = 420.dp)
    }
}

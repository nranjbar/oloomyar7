package com.oloomyar.app.data

import android.content.Context

class Grade7Chapter1Repository(context: Context) : AssetQuestionRepository(
    context = context,
    assetFile = "chapter1.json",
    collectionLabel = "Grade 7 chapter 1 workbook 1405",
    expectedQuestionCount = 8,
    expectedStepCount = 11,
    allowedSections = setOf("تمرین‌های اصلی")
)

class Grade7Chapter2Repository(context: Context) : AssetQuestionRepository(
    context = context,
    assetFile = "chapter2.json",
    collectionLabel = "Grade 7 chapter 2 workbook 1405",
    expectedQuestionCount = 20,
    expectedStepCount = 30,
    allowedSections = setOf("تمرین‌های اصلی", "چهارگزینه‌ای")
)

class Grade7Chapter3Repository(context: Context) : AssetQuestionRepository(
    context = context,
    assetFile = "chapter3.json",
    collectionLabel = "Grade 7 chapter 3 workbook 1405",
    expectedQuestionCount = 31,
    expectedStepCount = 35,
    allowedSections = setOf("تمرین‌های اصلی", "چهارگزینه‌ای")
)

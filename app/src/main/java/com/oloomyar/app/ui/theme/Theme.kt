package com.oloomyar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.content.SharedPreferences
import com.oloomyar.app.data.DisplaySettings
import com.oloomyar.app.data.LearningPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import com.oloomyar.app.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Blue = Color(0xFF087A76)
val RoyalBlue = Color(0xFF315DC7)
val BlueDark = Color(0xFF087A76)
val BlueSoft = Color(0xFFDDF6F3)
val Green = Color(0xFF12643C)
val GreenSoft = Color(0xFFE8F8EE)
val Orange = Color(0xFF8A5108)
val OrangeSoft = Color(0xFFFFF0DF)
val Purple = Color(0xFF7451A5)
val PurpleSoft = Color(0xFFF3EDFB)
val Aqua = Color(0xFF137E8A)
val AquaSoft = Color(0xFFE5F5F2)
val Yellow = Color(0xFFF6C343)
val Navy = Color(0xFF11215E)
val Ink = Color(0xFF11215E)
val Muted = Color(0xFF52617A)
val Background = Color(0xFFF6FBFD)
val SurfaceMuted = Color(0xFFF7FAFC)
val Border = Color(0xFFDDE7EF)
val Danger = Color(0xFFA92436)

val SelectionSoft = Color(0xFFFFF3C9)
val SelectionInk = Color(0xFF765000)
val DangerSoft = Color(0xFFFDECEF)

private val ArabicFont = FontFamily(Font(R.font.noto_arabic_regular), Font(R.font.noto_arabic_bold, FontWeight.Bold))

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Black,fontSize=32.sp,lineHeight=44.sp),
    headlineLarge = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Black,fontSize=29.sp,lineHeight=42.sp),
    headlineMedium = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Black,fontSize=23.sp,lineHeight=35.sp),
    headlineSmall = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.ExtraBold,fontSize=21.sp,lineHeight=32.sp),
    titleLarge = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.ExtraBold,fontSize=20.sp,lineHeight=31.sp),
    titleMedium = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Bold,fontSize=17.sp,lineHeight=27.sp),
    titleSmall = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Bold,fontSize=15.sp,lineHeight=24.sp),
    bodyLarge = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Normal,fontSize=17.sp,lineHeight=30.sp),
    bodyMedium = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Normal,fontSize=16.sp,lineHeight=28.sp),
    bodySmall = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Normal,fontSize=14.sp,lineHeight=24.sp),
    labelLarge = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Bold,fontSize=15.sp,lineHeight=24.sp),
    labelMedium = TextStyle(fontFamily=ArabicFont,fontWeight=FontWeight.Bold,fontSize=12.sp,lineHeight=19.sp)
)

private val Scheme = lightColorScheme(
    primary=Blue,onPrimary=Color.White,primaryContainer=BlueSoft,onPrimaryContainer=Ink,
    secondary=Purple,onSecondary=Color.White,secondaryContainer=PurpleSoft,onSecondaryContainer=Ink,
    tertiary=Aqua,onTertiary=Color.White,tertiaryContainer=AquaSoft,onTertiaryContainer=Ink,
    background=Background,onBackground=Ink,surface=Color.White,onSurface=Ink,
    surfaceVariant=SurfaceMuted,onSurfaceVariant=Muted,outline=Border,error=Danger
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
)

@Composable
fun OloomYarTheme(launchSplash: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { LearningPreferences(context) }
    var settings by remember(store) { mutableStateOf(store.display()) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settings = store.display() }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val system = LocalDensity.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDisplaySettings provides settings,
        LocalDensity provides Density(system.density, system.fontScale * settings.textScale)) {
        MaterialTheme(
            colorScheme = Scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = { if (launchSplash) com.oloomyar.app.ui.Grade7LaunchSplash(content) else content() }
        )
    }
}

val LocalDisplaySettings = staticCompositionLocalOf { DisplaySettings() }


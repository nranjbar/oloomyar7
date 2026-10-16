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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Blue = Color(0xFF087C89)
val RoyalBlue = Color(0xFF315DC7)
val BlueDark = Color(0xFF0D4D59)
val BlueSoft = Color(0xFFE6F5F5)
val Green = Color(0xFF157347)
val GreenSoft = Color(0xFFE5F6EB)
val Orange = Color(0xFFA64D13)
val OrangeSoft = Color(0xFFFFF0DF)
val Purple = Color(0xFF7961C8)
val PurpleSoft = Color(0xFFF1EDFC)
val Aqua = Color(0xFF137E8A)
val AquaSoft = Color(0xFFE5F5F2)
val Yellow = Color(0xFFFFDA76)
val Navy = Color(0xFF113C4A)
val Ink = Color(0xFF19334C)
val Muted = Color(0xFF526773)
val Background = Color(0xFFF2F8FC)
val SurfaceMuted = Color(0xFFF8FAF8)
val Border = Color(0xFFD9E6E1)
val Danger = Color(0xFFB52E4C)

val SelectionSoft = Color(0xFFFFF2BE)
val SelectionInk = Color(0xFF765000)
val DangerSoft = Color(0xFFFDE8EC)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Black,fontSize=32.sp,lineHeight=44.sp),
    headlineLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Black,fontSize=29.sp,lineHeight=42.sp),
    headlineMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Black,fontSize=23.sp,lineHeight=35.sp),
    headlineSmall = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold,fontSize=21.sp,lineHeight=32.sp),
    titleLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold,fontSize=20.sp,lineHeight=31.sp),
    titleMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=17.sp,lineHeight=27.sp),
    titleSmall = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=15.sp,lineHeight=24.sp),
    bodyLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=16.sp,lineHeight=28.sp),
    bodyMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=14.sp,lineHeight=24.sp),
    bodySmall = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=12.sp,lineHeight=20.sp),
    labelLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=15.sp,lineHeight=24.sp),
    labelMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=12.sp,lineHeight=19.sp)
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
fun OloomYarTheme(content: @Composable () -> Unit) {
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
            content = content
        )
    }
}

val LocalDisplaySettings = staticCompositionLocalOf { DisplaySettings() }


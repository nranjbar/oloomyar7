package com.oloomyar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Blue = Color(0xFF3D5CE7)
val BlueDark = Color(0xFF243DAE)
val BlueSoft = Color(0xFFEEF1FF)
val Green = Color(0xFF168866)
val GreenSoft = Color(0xFFE8F8F2)
val Orange = Color(0xFFEF7C38)
val OrangeSoft = Color(0xFFFFF0E7)
val Purple = Color(0xFF7B54D3)
val PurpleSoft = Color(0xFFF3EDFF)
val Aqua = Color(0xFF159EAD)
val AquaSoft = Color(0xFFE7F8FA)
val Yellow = Color(0xFFF2B84B)
val Navy = Color(0xFF14213D)
val Ink = Color(0xFF17213A)
val Muted = Color(0xFF68738B)
val Background = Color(0xFFF4F7FF)
val SurfaceMuted = Color(0xFFF9FAFF)
val Border = Color(0xFFDDE4F2)
val Danger = Color(0xFFC84455)

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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = Scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}


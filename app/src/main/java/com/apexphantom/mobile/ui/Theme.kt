package com.apexphantom.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Ap {
    val Ground = Color(0xFF0B0B0D)
    val Surface = Color(0xFF151518)
    val SurfaceLow = Color(0xFF0E0E11)
    val Border = Color(0xFF26262B)
    val BorderStrong = Color(0xFF3A3A41)
    val Red = Color(0xFFE5243B)
    val RedSoft = Color(0xFFFF6B7C)
    val RedDeep = Color(0xFF16080B)
    val RedBorder = Color(0xFF5A1420)
    val RedTint = Color(0xFF2A0D13)
    val Text = Color(0xFFF2F2F3)
    val TextSoft = Color(0xFFC9C9CF)
    val Muted = Color(0xFF9A9AA3)
    val Orange = Color(0xFFF5B57A)
    val OrangeBg = Color(0xFF3A2410)
    val KesinBg = Color(0xFF3A0C14)
    val KesinText = Color(0xFFFF8A97)
    val Blue = Color(0xFFA9BDFF)
    val BlueBg = Color(0xFF16223F)
    val Gray = Color(0xFFA8A8B0)
    val GrayBg = Color(0xFF232327)
    val Green = Color(0xFF3DDC97)
    val WarnBg = Color(0xFF1F1A0C)
    val WarnBorder = Color(0xFF4A3A12)
    val WarnText = Color(0xFFF2D9A0)
}

private val scheme = darkColorScheme(
    primary = Ap.Red,
    onPrimary = Color.White,
    secondary = Ap.RedSoft,
    background = Ap.Ground,
    onBackground = Ap.Text,
    surface = Ap.Surface,
    onSurface = Ap.Text,
    surfaceVariant = Ap.SurfaceLow,
    onSurfaceVariant = Ap.TextSoft,
    outline = Ap.Border
)

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

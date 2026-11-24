package com.hebiace.campus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 课程块马卡龙色（参考 youwu 课表配色）
val CourseColors = listOf(
    Color(0xFF8BC34A), // 绿
    Color(0xFF42A5F5), // 蓝
    Color(0xFFFFB300), // 黄
    Color(0xFFAB47BC), // 紫
    Color(0xFFFF7043), // 橙
    Color(0xFF26A69A), // 青
    Color(0xFFEF5350), // 红
    Color(0xFF5C6BC0), // 靛
    Color(0xFF66BB6A), // 浅绿
    Color(0xFFEC407A), // 粉
    Color(0xFF8D6E63), // 棕
    Color(0xFF78909C), // 蓝灰
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF185FA5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F1FB),
    secondary = Color(0xFF534AB7),
    secondaryContainer = Color(0xFFEEEDFE),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F2),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF85B7EB),
    onPrimary = Color(0xFF042C53),
    primaryContainer = Color(0xFF0C447C),
    secondary = Color(0xFFAFA9EC),
    secondaryContainer = Color(0xFF3C3489),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    onBackground = Color(0xFFE6E6E6),
    onSurface = Color(0xFFE6E6E6),
)

@Composable
fun HebiaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

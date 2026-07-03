package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.DARK_BLACK_BLUE,
    content: @Composable () -> Unit
) {
    val colorScheme = when(appTheme) {
        AppTheme.LIGHT_GREEN_WHITE -> lightColorScheme(
            primary = Color(0xFF2E7D32),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF1F8E9),
            onPrimary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFDCEDC8)
        )
        AppTheme.LIGHT_PURPLE_WHITE -> lightColorScheme(
            primary = Color(0xFF6A1B9A),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF3E5F5),
            onPrimary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFE1BEE7)
        )
        AppTheme.LIGHT_BLACK_WHITE -> lightColorScheme(
            primary = Color(0xFF000000),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF5F5F5),
            onPrimary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFE0E0E0)
        )
        AppTheme.LIGHT_PINK_WHITE -> lightColorScheme(
            primary = Color(0xFFC2185B),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFCE4EC),
            onPrimary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF8BBD0)
        )
        AppTheme.DARK_BLACK_PURPLE -> darkColorScheme(
            primary = Color(0xFFAB47BC), // purple
            background = Color(0xFF000000), // black
            surface = Color(0xFF1B001D),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF4A148C)
        )
        AppTheme.DARK_BLACK_BLUE -> darkColorScheme(
            primary = Color(0xFF42A5F5), // blue
            background = Color(0xFF000000), // black
            surface = Color(0xFF000B1A),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF0D47A1)
        )
        AppTheme.DARK_BLACK_RED -> darkColorScheme(
            primary = Color(0xFFEF5350), // red
            background = Color(0xFF000000), // black
            surface = Color(0xFF1D0000),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color(0xFFB71C1C)
        )
        AppTheme.DARK_BLACK_WHITE -> darkColorScheme(
            primary = Color(0xFFFFFFFF), // white
            background = Color(0xFF000000), // black
            surface = Color(0xFF111111),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF333333)
        )
        AppTheme.DARK_BLACK_GREEN -> darkColorScheme(
            primary = Color(0xFF66BB6A), // green
            background = Color(0xFF000000), // black
            surface = Color(0xFF001D00),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF1B5E20)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

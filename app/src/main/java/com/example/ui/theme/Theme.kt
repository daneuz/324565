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
        AppTheme.DARK_NEON_CYAN -> darkColorScheme(
            primary = Color(0xFF00E5FF),
            background = Color(0xFF0A0F1A),
            surface = Color(0xFF101B2B),
            onPrimary = Color.Black,
            onBackground = Color(0xFFE0FFFF),
            onSurface = Color(0xFFE0FFFF),
            surfaceVariant = Color(0xFF182B42)
        )
        AppTheme.DARK_NEON_MAGENTA -> darkColorScheme(
            primary = Color(0xFFFF00FF),
            background = Color(0xFF0F0014),
            surface = Color(0xFF2B0033),
            onPrimary = Color.White,
            onBackground = Color(0xFFFFE0FF),
            onSurface = Color(0xFFFFE0FF),
            surfaceVariant = Color(0xFF4C0059)
        )
        AppTheme.DARK_NEON_YELLOW -> darkColorScheme(
            primary = Color(0xFFFFFF00),
            background = Color(0xFF141400),
            surface = Color(0xFF2B2B00),
            onPrimary = Color.Black,
            onBackground = Color(0xFFFFFFE0),
            onSurface = Color(0xFFFFFFE0),
            surfaceVariant = Color(0xFF595900)
        )
        AppTheme.DARK_NEON_CRIMSON -> darkColorScheme(
            primary = Color(0xFFFF1744),
            background = Color(0xFF1A0A0E),
            surface = Color(0xFF2B1017),
            onPrimary = Color.Black,
            onBackground = Color(0xFFFFE0E5),
            onSurface = Color(0xFFFFE0E5),
            surfaceVariant = Color(0xFF591A25)
        )
        AppTheme.LIGHT_BLUE_WHITE -> lightColorScheme(
            primary = Color(0xFF1565C0),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFE3F2FD),
            onPrimary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFBBDEFB)
        )
        AppTheme.LIGHT_ORANGE_WHITE -> lightColorScheme(
            primary = Color(0xFFEF6C00),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFF3E0),
            onPrimary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFFFE0B2)
        )
        AppTheme.DARK_GREY_TEAL -> darkColorScheme(
            primary = Color(0xFF00BFA5),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF323232)
        )
        AppTheme.DARK_SPACE_PURPLE -> darkColorScheme(
            primary = Color(0xFF9D4EDD),
            background = Color(0xFF0D0B14),
            surface = Color(0xFF1E182D),
            onPrimary = Color.White,
            onBackground = Color(0xFFE0D4F5),
            onSurface = Color(0xFFE0D4F5),
            surfaceVariant = Color(0xFF3B2E5C)
        )
        AppTheme.DARK_FOREST_GREEN -> darkColorScheme(
            primary = Color(0xFF4CAF50),
            background = Color(0xFF0F1C11),
            surface = Color(0xFF18301B),
            onPrimary = Color.White,
            onBackground = Color(0xFFD0F0D4),
            onSurface = Color(0xFFD0F0D4),
            surfaceVariant = Color(0xFF2C5933)
        )
        AppTheme.DARK_CYBERPUNK -> darkColorScheme(
            primary = Color(0xFFFCE205),
            background = Color(0xFF050512),
            surface = Color(0xFF120C21),
            onPrimary = Color.Black,
            onBackground = Color(0xFF00FFFF),
            onSurface = Color(0xFFFF0055),
            surfaceVariant = Color(0xFF29104A)
        )
        AppTheme.LIGHT_ROSE_GOLD -> lightColorScheme(
            primary = Color(0xFFB76E79),
            background = Color(0xFFFFF0F5),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF4A3B40),
            onSurface = Color(0xFF4A3B40),
            surfaceVariant = Color(0xFFFFD1DC)
        )
        AppTheme.LIGHT_OCEAN_BLUE -> lightColorScheme(
            primary = Color(0xFF0077BE),
            background = Color(0xFFE0F7FA),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF00334E),
            onSurface = Color(0xFF00334E),
            surfaceVariant = Color(0xFFB2EBF2)
        )
        AppTheme.DARK_DRACULA -> darkColorScheme(
            primary = Color(0xFFFF79C6),
            background = Color(0xFF282A36),
            surface = Color(0xFF44475A),
            onPrimary = Color.Black,
            onBackground = Color(0xFFF8F8F2),
            onSurface = Color(0xFFF8F8F2),
            surfaceVariant = Color(0xFF6272A4)
        )
        AppTheme.DARK_STEALTH -> darkColorScheme(
            primary = Color(0xFF9E9E9E),
            background = Color(0xFF0D0D0D),
            surface = Color(0xFF1A1A1A),
            onPrimary = Color.Black,
            onBackground = Color(0xFFB3B3B3),
            onSurface = Color(0xFFB3B3B3),
            surfaceVariant = Color(0xFF333333)
        )
        AppTheme.LIGHT_PASTEL -> lightColorScheme(
            primary = Color(0xFFB39EB5),
            background = Color(0xFFFDF6E3),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF5C4D5C),
            onSurface = Color(0xFF5C4D5C),
            surfaceVariant = Color(0xFFE6D7E6)
        )
        AppTheme.LIGHT_CHERRY -> lightColorScheme(
            primary = Color(0xFFFF9AA2),
            background = Color(0xFFFFF2F2),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF593335),
            onSurface = Color(0xFF593335),
            surfaceVariant = Color(0xFFFFD9Dc)
        )
        AppTheme.DARK_SUNSET -> darkColorScheme(
            primary = Color(0xFFFF5E3A),
            background = Color(0xFF1A0914),
            surface = Color(0xFF331221),
            onPrimary = Color.White,
            onBackground = Color(0xFFFFD180),
            onSurface = Color(0xFFFFD180),
            surfaceVariant = Color(0xFF661E34)
        )
        AppTheme.DARK_BLOOD_MOON -> darkColorScheme(
            primary = Color(0xFFD50000),
            background = Color(0xFF0A0000),
            surface = Color(0xFF1E0000),
            onPrimary = Color.White,
            onBackground = Color(0xFFFF8A80),
            onSurface = Color(0xFFFF8A80),
            surfaceVariant = Color(0xFF520000)
        )
        AppTheme.LIGHT_MINT -> lightColorScheme(
            primary = Color(0xFF00BFA5),
            background = Color(0xFFF0FFF4),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF003E35),
            onSurface = Color(0xFF003E35),
            surfaceVariant = Color(0xFFB2DFDB)
        )
        AppTheme.LIGHT_LAVENDER -> lightColorScheme(
            primary = Color(0xFF7E57C2),
            background = Color(0xFFF6F0FA),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF311C54),
            onSurface = Color(0xFF311C54),
            surfaceVariant = Color(0xFFD1C4E9)
        )
        AppTheme.DARK_HACKER -> darkColorScheme(
            primary = Color(0xFF00FF41),
            background = Color(0xFF000000),
            surface = Color(0xFF001100),
            onPrimary = Color.Black,
            onBackground = Color(0xFF00FF41),
            onSurface = Color(0xFF00FF41),
            surfaceVariant = Color(0xFF003300)
        )
        AppTheme.DARK_GALAXY -> darkColorScheme(
            primary = Color(0xFF7B1FA2),
            background = Color(0xFF000022),
            surface = Color(0xFF110033),
            onPrimary = Color.White,
            onBackground = Color(0xFFE1BEE7),
            onSurface = Color(0xFFE1BEE7),
            surfaceVariant = Color(0xFF4A148C)
        )
        AppTheme.DARK_ABYSS -> darkColorScheme(
            primary = Color(0xFF009688),
            background = Color(0xFF001418),
            surface = Color(0xFF00222B),
            onPrimary = Color.White,
            onBackground = Color(0xFF80CBC4),
            onSurface = Color(0xFF80CBC4),
            surfaceVariant = Color(0xFF004D40)
        )
        AppTheme.LIGHT_CREAM -> lightColorScheme(
            primary = Color(0xFF8D6E63),
            background = Color(0xFFFFFDD0),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF3E2723),
            onSurface = Color(0xFF3E2723),
            surfaceVariant = Color(0xFFD7CCC8)
        )
        AppTheme.LIGHT_SUNFLOWER -> lightColorScheme(
            primary = Color(0xFFFBC02D),
            background = Color(0xFFFFFDE7),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.Black,
            onBackground = Color(0xFFF57F17),
            onSurface = Color(0xFFF57F17),
            surfaceVariant = Color(0xFFFFF59D)
        )
        AppTheme.DARK_SYNTHWAVE -> darkColorScheme(
            primary = Color(0xFF00E5FF),
            background = Color(0xFF1C0D26),
            surface = Color(0xFF281938),
            onPrimary = Color.Black,
            onBackground = Color(0xFFFF007F),
            onSurface = Color(0xFFFF007F),
            surfaceVariant = Color(0xFF422160)
        )
        AppTheme.DARK_GOLDEN_NIGHT -> darkColorScheme(
            primary = Color(0xFFFFD700),
            background = Color(0xFF111111),
            surface = Color(0xFF222222),
            onPrimary = Color.Black,
            onBackground = Color(0xFFFFE066),
            onSurface = Color(0xFFFFE066),
            surfaceVariant = Color(0xFF554400)
        )
        AppTheme.DARK_AURORA -> darkColorScheme(
            primary = Color(0xFF00E676),
            background = Color(0xFF0B192C),
            surface = Color(0xFF112B3C),
            onPrimary = Color.Black,
            onBackground = Color(0xFFB2FF59),
            onSurface = Color(0xFFB2FF59),
            surfaceVariant = Color(0xFF205375)
        )
        AppTheme.LIGHT_FROST -> lightColorScheme(
            primary = Color(0xFF4DD0E1),
            background = Color(0xFFF5FDFF),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF006064),
            onSurface = Color(0xFF006064),
            surfaceVariant = Color(0xFFB2EBF2)
        )
        AppTheme.LIGHT_EARTH -> lightColorScheme(
            primary = Color(0xFF5D4037),
            background = Color(0xFFEFEBE9),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF3E2723),
            onSurface = Color(0xFF3E2723),
            surfaceVariant = Color(0xFFD7CCC8)
        )
        AppTheme.DARK_NINJA -> darkColorScheme(
            primary = Color(0xFFFFFFFF),
            background = Color(0xFF000000),
            surface = Color(0xFF080808),
            onPrimary = Color.Black,
            onBackground = Color(0xFFCCCCCC),
            onSurface = Color(0xFFCCCCCC),
            surfaceVariant = Color(0xFF1A1A1A)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

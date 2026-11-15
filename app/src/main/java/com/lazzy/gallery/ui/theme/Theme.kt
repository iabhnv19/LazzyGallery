package com.lazzy.gallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

data class AccentPalette(val name: String, val lightPrimary: Color, val darkPrimary: Color)

val AccentPalettes = listOf(
    AccentPalette("Purple", Color(0xFF6200EE), Color(0xFFBB86FC)),
    AccentPalette("Blue", Color(0xFF3B82F6), Color(0xFF60A5FA)),
    AccentPalette("Green", Color(0xFF10B981), Color(0xFF34D399)),
    AccentPalette("Orange", Color(0xFFF97316), Color(0xFFFF8A65)),
    AccentPalette("Crimson", Color(0xFFEF4444), Color(0xFFF87171)),
    AccentPalette("Teal", Color(0xFF06B6D4), Color(0xFF22D3EE))
)

fun getCustomColorScheme(darkTheme: Boolean, index: Int) = run {
    val palette = AccentPalettes.getOrElse(index) { AccentPalettes[0] }
    if (darkTheme) {
        darkColorScheme(
            primary = palette.darkPrimary,
            secondary = SecondaryDark,
            tertiary = TertiaryDark,
            background = BackgroundDark,
            surface = SurfaceDark,
            onPrimary = BackgroundDark,
            onSecondary = palette.darkPrimary,
            onTertiary = BackgroundDark,
            onBackground = PrimaryDark,
            onSurface = PrimaryDark
        )
    } else {
        lightColorScheme(
            primary = palette.lightPrimary,
            secondary = SecondaryLight,
            tertiary = TertiaryLight,
            background = BackgroundLight,
            surface = SurfaceLight,
            onPrimary = SurfaceLight,
            onSecondary = palette.lightPrimary,
            onTertiary = SurfaceLight,
            onBackground = PrimaryLight,
            onSurface = PrimaryLight
        )
    }
}

@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColorIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            getCustomColorScheme(darkTheme, accentColorIndex)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

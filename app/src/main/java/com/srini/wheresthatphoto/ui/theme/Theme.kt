package com.srini.wheresthatphoto.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SeedLight = lightColorScheme(
    primary = Color(0xFF1B6B93),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8E5F5),
    onPrimaryContainer = Color(0xFF002535),
    secondary = Color(0xFF386A5F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBCEBE1),
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFF6850A4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8DDFF),
    onTertiaryContainer = Color(0xFF211047),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF4F6F9),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E4E9),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF73777F),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val SeedDark = darkColorScheme(
    primary = Color(0xFF7BD0F0),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D67),
    onPrimaryContainer = Color(0xFFB8E5F5),
    secondary = Color(0xFF9ECDC2),
    onSecondary = Color(0xFF00382E),
    secondaryContainer = Color(0xFF00513F),
    onSecondaryContainer = Color(0xFFBCEBE1),
    tertiary = Color(0xFFD0BCFF),
    onTertiary = Color(0xFF3B1D71),
    tertiaryContainer = Color(0xFF52389F),
    onTertiaryContainer = Color(0xFFE8DDFF),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun WheresThatPhotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SeedDark
        else -> SeedLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground
        ) {
            content()
        }
    }
}

package com.srini.wheresthatphoto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.srini.wheresthatphoto.ui.theme.ThemeMode

/**
 * Circular 38dp theme-toggle button, styled to match the design's
 * `.theme-toggle` element (surface-variant background, moon/sun icon,
 * soft drop shadow). Stays visually anchored in the top-right of the
 * screen when positioned in a parent `Box` with `Alignment.TopEnd`.
 *
 * The icon reflects the *effective* (resolved) dark state so users
 * always see what mode they'd be switching *into*: if the current
 * theme is dark, show the sun; if light, show the moon.
 */
@androidx.compose.runtime.Composable
fun ThemeToggleButton(
    themeMode: ThemeMode,
    onToggle: (systemIsDark: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val systemIsDark = isSystemInDarkTheme()
    val effectivelyDark = when (themeMode) {
        ThemeMode.System -> systemIsDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    Box(
        modifier = modifier
            .size(38.dp)
            .shadow(elevation = 2.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                role = Role.Button,
                onClick = { onToggle(systemIsDark) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (effectivelyDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = if (effectivelyDark) "Switch to light theme" else "Switch to dark theme",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

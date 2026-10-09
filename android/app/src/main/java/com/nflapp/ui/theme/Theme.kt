package com.nflapp.ui.theme

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

private val Navy = Color(0xFF013369)
private val Red = Color(0xFFD50A0A)

private val LightColors = lightColorScheme(
    primary = Navy,
    secondary = Red,
    tertiary = Color(0xFF2E7D32),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC2FF),
    secondary = Color(0xFFFFB4AB),
    tertiary = Color(0xFF81C784),
)

val WinColor = Color(0xFF2E7D32)
val LossColor = Color(0xFFC62828)
val TieColor = Color(0xFF757575)

@Composable
fun NflTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

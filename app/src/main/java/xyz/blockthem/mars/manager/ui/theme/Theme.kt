package xyz.blockthem.mars.manager.ui.theme

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

private val MarsRust = Color(0xFFB5442A)
private val MarsSand = Color(0xFFF2C6A0)
private val MarsDust = Color(0xFF8A5A44)

private val LightColors = lightColorScheme(
    primary = MarsRust,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3D0700),
    secondary = MarsDust,
    secondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFF5E6135),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A3),
    onPrimary = Color(0xFF601400),
    primaryContainer = Color(0xFF8A2E17),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = MarsSand,
    secondaryContainer = Color(0xFF6A4434),
    tertiary = Color(0xFFC8CA94),
)

@Composable
fun MarsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

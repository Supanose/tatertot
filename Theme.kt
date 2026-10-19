package app.tatertots.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Potato palette: cream & spud-brown by day, "soil" dark by night (3am-friendly). */
object Spud {
    val Skin = Color(0xFFE2B475)
    val SkinShade = Color(0xFFC8955B)
    val Outline = Color(0xFF5A3A22)
    val Blush = Color(0xFFF2A07B)
    val Cap = Color(0xFF6C7FD8)
    val CapLight = Color(0xFFDDE3FF)
    val Leaf = Color(0xFF7BA05B)
}

private val Day = lightColorScheme(
    primary = Color(0xFF8A5A2B), onPrimary = Color(0xFFFFF8EC),
    primaryContainer = Color(0xFFF6D9A8), onPrimaryContainer = Color(0xFF3E2716),
    secondary = Spud.Leaf, onSecondary = Color.White,
    tertiary = Spud.Cap, onTertiary = Color.White,
    background = Color(0xFFFFF6E6), onBackground = Color(0xFF3E2716),
    surface = Color(0xFFFFFBF3), onSurface = Color(0xFF3E2716),
    surfaceVariant = Color(0xFFF3E3C8), onSurfaceVariant = Color(0xFF7A5E46),
    surfaceContainer = Color(0xFFFCEFD9), surfaceContainerHigh = Color(0xFFF3E3C8),
    outline = Color(0xFFC8A47A),
)

private val Night = darkColorScheme(
    primary = Color(0xFFF2C27E), onPrimary = Color(0xFF3A2414),
    primaryContainer = Color(0xFF5A3D24), onPrimaryContainer = Color(0xFFFBE3BF),
    secondary = Color(0xFFA9C98A), onSecondary = Color(0xFF1F2A14),
    tertiary = Color(0xFFAEB9F2), onTertiary = Color(0xFF1C2350),
    background = Color(0xFF1E1610), onBackground = Color(0xFFF5E6D0),
    surface = Color(0xFF2A1F17), onSurface = Color(0xFFF5E6D0),
    surfaceVariant = Color(0xFF3A2C21), onSurfaceVariant = Color(0xFFCDB89C),
    surfaceContainer = Color(0xFF2A1F17), surfaceContainerHigh = Color(0xFF3A2C21),
    outline = Color(0xFF8C735A),
)

private val Rounded = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
)

@Composable
fun TaterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Night else Day, shapes = Rounded, content = content)
}

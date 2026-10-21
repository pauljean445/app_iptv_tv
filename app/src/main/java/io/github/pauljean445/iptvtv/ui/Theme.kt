package io.github.pauljean445.iptvtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Couleurs de l'appli PC (logo « Néon ») : nuit profonde et violet lumineux. */
object C {
    val bg = Color(0xFF100D22)        // fond des pages
    val panel = Color(0xFF15122B)     // fond des panneaux
    val surface = Color(0xFF1B1735)   // blocs, cartes
    val surface2 = Color(0xFF262046)  // emplacement des images, boutons secondaires
    val line = Color(0xFF2F2959)
    val text = Color(0xFFEEEBFF)
    val muted = Color(0xFFA6A0CC)
    val accent = Color(0xFF7B66FF)
    val accentHi = Color(0xFF9D8CFF)
    val live = Color(0xFFFF5470)      // pastille « En direct », erreurs
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent,
            onPrimary = Color.White,
            secondary = C.accentHi,
            background = C.bg,
            onBackground = C.text,
            surface = C.surface,
            onSurface = C.text,
            surfaceVariant = C.surface2,
            onSurfaceVariant = C.muted,
            border = C.line,
            error = C.live,
        ),
        content = content,
    )
}

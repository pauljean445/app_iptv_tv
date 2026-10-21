package io.github.pauljean445.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.pauljean445.iptvtv.data.Item

/** Image d'une entrée (affiche, logo) ; initiales du nom s'il n'y en a pas. */
@Composable
fun ItemImage(item: Item, modifier: Modifier = Modifier, crop: Boolean = true, padding: Dp = 0.dp) {
    // Initiales tant que l'image n'est pas chargée (ou s'il n'y en a pas), puis l'image seule : les logos
    // transparents laisseraient voir les initiales derrière eux (AsyncImage : chargée à la taille affichée)
    var loaded by remember(item.logo) { mutableStateOf(false) }
    Box(modifier.background(C.surface2), contentAlignment = Alignment.Center) {
        if (!loaded) Text(item.initials, color = C.muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (item.logo.isNotEmpty()) AsyncImage(
            model = item.logo, contentDescription = null,
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(padding),
            onSuccess = { loaded = true },
        )
    }
}

/** Vignette : image (affiche si poster, sinon logo), barre de progression, titre et sous-titre. */
@Composable
fun Tile(item: Item, subtitle: String, poster: Boolean, progress: Float = -1f, onClick: () -> Unit,
         onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val w = if (poster) 150.dp else 190.dp
    val h = if (poster) 225.dp else 118.dp
    Column(Modifier.width(w)) {
        FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(w).height(h), radius = 10.dp) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) {
                ItemImage(item, Modifier.fillMaxSize(), crop = poster, padding = if (poster) 0.dp else 14.dp)
                if (progress >= 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter))
            }
        }
        Text(item.name, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        if (subtitle.isNotEmpty()) Text(subtitle, color = C.muted, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).background(Color(0x3CFFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(C.accentHi))
    }
}

package io.github.pauljean445.iptvtv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Bouton principal (violet) ou secondaire (sombre), au style de l'appli PC ; icône facultative (jamais d'émoji :
 *  certains s'affichent en couleur sur la TV). */
@Composable
fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false,
              danger: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) C.accent else C.surface2,
            contentColor = if (primary) Color.White else C.text,
            focusedContainerColor = if (danger) C.live else C.accentHi,
            focusedContentColor = Color.White,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, if (primary) Color.Transparent else C.line)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        if (icon != null && text.isNotEmpty()) Spacer(Modifier.width(8.dp))
        if (text.isNotEmpty()) Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Champ de saisie (nom d'un profil…) : le clavier de la TV (ou du téléphone via l'appli Google TV) s'ouvre avec OK. */
@Composable
fun TextInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = C.text, fontSize = 18.sp), cursorBrush = SolidColor(C.accentHi),
        modifier = modifier.onFocusChanged { focused = it.isFocused }
            .background(C.bg, RoundedCornerShape(10.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) C.accent else C.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = C.muted, fontSize = 18.sp)
            inner()
        },
    )
}

/** Resélectionne cette entrée au retour dans la liste (après une lecture, une fiche…). */
@Composable
fun Modifier.restoreFocus(model: io.github.pauljean445.iptvtv.AppModel, key: String): Modifier {
    if (model.focusKey != key) return this
    val req = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { req.requestFocus(); model.focusKey = null } catch (e: Exception) { }
    }
    return this.focusRequester(req)
}

/** Surface sélectionnable à la télécommande : bord violet et léger agrandissement quand elle a le focus. */
@Composable
fun FocusCard(onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null,
              radius: Dp = 12.dp, color: Color = C.surface, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick, onLongClick = onLongClick, modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(radius)),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color,
            contentColor = C.text, focusedContentColor = C.text),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, C.line)),
            focusedBorder = Border(BorderStroke(3.dp, C.accentHi)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) { content() }
}

/** Pastille ronde d'un profil (initiale sur sa couleur). */
@Composable
fun Avatar(name: String, color: String, size: Dp) {
    Box(
        Modifier.size(size).background(parseColor(color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold)
    }
}

fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    C.accent
}

@Composable
fun Message(title: String, text: String = "", modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (text.isNotEmpty()) Text(text, color = C.muted, fontSize = 16.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
    }
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Interrupteur (comme sur le téléphone et le PC) dans une ligne sélectionnable à la télécommande : OK l'inverse.
 *  Pastille qui glisse, piste violette quand il est activé. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val knob by androidx.compose.animation.core.animateDpAsState(if (checked) 26.dp else 0.dp, label = "pastille")
    val track by androidx.compose.animation.animateColorAsState(if (checked) C.accent else C.surface2, label = "piste")
    FocusCard(onClick = onToggle, modifier = modifier, color = C.surface, radius = 14.dp) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 56.dp, height = 30.dp).background(track, RoundedCornerShape(15.dp))
                .border(1.dp, if (checked) Color.Transparent else C.muted, RoundedCornerShape(15.dp)).padding(4.dp)) {
                Box(Modifier.padding(start = knob).size(22.dp).background(if (checked) Color.White else C.text, CircleShape))
            }
            Text(label, color = C.text, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

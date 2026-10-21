package io.github.pauljean445.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text

/** Fenêtre au style du menu ☰ de l'appli PC : panneau arrondi, titre, texte, boutons. */
@Composable
fun Card(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(560.dp).background(C.panel, RoundedCornerShape(16.dp))
                .border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            content()
        }
    }
}

/** Après l'ouverture d'une fenêtre, les appuis sont ignorés un court instant : l'appui long qui l'a ouverte
 *  (touche OK encore enfoncée, répétitions) ne doit rien valider. */
private const val GUARD_MS = 700L

@Composable
private fun rememberGuard(): () -> Boolean {
    val opened = remember { android.os.SystemClock.uptimeMillis() }
    return { android.os.SystemClock.uptimeMillis() - opened > GUARD_MS }
}

/** Confirmation : le curseur est sur « Annuler » (une erreur de manip ne supprime rien). */
@Composable
fun ConfirmDialog(title: String, text: String, ok: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    val ready = rememberGuard()
    Card(title, onDismiss) {
        Text(text, color = C.text, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End)) {
            AppButton("Annuler", { if (ready()) onDismiss() }, Modifier.focusRequester(focus))
            AppButton(ok, { if (ready()) onOk() }, primary = true, danger = true)
        }
    }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }
}

/** Liste d'actions (appui long sur une entrée) : une ligne par action. */
@Composable
fun ActionsDialog(title: String, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    val ready = rememberGuard()
    Card(title, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.forEachIndexed { i, (label, action) ->
                AppButton(label, { if (ready()) { onDismiss(); action() } },
                    (if (i == 0) Modifier.focusRequester(focus) else Modifier).fillMaxWidth())
            }
        }
    }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }
}

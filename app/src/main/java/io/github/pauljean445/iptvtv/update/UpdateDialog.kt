package io.github.pauljean445.iptvtv.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.pauljean445.iptvtv.AppModel
import io.github.pauljean445.iptvtv.BuildConfig
import io.github.pauljean445.iptvtv.ui.AppButton
import io.github.pauljean445.iptvtv.ui.C
import io.github.pauljean445.iptvtv.ui.Card
import io.github.pauljean445.iptvtv.ui.ProgressBar

/** « Mise à jour disponible » : nouveautés, téléchargement (avec progression), puis installeur d'Android. */
@Composable
fun UpdateDialog(model: AppModel) {
    val info = model.update ?: return
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val progress = model.updateProgress
    Card("Mise à jour disponible", onDismiss = { if (progress < 0f) model.update = null }) {
        Text("La version ${info.version} est disponible (tu as la ${BuildConfig.VERSION_NAME}).", color = C.text, fontSize = 16.sp)
        if (info.notes.isNotEmpty()) Text(info.notes, color = C.muted, fontSize = 14.sp,
            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
        when {
            progress >= 0f -> {
                Text(if (progress < 1f) "Téléchargement… ${(progress * 100).toInt()} %" else "Ouverture de l'installeur…",
                    color = C.text, fontSize = 15.sp)
                ProgressBar(progress)
            }
            model.updateError.isNotEmpty() -> Text(model.updateError, color = C.live, fontSize = 15.sp)
            !Updater.canInstall(context) -> Text("Une seule fois : autorise IPTV Player à installer ses mises à jour " +
                "(« Autoriser » ouvre le réglage d'Android ; active-le puis reviens avec Retour).", color = C.accentHi, fontSize = 15.sp)
            else -> Text("L'appli va se fermer le temps de l'installation. Tes profils, favoris et reprises sont conservés.",
                color = C.muted, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            if (progress >= 0f) AppButton("Annuler", { model.cancelUpdate() }, Modifier.focusRequester(focus))
            else {
                AppButton("Plus tard", { model.update = null })
                if (!Updater.canInstall(context)) AppButton("Autoriser", { Updater.openInstallPermission(context) },
                    Modifier.focusRequester(focus), primary = true)
                else AppButton("Mettre à jour", { model.startUpdate(context) }, Modifier.focusRequester(focus), primary = true)
            }
        }
    }
    LaunchedEffect(progress >= 0f) { try { focus.requestFocus() } catch (e: Exception) { } }
}

package io.github.pauljean445.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.pauljean445.iptvtv.AppModel
import io.github.pauljean445.iptvtv.data.str

/** « Qui regarde ? » : choix du profil au démarrage. */
@Composable
fun ProfilesScreen(model: AppModel) {
    val current = remember { model.settings["profile"].str() }
    val focus = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().background(C.bg),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Qui regarde ?", color = C.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            for (p in model.profiles) {
                val id = p["id"].str()
                FocusCard(
                    onClick = { model.chooseProfile(id) },
                    modifier = if (id == current) Modifier.focusRequester(focus) else Modifier,
                    color = C.bg, radius = 20.dp,
                ) {
                    Column(Modifier.padding(18.dp).width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(p["name"].str(), p["color"].str(), 120.dp)
                        Text(p["name"].str(), color = C.text, fontSize = 20.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
        // Se souvenir du choix : au prochain démarrage, le profil choisi s'ouvre directement (modifiable dans Réglages)
        SwitchRow("Ouvrir directement le profil choisi au prochain démarrage", !model.askProfile,
            { model.setAskProfile(!model.askProfile) }, Modifier.padding(top = 44.dp))
    }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }
}

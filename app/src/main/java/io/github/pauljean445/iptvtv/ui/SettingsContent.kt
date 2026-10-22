package io.github.pauljean445.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import io.github.pauljean445.iptvtv.data.Obj
import io.github.pauljean445.iptvtv.data.Store
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.pauljean445.iptvtv.AppModel
import io.github.pauljean445.iptvtv.BuildConfig
import io.github.pauljean445.iptvtv.Screen
import io.github.pauljean445.iptvtv.data.str

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = C.text, fontSize = 18.sp), cursorBrush = SolidColor(C.accentHi),
        modifier = modifier.onFocusChanged { focused = it.isFocused }
            .background(C.panel, RoundedCornerShape(10.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) C.accent else C.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text("Rechercher une chaîne, un film, une série…", color = C.muted, fontSize = 18.sp)
            inner()
        },
    )
}

/** Page « Réglages » (bouton ☰ en haut à droite, comme le menu de l'appli PC). */
@Composable
fun SettingsScreen(model: AppModel) {
    Column(Modifier.fillMaxSize().background(C.bg).padding(top = 30.dp)) {
        Text("Réglages", color = C.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 40.dp))
        SettingsContent(model)
    }
}

/** Réglages : profil, catalogue, compte du foyer, version. */
@Composable
fun SettingsContent(model: AppModel) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var resetPid by remember { mutableStateOf<String?>(null) }
    var deletePid by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Pair<String?, Obj?>?>(null) }  // (id, profil) ; id null : nouveau profil
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Section("Profils", "Un profil par personne du foyer : chacun a ses favoris, ses lectures en cours et ses " +
                "langues. Les profils sont partagés avec les autres appareils du foyer.") {
                AppButton("Nouveau profil", { editing = null to null }, icon = Icons.Filled.PersonAdd)
                if (model.profiles.size > 1) SwitchRow("Demander « Qui regarde ? » au démarrage", model.askProfile,
                    { model.setAskProfile(!model.askProfile) })
            }
        }
        items(model.profiles, key = { it["id"].str() }) { p ->
            val pid = p["id"].str()
            val current = pid == model.profileId
            Row(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(14.dp))
                .border(1.dp, if (current) C.accent else C.line, RoundedCornerShape(14.dp)).padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(p["name"].str(), p["color"].str(), 44.dp)
                Text(p["name"].str() + if (current) "  ·  en cours" else "", color = C.text, fontSize = 18.sp,
                    modifier = Modifier.weight(1f).padding(start = 6.dp))
                if (!current) AppButton("Ouvrir", { model.switchProfile(pid) }, primary = true)
                AppButton("Modifier", { editing = pid to p }, icon = Icons.Filled.Edit)
                AppButton("Réinitialiser", { resetPid = pid })
                if (model.profiles.size > 1) AppButton("Supprimer", { deletePid = pid }, danger = true, icon = Icons.Filled.Delete)
            }
        }
        item {
            Section("Catalogue", model.source?.let { if (it.type == "xtream") "Xtream Codes · ${it.server}" else "Playlist M3U" }
                ?: "Aucune source IPTV") {
                AppButton("Recharger le catalogue", { model.loadCatalog(force = true); model.toast = "Rechargement du catalogue…" })
                AppButton("Changer d'abonnement IPTV", { model.open(Screen.Pairing(iptvOnly = true)) })
            }
        }
        item {
            Section("Compte du foyer", "Connecté au compte ${model.cloud.email}. Profils, favoris, reprises et langues " +
                "sont partagés avec les autres appareils du foyer.") {
                AppButton("Se déconnecter", { confirmSignOut = true })
            }
        }
        item {
            Section("Application", "IPTV Player TV · version ${BuildConfig.VERSION_NAME}. Les nouvelles versions sont " +
                "proposées au lancement de l'appli.") {
                AppButton("Rechercher une mise à jour", { model.checkUpdate(silent = false) }, icon = Icons.Filled.SystemUpdate)
            }
        }
    }
    if (confirmSignOut) ConfirmDialog("Se déconnecter",
        "Déconnecter la TV du compte du foyer ? Il faudra la reconnecter (QR code) pour continuer à utiliser l'appli.",
        "Se déconnecter", onOk = { confirmSignOut = false; model.signOut() }, onDismiss = { confirmSignOut = false })
    fun name(pid: String) = model.profiles.firstOrNull { it["id"] == pid }?.get("name").str()
    resetPid?.let { pid ->
        ConfirmDialog("Réinitialiser « ${name(pid)} »",
            "Effacer les favoris, lectures en cours, « Vu récemment », épisodes vus et choix de langue de ce profil, sur " +
                "tous les appareils ? À faire après un changement de fournisseur IPTV. Le profil est conservé.",
            "Réinitialiser", onOk = { resetPid = null; model.resetProfile(pid) }, onDismiss = { resetPid = null })
    }
    deletePid?.let { pid ->
        ConfirmDialog("Supprimer « ${name(pid)} »",
            "Supprimer ce profil, ses favoris, lectures en cours et « Vu récemment », sur tous les appareils du foyer ? " +
                "Les autres profils ne sont pas touchés.",
            "Supprimer", onOk = { deletePid = null; model.deleteProfile(pid) }, onDismiss = { deletePid = null })
    }
    editing?.let { (pid, p) ->
        ProfileDialog(p?.get("name").str(), p?.get("color").str().ifEmpty {
            Store.PROFILE_COLORS.firstOrNull { c -> model.profiles.none { it["color"] == c } } ?: Store.PROFILE_COLORS[0]
        }, isNew = pid == null, onSave = { n, c ->
            editing = null
            if (pid == null) model.createProfile(n, c) else model.editProfile(pid, n, c)
        }, onDismiss = { editing = null })
    }
}

@Composable
private fun Section(title: String, text: String, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(14.dp))
        .border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(22.dp)) {
        Text(title, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text, color = C.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp).width(900.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Nouveau profil / modifier un profil : nom et couleur. */
@Composable
private fun ProfileDialog(name: String, color: String, isNew: Boolean, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(name) }
    var c by remember { mutableStateOf(color) }
    val focus = remember { FocusRequester() }
    Card(if (isNew) "Nouveau profil" else "Modifier le profil", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(n.ifEmpty { "?" }, c, 56.dp)
            TextInput(n, { n = it.take(24) }, "Prénom", Modifier.weight(1f).focusRequester(focus))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Couleur", color = C.muted, fontSize = 15.sp, modifier = Modifier.width(80.dp))
            Store.PROFILE_COLORS.forEach { col ->
                FocusCard(onClick = { c = col }, color = parseColor(col), radius = 18.dp,
                    modifier = Modifier.size(36.dp).border(if (col == c) 3.dp else 0.dp, androidx.compose.ui.graphics.Color.White,
                        RoundedCornerShape(18.dp))) {}
            }
        }
        if (isNew) Text("Le nouveau profil reprend l'abonnement IPTV du profil en cours.", color = C.muted, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            AppButton("Annuler", onDismiss)
            AppButton(if (isNew) "Créer" else "Enregistrer", { if (n.isNotBlank()) onSave(n.trim(), c) }, primary = true,
                enabled = n.isNotBlank())
        }
    }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }
}

package io.github.pauljean445.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.pauljean445.iptvtv.AppModel
import io.github.pauljean445.iptvtv.Screen
import io.github.pauljean445.iptvtv.data.Item
import io.github.pauljean445.iptvtv.data.asObj
import io.github.pauljean445.iptvtv.data.str
import kotlinx.coroutines.delay

val TABS = listOf("home" to "Accueil", "live" to "Direct", "movie" to "Films", "series" to "Séries",
    "replay" to "Replays", "search" to "Recherche")

/** Écran principal : onglets en haut (comme sur PC), contenu dessous. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(model: AppModel) {
    var tab by rememberSaveable { mutableStateOf("home") }
    // Onglet sous le focus : son contenu n'est construit qu'après un court arrêt dessus (traverser la rangée
    // d'onglets à la télécommande ne reconstruit pas chaque onglet au passage)
    var focusedTab by remember { mutableStateOf(tab) }
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(focusedTab) { delay(250); tab = focusedTab }
    Column(Modifier.fillMaxSize().background(C.bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TabRow(
                // En remontant vers les onglets, on retombe sur l'onglet en cours (et non sur celui au-dessus du focus)
                modifier = Modifier.focusRestorer { tabFocus },
                selectedTabIndex = TABS.indexOfFirst { it.first == focusedTab },
                indicator = { positions, focused ->
                    TabRowDefaults.PillIndicator(currentTabPosition = positions[TABS.indexOfFirst { it.first == focusedTab }],
                        doesTabRowHaveFocus = focused, activeColor = C.accent, inactiveColor = C.surface2)
                },
            ) {
                TABS.forEach { (key, label) ->
                    Tab(selected = focusedTab == key, onFocus = { focusedTab = key }, onClick = { focusedTab = key; tab = key },
                        modifier = if (key == tab) Modifier.focusRequester(tabFocus) else Modifier,
                        colors = TabDefaults.pillIndicatorTabColors(contentColor = C.muted, selectedContentColor = C.text,
                            focusedContentColor = Color.White)) {
                        Text(label, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // À droite, comme le bouton ☰ de l'appli PC : le profil, qui ouvre les réglages (profils compris)
            model.currentProfile?.let { p ->
                FocusCard(onClick = { model.open(Screen.Settings) }, color = C.bg, radius = 20.dp) {
                    Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p["name"].str(), p["color"].str(), 30.dp)
                        Text(p["name"].str(), color = C.text, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp, end = 10.dp))
                        Icon(Icons.Filled.Menu, contentDescription = "Réglages", tint = C.muted)
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            val catalog = model.catalog
            when {
                catalog == null && model.catalogError.isNotEmpty() -> Message(
                    "Catalogue indisponible", model.catalogError) {
                    AppButton("Réessayer", { model.loadCatalog(force = true) }, primary = true)
                    AppButton("Saisir l'abonnement IPTV", { model.open(Screen.Pairing(iptvOnly = true)) })
                }
                catalog == null -> Message("Chargement du catalogue…")
                tab == "home" -> HomeContent(model)
                tab == "search" -> SearchContent(model)
                else -> BrowseContent(model, tab)
            }
        }
    }
}

/** Accueil : chaînes favorites, films et séries en cours, replays favoris (une rangée chacun). */
@Composable
fun HomeContent(model: AppModel) {
    val catalog = model.catalog ?: return
    val lib = model.library
    @Suppress("UNUSED_VARIABLE") val v = model.libVersion  // redessine après une modification
    var removing by remember { mutableStateOf<Pair<String, Item>?>(null) }

    fun channels(tab: String) = lib.favoriteKeys(tab).mapNotNull { catalog.byKey[it] }
    val movies = lib.recentKeys("movie").mapNotNull { catalog.byKey[it] }
    val series = lib.recentKeys("series").mapNotNull { catalog.byKey[it] }

    LazyColumn(contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item {
            HomeRow("Direct", "Chaînes favorites", channels("live"), poster = false,
                empty = "Aucune chaîne favorite. Dans l'onglet Direct, appui long sur une chaîne pour l'ajouter.",
                subtitle = { it.group }, progress = { -1f },
                onClick = { model.open(Screen.Player(it, channels("live"), resume = false)) }, model = model,
                onLongClick = { removing = "live" to it })
        }
        item {
            HomeRow("Films", "En cours de lecture", movies, poster = true,
                empty = "Aucun film en cours. Les films commencés apparaîtront ici.",
                subtitle = { m -> lib.position(m.key).let { if (it > 0) "Reprendre à ${fmtTime(it)}" else "À reprendre" } },
                progress = { m -> lib.length(m.key).let { if (it > 0) lib.position(m.key).toFloat() / it else -1f } },
                onClick = { model.open(Screen.Details(it)) }, model = model,
                onLongClick = { removing = "movie" to it })
        }
        item {
            HomeRow("Séries", "En cours de lecture", series, poster = true,
                empty = "Aucune série en cours. Les séries commencées apparaîtront ici.",
                subtitle = { s ->
                    val e = lib.recent[s.key].asObj()
                    val last = e?.get("last_ep").str()
                    val label = e?.get("last_label").str() ?: ""
                    val pos = lib.position(last)
                    if (label.isEmpty()) "En cours" else if (pos > 0) "$label · reprendre à ${fmtTime(pos)}" else "Après $label"
                },
                progress = { s ->
                    val last = lib.recent[s.key].asObj()?.get("last_ep").str()
                    lib.length(last).let { if (it > 0) lib.position(last).toFloat() / it else -1f }
                },
                onClick = { model.open(Screen.Details(it)) }, model = model,
                onLongClick = { removing = "series" to it })
        }
        item {
            HomeRow("Replays", "Chaînes favorites", channels("replay"), poster = false,
                empty = "Aucune chaîne favorite. Dans l'onglet Replays, appui long sur une chaîne pour l'ajouter.",
                subtitle = { "replay ${it.archiveDays} j" }, progress = { -1f },
                onClick = { model.open(Screen.Programs(it)) }, model = model,
                onLongClick = { removing = "replay" to it })
        }
    }

    removing?.let { (kind, item) ->
        val fav = kind == "live" || kind == "replay"
        ConfirmDialog(
            title = item.name,
            text = if (fav) "Retirer cette chaîne des favoris ?" else "Retirer des lectures en cours ? La position de reprise sera oubliée.",
            ok = if (fav) "Retirer des favoris" else "Retirer des lectures en cours",
            onOk = {
                if (fav) { if (lib.isFavorite(kind, item.key)) model.toggleFavorite(kind, item) } else model.removeRecent(item)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun HomeRow(title: String, sub: String, items: List<Item>, poster: Boolean, empty: String,
                    subtitle: (Item) -> String, progress: (Item) -> Float, onClick: (Item) -> Unit,
                    onLongClick: (Item) -> Unit, model: AppModel) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("  $sub" + if (items.isNotEmpty()) " · ${items.size}" else "", color = C.muted, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 3.dp))
        }
        if (items.isEmpty()) {
            Text(empty, color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        } else {
            LazyRow(contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(items, key = { it.key }) { it ->
                    Tile(it, subtitle(it), poster, progress(it), onClick = { onClick(it) }, onLongClick = { onLongClick(it) },
                        modifier = Modifier.restoreFocus(model, it.key))
                }
            }
        }
    }
}

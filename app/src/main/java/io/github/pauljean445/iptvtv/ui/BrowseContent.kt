package io.github.pauljean445.iptvtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import io.github.pauljean445.iptvtv.AppModel
import io.github.pauljean445.iptvtv.Screen
import io.github.pauljean445.iptvtv.data.Item
import kotlinx.coroutines.delay

const val FAV = "\u0000fav"
const val RECENT = "\u0000recent"
const val ALL = ""

/** Onglets Direct / Films / Séries / Replays : catégories à gauche, contenu à droite. */
@Composable
fun BrowseContent(model: AppModel, tab: String) {
    val catalog = model.catalog ?: return
    val lib = model.library
    @Suppress("UNUSED_VARIABLE") val v = model.libVersion
    var group by rememberSaveable(tab) { mutableStateOf(if (lib.favoriteKeys(tab).isNotEmpty()) FAV else ALL) }
    var actionsFor by remember { mutableStateOf<Item?>(null) }
    var groupActions by remember { mutableStateOf<String?>(null) }  // catégorie : masquer / réafficher
    var othersOpen by rememberSaveable(tab) { mutableStateOf(false) }
    // Catégories masquées (par profil, synchronisées) : hors de la liste et de « Toutes », rangées dans « Autres »
    val hiddenGroups = remember(catalog, tab, model.libVersion) {
        catalog.groups[tab].orEmpty().filter { lib.isHidden(tab, it) }
    }

    val personal = group == FAV || group == RECENT
    val items = remember(catalog, tab, group, if (personal || group == ALL) model.libVersion else 0) {
        val all = catalog.byTab[tab].orEmpty()
        when (group) {
            FAV -> lib.favoriteKeys(tab).mapNotNull { catalog.byKey[it] }
            RECENT -> lib.recentKeys(tab).mapNotNull { catalog.byKey[it] }
            ALL -> hiddenGroups.toSet().let { h -> if (h.isEmpty()) all else all.filter { it.groupLabel !in h } }
            else -> all.filter { it.groupLabel == group }
        }
    }
    val groups = remember(catalog, tab, hiddenGroups) {
        buildList {
            add(FAV to "★  Favoris")
            if (tab == "movie" || tab == "series") add(RECENT to "◷  Vu récemment")
            add(ALL to "Toutes les catégories")
            catalog.groups[tab].orEmpty().filter { it !in hiddenGroups }.forEach { add(it to it) }
        }
    }
    val title = groups.firstOrNull { it.first == group }?.second ?: group

    fun open(it: Item) = when (tab) {
        "live" -> model.open(Screen.Player(it, items, resume = false))
        "replay" -> model.open(Screen.Programs(it))
        else -> model.open(Screen.Details(it))
    }

    Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 24.dp, top = 8.dp)) {
        LazyColumn(Modifier.width(300.dp).fillMaxHeight(), contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(groups, key = { it.first }) { (key, label) ->
                GroupItem(label, key == group, onClick = { group = key },
                    onLongClick = if (key != FAV && key != RECENT && key != ALL) ({ groupActions = key }) else null)
            }
            if (hiddenGroups.isNotEmpty()) {
                item(key = "\u0000others") {
                    GroupItem((if (othersOpen) "▾  " else "▸  ") + "Autres · ${hiddenGroups.size}", false,
                        onClick = { othersOpen = !othersOpen })
                }
                if (othersOpen) items(hiddenGroups, key = { "\u0000h:$it" }) { g ->
                    GroupItem("     $g", g == group, muted = true, onClick = { group = g }, onLongClick = { groupActions = g })
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 24.dp)) {
            Text("$title · ${items.size}" + if (group in hiddenGroups) "  ·  catégorie masquée (dans « Autres »)" else "",
                color = C.muted, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 10.dp))
            if (items.isEmpty()) {
                Text(
                    when (group) {
                        FAV -> "Aucun favori. Appui long sur une entrée pour l'ajouter aux favoris."
                        RECENT -> "Rien en cours pour le moment."
                        else -> "Aucune entrée dans cette catégorie."
                    }, color = C.muted, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
            } else if (tab == "live" || tab == "replay") {
                LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.key }) { it ->
                        ChannelRow(it, if (tab == "replay") "${it.groupLabel} · replay ${it.archiveDays} j" else it.groupLabel,
                            fav = lib.isFavorite(tab, it.key), onClick = { open(it) }, onLongClick = { actionsFor = it },
                            modifier = Modifier.restoreFocus(model, it.key))
                    }
                }
            } else {
                LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(bottom = 40.dp, top = 6.dp, start = 6.dp, end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(items, key = { it.key }) { it ->
                        val pos = if (tab == "movie") lib.resumePosition(it.key) else 0L
                        val len = if (tab == "movie") lib.length(it.key) else 0L
                        val sub = listOf(it.year, if (it.rating.isNotEmpty()) "★ ${it.rating}" else "")
                            .filter { s -> s.isNotEmpty() }.joinToString(" · ")
                        Tile(it, sub, poster = true, progress = if (pos > 0 && len > 0) pos.toFloat() / len else -1f,
                            onClick = { open(it) }, onLongClick = { actionsFor = it }, modifier = Modifier.restoreFocus(model, it.key))
                    }
                }
            }
        }
    }

    groupActions?.let { g ->
        val hidden = lib.isHidden(tab, g)
        ActionsDialog(g, listOf(
            (if (hidden) "Réafficher la catégorie" else "Masquer la catégorie (rangée dans « Autres »)") to {
                lib.setHidden(tab, g, !hidden)
                model.libraryChanged()
                if (!hidden && group == g) group = ALL
                model.toast = if (hidden) "« $g » réaffichée" else "« $g » rangée dans « Autres »"
            },
        ), onDismiss = { groupActions = null })
    }

    actionsFor?.let { item ->
        val fav = lib.isFavorite(tab, item.key)
        ActionsDialog(item.name, buildList {
            add((if (fav) "Retirer des favoris" else "Ajouter aux favoris") to {
                val added = model.toggleFavorite(tab, item)
                model.toast = "${item.name} : ${if (added) "ajouté aux" else "retiré des"} favoris"
            })
            if (item.key in lib.recent) add("Retirer de « Vu récemment »" to { model.removeRecent(item) })
        }, onDismiss = { actionsFor = null })
    }
}

@Composable
fun ChannelRow(item: Item, subtitle: String, fav: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
               modifier: Modifier = Modifier) {
    FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth().height(64.dp), radius = 10.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 72.dp, height = 44.dp).clip(RoundedCornerShape(6.dp))) {
                ItemImage(item, Modifier.fillMaxSize(), crop = false, padding = 4.dp)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(item.name, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = C.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (fav) Text("★", color = C.accentHi, fontSize = 18.sp)
        }
    }
}

/** Recherche dans les films et les séries (sans tenir compte des accents) : une bande de films et une bande de
 *  séries, qui défilent vers la droite comme sur l'accueil ; comme la page Recherche de l'appli PC. */
@Composable
fun SearchContent(model: AppModel) {
    val catalog = model.catalog ?: return
    var query by rememberSaveable { mutableStateOf("") }
    var searched by remember { mutableStateOf(query) }
    LaunchedEffect(query) { delay(300); searched = query }  // pas de recherche à chaque lettre tapée
    val q = Item.normalize(searched.trim())
    val (films, series) = remember(q, catalog) {
        if (q.length < 2) emptyList<Item>() to emptyList()
        else catalog.byTab["movie"].orEmpty().filter { q in it.norm }.take(300) to
            catalog.byTab["series"].orEmpty().filter { q in it.norm }.take(300)
    }
    fun sub(it: Item) = listOf(it.year, if (it.rating.isNotEmpty()) "★ ${it.rating}" else "")
        .filter { s -> s.isNotEmpty() }.joinToString(" · ").ifEmpty { it.groupLabel }
    LazyColumn(contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Column {
                SearchField(query, { query = it }, Modifier.width(640.dp))
                Text(if (q.length < 2) "Tape au moins 2 lettres pour chercher un film ou une série (le clavier du " +
                    "téléphone fonctionne aussi, via l'appli Google TV)."
                    else "${films.size} film${if (films.size > 1) "s" else ""}, ${series.size} série${if (series.size > 1) "s" else ""}",
                    color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (q.length >= 2) {
            item {
                HomeRow("Films", "Résultats", films, poster = true, empty = "Aucun film trouvé.", subtitle = ::sub,
                    progress = { -1f }, onClick = { model.open(Screen.Details(it)) }, onLongClick = { }, model = model)
            }
            item {
                HomeRow("Séries", "Résultats", series, poster = true, empty = "Aucune série trouvée.", subtitle = ::sub,
                    progress = { -1f }, onClick = { model.open(Screen.Details(it)) }, onLongClick = { }, model = model)
            }
        }
    }
}

/** Une ligne de la liste des catégories (appui long : masquer / réafficher). */
@Composable
private fun GroupItem(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
                      muted: Boolean = false) {
    ListItem(
        selected = selected, onClick = onClick, onLongClick = onLongClick,
        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = if (muted) 14.sp else 15.sp) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (muted) C.muted.copy(alpha = 0.7f) else C.muted,
            selectedContainerColor = C.surface2, selectedContentColor = C.text,
            focusedContainerColor = C.accent, focusedContentColor = androidx.compose.ui.graphics.Color.White),
        modifier = Modifier.height(44.dp),
    )
}

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import io.github.pauljean445.iptvtv.data.ImdbBase
import io.github.pauljean445.iptvtv.data.num
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.focusProperties
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

val TABS = listOf("home" to "Accueil", "fav" to "Favoris", "live" to "Direct", "movie" to "Films", "series" to "Séries",
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
                tab == "home" -> HomeContent(model, onSeeAll = { t, g ->
                    // « Tout voir » : l'onglet, sur ★ Favoris ou ◷ Vu récemment
                    model.pendingGroup = t to g
                    focusedTab = t
                    tab = t
                })
                tab == "search" -> SearchContent(model)
                tab == "fav" -> FavoritesContent(model)
                else -> BrowseContent(model, tab)
            }
        }
    }
}

/** Vignettes au plus par bande « En cours » / « Favoris » de l'accueil (« Tout voir » pour le reste), comme le PC. */
const val HOME_MAX = 12
private const val SEE_ALL = "\u0000all"

/** Série « finie pour l'instant » : son dernier épisode disponible est vu (ou tous ses épisodes connus) ; elle
 *  quitte « En cours de lecture » (toujours dans ◷ Vu récemment), de nouveaux épisodes l'y refont apparaître. */
fun seriesDone(lib: io.github.pauljean445.iptvtv.data.Library, show: Item): Boolean {
    val e = lib.recent[show.key].asObj() ?: return false
    val last = e["last_ep"].str()
    if (last.isEmpty() || last !in lib.watched) return false
    val eps = show.episodes.orEmpty()
    if (eps.isNotEmpty()) return eps.last().key in lib.watched
    val total = e["total"].num().toLong()
    return total > 0 && lib.watchedKeys(show.key).size >= total
}

/** Accueil : les lectures en cours seulement, films puis séries (12 au plus, « Tout voir » ouvre l'onglet sur
 *  ◷ Vu récemment). Les favoris ont leur onglet (FavoritesContent). */
@Composable
fun HomeContent(model: AppModel, onSeeAll: (tab: String, group: String) -> Unit) {
    val catalog = model.catalog ?: return
    val lib = model.library
    @Suppress("UNUSED_VARIABLE") val v = model.libVersion  // redessine après une modification
    var removing by remember { mutableStateOf<Pair<String, Item>?>(null) }

    // Films finis / séries entièrement vues : dans « Vu récemment » seulement
    val movies = lib.recentKeys("movie").filterNot { lib.isDone(it) }.mapNotNull { catalog.byKey[it] }
    val series = lib.recentKeys("series").filterNot { lib.isDone(it) }.mapNotNull { catalog.byKey[it] }
        .filterNot { seriesDone(lib, it) }

    fun movieSub(m: Item) = lib.position(m.key).let { if (it > 0) "Reprendre à ${fmtTime(it)}" else "Commencé" }
    fun movieProgress(m: Item) = lib.length(m.key).let { if (it > 0) lib.position(m.key).toFloat() / it else -1f }
    fun seriesSub(s: Item): String {
        val e = lib.recent[s.key].asObj()
        val last = e?.get("last_ep").str()
        val label = e?.get("last_label").str()
        val pos = lib.position(last)
        return when {
            label.isEmpty() -> "En cours"
            pos > 0 -> "$label · reprendre à ${fmtTime(pos)}"
            last in lib.watched -> "Après $label"
            else -> "$label · commencé"
        }
    }
    fun seriesProgress(s: Item): Float {
        val last = lib.recent[s.key].asObj()?.get("last_ep").str()
        return lib.length(last).let { if (it > 0) lib.position(last).toFloat() / it else -1f }
    }
    val details: (Item) -> Unit = { model.open(Screen.Details(it)) }

    LazyColumn(contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp)) {
        item(key = "recent") {
            HomeGroup("En cours de lecture", Icons.Filled.PlayCircle) {
                HomeRow("Films", "", movies.take(HOME_MAX), poster = true, rowId = "movie", icon = Icons.Filled.Movie,
                    small = true, total = movies.size, onSeeAll = { onSeeAll("movie", RECENT) },
                    empty = "Aucun film en cours. Les films commencés apparaîtront ici.",
                    subtitle = ::movieSub, progress = ::movieProgress, onClick = details, model = model,
                    onLongClick = { removing = "movie" to it })
                HomeRow("Séries", "", series.take(HOME_MAX), poster = true, rowId = "series", icon = Icons.Filled.VideoLibrary,
                    small = true, total = series.size, onSeeAll = { onSeeAll("series", RECENT) },
                    empty = "Aucune série en cours. Les séries commencées apparaîtront ici.",
                    subtitle = ::seriesSub, progress = ::seriesProgress, onClick = details, model = model,
                    onLongClick = { removing = "series" to it })
            }
        }
    }
    removing?.let { (kind, item) -> RemoveDialog(model, kind, item) { removing = null } }
}

/** Onglet Favoris (comme le téléphone) : bloc VOD (films, séries ; derniers ajoutés d'abord) puis bloc TV (direct,
 *  replays ; dans l'ordre des favoris, celui du zapping). */
@Composable
fun FavoritesContent(model: AppModel) {
    val catalog = model.catalog ?: return
    val lib = model.library
    @Suppress("UNUSED_VARIABLE") val v = model.libVersion
    var removing by remember { mutableStateOf<Pair<String, Item>?>(null) }

    fun channels(tab: String) = lib.favoriteKeys(tab).mapNotNull { catalog.byKey[it] }
    fun vod(tab: String) = lib.favoriteKeys(tab)
        .sortedByDescending { lib.fav["$tab|$it"].asObj()?.get("t").num() }.mapNotNull { catalog.byKey[it] }
    val movies = vod("movie")
    val series = vod("series")
    val live = channels("live")
    val replays = channels("replay")
    val details: (Item) -> Unit = { model.open(Screen.Details(it)) }

    LazyColumn(contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp)) {
        item(key = "vod") {
            HomeGroup("VOD", Icons.Filled.Movie) {
                HomeRow("Films", "", movies, poster = true, rowId = "movie_fav", icon = Icons.Filled.Movie, small = true,
                    empty = "Aucun film favori. Sur la fiche d'un film : bouton Ajouter aux favoris.",
                    subtitle = { ImdbBase.tileText(it) }, progress = { -1f }, onClick = details, model = model,
                    onLongClick = { removing = "movie_fav" to it })
                HomeRow("Séries", "", series, poster = true, rowId = "series_fav", icon = Icons.Filled.VideoLibrary,
                    small = true, empty = "Aucune série favorite. Sur la fiche d'une série : bouton Ajouter aux favoris.",
                    subtitle = { ImdbBase.tileText(it) }, progress = { -1f }, onClick = details, model = model,
                    onLongClick = { removing = "series_fav" to it })
            }
        }
        item(key = "tv") {
            HomeGroup("TV", Icons.Filled.LiveTv) {
                HomeRow("Direct", "", live, poster = false, rowId = "live", icon = Icons.Filled.Tv, small = true,
                    empty = "Aucune chaîne favorite. Dans l'onglet Direct, appui long sur une chaîne pour l'ajouter.",
                    subtitle = { it.group }, progress = { -1f },
                    onClick = { model.open(Screen.Player(it, live, resume = false)) }, model = model,
                    onLongClick = { removing = "live" to it })
                HomeRow("Replays", "", replays, poster = false, rowId = "replay", icon = Icons.Filled.Replay, small = true,
                    empty = "Aucune chaîne favorite. Dans l'onglet Replays, appui long sur une chaîne pour l'ajouter.",
                    subtitle = { "replay ${it.archiveDays} j" }, progress = { -1f },
                    onClick = { model.open(Screen.Programs(it)) }, model = model,
                    onLongClick = { removing = "replay" to it })
            }
        }
    }
    removing?.let { (kind, item) -> RemoveDialog(model, kind, item) { removing = null } }
}

/** Appui long sur une vignette de l'accueil / des favoris : retirer des favoris ou des lectures en cours. */
@Composable
private fun RemoveDialog(model: AppModel, kind: String, item: Item, onDone: () -> Unit) {
    val lib = model.library
    val favTab = when (kind) { "live", "replay" -> kind; "movie_fav" -> "movie"; "series_fav" -> "series"; else -> null }
    ConfirmDialog(
        title = item.name,
        text = when {
            kind == "live" || kind == "replay" -> "Retirer cette chaîne des favoris ?"
            favTab != null -> "Retirer des favoris ?"
            else -> "Retirer des lectures en cours ? La position de reprise sera oubliée."
        },
        ok = if (favTab != null) "Retirer des favoris" else "Retirer des lectures en cours",
        onOk = {
            if (favTab != null) { if (lib.isFavorite(favTab, item.key)) model.toggleFavorite(favTab, item) }
            else model.removeRecent(item)
            onDone()
        },
        onDismiss = onDone,
    )
}

/** Groupe de l'accueil : icône violette, titre et trait, puis ses bandes Films et Séries. */
@Composable
private fun HomeGroup(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = C.accentHi, modifier = Modifier.size(28.dp))
            Text(title, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, end = 14.dp))
            Box(Modifier.weight(1f).height(1.dp).background(C.line))
        }
        content()
    }
}

/**
 * Une bande de l'accueil (aussi utilisée par la recherche). Elle retient la vignette qui avait le focus : en y
 * revenant (depuis les onglets, une autre bande), on retombe dessus et non sur celle qui est sous le focus à
 * l'écran (avant, remonter sur « Accueil » puis redescendre faisait changer de chaîne).
 * small : sous-bande d'un groupe (titre en petites capitales grises) ; onSeeAll : vignette « Tout voir » au bout.
 */
@OptIn(ExperimentalComposeUiApi::class)  // FocusRequester.Cancel : bouts de bande
@Composable
internal fun HomeRow(title: String, sub: String, items: List<Item>, poster: Boolean, empty: String,
                    subtitle: (Item) -> String, progress: (Item) -> Float, onClick: (Item) -> Unit,
                    onLongClick: (Item) -> Unit, model: AppModel, rowId: String = title, icon: ImageVector? = null,
                    small: Boolean = false, total: Int = items.size, onSeeAll: (() -> Unit)? = null) {
    // Vignette retenue (au retour d'une fiche / lecture ouverte depuis cette bande : celle-là)
    var last by rememberSaveable(rowId) { mutableStateOf(if (model.homeRow == rowId) model.focusKey else null) }
    val onSeeAll = onSeeAll?.takeIf { total > items.size }  // « Tout voir » seulement s'il y a plus que ce qui est montré
    val target = last?.takeIf { k -> (k == SEE_ALL && onSeeAll != null) || items.any { it.key == k } }
        ?: items.firstOrNull()?.key
    val sf = rememberSelectedFocus()
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = maxOf(0, items.indexOfFirst { it.key == target } - 1))
    val scope = rememberCoroutineScope()
    // Vignette retenue sortie de l'écran : on y fait défiler la bande, puis focus
    sf.bringBack = {
        val i = if (target == SEE_ALL) items.size else items.indexOfFirst { it.key == target }
        if (i >= 0) scope.launch {
            state.scrollToItem(maxOf(0, i - 1))
            repeat(20) {
                withFrameNanos { }
                if (sf.attached) {
                    try { sf.requester.requestFocus() } catch (e: Exception) { }
                    return@launch
                }
            }
        }
    }
    Column {
        Row(verticalAlignment = if (small) Alignment.CenterVertically else Alignment.Bottom) {
            if (icon != null) Icon(icon, contentDescription = null, tint = if (small) C.muted else C.accentHi,
                modifier = Modifier.padding(end = if (small) 8.dp else 10.dp).size(if (small) 18.dp else 26.dp))
            if (small) {
                Text(title.uppercase(), color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                if (total > 0) Text("  ·  $total", color = C.muted, fontSize = 13.sp)
            } else {
                Text(title, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("  $sub" + if (total > 0) " · $total" else "", color = C.muted, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        if (items.isEmpty()) {
            Text(empty, color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        } else {
            LazyRow(state = state, modifier = Modifier.enterOnSelected(sf),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                itemsIndexed(items, key = { _, it -> it.key }) { i, it ->
                    var m = Modifier.selectedTarget(sf, it.key == target)
                        .onFocusChanged { f -> if (f.isFocused) last = it.key }
                    // Retour d'une fiche : seulement dans la bande d'où elle a été ouverte (un film peut être à la
                    // fois « en cours » et favori)
                    if (model.homeRow == null || model.homeRow == rowId) m = m.restoreFocus(model, it.key)
                    Tile(it, subtitle(it), poster, progress(it), seen = poster && model.library.isDone(it.key),
                        onClick = { model.homeRow = rowId; onClick(it) }, onLongClick = { onLongClick(it) },
                        modifier = m.focusProperties {
                            // Bout de la bande : la flèche ne fait rien (au lieu de sauter sur ☰ ou un onglet)
                            if (i == 0) left = FocusRequester.Cancel
                            if (i == items.lastIndex && onSeeAll == null) right = FocusRequester.Cancel
                        })
                }
                if (onSeeAll != null) item(key = SEE_ALL) {
                    SeeAllTile(total, poster, onClick = onSeeAll,
                        modifier = Modifier.selectedTarget(sf, target == SEE_ALL)
                            .onFocusChanged { f -> if (f.isFocused) last = SEE_ALL }
                            .focusProperties { right = FocusRequester.Cancel })
                }
            }
        }
    }
}

/** Vignette « Tout voir » au bout d'une bande : ouvre l'onglet sur la catégorie complète. */
@Composable
private fun SeeAllTile(total: Int, poster: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val w = if (poster) 150.dp else 190.dp
    val h = if (poster) 225.dp else 118.dp
    FocusCard(onClick = onClick, modifier = modifier.width(w).height(h), radius = 10.dp, color = C.surface2) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = C.accentHi,
                modifier = Modifier.size(34.dp))
            Text("Tout voir", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp))
            Text("$total en tout", color = C.muted, fontSize = 13.sp)
        }
    }
}

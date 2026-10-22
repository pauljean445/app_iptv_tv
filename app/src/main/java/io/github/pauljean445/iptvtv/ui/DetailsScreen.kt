package io.github.pauljean445.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.pauljean445.iptvtv.AppModel
import io.github.pauljean445.iptvtv.Screen
import io.github.pauljean445.iptvtv.data.Item
import io.github.pauljean445.iptvtv.data.asObj
import io.github.pauljean445.iptvtv.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fiche d'un film ou d'une série (saisons et épisodes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(model: AppModel, item: Item) {
    val lib = model.library
    @Suppress("UNUSED_VARIABLE") val v = model.libVersion
    var loaded by remember { mutableIntStateOf(0) }  // redessine quand le descriptif / les épisodes arrivent
    var error by remember { mutableStateOf("") }
    LaunchedEffect(item.key) {
        val c = model.client ?: return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) {
                if (item.kind == "movie" && !item.infoLoaded) c.fillMovieInfo(item)
                if (item.kind == "series" && item.episodes == null) c.loadEpisodes(item)
            }
        } catch (e: Exception) {
            error = "Détails indisponibles : ${e.message}"
        }
        loaded++
    }
    @Suppress("UNUSED_VARIABLE") val reload = loaded  // relu : la fiche se redessine à l'arrivée des détails
    val focus = remember { FocusRequester() }
    var actionsFor by remember { mutableStateOf<Item?>(null) }
    val tab = if (item.kind == "series") "series" else "movie"

    val eps = item.episodes.orEmpty()
    val seasons = remember(eps.size) { eps.map { it.season }.distinct() }
    var season by remember(item.key, eps.size) {
        mutableIntStateOf(seriesTarget(model, item)?.season ?: seasons.firstOrNull() ?: 0)
    }
    Row(Modifier.fillMaxSize().background(C.bg).padding(start = 56.dp, top = 40.dp, end = 40.dp)) {
        Box(Modifier.width(260.dp).height(390.dp).clip(RoundedCornerShape(14.dp))) { ItemImage(item, Modifier.fillMaxSize()) }
        // Colonne de droite entièrement défilable : fiche, boutons, langues, saisons puis épisodes
        LazyColumn(Modifier.weight(1f).padding(start = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 8.dp, bottom = 48.dp)) {
            item {
                Column {
                    Text(item.name, color = C.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    val meta = listOf(item.year, if (item.rating.isNotEmpty()) "★ ${item.rating}" else "", item.genre, item.duration)
                        .filter { it.isNotEmpty() }.joinToString("  ·  ")
                    if (meta.isNotEmpty()) Text(meta, color = C.muted, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(item.plot.ifEmpty { if (error.isNotEmpty()) error else "" }, color = C.text, fontSize = 16.sp, maxLines = 5,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp))
                    if (item.director.isNotEmpty() || item.cast.isNotEmpty()) Text(
                        listOf(if (item.director.isNotEmpty()) "Réalisation : ${item.director}" else "",
                            if (item.cast.isNotEmpty()) "Avec : ${item.cast}" else "").filter { it.isNotEmpty() }.joinToString("\n"),
                        color = C.muted, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp))
                }
            }
            item {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.kind == "movie") {
                        val pos = lib.resumePosition(item.key)
                        AppButton(if (pos > 0) "Reprendre à ${fmtTime(pos)}" else "Lire",
                            { model.open(Screen.Player(item, listOf(item), resume = true)) },
                            Modifier.focusRequester(focus), primary = true, icon = Icons.Filled.PlayArrow)
                        if (pos > 0) AppButton("Depuis le début", { model.open(Screen.Player(item, listOf(item), resume = false)) })
                    } else {
                        val target = seriesTarget(model, item)
                        AppButton(
                            when {
                                item.episodes == null -> "Chargement des épisodes…"
                                target == null -> "Aucun épisode"
                                lib.resumePosition(target.key) > 0 -> "Reprendre ${target.episodeCode}"
                                else -> "Lire ${target.episodeCode}"
                            },
                            { target?.let { model.open(Screen.Player(it, eps, resume = true)) } },
                            Modifier.focusRequester(focus), primary = true, enabled = target != null, icon = Icons.Filled.PlayArrow)
                    }
                    val fav = lib.isFavorite(tab, item.key)
                    AppButton(if (fav) "Retirer des favoris" else "Ajouter aux favoris", { model.toggleFavorite(tab, item) },
                        icon = if (fav) Icons.Filled.Star else Icons.Filled.StarBorder)
                }
            }
            item {
                val langTarget = if (item.kind == "movie") item else seriesTarget(model, item)
                if (langTarget != null && langTarget.url.isNotEmpty()) LanguageChoice(model, item, langTarget)
            }
            if (item.kind == "series" && eps.isNotEmpty()) {
                item {
                    LazyRow(Modifier.padding(top = 18.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                        items(seasons) { s -> AppButton("Saison $s", { season = s }, primary = s == season) }
                    }
                }
                items(eps.filter { it.season == season }, key = { it.key }) { ep ->
                    EpisodeRow(model, ep, onClick = { model.open(Screen.Player(ep, eps, resume = true)) },
                        onLongClick = { actionsFor = ep }, modifier = Modifier.restoreFocus(model, ep.key))
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }

    actionsFor?.let { ep ->
        ActionsDialog(ep.episodeCode + " · " + ep.name, listOf(
            (if (lib.isWatched(ep)) "Marquer comme non vu" else "Marquer comme vu") to {
                if (lib.isWatched(ep)) lib.setUnwatched(ep) else model.saveProgress(ep, 0, 0, finished = true)
                model.libraryChanged()
            },
            "Lire depuis le début" to { model.open(Screen.Player(ep, item.episodes.orEmpty(), resume = false)) },
        ), onDismiss = { actionsFor = null })
    }
}

@Composable
private fun EpisodeRow(model: AppModel, ep: Item, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val lib = model.library
    @Suppress("UNUSED_VARIABLE") val v = model.libVersion  // redessinée à chaque changement (vu, reprise)
    val pos = lib.resumePosition(ep.key)
    val len = lib.length(ep.key)
    FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth().height(76.dp), radius = 10.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 112.dp, height = 63.dp).clip(RoundedCornerShape(6.dp))) {
                ItemImage(ep, Modifier.fillMaxSize())
                if (pos > 0 && len > 0) ProgressBar(pos.toFloat() / len, Modifier.align(Alignment.BottomCenter))
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("${ep.episodeCode} · ${ep.name}", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ep.plot.ifEmpty { ep.duration }, color = C.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(when {
                lib.isWatched(ep) -> "✓ Vu"
                pos > 0 -> "En cours · ${fmtTime(pos)}"
                else -> ""
            }, color = C.accentHi, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Épisode à lancer : celui en cours, sinon le suivant non vu (comme « Reprendre » sur PC), sinon le premier. */
fun seriesTarget(model: AppModel, show: Item): Item? {
    val eps = show.episodes ?: return null
    if (eps.isEmpty()) return null
    val lib = model.library
    val entry = lib.recent[show.key].asObj() ?: return eps.first()
    val last = entry["last_ep"].str()
    val idx = eps.indexOfFirst { it.key == last }
    if (idx < 0) return eps.first()
    if (lib.resumePosition(last) > 0) return eps[idx]
    val watched = lib.watchedKeys(show.key)
    if (last !in watched) return eps[idx]  // lancé moins de 15 s : on le relance depuis le début
    return (eps.drop(idx + 1) + eps.take(idx)).firstOrNull { it.key !in watched } ?: eps[idx]
}

/** Replays d'une chaîne : programmes passés, du plus récent au plus ancien, filtrés par jour. */
@Composable
fun ProgramsScreen(model: AppModel, channel: Item) {
    var programs by remember { mutableStateOf<List<Item>?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(channel.key) {
        val c = model.client
        if (c == null) { error = "Replays disponibles seulement avec un abonnement Xtream."; return@LaunchedEffect }
        try { programs = withContext(Dispatchers.IO) { c.archivePrograms(channel) } } catch (e: Exception) {
            error = "Programmes indisponibles : ${e.message}"
        }
    }
    val list = programs
    Column(Modifier.fillMaxSize().background(C.bg).padding(start = 56.dp, top = 36.dp, end = 56.dp)) {
        Text(channel.name, color = C.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Replays · ${channel.archiveDays} jours", color = C.muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 16.dp))
        when {
            error.isNotEmpty() -> Text(error, color = C.live, fontSize = 16.sp)
            list == null -> Text("Chargement des programmes…", color = C.muted, fontSize = 16.sp)
            list.isEmpty() -> Text("Aucun programme disponible en replay.", color = C.muted, fontSize = 16.sp)
            else -> {
                val days = list.map { it.group }.distinct()
                var day by remember { mutableStateOf(days.first()) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                    items(days) { d -> AppButton(d, { day = d }, primary = d == day) }
                }
                LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 40.dp, top = 4.dp, start = 4.dp, end = 4.dp)) {
                    val shown = list.filter { it.group == day }
                    items(shown, key = { it.key }) { p ->
                        FocusCard(onClick = { model.open(Screen.Player(p, list, resume = false)) },
                            modifier = Modifier.fillMaxWidth().height(64.dp), radius = 10.dp) {
                            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
                                Text(p.name, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                if (p.plot.isNotEmpty()) Text(p.plot, color = C.muted, fontSize = 13.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Langue et sous-titres, choisis sur la fiche (pistes lues dans le fichier, comme sur PC). Le choix est mémorisé
 * pour le film ou pour toute la série, appliqué à chaque lecture et partagé entre appareils ; il se change aussi
 * pendant la lecture (bouton « Langue et sous-titres »).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChoice(model: AppModel, item: Item, target: Item) {
    val lib = model.library
    @Suppress("UNUSED_VARIABLE") val v = model.libVersion
    var tracks by remember(target.url) { mutableStateOf<List<io.github.pauljean445.iptvtv.player.Track>?>(null) }
    LaunchedEffect(target.url) { tracks = io.github.pauljean445.iptvtv.player.Track.probe(model.libVlc, target.url) }
    val prefKey = item.key  // film : le film ; série : toute la série
    val pref = lib.pref(prefKey)
    val list = tracks
    Column(Modifier.padding(top = 18.dp)) {
        when {
            list == null -> Text("Recherche des langues disponibles…", color = C.muted, fontSize = 14.sp)
            list.isEmpty() -> Text("Langues non disponibles pour ce fichier.", color = C.muted, fontSize = 14.sp)
            else -> {
                val audio = list.filter { it.kind == "audio" }
                val subs = list.filter { it.kind == "spu" }
                if (audio.isNotEmpty()) Row(Modifier.padding(vertical = 4.dp)) {
                    Text("Langue", color = C.muted, fontSize = 14.sp, modifier = Modifier.width(96.dp).padding(top = 12.dp))
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        audio.forEach { t ->
                            AppButton(t.label, { lib.setPref(prefKey, "audio", t.pref()); model.libraryChanged() },
                                primary = io.github.pauljean445.iptvtv.player.Track.isPref(t, pref?.get("audio")))
                        }
                    }
                }
                if (subs.isNotEmpty()) Row(Modifier.padding(vertical = 4.dp)) {
                    Text("Sous-titres", color = C.muted, fontSize = 14.sp, modifier = Modifier.width(96.dp).padding(top = 12.dp))
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppButton("Aucun", { lib.setPref(prefKey, "spu", "off"); model.libraryChanged() },
                            primary = pref?.get("spu") == "off")
                        subs.forEach { t ->
                            AppButton(t.label, { lib.setPref(prefKey, "spu", t.pref()); model.libraryChanged() },
                                primary = io.github.pauljean445.iptvtv.player.Track.isPref(t, pref?.get("spu")))
                        }
                    }
                }
                Text(if (item.kind == "series") "Choix mémorisé pour toute la série (modifiable aussi pendant la lecture)."
                    else "Choix mémorisé pour ce film (modifiable aussi pendant la lecture).",
                    color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
        }
    }
}

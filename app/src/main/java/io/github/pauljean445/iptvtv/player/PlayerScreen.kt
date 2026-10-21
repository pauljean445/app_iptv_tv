package io.github.pauljean445.iptvtv.player

import android.net.Uri
import android.view.KeyEvent as AndroidKey
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Text
import io.github.pauljean445.iptvtv.AppModel
import io.github.pauljean445.iptvtv.data.Item
import io.github.pauljean445.iptvtv.data.Library
import io.github.pauljean445.iptvtv.data.XtreamClient
import io.github.pauljean445.iptvtv.ui.ActionsDialog
import io.github.pauljean445.iptvtv.ui.AppButton
import io.github.pauljean445.iptvtv.ui.C
import io.github.pauljean445.iptvtv.ui.Card
import io.github.pauljean445.iptvtv.ui.ProgressBar
import io.github.pauljean445.iptvtv.ui.fmtTime
import kotlinx.coroutines.delay
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

private val RESUMABLE = setOf("movie", "episode")
private const val MAX_RETRIES = 5

/**
 * Lecteur (VLC, comme sur PC). Télécommande, quand les commandes sont masquées :
 *  OK : pause / lecture · ← → : -10 s / +10 s (replay : -1 / +1 min)
 *  ↑ ↓ : chaîne précédente / suivante (direct), sinon affiche les commandes · Retour : quitte.
 * Commandes affichées : barre de temps (← → déplacent le curseur, OK y va), boutons (pause, déplacements,
 * langue et sous-titres, épisode suivant ; direct en pause : « Revenir au direct »).
 * Direct : une pause fait passer en différé (la lecture reprend là où elle s'était arrêtée), comme sur PC.
 */
@Composable
fun PlayerScreen(model: AppModel, start: Item, list: List<Item>, resume: Boolean, onExit: () -> Unit) {
    val context = LocalContext.current
    val lib = model.library
    val player = remember { MediaPlayer(model.libVlc) }
    var current by remember { mutableStateOf(start) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var pos by remember { mutableLongStateOf(0L) }
    var len by remember { mutableLongStateOf(0L) }
    var offsetMin by remember { mutableIntStateOf(0) }        // replay : le flux commence à cette minute du programme
    var overlay by remember { mutableStateOf(true) }
    var lastKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tracksPanel by remember { mutableStateOf(false) }
    var nextCountdown by remember { mutableIntStateOf(-1) }
    var retries by remember { mutableIntStateOf(0) }
    var prefsApplied by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var liveDelayMs by remember { mutableLongStateOf(0L) }    // direct : retard accumulé par les pauses
    var pausedAt by remember { mutableLongStateOf(0L) }       // direct : début de la pause en cours
    val focus = remember { FocusRequester() }
    val firstButton = remember { FocusRequester() }

    val isReplay = current.kind == "program"
    val isLive = current.kind == "live"
    fun prefKey(it: Item) = if (it.kind == "episode") it.parent?.key ?: it.key else it.key

    fun save(finished: Boolean? = null) {
        val it = current
        if (it.kind !in RESUMABLE || pos <= 0) return
        model.saveProgress(it, pos, len, finished ?: Library.isFinished(pos, len))
    }

    fun play(item: Item, startMs: Long, replayMinute: Int = 0) {
        current = item; error = ""; buffering = true; prefsApplied = false; nextCountdown = -1
        liveDelayMs = 0; pausedAt = 0
        offsetMin = replayMinute
        pos = startMs; len = if (item.kind == "program") (item.end - item.start) * 1000 else 0
        val url = if (item.kind == "program" && replayMinute > 0)
            XtreamClient.shiftTimeshiftUrl(item.url, replayMinute) ?: item.url else item.url
        val media = Media(model.libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=" + if (item.kind == "live") 3000 else 2000)
        media.addOption(":http-user-agent=${io.github.pauljean445.iptvtv.data.Http.USER_AGENT}")
        if (startMs > 0 && item.kind in RESUMABLE) media.addOption(":start-time=${startMs / 1000.0}")
        if (item.kind in RESUMABLE) lib.pref(prefKey(item))?.let { pref ->
            // VLC choisit d'emblée une piste de la bonne langue ; la piste exacte (VFF / VFQ…) suit dans applyPrefs
            (pref["audio"] as? Map<*, *>)?.get("lang")?.toString()?.takeIf { it.isNotEmpty() }
                ?.let { media.addOption(":audio-language=$it") }
            when (val spu = pref["spu"]) {
                "off" -> media.addOption(":no-sub-autodetect-file")
                is Map<*, *> -> spu["lang"]?.toString()?.takeIf { it.isNotEmpty() }?.let { media.addOption(":sub-language=$it") }
            }
        }
        startedAt = System.currentTimeMillis()
        player.media = media
        media.release()
        player.play()
    }

    fun nextItem(): Item? = when (current.kind) {
        "episode" -> list.getOrNull(list.indexOf(current) + 1)
        "program" -> list.filter { it.start > current.start }.minByOrNull { it.start }
        else -> null
    }

    /** Impose la langue / les sous-titres mémorisés. VLC découvre les pistes une à une et choisit d'abord les
     *  siennes : on réessaie à chaque nouvelle piste (et pendant les 15 premières secondes) jusqu'à ce que les
     *  pistes voulues soient actives. Ensuite on n'y touche plus (un choix fait pendant la lecture est respecté). */
    fun applyPrefs() {
        if (prefsApplied || current.kind !in RESUMABLE) return
        if (System.currentTimeMillis() - startedAt > 15_000) { prefsApplied = true; return }
        val pref = lib.pref(prefKey(current)) ?: run { prefsApplied = true; return }
        val tracks = Track.of(player.media)
        if (tracks.none { it.kind == "audio" }) return
        val audio = Track.match(tracks, "audio", pref["audio"])?.takeIf { it >= 0 }
        val spu = Track.match(tracks, "spu", pref["spu"])
        if (audio != null && player.audioTrack != audio) player.audioTrack = audio
        if (spu != null && player.spuTrack != spu) player.spuTrack = spu
        if ((audio == null || player.audioTrack == audio) && (spu == null || player.spuTrack == spu)) prefsApplied = true
    }

    DisposableEffect(Unit) {
        player.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true; buffering = false; retries = 0; applyPrefs()
                    if (pausedAt > 0) { liveDelayMs += System.currentTimeMillis() - pausedAt; pausedAt = 0 }
                }
                MediaPlayer.Event.Paused -> { playing = false; if (current.kind == "live") pausedAt = System.currentTimeMillis() }
                MediaPlayer.Event.Stopped -> playing = false
                MediaPlayer.Event.Buffering -> buffering = ev.buffering < 100f
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected -> applyPrefs()
                MediaPlayer.Event.EndReached -> {
                    if (current.kind in RESUMABLE) { pos = maxOf(pos, len); save(finished = true) }
                    if (nextItem() != null) nextCountdown = 5 else onExit()
                }
                MediaPlayer.Event.EncounteredError -> {
                    if (retries < MAX_RETRIES) {  // coupure : reconnexion, à la position atteinte
                        retries++
                        play(current, if (current.kind in RESUMABLE) pos else 0, offsetMin)
                    } else error = "Lecture impossible : le flux ne répond pas."
                }
            }
        }
        val startMs = if (resume && start.kind in RESUMABLE) lib.resumePosition(start.key) else 0L
        play(start, startMs)
        onDispose {
            save()
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
        }
    }

    // Position, sauvegarde toutes les 10 s, masquage des commandes après 5 s sans touche
    LaunchedEffect(Unit) {
        var lastSave = System.currentTimeMillis()
        while (true) {
            delay(500)
            if (playing && !prefsApplied) applyPrefs()
            if (playing) {
                pos = if (isReplayItem(current)) offsetMin * 60_000L + player.time else player.time
                if (!isReplayItem(current) && player.length > 0) len = player.length
            }
            if (System.currentTimeMillis() - lastSave > 10_000 && playing) { save(); lastSave = System.currentTimeMillis() }
            if (overlay && playing && System.currentTimeMillis() - lastKey > 5000 && !tracksPanel) overlay = false
        }
    }
    LaunchedEffect(nextCountdown) {
        if (nextCountdown > 0) { delay(1000); nextCountdown-- }
        else if (nextCountdown == 0) nextItem()?.let { n ->
            play(n, if (n.kind in RESUMABLE) lib.resumePosition(n.key) else 0)
        }
    }

    /** Va à la position target (ms). Replay : à la minute (le serveur découpe les enregistrements par minute). */
    fun seekTo(target: Long) {
        if (isReplay) {
            val minute = (target / 60_000).toInt().coerceIn(0, ((len / 60_000) - 1).toInt().coerceAtLeast(0))
            play(current, 0, minute)
        } else if (!isLive && len > 0) {
            player.time = target.coerceIn(0, len - 1000)
            pos = player.time
        }
    }

    fun seek(deltaMs: Long) = seekTo(pos + deltaMs)

    fun zap(step: Int) {
        val i = list.indexOf(current)
        if (i < 0 || list.size < 2) return
        play(list[(i + step + list.size) % list.size], 0)
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastKey = System.currentTimeMillis()
                val code = e.key.nativeKeyCode
                if (code == AndroidKey.KEYCODE_BACK) {
                    when {
                        nextCountdown >= 0 -> { nextCountdown = -1; return@onPreviewKeyEvent true }
                        overlay && !tracksPanel -> { overlay = false; focus.requestFocus(); return@onPreviewKeyEvent true }
                    }
                    return@onPreviewKeyEvent false
                }
                if (overlay || tracksPanel) return@onPreviewKeyEvent false  // les boutons gèrent les touches
                when (code) {
                    AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        overlay = true
                    }
                    AndroidKey.KEYCODE_DPAD_LEFT, AndroidKey.KEYCODE_MEDIA_REWIND -> seek(if (isReplay) -60_000 else -10_000)
                    AndroidKey.KEYCODE_DPAD_RIGHT, AndroidKey.KEYCODE_MEDIA_FAST_FORWARD -> seek(if (isReplay) 60_000 else 10_000)
                    AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_CHANNEL_UP -> if (isLive) zap(-1) else overlay = true
                    AndroidKey.KEYCODE_DPAD_DOWN, AndroidKey.KEYCODE_CHANNEL_DOWN -> if (isLive) zap(1) else overlay = true
                    AndroidKey.KEYCODE_MENU -> tracksPanel = true
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        AndroidView(factory = {
            VLCVideoLayout(context).also { v -> v.keepScreenOn = true; player.attachViews(v, null, false, false) }
        }, modifier = Modifier.fillMaxSize())

        if (buffering && error.isEmpty()) Text("Chargement…", color = Color.White, fontSize = 18.sp,
            modifier = Modifier.align(Alignment.Center))
        if (error.isNotEmpty()) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error, color = C.live, fontSize = 20.sp)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("Réessayer", { retries = 0; play(current, pos, offsetMin) }, primary = true)
                AppButton("Quitter", onExit)
            }
        }

        AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6100D22))))
                    .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
            ) {
                val title = if (current.parent != null && current.kind == "episode")
                    "${current.parent!!.name} · ${current.episodeCode} · ${current.name}" else current.name
                Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (isLive) {
                    val delayed = liveDelayMs + if (pausedAt > 0) System.currentTimeMillis() - pausedAt else 0
                    Text(if (delayed > 1000) "● Différé : ${fmtDelay(delayed)}" else "● En direct" +
                        if (list.size > 1) "  ·  ↑ ↓ pour changer de chaîne" else "",
                        color = if (delayed > 1000) C.accentHi else C.live, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (!isLive && len > 0) SeekBar(pos, len, stepMs = if (isReplay) 60_000 else 10_000,
                    onSeek = { target -> seekTo(target) }, modifier = Modifier.padding(top = 14.dp))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(if (playing) "Pause" else "Lecture", { if (player.isPlaying) player.pause() else player.play() },
                        Modifier.focusRequester(firstButton), primary = true,
                        icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow)
                    if (isLive && (liveDelayMs > 1000 || pausedAt > 0)) AppButton("Revenir au direct", { play(current, 0) },
                        icon = Icons.Filled.LiveTv)
                    if (!isLive) {
                        AppButton(if (isReplay) "1 min" else "10 s", { seek(if (isReplay) -60_000 else -10_000) }, icon = Icons.Filled.FastRewind)
                        AppButton(if (isReplay) "1 min" else "10 s", { seek(if (isReplay) 60_000 else 10_000) }, icon = Icons.Filled.FastForward)
                    }
                    AppButton("Langue et sous-titres", { tracksPanel = true }, icon = Icons.Filled.Subtitles)
                    nextItem()?.let { n ->
                        AppButton(if (n.kind == "episode") "Épisode suivant · ${n.episodeCode}" else "Programme suivant",
                            { save(); play(n, if (n.kind in RESUMABLE) lib.resumePosition(n.key) else 0) }, icon = Icons.Filled.SkipNext)
                    }
                }
            }
        }

        if (nextCountdown > 0) Box(Modifier.align(Alignment.Center).background(C.panel, RoundedCornerShape(16.dp)).padding(28.dp)) {
            Text("${if (current.kind == "episode") "Épisode" else "Programme"} suivant dans $nextCountdown s  ·  Retour pour annuler",
                color = C.text, fontSize = 20.sp)
        }
    }

    if (tracksPanel) TracksPanel(player, onPick = { kind, track ->
        if (kind == "audio") player.audioTrack = track!!.id else player.spuTrack = track?.id ?: -1
        if (current.kind in RESUMABLE) lib.setPref(prefKey(current), kind, track?.pref() ?: "off")
    }, onDismiss = { tracksPanel = false; focus.requestFocus() })

    LaunchedEffect(overlay) {
        try { if (overlay) firstButton.requestFocus() else focus.requestFocus() } catch (e: Exception) { }
    }
}

private fun isReplayItem(it: Item) = it.kind == "program"

/** Langue et sous-titres pendant la lecture : deux colonnes ; le choix est appliqué et mémorisé aussitôt. */
@Composable
private fun TracksPanel(player: MediaPlayer, onPick: (String, Track?) -> Unit, onDismiss: () -> Unit) {
    val tracks = remember { Track.of(player.media) }
    val audio = tracks.filter { it.kind == "audio" }
    val subs = tracks.filter { it.kind == "spu" }
    var audioId by remember { mutableIntStateOf(player.audioTrack) }
    var spuId by remember { mutableIntStateOf(player.spuTrack) }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(820.dp).background(C.panel, RoundedCornerShape(16.dp))
            .border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(26.dp)) {
            Text("Langue et sous-titres", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (tracks.isEmpty()) Text("Aucune autre piste dans ce flux.", color = C.muted, fontSize = 16.sp,
                modifier = Modifier.padding(top = 14.dp))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Langues", color = C.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (audio.isEmpty()) Text("Une seule piste", color = C.muted, fontSize = 14.sp)
                    audio.forEachIndexed { i, t ->
                        AppButton(t.label, { audioId = t.id; onPick("audio", t) },
                            (if (i == 0) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
                            primary = audioId == t.id, icon = if (audioId == t.id) Icons.Filled.Check else null)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sous-titres", color = C.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    AppButton("Aucun", { spuId = -1; onPick("spu", null) }, Modifier.fillMaxWidth(),
                        primary = spuId == -1, icon = if (spuId == -1) Icons.Filled.Check else null)
                    subs.forEach { t ->
                        AppButton(t.label, { spuId = t.id; onPick("spu", t) }, Modifier.fillMaxWidth(),
                            primary = spuId == t.id, icon = if (spuId == t.id) Icons.Filled.Check else null)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                AppButton("Fermer", onDismiss)
            }
        }
    }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (e: Exception) { } }
}

private fun fmtDelay(ms: Long): String {
    val s = ms / 1000
    return if (s >= 60) "${s / 60} min %02d s".format(s % 60) else "$s s"
}

/**
 * Barre de temps à la télécommande : ← → déplacent le curseur (de plus en plus vite si on reste appuyé),
 * OK y va tout de suite, sinon on y va 1 s après le dernier appui.
 */
@Composable
private fun SeekBar(pos: Long, len: Long, stepMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Long?>(null) }
    var lastMove by remember { mutableLongStateOf(0L) }
    LaunchedEffect(target, lastMove) {
        val t = target ?: return@LaunchedEffect
        delay(1000)
        onSeek(t); target = null
    }
    val shown = target ?: pos
    Column(modifier
        .onFocusChanged { focused = it.isFocused; if (!it.isFocused) target = null }
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val repeat = e.nativeKeyEvent.repeatCount
            val step = when {
                repeat > 25 -> stepMs * 30
                repeat > 10 -> stepMs * 6
                repeat > 3 -> stepMs * 3
                else -> stepMs
            }
            when (e.key.nativeKeyCode) {
                AndroidKey.KEYCODE_DPAD_LEFT -> { target = (shown - step).coerceIn(0, len); lastMove = System.currentTimeMillis(); true }
                AndroidKey.KEYCODE_DPAD_RIGHT -> { target = (shown + step).coerceIn(0, len); lastMove = System.currentTimeMillis(); true }
                AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER -> {
                    target?.let { onSeek(it); target = null }; true
                }
                else -> false
            }
        }
        .focusable()) {
        Box(Modifier.fillMaxWidth().height(if (focused) 10.dp else 5.dp)
            .background(Color(0x3CFFFFFF), RoundedCornerShape(5.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((shown.toFloat() / len).coerceIn(0f, 1f))
                .background(if (focused) C.accentHi else C.accent, RoundedCornerShape(5.dp)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(fmtTime(shown) + if (target != null) "  ·  OK pour y aller" else if (focused) "  ·  ← → pour se déplacer" else "",
                color = if (target != null) C.accentHi else C.muted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(len), color = C.muted, fontSize = 14.sp)
        }
    }
}

package io.github.pauljean445.iptvtv

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.pauljean445.iptvtv.data.CatalogCache
import io.github.pauljean445.iptvtv.data.Cloud
import io.github.pauljean445.iptvtv.data.Item
import io.github.pauljean445.iptvtv.data.Library
import io.github.pauljean445.iptvtv.data.Obj
import io.github.pauljean445.iptvtv.data.PairingServer
import io.github.pauljean445.iptvtv.data.Playlist
import io.github.pauljean445.iptvtv.data.Source
import io.github.pauljean445.iptvtv.data.Store
import io.github.pauljean445.iptvtv.data.Sync
import io.github.pauljean445.iptvtv.data.XtreamClient
import io.github.pauljean445.iptvtv.data.now
import io.github.pauljean445.iptvtv.update.Updater
import io.github.pauljean445.iptvtv.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.LibVLC

/** Écrans de l'appli (pile : Retour revient au précédent). */
sealed class Screen {
    object Loading : Screen()
    data class Pairing(val notice: String = "", val iptvOnly: Boolean = false) : Screen()
    object Profiles : Screen()
    object Main : Screen()
    object Settings : Screen()
    data class Details(val item: Item) : Screen()
    data class Programs(val channel: Item) : Screen()
    data class Player(val item: Item, val list: List<Item>, val resume: Boolean) : Screen()
}

/** Catalogue du profil : entrées par onglet et catégories dans l'ordre du fournisseur. */
class Catalog(items: List<Item>) {
    val byKey: Map<String, Item> = items.associateBy { it.key }
    val byTab: Map<String, List<Item>>
    val groups: Map<String, List<String>>

    init {
        val live = items.filter { it.kind == "live" }
        byTab = mapOf(
            "live" to live, "movie" to items.filter { it.kind == "movie" },
            "series" to items.filter { it.kind == "series" }, "replay" to live.filter { it.archiveDays > 0 },
        )
        groups = byTab.mapValues { (_, list) -> list.map { it.groupLabel }.distinct() }
    }
}

class AppModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app.filesDir)
    val cloud = Cloud(app.filesDir)
    /** Moteur VLC (un seul pour toute l'appli : son démarrage prend quelques centaines de ms). */
    val libVlc: LibVLC by lazy { LibVLC(app, arrayListOf("--http-reconnect", "--no-stats")) }

    // ---- Navigation ------------------------------------------------------------------ //
    val stack = mutableStateListOf<Screen>(Screen.Loading)
    val screen: Screen get() = stack.last()
    fun open(s: Screen) {
        when (s) {
            is Screen.Player -> focusKey = s.item.key
            is Screen.Details -> focusKey = s.item.key
            is Screen.Programs -> focusKey = s.channel.key
            else -> Unit
        }
        stack.add(s)
    }
    fun replace(s: Screen) { stack.clear(); stack.add(s) }
    /** Retour : false s'il n'y a plus d'écran (l'appli se ferme). */
    fun back(): Boolean { if (stack.size <= 1) return false; stack.removeAt(stack.lastIndex); return true }

    // ---- Profil en cours --------------------------------------------------------------- //
    var settings: Obj by mutableStateOf(LinkedHashMap())
    var profileId by mutableStateOf("")
    lateinit var library: Library
    /** Change à chaque modification des favoris / reprises : les écrans qui le lisent se redessinent. */
    var libVersion by mutableIntStateOf(0); private set
    var source: Source? = null; private set
    var client: XtreamClient? = null; private set
    var catalog by mutableStateOf<Catalog?>(null); private set
    private var normJob: Job? = null

    /** Affiche le catalogue, puis prépare en tâche de fond les noms pour la recherche (première recherche fluide). */
    private fun showCatalog(c: Catalog) {
        catalog = c
        normJob?.cancel()
        normJob = viewModelScope.launch(Dispatchers.Default) { c.byKey.values.forEach { it.norm } }
    }
    var catalogError by mutableStateOf(""); private set
    var catalogLoading by mutableStateOf(false); private set
    var toast by mutableStateOf("")
    /** Entrée à resélectionner au retour dans une liste (après une lecture, une fiche…). */
    var focusKey: String? = null
    private var catalogJob: Job? = null

    // ---- Mise à jour de l'appli (releases GitHub) ----------------------------------------- //
    var update by mutableStateOf<Updater.Info?>(null)   // version proposée (fenêtre affichée)
    var updateProgress by mutableStateOf(-1f); private set  // téléchargement : 0..1, -1 sinon
    var updateError by mutableStateOf(""); private set
    private var updateJob: Job? = null
    @Volatile private var updateCancelled = false
    private var updateChecked = false

    /** Cherche une version plus récente ; silent : au démarrage, rien n'est affiché s'il n'y en a pas. */
    fun checkUpdate(silent: Boolean) {
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { Updater.check() }
                if (info != null) { updateError = ""; update = info }
                else if (!silent) toast = "Tu as la dernière version (${io.github.pauljean445.iptvtv.BuildConfig.VERSION_NAME})."
            } catch (e: Exception) {
                if (!silent) toast = "Impossible de vérifier les mises à jour : ${e.message}"
            }
        }
    }

    fun startUpdate(context: android.content.Context) {
        val info = update ?: return
        updateCancelled = false
        updateError = ""
        updateProgress = 0f
        updateJob = viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    Updater.download(context.applicationContext, info,
                        { got, total -> if (total > 0) updateProgress = (got.toFloat() / total).coerceAtMost(0.99f) },
                        { updateCancelled })
                }
                updateProgress = 1f
                if (::library.isInitialized) { library.save(); withContext(Dispatchers.IO) { pushNow() } }
                withContext(Dispatchers.IO) { Updater.install(context.applicationContext, apk) }
            } catch (e: Updater.Cancelled) {
                updateProgress = -1f
            } catch (e: Exception) {
                updateProgress = -1f
                updateError = "La mise à jour a échoué : ${e.message}"
            }
        }
    }

    fun cancelUpdate() { updateCancelled = true; updateJob?.cancel(); updateProgress = -1f }
    private var syncJob: Job? = null

    val profiles: List<Obj> get() = store.profiles(settings)
    val currentProfile: Obj? get() = profiles.firstOrNull { it["id"] == profileId }

    fun libraryChanged() { libVersion++ }

    // ---- Démarrage ----------------------------------------------------------------------- //
    fun start(notice: String = "") {
        if (!updateChecked) {  // une fois par lancement, 8 s après l'ouverture (comme sur PC)
            updateChecked = true
            viewModelScope.launch { delay(8000); checkUpdate(silent = true) }
        }
        if (!cloud.signedIn) { replace(Screen.Pairing(notice)); return }
        settings = store.loadSettings()
        if (settings["sync_uid"].str() == cloud.uid) {
            // Déjà synchronisée avec ce compte : ouverture immédiate sur les données de la TV ; la liste des profils
            // est mise à jour par le premier passage de synchro (et une session refusée y est détectée)
            if (profiles.size > 1) replace(Screen.Profiles) else openProfile(settings["profile"].str())
            return
        }
        replace(Screen.Loading)
        viewModelScope.launch {
            val lost = withContext(Dispatchers.IO) {
                try {
                    Sync.startup(store, cloud); null
                } catch (e: Cloud.AuthError) {
                    e.message ?: "session expirée"
                } catch (e: Exception) {
                    Log.e(TAG, "démarrage", e); null  // on continue avec les profils de la TV
                }
            }
            if (lost != null) { cloud.signOut(); start("Reconnexion nécessaire : $lost."); return@launch }
            settings = store.loadSettings()
            if (profiles.size > 1) replace(Screen.Profiles) else openProfile(settings["profile"].str())
        }
    }

    /** Formulaire du téléphone (QR code) : compte du foyer, puis abonnement IPTV s'il n'est pas déjà dans le
     *  compte. Appelé dans le thread du serveur (réseau autorisé). */
    fun pair(fields: Map<String, String>): PairingServer.Reply {
        if (fields["step"] == "iptv") return pairIptv(fields)
        val email = fields["email"].orEmpty().trim()
        val password = fields["password"].orEmpty()
        if (email.isEmpty() || password.isEmpty()) return PairingServer.Reply.Error("Saisis l'email et le mot de passe du compte.")
        try {
            cloud.signIn(email, password)
            Sync.startup(store, cloud)
            // Abonnements IPTV du foyer (chiffrés dans le compte) : déchiffrés dans les profils de la TV
            val s = store.loadSettings()
            for (pid in store.profiles(s).map { it["id"].str() }) {
                val (_, f) = Sync.sourceWrites(store, pid, cloud.getDoc("profiles/$pid"), cloud.vaultKey)
                cloud.commit(Sync.withFields(emptyList(), pid, f))
            }
            if (Source.fromProfile(store.readProfile(s["profile"].str())) == null) return PairingServer.Reply.AskIptv(
                "TV connectée au compte. Aucun abonnement IPTV n'est encore enregistré dans le compte : saisis-le " +
                    "une seule fois.")
        } catch (e: Cloud.CloudError) {
            return PairingServer.Reply.Error("Compte du foyer : ${e.message}.")
        }
        viewModelScope.launch { start() }
        return PairingServer.Reply.Done
    }

    /** Abonnement IPTV saisi sur le téléphone : vérifié, puis donné aux profils de la TV qui n'en ont pas et
     *  envoyé chiffré dans le compte (partagé avec les autres appareils). */
    private fun pairIptv(fields: Map<String, String>): PairingServer.Reply {
        if (!cloud.signedIn) return PairingServer.Reply.Error("La TV n'est plus connectée au compte : scanne à nouveau le QR code.")
        val mode = if (fields["mode"] == "url") "url" else "xtream"
        val own: Obj = LinkedHashMap<String, Any?>().apply {
            put("mode", mode); put("stream_format", "ts"); put("remember", true)
            if (mode == "xtream") {
                put("server", fields["server"].orEmpty().trim()); put("username", fields["username"].orEmpty().trim())
                put("password", fields["iptv_password"].orEmpty())
            } else put("url", fields["url"].orEmpty().trim())
            put("source_t", now())
        }
        val src = Source.fromProfile(own) ?: return PairingServer.Reply.Error("Complète les identifiants IPTV.")
        try {  // vérifié avant tout : une erreur de saisie se corrige tout de suite sur le téléphone
            if (src.type == "xtream") runBlocking { XtreamClient(src.server, src.username, src.password).fetchCatalog() }
        } catch (e: Exception) {
            return PairingServer.Reply.Error("Abonnement IPTV : ${e.message ?: "connexion impossible"}")
        }
        val s = store.loadSettings()
        val current = s["profile"].str()
        for (pid in store.profiles(s).map { it["id"].str() }) {
            if (pid != current && Source.fromProfile(store.readProfile(pid)) != null) continue
            store.writeProfile(pid, LinkedHashMap(store.readProfile(pid)).apply { putAll(own) })
            try {
                val (_, f) = Sync.sourceWrites(store, pid, cloud.getDoc("profiles/$pid"), cloud.vaultKey)
                cloud.commit(Sync.withFields(emptyList(), pid, f))
            } catch (e: Cloud.CloudError) {
                Log.w(TAG, "envoi de la source : ${e.message}")  // renvoyée au prochain passage de synchro
            }
        }
        viewModelScope.launch {
            if (profileId.isNotEmpty() && screen is Screen.Pairing) { back(); loadCatalog(force = true) } else start()
        }
        return PairingServer.Reply.Done
    }

    fun openProfile(pid: String) {
        settings = store.loadSettings()
        val id = if (profiles.any { it["id"] == pid }) pid else profiles[0]["id"].str()
        settings["profile"] = id
        store.saveSettings(settings)
        profileId = id
        library = Library(store.profileDir(id))
        libraryChanged()
        catalog = null
        replace(Screen.Main)
        loadCatalog()
        startSyncLoop()
    }

    fun switchProfile(pid: String) {
        syncJob?.cancel()
        val (lib, old) = library to profileId
        viewModelScope.launch(Dispatchers.IO) { pushNow(lib, old) }
        openProfile(pid)
    }

    // ---- Catalogue ----------------------------------------------------------------------- //
    fun loadCatalog(force: Boolean = false) {
        catalogJob?.cancel()
        val own = store.readProfile(profileId)
        val src = Source.fromProfile(own)
        source = src
        catalogError = ""
        if (src == null) { client = null; catalogError = "Aucune source IPTV pour ce profil."; return }
        val folder = store.profileDir(profileId)
        val keywords = listOf("séries", "series", "série", "saison", "season")
        catalogJob = viewModelScope.launch {
            catalogLoading = true
            try {
                if (src.type == "xtream") {
                    val c = XtreamClient(src.server, src.username, src.password, src.format)
                    client = c
                    val t0 = System.currentTimeMillis()
                    val cached = if (force) null else withContext(Dispatchers.IO) { CatalogCache.load(folder, src, c) }
                    Log.i(TAG, "catalogue en cache : ${cached?.second?.size ?: 0} entrées en ${System.currentTimeMillis() - t0} ms")
                    if (cached != null && catalog == null) showCatalog(withContext(Dispatchers.Default) { Catalog(cached.second) })
                    val fresh = withContext(Dispatchers.IO) {
                        val raw = c.fetchCatalog()
                        val sig = XtreamClient.signature(raw)
                        if (sig == cached?.first) null
                        else c.buildCatalog(raw).also { CatalogCache.save(folder, src, sig, it) }
                    }
                    if (fresh != null) showCatalog(withContext(Dispatchers.Default) { Catalog(fresh) })
                } else {
                    client = null
                    showCatalog(withContext(Dispatchers.IO) { Catalog(Playlist.loadFromUrl(src.url, keywords)) })
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "catalogue", e)
                if (catalog == null) catalogError = e.message ?: "Chargement impossible."
                else toast = "Actualisation du catalogue impossible : ${e.message}"
            } finally {
                catalogLoading = false
            }
        }
    }

    // ---- Favoris, reprises ------------------------------------------------------------------ //
    // ---- Profils (gestion) ----------------------------------------------------------------- //
    fun createProfile(name: String, color: String) {
        val pid = store.createProfile(settings, name, color, profileId)  // même abonnement IPTV que le profil en cours
        store.saveSettings(settings)
        settings = LinkedHashMap(settings)
        syncSoon()
        switchProfile(pid)
    }

    fun editProfile(pid: String, name: String, color: String) {
        store.profiles(settings).firstOrNull { it["id"] == pid }?.let { it["name"] = name; it["color"] = color; it["t"] = now() }
        store.saveSettings(settings)
        settings = LinkedHashMap(settings)
        syncSoon()
    }

    /** Supprime un profil (sur tous les appareils du foyer, avec ses données). */
    fun deleteProfile(pid: String) {
        val wasCurrent = pid == profileId
        store.deleteProfile(settings, pid)
        store.saveSettings(settings)
        settings = LinkedHashMap(settings)
        if (wasCurrent) {
            syncJob?.cancel()
            openProfile(profiles[0]["id"].str())
        }
        syncSoon()
    }

    fun resetProfile(pid: String) {
        if (pid == profileId) { library.reset(); libraryChanged(); syncSoon(); return }
        val lib = Library(store.profileDir(pid))
        lib.reset()
        viewModelScope.launch(Dispatchers.IO) { pushNow(lib, pid) }
    }

    fun toggleFavorite(tab: String, item: Item): Boolean = library.toggleFavorite(tab, item.key).also { libraryChanged() }

    fun removeRecent(item: Item) {
        library.removeRecent(item.key, item.episodes.orEmpty().map { it.key })
        libraryChanged()
    }

    fun saveProgress(item: Item, pos: Long, length: Long, finished: Boolean) {
        library.update(item, pos, length, finished)
        library.save()
        libraryChanged()
    }

    // ---- Synchro avec le compte du foyer ------------------------------------------------------ //
    private fun startSyncLoop() {
        syncJob?.cancel()
        val pid = profileId
        syncJob = viewModelScope.launch {
            var round = 0
            while (isActive) {
                syncOnce(pid, withRoot = round % SYNC_ROOT_EVERY == 0)
                round++
                delay(SYNC_MS)
            }
        }
    }

    /** Un passage : lecture du serveur (réseau), fusion ici (interface), envoi de ce qui est plus récent ici. */
    private suspend fun syncOnce(pid: String, withRoot: Boolean) {
        if (!cloud.signedIn || pid != profileId) return
        try {
            val (root, doc) = withContext(Dispatchers.IO) {
                (if (withRoot) cloud.getDoc("") else null) to cloud.getDoc("profiles/$pid")
            }
            if (pid != profileId) return
            val writes = ArrayList<Cloud.Write>()
            if (withRoot) {
                val r = Sync.profilesWrites(store, settings, root, cloud.uid)
                writes += r.writes
                if (r.changed) {
                    val gone = pid in r.removed
                    Sync.applyProfiles(store, settings, r.removed, r.added, if (gone) null else pid)
                    store.saveSettings(settings)
                    settings = LinkedHashMap(settings)  // liste des profils redessinée
                    if (gone) {
                        withContext(Dispatchers.IO) { cloud.commit(writes) }
                        toast = "Ce profil a été supprimé depuis un autre appareil."
                        openProfile(profiles[0]["id"].str())
                        return
                    }
                }
            }
            val (changed, libWrites) = Sync.libraryWrites(library, doc, pid)
            if (changed) libraryChanged()
            writes += libWrites
            val (srcChanged, srcFields) = withContext(Dispatchers.IO) { Sync.sourceWrites(store, pid, doc, cloud.vaultKey) }
            val all = Sync.withFields(writes, pid, srcFields)
            writes.clear(); writes += all
            if (srcChanged) { toast = "Abonnement IPTV mis à jour depuis un autre appareil"; loadCatalog(force = true) }
            if (writes.isNotEmpty()) withContext(Dispatchers.IO) { cloud.commit(writes) }
        } catch (e: Cloud.AuthError) {
            sessionLost(e.message ?: "session expirée")
        } catch (e: Cloud.Forbidden) {
            sessionLost("l'accès de ce compte a été retiré")
        } catch (e: Cloud.CloudError) {
            Log.w(TAG, "synchro : ${e.message}")  // serveur injoignable : nouvel essai au passage suivant
        }
    }

    fun syncSoon() { viewModelScope.launch { syncOnce(profileId, withRoot = true) } }

    private fun sessionLost(reason: String) {
        syncJob?.cancel()
        cloud.signOut()
        start("Reconnexion nécessaire : $reason.")
    }

    /** Envoie tout de suite les dernières positions (appli quittée, changement de profil). Réseau : hors interface. */
    fun pushNow(lib: Library = library, pid: String = profileId) {
        if (!cloud.signedIn || pid.isEmpty()) return
        try { Sync.syncLibraryNow(cloud, lib, pid, store, 4) } catch (e: Exception) { Log.w(TAG, "envoi final : ${e.message}") }
    }

    fun onAppHidden() {
        if (!::library.isInitialized) return
        library.save()
        val (lib, pid) = library to profileId
        viewModelScope.launch(Dispatchers.IO) { withTimeoutOrNull(6000) { pushNow(lib, pid) } }
    }

    fun signOut() {
        syncJob?.cancel()
        if (::library.isInitialized) {
            val (lib, pid) = library to profileId
            viewModelScope.launch(Dispatchers.IO) { pushNow(lib, pid); cloud.signOut() }
        } else cloud.signOut()
        replace(Screen.Pairing())
    }

    fun newPairingServer(iptvOnly: Boolean): PairingServer = PairingServer(iptvOnly) { pair(it) }

    companion object {
        const val TAG = "IPTV"
        const val SYNC_MS = 30_000L      // synchro toutes les 30 s…
        const val SYNC_ROOT_EVERY = 4    // … et la liste des profils un passage sur 4 (2 min)
    }
}

package io.github.pauljean445.iptvtv.player

import android.net.Uri
import io.github.pauljean445.iptvtv.data.Http
import io.github.pauljean445.iptvtv.data.Obj
import io.github.pauljean445.iptvtv.data.str
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.IMedia
import java.text.Normalizer

/**
 * Pistes audio et sous-titres, portage de tracks.py. Les numéros de pistes changent d'un fichier à l'autre
 * (VF en 1re piste ici, en 2e là) : un choix est mémorisé par langue + libellé ({"lang": "fre", "desc": "VFF"}),
 * puis retrouvé dans chaque fichier. Même format que sur PC : les choix sont partagés entre appareils.
 */
data class Track(val kind: String, val id: Int, val lang: String, val desc: String) {
    val label: String
        get() {
            val name = NAMES[lang] ?: lang.uppercase()
            val d = desc.trim()
            return when {
                d.isEmpty() -> name.ifEmpty { "Piste $id" }
                name.isEmpty() || name.lowercase() in d.lowercase() -> d
                else -> "$name · $d"
            }
        }

    fun pref(): Obj = linkedMapOf("lang" to lang, "desc" to desc)

    companion object {
        private val ALIASES = mapOf("fr" to "fre", "fra" to "fre", "en" to "eng", "es" to "spa", "de" to "ger", "deu" to "ger",
            "it" to "ita", "pt" to "por", "nl" to "dut", "nld" to "dut", "sv" to "swe", "no" to "nor", "nb" to "nor",
            "da" to "dan", "fi" to "fin", "ru" to "rus", "pl" to "pol", "tr" to "tur", "ar" to "ara", "ja" to "jpn",
            "ko" to "kor", "zh" to "chi", "zho" to "chi", "hi" to "hin", "he" to "heb", "el" to "gre", "ell" to "gre",
            "ro" to "rum", "ron" to "rum", "cs" to "cze", "ces" to "cze", "hu" to "hun", "uk" to "ukr")
        private val NAMES = mapOf("fre" to "Français", "eng" to "Anglais", "spa" to "Espagnol", "ger" to "Allemand",
            "ita" to "Italien", "por" to "Portugais", "dut" to "Néerlandais", "swe" to "Suédois", "nor" to "Norvégien",
            "dan" to "Danois", "fin" to "Finnois", "rus" to "Russe", "pol" to "Polonais", "tur" to "Turc", "ara" to "Arabe",
            "jpn" to "Japonais", "kor" to "Coréen", "chi" to "Chinois", "hin" to "Hindi", "heb" to "Hébreu", "gre" to "Grec",
            "rum" to "Roumain", "cze" to "Tchèque", "hun" to "Hongrois", "ukr" to "Ukrainien")

        fun normLang(code: String?): String {
            val c = (code ?: "").trim().lowercase()
            val n = ALIASES[c] ?: c
            return if (n in setOf("und", "unk", "mis", "zxx")) "" else n
        }

        private fun text(s: String?) = Normalizer.normalize(s ?: "", Normalizer.Form.NFC)

        private val probed = HashMap<String, List<Track>>()

        /** Pistes audio / sous-titres d'un fichier, lues avant la lecture (préanalyse de VLC, quelques secondes). */
        suspend fun probe(libVlc: LibVLC, url: String): List<Track> {
            synchronized(probed) { probed[url] }?.let { return it }
            val tracks = withContext(Dispatchers.IO) {
                val media = Media(libVlc, Uri.parse(url))
                media.addOption(":http-user-agent=${Http.USER_AGENT}")
                val done = CompletableDeferred<Unit>()
                media.setEventListener { if (it.type == IMedia.Event.ParsedChanged) done.complete(Unit) }
                media.parseAsync(IMedia.Parse.ParseNetwork, 8000)
                withTimeoutOrNull(9000) { done.await() }
                val t = of(media)
                media.setEventListener(null)
                media.release()
                t
            }
            if (tracks.isNotEmpty()) synchronized(probed) { probed[url] = tracks }
            return tracks
        }

        /** Pistes audio / sous-titres du média en cours de lecture. */
        fun of(media: IMedia?): List<Track> {
            media ?: return emptyList()
            return (0 until media.trackCount).mapNotNull { i ->
                val t = media.getTrack(i) ?: return@mapNotNull null
                val kind = when (t.type) { IMedia.Track.Type.Audio -> "audio"; IMedia.Track.Type.Text -> "spu"; else -> null }
                    ?: return@mapNotNull null
                Track(kind, t.id, normLang(t.language), text(t.description))
            }
        }

        /** Identifiant de la piste qui correspond au choix mémorisé (null : rien à imposer, -1 : désactivé). */
        fun match(tracks: List<Track>, kind: String, pref: Any?): Int? {
            if (pref == null) return null
            if (pref == "off") return -1
            val p = pref as? Map<*, *> ?: return null
            val same = tracks.filter { it.kind == kind && it.lang == p["lang"].str() }
            same.firstOrNull { it.desc.trim().equals(p["desc"].str().trim(), ignoreCase = true) }?.let { return it.id }
            return same.firstOrNull()?.id  // même langue, autre libellé (ex. « VF » au lieu de « VFF »)
        }

        fun isPref(t: Track, pref: Any?): Boolean {
            val p = pref as? Map<*, *> ?: return false
            return t.lang == p["lang"].str() && t.desc.trim().equals(p["desc"].str().trim(), ignoreCase = true)
        }
    }
}

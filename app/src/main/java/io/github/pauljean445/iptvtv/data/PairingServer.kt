package io.github.pauljean445.iptvtv.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Connexion de la TV depuis un téléphone : la TV affiche un QR code (adresse de la TV sur le réseau local +
 * code à usage unique). Le téléphone l'ouvre, et la TV lui sert un formulaire (compte du foyer, identifiants
 * IPTV) qu'il renvoie à la TV.
 *
 * Sécurité : le serveur n'existe que pendant l'écran de connexion et s'arrête dès que la connexion réussit ;
 * il n'écoute que sur le réseau local ; toute requête sans le code aléatoire (128 bits) du QR est refusée ;
 * il ne sait faire qu'une chose (recevoir ce formulaire) et ne donne accès à rien sur la TV.
 *
 * Deux étapes : le compte du foyer (step=account), puis l'abonnement IPTV (step=iptv) seulement si le compte n'en a
 * pas encore (il y est stocké chiffré, voir Vault). iptvOnly : directement l'étape IPTV (TV déjà connectée).
 * onSubmit est appelé (dans le thread du serveur) avec les champs du formulaire et dit quoi afficher ensuite.
 */
class PairingServer(private val iptvOnly: Boolean = false, private val onSubmit: (Map<String, String>) -> Reply) {
    sealed class Reply {
        object Done : Reply()
        data class Error(val message: String) : Reply()
        /** Compte connecté, mais aucun abonnement IPTV enregistré : on le demande. */
        data class AskIptv(val message: String) : Reply()
    }

    private val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private var socket: ServerSocket? = null
    @Volatile private var done = false

    /** Adresse à mettre dans le QR code, null si la TV n'a pas d'adresse sur le réseau local. */
    var url: String? = null; private set

    fun start(): String? {
        val ip = localIp() ?: return null
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(PORT))
        socket = s
        url = "http://$ip:$PORT/?t=$token"
        thread(name = "pairing", isDaemon = true) {
            while (!done) {
                val client = try { s.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { client.use { handle(it) } }
            }
        }
        return url
    }

    fun stop() {
        done = true
        try { socket?.close() } catch (e: Exception) { }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 15_000
        val input = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
        val requestLine = input.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val (method, target) = parts[0] to parts[1]
        var length = 0
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            if (line.lowercase().startsWith("content-length:")) length = line.substringAfter(":").trim().toIntOrNull() ?: 0
        }
        val query = parseForm(target.substringAfter('?', ""))
        val out = c.getOutputStream()
        fun reply(code: String, html: String) {
            val body = html.toByteArray(Charsets.UTF_8)
            out.write(("HTTP/1.1 $code\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
            out.write(body)
            out.flush()
        }
        if (method == "GET" && target.substringBefore('?') == "/" && query["t"] == token) {
            reply("200 OK", page(form(if (iptvOnly) "iptv" else "account", emptyMap(), null)))
        } else if (method == "POST" && target.substringBefore('?') == "/send" && length in 1..20_000) {
            val buf = CharArray(length)
            var read = 0
            while (read < length) { val n = input.read(buf, read, length - read); if (n < 0) break; read += n }
            val fields = parseForm(String(buf, 0, read))
            if (fields["t"] != token || done) return reply("403 Forbidden", page("<p>Code expiré : scanne à nouveau le QR code affiché sur la TV.</p>"))
            val step = if (fields["step"] == "iptv") "iptv" else "account"
            when (val r = onSubmit(fields)) {
                Reply.Done -> {
                    reply("200 OK", page("<h2>✓ C'est fait</h2><p>Tu peux fermer cette page et reprendre la télécommande.</p>"))
                    stop()
                }
                is Reply.Error -> reply("200 OK", page(form(step, fields, r.message)))
                is Reply.AskIptv -> reply("200 OK", page(form("iptv", emptyMap(), null, r.message)))
            }
        } else {
            reply("404 Not Found", page("<p>Scanne le QR code affiché sur la TV.</p>"))
        }
    }

    private fun parseForm(s: String): Map<String, String> = s.split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    private fun esc(s: String?) = (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")

    private fun form(step: String, v: Map<String, String>, error: String?, info: String? = null): String {
        val xtream = v["mode"] != "url"
        fun field(name: String, label: String, type: String = "text", value: Boolean = true, hint: String = "") =
            """<label>$label<input name="$name" type="$type" autocomplete="off" autocapitalize="off"
               placeholder="${esc(hint)}" value="${if (value) esc(v[name]) else ""}"></label>"""
        val head = """${if (error != null) "<p class=err>${esc(error)}</p>" else ""}
${if (info != null) "<p class=info>${esc(info)}</p>" else ""}
<form method="post" action="/send">
<input type="hidden" name="t" value="$token"><input type="hidden" name="step" value="$step">"""
        if (step == "account") return """
<h2>Connecter la TV</h2>
$head
<h3>Compte du foyer</h3>
${field("email", "Email", "email")}
${field("password", "Mot de passe", "password", value = false)}
<button type=submit>Connecter la TV</button>
<p class=note>Ces informations vont directement de ton téléphone à la TV, sur ton réseau. Le mot de passe n'est pas
enregistré : la TV garde seulement une session chiffrée. L'abonnement IPTV est récupéré dans le compte (chiffré).</p>
</form>"""
        return """
<h2>Abonnement IPTV</h2>
$head
<h3>Abonnement IPTV</h3>
<div class=tabs>
 <label class=tab><input type=radio name=mode value=xtream ${if (xtream) "checked" else ""}> Xtream Codes</label>
 <label class=tab><input type=radio name=mode value=url ${if (!xtream) "checked" else ""}> URL M3U</label>
</div>
<div class=xt>
${field("server", "Serveur", hint = "http://mon-fournisseur.tld:8080")}
${field("username", "Utilisateur")}
${field("iptv_password", "Mot de passe IPTV", "password", value = false)}
</div>
<div class=m3u>${field("url", "URL de la playlist", "url", hint = "http://…/get.php?username=…")}</div>
<button type=submit>Enregistrer</button>
<p class=note>Saisi une seule fois : l'abonnement est enregistré dans le compte du foyer, chiffré avec une clé tirée
du mot de passe du compte (ni Google ni Firebase ne peuvent le lire), et partagé avec tes autres appareils.</p>
</form>"""
    }

    private fun page(content: String) = """<!doctype html><html lang=fr><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>IPTV Player · TV</title><style>
body{margin:0;background:#100D22;color:#EEEBFF;font:16px system-ui,sans-serif}
main{max-width:460px;margin:0 auto;padding:20px 16px 40px}
h2{margin:8px 0 16px}h3{color:#A6A0CC;font-size:13px;letter-spacing:1px;text-transform:uppercase;margin:22px 0 8px}
label{display:block;color:#A6A0CC;font-size:13px;margin:10px 0}
input[type=text],input[type=email],input[type=password],input[type=url]{display:block;box-sizing:border-box;width:100%;
margin-top:6px;padding:12px;border-radius:10px;border:1px solid #2F2959;background:#15122B;color:#EEEBFF;font-size:16px}
input:focus{outline:none;border-color:#7B66FF}
.tabs{display:flex;gap:8px}.tab{flex:1;margin:0;padding:10px;border:1px solid #2F2959;border-radius:10px;background:#1B1735;color:#EEEBFF}
.m3u{display:none}form:has(input[value=url]:checked) .m3u{display:block}form:has(input[value=url]:checked) .xt{display:none}
button{width:100%;margin-top:22px;padding:14px;border:0;border-radius:10px;background:#7B66FF;color:#fff;font-size:17px;font-weight:600}
.info{background:#1B1735;border:1px solid #2F2959;color:#EEEBFF;padding:10px 12px;border-radius:10px}
.err{background:rgba(255,84,112,.12);border:1px solid rgba(255,84,112,.5);color:#FFC2CD;padding:10px 12px;border-radius:10px}
.note{color:#A6A0CC;font-size:12px;margin-top:16px}
</style></head><body><main>$content</main></body></html>"""

    companion object {
        const val PORT = 8765

        /** Adresse IPv4 de la TV sur le réseau local (Wi-Fi ou Ethernet). */
        fun localIp(): String? = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }
}

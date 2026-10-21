package io.github.pauljean445.iptvtv.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import io.github.pauljean445.iptvtv.BuildConfig
import io.github.pauljean445.iptvtv.data.JSON
import io.github.pauljean445.iptvtv.data.asObj
import io.github.pauljean445.iptvtv.data.str
import io.github.pauljean445.iptvtv.data.toAny
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Mise à jour depuis l'appli : dernière version publiée sur GitHub (page « Releases » du dépôt), comme l'appli PC.
 *
 * Publier une version : augmenter versionCode et versionName (app/build.gradle.kts), lancer build_release.bat, puis
 * créer sur GitHub une release avec le tag « v<versionName> » (ex. v0.3.0) et y joindre dist\IPTVPlayerTV-<version>.apk.
 * Le texte de la release est montré dans l'appli. Le dépôt doit être public (sinon l'API ne répond pas sans identifiants).
 * L'APK doit être signé avec la même clé que la version installée (keystore.properties), sinon Android le refuse.
 */
object Updater {
    const val REPO = "pauljean445/app_iptv_tv"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    data class Info(val version: String, val notes: String, val url: String, val name: String, val size: Long)

    class Cancelled : IOException("téléchargement annulé")

    private fun version(text: String): List<Int> = Regex("\\d+").findAll(text).take(3).map { it.value.toInt() }.toList()

    private fun newer(a: String, b: String): Boolean {
        val x = version(a); val y = version(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** La version publiée si elle est plus récente que celle installée, sinon null. Réseau : hors interface. */
    fun check(): Info? {
        val req = Request.Builder().url(API).header("User-Agent", "IPTVPlayerTV/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.github+json").build()
        val rel = io.github.pauljean445.iptvtv.data.Http.client.newCall(req).execute().use { resp ->
            when {
                resp.code == 404 -> throw IOException("aucune version publiée n'est accessible (dépôt privé ou sans release)")
                !resp.isSuccessful -> throw IOException("GitHub a répondu « ${resp.code} »")
            }
            JSON.parseToJsonElement(resp.body!!.string()).toAny().asObj() ?: return null
        }
        val tag = rel["tag_name"].str().trimStart('v', 'V')
        if (tag.isEmpty() || !newer(tag, BuildConfig.VERSION_NAME)) return null
        val asset = (rel["assets"] as? List<*>).orEmpty().mapNotNull { it.asObj() }
            .firstOrNull { it["name"].str().lowercase().endsWith(".apk") } ?: return null
        return Info(tag, rel["body"].str().trim(), asset["browser_download_url"].str(), File(asset["name"].str()).name,
            asset["size"].str().toLongOrNull() ?: 0L)
    }

    /** Télécharge l'APK (dans le cache de l'appli). progress(reçu, total) ; cancelled() arrête le téléchargement. */
    fun download(context: Context, info: Info, progress: (Long, Long) -> Unit, cancelled: () -> Boolean): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }  // anciennes mises à jour : la TV a peu de place
        // L'APK et son installation prennent environ deux fois sa taille, et Android garde une marge de sécurité
        val free = StatFs(dir.path).availableBytes
        if (info.size > 0 && free < info.size * 2 + 250L * 1024 * 1024) throw IOException(
            "pas assez de place sur la TV (${free / 1_048_576} Mo libres) : libère de l'espace (applis, caches) et réessaie")
        val part = File(dir, info.name + ".part")
        val req = Request.Builder().url(info.url).header("User-Agent", "IPTVPlayerTV/${BuildConfig.VERSION_NAME}").build()
        io.github.pauljean445.iptvtv.data.Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("téléchargement impossible (« ${resp.code} »)")
            val body = resp.body ?: throw IOException("réponse vide")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            var done = 0L
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (cancelled()) { part.delete(); throw Cancelled() }
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        progress(done, total)
                    }
                }
            }
            if (total > 0 && done != total) { part.delete(); throw IOException("téléchargement incomplet") }
        }
        return File(dir, info.name).also { part.renameTo(it) }
    }

    /** L'appli a-t-elle le droit d'installer des APK ? (réglage Android « Installer des applis inconnues ») */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** Ouvre le réglage Android qui autorise cette appli à installer ses mises à jour. */
    fun openInstallPermission(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Confie l'APK à l'installeur d'Android, qui demande confirmation ; l'appli est fermée pendant la mise à jour. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName); setSize(apk.length()) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val intent = PendingIntent.getBroadcast(context, id,
                Intent(context, InstallReceiver::class.java).setPackage(context.packageName), flags)
            session.commit(intent.intentSender)
        }
    }
}

/** Résultat de l'installation : Android demande la confirmation de l'utilisateur, qu'on affiche. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

package io.github.pauljean445.iptvtv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.pauljean445.iptvtv.AppModel

/** Connexion de la TV : QR code à scanner avec le téléphone (formulaire servi par la TV sur le réseau local). */
@Composable
fun PairingScreen(model: AppModel, notice: String, iptvOnly: Boolean = false) {
    var url by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val server = model.newPairingServer(iptvOnly)
        url = try { server.start() } catch (e: Exception) { null }
        failed = url == null
        onDispose { server.stop() }
    }
    val qr = remember(url) { url?.let { qrBitmap(it) } }

    Row(
        Modifier.fillMaxSize().background(C.bg).padding(horizontal = 80.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(64.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("IPTV Player", color = C.accentHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(if (iptvOnly) "Abonnement IPTV" else "Connecter la TV", color = C.text, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
            if (notice.isNotEmpty()) {
                Text(notice, color = C.live, fontSize = 16.sp, modifier = Modifier.padding(bottom = 18.dp))
            }
            Step(1, "Connecte ton téléphone au même Wi-Fi que la TV.")
            Step(2, "Scanne le QR code avec l'appareil photo du téléphone.")
            Step(3, if (iptvOnly) "Sur la page qui s'ouvre, saisis les identifiants de l'abonnement IPTV."
                else "Sur la page qui s'ouvre, saisis l'email et le mot de passe du compte du foyer.")
            Text((if (iptvOnly) "L'abonnement est enregistré dans le compte du foyer, chiffré : partagé avec tes autres " +
                "appareils, illisible pour Google / Firebase. " else "La TV ne garde pas le mot de passe, seulement une " +
                "session chiffrée ; l'abonnement IPTV est récupéré dans le compte. ") + "Cette page n'est accessible " +
                "que sur ton réseau, avec le code du QR, et seulement tant que cet écran est affiché.",
                color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp).width(560.dp))
        }
        Box(
            Modifier.size(380.dp).background(C.surface, RoundedCornerShape(20.dp)).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                qr != null -> Image(qr, contentDescription = "QR code de connexion", Modifier.fillMaxSize())
                failed -> Text("La TV n'est pas connectée au réseau local.\nVérifie le Wi-Fi ou le câble.",
                    color = C.live, fontSize = 16.sp)
                else -> Text("Préparation…", color = C.muted)
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(C.accent, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            Text("$n", color = C.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(text, color = C.text, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp))
    }
}

private fun qrBitmap(text: String): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    val scale = 12
    val bmp = Bitmap.createBitmap(m.width * scale, m.height * scale, Bitmap.Config.RGB_565)
    for (y in 0 until m.height) for (x in 0 until m.width) {
        val c = if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        for (dy in 0 until scale) for (dx in 0 until scale) bmp.setPixel(x * scale + dx, y * scale + dy, c)
    }
    return bmp.asImageBitmap()
}

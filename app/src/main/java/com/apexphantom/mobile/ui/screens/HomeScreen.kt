package com.apexphantom.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.apexphantom.mobile.ApexApp
import com.apexphantom.mobile.data.GameId
import com.apexphantom.mobile.monitor.Thermal
import com.apexphantom.mobile.network.NetworkTools
import com.apexphantom.mobile.shield.Games
import com.apexphantom.mobile.shield.ShieldService
import com.apexphantom.mobile.shield.ShieldStatusHolder
import com.apexphantom.mobile.shizuku.ShizukuManager
import com.apexphantom.mobile.shizuku.ShizukuState
import com.apexphantom.mobile.ui.Ap
import com.apexphantom.mobile.ui.ApCard
import com.apexphantom.mobile.ui.PrimaryButton
import com.apexphantom.mobile.ui.SecondaryButton
import com.apexphantom.mobile.ui.SectionLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Son saat ölçümünü ekranlar arasında paylaşmak için. */
object ClockCache {
    var lastOffsetMs: Long? = null
}

@Composable
fun HomeScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as ApexApp
    val shizuku by ShizukuManager.state.collectAsState()
    val status by ShieldStatusHolder.status.collectAsState()
    val settings by app.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var thermal by remember { mutableStateOf(Thermal.status(context)) }
    var link by remember { mutableStateOf(NetworkTools.linkInfo(context)) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            thermal = Thermal.status(context)
            link = NetworkTools.linkInfo(context)
            overlayOk = Settings.canDrawOverlays(context)
            delay(3000)
        }
    }
    LaunchedEffect(shizuku, settings.autoShield) {
        if (shizuku == ShizukuState.READY && settings.autoShield) ShieldService.start(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("APEX PHANTOM", color = Ap.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("MOBILE", color = Ap.Muted, fontSize = 11.sp, letterSpacing = 2.sp)
            }
            val (dot, label) = when (shizuku) {
                ShizukuState.READY -> Ap.Green to "Shizuku bağlı"
                ShizukuState.NO_PERMISSION -> Ap.Orange to "İzin gerekli"
                ShizukuState.NOT_RUNNING -> Ap.Red to "Shizuku kapalı"
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, Ap.Border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Text(label, color = Ap.TextSoft, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }

        when (shizuku) {
            ShizukuState.NOT_RUNNING -> ApCard {
                Text("Shizuku çalışmıyor", color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "Kalkan ve izleme özellikleri Shizuku ile çalışır. Shizuku uygulamasını aç, Geliştirici seçeneklerinden Kablosuz hata ayıklama ile başlat, sonra buraya dön. Telefon yeniden başlatılınca bu adım tekrarlanır.",
                    color = Ap.TextSoft, fontSize = 13.sp, lineHeight = 19.sp
                )
                PrimaryButton("Shizuku’yu Aç", Modifier.fillMaxWidth()) { openShizuku(context) }
            }
            ShizukuState.NO_PERMISSION -> ApCard {
                Text("Shizuku izni gerekli", color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Apex Phantom’un ayarları değiştirebilmesi için Shizuku’dan izin vermen gerekiyor.", color = Ap.TextSoft, fontSize = 13.sp, lineHeight = 19.sp)
                PrimaryButton("İzin Ver", Modifier.fillMaxWidth()) { ShizukuManager.requestPermission() }
            }
            ShizukuState.READY -> {}
        }

        if (settings.overlay && !overlayOk) {
            ApCard {
                Text("Oyun içi panel için izin", color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Oyun sırasında sol kenardaki paneli gösterebilmek için “Diğer uygulamaların üzerinde göster” izni gerekiyor.", color = Ap.TextSoft, fontSize = 13.sp, lineHeight = 19.sp)
                PrimaryButton("İzin Ver", Modifier.fillMaxWidth()) {
                    scope.launch {
                        val viaShizuku = ShizukuManager.exec("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
                        delay(500)
                        overlayOk = Settings.canDrawOverlays(context)
                        if (viaShizuku == null || !overlayOk) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }
        }

        ApCard(accent = true) {
            Text("OYUN KALKANI", color = Ap.RedSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            val title = when {
                status.activeGame != null -> "Etkin"
                !settings.autoShield -> "Kapalı"
                shizuku != ShizukuState.READY -> "Shizuku bekleniyor"
                else -> "Hazır"
            }
            Text(title, color = Ap.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                if (status.activeGame != null) "${Games.packages[status.activeGame]} için ayarlar uygulandı." +
                    (if (status.lastActions.isNotEmpty()) "\n" + status.lastActions.joinToString(" · ") else "")
                else "Mobile Legends veya Honor of Kings açıldığında otomatik devreye girer, oyun kapanınca her şeyi eski haline getirir.",
                color = Ap.TextSoft, fontSize = 14.sp, lineHeight = 20.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                PrimaryButton("Kalkan Ayarları", Modifier.weight(1f)) { nav.navigate("performance") }
                SecondaryButton("Canlı İzleme", Modifier.weight(1f)) { nav.navigate("monitor") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Cihaz ısısı", thermal, Modifier.weight(1f), if (Thermal.isHot(context)) Ap.Orange else Ap.Text)
            StatTile("Kalkan", if (status.activeGame != null) "Etkin" else "Bekliyor", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val linkText = when {
                link.type == "Wi-Fi" && link.bandGhz != null -> "Wi-Fi ${link.bandGhz} GHz"
                else -> link.type
            }
            StatTile("Bağlantı", linkText, Modifier.weight(1f), onClick = { nav.navigate("network") })
            StatTile("Saat sapması", ClockCache.lastOffsetMs?.let { "${kotlin.math.abs(it)} ms" } ?: "Ölçülmedi", Modifier.weight(1f), onClick = { nav.navigate("network") })
        }

        SectionLabel("OYUNLAR")
        GameEntry("ML", GameId.MLBB.title) { nav.navigate("game/mlbb") }
        GameEntry("HoK", GameId.HOK.title) { nav.navigate("game/hok") }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, valueColor: Color = Ap.Text, onClick: (() -> Unit)? = null) {
    ApCard(modifier = modifier, onClick = onClick) {
        Text(label, color = Ap.Muted, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GameEntry(short: String, title: String, onClick: () -> Unit) {
    ApCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ap.RedTint)
                    .border(1.dp, Ap.RedBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Text(short, color = Ap.RedSoft, fontWeight = FontWeight.Bold) }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("Karakterler, eşyalar, build planner", color = Ap.Muted, fontSize = 13.sp)
            }
            Text("›", color = Ap.Muted, fontSize = 24.sp)
        }
    }
}

private fun openShizuku(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

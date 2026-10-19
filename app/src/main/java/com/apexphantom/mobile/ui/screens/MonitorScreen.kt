package com.apexphantom.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexphantom.mobile.ApexApp
import com.apexphantom.mobile.data.FreezeMode
import com.apexphantom.mobile.monitor.AppUsage
import com.apexphantom.mobile.monitor.Verdict
import com.apexphantom.mobile.shizuku.ShizukuManager
import com.apexphantom.mobile.shizuku.ShizukuState
import com.apexphantom.mobile.ui.Ap
import com.apexphantom.mobile.ui.ApCard
import com.apexphantom.mobile.ui.Badge
import com.apexphantom.mobile.ui.PrimaryButton
import com.apexphantom.mobile.ui.ScreenHeader
import com.apexphantom.mobile.ui.Segmented
import com.apexphantom.mobile.ui.SmallButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun verdictColors(v: Verdict): Triple<String, Color, Color> = when (v) {
    Verdict.KESIN_KAPAT -> Triple("KESİN KAPAT", Ap.KesinBg, Ap.KesinText)
    Verdict.ONERILIR -> Triple("ÖNERİLİR", Ap.OrangeBg, Ap.Orange)
    Verdict.ISTISNA -> Triple("İSTİSNA", Ap.BlueBg, Ap.Blue)
    Verdict.DOKUNMA -> Triple("DOKUNMA", Ap.GrayBg, Ap.Gray)
}

@Composable
fun MonitorScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as ApexApp
    val shizuku by ShizukuManager.state.collectAsState()
    val settings by app.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var usage by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(shizuku) {
        if (shizuku != ShizukuState.READY) return@LaunchedEffect
        while (true) {
            usage = app.monitor.sample(app.settings.settings.value.exceptions)
            loaded = true
            delay(4000)
        }
    }

    val sorted = if (tab == 0) usage.sortedByDescending { it.cpuPercent } else usage.sortedByDescending { it.ramMb }
    val maxValue = if (tab == 0) (sorted.maxOfOrNull { it.cpuPercent } ?: 1f).coerceAtLeast(1f)
    else (sorted.maxOfOrNull { it.ramMb.toFloat() } ?: 1f).coerceAtLeast(1f)
    val top = sorted.firstOrNull { it.verdict == Verdict.KESIN_KAPAT || it.verdict == Verdict.ONERILIR }
    val pending = usage.filter { it.verdict == Verdict.KESIN_KAPAT && it.packageName !in stopped }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("İZLEME", "Canlı İzleme")
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            if (shizuku != ShizukuState.READY) {
                ApCard { Text("Canlı izleme için Shizuku gerekli. Ana Sayfa’dan Shizuku’yu başlatıp izin ver.", color = Ap.TextSoft, fontSize = 14.sp, lineHeight = 20.sp) }
                return@Column
            }
            if (!loaded) {
                Text("Ölçülüyor…", color = Ap.Muted, fontSize = 14.sp)
                return@Column
            }

            ApCard(accent = true) {
                Text("Telefonunu şu an en çok meşgul eden", color = Ap.TextSoft, fontSize = 13.sp)
                if (top != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(top.label, color = Ap.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            if (tab == 0) "İşlemcinin %${"%.0f".format(top.cpuPercent)}’i" else "${top.ramMb} MB",
                            color = Ap.KesinText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text("Arka planda yük yapan bir uygulama yok", color = Ap.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                PrimaryButton(
                    if (pending.isNotEmpty()) "Önerilenleri Uygula (${pending.size})" else "Önerilenler uygulandı",
                    Modifier.fillMaxWidth(),
                    enabled = pending.isNotEmpty()
                ) {
                    scope.launch {
                        pending.forEach { app.monitor.stop(it.packageName) }
                        stopped = stopped + pending.map { it.packageName }
                    }
                }
                Text("Kesin kapat olarak işaretlenen uygulamaları durdurur. Dokunma olarak işaretlenenler hiçbir zaman durdurulmaz.", color = Ap.Gray, fontSize = 12.sp, lineHeight = 17.sp)
            }

            Segmented(listOf("İşlemci", "RAM"), tab) { tab = it }

            sorted.forEach { u ->
                val isStopped = u.packageName in stopped
                val (badge, bg, fg) = verdictColors(u.verdict)
                ApCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(bg),
                            contentAlignment = Alignment.Center
                        ) { Text(u.label.take(1).uppercase(), color = fg, fontWeight = FontWeight.Bold) }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(u.label, color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(if (isStopped) "Durduruldu" else u.reason, color = Ap.Muted, fontSize = 12.sp)
                        }
                        Text(
                            if (isStopped) "—" else if (tab == 0) "%${"%.0f".format(u.cpuPercent)}" else "${u.ramMb} MB",
                            color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                        )
                    }
                    val value = if (tab == 0) u.cpuPercent else u.ramMb.toFloat()
                    val fraction = if (isStopped) 0f else (value / maxValue).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(Ap.Border)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(fg))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(badge, bg, fg)
                        Box(Modifier.weight(1f))
                        when {
                            isStopped -> Text("Durduruldu", color = Ap.Gray, fontSize = 13.sp)
                            u.verdict == Verdict.KESIN_KAPAT || u.verdict == Verdict.ONERILIR -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmallButton("Durdur", filled = true) {
                                    scope.launch {
                                        app.monitor.stop(u.packageName)
                                        stopped = stopped + u.packageName
                                    }
                                }
                                val inList = if (settings.freezeMode == FreezeMode.ALL) false else u.packageName in settings.selected
                                if (settings.freezeMode == FreezeMode.SELECTED && !inList) {
                                    SmallButton("Listeye ekle", filled = false) {
                                        app.settings.update { it.copy(selected = it.selected + u.packageName) }
                                    }
                                }
                            }
                            u.verdict == Verdict.ISTISNA -> SmallButton("İstisnadan çıkar", filled = false) {
                                app.settings.update { it.copy(exceptions = it.exceptions - u.packageName) }
                            }
                            else -> Text("Kilitli", color = Ap.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }

            Text(
                "Ölçümler 4 saniyede bir yenilenir; işlemci değeri sistemin son ölçüm aralığındaki ortalamasıdır. Oyun sırasında bu ekranı açık bırakma, ölçüm de az miktarda yük oluşturur.",
                color = Ap.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

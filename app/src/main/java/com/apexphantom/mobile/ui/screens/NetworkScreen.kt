package com.apexphantom.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.apexphantom.mobile.network.DnsChoice
import com.apexphantom.mobile.network.DnsManager
import com.apexphantom.mobile.network.NetworkTools
import com.apexphantom.mobile.network.PingResult
import com.apexphantom.mobile.network.TimeSync
import com.apexphantom.mobile.shizuku.ShizukuManager
import com.apexphantom.mobile.shizuku.ShizukuState
import com.apexphantom.mobile.ui.Ap
import com.apexphantom.mobile.ui.ApCard
import com.apexphantom.mobile.ui.NoticeBox
import com.apexphantom.mobile.ui.PrimaryButton
import com.apexphantom.mobile.ui.ScreenHeader
import com.apexphantom.mobile.ui.SecondaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val CLOCK_FIX_THRESHOLD_MS = 100L

@Composable
fun NetworkScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuManager.state.collectAsState()

    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PingResult?>(null) }
    var testFailed by remember { mutableStateOf(false) }
    val link = remember { NetworkTools.linkInfo(context) }

    var measuring by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(ClockCache.lastOffsetMs) }
    var beforeFix by remember { mutableStateOf<Long?>(null) }
    var clockError by remember { mutableStateOf(false) }

    var dns by remember { mutableStateOf(DnsChoice.AUTO) }
    LaunchedEffect(shizuku) { if (shizuku == ShizukuState.READY) dns = DnsManager.current() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "‹ Performans",
            color = Ap.TextSoft,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(start = 12.dp, top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { nav.popBackStack() }
                .padding(horizontal = 8.dp, vertical = 12.dp)
        )
        ScreenHeader("AĞ VE SAAT", "Bağlantı")
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            ApCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bağlantı Testi", color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    val linkText = buildString {
                        append(link.type)
                        link.bandGhz?.let { append(" · $it GHz") }
                        link.signal?.let { append(" · Sinyal $it") }
                    }
                    Text(linkText, color = Ap.Muted, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Ping", result?.let { "${it.avgMs.toInt()} ms" } ?: "—", Modifier.weight(1f))
                    Metric("Sıçrama", result?.let { "${it.jitterMs.toInt()} ms" } ?: "—", Modifier.weight(1f))
                    Metric("Paket kaybı", result?.let { "%" + "%.1f".format(it.lossPercent).replace('.', ',') } ?: "—", Modifier.weight(1f))
                }
                result?.let { r -> NetworkTools.advice(r, link).forEach { NoticeBox(it) } }
                if (testFailed) NoticeBox("Test çalıştırılamadı. İnternet bağlantını kontrol edip tekrar dene.")
                PrimaryButton(
                    when {
                        testing -> "Test ediliyor…"
                        result != null -> "Testi Tekrarla"
                        else -> "Testi Başlat"
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !testing
                ) {
                    scope.launch {
                        testing = true
                        testFailed = false
                        val r = NetworkTools.ping()
                        result = r
                        testFailed = r == null
                        testing = false
                    }
                }
                Text(
                    "Test, ${NetworkTools.PING_TARGET_LABEL} sunucusuna 20 ölçüm yapar. Oyunun ekranda gösterdiği ping değeriyle birebir aynı olmayabilir.",
                    color = Ap.Muted, fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            ApCard {
                Text("Saat Eşitleme", color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("İnternet saatine göre sapma", color = Ap.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    val o = offset
                    Text(
                        o?.let { "${abs(it)} ms" } ?: "—",
                        color = when {
                            o == null -> Ap.Text
                            abs(o) >= CLOCK_FIX_THRESHOLD_MS -> Ap.KesinText
                            else -> Ap.Green
                        },
                        fontSize = 22.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                val o = offset
                val note = when {
                    clockError -> "Ölçüm yapılamadı. İnternet bağlantını kontrol et."
                    o == null -> "Henüz ölçüm yapılmadı."
                    beforeFix != null -> "Önce ${abs(beforeFix!!)} ms sapma vardı, şimdi ${abs(o)} ms."
                    abs(o) >= CLOCK_FIX_THRESHOLD_MS -> "Saatin internet saatinden belirgin şekilde sapmış. Düzelt ile eşitleyebilirsin."
                    else -> "Saatin doğru."
                }
                Text(note, color = Ap.TextSoft, fontSize = 13.sp, lineHeight = 19.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(if (measuring) "Ölçülüyor…" else "Ölç", Modifier.weight(1f), enabled = !measuring) {
                        scope.launch {
                            measuring = true
                            clockError = false
                            beforeFix = null
                            val v = TimeSync.measureOffsetMs()
                            if (v == null) clockError = true else { offset = v; ClockCache.lastOffsetMs = v }
                            measuring = false
                        }
                    }
                    if (o != null && abs(o) >= CLOCK_FIX_THRESHOLD_MS && shizuku == ShizukuState.READY) {
                        PrimaryButton("Düzelt", Modifier.weight(1f), enabled = !measuring) {
                            scope.launch {
                                measuring = true
                                val before = o
                                TimeSync.forceSystemSync()
                                delay(3000)
                                val v = TimeSync.measureOffsetMs()
                                if (v == null) clockError = true else {
                                    beforeFix = before
                                    offset = v
                                    ClockCache.lastOffsetMs = v
                                }
                                measuring = false
                            }
                        }
                    }
                }
                Text(
                    "Ölçüm ${TimeSync.NTP_HOST} sunucusuyla, bağlantı gecikmesi hesaba katılarak yapılır. Düzelt, sistemin otomatik saat eşitlemesini yeniden çalıştırır ve sonucu tekrar ölçer. Bağlantı ve giriş hatalarını giderir.",
                    color = Ap.Muted, fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            ApCard {
                Text("Özel DNS", color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                DnsChoice.entries.forEach { choice ->
                    val on = dns == choice
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(shape)
                            .background(if (on) Ap.RedTint else Ap.SurfaceLow)
                            .border(1.dp, if (on) Ap.Red else Ap.Border, shape)
                            .clickable(enabled = shizuku == ShizukuState.READY) {
                                scope.launch { if (DnsManager.set(choice)) dns = choice }
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(if (on) 6.dp else 2.dp, if (on) Ap.Red else Ap.BorderStrong, CircleShape)
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(choice.label, color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(choice.description, color = Ap.Muted, fontSize = 12.sp)
                        }
                    }
                }
                if (shizuku != ShizukuState.READY) Text("DNS değiştirmek için Shizuku gerekli.", color = Ap.Orange, fontSize = 12.sp)
                Text(
                    "Alan adı çözümlemesini hızlandırabilir ve oyuna giriş sürecine yardımcı olabilir. Oyun içi ping değerini değiştirmez.",
                    color = Ap.Muted, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(Ap.SurfaceLow)
            .border(1.dp, Ap.Border, shape)
            .padding(12.dp)
    ) {
        Text(label, color = Ap.Muted, fontSize = 12.sp)
        Text(value, color = Ap.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}

package com.apexphantom.mobile.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.apexphantom.mobile.ApexApp
import com.apexphantom.mobile.data.FreezeMode
import com.apexphantom.mobile.shield.Games
import com.apexphantom.mobile.shield.ShieldService
import com.apexphantom.mobile.ui.Ap
import com.apexphantom.mobile.ui.ApCard
import com.apexphantom.mobile.ui.ScreenHeader
import com.apexphantom.mobile.ui.SectionLabel
import com.apexphantom.mobile.ui.Segmented
import com.apexphantom.mobile.ui.SmallButton
import com.apexphantom.mobile.ui.SwitchRow

data class InstalledApp(val packageName: String, val label: String)

fun installedUserApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    return pm.getInstalledApplications(0)
        .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        .filter { it.packageName != context.packageName && !Games.isGame(it.packageName) }
        .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun PerformanceScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as ApexApp
    val s by app.settings.settings.collectAsState()
    var picker by remember { mutableStateOf(false) }
    val labels = remember { installedUserApps(context).associate { it.packageName to it.label } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("PERFORMANS", "Oyun Kalkanı")
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            SwitchRow(
                "Otomatik Kalkan",
                "Seçili oyunlardan biri açılınca aşağıdaki açık ayarları uygular, oyun kapanınca hepsini geri alır.",
                s.autoShield
            ) { on ->
                app.settings.update { it.copy(autoShield = on) }
                if (on) ShieldService.start(context) else ShieldService.stop(context)
            }
            SwitchRow(
                "Oyun içi panel",
                "Oyun sırasında ekranın sol kenarında bir tutamak gösterir. Dokununca kalkan durumu, cihaz ısısı ve en çok yük yapan uygulamalar açılır.",
                s.overlay
            ) { on -> app.settings.update { it.copy(overlay = on) } }

            SectionLabel("KALKANIN DEVREYE GİRDİĞİ OYUNLAR")
            Text("Mobile Legends: Bang Bang · Honor of Kings", color = Ap.TextSoft, fontSize = 14.sp)

            SectionLabel("ARKA PLAN DONDURMA")
            Segmented(
                listOf("Hepsini dondur", "Sadece seçtiklerim"),
                if (s.freezeMode == FreezeMode.ALL) 0 else 1
            ) { i -> app.settings.update { it.copy(freezeMode = if (i == 0) FreezeMode.ALL else FreezeMode.SELECTED) } }
            Text(
                if (s.freezeMode == FreezeMode.ALL) "Yüklediğin uygulamaların hepsi durur. Sadece istisna listendekiler açık kalır."
                else "Sadece listene eklediğin uygulamalar durur. Diğerlerine dokunulmaz.",
                color = Ap.TextSoft, fontSize = 13.sp, lineHeight = 19.sp
            )
            val list = if (s.freezeMode == FreezeMode.ALL) s.exceptions else s.selected
            ApCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (s.freezeMode == FreezeMode.ALL) "İstisnalar (açık kalacaklar)" else "Durdurulacaklar",
                        color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f)
                    )
                    SmallButton("Uygulama ekle", filled = false) { picker = true }
                }
                if (list.isEmpty()) {
                    Text("Liste boş.", color = Ap.Muted, fontSize = 13.sp)
                }
                list.sorted().forEach { pkg ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(labels[pkg] ?: pkg, color = Ap.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        SmallButton("Kaldır", filled = false) {
                            app.settings.update {
                                if (it.freezeMode == FreezeMode.ALL) it.copy(exceptions = it.exceptions - pkg)
                                else it.copy(selected = it.selected - pkg)
                            }
                        }
                    }
                }
                Text(
                    "Sistem uygulamaları, Google Play Hizmetleri, klavye, ana ekran uygulaması ve Shizuku her iki modda da çalışmaya devam eder.",
                    color = Ap.Muted, fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            SectionLabel("SİSTEM")
            SwitchRow("Arka planı dondur", "Dondurma moduna göre uygulamaları durdurur ve arka planda çalışma izinlerini kaldırır. Oyun kapanınca izinler geri verilir.", s.freeze) { on -> app.settings.update { it.copy(freeze = on) } }
            SwitchRow("Pil tasarrufunu kapat", "Pil Tasarrufu açıksa oyun boyunca kapatır, böylece işlemci kısıtlaması kalkar. Oyun bitince eski durumuna döner.", s.battery) { on -> app.settings.update { it.copy(battery = on) } }
            SwitchRow("Oyun modu: Performans", "Android oyun modunu bu oyun için Performans olarak ayarlar. Android 12 ve üstünde çalışır; etkisi cihaz üreticisinin desteğine bağlıdır.", s.gameMode) { on -> app.settings.update { it.copy(gameMode = on) } }
            SwitchRow("Yenileme hızını sabitle", "Ekranı desteklediği en yüksek yenileme hızında tutar, oyun sırasında düşmesini engeller. Oyun bitince eski durumuna döner.", s.refresh) { on -> app.settings.update { it.copy(refresh = on) } }
            SwitchRow("Animasyonları kapat", "Pencere, geçiş ve animatör süre ölçeklerini 0 yapar. Oyun bitince önceki değerlere döner.", s.animations) { on -> app.settings.update { it.copy(animations = on) } }
            SwitchRow("Rahatsız Etmeyin", "Oyun boyunca Rahatsız Etmeyin modunu (öncelikli mod) açar; bildirimler ve bildirim sesleri susturulur. Oyun bitince kapanır.", s.dnd) { on -> app.settings.update { it.copy(dnd = on) } }

            SectionLabel("AĞ")
            SwitchRow("Arka plan mobil verisini kes", "Mobil veri kullanırken diğer uygulamaların arka planda internete çıkmasını engeller. Wi-Fi’de bu işi arka plan dondurma yapar.", s.mobileData) { on -> app.settings.update { it.copy(mobileData = on) } }
            SwitchRow("Konum taramalarını kapat", "Konum doğruluğu için yapılan arka plan Wi-Fi ve Bluetooth taramalarını kapatır. Oyun bitince eski durumuna döner.", s.locationScan) { on -> app.settings.update { it.copy(locationScan = on) } }
            ApCard(onClick = { nav.navigate("network") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bağlantı testi, saat eşitleme ve özel DNS", color = Ap.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("›", color = Ap.Muted, fontSize = 22.sp)
                }
            }
            Text(
                "Değişiklikler bir sonraki oyun açılışında uygulanır.",
                color = Ap.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }

    if (picker) {
        AppPickerDialog(
            title = if (s.freezeMode == FreezeMode.ALL) "İstisna ekle" else "Durdurulacak uygulama ekle",
            exclude = if (s.freezeMode == FreezeMode.ALL) s.exceptions else s.selected,
            onDismiss = { picker = false },
            onPick = { pkg ->
                app.settings.update {
                    if (it.freezeMode == FreezeMode.ALL) it.copy(exceptions = it.exceptions + pkg)
                    else it.copy(selected = it.selected + pkg)
                }
                picker = false
            }
        )
    }
}

@Composable
fun AppPickerDialog(title: String, exclude: Set<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val apps = remember { installedUserApps(context) }
    var query by remember { mutableStateOf("") }
    val filtered = apps.filter { it.packageName !in exclude && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ap.Surface,
        title = { Text(title, color = Ap.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Uygulama ara") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ap.Red,
                        unfocusedBorderColor = Ap.Border,
                        focusedTextColor = Ap.Text,
                        unfocusedTextColor = Ap.Text,
                        cursorColor = Ap.Red
                    )
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.packageName }) { a ->
                        Text(
                            a.label,
                            color = Ap.Text,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(a.packageName) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = Ap.TextSoft) } }
    )
}

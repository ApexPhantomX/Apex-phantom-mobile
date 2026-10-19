package com.apexphantom.mobile.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.apexphantom.mobile.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs

data class PingResult(
    val avgMs: Float,
    val jitterMs: Float,
    val lossPercent: Float,
    val samples: Int
)

data class LinkInfo(
    val type: String,
    val bandGhz: String?,
    val signal: String?
)

object NetworkTools {

    /** Bağlantı testinin hedefi. Oyun sunucularının adresi oyunlar tarafından yayınlanmadığı için genel bir sunucu kullanılır. */
    const val PING_TARGET = "8.8.8.8"
    const val PING_TARGET_LABEL = "Google DNS (8.8.8.8)"

    suspend fun ping(count: Int = 20): PingResult? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("ping", "-c", count.toString(), "-i", "0.2", "-W", "2", PING_TARGET)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val times = Regex("""time=([\d.]+)""").findAll(out).mapNotNull { it.groupValues[1].toFloatOrNull() }.toList()
            val loss = Regex("""([\d.]+)% packet loss""").find(out)?.groupValues?.get(1)?.toFloatOrNull()
                ?: if (times.isEmpty()) 100f else 0f
            if (times.isEmpty()) return@withContext PingResult(0f, 0f, loss, 0)
            val avg = times.average().toFloat()
            val jitter = if (times.size > 1) times.zipWithNext { a, b -> abs(b - a) }.average().toFloat() else 0f
            PingResult(avg, jitter, loss, times.size)
        } catch (e: Exception) {
            null
        }
    }

    fun linkInfo(context: Context): LinkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return LinkInfo("Bağlantı yok", null, null)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val info = wm.connectionInfo
                val freq = info.frequency
                val band = when {
                    freq in 2400..2500 -> "2.4"
                    freq in 4900..5900 -> "5"
                    freq in 5925..7125 -> "6"
                    else -> null
                }
                val level: Int
                val max: Int
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    level = wm.calculateSignalLevel(info.rssi)
                    max = wm.maxSignalLevel
                } else {
                    level = WifiManager.calculateSignalLevel(info.rssi, 5)
                    max = 4
                }
                val signal = when {
                    max <= 0 -> null
                    level * 4 >= max * 3 -> "güçlü"
                    level * 2 >= max -> "orta"
                    else -> "zayıf"
                }
                LinkInfo("Wi-Fi", band, signal)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> LinkInfo("Mobil veri", null, null)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> LinkInfo("Kablolu", null, null)
            else -> LinkInfo("Diğer", null, null)
        }
    }

    /** Test sonucuna ve bağlantı türüne göre öneriler. */
    fun advice(result: PingResult, link: LinkInfo): List<String> {
        val list = mutableListOf<String>()
        if (result.samples == 0) {
            list += "Sunucuya ulaşılamadı. İnternet bağlantını kontrol et."
            return list
        }
        if (result.lossPercent >= 1f) list += "Paket kaybı var. Modeme yaklaşmak ya da 5 GHz ağa geçmek genelde düzeltir."
        if (result.jitterMs >= 20f) list += "Ping dalgalanıyor. Aynı ağda büyük bir indirme ya da yayın varsa durdur; modemde QoS ayarını açmak bunu genelde düzeltir."
        if (link.type == "Wi-Fi" && link.bandGhz == "2.4") list += "2.4 GHz ağdasın. Modemin 5 GHz ağı varsa ona bağlan, parazit çok daha az olur."
        if (link.type == "Wi-Fi" && link.signal == "zayıf") list += "Wi-Fi sinyalin zayıf. Modeme yaklaş ya da aradaki duvar sayısını azalt."
        if (list.isEmpty()) list += "Bağlantın sağlıklı görünüyor."
        return list
    }
}

object TimeSync {

    const val NTP_HOST = "time.google.com"

    /** Telefon saati ile internet saati arasındaki fark (ms). Pozitif: telefon geride. */
    suspend fun measureOffsetMs(): Long? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 4000
                val address = InetAddress.getByName(NTP_HOST)
                val buffer = ByteArray(48)
                buffer[0] = 0x1B.toByte() // LI=0, VN=3, Mode=3 (istemci)
                val t0 = System.currentTimeMillis()
                writeTimestamp(buffer, 40, t0)
                socket.send(DatagramPacket(buffer, buffer.size, address, 123))
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val t3 = System.currentTimeMillis()
                val t1 = readTimestamp(buffer, 32)
                val t2 = readTimestamp(buffer, 40)
                ((t1 - t0) + (t2 - t3)) / 2
            }
        } catch (e: Exception) {
            null
        }
    }

    private const val NTP_EPOCH_OFFSET = 2208988800L

    private fun readTimestamp(b: ByteArray, offset: Int): Long {
        var seconds = 0L
        var fraction = 0L
        for (i in 0..3) seconds = (seconds shl 8) or (b[offset + i].toLong() and 0xFF)
        for (i in 4..7) fraction = (fraction shl 8) or (b[offset + i].toLong() and 0xFF)
        return (seconds - NTP_EPOCH_OFFSET) * 1000 + (fraction * 1000L) / 0x100000000L
    }

    private fun writeTimestamp(b: ByteArray, offset: Int, millis: Long) {
        val seconds = millis / 1000 + NTP_EPOCH_OFFSET
        val fraction = (millis % 1000) * 0x100000000L / 1000
        for (i in 0..3) b[offset + i] = (seconds shr (24 - 8 * i)).toByte()
        for (i in 0..3) b[offset + 4 + i] = (fraction shr (24 - 8 * i)).toByte()
    }

    /** Sistemin otomatik saat eşitlemesini yeniden çalıştırır (Shizuku gerekir). */
    suspend fun forceSystemSync(): Boolean {
        if (!ShizukuManager.isReady) return false
        ShizukuManager.exec("cmd network_time_update_service force_refresh")
        val auto = ShizukuManager.exec("settings get global auto_time")?.trim()
        if (auto == "1") {
            ShizukuManager.exec("settings put global auto_time 0; settings put global auto_time 1")
        } else {
            ShizukuManager.exec("settings put global auto_time 1")
        }
        return true
    }
}

enum class DnsChoice(val label: String, val host: String?, val description: String) {
    AUTO("Otomatik", null, "İnternet sağlayıcının DNS sunucusu"),
    CLOUDFLARE("Cloudflare", "one.one.one.one", "one.one.one.one"),
    GOOGLE("Google", "dns.google", "dns.google")
}

object DnsManager {

    suspend fun current(): DnsChoice {
        val mode = ShizukuManager.exec("settings get global private_dns_mode")?.trim()
        val host = ShizukuManager.exec("settings get global private_dns_specifier")?.trim()
        return if (mode == "hostname") DnsChoice.entries.firstOrNull { it.host == host } ?: DnsChoice.AUTO else DnsChoice.AUTO
    }

    suspend fun set(choice: DnsChoice): Boolean {
        if (!ShizukuManager.isReady) return false
        if (choice.host == null) {
            ShizukuManager.exec("settings put global private_dns_mode opportunistic")
        } else {
            ShizukuManager.exec("settings put global private_dns_specifier ${choice.host}; settings put global private_dns_mode hostname")
        }
        return true
    }
}

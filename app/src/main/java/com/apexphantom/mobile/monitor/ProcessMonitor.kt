package com.apexphantom.mobile.monitor

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.apexphantom.mobile.shield.Games
import com.apexphantom.mobile.shizuku.ShizukuManager

enum class Verdict { KESIN_KAPAT, ONERILIR, ISTISNA, DOKUNMA }

data class AppUsage(
    val packageName: String,
    val label: String,
    val cpuPercent: Float,
    val ramMb: Int,
    val isSystem: Boolean,
    val verdict: Verdict,
    val reason: String
)

/**
 * Sistemin kendi ölçümlerini (dumpsys cpuinfo / meminfo) Shizuku yetkisiyle okur.
 * İşlemci değeri, sistemin son ölçüm aralığındaki ortalamasıdır.
 */
class ProcessMonitor(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    suspend fun sample(exceptions: Set<String>): List<AppUsage> {
        val cpuOut = ShizukuManager.exec("dumpsys cpuinfo") ?: return emptyList()
        val memOut = ShizukuManager.exec("dumpsys meminfo") ?: ""

        val cpu = HashMap<String, Float>()
        val cpuRegex = Regex("""^\s*([\d.]+)%\s+\d+/([^:\s]+)""")
        for (line in cpuOut.lines()) {
            val m = cpuRegex.find(line) ?: continue
            val pkg = m.groupValues[2].substringBefore(':')
            val value = m.groupValues[1].toFloatOrNull() ?: continue
            cpu[pkg] = (cpu[pkg] ?: 0f) + value
        }

        val ram = HashMap<String, Int>()
        val memRegex = Regex("""^\s*([\d,]+)K:\s+(\S+)\s+\(pid""")
        var inSection = false
        for (line in memOut.lines()) {
            if (line.contains("by process:")) {
                // İlk "... by process:" bölümünü kullan (PSS ya da RSS)
                if (ram.isNotEmpty()) break
                inSection = true
                continue
            }
            if (!inSection) continue
            if (line.isBlank()) { if (ram.isNotEmpty()) break else continue }
            val m = memRegex.find(line) ?: continue
            val pkg = m.groupValues[2].substringBefore(':')
            val kb = m.groupValues[1].replace(",", "").toIntOrNull() ?: continue
            ram[pkg] = (ram[pkg] ?: 0) + kb / 1024
        }

        val own = context.packageName
        val result = mutableListOf<AppUsage>()
        for (pkg in (cpu.keys + ram.keys).toSet()) {
            val info = try { pm.getApplicationInfo(pkg, 0) } catch (e: PackageManager.NameNotFoundException) { null } ?: continue
            if (pkg == own || Games.isGame(pkg)) continue
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val c = cpu[pkg] ?: 0f
            val r = ram[pkg] ?: 0
            val verdict = when {
                isSystem || pkg == "moe.shizuku.privileged.api" -> Verdict.DOKUNMA
                pkg in exceptions -> Verdict.ISTISNA
                c >= 5f || r >= 300 -> Verdict.KESIN_KAPAT
                else -> Verdict.ONERILIR
            }
            val reason = when (verdict) {
                Verdict.DOKUNMA -> "Sistem bileşeni, durdurulamaz"
                Verdict.ISTISNA -> "İstisna listende, açık kalacak"
                Verdict.KESIN_KAPAT -> if (c >= 5f) "Arka planda yoğun işlemci kullanıyor" else "Bellekte çok yer tutuyor"
                Verdict.ONERILIR -> "Şu an az yük, her an hareketlenebilir"
            }
            result += AppUsage(pkg, pm.getApplicationLabel(info).toString(), c, r, isSystem, verdict, reason)
        }
        return result
    }

    suspend fun stop(pkg: String) {
        ShizukuManager.exec("am force-stop $pkg")
    }
}

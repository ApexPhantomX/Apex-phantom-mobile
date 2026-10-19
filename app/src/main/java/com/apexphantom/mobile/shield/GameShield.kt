package com.apexphantom.mobile.shield

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.apexphantom.mobile.data.FreezeMode
import com.apexphantom.mobile.data.ShieldSettings
import com.apexphantom.mobile.shizuku.ShizukuManager
import org.json.JSONArray
import org.json.JSONObject

/** Kalkanın tanıdığı oyunlar. */
object Games {
    val packages: Map<String, String> = linkedMapOf(
        "com.mobile.legends" to "Mobile Legends: Bang Bang",
        "com.levelinfinite.sgameGlobal" to "Honor of Kings",
        "com.tencent.tmgp.sgame" to "Honor of Kings (Çin)"
    )

    fun isGame(pkg: String?) = pkg != null && packages.containsKey(pkg)
}

/** Uygulamanın kendisi, Shizuku ve sistemin çalışması için gereken paketler hiçbir zaman durdurulmaz. */
private val ALWAYS_PROTECTED = setOf(
    "com.apexphantom.mobile",
    "moe.shizuku.privileged.api"
)

/**
 * Oyun Kalkanı: ayarları uygular, önceki değerleri saklar ve oyun bitince geri yükler.
 * Önceki değerler telefona kaydedilir; uygulama kapansa bile sonraki açılışta geri alınır.
 */
class GameShield(private val context: Context) {

    private val prefs = context.getSharedPreferences("shield_backup", Context.MODE_PRIVATE)

    val isActive: Boolean get() = backup()?.optBoolean("active", false) == true
    val activeGame: String? get() = backup()?.optString("game")?.takeIf { it.isNotEmpty() }

    private fun backup(): JSONObject? =
        prefs.getString("backup", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun saveBackup(json: JSONObject?) {
        prefs.edit().putString("backup", json?.toString()).apply()
    }

    private suspend fun sh(cmd: String): String = ShizukuManager.exec(cmd) ?: ""

    private suspend fun getSetting(ns: String, key: String): String = sh("settings get $ns $key").trim()

    private fun restoreSettingCmd(ns: String, key: String, prev: String): String =
        if (prev.isEmpty() || prev == "null") "settings delete $ns $key" else "settings put $ns $key $prev"

    /** Yüklü üçüncü taraf uygulamalar: paket adı -> uid */
    suspend fun installedUserApps(): Map<String, Int> {
        val out = sh("cmd package list packages -3 -U")
        val result = linkedMapOf<String, Int>()
        val regex = Regex("""package:(\S+)\s+uid:(\d+)""")
        out.lineSequence().forEach { line ->
            regex.find(line)?.let { m -> result[m.groupValues[1]] = m.groupValues[2].toInt() }
        }
        return result
    }

    private suspend fun keyboardPackage(): String? =
        getSetting("secure", "default_input_method").substringBefore('/').takeIf { it.isNotEmpty() && it != "null" }

    private suspend fun launcherPackage(): String? {
        val out = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        return out.lines().map { it.trim() }.lastOrNull { it.contains('/') }?.substringBefore('/')
    }

    private fun maxRefreshRate(): Float {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        return display.supportedModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
    }

    /** Kalkanı uygular. Uygulanan adımların okunabilir listesini döndürür. */
    suspend fun apply(gamePkg: String, s: ShieldSettings): List<String> {
        if (!ShizukuManager.isReady) return emptyList()
        if (isActive) return listOf("Kalkan zaten etkin")

        val done = mutableListOf<String>()
        val b = JSONObject().put("active", true).put("game", gamePkg)

        // Durdurulacak uygulamaları belirle
        val apps = installedUserApps()
        val protectedPkgs = HashSet<String>(ALWAYS_PROTECTED).apply {
            add(gamePkg)
            addAll(Games.packages.keys)
            addAll(s.exceptions)
            keyboardPackage()?.let { add(it) }
            launcherPackage()?.let { add(it) }
        }
        val targets: List<String> = when (s.freezeMode) {
            FreezeMode.ALL -> apps.keys.filter { it !in protectedPkgs }
            FreezeMode.SELECTED -> s.selected.filter { it in apps && it !in protectedPkgs }
        }

        // --- Önceki değerleri topla ---
        if (s.freeze && targets.isNotEmpty()) {
            val script = targets.joinToString("; ") { "echo \"@@$it\"; cmd appops get $it RUN_ANY_IN_BACKGROUND" }
            val out = sh(script)
            val modes = JSONObject()
            val modeRegex = Regex("""RUN_ANY_IN_BACKGROUND:\s*(\w+)""")
            var current = ""
            for (raw in out.lines()) {
                val line = raw.trim()
                if (line.startsWith("@@")) {
                    current = line.removePrefix("@@")
                    modes.put(current, "default")
                } else if (current.isNotEmpty()) {
                    val m = modeRegex.find(line)
                    if (m != null) modes.put(current, m.groupValues[1])
                }
            }
            b.put("appops", modes)
        }

        var netUids: List<Int> = emptyList()
        var netVerb = "restrict-background-blacklist"
        if (s.mobileData && targets.isNotEmpty()) {
            var listOut = sh("cmd netpolicy list restrict-background-blacklist")
            if (listOut.contains("nknown") || listOut.contains("rror")) {
                netVerb = "restrict-background-denylist"
                listOut = sh("cmd netpolicy list $netVerb")
            }
            val already = Regex("""\d+""").findAll(listOut.substringAfter(':', "")).map { it.value.toInt() }.toSet()
            netUids = targets.mapNotNull { apps[it] }.filter { it !in already }.distinct()
            b.put("netVerb", netVerb).put("netUids", JSONArray(netUids))
        }

        if (s.battery) b.put("lowPower", getSetting("global", "low_power"))
        if (s.refresh) {
            b.put("peak", getSetting("system", "peak_refresh_rate"))
            b.put("min", getSetting("system", "min_refresh_rate"))
        }
        val animKeys = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        if (s.animations) {
            val anim = JSONObject()
            animKeys.forEach { anim.put(it, getSetting("global", it)) }
            b.put("anim", anim)
        }
        if (s.dnd) b.put("zen", getSetting("global", "zen_mode"))
        val scanKeys = listOf("wifi_scan_always_enabled", "ble_scan_always_enabled")
        if (s.locationScan) {
            val scans = JSONObject()
            scanKeys.forEach { scans.put(it, getSetting("global", it)) }
            b.put("scans", scans)
        }
        val canGameMode = s.gameMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (canGameMode) b.put("gameModePkg", gamePkg)

        // Değişikliklerden önce yedeği kaydet
        saveBackup(b)

        // --- Uygula ---
        if (s.freeze && targets.isNotEmpty()) {
            val cmds = targets.joinToString("; ") { "cmd appops set $it RUN_ANY_IN_BACKGROUND ignore; am force-stop $it" }
            sh(cmds)
            done += "${targets.size} uygulama durduruldu"
        }
        if (netUids.isNotEmpty()) {
            sh(netUids.joinToString("; ") { "cmd netpolicy add $netVerb $it" })
            done += "Arka plan mobil verisi kesildi"
        }
        if (s.battery && b.optString("lowPower") == "1") {
            sh("settings put global low_power 0")
            done += "Pil tasarrufu kapatıldı"
        }
        if (canGameMode) {
            sh("cmd game mode performance $gamePkg")
            done += "Oyun modu: Performans"
        }
        if (s.refresh) {
            val rate = maxRefreshRate()
            sh("settings put system peak_refresh_rate $rate; settings put system min_refresh_rate $rate")
            done += "Yenileme hızı ${rate.toInt()} Hz’e sabitlendi"
        }
        if (s.animations) {
            sh(animKeys.joinToString("; ") { "settings put global $it 0" })
            done += "Animasyonlar kapatıldı"
        }
        if (s.dnd && b.optString("zen") == "0") {
            sh("cmd notification set_dnd priority")
            done += "Rahatsız Etmeyin açıldı"
        }
        if (s.locationScan) {
            sh(scanKeys.joinToString("; ") { "settings put global $it 0" })
            done += "Konum taramaları kapatıldı"
        }
        return done
    }

    /** Kaydedilen değerlere geri döner. */
    suspend fun restore(): Boolean {
        val b = backup() ?: return false
        if (!b.optBoolean("active", false)) return false
        if (!ShizukuManager.isReady) return false

        val cmds = mutableListOf<String>()

        b.optJSONObject("appops")?.let { modes ->
            modes.keys().forEach { pkg ->
                val mode = modes.optString(pkg, "default").ifEmpty { "default" }
                cmds += "cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode"
            }
        }
        b.optJSONArray("netUids")?.let { arr ->
            val verb = b.optString("netVerb", "restrict-background-blacklist")
            for (i in 0 until arr.length()) cmds += "cmd netpolicy remove $verb ${arr.getInt(i)}"
        }
        if (b.has("lowPower") && b.optString("lowPower") == "1") cmds += "settings put global low_power 1"
        b.optString("gameModePkg").takeIf { it.isNotEmpty() }?.let { cmds += "cmd game mode standard $it" }
        if (b.has("peak")) {
            cmds += restoreSettingCmd("system", "peak_refresh_rate", b.optString("peak"))
            cmds += restoreSettingCmd("system", "min_refresh_rate", b.optString("min"))
        }
        b.optJSONObject("anim")?.let { anim ->
            anim.keys().forEach { k -> cmds += restoreSettingCmd("global", k, anim.optString(k)) }
        }
        if (b.has("zen") && b.optString("zen") == "0") cmds += "cmd notification set_dnd off"
        b.optJSONObject("scans")?.let { scans ->
            scans.keys().forEach { k -> cmds += restoreSettingCmd("global", k, scans.optString(k)) }
        }

        if (cmds.isNotEmpty()) sh(cmds.joinToString("; "))
        saveBackup(null)
        return true
    }
}

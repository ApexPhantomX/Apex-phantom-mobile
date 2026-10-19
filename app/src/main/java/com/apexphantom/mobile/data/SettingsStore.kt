package com.apexphantom.mobile.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FreezeMode { ALL, SELECTED }

data class ShieldSettings(
    val autoShield: Boolean = true,
    val freezeMode: FreezeMode = FreezeMode.ALL,
    val exceptions: Set<String> = emptySet(),
    val selected: Set<String> = emptySet(),
    val freeze: Boolean = true,
    val battery: Boolean = true,
    val gameMode: Boolean = true,
    val refresh: Boolean = true,
    val animations: Boolean = false,
    val dnd: Boolean = true,
    val mobileData: Boolean = true,
    val locationScan: Boolean = false,
    val overlay: Boolean = true
)

/** Kullanıcı ayarlarını telefonda saklar. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shield_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ShieldSettings> = _settings.asStateFlow()

    private fun load(): ShieldSettings {
        val d = ShieldSettings()
        return ShieldSettings(
            autoShield = prefs.getBoolean("autoShield", d.autoShield),
            freezeMode = if (prefs.getString("freezeMode", "ALL") == "SELECTED") FreezeMode.SELECTED else FreezeMode.ALL,
            exceptions = prefs.getStringSet("exceptions", emptySet())?.toSet() ?: emptySet(),
            selected = prefs.getStringSet("selected", emptySet())?.toSet() ?: emptySet(),
            freeze = prefs.getBoolean("freeze", d.freeze),
            battery = prefs.getBoolean("battery", d.battery),
            gameMode = prefs.getBoolean("gameMode", d.gameMode),
            refresh = prefs.getBoolean("refresh", d.refresh),
            animations = prefs.getBoolean("animations", d.animations),
            dnd = prefs.getBoolean("dnd", d.dnd),
            mobileData = prefs.getBoolean("mobileData", d.mobileData),
            locationScan = prefs.getBoolean("locationScan", d.locationScan),
            overlay = prefs.getBoolean("overlay", d.overlay)
        )
    }

    fun update(transform: (ShieldSettings) -> ShieldSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putBoolean("autoShield", s.autoShield)
            .putString("freezeMode", s.freezeMode.name)
            .putStringSet("exceptions", HashSet(s.exceptions))
            .putStringSet("selected", HashSet(s.selected))
            .putBoolean("freeze", s.freeze)
            .putBoolean("battery", s.battery)
            .putBoolean("gameMode", s.gameMode)
            .putBoolean("refresh", s.refresh)
            .putBoolean("animations", s.animations)
            .putBoolean("dnd", s.dnd)
            .putBoolean("mobileData", s.mobileData)
            .putBoolean("locationScan", s.locationScan)
            .putBoolean("overlay", s.overlay)
            .apply()
        _settings.value = s
    }
}

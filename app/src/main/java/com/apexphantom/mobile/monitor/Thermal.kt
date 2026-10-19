package com.apexphantom.mobile.monitor

import android.content.Context
import android.os.PowerManager

object Thermal {
    /** Android'in bildirdiği ısı durumu, Türkçe olarak. */
    fun status(context: Context): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Hafif ısınma"
            PowerManager.THERMAL_STATUS_MODERATE -> "Isınıyor"
            PowerManager.THERMAL_STATUS_SEVERE -> "Sıcak"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Çok sıcak"
            PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> "Kritik"
            else -> "Bilinmiyor"
        }
    }

    fun isHot(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
    }
}

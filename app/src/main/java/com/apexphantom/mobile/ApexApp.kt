package com.apexphantom.mobile

import android.app.Application
import com.apexphantom.mobile.data.GameRepository
import com.apexphantom.mobile.data.SettingsStore
import com.apexphantom.mobile.monitor.ProcessMonitor
import com.apexphantom.mobile.shield.GameShield
import com.apexphantom.mobile.shizuku.ShizukuManager

class ApexApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var shield: GameShield
        private set
    lateinit var games: GameRepository
        private set
    lateinit var monitor: ProcessMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        shield = GameShield(this)
        games = GameRepository(this)
        monitor = ProcessMonitor(this)
        ShizukuManager.init()
    }
}

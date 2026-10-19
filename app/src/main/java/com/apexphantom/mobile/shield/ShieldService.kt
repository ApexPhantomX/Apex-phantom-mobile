package com.apexphantom.mobile.shield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.apexphantom.mobile.ApexApp
import com.apexphantom.mobile.MainActivity
import com.apexphantom.mobile.R
import com.apexphantom.mobile.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ShieldStatus(
    val running: Boolean = false,
    val activeGame: String? = null,
    val lastActions: List<String> = emptyList()
)

object ShieldStatusHolder {
    private val _status = MutableStateFlow(ShieldStatus())
    val status: StateFlow<ShieldStatus> = _status.asStateFlow()
    fun set(s: ShieldStatus) { _status.value = s }
    fun update(t: (ShieldStatus) -> ShieldStatus) { _status.value = t(_status.value) }
}

/**
 * Arka planda ön plandaki uygulamayı izler. Desteklenen bir oyun açılınca kalkanı uygular,
 * oyundan 20 saniye boyunca uzak kalınca ayarları geri alır.
 */
class ShieldService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var lastForeground: String? = null
    private var awaySince = 0L
    private val overlay by lazy { OverlayController(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification("Kalkan bekliyor: oyun açılınca devreye girer"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        ShieldStatusHolder.update { it.copy(running = true) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                (application as ApexApp).shield.restore()
                ShieldStatusHolder.set(ShieldStatus())
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (loop == null) loop = scope.launch { watch() }
        return START_STICKY
    }

    private suspend fun watch() {
        val app = application as ApexApp
        val shield = app.shield
        ShizukuManager.exec("appops set $packageName GET_USAGE_STATS allow")

        // Önceki oturumdan kalan bir yedek varsa ve oyun açık değilse geri al
        if (shield.isActive) {
            val fg = currentForeground()
            if (fg == null || fg != shield.activeGame) shield.restore()
        }

        while (scope.isActive) {
            val fg = currentForeground()
            val settings = app.settings.settings.value
            val now = System.currentTimeMillis()

            if (Games.isGame(fg) && settings.overlay) overlay.showHandle() else overlay.hideAll()

            if (Games.isGame(fg) && !shield.isActive && settings.autoShield) {
                val actions = shield.apply(fg!!, settings)
                awaySince = 0L
                ShieldStatusHolder.update { it.copy(activeGame = fg, lastActions = actions) }
                notifyText("Kalkan etkin: ${Games.packages[fg]}")
            } else if (shield.isActive) {
                if (fg != null && fg == shield.activeGame) {
                    awaySince = 0L
                } else {
                    if (awaySince == 0L) awaySince = now
                    if (now - awaySince >= AWAY_LIMIT_MS) {
                        shield.restore()
                        awaySince = 0L
                        ShieldStatusHolder.update { it.copy(activeGame = null) }
                        notifyText("Kalkan bekliyor: oyun açılınca devreye girer")
                    }
                }
            }
            delay(2000)
        }
    }

    private fun currentForeground(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 15_000, end)
        val e = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) latest = e.packageName
        }
        if (latest != null) lastForeground = latest
        return lastForeground
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Oyun Kalkanı", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Apex Phantom")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun notifyText(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        overlay.hideAll()
        scope.cancel()
        ShieldStatusHolder.update { it.copy(running = false) }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "shield"
        private const val NOTIFICATION_ID = 7
        private const val AWAY_LIMIT_MS = 20_000L
        const val ACTION_STOP = "com.apexphantom.mobile.STOP_SHIELD"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ShieldService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ShieldService::class.java).setAction(ACTION_STOP))
        }
    }
}

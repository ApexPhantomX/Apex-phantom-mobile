package com.apexphantom.mobile.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.apexphantom.mobile.BuildConfig
import com.apexphantom.mobile.IShellService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_RUNNING, NO_PERMISSION, READY }

/**
 * Shizuku bağlantısını yönetir ve komutları ShellService üzerinden çalıştırır.
 */
object ShizukuManager {

    private const val PERMISSION_REQUEST_CODE = 4101

    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    private var service: IShellService? = null
    private var serviceDeferred = CompletableDeferred<IShellService>()
    private var bound = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                val s = IShellService.Stub.asInterface(binder)
                service = s
                if (!serviceDeferred.isCompleted) serviceDeferred.complete(s)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            serviceDeferred = CompletableDeferred()
        }
    }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky { refreshState() }
        Shizuku.addBinderDeadListener {
            service = null
            bound = false
            serviceDeferred = CompletableDeferred()
            _state.value = ShizukuState.NOT_RUNNING
        }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) refreshState()
        }
        refreshState()
    }

    fun refreshState() {
        _state.value = try {
            when {
                !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
                Shizuku.isPreV11() -> ShizukuState.NOT_RUNNING
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
                else -> ShizukuState.NO_PERMISSION
            }
        } catch (e: Exception) {
            ShizukuState.NOT_RUNNING
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (_: Exception) {
        }
    }

    private fun bindIfNeeded() {
        if (!bound) {
            bound = true
            try {
                Shizuku.bindUserService(userServiceArgs, connection)
            } catch (e: Exception) {
                bound = false
            }
        }
    }

    /** Shizuku hazır değilse null döner. */
    suspend fun exec(command: String): String? = withContext(Dispatchers.IO) {
        refreshState()
        if (_state.value != ShizukuState.READY) return@withContext null
        val current = service ?: run {
            bindIfNeeded()
            withTimeoutOrNull(8000) { serviceDeferred.await() }
        } ?: return@withContext null
        try {
            current.exec(command)
        } catch (e: Exception) {
            null
        }
    }

    val isReady: Boolean get() = _state.value == ShizukuState.READY
}

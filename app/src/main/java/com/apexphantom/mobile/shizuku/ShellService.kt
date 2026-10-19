package com.apexphantom.mobile.shizuku

import com.apexphantom.mobile.IShellService
import kotlin.system.exitProcess

/**
 * Shizuku tarafından ayrı bir süreçte, ADB (shell) yetkisiyle başlatılır.
 * Uygulamanın kendisi bu yetkiye sahip değildir; komutlar burada çalışır.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}

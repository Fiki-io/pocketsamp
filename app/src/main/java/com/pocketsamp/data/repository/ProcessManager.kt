package com.pocketsamp.data.repository

import com.pocketsamp.data.model.ProcessState
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProcessManager(private val assetHelper: AssetManagerHelper) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var mariadbProcess: Process? = null
    private var lighttpdProcess: Process? = null
    private var sampProcess: Process? = null

    private val _logFlow = MutableSharedFlow<String>(replay = 50)
    val logFlow: SharedFlow<String> = _logFlow.asSharedFlow()

    fun emitLog(message: String) {
        scope.launch {
            _logFlow.emit(message)
        }
    }

    suspend fun startDatabase(): Result<Unit> {
        return runCatching {
            assetHelper.extractAssetsIfNeeded { emitLog(it) }
            assetHelper.generateConfigurations()
            assetHelper.makeExecutables()

            // Start MariaDB Daemon
            val mariadbdBin = File(assetHelper.nativeBinDir, "mariadbd")
            val pbMaria = ProcessBuilder(
                mariadbdBin.absolutePath,
                "--defaults-file=${assetHelper.myCnfFile.absolutePath}"
            )
            pbMaria.environment()["LD_LIBRARY_PATH"] = assetHelper.nativeLibDir.absolutePath
            pbMaria.environment()["TMPDIR"] = assetHelper.mariadbTmpDir.absolutePath
            pbMaria.redirectErrorStream(true)
            val mProc = pbMaria.start()
            mariadbProcess = mProc
            scope.launch {
                BufferedReader(InputStreamReader(mProc.inputStream)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        emitLog("[MySQL] $line")
                    }
                }
            }

            // Start Lighttpd
            val lighttpdBin = File(assetHelper.nativeBinDir, "lighttpd")
            val pbLighttpd = ProcessBuilder(
                lighttpdBin.absolutePath,
                "-D",
                "-f",
                assetHelper.lighttpdConfFile.absolutePath
            )
            pbLighttpd.environment()["LD_LIBRARY_PATH"] = assetHelper.nativeLibDir.absolutePath
            pbLighttpd.redirectErrorStream(true)
            val lProc = pbLighttpd.start()
            lighttpdProcess = lProc
            scope.launch {
                BufferedReader(InputStreamReader(lProc.inputStream)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        emitLog("[Lighttpd] $line")
                    }
                }
            }

            emitLog("[Database] MySQL (3306) & phpMyAdmin (8080) aktif.")
        }
    }

    fun stopDatabase() {
        try {
            lighttpdProcess?.destroy()
            lighttpdProcess = null
            mariadbProcess?.destroy()
            mariadbProcess = null
            emitLog("[Database] MySQL dan Web Server dimatikan.")
        } catch (e: Exception) {
            emitLog("[Database Error] Gagal mematikan database: ${e.message}")
        }
    }

    suspend fun startServer(serverDir: File): Result<Unit> {
        return runCatching {
            assetHelper.extractAssetsIfNeeded { emitLog(it) }
            val serverCfg = File(serverDir, "server.cfg")
            if (!serverCfg.exists()) {
                throw IllegalStateException("File server.cfg tidak ditemukan di ${serverDir.absolutePath}")
            }

            // Thermal protection: Ensure sleep 5 is configured to prevent 100% CPU lock
            ensureOptimalSleepConfig(serverCfg)

            val sampBin = File(serverDir, "samp03svr")
            if (!sampBin.exists()) {
                throw IllegalStateException("Binary samp03svr tidak ditemukan di ${serverDir.absolutePath}")
            }
            sampBin.setExecutable(true, false)

            val boxBin = assetHelper.getBoxBinary()
            if (!boxBin.exists()) {
                throw IllegalStateException("Binary translator ${boxBin.name} tidak ditemukan.")
            }
            boxBin.setExecutable(true, false)

            emitLog("[Server] Menjalankan ${boxBin.name} ./samp03svr...")
            val pb = ProcessBuilder(
                boxBin.absolutePath,
                "./samp03svr"
            )
            pb.directory(serverDir)
            val env = pb.environment()
            env["BOX86_LD_LIBRARY_PATH"] = "${assetHelper.sysrootDir.absolutePath}:./plugins:."
            env["BOX86_PATH"] = "${assetHelper.sysrootDir.absolutePath}:."
            env["BOX86_DYNAREC"] = "1"
            env["BOX86_NOBANNER"] = "1"
            env["BOX86_LOG"] = "0"
            env["LD_LIBRARY_PATH"] = assetHelper.nativeLibDir.absolutePath

            pb.redirectErrorStream(true)
            val proc = pb.start()
            sampProcess = proc

            scope.launch {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        emitLog(line)
                    }
                }
                emitLog("[Server] Proses server telah berhenti.")
            }
        }
    }

    fun stopServer() {
        try {
            sampProcess?.destroy()
            sampProcess = null
            emitLog("[Server] Server SA-MP dimatikan.")
        } catch (e: Exception) {
            emitLog("[Server Error] Gagal mematikan server: ${e.message}")
        }
    }

    private fun ensureOptimalSleepConfig(cfgFile: File) {
        try {
            val lines = cfgFile.readLines().toMutableList()
            var foundSleep = false
            var modified = false

            for (i in lines.indices) {
                val trimmed = lines[i].trim()
                if (trimmed.startsWith("sleep")) {
                    foundSleep = true
                    val parts = trimmed.split(Regex("\\s+"))
                    val sleepVal = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    if (sleepVal <= 0) {
                        lines[i] = "sleep 5"
                        modified = true
                    }
                    break
                }
            }

            if (!foundSleep) {
                lines.add("sleep 5")
                modified = true
            }

            if (modified) {
                cfgFile.writeText(lines.joinToString("\n") + "\n")
                emitLog("[Config] Parameter 'sleep 5' diterapkan pada server.cfg untuk menjaga suhu HP tetap dingin.")
            }
        } catch (e: Exception) {
            emitLog("[Config Warning] Tidak dapat memperbarui server.cfg: ${e.message}")
        }
    }

    fun isDatabaseRunning(): Boolean = mariadbProcess?.isAlive == true
    fun isServerRunning(): Boolean = sampProcess?.isAlive == true
}

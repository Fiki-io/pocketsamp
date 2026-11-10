package com.pocketsamp.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketsamp.data.model.HardwareStats
import com.pocketsamp.data.model.ProcessState
import com.pocketsamp.data.model.RunnerUiState
import com.pocketsamp.data.repository.ProcessManager
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RunnerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RunnerUiState())
    val uiState: StateFlow<RunnerUiState> = _uiState.asStateFlow()

    private var processManager: ProcessManager? = null

    fun bindProcessManager(pm: ProcessManager) {
        if (processManager != pm) {
            processManager = pm
            viewModelScope.launch {
                pm.logFlow.collect { logLine ->
                    _uiState.update { current ->
                        val updated = (current.logs + logLine).takeLast(200)
                        current.copy(logs = updated)
                    }
                }
            }
        }
    }

    fun startHardwareMonitoring(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val appContext = context.applicationContext
            while (isActive) {
                val stats = readHardwareStats(appContext)
                _uiState.update { it.copy(hardwareStats = stats) }
                delay(3000)
            }
        }
    }

    private fun readHardwareStats(context: Context): HardwareStats {
        // Battery Temperature
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = rawTemp / 10.0f

        // RAM Usage
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availMb

        // IP Address
        var detectedIp = "127.0.0.1"
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp && !iface.isLoopback) {
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            detectedIp = addr.hostAddress ?: "127.0.0.1"
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        return HardwareStats(
            batteryTemp = tempCelsius,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            ipAddress = detectedIp
        )
    }

    fun setServerDirectory(path: String) {
        _uiState.update { it.copy(serverDirectory = path, errorMessage = null) }
    }

    fun toggleDatabase() {
        val pm = processManager ?: return
        if (pm.isDatabaseRunning()) {
            pm.stopDatabase()
            _uiState.update { it.copy(databaseState = ProcessState.STOPPED) }
        } else {
            viewModelScope.launch {
                _uiState.update { it.copy(databaseState = ProcessState.STARTING) }
                val result = pm.startDatabase()
                if (result.isSuccess) {
                    _uiState.update { it.copy(databaseState = ProcessState.RUNNING) }
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Gagal memulai database"
                    _uiState.update { it.copy(databaseState = ProcessState.ERROR, errorMessage = err) }
                }
            }
        }
    }

    fun toggleServer() {
        val pm = processManager ?: return
        val currentDir = _uiState.value.serverDirectory
        if (pm.isServerRunning()) {
            pm.stopServer()
            _uiState.update { it.copy(serverState = ProcessState.STOPPED) }
        } else {
            if (currentDir.isNullOrBlank()) {
                _uiState.update { it.copy(errorMessage = "Silakan pilih folder server SA-MP terlebih dahulu.") }
                return
            }
            val dir = File(currentDir)
            if (!dir.exists() || !dir.isDirectory) {
                _uiState.update { it.copy(errorMessage = "Direktori server tidak valid atau tidak ditemukan.") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(serverState = ProcessState.STARTING) }
                val result = pm.startServer(dir)
                if (result.isSuccess) {
                    _uiState.update { it.copy(serverState = ProcessState.RUNNING) }
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Gagal memulai server"
                    _uiState.update { it.copy(serverState = ProcessState.ERROR, errorMessage = err) }
                }
            }
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }
}

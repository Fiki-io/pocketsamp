package com.pocketsamp.data.model

enum class ProcessState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class HardwareStats(
    val batteryTemp: Float = 0.0f,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val ipAddress: String = "127.0.0.1"
)

data class RunnerUiState(
    val serverState: ProcessState = ProcessState.STOPPED,
    val databaseState: ProcessState = ProcessState.STOPPED,
    val serverDirectory: String? = null,
    val hardwareStats: HardwareStats = HardwareStats(),
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null
)

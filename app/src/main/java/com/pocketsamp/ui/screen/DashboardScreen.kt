package com.pocketsamp.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketsamp.R
import com.pocketsamp.data.model.ProcessState
import com.pocketsamp.data.model.RunnerUiState
import com.pocketsamp.ui.theme.DarkBackground
import com.pocketsamp.ui.theme.DarkSurface
import com.pocketsamp.ui.theme.DarkSurfaceVariant
import com.pocketsamp.ui.theme.PrimaryBlue
import com.pocketsamp.ui.theme.StatusGreen
import com.pocketsamp.ui.theme.StatusOrange
import com.pocketsamp.ui.theme.StatusRed
import com.pocketsamp.ui.theme.TerminalBackground
import com.pocketsamp.ui.theme.TerminalGreen
import com.pocketsamp.ui.theme.TextPrimary
import com.pocketsamp.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: RunnerUiState,
    onToggleDatabase: () -> Unit,
    onToggleServer: () -> Unit,
    onSelectFolder: () -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_dashboard),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner if any
            if (uiState.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            color = Color.White,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 1. Hardware & Network Info Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.card_system_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.label_ip_address),
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = uiState.hardwareStats.ipAddress,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.label_battery_temp),
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                val temp = uiState.hardwareStats.batteryTemp
                                val tempColor = if (temp < 38f) StatusGreen else if (temp < 43f) StatusOrange else StatusRed
                                Text(
                                    text = "${temp} °C",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tempColor
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.label_ram_usage),
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${uiState.hardwareStats.ramUsedMb} / ${uiState.hardwareStats.ramTotalMb} MB",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 2. Database & Web Stack Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.card_database_title),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = PrimaryBlue
                            )
                            StatusIndicator(state = uiState.databaseState)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.desc_database),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onToggleDatabase,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.databaseState == ProcessState.RUNNING) StatusRed else PrimaryBlue
                                )
                            ) {
                                Text(
                                    text = if (uiState.databaseState == ProcessState.RUNNING)
                                        stringResource(R.string.btn_stop_db)
                                    else
                                        stringResource(R.string.btn_start_db)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8080/phpmyadmin"))
                                    context.startActivity(intent)
                                },
                                enabled = uiState.databaseState == ProcessState.RUNNING,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = stringResource(R.string.btn_open_pma))
                            }
                        }
                    }
                }
            }

            // 3. SA-MP Server Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.card_server_title),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = PrimaryBlue
                            )
                            StatusIndicator(state = uiState.serverState)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.desc_server),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.label_server_folder),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.serverDirectory ?: stringResource(R.string.folder_not_selected),
                                color = if (uiState.serverDirectory != null) TextPrimary else TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSelectFolder,
                                enabled = uiState.serverState == ProcessState.STOPPED,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = stringResource(R.string.btn_select_folder))
                            }
                            Button(
                                onClick = onToggleServer,
                                enabled = uiState.serverDirectory != null,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.serverState == ProcessState.RUNNING) StatusRed else StatusGreen
                                )
                            ) {
                                Text(
                                    text = if (uiState.serverState == ProcessState.RUNNING)
                                        stringResource(R.string.btn_stop_server)
                                    else
                                        stringResource(R.string.btn_start_server)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Real-time Console Log Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.card_log_title),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = PrimaryBlue
                            )
                            OutlinedButton(onClick = onClearLogs) {
                                Text(text = stringResource(R.string.btn_clear_log), fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(TerminalBackground, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            if (uiState.logs.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.log_empty),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(uiState.logs) { log ->
                                        Text(
                                            text = log,
                                            color = TerminalGreen,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(state: ProcessState) {
    val (color, textRes) = when (state) {
        ProcessState.STOPPED -> StatusRed to R.string.status_stopped
        ProcessState.STARTING -> StatusOrange to R.string.status_starting
        ProcessState.RUNNING -> StatusGreen to R.string.status_running
        ProcessState.ERROR -> StatusRed to R.string.status_error
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(textRes),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

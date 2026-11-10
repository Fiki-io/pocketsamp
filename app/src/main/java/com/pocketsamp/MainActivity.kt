package com.pocketsamp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.pocketsamp.service.RunnerService
import com.pocketsamp.ui.screen.DashboardScreen
import com.pocketsamp.ui.theme.PocketSAMPTheme
import com.pocketsamp.ui.viewmodel.RunnerViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: RunnerViewModel by viewModels()
    private var runnerService: RunnerService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RunnerService.LocalBinder
            val srv = binder.getService()
            runnerService = srv
            isBound = true
            viewModel.bindProcessManager(srv.processManager)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            runnerService = null
            isBound = false
        }
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        }

    private val requestLegacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        }

    private val openDocumentTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                try {
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                val path = resolveRealPath(it)
                if (path != null) {
                    viewModel.setServerDirectory(path)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkNotificationPermission()
        checkAndRequestStoragePermission()
        startAndBindService()
        viewModel.startHardwareMonitoring(this)

        setContent {
            PocketSAMPTheme {
                val uiState by viewModel.uiState.collectAsState()
                DashboardScreen(
                    uiState = uiState,
                    onToggleDatabase = {
                        startServiceIntent()
                        viewModel.toggleDatabase()
                    },
                    onToggleServer = {
                        if (checkAndRequestStoragePermission()) {
                            startServiceIntent()
                            viewModel.toggleServer()
                        }
                    },
                    onSelectFolder = {
                        if (checkAndRequestStoragePermission()) {
                            openDocumentTreeLauncher.launch(null)
                        }
                    },
                    onClearLogs = {
                        viewModel.clearLogs()
                    }
                )
            }
        }
    }

    private fun checkAndRequestStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
                return false
            }
        } else {
            val readPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val writePerm = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (readPerm != PackageManager.PERMISSION_GRANTED || writePerm != PackageManager.PERMISSION_GRANTED) {
                requestLegacyStoragePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
                return false
            }
        }
        return true
    }

    private fun startServiceIntent() {
        val intent = Intent(this, RunnerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, RunnerService::class.java)
        startServiceIntent()
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun resolveRealPath(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split = docId.split(":")
        if (split.size >= 2) {
            val type = split[0]
            val relativePath = split[1]
            if ("primary".equals(type, ignoreCase = true)) {
                return File(Environment.getExternalStorageDirectory(), relativePath).absolutePath
            } else {
                return File("/storage/$type", relativePath).absolutePath
            }
        }
        return treeUri.path
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}

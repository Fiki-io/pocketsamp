package com.pocketsamp.data.repository

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

class AssetManagerHelper(private val context: Context) {

    val filesDir: File = context.filesDir

    val nativeDir = File(filesDir, "native")
    val nativeBinDir = File(nativeDir, "bin")
    val nativeLibDir = File(nativeDir, "lib")
    val nativeShareDir = File(nativeDir, "share")

    val sysrootDir = File(filesDir, "sysroot_i386")
    val boxRunnerDir = File(filesDir, "box_runner")

    val mariadbDir = File(filesDir, "mariadb")
    val mariadbDataDir = File(mariadbDir, "data")
    val mariadbSocketDir = File(mariadbDir, "socket")
    val mariadbTmpDir = File(mariadbDir, "tmp")
    val myCnfFile = File(mariadbDir, "my.cnf")

    val webDir = File(filesDir, "web")
    val webWwwDir = File(webDir, "www")
    val webSocketDir = File(webDir, "socket")
    val webTmpDir = File(webDir, "tmp")
    val webLogDir = File(webDir, "log")
    val lighttpdConfFile = File(webDir, "lighttpd.conf")

    val is64Bit: Boolean
        get() = Build.SUPPORTED_ABIS.isNotEmpty() && Build.SUPPORTED_ABIS[0].contains("64")

    fun getBoxBinary(): File {
        return if (is64Bit) {
            File(boxRunnerDir, "box64")
        } else {
            File(boxRunnerDir, "box86")
        }
    }

    fun ensureDirectories() {
        nativeBinDir.mkdirs()
        nativeLibDir.mkdirs()
        nativeShareDir.mkdirs()
        sysrootDir.mkdirs()
        boxRunnerDir.mkdirs()
        mariadbDataDir.mkdirs()
        mariadbSocketDir.mkdirs()
        mariadbTmpDir.mkdirs()
        webWwwDir.mkdirs()
        webSocketDir.mkdirs()
        webTmpDir.mkdirs()
        webLogDir.mkdirs()
    }

    fun generateConfigurations() {
        ensureDirectories()

        // Generate my.cnf with absolute paths
        val myCnfContent = """
            [client]
            port = 3306
            socket = ${mariadbSocketDir.absolutePath}/mysql.sock
            default-character-set = utf8mb4

            [mysqld]
            user = root
            port = 3306
            bind-address = 127.0.0.1
            socket = ${mariadbSocketDir.absolutePath}/mysql.sock
            datadir = ${mariadbDataDir.absolutePath}
            tmpdir = ${mariadbTmpDir.absolutePath}

            performance_schema = 0
            innodb_buffer_pool_size = 16M
            innodb_log_buffer_size = 1M
            innodb_flush_log_at_trx_commit = 2
            innodb_file_per_table = 1
            innodb_stats_on_metadata = 0
            innodb_read_io_threads = 2
            innodb_write_io_threads = 2
            key_buffer_size = 4M
            max_connections = 10
            query_cache_type = 0
            query_cache_size = 0
            table_open_cache = 64
            thread_cache_size = 2
            skip-name-resolve
            skip-external-locking
        """.trimIndent()
        myCnfFile.writeText(myCnfContent)

        // Generate lighttpd.conf with absolute paths
        val phpCgiBin = File(nativeBinDir, "php-cgi").absolutePath
        val lighttpdContent = """
            server.port = 8080
            server.bind = "127.0.0.1"
            server.document-root = "${webWwwDir.absolutePath}"
            server.upload-dirs = ( "${webTmpDir.absolutePath}" )
            server.errorlog = "${webLogDir.absolutePath}/lighttpd.log"

            index-file.names = ( "index.php", "index.html" )
            mimetype.assign = (
              ".html" => "text/html",
              ".txt" => "text/plain",
              ".css" => "text/css",
              ".js" => "application/x-javascript",
              ".png" => "image/png",
              ".jpg" => "image/jpeg",
              ".svg" => "image/svg+xml"
            )

            server.modules += ( "mod_fastcgi" )

            fastcgi.server = ( ".php" =>
              ((
                "bin-path" => "$phpCgiBin",
                "socket" => "${webSocketDir.absolutePath}/php.socket",
                "max-procs" => 1,
                "bin-environment" => (
                  "PHP_FCGI_CHILDREN" => "1",
                  "PHP_FCGI_MAX_REQUESTS" => "500"
                ),
                "broken-scriptfilename" => "enable"
              ))
            )
        """.trimIndent()
        lighttpdConfFile.writeText(lighttpdContent)
    }

    fun isDatabaseInitialized(): Boolean {
        val mysqlSystemDb = File(mariadbDataDir, "mysql")
        return mysqlSystemDb.exists() && mysqlSystemDb.isDirectory
    }

    fun makeExecutables() {
        nativeBinDir.listFiles()?.forEach { it.setExecutable(true, false) }
        boxRunnerDir.listFiles()?.forEach { it.setExecutable(true, false) }
    }

    fun extractAssetsIfNeeded(onProgress: (String) -> Unit = {}) {
        val marker = File(filesDir, ".installed_v1")
        if (marker.exists()) {
            return
        }

        ensureDirectories()
        val assetManager = context.assets

        // 1. Extract Box Runner
        try {
            onProgress("Mengekstrak Box Runner translator...")
            assetManager.open("box_runner.tar.gz").use { input ->
                extractTarGz(input, filesDir)
            }
        } catch (_: Exception) {}

        // 2. Extract Sysroot i386
        try {
            onProgress("Mengekstrak runtime sysroot i386...")
            assetManager.open("sysroot_i386.tar.gz").use { input ->
                extractTarGz(input, filesDir)
            }
        } catch (_: Exception) {}

        // 3. Extract Web Stack (Lighttpd & phpMyAdmin)
        try {
            onProgress("Mengekstrak web stack phpMyAdmin...")
            assetManager.open("web_stack.tar.gz").use { input ->
                extractTarGz(input, filesDir)
            }
        } catch (_: Exception) {}

        // 4. Extract Pre-initialized MariaDB System Data
        try {
            val mysqlDb = File(mariadbDataDir, "mysql")
            if (!mysqlDb.exists()) {
                onProgress("Mengekstrak database sistem MariaDB...")
                assetManager.open("mariadb_data.tar.gz").use { input ->
                    extractTarGz(input, mariadbDir)
                }
            }
        } catch (_: Exception) {}

        // 5. Extract Native Stack according to ABI
        val nativeArchive = if (is64Bit) "native_arm64.tar.gz" else "native_armhf.tar.gz"
        val archSubdir = if (is64Bit) "arm64" else "armhf"
        try {
            onProgress("Mengekstrak native binary ($archSubdir)...")
            assetManager.open(nativeArchive).use { input ->
                extractTarGz(input, filesDir)
            }

            // Move native_stack/{arch} contents to native/
            val extractedNative = File(filesDir, "native_stack/$archSubdir")
            if (extractedNative.exists()) {
                val binSrc = File(extractedNative, "bin")
                if (binSrc.exists()) {
                    binSrc.listFiles()?.forEach { it.copyTo(File(nativeBinDir, it.name), overwrite = true) }
                }
                val libSrc = File(extractedNative, "lib")
                if (libSrc.exists()) {
                    libSrc.listFiles()?.forEach { it.copyTo(File(nativeLibDir, it.name), overwrite = true) }
                }
                val shareSrc = File(extractedNative, "share")
                if (shareSrc.exists()) {
                    shareSrc.copyRecursively(nativeShareDir, overwrite = true)
                }
                File(filesDir, "native_stack").deleteRecursively()
            }
        } catch (_: Exception) {}

        generateConfigurations()
        makeExecutables()
        marker.writeText("installed")
        onProgress("Ekstraksi aset selesai.")
    }

    fun extractTarGz(inputStream: InputStream, destDir: File) {
        destDir.mkdirs()
        GZIPInputStream(inputStream).use { gzipIn ->
            TarArchiveInputStream(gzipIn).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val outputFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else if (entry.isSymbolicLink) {
                        outputFile.parentFile?.mkdirs()
                        try {
                            if (outputFile.exists()) outputFile.delete()
                            android.system.Os.symlink(entry.linkName, outputFile.absolutePath)
                        } catch (_: Exception) {
                        }
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { out ->
                            tarIn.copyTo(out)
                        }
                        if (entry.mode and 0b001000000 != 0) {
                            outputFile.setExecutable(true, false)
                        }
                    }
                    entry = tarIn.nextEntry
                }
            }
        }
    }
}

# PocketSAMP

**PocketSAMP** adalah aplikasi Android native yang memungkinkan kamu menjalankan server SA-MP (San Andreas Multiplayer) Linux x86 beserta stack database MySQL (MariaDB) dan antarmuka web phpMyAdmin langsung secara lokal di HP Android tanpa perlu akses root.

---

## Fitur Utama

- **Universal SA-MP Server Runner**: Menjalankan binary server Linux standard (`samp03svr`) dan seluruh plugin `.so` 32-bit (seperti `mysql.so`, `streamer.so`, `sscanf.so`, `colandreas.so`) tanpa perlu compile ulang gamemode.
- **Tanpa PRoot (Zero Syscall Overhead)**: Menggunakan binary translator Box86/Box64 standalone dengan built-in ELF loader dan mapping library userspace (`BOX86_LD_LIBRARY_PATH`), menghindari overhead ptrace yang biasa membuat HP panas dan lag.
- **Embedded Local MySQL & phpMyAdmin (Pengganti XAMPP)**:
  - MariaDB Server native Bionic (listen di `127.0.0.1:3306`).
  - Web Server Lighttpd ultra-ringan + PHP-CGI (listen di `127.0.0.1:8080`).
  - phpMyAdmin 5.2 pre-configured: Buka dari browser HP ke `http://127.0.0.1:8080/phpmyadmin` untuk membuat database dan import file `.sql`.
- **Thermal & Memory Optimization**:
  - Otomatis memastikan parameter `sleep 5` aktif di `server.cfg` agar core CPU tidak ter-lock di 100%.
  - Buffer pool MariaDB di-tune ke 16 MB.
- **Multi-Arsitektur (ARM64 & ARM32)**: Mendukung perangkat modern 64-bit (`arm64-v8a`) dan perangkat lama 32-bit (`armeabi-v7a`).
- **Foreground Background Execution**: Dilengkapi Android `ForegroundService` dengan persistent notification, `WakeLock`, dan `WifiLock` agar server tetap aktif saat layar HP mati atau aplikasi diminimize.
- **Modern Jetpack Compose UI**: Antarmuka bertema gelap fungsional dengan monitor suhu baterai realtime, pemantauan konsumsi RAM, alamat IP jaringan, dan live terminal console.

---

## Cara Penggunaan

1. Download dan instal APK PocketSAMP dari halaman Releases atau hasil build GitHub Actions.
2. Buka aplikasi PocketSAMP. Pada pembukaan pertama, aplikasi akan menyiapkan runtime native secara otomatis.
3. Klik tombol **Aktifkan MySQL**, lalu klik **Buka phpMyAdmin** untuk mengimpor file `.sql` database gamemode kamu di browser.
4. Klik **Pilih Folder Server** dan arahkan ke folder server SA-MP kamu (folder yang berisi `samp03svr`, `server.cfg`, `gamemodes/`, `plugins/`, dll).
5. Klik **Jalankan Server**. Output log server akan muncul langsung di layar secara realtime.
6. Pemain lain di jaringan WiFi atau Hotspot yang sama dapat bergabung menggunakan alamat IP HP yang tertera di kartu informasi dan port `7777`.

---

## Lisensi
Proyek ini didistribusikan di bawah lisensi MIT.

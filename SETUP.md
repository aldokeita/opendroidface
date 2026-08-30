# Setup

Folder ini: `C:\Users\ALDO\Documents\opendroidface`

## 1. Prasyarat

| Yang dibutuhkan | Versi | Cek |
|---|---|---|
| JDK | 17 (Temurin/Adoptium) | `java -version` |
| Android SDK | API 34+ | Android Studio → SDK Manager |
| Git | apa saja | `git --version` |
| adb | dari platform-tools | `adb version` |

Kalau belum ada Android Studio, minimal pasang **command line tools** + platform-tools,
lalu set `ANDROID_HOME` ke folder SDK.

## 2. Ambil kode upstream

Jalankan di folder ini (PowerShell). Folder sudah berisi `CLAUDE.md`, `PLAN.md`,
`SETUP.md`, `starter/`, dan `.claude/` — clone perlu dilakukan lewat repo sementara
supaya file-file itu tidak tertimpa.

```powershell
git clone https://github.com/yashab-cyber/opendroid.git .upstream-tmp
robocopy .upstream-tmp . /E /XC /XN /XO
Remove-Item -Recurse -Force .upstream-tmp
```

Lalu inisialisasi git di folder ini:

```powershell
git init
git remote add upstream https://github.com/yashab-cyber/opendroid.git
git fetch upstream
# ganti URL berikut dengan fork Anda sendiri di GitHub
git remote add origin https://github.com/<username>/opendroidface.git
git checkout -b feature/robot-face
```

> Alternatif lebih rapi: fork dulu `yashab-cyber/opendroid` di GitHub lewat tombol Fork,
> rename jadi `opendroidface`, lalu `git clone` fork itu ke sini dan salin balik file-file
> handoff ini. Pilih cara ini kalau ingin riwayat commit upstream tetap utuh.

## 3. Konfigurasi

**Jangan** menyalin `gradle.properties.example` menjadi `gradle.properties` — upstream
sekarang melacak `gradle.properties` di Git dan build butuh isinya apa adanya.
Kredensial signing (dan hal rahasia lain) masuk ke `~/.gradle/gradle.properties`
atau environment variable; `gradle.properties.example` menjelaskan keduanya.

Lokasi Android SDK diberi tahu lewat `local.properties` (sudah di `.gitignore`):

```
sdk.dir=C\:\\Users\\ALDO\\Android\\Sdk
```

API key provider cloud diisi dari dalam aplikasi (layar Settings), bukan dari Gradle.

Untuk pengembangan wajah, provider paling praktis:
- **Ollama** di PC Anda — set base URL ke `http://10.0.2.2:11434` (emulator) atau IP LAN (perangkat fisik)
- **On-device** (`Gemma 3n E2B`) — tanpa jaringan sama sekali, tapi butuh perangkat ≥6GB RAM

## 4. Build

```powershell
.\gradlew assembleDebug
.\gradlew installDebug      # perangkat/emulator harus terhubung
```

Build pertama lama (unduh dependency + LiteRT native lib). Kalau gagal karena memori,
tambahkan di `gradle.properties`:

```
org.gradle.jvmargs=-Xmx4096m
```

## 5. Izin yang harus diaktifkan manual di HP

Setelah install, buka app dan lewati onboarding, lalu aktifkan:

1. **Accessibility Service** — Settings → Accessibility → OpenDroid (ini inti kontrol HP)
2. **Microphone** — untuk STT dan wake word
3. **Notification access** — kalau mau fitur notifikasi/auto-reply
4. **Display over other apps** — untuk overlay

Tanpa Accessibility Service, agent tidak bisa membuka aplikasi apa pun.

## 6. Jembatan ke desktop (MCP)

`core/service/McpServer.kt` menjalankan server MCP JSON-RPC di `127.0.0.1:8765` **di dalam HP**.
Untuk menjangkaunya dari PC:

```powershell
adb forward tcp:8765 tcp:8765
```

Setelah itu PC bisa bicara MCP ke `localhost:8765` — coba dulu dengan:

```powershell
# uji manual: kirim satu request tools/list
# (server memakai framing header ala LSP, jadi paling mudah lewat MCP client sungguhan)
```

Daftar tool yang tersedia ada di `McpServer.tools()`. Belum ada autentikasi —
aman hanya karena bind ke loopback. Jangan buka ke `0.0.0.0` sebelum ada token.

## 7. Jalankan Claude Code

Dari folder ini:

```powershell
claude
```

`CLAUDE.md` akan terbaca otomatis. Mulai dengan:

> baca PLAN.md, kerjakan Fase 0 lalu Fase 1

## 8. Sinkron dengan upstream secara berkala

```powershell
git fetch upstream
git rebase upstream/main
```

Repo upstream aktif (400+ commit), jadi lakukan ini tiap beberapa minggu supaya
konflik tetap kecil.

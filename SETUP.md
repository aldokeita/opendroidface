# Setup

Repo: **https://github.com/aldokeita/opendroidface** (privat)
Folder kerja di PC: `C:\Users\ALDO\Documents\opendroidface`

Dua remote:

| Remote | Untuk apa |
|---|---|
| `origin` | repo sendiri — `main` berisi pekerjaan wajah, `feature/robot-face` cerminnya |
| `upstream` | `yashab-cyber/opendroid` — sumber fork, hanya untuk `fetch` dan merge berkala |

## 1. Prasyarat

| Yang dibutuhkan | Versi | Cek |
|---|---|---|
| JDK | 21 (Temurin/Adoptium) | `java -version` |
| Android SDK | API 36 | Android Studio → SDK Manager |
| Git | apa saja | `git --version` |
| adb | dari platform-tools | `adb version` |

JDK 21, bukan 17: `app/build.gradle` menyetel `JavaVersion.VERSION_21` dan
`jvmTarget '21'`.

Kalau belum ada Android Studio, minimal pasang **command line tools** +
platform-tools, lalu set `ANDROID_HOME` ke folder SDK.

## 2. Ambil kode

```powershell
git clone https://github.com/aldokeita/opendroidface.git
cd opendroidface
git remote add upstream https://github.com/yashab-cyber/opendroid.git
git fetch upstream
```

## 3. Konfigurasi

**Jangan** menyalin `gradle.properties.example` menjadi `gradle.properties` —
upstream melacak `gradle.properties` di Git dan build butuh isinya apa adanya.
Kredensial signing masuk ke `~/.gradle/gradle.properties` atau environment
variable; `gradle.properties.example` menjelaskan keduanya.

Lokasi Android SDK diberi tahu lewat `local.properties` (sudah di `.gitignore`):

```
sdk.dir=C\:\\Users\\ALDO\\Android\\Sdk
```

API key provider cloud diisi dari dalam aplikasi (layar Settings), bukan dari Gradle.

Provider yang praktis untuk pengembangan wajah:
- **Codex bridge** di PC — lihat `docs/face/desktop-codex-bridge.md`
- **Ollama** di PC — base URL `http://10.0.2.2:11434` (emulator) atau IP LAN (perangkat fisik)
- **On-device** (`Gemma 3n E2B`) — tanpa jaringan sama sekali, butuh perangkat ≥6GB RAM

## 4. Build

```powershell
.\gradlew assembleDebug
.\gradlew installDebug      # perangkat/emulator harus terhubung
.\gradlew testDebugUnitTest
```

Build pertama lama (unduh dependency + LiteRT native lib). Kalau gagal karena
memori, naikkan `org.gradle.jvmargs` di `~/.gradle/gradle.properties` — bukan di
`gradle.properties` proyek.

## 5. Izin yang harus diaktifkan manual di HP

Setelah install, buka app dan lewati onboarding, lalu aktifkan:

1. **Accessibility Service** — Settings → Accessibility → OpenDroid (inti kontrol HP)
2. **Microphone** — untuk STT dan wake word
3. **Notification access** — kalau mau fitur notifikasi/auto-reply
4. **Display over other apps** — untuk overlay

Tanpa Accessibility Service, agent tidak bisa membuka aplikasi apa pun.

## 6. Jembatan ke desktop (MCP)

Server MCP berjalan di dalam HP di port 8765, dijaga token bearer. Cara memakainya
— lewat USB maupun jaringan — ada di **`docs/desktop-mcp.md`**. Ringkasnya: ikon
komputer di bar atas layar Chat menampilkan token dan satu-satunya switch yang
memindahkan bind dari loopback ke jaringan.

## 7. Merilis APK lewat GitHub Releases

`.github/workflows/release.yml` membangun APK **release yang tertanda** dan
mengunggahnya ke halaman Releases setiap kali sebuah tag `v*` didorong. Ini
dirancang supaya rilis bisa dibuat dari HP: dorong tag, tunggu, unduh APK-nya.

### Sekali saja: siapkan kunci signing

APK yang ditandatangani kunci berbeda tidak bisa saling menimpa — Android akan
menolak memasangnya sebagai pembaruan. Karena itu rilis **tidak** boleh memakai
kunci debug (runner membuat kunci debug baru tiap kali jalan); harus satu kunci
tetap milik sendiri.

Di PC, buat keystore-nya:

```powershell
keytool -genkeypair -v -keystore opendroid-release.keystore `
  -alias opendroid -keyalg RSA -keysize 4096 -validity 10000
```

Simpan berkas itu **di luar Git** (sudah diblokir `.gitignore`) dan cadangkan.
Kehilangan kunci ini berarti tidak akan pernah bisa memperbarui aplikasi yang
sudah terpasang — hanya mencopot dan memasang ulang.

Lalu encode dan pasang sebagai secret repo (Settings → Secrets and variables →
Actions):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("opendroid-release.keystore")) | Set-Clipboard
```

| Secret | Isi |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | hasil base64 di atas |
| `RELEASE_STORE_PASSWORD` | kata sandi keystore |
| `RELEASE_KEY_ALIAS` | `opendroid` (atau alias yang dipakai) |
| `RELEASE_KEY_PASSWORD` | kata sandi kunci |

Isi keempatnya lewat antarmuka GitHub sendiri. Jangan pernah menaruhnya di
berkas mana pun dalam repo.

### Tiap rilis

```bash
git tag v0.2.0
git push origin v0.2.0
```

Workflow menjalankan unit test, membangun APK, lalu membuat Release dengan catatan
otomatis dan berkas `opendroidface-v0.2.0.apk` terlampir. Kalau tag sudah ada dan
ingin membangun ulang, jalankan workflow **Release APK** secara manual dan isi
nama tag-nya.

Untuk memasang di HP: buka halaman Releases, unduh APK, izinkan pemasangan dari
sumber ini saat diminta.

## 8. Bekerja langsung dari HP

Cukup dengan editor Git di HP (mis. aplikasi klien Git + editor teks) atau lewat
antarmuka web GitHub:

- Sunting berkas, commit, dorong ke `main`.
- `Android CI` berjalan otomatis pada tiap push ke `main` dan tiap pull request.
- Kalau hasilnya mau dipasang, dorong tag `v*` dan tunggu Release-nya.

> **Perhatian kuota.** Repo privat memakai jatah menit GitHub Actions. `Android CI`
> menjalankan emulator untuk API 26 dan 36 pada setiap push ke `main`, dan itu
> bagian yang paling mahal. Kalau jatah cepat habis, batasi job
> `instrumented-tests` supaya hanya berjalan pada pull request.

## 9. Sinkron dengan upstream secara berkala

```powershell
git fetch upstream
git merge upstream/main
```

Repo upstream aktif (400+ commit). Kode wajah sengaja ditaruh di package sendiri
(`ui/face`, `ui/bridge`, `core/face`, `core/bridge`) supaya jejak di berkas
upstream tetap kecil dan merge tetap mudah. Berkas upstream yang memang disentuh
tercatat di `CLAUDE.md`.

## 10. Menjalankan Claude Code

Dari folder proyek di PC:

```powershell
claude
```

`CLAUDE.md` terbaca otomatis (berkas itu tidak dilacak Git, jadi hanya ada di PC).
`PLAN.md` memuat roadmap; keempat fasenya sudah selesai, begitu pula backlog
kiosk, aksesibilitas, dokumentasi MCP, dan MCP lewat jaringan.

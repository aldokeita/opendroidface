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
- **Codex** — masuk dengan akun ChatGPT langsung dari HP, lihat `docs/face/codex-sign-in.md`
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

### Verifikasi developer Android — baca ini kalau pemasangan gagal

Android punya layanan sistem **Android Developer Verifier**
(`com.google.android.verifier`) yang memeriksa apakah sebuah app terdaftar pada
developer terverifikasi. Sejak **30 September 2026** hanya app dari developer
terverifikasi yang bisa dipasang di perangkat Android bersertifikat, dan
**Indonesia termasuk empat pasar pertama** (bersama Brasil, Singapura,
Thailand); sisanya menyusul 2027. Alur pemasangan barunya sudah digulirkan
bertahap sejak pertengahan Agustus 2026.

Artinya APK dari repo ini — mau ditandatangani kunci debug maupun kunci rilis
sendiri — **ditolak** di perangkat bersertifikat, dengan pesan yang tidak
menjelaskan apa pun:

> Something went wrong. App not installed.

Yang penting dipahami: ini **bukan** Play Protect. Mematikan Play Protect atau
Advanced Protection tidak berpengaruh, dan verifikasi ini juga bukan soal jenis
tanda tangan — menyiapkan keystore rilis di atas tidak membuatnya lolos.

Dua jalan keluar, keduanya resmi:

**1. `adb` — dikecualikan sepenuhnya.** Cara tercepat kalau ada PC:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`adb` juga yang mencetak kode kegagalan sebenarnya (`INSTALL_FAILED_...`) kalau
suatu saat pemasangan gagal karena sebab lain — jauh lebih berguna daripada
pesan generik di HP.

**2. Alur lanjutan di HP,** sekali siapkan lalu berlaku terus:

1. Developer options → **Apps from unverified developers**
2. Nyalakan **Allow apps from unverified developers**
3. Autentikasi dengan kunci layar
4. Konfirmasi tidak ada yang menekan Anda mengubah setelan ini
5. **Restart HP, tunggu 24 jam**
6. Kembali ke setelan itu, pilih izinkan **7 hari** atau **selamanya**

Masa tunggu 24 jam itu sekali saja, bukan per app — dirancang memutus tekanan
penipu yang memandu korban lewat telepon. Developer options tidak perlu
dibiarkan menyala setelah selesai.

Rujukan: [Android Help](https://support.google.com/android/answer/17065026),
[Play Console Help](https://support.google.com/android-developer-console/answer/16561738).

## 8. Bekerja langsung dari HP

Cukup dengan editor Git di HP (mis. aplikasi klien Git + editor teks) atau lewat
antarmuka web GitHub:

- Sunting berkas, commit, dorong ke `main`.
- `Android CI` berjalan otomatis pada tiap push ke `main` dan tiap pull request.
- APK debug tiap run tersimpan sebagai artifact `app-debug-apk` (retensi 7 hari),
  jadi sebuah branch bisa dipasang tanpa menunggu rilis. Berkasnya dibungkus ZIP
  oleh GitHub — ekstrak dulu sebelum memasang.
- Kalau hasilnya mau dipasang sebagai rilis, dorong tag `v*` dan tunggu Release-nya.

Pemasangan dari HP tunduk pada verifikasi developer Android — lihat §7 kalau
muncul "App not installed".

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

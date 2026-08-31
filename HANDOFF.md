# Handoff — sesi remote 2026-08-31

Catatan serah-terima dari sesi Claude Code di web ke sesi lokal. Isinya keadaan
repo saat sesi berakhir, apa yang sudah beres, dan apa yang menunggu.

Hapus berkas ini kalau pekerjaannya sudah tuntas.

---

## Keadaan repo

| | |
|---|---|
| `main` | `3d263fc` (merge PR #3) |
| Branch kerja | `claude/opendroidface-repo-review-h11xxn`, sudah didorong, **2 commit di atas `main`, belum ada PR** |
| Build | `assembleDebug` hijau |
| Unit test | 602 test, 0 gagal |
| Lint | **0 error** (16 masih ditahan `lint-baseline.xml` warisan upstream) |

Dua commit yang menunggu di branch:

- `7bd7144` **build** — `applicationIdSuffix '.debug'` + `versionNameSuffix '-debug'`,
  supaya build debug (`com.opendroid.aiagent.debug`) bisa terpasang berdampingan
  dengan app produksi, bukan menuntut yang lama dicopot.
- `f95bc1b` **docs** — penjelasan verifikasi developer Android di `SETUP.md` §7,
  `docs/RELEASE.md`, dan `RELEASE.md` (lihat bagian "Yang menghalangi" di bawah).

Buka PR untuk keduanya, atau merge langsung — terserah. Belum dibuka karena
sesi berakhir lebih dulu.

---

## Sudah beres (merged ke `main`)

**PR #2** — perombakan UI selesai untuk **semua sembilan layar**: Chat, Splash,
Settings, Plan, Memory, Macros, Logs, Onboarding, Permissions, plus
`ui/components/PlanStepCard.kt`. Pola cacatnya sama di semuanya dan seluruhnya
diperbaiki:

- ~700 pembacaan alias warna gelap statis (`TextPrimary`, `CardBackground`, …)
  diganti palet aktif. **Tema terang sebelumnya salah cat total** di layar-layar ini.
- ~90 pemakaian `FontFamily.Monospace` dibuang; tipografi patuh tiga keluarga di
  `ui/theme/Type.kt`.
- Garis tepi kartu dihapus, kecuali tiga yang benar-benar menandai keadaan
  (model aktif di Settings, run aktif dan baris riwayat terpilih di Plan).
- Judul bar atas jadi kalimat wajar dengan Montserrat, bukan huruf kapital neon
  bertracking 2sp.
- Splash dan Onboarding membuka pada `RobotFace`, bukan logo raster.
- Durasi splash turun dari 3,3 detik ke di bawah 1 detik, menghormati setelan
  animasi perangkat.

Juga di PR #2: `AGENTS.md`/`ROADMAP.md`/`docs/agents/` diarahkan ke repo yang
benar (dulu menyebut `JMAN730/opendroid`), lint 9 error → 2, dan `Android CI`
menyimpan APK debug tiap run sebagai artifact `app-debug-apk` (retensi 7 hari).

**PR #3** — lint 2 error → **0**. Cabang mati `SDK_INT >= M` di
`SettingsViewModel.isCellularNetwork()` dibuang (`minSdk` 26, jadi tidak pernah
jalan; itu satu-satunya pemakai dua API `ConnectivityManager` yang deprecated),
dan `network_security_config.xml` debug diberi `tools:ignore`, bukan entri
baseline.

`PLAN.md` bagian "Perombakan UI" sudah dicentang penuh. Kotak `[ ]` yang masih
terlihat di Fase 0 itu riwayat, bukan pekerjaan tersisa.

---

## Yang belum dikerjakan, dan ini yang utama

**Sembilan layar itu belum pernah dilihat di perangkat.** Build hijau dan 602
test lewat hanya membuktikan kodenya kompilasi dan logikanya benar — bukan bahwa
tata letaknya benar. Khusus tema terang, layar-layar ini memang **tidak pernah**
benar sebelumnya, jadi jalur itu baru sepenuhnya dan belum pernah dilihat mata.

Yang perlu dilakukan: pasang APK debug, buka kesembilan layar di **tema gelap
dan terang**, catat yang meleset.

```powershell
git pull
.\gradlew installDebug
```

---

## Yang menghalangi pemasangan (sudah terpecahkan, tapi perlu diketahui)

Sesi ini habis banyak waktu di sini, jadi jangan mengulanginya.

APK ditolak Pixel 8a (Android 17) dengan pesan **"Something went wrong. App not
installed."** Penyebabnya **bukan** bug di repo ini. APK-nya lolos semua
pemeriksaan statis: paket, `minSdk` 26/`targetSdk` 36, keempat ABI, tanda tangan
v2 valid, tanpa `testOnly`, perataan zip 4 byte dan 16 KB, dan segmen ELF native
lib selaras 16 KB (`0x4000`).

Penyebabnya **Android Developer Verifier** (`com.google.android.verifier`):
sejak 30 September 2026 hanya app dari developer terverifikasi yang bisa
dipasang di perangkat bersertifikat, dan **Indonesia salah satu dari empat pasar
pertama**. Alur barunya sudah digulirkan sejak pertengahan Agustus 2026.

Yang penting:

- Ini **bukan** Play Protect. Mematikan Play Protect maupun Advanced Protection
  tidak berpengaruh — sudah dicoba.
- Ini **bukan** soal jenis tanda tangan. Menyiapkan keystore rilis **tidak**
  membuatnya lolos.
- **`adb` dikecualikan sepenuhnya.** Jalur ini yang dipakai.
- Alternatif tanpa PC: Developer options → *Apps from unverified developers* →
  izinkan → autentikasi → restart → **tunggu 24 jam** → pilih 7 hari atau
  selamanya. Sekali saja, bukan per app.

Seluruhnya sudah tertulis di `SETUP.md` §7 dan `docs/RELEASE.md`.

**Kendala terakhir yang belum tuntas:** di PC kantor, `adb devices` tidak
menampilkan perangkat apa pun — kabel, mode USB, atau driver/kebijakan endpoint.
Di komputer rumah kemungkinan tidak jadi masalah. Kalau iya, wireless debugging
melewatinya: HP → Developer options → Wireless debugging → *Pair device with
pairing code*, lalu `adb pair IP:PORT` (port pairing) dan `adb connect IP:PORT`
(port yang berbeda, dari layar utama Wireless debugging).

---

## Catatan lain

- **Robolectric flaky.** `MediaActionsTest` tiga kali gagal dengan
  `FileSystemAlreadyExistsException` saat Robolectric menyiapkan font — mati
  sebelum badan test jalan, dan selalu hijau saat diulang. Bukan cacat produk.
  Kalau muncul di CI, ulangi job-nya.
- **APK debug 74 MB.** `classes.dex` 41 MB (debug tanpa R8) plus
  `liblitertlm_jni.so` untuk empat ABI (x86_64 saja 24 MB). Terlalu besar untuk
  dikirim lewat chat, karena itu jalurnya artifact CI.
- **`PlanStepCard` — keputusan yang bisa dibalik.** Dua warna status dulu hex
  yang di-hardcode dan tidak bisa mengikuti tema (amber di atas kartu putih
  hampir tak terlihat). Sekarang `AUTO_FIXING` memakai ungu (senada `REPAIRED`,
  "mesin perbaikan") dan `BLOCKED` memakai oranye peringatan dari palet. Kalau
  pemetaan lama lebih dikenali, ini sebaris ubah.
- **Backlog `PLAN.md`** yang masih terbuka hanya "Wajah kustom" (Lottie dari
  file eksternal), dan itu sudah dikesampingkan pemilik pada 2026-08-31.
- **`lint-baseline.xml`** masih menahan 16 error warisan upstream. Tidak
  memerahkan CI; membereskannya pekerjaan terpisah.

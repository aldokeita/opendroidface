# Roadmap — Lapisan Wajah Robot

Empat fase. Tiap fase berdiri sendiri dan bisa di-build serta diuji.
Selesaikan berurutan; jangan lompat ke fase berikutnya sebelum yang sekarang jalan di perangkat.

---

## Arah produk (ditetapkan pemilik, 2026-08-30)

Dua mode, satu aplikasi:

1. **Mode Chat (default saat aplikasi dibuka)** — UI modern minimalis: ruang kosong lega,
   satu warna aksen, tanpa ornamen. Riwayat chat + input teks + tombol mikrofon,
   ditambah satu tombol **Hands-free** yang menonjol.
2. **Mode Auto (label di layar: "Hands-free")** — layar penuh berisi wajah robot saja. Pengguna hanya bicara; tidak ada
   input teks, tidak ada gelembung chat. Wajah adalah satu-satunya umpan balik:
   mendengar → berpikir → mengeksekusi → bicara. Keluar lewat satu tombol tutup.

Konsekuensi ke roadmap: `RobotFace` harus dipakai di dua ukuran (kecil di header
Mode Chat, penuh di Mode Auto) — komponennya wajib bebas ukuran (`fillMaxSize` di
dalam `Box` pemanggil), tanpa dp hardcode.

---

## Fase 0 — Fondasi repo (½ hari)

- [ ] `git clone https://github.com/yashab-cyber/opendroid.git .` ke folder ini (lihat `SETUP.md`)
- [ ] `git remote rename origin upstream`, lalu tambah `origin` ke fork sendiri
- [ ] Branch kerja: `git checkout -b feature/robot-face`
- [ ] Build bersih: `./gradlew assembleDebug` — pastikan hijau **sebelum** menyentuh apa pun
- [ ] Pindahkan file dari `starter/` ke lokasi aslinya (lihat header tiap file), lalu hapus `starter/`

**Selesai kalau:** APK debug terpasang dan chat biasa sudah berfungsi dengan satu provider (mis. Ollama atau Gemini).

---

## Fase 1 — Wajah statis yang mengikuti AgentState (2–3 hari)

Target: wajah muncul di atas `ChatScreen`, ekspresi berganti sesuai state agent.

**File baru** (sudah disediakan di `starter/`):
- `app/src/main/java/com/opendroid/ai/ui/face/FaceExpression.kt`
- `app/src/main/java/com/opendroid/ai/ui/face/RobotFace.kt`

**Satu-satunya sentuhan ke upstream:**
`ui/screens/ChatScreen.kt` — sisipkan `RobotFace(...)` di bagian atas kolom utama,
di-drive oleh `viewModel.visibleAgentState.collectAsState()`.

### Peta state → ekspresi

| AgentState | Ekspresi | Detail visual |
|---|---|---|
| `Idle` | `NEUTRAL` | Mata terbuka normal, kedip acak tiap 3–6 detik, sedikit gerak melayang |
| `Listening` | `LISTENING` | Mata membesar, alis naik sedikit, cincin/gelombang di sekeliling wajah |
| `Thinking` | `THINKING` | Pupil bergerak ke atas-samping, alis miring, titik "…" |
| `PlanProposed` | `CURIOUS` | Kepala miring, satu alis naik — menunggu persetujuan |
| `ExecutingPlan` | `FOCUSED` | Mata menyipit, pupil mengecil, indikator progres tipis |
| `Speaking` | `SPEAKING` | Mulut membuka-menutup (Fase 3 mengikat ke amplitudo) |
| `Error` | `SAD` | Alis turun ke dalam, mata sayu, warna aksen → `accentRed` |

Gunakan warna dari `LocalOpenDroidColors.current` supaya ikut light/dark.

**Selesai kalau:** kirim perintah, wajah berjalan Listening → Thinking → ExecutingPlan → Speaking → Idle.

---

## Fase 1b — Mode Auto & pembersihan UI (1–2 hari)

Target: tombol **Hands-free** di Mode Chat membuka layar penuh berisi wajah + input suara saja.

Catatan nama: di kode mode ini bernama *Auto* (`AutoModeScreen`, `AutoModeHost`), tapi
labelnya di layar **"Hands-free"** — upstream sudah punya chip `Auto` yang artinya
persetujuan rencana otomatis, dan dua kontrol bernama "Auto" akan terbaca sebagai
setelan yang sama.

**File baru:**
- `app/src/main/java/com/opendroid/ai/ui/face/AutoModeScreen.kt` — layar penuh murni UI:
  `RobotFace` di tengah, label state satu baris, transkrip, tombol mikrofon, tombol tutup.
  Tanpa ketergantungan ke ViewModel supaya label statusnya bisa diuji.
- `app/src/main/java/com/opendroid/ai/ui/face/AutoModeHost.kt` — perkabelan suara:
  memegang state mendengar, meminta izin mikrofon, mengirim hasil final ke
  `ChatViewModel.sendMessage`. Ditaruh di sini, bukan di `ChatScreen`, supaya file
  upstream cukup tahu bahwa Mode Auto ada — bukan cara kerjanya.
- `app/src/main/java/com/opendroid/ai/ui/face/AutoModeButton.kt` — tombol masuk mode Auto.

**Sentuhan ke upstream:** tetap hanya `ui/screens/ChatScreen.kt` — menaruh `RobotFace`
di header, `AutoModeButton` di bar aksi, dan menampilkan `AutoModeScreen` sebagai
overlay saat aktif. State `autoMode` disimpan lokal di composable (`rememberSaveable`),
bukan di `ChatViewModel`, supaya `ChatViewModel` tidak tersentuh.

Mode Auto memakai jalur suara yang sudah ada (`SpeechRecognitionEngine` yang sama
dengan tombol mikrofon Mode Chat) — tidak ada mesin suara baru.

**Selesai kalau:** tekan Hands-free → layar wajah penuh; bicara → wajah bereaksi dan agent
menjawab dengan suara; tekan tutup → kembali ke chat dengan riwayat utuh.

---

## Fase 2 — Amplitudo saat mendengar (½ hari)

Ini yang membuat wajah terasa "hidup" dengan usaha paling kecil.

`SpeechRecognitionEngine` sudah menerima callback `onRmsChanged(rmsdB: Float)` dari
Android, tapi isinya kosong (`core/voice/SpeechRecognitionEngine.kt`, di dalam
`object : RecognitionListener`).

**Langkah:**
1. Tambah file baru `core/voice/VoiceAmplitude.kt` (disediakan di `starter/`) — `@Singleton`
   pemegang `StateFlow<Float>` ternormalisasi 0f..1f.
2. Di `SpeechRecognitionEngine`, tambah parameter konstruktor opsional
   `private val amplitude: VoiceAmplitude? = null` (default null supaya pemanggil lama tak rusak),
   lalu isi `onRmsChanged` dengan `amplitude?.publishRms(rmsdB)`.
3. `RobotFace` meng-collect `VoiceAmplitude.level` dan memakainya untuk skala mulut/cincin
   **hanya** saat state `Listening`.

Catatan: `rmsdB` dari Android berkisar kira-kira −2f..10f dan berisik. Normalisasi
dengan clamp + low-pass filter (sudah ada di file starter), jangan dipakai mentah.

**Selesai kalau:** wajah bereaksi ke volume suara Anda secara real-time, halus, tanpa jitter.

---

## Fase 3 — Lip-sync saat bicara (3–5 hari)

Dua jalur TTS di `core/voice/TextToSpeechEngine.kt`, masing-masing butuh pendekatan berbeda.

### Jalur A — ElevenLabs (via `MediaPlayer`)
Pasang `android.media.audiofx.Visualizer` ke `mediaPlayer.audioSessionId`, ambil
`getWaveForm` / `getFft`, hitung RMS per frame, publikasikan ke `VoiceAmplitude`.
- Butuh permission `android.permission.RECORD_AUDIO` (sudah ada di manifest untuk STT).
- Lepaskan `Visualizer` di `onCompletion` — kalau bocor, TTS berikutnya diam.

### Jalur B — Android TTS lokal (fallback)
Tidak ada akses audio buffer. Pakai `UtteranceProgressListener.onRangeStart(utteranceId, start, end, frame)`
yang memberi batas per kata → animasikan mulut per kata. Kasar tapi memadai.
- Listener-nya sudah dipasang di `TextToSpeechEngine.init` — tinggal tambah override.
- Butuh `putExtra` / `KEY_PARAM_UTTERANCE_ID` yang sudah ada (`"opendroid_tts"`).

**Selesai kalau:** mulut bergerak sinkron dengan suara di kedua jalur, dan berhenti rapi saat selesai.

---

## Fase 4 — Emosi dari LLM (2–3 hari)

Cara paling akurat: biarkan LLM yang menentukan emosi, jangan ditebak dari audio.

`AgentLoop` sudah memanggil LLM dengan `ResponseFormat.JSON` (lihat `AgentLoop.kt` sekitar baris 644–705).

**Langkah:**
1. `docs/prompts.md` — tambah field opsional ke skema respons:
   `"emotion": "neutral" | "happy" | "curious" | "confused" | "apologetic"`
2. Parser plan di `AgentLoop.parsePlanFromLlmResponse(...)` — baca field itu,
   **abaikan diam-diam kalau tidak ada** (model kecil on-device sering melewatkannya).
3. Simpan ke `VoiceAmplitude`-sejenis holder baru, mis. `FaceMood`, yang di-blend
   `RobotFace` di atas ekspresi dasar dari `AgentState`.

Prinsip: `AgentState` menentukan **bentuk dasar** ekspresi; emosi LLM hanya **memodulasi**
(sudut alis, kelengkungan mulut, warna aksen). Jangan biarkan emosi menimpa state —
wajah "senang" saat `Error` akan terasa rusak.

**Selesai kalau:** jawaban bernada minta maaf memunculkan wajah yang berbeda dari jawaban biasa.

---

## Backlog (setelah Fase 4)

- ~~**Mode kiosk/dock**~~ — selesai. Tombol dok di layar Hands-free: kontrol disembunyikan,
  layar dijaga tetap menyala, mikrofon menyalakan dirinya sendiri saat agent menganggur
  (berhenti setelah 30 kali sunyi beruntun), dan seluruh wajah bergeser sangat lambat
  untuk mencegah burn-in OLED. Keluar dengan tekan-lama. Lihat `ui/face/KioskMode.kt`.
- **Desktop bridge terdokumentasi** — tulis `docs/desktop-mcp.md`: `adb forward tcp:8765 tcp:8765`,
  contoh konfigurasi MCP client, daftar tool yang diekspos `McpServer.tools()`
- **MCP lewat jaringan** — ganti `LOOPBACK` → `0.0.0.0` **hanya setelah** ada token auth + pairing
- **Wajah kustom** — muat animasi Lottie dari file eksternal supaya pengguna bisa ganti karakter
- **Aksesibilitas** — sediakan opsi matikan animasi (`Settings.Global.ANIMATOR_DURATION_SCALE`)
  dan padanan teks untuk tiap ekspresi bagi pengguna TalkBack

## Risiko yang sudah diketahui

| Risiko | Mitigasi |
|---|---|
| Merge konflik dengan upstream (repo aktif, 421+ commit) | Ubah sesedikit mungkin file upstream; rebase rutin |
| `Visualizer` butuh RECORD_AUDIO & bisa ditolak OEM | Selalu sediakan fallback ke animasi berbasis timer |
| Model on-device kecil (Qwen 0.5B) sering gagal ikut skema JSON | Field `emotion` wajib opsional; default `neutral` |
| Animasi menguras baterai saat idle | Turunkan frame rate / hentikan animasi saat layar mati atau app di background |

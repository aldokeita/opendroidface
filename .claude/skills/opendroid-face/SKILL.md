---
name: opendroid-face
description: Konvensi kerja untuk fork OpenDroidFace — di mana kode wajah robot boleh diletakkan, file upstream mana yang boleh disentuh, dan cara memverifikasi perubahan. Gunakan setiap kali mengedit, menambah, atau meninjau kode di repo ini.
---

# Konvensi OpenDroidFace

Repo ini fork dari `yashab-cyber/opendroid` (Apache-2.0). Tugas kita menambah
lapisan wajah robot; sisanya milik upstream.

## Aturan sentuhan upstream

Upstream aktif (400+ commit). Tiap file upstream yang kita ubah = calon konflik rebase.

**Boleh dibuat bebas** (kode kita sendiri):
- `app/src/main/java/com/opendroid/ai/ui/face/**`
- `app/src/test/java/com/opendroid/ai/ui/face/**`
- `docs/face/**`

**Boleh diubah, tapi minimal dan seperlunya** (sudah direncanakan di `PLAN.md`):
- `ui/screens/ChatScreen.kt` — hanya untuk menyisipkan `RobotFace(...)`
- `core/voice/SpeechRecognitionEngine.kt` — hanya mengisi `onRmsChanged` + parameter opsional
- `core/voice/TextToSpeechEngine.kt` — hanya hook amplitudo/lip-sync
- `core/agent/AgentLoop.kt` — hanya parsing field `emotion` yang opsional
- `docs/prompts.md` — hanya menambah field `emotion` ke skema

**Jangan disentuh tanpa persetujuan user:**
segala hal di `accessibility/`, `actions/`, `core/llm/`, `data/`, `di/`, `core/security/`,
dan `core/service/McpServer.kt`.

Kalau sebuah perubahan tampaknya menuntut menyentuh file terlarang, **berhenti dan
jelaskan ke user kenapa** — biasanya ada jalan lain lewat `ui/face/`.

## Kompatibilitas mundur

Saat menambah parameter ke kelas upstream, selalu beri nilai default supaya
pemanggil lama tidak rusak:

```kotlin
class SpeechRecognitionEngine(
    private val context: Context,
    private val amplitude: VoiceAmplitude? = null,   // default null — pemanggil lama aman
)
```

`SpeechRecognitionEngine` dibuat di dua tempat (`ui/screens/ChatScreen.kt` dan
`core/service/OpenDroidService.kt`) — cek keduanya setiap kali mengubah konstruktornya.

## Gaya kode

- Warna **selalu** `LocalOpenDroidColors.current`. Tidak ada hex literal di kode wajah.
- DI lewat Hilt. State global wajah = `@Singleton` + `StateFlow`, bukan `object` dengan var statis.
- Animasi: `Animatable` / `animateFloatAsState` / `graphicsLayer`. Jangan pernah
  `mutableStateOf` yang di-update dari loop per-frame — itu memicu recomposition 60×/detik.
- Nilai dari sensor/audio **selalu** di-clamp dan dihaluskan sebelum menyentuh UI.
- Komentar menjelaskan **kenapa**, bukan **apa**. Ikuti gaya komentar upstream yang panjang
  dan menjelaskan alasan (lihat `SpeechRecognitionEngine.activeSessionId`).

## Verifikasi sebelum menyatakan selesai

Urutan wajib:

1. `./gradlew assembleDebug` — harus hijau
2. `./gradlew testDebugUnitTest` — harus hijau; logika pemetaan state→ekspresi wajib punya test
3. Kalau perubahan menyentuh UI: pasang ke perangkat dan lihat sendiri, atau minta user
   mengirim screenshot/rekaman. **Jangan menyatakan animasi "sudah benar" tanpa dilihat.**
4. `git diff --stat upstream/main` — pastikan jumlah file upstream yang tersentuh
   masih sesuai daftar di atas

## Yang tidak boleh dilakukan

- Mengubah `McpServer.LOOPBACK` menjadi `0.0.0.0` — server itu tanpa autentikasi
- Meng-commit `gradle.properties` (berisi API key)
- Menambah dependency animasi baru — `lottie-compose` dan Compose animation sudah ada
- Menaikkan versi dependency upstream "sekalian" saat mengerjakan hal lain

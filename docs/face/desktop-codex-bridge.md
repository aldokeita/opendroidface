# Menghubungkan OpenDroidFace ke GPT lewat Codex CLI

> **Tidak lagi diperlukan untuk provider Codex.** Provider **Codex** di layar Settings kini
> masuk lewat OAuth langsung di HP — tanpa PC, tanpa alamat, tanpa token. Lihat
> `docs/face/codex-sign-in.md`.
>
> Dokumen ini tetap ada untuk kasus lain: menjalankan Codex CLI milik PC sebagai endpoint
> **Custom OpenAI Compatible**, misalnya supaya jawaban dihasilkan di mesin yang punya akses
> ke repo di PC itu.

Ringkasnya: PC menjalankan jembatan kecil yang bicara protokol OpenAI (`POST /v1/chat/completions`)
dan meneruskan tiap permintaan ke `codex exec`. HP memakai provider **Custom OpenAI Compatible**
dan menunjuk ke IP LAN PC. Tidak butuh API key OpenAI — memakai akun ChatGPT yang sudah
login di Codex CLI.

## Peringatan sebelum mulai

- **Ini memakai langganan ChatGPT Anda lewat jalur yang bukan API resmi.** Kemungkinan besar
  melanggar ketentuan layanan OpenAI. Anda sudah memilih jalur ini secara sadar; catatan ini
  ada supaya tidak terlupa.
- Jembatan mendengarkan di **seluruh antarmuka jaringan** (`0.0.0.0`) supaya HP bisa
  menjangkaunya. Karena itu **token wajib** — tanpa token, siapa pun di Wi-Fi yang sama bisa
  memakai akun ChatGPT Anda. Token diperiksa dengan perbandingan waktu-konstan.
- Codex dijalankan dengan `--sandbox read-only`. Permintaan dari HP bisa **membaca** PC ini,
  tidak bisa menulis. Jangan longgarkan tanpa alasan kuat.
- Jangan jalankan di Wi-Fi publik.

## 1. Syarat

| Yang dibutuhkan | Cek |
|---|---|
| Node.js | `node --version` |
| Codex CLI sudah login | `codex --version`, lalu `codex exec "hi"` harus menjawab |
| HP dan PC di jaringan yang sama | atau pakai `adb reverse`, lihat bagian 6 |
| APK debug (bukan release) | wajib — alasannya di bagian 5 |

## 2. Jalankan jembatan

```powershell
.\tools\codex-bridge\start.ps1
```

Kali pertama, skrip membuat token acak, mencetaknya, dan menyimpannya di
`tools/codex-bridge/.codex-bridge-token` (sudah masuk `.gitignore`). Salin token itu —
di aplikasi, token inilah yang diisi sebagai **API key**.

Keluarannya juga mencetak alamat yang bisa dipakai HP, misalnya:

```
codex-bridge listening on http://0.0.0.0:8787
  reachable from this machine's LAN at http://192.168.1.5:8787/v1
```

Pilih alamat yang satu subnet dengan HP (biasanya `192.168.x.x`).

## 3. Izinkan lewat Windows Firewall

Sekali saja, di PowerShell **sebagai Administrator**:

```powershell
New-NetFirewallRule -DisplayName "Codex bridge 8787" -Direction Inbound -LocalPort 8787 -Protocol TCP -Action Allow -Profile Private
```

`-Profile Private` menahan aturan ini hanya untuk jaringan yang ditandai privat.

## 4. Setel di aplikasi

Di HP: **Settings → provider → Custom OpenAI Compatible**, lalu isi:

| Kolom | Nilai |
|---|---|
| Endpoint / Base URL | `http://192.168.1.5:8787/v1` (ganti dengan IP PC Anda) |
| API key | token dari langkah 2 |
| Model | `codex` (nama bebas; jembatan mengabaikannya kecuali `CODEX_MODEL` diisi) |

Uji cepat dari PC dulu:

```powershell
curl.exe -s http://127.0.0.1:8787/health
```

## 5. Kenapa harus build debug

`network_security_config.xml` bawaan upstream melarang HTTP polos kecuali ke
`localhost`, `127.0.0.1`, dan `10.0.2.2`. Alamat LAN seperti `192.168.1.5` diblokir,
dan aplikasi melaporkannya sebagai **"Network error"** — bukan masalah key.

Build **debug** memakai `app/src/debug/res/xml/network_security_config.xml` yang
mengizinkan cleartext ke mana saja. Build **release** tidak pernah memakainya.
Artinya jembatan ini hanya jalan di build debug, dan itu memang tepat: jembatan
memang alat pengembangan.

Kalau suatu saat butuh di build release, jangan longgarkan confignya — pakai
`adb reverse` di bawah, yang tetap masuk lewat `127.0.0.1`.

## 6. Kalau tidak satu Wi-Fi

Lewat USB, tanpa jaringan sama sekali:

```powershell
adb reverse tcp:8787 tcp:8787
```

Lalu di aplikasi pakai `http://127.0.0.1:8787/v1`. HP akan menembus ke PC lewat kabel.
Perlu diulang tiap kali kabel dicabut.

## 7. Setelan lain

Semua lewat environment variable:

| Variabel | Default | Guna |
|---|---|---|
| `CODEX_BRIDGE_TOKEN` | — (wajib, min 16 karakter) | Token bersama |
| `CODEX_BRIDGE_PORT` | `8787` | Port |
| `CODEX_BRIDGE_HOST` | `0.0.0.0` | Isi `127.0.0.1` kalau hanya dipakai lewat `adb reverse` |
| `CODEX_BIN` | `codex` | Path ke binary Codex |
| `CODEX_MODEL` | kosong | Paksa model tertentu, mis. `gpt-5` |
| `CODEX_TIMEOUT_MS` | `180000` | Batas waktu satu permintaan |

## Cara kerjanya

1. Aplikasi mengirim `POST /v1/chat/completions` berisi `messages`.
2. Jembatan memeriksa token, meratakan pesan jadi satu prompt, menambah instruksi
   "jawab langsung, jangan ubah berkas, jangan jalankan perintah".
3. Kalau aplikasi meminta `response_format: json_object`, jembatan menambah instruksi JSON
   dan **mengekstrak objek JSON terluar** dari jawaban. Ini penting: `AgentLoop` menolak
   respons yang dibungkus prosa atau pagar markdown, dan Codex sering menambahkannya.
4. Codex dijalankan sebagai `codex exec` dengan `-o <file>`, dan isi file itu jadi jawaban.
5. Jawaban dikembalikan dalam bentuk `chat.completion` ala OpenAI. Jumlah token selalu 0 —
   Codex tidak melaporkannya, dan aplikasi hanya mencatatnya.

Permintaan **diantrikan**, satu per satu. Codex adalah proses berat; menjalankan beberapa
sekaligus hanya menghasilkan timeout.

### Kenapa ada "detak" di tengah jawaban

OkHttp di aplikasi memakai `readTimeout` **15 detik** (`di/AppModule.kt`), sedangkan satu
putaran Codex sering 30–45 detik. Aplikasi akan menyerah dan menampilkan
**"Can't reach Custom OpenAI Compatible"** padahal Codex masih bekerja.

Karena `di/` adalah wilayah upstream yang tidak boleh kita sentuh sembarangan, jembatanlah
yang menyesuaikan: kalau jawaban belum siap dalam 8 detik, jembatan membuka respons lebih
dulu lalu menulis satu spasi tiap 5 detik. Tiap penulisan mereset timer baca di sisi klien,
dan spasi di depan objek JSON diabaikan parser mana pun. Setelah Codex selesai, badan JSON
yang sebenarnya menyusul.

Efek samping yang perlu diketahui: begitu respons dibuka, status HTTP sudah terkirim, jadi
kegagalan yang terjadi **sesudah** itu tidak bisa lagi jadi error HTTP. Kegagalan seperti itu
dikirim sebagai jawaban asisten berisi `Codex bridge error: ...` — supaya alasannya tetap
terbaca di layar, bukan berubah jadi "network error" yang tak menjelaskan apa-apa.

Kalau suatu saat Anda memang ingin menaikkan timeout di aplikasi, itu satu baris di
`di/AppModule.kt` — tapi menyentuh file upstream berarti menambah risiko konflik saat rebase.

## Kalau bermasalah

| Gejala | Sebab yang paling sering |
|---|---|
| "Network error" di layar tes | Build release, atau HTTP polos ke IP LAN diblokir — lihat bagian 5 |
| `401` dari jembatan | Token di aplikasi tidak sama dengan yang dicetak skrip. Log jembatan mencetak panjang key yang diterima (bukan isinya) |
| HP timeout, PC diam | Firewall memblokir, atau IP yang dipakai bukan subnet HP |
| `Could not start "codex"` | Codex tidak ada di PATH proses ini; isi `CODEX_BIN` dengan path lengkap |
| Jawaban lama sekali | Wajar: satu putaran Codex bisa puluhan detik. Naikkan `CODEX_TIMEOUT_MS` |
| "Can't reach Custom OpenAI Compatible" | Timeout baca 15 detik di aplikasi. Sudah ditangani oleh detak keep-alive — kalau muncul lagi, pastikan jembatan yang jalan adalah versi terbaru |
| Ganti token tapi tetap `401` | Server membaca token **sekali saat start**. Matikan server dulu, baru `start.ps1` |
| Agent bilang gagal parsing rencana | Model membalas prosa, bukan JSON. Lihat log jembatan untuk jawaban mentahnya |

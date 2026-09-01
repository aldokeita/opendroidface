<p align="center">
  <img src="assets/backgroundremoved.png" alt="Logo OpenDroidFace" width="200px">
</p>

<h1 align="center">OpenDroidFace</h1>

<p align="center">
  <strong>Agen AI otonom untuk Android — dengan wajah</strong>
</p>

<p align="center">
  <em>HP-mu. Aturanmu. AI-mu.</em>
</p>

<p align="center">
  <a href="https://github.com/aldokeita/opendroidface/releases"><img src="https://img.shields.io/github/v/release/aldokeita/opendroidface?style=for-the-badge&color=E5484D&labelColor=0D1117&logo=android&logoColor=white" alt="Rilis"></a>
  <a href="https://github.com/aldokeita/opendroidface/stargazers"><img src="https://img.shields.io/github/stars/aldokeita/opendroidface?style=for-the-badge&color=FFD700&labelColor=0D1117&logo=github&logoColor=white" alt="Bintang"></a>
  <a href="https://github.com/aldokeita/opendroidface/blob/main/LICENSE"><img src="https://img.shields.io/github/license/aldokeita/opendroidface?style=for-the-badge&color=00BFFF&labelColor=0D1117" alt="Lisensi"></a>
</p>

<p align="center">
  <a href="#apa-itu-opendroidface">Apa ini</a> •
  <a href="#wajah-robot">Wajah</a> •
  <a href="#kemampuan">Kemampuan</a> •
  <a href="#provider-llm">Provider</a> •
  <a href="#memasang">Memasang</a> •
  <a href="#lisensi">Lisensi</a>
</p>

---

## Apa itu OpenDroidFace?

Ini bukan chatbot. Ini **agen** yang tinggal di HP Android dan benar-benar mengerjakan sesuatu.

> *"Cek besok hujan atau tidak. Kalau iya, kirim pesan ke istriku bilang aku telat, terus pasang alarm jam 6 sore."*

Perintah itu dipecah jadi tiga langkah, dijalankan satu per satu, hasilnya diperiksa, dan rencananya disusun ulang kalau ada langkah yang gagal.

Bedanya dengan [proyek asalnya](https://github.com/yashab-cyber/opendroid): OpenDroidFace punya **wajah**. Ekspresinya mengikuti apa yang sedang dikerjakan agen, nada suaranya, dan emosi jawaban dari LLM.

---

## Wajah robot

Wajahnya membaca `AgentState` langsung dari otak agen, jadi ia tidak pernah berbohong soal apa yang sedang terjadi — diam, mendengar, berpikir, menjalankan rencana, bicara, atau gagal.

| | |
|---|---|
| **Dua gaya** | Gaya EMO dengan warna pilihanmu, dan gaya panel polos dengan 24 ekspresi |
| **Emosi dari jawaban** | Perencana diminta menyertakan emosi; wajahnya menyesuaikan |
| **Lip-sync** | Mulutnya bergerak mengikuti suara yang sedang diucapkan |
| **Mode Hands-free** | Layar penuh, tanpa tangan, jalan juga dalam posisi landscape |
| **Mode Dok** | Untuk HP yang ditaruh di dudukan meja |
| **Bisa diam** | Setelan gerak yang menghormati "kurangi animasi" milik sistem |

Meteran suaranya satu garis gelombang, bukan bar-bar equalizer.

---

## Kemampuan

### Otak agen

| | |
|---|---|
| **Perencanaan sendiri** | Perintah rumit dipecah jadi langkah berurutan dengan pelacakan ketergantungan |
| **Deteksi kebiasaan** | Urutan aplikasi yang berulang tiap hari (Gmail → Calendar → Slack jam 9 pagi) dikenali dan ditawarkan jadi rutinitas otomatis |
| **Evaluasi ulang** | Hasil tiap langkah dipantau; rencana disusun ulang saat ada yang gagal |
| **Penjaga niat gabungan** | Perintah dua aksi ("buka WhatsApp *dan* kirim pesan") dikenali sebagai dua langkah |
| **Pemilahan kontak** | Empat lapis penyelesaian nama, termasuk kecocokan samar dan panggilan hubungan ("telepon ayah") |

### Kendali perangkat

| Bidang | Contoh |
|---|---|
| **Sistem** | Kecerahan, WiFi, Bluetooth, senter, jangan ganggu, volume, tangkapan layar |
| **Komunikasi** | Pesan WhatsApp, Telegram (`@user`), telepon, SMS, draf email |
| **Rutinitas** | Ringkasan pagi, ringkasan agenda, urutan makro otomatis |
| **Produktivitas** | Baca & ingat isi layar, alarm, timer, pengingat, acara kalender, catatan |
| **Navigasi** | Rute Google Maps, pesan Uber/Ola |
| **Media** | Putar/jeda musik, cari YouTube, kamera |
| **Keuangan** | Pembayaran UPI, patungan tagihan, konversi mata uang |
| **Rumah pintar** | Kendali perangkat Google Home |

### Melihat layar

Tangkapan layar diambil lewat Accessibility API lalu diberikan ke LLM yang bisa melihat gambar. Di perangkat lama, jatuh kembali ke pembacaan teks dari pohon aksesibilitas.

### Ingatan empat lapis

```
┌─────────────────────────────────────────────────────────────┐
│                  Grafik Pengetahuan Pribadi                 │
├──────────────┬──────────────┬────────────────┬──────────────┤
│   Lapis 1    │   Lapis 2    │    Lapis 3     │   Lapis 4    │
│  Sementara   │  Jangka      │  Pola yang     │  Sensitif    │
│  (rencana    │  panjang     │  dipelajari    │  (terenkripsi│
│   berjalan)  │  (fakta)     │  (simpulan)    │   Keystore)  │
└──────────────┴──────────────┴────────────────┴──────────────┘
```

### Suara

Kata bangun luring — cukup ucapkan *"OpenDroid"*. Ada pengenalan ucapan untuk perintah tanpa tangan, dan suara balasan lewat TTS bawaan atau ElevenLabs.

### Jembatan ke desktop

Server MCP JSON-RPC di `127.0.0.1:8765`. Setiap permintaan wajib membawa token dari layar **Desktop bridge**, dibandingkan dengan waktu konstan, disimpan terenkripsi di Keystore. Server hanya mendengarkan di loopback kecuali pemiliknya sendiri yang menyalakan akses jaringan — dan itu berarti memberi shell perangkat ini kepada siapa pun di jaringan yang memegang token. Rinciannya di [`docs/desktop-mcp.md`](docs/desktop-mcp.md).

### Tampilan

Jetpack Compose, tiga tab — Chat, Memory, Settings — dengan bilah navigasi hitam beraksen dan lekukan bercahaya yang ikut berpindah bersama tab terpilih. Warna aksennya dipilih di Settings dan berlaku untuk seluruh aplikasi. Tipografinya tiga keluarga huruf dengan peran tetap: Plus Jakarta Sans, Montserrat, dan Poppins.

---

## Provider LLM

Lima belas provider, dengan rantai cadangan yang bisa diatur:

| Provider | Model | Jenis |
|---|---|---|
| **Codex** | gpt-5-codex, gpt-5 | Awan — masuk dengan akun ChatGPT |
| **Google Gemini** | Gemini 2.5 Flash, Pro, Nano | Awan + di perangkat |
| **Anthropic Claude** | Claude Sonnet, Opus | Awan |
| **OpenAI** | GPT-4o, o3 | Awan |
| **Groq** | LLaMA 3, Mixtral | Awan |
| **DeepSeek** | DeepSeek V3, R1 | Awan |
| **Mistral AI** | Mistral Large, Medium | Awan |
| **OpenRouter** | 200+ model lewat satu API | Awan |
| **Together AI** | Model sumber terbuka | Awan |
| **Cohere** | Command R+ | Awan |
| **Copilot API** | GPT dan Claude lewat Copilot | Awan |
| **Ollama** | Model lokal apa pun | Lokal |
| **Custom OpenAI** | Endpoint apa pun yang sesuai OpenAI | Sendiri |
| **On-Device AI** | Gemma di perangkat | Luring |
| **LiteRT-LM** | Berkas `.litertlm` / `.task` | Luring |

**Codex** masuk dengan akun ChatGPT langsung dari HP — tanpa komputer, tanpa alamat jembatan, tanpa kunci. Caranya di [`docs/face/codex-sign-in.md`](docs/face/codex-sign-in.md).

Untuk model di perangkat, ada pengunduh latar belakang dengan jeda/lanjut, pelacakan kecepatan dan perkiraan waktu, verifikasi SHA-256, serta impor berkas lokal.

---

## Memasang

### Dari rilis

Unduh APK dari [halaman Releases](https://github.com/aldokeita/opendroidface/releases). APK-nya ditandatangani kunci tetap, jadi pembaruan berikutnya bisa dipasang menimpa yang lama tanpa kehilangan data.

Sejak 30 September 2026, Android di Indonesia menolak memasang aplikasi dari pengembang yang belum terverifikasi. Jalan keluarnya ada dua, dijelaskan di [`SETUP.md`](SETUP.md).

### Dari sumber

Butuh **JDK 21** — bukan yang lebih baru — dan **Android SDK 36**. Berkas `gradle/gradle-daemon-jvm.properties` sudah membuat `./gradlew` memilih JDK 21 yang terpasang, jadi `JAVA_HOME` tidak perlu diubah.

```bash
git clone https://github.com/aldokeita/opendroidface.git
cd opendroidface
./gradlew assembleDebug
```

APK-nya keluar di `app/build/outputs/apk/debug/app-debug.apk`. Build debug memakai nama paket sendiri, jadi bisa terpasang berdampingan dengan versi rilis.

Langkah lengkapnya, termasuk kunci penandatanganan dan cara memotong rilis dari HP, ada di [`SETUP.md`](SETUP.md).

### Izin yang diminta

Saat pertama dibuka, aplikasi memandu pemberian izin berikut:

| Izin | Untuk apa |
|---|---|
| **Layanan Aksesibilitas** | Mengendalikan antarmuka, membaca layar, mengoperasikan aplikasi |
| **Ubah Setelan** | Menyalakan WiFi, Bluetooth, mengatur kecerahan |
| **Rekam Audio** | Kata bangun dan perintah suara |
| **Akses Notifikasi** | Membaca notifikasi dan membalas otomatis |
| **Kirim Notifikasi** | Status layanan latar depan |

### Mengatur LLM

Di **Settings → Brain**, pilih provider lalu isi kuncinya. Untuk Codex, cukup tekan tombol masuk. Yang paling enak untuk memulai:

- **Codex** — kalau sudah punya langganan ChatGPT
- **Gemini** — ada tingkat gratis
- **Groq** — jawabannya paling cepat
- **Ollama** atau **On-Device AI** — sepenuhnya luring

---

## Susunan kode

Arsitektur bersih dengan Dagger-Hilt:

```
com.opendroid.ai
│
├── accessibility/      Otomasi per aplikasi (WhatsApp, SMS, telepon)
├── actions/            60+ pelaksana aksi
├── core/
│   ├── agent/          AgentLoop, PlanManager, IntentClassifier, VisionEngine
│   ├── bridge/         Alamat bind server MCP
│   ├── face/           Emosi yang dipublikasikan agen
│   ├── llm/            15 provider, rantai cadangan, mesin prompt
│   │   └── codex/      Alur OAuth Codex di perangkat
│   ├── memory/         Ingatan empat lapis dan pembacaan notifikasi
│   ├── security/       Kredensial di Android Keystore
│   ├── service/        Layanan latar depan, server MCP, penerima boot
│   └── voice/          Kata bangun, pengenalan ucapan, TTS
│
├── data/
│   ├── db/             Room
│   ├── models/         Model bersama (Plan, Memory, ChatMessage)
│   └── repository/     Repositori di atas Room dan DataStore
│
├── di/                 Modul Hilt
└── ui/
    ├── bridge/         Layar Desktop bridge
    ├── face/           Wajah, hands-free, dok, galeri ekspresi
    ├── theme/          Warna, aksen, tipografi
    ├── screens/        19 layar
    ├── viewmodel/      ViewModel
    └── components/     Komponen Compose yang dipakai ulang
```

Kode wajah sengaja ditaruh di paket sendiri supaya jejaknya di berkas asal tetap kecil dan `git merge upstream/main` tetap mudah.

---

## Keamanan

Menemukan celah? Laporkan dengan bertanggung jawab — lihat [`docs/SECURITY.md`](docs/SECURITY.md).

Beberapa hal yang perlu diketahui pemakai:

- Kunci API, token Hugging Face, sesi Codex, dan token MCP semuanya disimpan sebagai envelope AES-GCM dengan kunci di Android Keystore. Tidak pernah masuk log.
- Server MCP terikat ke `127.0.0.1` secara bawaan. Memindahkannya ke `0.0.0.0` hanya bisa dilakukan pemiliknya lewat layar Desktop bridge, dan jalur jaringannya tanpa TLS.
- Provider Codex memakai langganan ChatGPT lewat jalur yang bukan API resmi. Kemungkinan besar melanggar ketentuan layanan OpenAI.

---

## Lisensi

Fork dari [yashab-cyber/opendroid](https://github.com/yashab-cyber/opendroid), berlisensi Apache-2.0.

```
Copyright 2026 OpenDroid Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

<p align="center">
  Dirawat oleh <a href="https://github.com/aldokeita"><strong>aldokeita</strong></a>
</p>

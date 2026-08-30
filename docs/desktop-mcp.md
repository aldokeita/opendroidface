# Menghubungkan desktop ke OpenDroid (MCP)

OpenDroid menjalankan server MCP (JSON-RPC 2.0 di atas HTTP) di dalam
`OpenDroidService`. Selama layanan hidup, server itu mendengarkan di port
**8765**. Lewat server ini, klien MCP di komputer bisa membaca status perangkat,
menjalankan aksi OpenDroid, membuka sesi shell, dan mengelola konfigurasi
endpoint MCP.

> Server ini bisa menjalankan perintah shell dan mengendalikan layar. Anggap
> tokennya setara kata sandi perangkat — karena memang itulah fungsinya.

## 1. Ambil token

Di aplikasi: ikon **komputer** di bar atas layar Chat → layar **Desktop bridge**.

- Token ditampilkan tersamar (`8140…610e`). Tekan **Reveal** untuk melihat penuh,
  **Copy** untuk menyalin (Android 13+ tidak menampilkan pratinjau clipboard untuk
  nilai ini).
- **Regenerate** mengganti token. Semua klien yang memakai token lama langsung
  berhenti bekerja. Lakukan ini kalau token pernah terlihat orang lain — misalnya
  masuk ke screenshot atau layar yang dibagikan.

Token disimpan sebagai envelope AES-GCM di Android Keystore, dibuat acak saat
pertama kali dibutuhkan, dan dibandingkan secara constant-time. Nilainya tidak
pernah ditulis ke log.

## 2. Pilih jalur: USB atau jaringan

### Lewat USB (dianjurkan)

Server mendengarkan di `127.0.0.1` saja. Teruskan portnya lewat adb:

```bash
adb forward tcp:8765 tcp:8765
```

Endpoint di komputer menjadi `http://127.0.0.1:8765/mcp`. Tidak ada paket yang
keluar dari kabel, dan tidak ada perubahan setelan di HP.

### Lewat jaringan

Di layar Desktop bridge, nyalakan **Reachable from your Wi-Fi**. Aplikasi
meminta konfirmasi dulu, lalu me-restart server supaya bind ke `0.0.0.0`
(alamat bind ditetapkan saat socket dibuka, jadi restart itu wajib). Layar
kemudian menampilkan URL yang bisa dipakai, misalnya
`http://192.168.1.16:8765/mcp`.

Mati secara bawaan, dan kembali ke loopback begitu switch dimatikan.

**Sebelum menyalakannya, pahami cakupannya:** siapa pun di jaringan yang sama
bisa menjangkau port itu. Yang menahan mereka hanyalah token. Kalau token bocor,
mereka mendapat shell di HP ini dan kendali penuh atas layarnya. Jangan
nyalakan di Wi-Fi publik atau kantor bersama; kalau ragu, pakai jalur USB.

Tidak ada TLS di jalur ini. Trafiknya polos, termasuk header token — satu alasan
lagi untuk membatasinya ke jaringan rumah sendiri.

## 3. Konfigurasi klien MCP

Setiap permintaan adalah `POST /mcp` dengan header:

| Header | Nilai |
|---|---|
| `Content-Type` | `application/json` |
| `X-OpenDroid-Token` | token dari layar Desktop bridge |
| `Content-Length` | wajib; badan permintaan maksimal 1 MiB |

Uji cepat (ganti `TOKEN`):

```bash
curl -s http://127.0.0.1:8765/mcp -H "X-OpenDroid-Token: TOKEN" -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

Tanpa token yang cocok, jawabannya `401 {"error":"Unauthorized"}` sebelum badan
permintaan dibaca sama sekali.

Untuk klien yang hanya bisa bicara stdio (mis. Claude Desktop), pakai jembatan
stdio→HTTP apa pun dan arahkan ke URL di atas dengan header token yang sama.

## 4. Metode JSON-RPC

| Metode | Hasil |
|---|---|
| `initialize` | `protocolVersion` `2024-11-05`, `serverInfo.name` = `opendroid` |
| `ping` | objek kosong |
| `tools/list` | daftar tool di bawah |
| `tools/call` | `params: { name, arguments }` |

Permintaan tanpa `id` (notifikasi) dijawab `202` tanpa badan.

## 5. Tool yang tersedia

Sumber: `McpServer.tools()` di `core/service/McpServer.kt`.

| Tool | Argumen wajib | Kegunaan |
|---|---|---|
| `device_info` | — | Paket, level SDK, status backend privileged, port MCP |
| `list_actions` | — | Semua action yang terdaftar di `ActionDispatcher` |
| `execute_action` | `action` (+ `params` objek) | Menjalankan satu action OpenDroid |
| `run_privileged_command` | `command` | Menjalankan perintah lewat Shizuku, root, atau app shell |
| `mcp_list_configs` | — | Konfigurasi endpoint MCP yang tersimpan |
| `mcp_configure` | `name`, `url` (+ `enabled`, `headers`) | Menambah/memperbarui endpoint |
| `mcp_remove_config` | `name` | Menghapus endpoint |
| `terminal_create` | — | Membuka sesi shell persisten |
| `terminal_write` | `sessionId`, `command` | Mengirim perintah ke sesi |
| `terminal_read` | `sessionId` | Membaca output yang sudah tersedia |
| `terminal_list` | — | Daftar sesi terbuka |
| `terminal_close` | `sessionId` | Menutup sesi |

`run_privileged_command` dan `terminal_*` bergantung pada Shizuku atau root.
Tanpa keduanya, perintah tetap jalan tapi terbatas pada shell milik aplikasi.

## 6. Kalau tidak tersambung

| Gejala | Penyebab yang paling sering |
|---|---|
| Koneksi ditolak | `OpenDroidService` mati — buka aplikasi dulu, layanan yang menjalankan server |
| `401` terus | Token salah, atau baru saja di-*regenerate*; ambil ulang dari layar Desktop bridge |
| `404` | Path selain `/mcp`, atau metode selain `POST` |
| Jalan lewat USB, mati lewat Wi-Fi | Switch jaringan mati, HP di jaringan lain, atau AP memblokir trafik antar-klien |
| `Request body is too large` | Badan permintaan melebihi 1 MiB |

Cek alamat bind yang sedang aktif:

```bash
adb shell ss -tln | grep 8765
```

`127.0.0.1:8765` berarti loopback saja; `*:8765` berarti terbuka ke jaringan.

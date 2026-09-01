# Masuk ke Codex dari HP

Provider **Codex** memakai akun ChatGPT Anda langsung dari HP. Tidak ada PC yang harus menyala,
tidak ada alamat jembatan, tidak ada token yang diketik.

## Cara pakai

1. Settings → **Brain** → Provider → **Codex**
2. Di bagian **CHATGPT ACCOUNT**, tekan **Sign in with ChatGPT**
3. Tab browser terbuka di halaman login OpenAI. Selesaikan di situ.
4. Browser kembali ke aplikasi, dan baris status berganti jadi alamat email plus nama paket
   (misalnya `owner@example.com · ChatGPT Plus`)

Kalau nanti ingin melepas akunnya, tekan **Sign out** di tempat yang sama.

## Yang terjadi di balik layar

Alur OAuth 2.0 authorization code dengan PKCE, persis yang dipakai Codex CLI:

| Bagian | Nilai |
|---|---|
| Authorize | `https://auth.openai.com/oauth/authorize` |
| Token | `https://auth.openai.com/oauth/token` |
| Client | `app_EMoamEEZ73f0CkXaXp7hrann` (klien publik, tanpa secret) |
| Redirect | `http://localhost:1455/auth/callback` |
| Scope | `openid profile email offline_access` |
| Inferensi | `https://chatgpt.com/backend-api/codex/responses` |

Port 1455 dipatok oleh registrasi aplikasi OpenAI, jadi bukan pilihan kita. Yang menangkap
redirect-nya adalah listener kecil di dalam aplikasi ini, terikat **hanya** ke alamat loopback
(`127.0.0.1` dan `::1`) selama proses login berlangsung, lalu ditutup. Tidak ada port yang
terbuka ke jaringan.

Kode di `app/src/main/java/com/opendroid/ai/core/llm/codex/`:

| Berkas | Isi |
|---|---|
| `CodexOAuth.kt` | Parameter OAuth, PKCE, parsing callback dan klaim `id_token` |
| `CodexLoopbackReceiver.kt` | Listener loopback yang menangkap redirect |
| `CodexAuthStore.kt` | Sesi tersimpan sebagai envelope AES-GCM di Keystore |
| `CodexAuthManager.kt` | Menjalankan alurnya, menyegarkan token saat mau kedaluwarsa |

Provider-nya sendiri ada di `core/llm/providers/CodexProvider.kt`. Ia bicara **Responses API**,
bukan `/v1/chat/completions`, jadi tidak berbagi transport dengan provider OpenAI lain.

## Catatan

- **Ini memakai langganan ChatGPT lewat jalur yang bukan API resmi.** Kemungkinan besar melanggar
  ketentuan layanan OpenAI. Anda sudah memilih jalur ini secara sadar; catatan ini ada supaya
  tidak terlupa.
- Access token dan refresh token disimpan terenkripsi di Keystore, tidak pernah masuk log, dan
  hanya keluar dari proses sebagai header `Authorization` di atas TLS.
- Kalau port 1455 sedang dipakai aplikasi lain, sign-in akan gagal dengan pesan yang menyebut
  port itu. Tutup aplikasi tersebut lalu coba lagi — portnya tidak bisa diganti.
- Model bawaan `gpt-5-codex`. Pilihan lain di daftar model: `gpt-5`, `codex-mini-latest`.

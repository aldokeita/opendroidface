#!/usr/bin/env node
// OpenAI-compatible bridge in front of the Codex CLI.
//
// OpenDroid's "Custom OpenAI Compatible" provider speaks POST /v1/chat/completions.
// Codex speaks `codex exec`. This translates between the two so the phone can use
// the ChatGPT account the Codex CLI is already signed in to on this machine.
//
// Node built-ins only - no npm install, nothing to keep updated.
//
//   node server.js                      (reads config from the environment)
//
//   CODEX_BRIDGE_TOKEN   required. Shared secret; the phone sends it as the
//                        provider's API key and every request must carry it.
//   CODEX_BRIDGE_PORT    default 8787
//   CODEX_BRIDGE_HOST    default 0.0.0.0, so the phone on the same Wi-Fi can reach it
//   CODEX_BIN            default "codex"
//   CODEX_MODEL          default: whatever Codex is configured to use
//   CODEX_TIMEOUT_MS     default 180000
//
// The bridge runs Codex with --sandbox read-only: a request arriving from the
// phone can read this machine but never write to it.

'use strict'

const http = require('node:http')
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const crypto = require('node:crypto')

const TOKEN = process.env.CODEX_BRIDGE_TOKEN
const PORT = Number(process.env.CODEX_BRIDGE_PORT || 8787)
const HOST = process.env.CODEX_BRIDGE_HOST || '0.0.0.0'
const CODEX_BIN = process.env.CODEX_BIN || 'codex'
const MODEL = process.env.CODEX_MODEL || ''
const TIMEOUT_MS = Number(process.env.CODEX_TIMEOUT_MS || 180000)
const MAX_BODY_BYTES = 1024 * 1024

if (!TOKEN || TOKEN.length < 16) {
  console.error(
    'CODEX_BRIDGE_TOKEN must be set to at least 16 characters.\n' +
      'It is the only thing standing between this bridge and anyone else on the network.'
  )
  process.exit(1)
}

// Codex is a single heavy process per request; running several at once mostly
// produces timeouts. Requests queue instead.
let chain = Promise.resolve()
function serialize(task) {
  const run = chain.then(task, task)
  chain = run.catch(() => {})
  return run
}

function tokenMatches(header) {
  const provided = String(header || '').replace(/^Bearer\s+/i, '')
  const a = Buffer.from(provided)
  const b = Buffer.from(TOKEN)
  // Length differences leak through timingSafeEqual's own error, so compare sizes first.
  return a.length === b.length && crypto.timingSafeEqual(a, b)
}

/** Flattens OpenAI chat messages into the single prompt `codex exec` accepts. */
function buildPrompt(body) {
  const messages = Array.isArray(body.messages) ? body.messages : []
  const parts = []

  for (const message of messages) {
    const role = message?.role || 'user'
    const content = typeof message?.content === 'string'
      ? message.content
      : Array.isArray(message?.content)
        // Vision-style content arrays: keep the text, drop the images.
        ? message.content.filter((c) => c?.type === 'text').map((c) => c.text).join('\n')
        : ''
    if (!content.trim()) continue
    if (role === 'system') parts.push(`[system]\n${content}`)
    else if (role === 'assistant') parts.push(`[assistant]\n${content}`)
    else parts.push(`[user]\n${content}`)
  }

  parts.push(
    '[instructions]\n' +
      'Answer the request above directly. Do not modify files, do not run commands, ' +
      'and do not ask follow-up questions.'
  )

  if (body.response_format?.type === 'json_object') {
    parts.push(
      'Reply with a single JSON object and nothing else: no prose, no explanation, ' +
        'no markdown code fences.'
    )
  }

  return parts.join('\n\n')
}

/**
 * Codex writes prose around JSON no matter how firmly it is asked not to, and the
 * caller's parser will reject the whole response over one stray sentence. This
 * pulls out the outermost balanced JSON object.
 */
function extractJson(text) {
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i)
  const candidate = fenced ? fenced[1] : text

  const start = candidate.indexOf('{')
  if (start === -1) return text.trim()

  let depth = 0
  let inString = false
  let escaped = false
  for (let i = start; i < candidate.length; i++) {
    const ch = candidate[i]
    if (inString) {
      if (escaped) escaped = false
      else if (ch === '\\') escaped = true
      else if (ch === '"') inString = false
      continue
    }
    if (ch === '"') inString = true
    else if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) return candidate.slice(start, i + 1)
    }
  }
  return text.trim()
}

function runCodex(prompt, wantsJson) {
  return new Promise((resolve, reject) => {
    const workdir = fs.mkdtempSync(path.join(os.tmpdir(), 'codex-bridge-'))
    const lastMessageFile = path.join(workdir, 'last-message.txt')

    const args = [
      'exec',
      '--sandbox', 'read-only',
      '--skip-git-repo-check',
      '--ephemeral',
      '--color', 'never',
      '-C', workdir,
      '-o', lastMessageFile,
    ]
    if (MODEL) args.push('--model', MODEL)
    args.push('-')

    const child = spawn(CODEX_BIN, args, { stdio: ['pipe', 'pipe', 'pipe'] })
    let stdout = ''
    let stderr = ''
    let settled = false

    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      child.kill()
      cleanup()
      reject(Object.assign(new Error(`Codex timed out after ${TIMEOUT_MS}ms`), { status: 504 }))
    }, TIMEOUT_MS)

    function cleanup() {
      clearTimeout(timer)
      try {
        fs.rmSync(workdir, { recursive: true, force: true })
      } catch {
        // A leftover temp dir is not worth failing a request over.
      }
    }

    child.stdout.on('data', (d) => { stdout += d.toString() })
    child.stderr.on('data', (d) => { stderr += d.toString() })

    child.on('error', (err) => {
      if (settled) return
      settled = true
      cleanup()
      reject(Object.assign(
        new Error(`Could not start "${CODEX_BIN}": ${err.message}`),
        { status: 500 }
      ))
    })

    child.on('close', (code) => {
      if (settled) return
      settled = true

      let last = ''
      try {
        last = fs.readFileSync(lastMessageFile, 'utf8')
      } catch {
        // Codex only writes the file when it produced a final message.
      }
      cleanup()

      if (code !== 0 && !last.trim()) {
        reject(Object.assign(
          new Error(`Codex exited with ${code}: ${(stderr || stdout).trim().slice(0, 500)}`),
          { status: 502 }
        ))
        return
      }

      const text = (last.trim() || stdout.trim())
      resolve(wantsJson ? extractJson(text) : text)
    })

    child.stdin.end(prompt)
  })
}

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(body),
  })
  res.end(body)
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks = []
    req.on('data', (chunk) => {
      size += chunk.length
      if (size > MAX_BODY_BYTES) {
        reject(Object.assign(new Error('Request body too large'), { status: 413 }))
        req.destroy()
        return
      }
      chunks.push(chunk)
    })
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    req.on('error', reject)
  })
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`)

  if (req.method === 'GET' && url.pathname === '/health') {
    sendJson(res, 200, { status: 'ok' })
    return
  }

  if (!tokenMatches(req.headers.authorization)) {
    // Logged because the usual cause is a mistyped key, and without this the
    // client just sees "key rejected" with no way to tell whether the request
    // even arrived. The token itself is never printed - only its length.
    const supplied = String(req.headers.authorization || '').replace(/^Bearer\s+/i, '')
    console.log(
      `[${new Date().toISOString()}] 401 ${req.method} ${url.pathname} ` +
        `from ${req.socket.remoteAddress}, key length ${supplied.length} (expected ${TOKEN.length})`
    )
    sendJson(res, 401, { error: { message: 'Invalid or missing bearer token', type: 'auth_error' } })
    return
  }

  if (req.method === 'GET' && url.pathname === '/v1/models') {
    sendJson(res, 200, {
      object: 'list',
      data: [{ id: MODEL || 'codex', object: 'model', owned_by: 'codex-bridge' }],
    })
    return
  }

  if (req.method !== 'POST' || url.pathname !== '/v1/chat/completions') {
    sendJson(res, 404, { error: { message: `No route for ${req.method} ${url.pathname}` } })
    return
  }

  try {
    const raw = await readBody(req)
    const body = JSON.parse(raw || '{}')
    const wantsJson = body.response_format?.type === 'json_object'
    const prompt = buildPrompt(body)
    const started = Date.now()

    console.log(`[${new Date().toISOString()}] request: ${prompt.length} chars, json=${wantsJson}`)
    const content = await serialize(() => runCodex(prompt, wantsJson))
    console.log(`  answered in ${Date.now() - started}ms, ${content.length} chars`)

    sendJson(res, 200, {
      id: `chatcmpl-${crypto.randomUUID()}`,
      object: 'chat.completion',
      created: Math.floor(Date.now() / 1000),
      model: body.model || MODEL || 'codex',
      choices: [{
        index: 0,
        message: { role: 'assistant', content },
        finish_reason: 'stop',
      }],
      // Codex does not report token counts; the caller only logs this number.
      usage: { prompt_tokens: 0, completion_tokens: 0, total_tokens: 0 },
    })
  } catch (err) {
    const status = err.status || 500
    console.error(`  failed (${status}): ${err.message}`)
    sendJson(res, status, { error: { message: err.message, type: 'codex_bridge_error' } })
  }
})

server.listen(PORT, HOST, () => {
  console.log(`codex-bridge listening on http://${HOST}:${PORT}`)
  console.log(`  codex binary: ${CODEX_BIN}${MODEL ? `, model: ${MODEL}` : ''}`)
  for (const [name, addrs] of Object.entries(os.networkInterfaces())) {
    for (const addr of addrs || []) {
      if (addr.family === 'IPv4' && !addr.internal) {
        console.log(`  reachable from this machine's LAN at http://${addr.address}:${PORT}/v1`)
      }
    }
  }
})

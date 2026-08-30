# Starts the Codex bridge for OpenDroidFace.
#
#   .\tools\codex-bridge\start.ps1
#
# On first run it generates a token and prints it. Enter that token in the app as
# the API key for the "Custom OpenAI Compatible" provider. The token is stored in
# .codex-bridge-token next to this script, which is git-ignored.

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$tokenFile = Join-Path $scriptDir '.codex-bridge-token'

if (-not (Test-Path $tokenFile)) {
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $token = [Convert]::ToBase64String($bytes).Replace('+', '-').Replace('/', '_').TrimEnd('=')
    Set-Content -Path $tokenFile -Value $token -NoNewline
    Write-Host "Generated a new bridge token." -ForegroundColor Green
}

$token = (Get-Content $tokenFile -Raw).Trim()

Write-Host ""
Write-Host "API key to enter in the app:" -ForegroundColor Cyan
Write-Host "  $token"
Write-Host ""

$env:CODEX_BRIDGE_TOKEN = $token
if (-not $env:CODEX_BRIDGE_PORT) { $env:CODEX_BRIDGE_PORT = '8787' }

node (Join-Path $scriptDir 'server.js')

# FamilyAgent One-Click Stop
$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$PidFile = Join-Path $Root ".service-pids.txt"
$RuntimePidFile = Join-Path $Root ".codex-runtime-pids.txt"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "    FamilyAgent - Stop All Services" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ── 1. Close cmd windows FIRST (triggers Ctrl+C, cascades to children) ──
Write-Host "Closing service windows..." -ForegroundColor Yellow
if (Test-Path $PidFile) {
    Get-Content $PidFile | ForEach-Object {
        if ($_ -notmatch '(\d+)$') { return }
        $procId = [int]$matches[1]
        $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($p -and $p.ProcessName -eq "cmd") {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}
# Fallback: close by title
foreach ($title in @("*AI-Service*", "*Backend*", "*Frontend*")) {
    Get-Process cmd -ErrorAction SilentlyContinue | Where-Object {
        $_.MainWindowTitle -like $title
    } | Stop-Process -Force -ErrorAction SilentlyContinue
}

if (Test-Path $RuntimePidFile) {
    Get-Content $RuntimePidFile | ForEach-Object {
        if ($_ -notmatch '(\d+)$') { return }
        $procId = [int]$matches[1]
        $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($p -and $p.ProcessName -eq "cloudflared") {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            Write-Host "       Killed cloudflared (PID $procId)" -ForegroundColor DarkYellow
        }
    }
}
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 2
Write-Host "       Windows closed" -ForegroundColor Green

# ── 2. Kill ALL python/node/java on project ports + process-name fallback ──
Write-Host "Cleaning up remaining processes..." -ForegroundColor Yellow

$ports = @(3000, 8000, 8080)
$labels = @("Frontend", "AI-Service", "Backend")
$killed = @{}

# a) netstat-based (works for most cases)
for ($i = 0; $i -lt $ports.Length; $i++) {
    $port = $ports[$i]
    $label = $labels[$i]
    $procIds = netstat -ano 2>$null | Select-String ":$port " | ForEach-Object {
        ($_ -split '\s+')[-1]
    } | Where-Object { $_ -match '^\d+$' } | Sort-Object -Unique

    foreach ($procIdStr in $procIds) {
        $procId = [int]$procIdStr
        if ($killed[$procId]) { continue }
        $killed[$procId] = $true
        $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($p) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            Write-Host "       Killed $($p.ProcessName) (PID $procId) — $label" -ForegroundColor DarkYellow
        }
    }
}

# b) Process-name brute-force (handles uvicorn child processes that inherit sockets)
# Only kills processes related to FamilyAgent project
$projectKeywords = @("familyagent", "FamilyAgent", "uvicorn", "app.main", "next", "spring-boot", "\\ai-service", "\\frontend", "\\backend")
foreach ($procName in @("python", "node", "java")) {
    Get-Process -Name $procName -ErrorAction SilentlyContinue | ForEach-Object {
        if ($killed[$_.Id]) { continue }
        $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
        $isOurs = $false
        foreach ($kw in $projectKeywords) {
            if ($cmdLine -like "*$kw*") { $isOurs = $true; break }
        }
        if ($isOurs) {
            $killed[$_.Id] = $true
            Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
            Write-Host "       Killed $procName (PID $($_.Id))" -ForegroundColor DarkYellow
        }
    }
}
Start-Sleep 0.5

# ── 3. Stop Docker ──
Write-Host ""
Write-Host "Stopping infrastructure..." -ForegroundColor Yellow
$env:Path += ";C:\Program Files\Docker\Docker\resources\bin"
(& docker compose -f "$Root\docker-compose.yml" stop 2>&1) | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "       Infrastructure stopped but containers kept (PostgreSQL, Redis, RabbitMQ, MinIO)" -ForegroundColor Green
} else {
    Write-Host "       [WARN] docker compose failed or Docker not running" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   All services stopped." -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Pause

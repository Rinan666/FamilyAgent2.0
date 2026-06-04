# FamilyAgent One-Click Start (PowerShell)
$ErrorActionPreference = "Continue"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

# Add Docker to PATH (may not be in cmd.exe PATH by default)
$env:Path += ";C:\Program Files\Docker\Docker\resources\bin"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "    FamilyAgent One-Click Start" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ── PID file for stop-all to close windows ──
$PidFile = Join-Path $Root ".service-pids.txt"
"" | Set-Content $PidFile -Force

# ── Helper: kill old process on a port, then start fresh ──
function Start-ServiceOnPort($port, $title, $workDir, $command) {
    # Kill any old process on this port (use netstat for reliable PID lookup)
    $pids = netstat -ano 2>$null | Select-String ":$port .*LISTENING" | ForEach-Object {
        ($_ -split '\s+')[-1]
    } | Where-Object { $_ -match '^\d+$' } | Sort-Object -Unique
    foreach ($pidStr in $pids) {
        $p = Get-Process -Id ([int]$pidStr) -ErrorAction SilentlyContinue
        if ($p) {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
            Write-Host "       [OK] Killed old $($p.ProcessName) (PID $($p.Id)) on port $port" -ForegroundColor DarkYellow
        }
    }
    if ($pids) { Start-Sleep 1 }
    $newProc = Start-Process cmd -ArgumentList "/k", "title $title && cd /d `"$workDir`" && $command" -PassThru
    Add-Content $PidFile $newProc.Id
}

# ── 0. Check prerequisites ──
Write-Host "[0/4] Checking prerequisites..." -ForegroundColor Yellow

$dockerOk = $true
try {
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw }
    Write-Host "       Docker ready" -ForegroundColor Green
} catch {
    Write-Host "       [WARN] Docker not detected" -ForegroundColor DarkYellow
    $dockerOk = $false
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Java not found" -ForegroundColor Red
    Pause; exit 1
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Node.js not found" -ForegroundColor Red
    Pause; exit 1
}

Write-Host "       All checks passed" -ForegroundColor Green

# ── 1. Infrastructure ──
Write-Host ""
Write-Host "[1/4] Starting infrastructure..." -ForegroundColor Yellow
if ($dockerOk) {
    docker compose -f "$Root\docker-compose.yml" up -d 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "       Containers started, waiting 15s..." -ForegroundColor Green
        Start-Sleep 15
        Write-Host "       Initializing database..." -ForegroundColor Yellow
        docker exec fa-postgres psql -U fa_user -d familyagent -f /docker-entrypoint-initdb.d/01-init.sql 2>&1 | Out-Null
        Write-Host "       Database ready" -ForegroundColor Green
    } else {
        Write-Host "       [WARN] docker compose failed" -ForegroundColor DarkYellow
    }
}

# ── 2. AI Service (port 8000) ──
Write-Host ""
Write-Host "[2/4] Starting AI Service (port 8000)..." -ForegroundColor Yellow
Start-ServiceOnPort 8000 "AI-Service" "$Root\ai-service" "echo AI Service http://localhost:8000 && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000"
Write-Host "       AI Service window opened" -ForegroundColor Green

# ── 3. Backend (port 8080) ──
Write-Host ""
Write-Host "[3/4] Starting Backend (port 8080)..." -ForegroundColor Yellow
Start-ServiceOnPort 8080 "Backend" "$Root\backend" "echo Backend compiling... first time ~1-2min && mvn spring-boot:run -Dspring-boot.run.profiles=dev"
Write-Host "       Backend window opened" -ForegroundColor Green

# ── 4. Frontend (port 3000) ──
Write-Host ""
Write-Host "[4/4] Starting Frontend (port 3000)..." -ForegroundColor Yellow
Start-ServiceOnPort 3000 "Frontend" "$Root\frontend" "echo Frontend http://localhost:3000 && npm run dev"
Write-Host "       Frontend window opened" -ForegroundColor Green

# ── Done ──
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   All services launching!" -ForegroundColor Cyan
Write-Host ""
Write-Host "   Frontend:  http://localhost:3000" -ForegroundColor Cyan
Write-Host "   Backend:   http://localhost:8080" -ForegroundColor Cyan
Write-Host "   AI API:    http://localhost:8000/docs" -ForegroundColor Cyan
Write-Host "   MinIO:     http://localhost:9001" -ForegroundColor Cyan
Write-Host "   RabbitMQ:  http://localhost:15672" -ForegroundColor Cyan
Write-Host ""
Write-Host "   Wait for Backend to show [Started]" -ForegroundColor Yellow
Write-Host "   then open the frontend URL in browser." -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Pause

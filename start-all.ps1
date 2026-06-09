# FamilyAgent One-Click Start (PowerShell)
$ErrorActionPreference = "Continue"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$AiServicePort = 8090
$BackendPort = 8180
$InfraEnvFile = Join-Path $Root ".env.infra.local"
$InfraEnvExampleFile = Join-Path $Root ".env.infra.example"

# Add Docker to PATH (may not be in cmd.exe PATH by default)
$env:Path += ";C:\Program Files\Docker\Docker\resources\bin"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "    FamilyAgent One-Click Start" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ── PID files for stop-all to close windows/processes ──
$PidFile = Join-Path $Root ".service-pids.txt"
$RuntimePidFile = Join-Path $Root ".codex-runtime-pids.txt"
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

function Set-NamedPid($name, $procId) {
    $lines = @()
    if (Test-Path $RuntimePidFile) {
        $lines = Get-Content $RuntimePidFile | Where-Object { $_ -notmatch "^$name=" }
    }
    $lines + "$name=$procId" | Set-Content $RuntimePidFile -Force
}

function Start-CloudflareTunnel {
    $cloudflared = Get-Command cloudflared.exe -ErrorAction SilentlyContinue
    $configPath = Join-Path $env:USERPROFILE ".cloudflared\config.yml"
    if (-not $cloudflared -or -not (Test-Path $configPath)) {
        Write-Host "       [SKIP] Cloudflare Tunnel not configured" -ForegroundColor DarkYellow
        return
    }

    Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    $logDir = Join-Path $Root ".codex-runtime-logs"
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    $outLog = Join-Path $logDir "cloudflared-named.out.log"
    $errLog = Join-Path $logDir "cloudflared-named.err.log"
    $proc = Start-Process -FilePath $cloudflared.Source `
        -ArgumentList @("tunnel", "--config", $configPath, "run") `
        -WindowStyle Hidden `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru
    Set-NamedPid "cloudflared-named" $proc.Id
    Write-Host "       Cloudflare Tunnel started (PID $($proc.Id))" -ForegroundColor Green
}

# ── 0. Check prerequisites ──
Write-Host "[0/5] Checking prerequisites..." -ForegroundColor Yellow

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
Write-Host "[1/5] Starting infrastructure..." -ForegroundColor Yellow
if ($dockerOk) {
    if (-not (Test-Path $InfraEnvFile)) {
        Write-Host "       [ERROR] Missing infra config: $InfraEnvFile" -ForegroundColor Red
        if (Test-Path $InfraEnvExampleFile) {
            Write-Host "       Copy $InfraEnvExampleFile to .env.infra.local and fill in local infra values." -ForegroundColor Yellow
        }
        Pause; exit 1
    }
    docker compose --env-file "$InfraEnvFile" -f "$Root\docker-compose.yml" up -d 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "       Containers started, waiting 5s..." -ForegroundColor Green
        Start-Sleep 5
        Write-Host "       Initializing database..." -ForegroundColor Yellow
        docker exec fa-postgres psql -U fa_user -d familyagent -f /docker-entrypoint-initdb.d/01-init.sql 2>&1 | Out-Null
        docker cp "$Root\scripts\migrate-multitenant-storage.sql" fa-postgres:/tmp/migrate-multitenant-storage.sql 2>&1 | Out-Null
        docker exec fa-postgres psql -U fa_user -d familyagent -f /tmp/migrate-multitenant-storage.sql 2>&1 | Out-Null
        docker cp "$Root\scripts\migrate-invite-codes.sql" fa-postgres:/tmp/migrate-invite-codes.sql 2>&1 | Out-Null
        docker exec fa-postgres psql -U fa_user -d familyagent -f /tmp/migrate-invite-codes.sql 2>&1 | Out-Null
        docker cp "$Root\scripts\migrate-wrong-question-records.sql" fa-postgres:/tmp/migrate-wrong-question-records.sql 2>&1 | Out-Null
        docker exec fa-postgres psql -U fa_user -d familyagent -f /tmp/migrate-wrong-question-records.sql 2>&1 | Out-Null
        Write-Host "       Database ready" -ForegroundColor Green
    } else {
        Write-Host "       [WARN] docker compose failed" -ForegroundColor DarkYellow
    }
}

# ── 2. AI Service (port 8090) ──
Write-Host ""
Write-Host "[2/5] Starting AI Service (port $AiServicePort)..." -ForegroundColor Yellow
Start-ServiceOnPort $AiServicePort "AI-Service" "$Root\ai-service" "echo AI Service http://localhost:$AiServicePort && set AI_SERVICE_PORT=$AiServicePort && call start.bat"
Write-Host "       AI Service window opened" -ForegroundColor Green

# ── 3. Backend (port 8180) ──
Write-Host ""
Write-Host "[3/5] Starting Backend (port $BackendPort)..." -ForegroundColor Yellow
Start-ServiceOnPort $BackendPort "Backend" "$Root\backend" "echo Backend compiling... first time ~1-2min && set SERVER_PORT=$BackendPort && .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev"
Write-Host "       Backend window opened" -ForegroundColor Green

# ── 4. Frontend (port 3000) ──
Write-Host ""
Write-Host "[4/5] Starting Frontend (port 3000)..." -ForegroundColor Yellow
Start-ServiceOnPort 3000 "Frontend" "$Root\frontend" "echo Frontend http://localhost:3000 && npm run build && npm run start"
Write-Host "       Frontend window opened" -ForegroundColor Green

# ── 5. Cloudflare Tunnel ──
Write-Host ""
Write-Host "[5/5] Starting Cloudflare Tunnel..." -ForegroundColor Yellow
Start-CloudflareTunnel

# ── Done ──
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   All services launching!" -ForegroundColor Cyan
Write-Host ""
Write-Host "   Frontend:  http://localhost:3000" -ForegroundColor Cyan
Write-Host "   Public:    https://familyagent.cn" -ForegroundColor Cyan
Write-Host "   Backend:   http://localhost:$BackendPort" -ForegroundColor Cyan
Write-Host "   AI API:    http://localhost:$AiServicePort/docs" -ForegroundColor Cyan
Write-Host "   AI Public: https://ai.familyagent.cn/ai/health" -ForegroundColor Cyan
Write-Host "   MinIO:     http://localhost:9001" -ForegroundColor Cyan
Write-Host "   RabbitMQ:  http://localhost:15672" -ForegroundColor Cyan
Write-Host ""
Write-Host "   Wait for Backend to show [Started]" -ForegroundColor Yellow
Write-Host "   then open the frontend URL in browser." -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Pause

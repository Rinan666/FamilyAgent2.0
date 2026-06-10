# FamilyAgent One-Click Start (PowerShell)
$ErrorActionPreference = "Continue"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$AiServicePort = 8090
$BackendPort = 8180
$InfraEnvFile = Join-Path $Root ".env.infra.local"
$InfraEnvExampleFile = Join-Path $Root ".env.infra.example"
$TunnelEnvFile = Join-Path $Root ".env.tunnel.local"
$TunnelExampleFile = Join-Path $Root ".env.tunnel.example"
$TunnelScript = Join-Path $Root "tunnel.ps1"
$LogDir = Join-Path $Root "logs"

# Add Docker to PATH (may not be in cmd.exe PATH by default)
$env:Path += ";C:\Program Files\Docker\Docker\resources\bin"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "    FamilyAgent One-Click Start" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

$PidFile = Join-Path $Root ".service-pids.txt"
"" | Set-Content $PidFile -Force

function Load-EnvFile([string]$Path) {
    if (-not (Test-Path $Path)) {
        return
    }

    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $parts = $line -split "=", 2
        if ($parts.Count -ne 2) {
            return
        }

        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
    }
}

function Test-Truthy([string]$Value, [bool]$Default = $false) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $Default
    }

    switch ($Value.Trim().ToLowerInvariant()) {
        "1" { return $true }
        "true" { return $true }
        "yes" { return $true }
        "on" { return $true }
        "0" { return $false }
        "false" { return $false }
        "no" { return $false }
        "off" { return $false }
        default { return $Default }
    }
}

function Get-EnvOrDefault([string]$Name, [string]$Default = "") {
    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $Default
    }
    return $value.Trim()
}

function Start-ServiceOnPort($port, $title, $workDir, $command) {
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

    if ($pids) {
        Start-Sleep 1
    }

    $newProc = Start-Process cmd -ArgumentList "/k", "title $title && cd /d `"$workDir`" && $command" -PassThru
    Add-Content $PidFile $newProc.Id
}

Write-Host "[0/5] Checking prerequisites..." -ForegroundColor Yellow

$dockerOk = $true
try {
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw
    }
    Write-Host "       Docker ready" -ForegroundColor Green
} catch {
    Write-Host "       [WARN] Docker not detected" -ForegroundColor DarkYellow
    $dockerOk = $false
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Java not found" -ForegroundColor Red
    Pause
    exit 1
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Node.js not found" -ForegroundColor Red
    Pause
    exit 1
}

Load-EnvFile $TunnelEnvFile
$StartTunnel = Test-Truthy (Get-EnvOrDefault "START_TUNNEL" "false") $false
$TunnelPublicHost = Get-EnvOrDefault "TUNNEL_PUBLIC_HOST" ""

Write-Host "       All checks passed" -ForegroundColor Green

Write-Host ""
Write-Host "[1/5] Starting infrastructure..." -ForegroundColor Yellow
if ($dockerOk) {
    if (-not (Test-Path $InfraEnvFile)) {
        Write-Host "       [ERROR] Missing infra config: $InfraEnvFile" -ForegroundColor Red
        if (Test-Path $InfraEnvExampleFile) {
            Write-Host "       Copy $InfraEnvExampleFile to .env.infra.local and fill in local infra values." -ForegroundColor Yellow
        }
        Pause
        exit 1
    }

    docker compose --env-file "$InfraEnvFile" -f "$Root\docker-compose.yml" up -d 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "       Containers started, waiting 5s..." -ForegroundColor Green
        Start-Sleep 5
        Write-Host "       Database ready (schema will be managed by backend Flyway)" -ForegroundColor Green
    } else {
        Write-Host "       [WARN] docker compose failed" -ForegroundColor DarkYellow
    }
}

Write-Host ""
Write-Host "[2/5] Starting AI Service (port $AiServicePort)..." -ForegroundColor Yellow
Start-ServiceOnPort $AiServicePort "AI-Service" "$Root\ai-service" "echo AI Service http://localhost:$AiServicePort && set AI_SERVICE_PORT=$AiServicePort && call start.bat"
Write-Host "       AI Service window opened" -ForegroundColor Green

Write-Host ""
Write-Host "[3/5] Starting Backend (port $BackendPort)..." -ForegroundColor Yellow
Start-ServiceOnPort $BackendPort "Backend" "$Root\backend" "echo Backend compiling... first time ~1-2min && set SERVER_PORT=$BackendPort && .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev"
Write-Host "       Backend window opened" -ForegroundColor Green

Write-Host ""
Write-Host "[4/5] Starting Frontend (port 3000)..." -ForegroundColor Yellow
Write-Host "       Building frontend (this may take a while)..." -ForegroundColor DarkYellow
$FrontendBuildLog = Join-Path $LogDir "frontend-build.log"
$FrontendNextDir = Join-Path $Root "frontend\.next"
$FrontendBuildId = Join-Path $FrontendNextDir "BUILD_ID"

if (Test-Path $FrontendNextDir) {
    Remove-Item $FrontendNextDir -Recurse -Force
}

Push-Location "$Root\frontend"
try {
    npm run build *>&1 | Tee-Object -FilePath $FrontendBuildLog
    if ($LASTEXITCODE -ne 0) {
        Write-Host "       [ERROR] Frontend build failed. Check $FrontendBuildLog" -ForegroundColor Red
        Pause
        exit 1
    }
} finally {
    Pop-Location
}

if (-not (Test-Path $FrontendBuildId)) {
    Write-Host "       [ERROR] Frontend build did not produce .next\BUILD_ID. Check $FrontendBuildLog" -ForegroundColor Red
    Pause
    exit 1
}

Start-ServiceOnPort 3000 "Frontend" "$Root\frontend" "echo Frontend http://localhost:3000 && npm run start"
Write-Host "       Frontend window opened" -ForegroundColor Green

Write-Host ""
Write-Host "[5/5] Starting Cloudflare Tunnel..." -ForegroundColor Yellow
if ($StartTunnel) {
    if (Test-Path $TunnelScript) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $TunnelScript up
    } else {
        Write-Host "       [WARN] Missing tunnel entry: $TunnelScript" -ForegroundColor DarkYellow
    }
} else {
    Write-Host "       [SKIP] Tunnel disabled (set START_TUNNEL=true in .env.tunnel.local to auto-start)" -ForegroundColor DarkYellow
    if (-not (Test-Path $TunnelEnvFile) -and (Test-Path $TunnelExampleFile)) {
        Write-Host "       Copy $TunnelExampleFile to .env.tunnel.local to configure Tunnel defaults." -ForegroundColor DarkYellow
    }
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   All services launching!" -ForegroundColor Cyan
Write-Host ""
Write-Host "   Frontend:  http://localhost:3000" -ForegroundColor Cyan
if ($TunnelPublicHost) {
    Write-Host "   Public:    https://$TunnelPublicHost" -ForegroundColor Cyan
}
Write-Host "   Backend:   http://localhost:$BackendPort" -ForegroundColor Cyan
Write-Host "   AI API:    http://localhost:$AiServicePort/docs" -ForegroundColor Cyan
Write-Host "   MinIO:     http://localhost:9001" -ForegroundColor Cyan
Write-Host "   RabbitMQ:  http://localhost:15672" -ForegroundColor Cyan
Write-Host ""
Write-Host "   Wait for Backend to show [Started]" -ForegroundColor Yellow
Write-Host "   then open the frontend URL in browser." -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Pause

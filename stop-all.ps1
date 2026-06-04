# FamilyAgent One-Click Stop
$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "    FamilyAgent - Stop All Services" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ── Helper: kill process listening on a port ──
function Stop-ProcessOnPort($port) {
    $line = netstat -ano 2>$null | Select-String ":$port .*LISTENING"
    if ($line) {
        $pidStr = ($line -split '\s+')[-1]
        try {
            $proc = Get-Process -Id ([int]$pidStr) -ErrorAction SilentlyContinue
            if ($proc) {
                $name = $proc.ProcessName
                Stop-Process -Id ([int]$pidStr) -Force -ErrorAction SilentlyContinue
                Write-Host "       Killed $name (PID $pidStr) on port $port" -ForegroundColor Green
                return $true
            }
        } catch {}
    }
    return $false
}

# ── 1. Kill processes on project ports ──
Write-Host "Stopping application services..." -ForegroundColor Yellow

$stopped = @()
if (Stop-ProcessOnPort 3000) { $stopped += "Frontend" }
if (Stop-ProcessOnPort 8000) { $stopped += "AI-Service" }
if (Stop-ProcessOnPort 8080) { $stopped += "Backend" }

if ($stopped.Count -gt 0) {
    Write-Host "       Stopped: $($stopped -join ', ')" -ForegroundColor Green
} else {
    Write-Host "       No application processes found on project ports" -ForegroundColor Gray
}

# ── 2. Stop Docker containers ──
Write-Host ""
Write-Host "Stopping infrastructure..." -ForegroundColor Yellow
$env:Path += ";C:\Program Files\Docker\Docker\resources\bin"
$dockerOut = (& docker compose -f "$Root\docker-compose.yml" down 2>&1) -join "`n"
if ($LASTEXITCODE -eq 0) {
    Write-Host "       Infrastructure stopped (PostgreSQL, Redis, RabbitMQ, MinIO)" -ForegroundColor Green
} else {
    Write-Host "       [WARN] docker compose failed or Docker not running" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   All services stopped." -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Pause

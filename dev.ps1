param(
    [Parameter(Position = 0)]
    [string]$Command = "help",
    [Parameter(Position = 1)]
    [string]$Subcommand = "",
    [switch]$Follow
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $Root "scripts\dev-runtime.ps1")

$ProjectEnvFile = Join-Path $Root ".env"
$ProjectEnvExampleFile = Join-Path $Root ".env.example"
$RuntimePidFile = Join-Path $Root ".codex-runtime-pids.txt"
$LegacyPidFile = Join-Path $Root ".service-pids.txt"
$LogDir = Join-Path $Root "logs"
$TunnelLogOut = Join-Path $LogDir "cloudflared-tunnel.out.log"
$TunnelLogErr = Join-Path $LogDir "cloudflared-tunnel.err.log"
$TunnelPidName = "tunnel"

function Show-Usage {
    Write-Host "Usage:" -ForegroundColor Cyan
    Write-Host "  .\dev.ps1 start"
    Write-Host "  .\dev.ps1 stop"
    Write-Host "  .\dev.ps1 tunnel up"
    Write-Host "  .\dev.ps1 tunnel down"
    Write-Host "  .\dev.ps1 tunnel status"
    Write-Host "  .\dev.ps1 tunnel logs [-Follow]"
}

function Fail-AndExit([string]$Message, [string]$Hint = "") {
    Write-Host $Message -ForegroundColor Red
    if ($Hint) {
        Write-Host "       $Hint" -ForegroundColor Yellow
    }
    exit 1
}

function Invoke-DockerComposeSilently([string]$Arguments) {
    $quoted = "docker compose $Arguments >nul 2>&1"
    cmd /c $quoted | Out-Null
    return $LASTEXITCODE
}

function Wait-ForTcpPort([string]$TargetHost, [int]$Port, [int]$TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $client = $null
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $async = $client.BeginConnect($TargetHost, $Port, $null, $null)
            if ($async.AsyncWaitHandle.WaitOne(1000) -and $client.Connected) {
                return $true
            }
        } catch {
        } finally {
            if ($client) {
                $client.Close()
            }
        }

        Start-Sleep -Seconds 1
    }

    return $false
}

function Start-ServiceWindow([string]$Name, [int]$Port, [string]$Title, [string]$WorkDir, [string]$CommandText) {
    $existingPids = Get-ListeningProcessIds $Port
    foreach ($process in Stop-Processes $existingPids) {
        Write-Host "       [OK] Killed old $($process.ProcessName) (PID $($process.Id)) on port $Port" -ForegroundColor DarkYellow
    }

    if ($existingPids.Count -gt 0) {
        Start-Sleep -Seconds 1
    }

    $window = Start-Process cmd -ArgumentList "/k", "title $Title && cd /d `"$WorkDir`" && $CommandText" -PassThru
    Set-RuntimePidEntry $RuntimePidFile "$Name-window" $window.Id
}

function Stop-TrackedWindow([string]$Name, [string]$TitlePattern) {
    $runtimeKey = "$Name-window"
    $trackedPid = Get-RuntimePidEntry $RuntimePidFile $runtimeKey
    if (Test-ProcessAlive $trackedPid) {
        Stop-Process -Id $trackedPid -Force -ErrorAction SilentlyContinue
    }

    Remove-RuntimePidEntry $RuntimePidFile $runtimeKey
    Get-Process cmd -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like $TitlePattern } |
        Stop-Process -Force -ErrorAction SilentlyContinue
}

function Stop-ProjectProcesses([hashtable]$Seen, [string[]]$ProcessNames, [string[]]$Keywords) {
    foreach ($processName in $ProcessNames) {
        Get-Process -Name $processName -ErrorAction SilentlyContinue | ForEach-Object {
            if ($Seen[$_.Id]) {
                return
            }

            $commandLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
            if (-not $commandLine) {
                return
            }

            foreach ($keyword in $Keywords) {
                if ($commandLine -like "*$keyword*") {
                    $Seen[$_.Id] = $true
                    Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
                    Write-Host "       Killed $processName (PID $($_.Id))" -ForegroundColor DarkYellow
                    break
                }
            }
        }
    }
}

function Get-CloudflaredCommand() {
    return Get-Command cloudflared.exe -ErrorAction SilentlyContinue
}

function Get-ResolvedTunnelConfigPath() {
    $defaultConfigPath = Join-Path $env:USERPROFILE ".cloudflared\config.yml"
    return Get-ConfigValue "TUNNEL_CONFIG_PATH" $defaultConfigPath
}

function Find-CloudflaredPid() {
    $storedPid = Get-RuntimePidEntry $RuntimePidFile $TunnelPidName
    if ($storedPid -and (Test-ProcessAlive $storedPid)) {
        return $storedPid
    }

    $configPath = Get-ResolvedTunnelConfigPath
    $escapedConfigPath = [Regex]::Escape($configPath)
    $processes = Get-CimInstance Win32_Process -Filter "Name = 'cloudflared.exe'" -ErrorAction SilentlyContinue
    foreach ($process in $processes) {
        $commandLine = $process.CommandLine
        if ($commandLine -match "tunnel" -and $commandLine -match $escapedConfigPath) {
            return [int]$process.ProcessId
        }
    }

    return $null
}

function Get-TunnelConfig() {
    Load-ProjectEnv $ProjectEnvFile

    return [ordered]@{
        enabled = Test-Truthy (Get-ConfigValue "TUNNEL_ENABLED" "true") $true
        provider = Get-ConfigValue "TUNNEL_PROVIDER" "cloudflare"
        mode = Get-ConfigValue "TUNNEL_MODE" "named"
        configPath = Get-ResolvedTunnelConfigPath
        publicHost = Get-ConfigValue "TUNNEL_PUBLIC_HOST" ""
        targetUrl = Get-ConfigValue "TUNNEL_TARGET_URL" "http://127.0.0.1:3000"
    }
}

function Write-TunnelStatus([System.Collections.IDictionary]$Config) {
    $tunnelProcessId = Find-CloudflaredPid
    $state = "stopped"
    if (-not $Config.enabled) {
        $state = "disabled"
    } elseif (-not (Test-Path $Config.configPath)) {
        $state = "not_configured"
    } elseif ($tunnelProcessId) {
        $state = "running"
    }

    Write-Output "provider=$($Config.provider)"
    Write-Output "mode=$($Config.mode)"
    Write-Output "enabled=$($Config.enabled.ToString().ToLowerInvariant())"
    Write-Output "state=$state"
    Write-Output "pid=$(if ($tunnelProcessId) { $tunnelProcessId } else { '' })"
    Write-Output "public_host=$($Config.publicHost)"
    Write-Output "target_url=$($Config.targetUrl)"
    Write-Output "config_path=$($Config.configPath)"
    Write-Output "log_out=$TunnelLogOut"
    Write-Output "log_err=$TunnelLogErr"
}

function Ensure-TunnelPreconditions([System.Collections.IDictionary]$Config) {
    if (-not $Config.enabled) {
        throw "Tunnel is disabled. Set TUNNEL_ENABLED=true in .env or your shell."
    }
    if ($Config.provider -ne "cloudflare") {
        throw "Unsupported tunnel provider: $($Config.provider). Only Cloudflare is supported."
    }
    if ($Config.mode -ne "named") {
        throw "Unsupported tunnel mode: $($Config.mode). Only named tunnels are supported."
    }

    $cloudflared = Get-CloudflaredCommand
    if (-not $cloudflared) {
        throw "cloudflared.exe was not found on PATH."
    }
    if (-not (Test-Path $Config.configPath)) {
        throw "Tunnel config not found: $($Config.configPath)"
    }

    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
    return $cloudflared
}

function Invoke-Tunnel([string]$Action) {
    $config = Get-TunnelConfig

    switch ($Action) {
        "up" {
            $cloudflared = Ensure-TunnelPreconditions $config
            $existingPid = Find-CloudflaredPid
            if ($existingPid) {
                Set-RuntimePidEntry $RuntimePidFile $TunnelPidName $existingPid
                Write-Host "Tunnel already running (PID $existingPid)" -ForegroundColor Yellow
                Write-TunnelStatus $config
                return
            }

            $proc = Start-Process -FilePath $cloudflared.Source `
                -ArgumentList @("tunnel", "--config", $config.configPath, "run") `
                -WindowStyle Hidden `
                -RedirectStandardOutput $TunnelLogOut `
                -RedirectStandardError $TunnelLogErr `
                -PassThru

            Start-Sleep -Seconds 1
            Set-RuntimePidEntry $RuntimePidFile $TunnelPidName $proc.Id
            Write-Host "Tunnel started (PID $($proc.Id))" -ForegroundColor Green
            Write-TunnelStatus $config
        }
        "down" {
            $tunnelProcessId = Find-CloudflaredPid
            if (-not $tunnelProcessId) {
                Remove-RuntimePidEntry $RuntimePidFile $TunnelPidName
                Write-Host "Tunnel already stopped" -ForegroundColor Yellow
                Write-TunnelStatus $config
                return
            }

            Stop-Process -Id $tunnelProcessId -Force -ErrorAction SilentlyContinue
            Start-Sleep -Milliseconds 500
            Remove-RuntimePidEntry $RuntimePidFile $TunnelPidName
            Write-Host "Tunnel stopped (PID $tunnelProcessId)" -ForegroundColor Green
            Write-TunnelStatus $config
        }
        "status" {
            Write-TunnelStatus $config
        }
        "logs" {
            Write-TunnelStatus $config
            foreach ($path in @($TunnelLogOut, $TunnelLogErr)) {
                Write-Host ""
                Write-Host "==> $path <==" -ForegroundColor Cyan
                if (-not (Test-Path $path)) {
                    Write-Host "(missing)"
                    continue
                }

                if ($Follow) {
                    Get-Content $path -Wait
                    continue
                }

                Get-Content $path -Tail 40
            }
        }
        default {
            throw "Unsupported tunnel action: $Action"
        }
    }
}

function Invoke-StartAll {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host "    FamilyAgent One-Click Start" -ForegroundColor Cyan
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host ""

    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    Add-DockerToPath
    Remove-Item $LegacyPidFile -Force -ErrorAction SilentlyContinue

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
        Fail-AndExit "[ERROR] Java not found"
    }

    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        Fail-AndExit "[ERROR] Node.js not found"
    }

    if (-not (Test-Path $ProjectEnvFile)) {
        $hint = ""
        if (Test-Path $ProjectEnvExampleFile) {
            $hint = "Copy $ProjectEnvExampleFile to .env and fill in local values."
        }
        Fail-AndExit "[ERROR] Missing shared env file: $ProjectEnvFile" $hint
    }

    Load-ProjectEnv $ProjectEnvFile
    $AiServicePort = [int](Get-ConfigValue "AI_SERVICE_PORT" "8000")
    $BackendPort = [int](Get-ConfigValue "SERVER_PORT" "8080")
    $DbPort = [int](Get-ConfigValue "DB_PORT" "5432")
    $StartTunnel = Test-Truthy (Get-ConfigValue "START_TUNNEL" "false") $false
    $TunnelPublicHost = Get-ConfigValue "TUNNEL_PUBLIC_HOST" ""

    Write-Host "       All checks passed" -ForegroundColor Green

    Write-Host ""
    Write-Host "[1/5] Starting infrastructure..." -ForegroundColor Yellow
    if ($dockerOk) {
        $composeExit = Invoke-DockerComposeSilently "--env-file `"$ProjectEnvFile`" -f `"$Root\docker-compose.yml`" up -d"
        if ($composeExit -eq 0) {
            Write-Host "       Containers started, waiting for PostgreSQL on port $DbPort..." -ForegroundColor Green
            if (-not (Wait-ForTcpPort "127.0.0.1" $DbPort 60)) {
                Fail-AndExit "[ERROR] PostgreSQL did not become reachable on port $DbPort within 60s."
            }
            Write-Host "       Database ready (schema will be managed by backend Flyway)" -ForegroundColor Green
        } else {
            Write-Host "       [WARN] docker compose failed" -ForegroundColor DarkYellow
        }
    }

    Write-Host ""
    Write-Host "[2/5] Starting AI Service (port $AiServicePort)..." -ForegroundColor Yellow
    Start-ServiceWindow "ai" $AiServicePort "AI-Service" "$Root\ai-service" "echo AI Service http://localhost:$AiServicePort && set AI_SERVICE_PORT=$AiServicePort && call start.bat"
    Write-Host "       AI Service window opened" -ForegroundColor Green

    Write-Host ""
    Write-Host "[3/5] Starting Backend (port $BackendPort)..." -ForegroundColor Yellow
    Start-ServiceWindow "backend" $BackendPort "Backend" "$Root\backend" "echo Backend compiling... first time ~1-2min && set SERVER_PORT=$BackendPort && .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev"
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
        $buildErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        npm run build *>&1 | Tee-Object -FilePath $FrontendBuildLog
        $frontendBuildExitCode = $LASTEXITCODE
        $ErrorActionPreference = $buildErrorActionPreference
        if ($frontendBuildExitCode -ne 0) {
            Fail-AndExit "       [ERROR] Frontend build failed. Check $FrontendBuildLog"
        }
    } finally {
        if ($buildErrorActionPreference) {
            $ErrorActionPreference = $buildErrorActionPreference
        }
        Pop-Location
    }

    if (-not (Test-Path $FrontendBuildId)) {
        Fail-AndExit "       [ERROR] Frontend build did not produce .next\BUILD_ID. Check $FrontendBuildLog"
    }

    Start-ServiceWindow "frontend" 3000 "Frontend" "$Root\frontend" "echo Frontend http://localhost:3000 && npm run start"
    Write-Host "       Frontend window opened" -ForegroundColor Green

    Write-Host ""
    Write-Host "[5/5] Starting Cloudflare Tunnel..." -ForegroundColor Yellow
    if ($StartTunnel) {
        Invoke-Tunnel "up"
    } else {
        Write-Host "       [SKIP] Tunnel disabled (set START_TUNNEL=true in .env to auto-start)" -ForegroundColor DarkYellow
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
}

function Invoke-StopAll {
    Load-ProjectEnv $ProjectEnvFile
    $AiServicePort = [int](Get-ConfigValue "AI_SERVICE_PORT" "8000")
    $BackendPort = [int](Get-ConfigValue "SERVER_PORT" "8080")
    $Services = @(
        @{ Name = "ai"; Title = "*AI-Service*"; Port = $AiServicePort; Label = "AI-Service" },
        @{ Name = "backend"; Title = "*Backend*"; Port = $BackendPort; Label = "Backend" },
        @{ Name = "frontend"; Title = "*Frontend*"; Port = 3000; Label = "Frontend" }
    )

    Write-Host ""
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host "    FamilyAgent - Stop All Services" -ForegroundColor Cyan
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host ""

    Write-Host "Closing service windows..." -ForegroundColor Yellow
    foreach ($service in $Services) {
        Stop-TrackedWindow $service.Name $service.Title
    }

    Invoke-Tunnel "down" | Out-Null

    Start-Sleep -Seconds 2
    Write-Host "       Windows closed" -ForegroundColor Green

    Write-Host "Cleaning up remaining processes..." -ForegroundColor Yellow
    $killed = @{}
    foreach ($service in $Services) {
        foreach ($process in Stop-Processes (Get-ListeningProcessIds $service.Port)) {
            if ($killed[$process.Id]) {
                continue
            }

            $killed[$process.Id] = $true
            Write-Host "       Killed $($process.ProcessName) (PID $($process.Id)) - $($service.Label)" -ForegroundColor DarkYellow
        }
    }

    $projectKeywords = @("familyagent", "FamilyAgent", "uvicorn", "app.main", "next", "spring-boot", "\ai-service", "\frontend", "\backend")
    Stop-ProjectProcesses $killed @("python", "node", "java") $projectKeywords

    Start-Sleep -Milliseconds 500

    Write-Host ""
    Write-Host "Stopping infrastructure..." -ForegroundColor Yellow
    Add-DockerToPath
    $composeExit = Invoke-DockerComposeSilently "-f `"$Root\docker-compose.yml`" stop"
    if ($composeExit -eq 0) {
        Write-Host "       Infrastructure stopped but containers kept (PostgreSQL, Redis, RabbitMQ, MinIO)" -ForegroundColor Green
    } else {
        Write-Host "       [WARN] docker compose failed or Docker not running" -ForegroundColor DarkYellow
    }

    Remove-Item $LegacyPidFile -Force -ErrorAction SilentlyContinue

    Write-Host ""
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host "   All services stopped." -ForegroundColor Cyan
    Write-Host "============================================" -ForegroundColor Cyan
}

switch ($Command.ToLowerInvariant()) {
    "start" {
        Invoke-StartAll
    }
    "stop" {
        Invoke-StopAll
    }
    "tunnel" {
        $action = if ($Subcommand) { $Subcommand.ToLowerInvariant() } else { "status" }
        if ($action -notin @("up", "down", "status", "logs")) {
            Fail-AndExit "[ERROR] Unsupported tunnel action: $Subcommand"
        }
        Invoke-Tunnel $action
    }
    "help" {
        Show-Usage
    }
    default {
        Fail-AndExit "[ERROR] Unsupported command: $Command"
    }
}

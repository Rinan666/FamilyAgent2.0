param(
    [Parameter(Position = 0)]
    [ValidateSet("up", "down", "status", "logs")]
    [string]$Action = "status",
    [switch]$Follow
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$TunnelEnvFile = Join-Path $Root ".env.tunnel.local"
$RuntimePidFile = Join-Path $Root ".codex-runtime-pids.txt"
$LogDir = Join-Path $Root "logs"
$TunnelLogOut = Join-Path $LogDir "cloudflared-tunnel.out.log"
$TunnelLogErr = Join-Path $LogDir "cloudflared-tunnel.err.log"
$TunnelPidName = "tunnel"

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

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Get-ConfigValue([string]$Name, [string]$Default = "") {
    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $Default
    }
    return $value.Trim()
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

function Get-RuntimePid([string]$Name) {
    if (-not (Test-Path $RuntimePidFile)) {
        return $null
    }

    foreach ($line in Get-Content $RuntimePidFile) {
        if ($line -match "^$Name=(\d+)$") {
            return [int]$matches[1]
        }
    }

    return $null
}

function Set-RuntimePid([string]$Name, [int]$ProcessId) {
    $lines = @()
    if (Test-Path $RuntimePidFile) {
        $lines = Get-Content $RuntimePidFile | Where-Object { $_ -notmatch "^$Name=" }
    }
    $lines + "$Name=$ProcessId" | Set-Content $RuntimePidFile -Force
}

function Remove-RuntimePid([string]$Name) {
    if (-not (Test-Path $RuntimePidFile)) {
        return
    }

    $lines = Get-Content $RuntimePidFile | Where-Object { $_ -notmatch "^$Name=" }
    if ($lines.Count -eq 0) {
        Remove-Item $RuntimePidFile -Force -ErrorAction SilentlyContinue
        return
    }
    $lines | Set-Content $RuntimePidFile -Force
}

function Get-CloudflaredCommand() {
    return Get-Command cloudflared.exe -ErrorAction SilentlyContinue
}

function Test-Alive([int]$ProcessId) {
    if (-not $ProcessId) {
        return $false
    }
    return $null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Find-CloudflaredPid() {
    $storedPid = Get-RuntimePid $TunnelPidName
    if ($storedPid -and (Test-Alive $storedPid)) {
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

function Get-ResolvedTunnelConfigPath() {
    $defaultConfigPath = Join-Path $env:USERPROFILE ".cloudflared\config.yml"
    return Get-ConfigValue "TUNNEL_CONFIG_PATH" $defaultConfigPath
}

function Get-TunnelConfig() {
    Load-EnvFile $TunnelEnvFile

    return [ordered]@{
        enabled = Test-Truthy (Get-ConfigValue "TUNNEL_ENABLED" "true") $true
        provider = Get-ConfigValue "TUNNEL_PROVIDER" "cloudflare"
        mode = Get-ConfigValue "TUNNEL_MODE" "named"
        configPath = Get-ResolvedTunnelConfigPath
        publicHost = Get-ConfigValue "TUNNEL_PUBLIC_HOST" ""
        targetUrl = Get-ConfigValue "TUNNEL_TARGET_URL" "http://127.0.0.1:3000"
    }
}

function Write-Status([hashtable]$Config) {
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

function Ensure-TunnelPreconditions([hashtable]$Config) {
    if (-not $Config.enabled) {
        throw "Tunnel is disabled. Set TUNNEL_ENABLED=true in .env.tunnel.local or your shell."
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

function Start-Tunnel {
    $config = Get-TunnelConfig
    $cloudflared = Ensure-TunnelPreconditions $config
    $existingPid = Find-CloudflaredPid
    if ($existingPid) {
        Set-RuntimePid $TunnelPidName $existingPid
        Write-Host "Tunnel already running (PID $existingPid)" -ForegroundColor Yellow
        Write-Status $config
        return
    }

    $proc = Start-Process -FilePath $cloudflared.Source `
        -ArgumentList @("tunnel", "--config", $config.configPath, "run") `
        -WindowStyle Hidden `
        -RedirectStandardOutput $TunnelLogOut `
        -RedirectStandardError $TunnelLogErr `
        -PassThru

    Start-Sleep -Seconds 1
    Set-RuntimePid $TunnelPidName $proc.Id
    Write-Host "Tunnel started (PID $($proc.Id))" -ForegroundColor Green
    Write-Status $config
}

function Stop-Tunnel {
    $config = Get-TunnelConfig
    $tunnelProcessId = Find-CloudflaredPid
    if (-not $tunnelProcessId) {
        Remove-RuntimePid $TunnelPidName
        Write-Host "Tunnel already stopped" -ForegroundColor Yellow
        Write-Status $config
        return
    }

    Stop-Process -Id $tunnelProcessId -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 500
    Remove-RuntimePid $TunnelPidName
    Write-Host "Tunnel stopped (PID $tunnelProcessId)" -ForegroundColor Green
    Write-Status $config
}

function Show-TunnelLogs {
    $config = Get-TunnelConfig
    Write-Status $config
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

switch ($Action) {
    "up" { Start-Tunnel }
    "down" { Stop-Tunnel }
    "status" { Write-Status (Get-TunnelConfig) }
    "logs" { Show-TunnelLogs }
}

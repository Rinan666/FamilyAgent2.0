# Shared helpers for local dev scripts.

function Load-ProjectEnv([string]$Path) {
    if (-not (Test-Path $Path)) {
        return
    }

    foreach ($rawLine in Get-Content $Path) {
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            continue
        }

        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $name = $matches[1]
            $value = $matches[2].Trim()
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
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

function Add-DockerToPath {
    $dockerBin = "C:\Program Files\Docker\Docker\resources\bin"
    $pathEntries = $env:Path -split ";"
    if ((Test-Path $dockerBin) -and -not ($pathEntries -contains $dockerBin)) {
        $env:Path = "$env:Path;$dockerBin"
    }
}

function Get-ListeningProcessIds([int]$Port) {
    return @(
        netstat -ano 2>$null |
            Select-String ":$Port .*LISTENING" |
            ForEach-Object { ($_ -split '\s+')[-1] } |
            Where-Object { $_ -match '^\d+$' } |
            ForEach-Object { [int]$_ } |
            Sort-Object -Unique
    )
}

function Stop-Processes([int[]]$ProcessIds) {
    $stopped = @()
    foreach ($processId in ($ProcessIds | Sort-Object -Unique)) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $process) {
            continue
        }

        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        $stopped += $process
    }

    return $stopped
}

function Test-ProcessAlive([int]$ProcessId) {
    if (-not $ProcessId) {
        return $false
    }

    return $null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Read-RuntimeState([string]$Path) {
    $state = @{}
    if (-not (Test-Path $Path)) {
        return $state
    }

    foreach ($line in Get-Content $Path) {
        if ($line -match '^([^=]+)=(\d+)$') {
            $state[$matches[1]] = [int]$matches[2]
        }
    }

    return $state
}

function Write-RuntimeState([string]$Path, [hashtable]$State) {
    $lines = @(
        $State.Keys |
            Sort-Object |
            ForEach-Object { "$_=$($State[$_])" }
    )

    if ($lines.Count -eq 0) {
        Remove-Item $Path -Force -ErrorAction SilentlyContinue
        return
    }

    $lines | Set-Content $Path -Force
}

function Get-RuntimePidEntry([string]$Path, [string]$Name) {
    $state = Read-RuntimeState $Path
    if ($state.ContainsKey($Name)) {
        return [int]$state[$Name]
    }

    return $null
}

function Set-RuntimePidEntry([string]$Path, [string]$Name, [int]$ProcessId) {
    $state = Read-RuntimeState $Path
    $state[$Name] = $ProcessId
    Write-RuntimeState $Path $state
}

function Remove-RuntimePidEntry([string]$Path, [string]$Name) {
    $state = Read-RuntimeState $Path
    $null = $state.Remove($Name)
    Write-RuntimeState $Path $state
}

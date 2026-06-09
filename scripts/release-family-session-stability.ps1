param(
    [switch]$SkipBackendTests,
    [switch]$SkipFrontendLint,
    [switch]$SkipFrontendTypecheck
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"

function Invoke-Step {
    param(
        [string]$Title,
        [scriptblock]$Action
    )

    Write-Host ""
    Write-Host "== $Title ==" -ForegroundColor Cyan
    & $Action
}

if (-not $SkipBackendTests) {
    Invoke-Step "Backend tests" {
        Push-Location $backendDir
        try {
            .\mvnw.cmd test
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipFrontendLint) {
    Invoke-Step "Frontend lint" {
        Push-Location $frontendDir
        try {
            npm run lint
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipFrontendTypecheck) {
    Invoke-Step "Frontend typecheck" {
        Push-Location $frontendDir
        try {
            npx tsc --noEmit
        } finally {
            Pop-Location
        }
    }
}

Write-Host ""
Write-Host "Preprod manual checklist" -ForegroundColor Yellow
Write-Host "1. Run scripts/migrate-chat-session-storage.sql in preprod before starting backend."
Write-Host "2. Confirm backend non-dev config keeps familyagent.session-schema.auto-init=false."
Write-Host "3. Start backend and verify startup only logs family lifecycle audit warnings; no family dissolve occurs."
Write-Host "4. Run scripts/preprod-family-session-stability-check.sql and inspect suspicious families / archive ranges."
Write-Host "5. Restore one long chat session in the UI and confirm archived + live messages appear as one timeline."
Write-Host "6. Delete a normal user from admin page and confirm explicit delete flow still works."
Write-Host "7. Attempt to delete an ADMIN user and confirm the request is rejected."

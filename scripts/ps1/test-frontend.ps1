param(
    [ValidateSet("fast", "full", "release")]
    [string]$Mode = "fast",
    [switch]$RunE2E
)

$ErrorActionPreference = "Stop"
$scriptStart = [System.Diagnostics.Stopwatch]::StartNew()
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
$frontendDir = Join-Path $repoRoot "new_frontend"

function Invoke-FrontendStep {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Command
    )
    Write-Host "[test-frontend] $Name ..."
    & powershell -NoProfile -Command $Command
    if ($LASTEXITCODE -ne 0) {
        throw "[test-frontend] $Name 失败，退出码: $LASTEXITCODE"
    }
}

Write-Host "[test-frontend] mode: $Mode"
Write-Host "[test-frontend] dir:  $frontendDir"

Push-Location $frontendDir
try {
    switch ($Mode) {
        "fast" {
            Invoke-FrontendStep -Name "gate:fast" -Command "pnpm gate:fast"
        }
        "full" {
            Invoke-FrontendStep -Name "gate:full" -Command "pnpm gate:full"
        }
        "release" {
            Invoke-FrontendStep -Name "gate:release" -Command "pnpm gate:release"
        }
    }

    if ($RunE2E) {
        Invoke-FrontendStep -Name "e2e" -Command "pnpm e2e"
    }
}
finally {
    Pop-Location
    $scriptStart.Stop()
    Write-Host ("[test-frontend] elapsed: {0} ms ({1:n1} s)" -f $scriptStart.ElapsedMilliseconds, $scriptStart.Elapsed.TotalSeconds)
}

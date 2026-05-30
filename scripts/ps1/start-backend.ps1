param(
    [string]$EnvFile = ".env",
    [switch]$RunInCurrentShell
)

$ErrorActionPreference = "Stop"
$script:StartBackendDebug = [System.Environment]::GetEnvironmentVariable("MPFM_START_BACKEND_DEBUG", "Process")

function Write-DebugLog {
    param([string]$Message)
    if (-not [string]::IsNullOrWhiteSpace($script:StartBackendDebug)) {
        Write-Host "[start-backend][debug] $Message"
    }
}

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Env file not found: $Path"
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) {
            return
        }

        $pair = $line -split "=", 2
        if ($pair.Length -ne 2) {
            return
        }

        $key = $pair[0].Trim()
        $val = $pair[1]
        [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$envPath = Join-Path $repoRoot $EnvFile
Import-DotEnv -Path $envPath

function Get-EnvBool {
    param(
        [string]$Name,
        [bool]$Default = $false
    )

    $raw = [System.Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $Default
    }

    $normalized = $raw.Trim().ToLowerInvariant()
    return @("1", "true", "yes", "on").Contains($normalized)
}

function Resolve-BackendEndpoint {
    $tlsEnabled = Get-EnvBool -Name "MPFM_TLS_ENABLED" -Default $false
    $scheme = if ($tlsEnabled) { "https" } else { "http" }
    $defaultPort = if ($tlsEnabled) { 8443 } else { 8080 }
    $portRaw = [System.Environment]::GetEnvironmentVariable("MPFM_SERVER_PORT", "Process")
    $port = $defaultPort
    if (-not [string]::IsNullOrWhiteSpace($portRaw) -and ($portRaw -as [int])) {
        $port = [int]$portRaw
    }

    return @{
        Scheme = $scheme
        Port = $port
    }
}

function Clear-ServerPort {
    param([int]$Port)

    $ownerPids = @()
    $detectPath = "none"
    function Get-ListeningPidsByNetstat {
        param([int]$TargetPort)
        $hits = @()
        $rows = netstat -ano -p tcp | Select-String -Pattern "LISTENING|侦听"
        foreach ($row in $rows) {
            $parts = ($row.Line -replace "\s+", " ").Trim().Split(" ")
            if ($parts.Length -lt 5) { continue }
            $local = $parts[1]
            $pidText = $parts[$parts.Length - 1]
            if ($local -match ":(\d+)$" -and [int]$matches[1] -eq $TargetPort -and $pidText -match "^\d+$") {
                $hits += [int]$pidText
            }
        }
        return @($hits | Select-Object -Unique)
    }

    try {
        # 优先使用系统 API，避免依赖 netstat 在不同语言环境下的状态文本。
        $ownerPids = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            Select-Object -ExpandProperty OwningProcess -Unique
        $detectPath = "Get-NetTCPConnection"
    }
    catch {
        $detectPath = "Get-NetTCPConnection(failed)->netstat"
    }

    # 关键修复：主路径“成功但返回空”也要回退 netstat，避免误判端口空闲。
    if (@($ownerPids).Count -eq 0) {
        $fallbackPids = Get-ListeningPidsByNetstat -TargetPort $Port
        if ($fallbackPids.Count -gt 0) {
            $ownerPids = $fallbackPids
            $detectPath = "$detectPath->netstat(non-empty)"
        } elseif ($detectPath -like "Get-NetTCPConnection*") {
            $detectPath = "$detectPath->netstat(empty)"
        }
    }

    $ownerPids = @($ownerPids | Select-Object -Unique)
    Write-DebugLog "detectPath=$detectPath ownerPids=$($ownerPids -join ',') selfPid=$PID"
    if ($ownerPids.Count -eq 0) {
        Write-Host "[start-backend] port $Port is free."
        return
    }
    foreach ($ownerPid in $ownerPids) {
        if ($ownerPid -and $ownerPid -ne $PID) {
            try {
                Stop-Process -Id $ownerPid -Force -ErrorAction Stop
                Write-Host "[start-backend] stopped process on ${Port}: pid=$ownerPid"
            }
            catch {
                Write-Host "[start-backend] failed to stop pid=${ownerPid}: $($_.Exception.Message)"
            }
        }
    }

    # 终止后复核端口是否仍被占用；若未释放则中止启动，避免继续拉起新窗口造成误判。
    Start-Sleep -Milliseconds 300
    $remainingPids = @()
    try {
        $remainingPids = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            Select-Object -ExpandProperty OwningProcess -Unique
    }
    catch {
        $rows = netstat -ano -p tcp | Select-String -Pattern "LISTENING|侦听"
        foreach ($row in $rows) {
            $parts = ($row.Line -replace "\s+", " ").Trim().Split(" ")
            if ($parts.Length -lt 5) { continue }
            $local = $parts[1]
            $pidText = $parts[$parts.Length - 1]
            if ($local -match ":(\d+)$" -and [int]$matches[1] -eq $Port -and $pidText -match "^\d+$") {
                $remainingPids += [int]$pidText
            }
        }
    }
    $remainingPids = @($remainingPids | Select-Object -Unique | Where-Object { $_ -and $_ -ne $PID })
    Write-DebugLog "recheck remainingPids=$($remainingPids -join ',') selfPid=$PID"
    if ($remainingPids.Count -gt 0) {
        throw "[start-backend] port $Port is still occupied by pid(s): $($remainingPids -join ', '). Abort launching new backend process."
    }
}

if (-not $RunInCurrentShell) {
    Write-DebugLog "scriptPath=$PSCommandPath pwd=$((Get-Location).Path)"
    $endpoint = Resolve-BackendEndpoint
    Write-Host "[start-backend] target endpoint: $($endpoint.Scheme)://localhost:$($endpoint.Port)"
    Clear-ServerPort -Port $endpoint.Port
    $scriptPath = Join-Path $PSScriptRoot "start-backend.ps1"
    $args = @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-File", "`"$scriptPath`"",
        "-EnvFile", "`"$EnvFile`"",
        "-RunInCurrentShell"
    )
    Start-Process -FilePath "powershell.exe" -ArgumentList $args -WorkingDirectory $repoRoot
    Write-Host "[start-backend] launched backend in a new shell window."
    return
}

Write-Host "[start-backend] env: $envPath"
Write-Host "[start-backend] starting backend in current shell..."

Push-Location (Join-Path $repoRoot "backend")
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}

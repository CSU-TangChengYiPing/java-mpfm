param(
    [string]$EnvFile = ".env",
    [switch]$SkipQualityGate,
    [Alias("Mode")]
    [ValidateSet("new-code", "all")]
    [string]$QualityGateMode = "new-code",
    [switch]$FastOnlyNewCode,
    [switch]$VerboseTiming,
    [switch]$RunPmd,
    [switch]$LowMemoryMode
)

$ErrorActionPreference = "Stop"
$scriptStart = [System.Diagnostics.Stopwatch]::StartNew()

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

function Get-DbNameFromUrl {
    param([string]$Url)

    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $null
    }

    $m = [regex]::Match($Url, "jdbc:postgresql://[^/]+/(?<db>[^?]+)")
    if ($m.Success) {
        return $m.Groups["db"].Value
    }

    return $null
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$envPath = Join-Path $repoRoot $EnvFile
Import-DotEnv -Path $envPath

try {
if ($FastOnlyNewCode) {
    if ($SkipQualityGate) {
        throw "FastOnlyNewCode cannot be used with SkipQualityGate."
    }

    Write-Host "test-backend fast-only-new-code: run quality gate only (mode=new-code), skip db reset and mvn test."
    & (Join-Path $PSScriptRoot "quality-gate.ps1") -EnvFile $EnvFile -Mode "new-code" -VerboseTiming:$VerboseTiming -RunPmd:$RunPmd
    if ($LASTEXITCODE -ne 0) {
        throw "quality-gate failed with exit code $LASTEXITCODE"
    }
    return
}

$testDbHost = if ($env:MPFM_TEST_DB_HOST) { $env:MPFM_TEST_DB_HOST } else { if ($env:MPFM_DB_HOST) { $env:MPFM_DB_HOST } else { "localhost" } }
$testDbPort = if ($env:MPFM_TEST_DB_PORT) { $env:MPFM_TEST_DB_PORT } else { if ($env:MPFM_DB_PORT) { $env:MPFM_DB_PORT } else { "5432" } }
$testDbUser = if ($env:MPFM_TEST_DB_USERNAME) { $env:MPFM_TEST_DB_USERNAME } else { $env:MPFM_DB_USERNAME }
$testDbPass = if ($env:MPFM_TEST_DB_PASSWORD) { $env:MPFM_TEST_DB_PASSWORD } else { $env:MPFM_DB_PASSWORD }
$testDbName = if ($env:MPFM_TEST_DB_NAME) { $env:MPFM_TEST_DB_NAME } else { Get-DbNameFromUrl -Url $env:MPFM_TEST_DB_URL }
if ([string]::IsNullOrWhiteSpace($testDbName)) {
    $testDbName = "mpfm_test"
}
$testDbUrl = if ($env:MPFM_TEST_DB_URL) { $env:MPFM_TEST_DB_URL } else { "jdbc:postgresql://$testDbHost`:$testDbPort/$testDbName" }

[System.Environment]::SetEnvironmentVariable("MPFM_TEST_DB_HOST", $testDbHost, "Process")
[System.Environment]::SetEnvironmentVariable("MPFM_TEST_DB_PORT", $testDbPort, "Process")
[System.Environment]::SetEnvironmentVariable("MPFM_TEST_DB_USERNAME", $testDbUser, "Process")
[System.Environment]::SetEnvironmentVariable("MPFM_TEST_DB_PASSWORD", $testDbPass, "Process")
[System.Environment]::SetEnvironmentVariable("MPFM_TEST_DB_NAME", $testDbName, "Process")
[System.Environment]::SetEnvironmentVariable("MPFM_TEST_DB_URL", $testDbUrl, "Process")

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql not found. Install PostgreSQL client and add psql to PATH."
}

if (-not $testDbUser) {
    throw "Missing MPFM_TEST_DB_USERNAME (or MPFM_DB_USERNAME fallback)."
}

$env:PGPASSWORD = $testDbPass
$adminDb = "postgres"
$exists = & psql -h $testDbHost -p $testDbPort -U $testDbUser -d $adminDb -tAc "SELECT 1 FROM pg_database WHERE datname = '$testDbName';"
if (($exists | Out-String).Trim() -ne "1") {
    $null = & createdb -h $testDbHost -p $testDbPort -U $testDbUser $testDbName
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create test database '$testDbName'."
    }
}

$resetSql = @"
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
"@
$null = & psql -h $testDbHost -p $testDbPort -U $testDbUser -d $testDbName -v ON_ERROR_STOP=1 -c $resetSql
if ($LASTEXITCODE -ne 0) {
    throw "Failed to reset schema for test database '$testDbName'."
}

Write-Host "[test-backend] env: $envPath"
Write-Host "[test-backend] db: $testDbUrl"

if (-not $SkipQualityGate) {
    Write-Host "[test-backend] running quality gate (mode=$QualityGateMode)..."
    & (Join-Path $PSScriptRoot "quality-gate.ps1") -EnvFile $EnvFile -Mode $QualityGateMode -VerboseTiming:$VerboseTiming -RunPmd:$RunPmd
    if ($LASTEXITCODE -ne 0) {
        throw "quality-gate failed with exit code $LASTEXITCODE"
    }
}

Write-Host "[test-backend] running mvn test..."

Push-Location (Join-Path $repoRoot "backend")
try {
    if ($LowMemoryMode) {
        Write-Host "[test-backend] low-memory mode enabled: no-fork + tiny heap"
        $env:MAVEN_OPTS = "-Xms16m -Xmx128m -XX:+UseSerialGC"
        mvn -q "-DforkCount=0" test
    }
    else {
        mvn -q test
    }
    if ($LASTEXITCODE -ne 0) {
        throw "mvn test failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
}
finally {
    $scriptStart.Stop()
    Write-Host ("[test-backend] elapsed: {0} ms ({1:n1} s)" -f $scriptStart.ElapsedMilliseconds, $scriptStart.Elapsed.TotalSeconds)
}

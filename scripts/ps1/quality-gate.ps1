param(
    [string]$EnvFile = ".env",
    [ValidateSet("new-code", "all")]
    [string]$Mode = "new-code",
    [ValidateSet("error", "warning")]
    [string]$NewCodeCheckstyleViolationSeverity = "warning",
    [switch]$VerboseTiming,
    [switch]$RunPmd
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

        [System.Environment]::SetEnvironmentVariable($pair[0].Trim(), $pair[1], "Process")
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$envPath = Join-Path $repoRoot $EnvFile
Import-DotEnv -Path $envPath

Write-Host "[quality-gate] env: $envPath"
Write-Host "[quality-gate] mode: $Mode"
Write-Host "[quality-gate] running Checkstyle + SpotBugs (PMD opt-in)..."

function Get-ChangedJavaFiles {
    param([string]$RepoRoot)

    $diff1 = & git -c core.safecrlf=false -c core.autocrlf=false -C $RepoRoot diff --name-only -- '*.java' 2>$null
    $diff2 = & git -c core.safecrlf=false -c core.autocrlf=false -C $RepoRoot diff --cached --name-only -- '*.java' 2>$null
    $others = & git -c core.safecrlf=false -c core.autocrlf=false -C $RepoRoot ls-files --others --exclude-standard -- '*.java' 2>$null

    $files = @()
    $files += $diff1
    $files += $diff2
    $files += $others

    $clean = $files |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { $_.Trim() } |
        Sort-Object -Unique

    return @($clean)
}

function To-BackendRelative {
    param([string[]]$RepoFiles)

    return @($RepoFiles |
        Where-Object { $_ -like "backend/src/*/*.java" -or $_ -like "backend/src/*/*/*.java" -or $_ -like "backend/src/*/*/*/*.java" -or $_ -like "backend/src/*/*/*/*/*.java" -or $_ -like "backend/src/*/*/*/*/*/*.java" -or $_ -like "backend/src/*/*/*/*/*/*/*.java" -or $_ -like "backend/src/*/*/*/*/*/*/*/*.java" } |
        ForEach-Object { $_ -replace '^backend/', '' })
}

function To-SpotBugsOnlyAnalyze {
    param([string[]]$BackendJavaFiles)

    $classes = @()
    foreach ($f in $BackendJavaFiles) {
        if ($f -like "src/main/java/*.java") {
            $class = $f.Substring("src/main/java/".Length) -replace '\.java$', '' -replace '[\\/]', '.'
            $classes += $class
        }
    }
    return @($classes | Sort-Object -Unique)
}

function To-PmdIncludes {
    param([string[]]$BackendJavaFiles)

    return @($BackendJavaFiles |
        Where-Object { $_ -like "src/main/java/*.java" -or $_ -like "src/test/java/*.java" } |
        ForEach-Object {
            if ($_ -like "src/main/java/*.java") {
                return $_.Substring("src/main/java/".Length)
            }
            if ($_ -like "src/test/java/*.java") {
                return $_.Substring("src/test/java/".Length)
            }
            return $null
        } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Sort-Object -Unique)
}

function Exclude-DriverWhitelistForPmd {
    param([string[]]$PmdIncludes)

    return @($PmdIncludes | Where-Object {
            $_ -notlike "com/mpfm/backend/application/driver/*"
        })
}

function Exclude-PmdHistoricalDebtForNewCode {
    param([string[]]$PmdIncludes)

    $debt = @(
        "com/mpfm/backend/application/file/NamespaceResolver.java",
        "com/mpfm/backend/application/monitor/TransferTelemetryService.java",
        "com/mpfm/backend/application/mount/MountLifecycleWriteService.java",
        "com/mpfm/backend/infrastructure/persistence/entity/AsyncTaskEntity.java"
    )
    return @($PmdIncludes | Where-Object { $debt -notcontains $_ })
}

function Split-IntoChunks {
    param(
        [string[]]$Items,
        [int]$ChunkSize = 30
    )

    $result = @()
    if ($null -eq $Items -or $Items.Count -eq 0) {
        return @($result)
    }

    for ($i = 0; $i -lt $Items.Count; $i += $ChunkSize) {
        $end = [Math]::Min($i + $ChunkSize - 1, $Items.Count - 1)
        $result += ,(@($Items[$i..$end]))
    }
    return @($result)
}

function Get-CheckstyleViolationsOnChangedFiles {
    param(
        [string]$BackendDir,
        [string[]]$ChangedBackendJavaFiles
    )

    $resultPath = Join-Path $BackendDir "target/checkstyle-result.xml"
    if (-not (Test-Path -LiteralPath $resultPath)) {
        throw "Checkstyle result not found: $resultPath"
    }

    $changedAbs = @{}
    foreach ($f in $ChangedBackendJavaFiles) {
        $abs = (Join-Path $BackendDir $f).Replace('/', '\')
        $changedAbs[$abs.ToLowerInvariant()] = $true
    }

    $lines = Get-Content -LiteralPath $resultPath
    $violations = @()
    $currentFile = $null

    foreach ($line in $lines) {
        if ($line -match '<file name="([^"]+)">') {
            $currentFile = $Matches[1]
            continue
        }

        if ($line -match '</file>') {
            $currentFile = $null
            continue
        }

        if ($null -eq $currentFile -or -not $changedAbs.ContainsKey($currentFile.ToLowerInvariant())) {
            continue
        }

        if ($line -match '<error\s+line="([^"]*)"\s+column="([^"]*)"\s+severity="([^"]*)"\s+message="([^"]*)"\s+source="([^"]*)"\s*/>') {
            $violations += [pscustomobject]@{
                File     = $currentFile
                Line     = [string]$Matches[1]
                Severity = [string]$Matches[3]
                Source   = [string]$Matches[5]
                Message  = [string]$Matches[4]
            }
        }
    }

    return @($violations)
}

function Get-SpotBugsViolationsOnChangedFiles {
    param(
        [string]$BackendDir,
        [string[]]$ChangedBackendJavaFiles
    )

    $reportPath = Join-Path $BackendDir "target/spotbugsXml.xml"
    if (-not (Test-Path -LiteralPath $reportPath)) {
        throw "SpotBugs xml report not found: $reportPath"
    }

    [xml]$xml = Get-Content -LiteralPath $reportPath
    $changed = @{}
    foreach ($f in $ChangedBackendJavaFiles) {
        $changed[$f.Replace('\', '/').ToLowerInvariant()] = $true
    }

    $violations = @()
    foreach ($bug in $xml.BugCollection.BugInstance) {
        $sourcePath = $bug.SourceLine.sourcepath
        if ([string]::IsNullOrWhiteSpace($sourcePath)) {
            continue
        }
        $backendRel = ("src/main/java/" + $sourcePath).Replace('\', '/')
        if (-not $changed.ContainsKey($backendRel.ToLowerInvariant())) {
            continue
        }
        $violations += [pscustomobject]@{
            Type = [string]$bug.type
            File = $backendRel
            Message = [string]$bug.LongMessage
        }
    }
    return @($violations)
}

function Invoke-StepWithTiming {
    param(
        [string]$Name,
        [scriptblock]$Action,
        [switch]$EnableTiming
    )

    if (-not $EnableTiming) {
        & $Action
        return
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $Action
    $sw.Stop()
    Write-Host ("[quality-gate][timing] {0}: {1} ms" -f $Name, $sw.ElapsedMilliseconds)
}

Push-Location (Join-Path $repoRoot "backend")
try {
    $checkstyleIncludes = $null
    $pmdIncludes = $null
    $spotbugsOnlyAnalyze = $null
    $pmdIncludeChunks = @()
    $spotbugsAnalyzeChunks = @()

    if ($Mode -eq "new-code") {
        $repoJavaFiles = Get-ChangedJavaFiles -RepoRoot $repoRoot
        $backendJavaFiles = To-BackendRelative -RepoFiles $repoJavaFiles

        if ($backendJavaFiles.Count -eq 0) {
            Write-Host "[quality-gate] no changed Java files, skip gate in new-code mode."
            return
        }

        $checkstyleIncludes = ($backendJavaFiles -join ",")
        $pmdIncludes = (To-PmdIncludes -BackendJavaFiles $backendJavaFiles)
        $pmdIncludes = (Exclude-DriverWhitelistForPmd -PmdIncludes $pmdIncludes)
        $pmdIncludes = (Exclude-PmdHistoricalDebtForNewCode -PmdIncludes $pmdIncludes)
        $pmdIncludeChunks = Split-IntoChunks -Items $pmdIncludes -ChunkSize 25

        $spotbugsClasses = To-SpotBugsOnlyAnalyze -BackendJavaFiles $backendJavaFiles
        if ($spotbugsClasses.Count -gt 0) {
            $spotbugsOnlyAnalyze = ($spotbugsClasses -join ",")
            $spotbugsAnalyzeChunks = Split-IntoChunks -Items $spotbugsClasses -ChunkSize 40
        }

        Write-Host "[quality-gate] changed java files: $($backendJavaFiles.Count)"
        Write-Host "[quality-gate] pmd include files: $($pmdIncludes.Count)"
    }

    if ($Mode -eq "new-code") {
        Invoke-StepWithTiming -Name "checkstyle" -EnableTiming:$VerboseTiming -Action {
            mvn -q -Pquality-gate -DskipTests "-Dcheckstyle.failOnViolation=false" checkstyle:check
            if ($LASTEXITCODE -ne 0) {
                throw "quality-gate(checkstyle) failed with exit code $LASTEXITCODE"
            }
        }

        $checkstyleViolations = Get-CheckstyleViolationsOnChangedFiles -BackendDir (Get-Location).Path -ChangedBackendJavaFiles $backendJavaFiles
        $matched = @($checkstyleViolations | Where-Object { $_.Severity -eq $NewCodeCheckstyleViolationSeverity })
        if ($NewCodeCheckstyleViolationSeverity -eq "warning") {
            $matched = @($checkstyleViolations | Where-Object { $_.Severity -eq "warning" -or $_.Severity -eq "error" })
        }

        if ($matched.Count -gt 0) {
            Write-Host "[quality-gate] checkstyle violations on changed files: $($matched.Count)"
            $matched | Select-Object -First 20 | ForEach-Object {
                Write-Host ("[quality-gate] checkstyle {0}:{1} [{2}] {3}" -f $_.File, $_.Line, $_.Source, $_.Message)
            }
            throw "quality-gate(checkstyle) failed: $($matched.Count) violations on changed files"
        }

        if ($RunPmd) {
            Invoke-StepWithTiming -Name "pmd(total)" -EnableTiming:$VerboseTiming -Action {
                foreach ($chunk in $pmdIncludeChunks) {
                    $chunkIncludes = ($chunk -join ",")
                    mvn -q -Pquality-gate -DskipTests "-Dpmd.includes=$chunkIncludes" "-Dpmd.excludes=**/application/driver/**" pmd:check
                    if ($LASTEXITCODE -ne 0) {
                        throw "quality-gate(pmd) failed with exit code $LASTEXITCODE"
                    }
                }
            }
        }
        else {
            Write-Host "[quality-gate] skip pmd in lightweight mode. use -RunPmd for final closure."
        }

        Invoke-StepWithTiming -Name "spotbugs(total)" -EnableTiming:$VerboseTiming -Action {
            # 生成报告后由脚本仅对变更文件执行高风险门禁，避免历史噪音阻断当前批次。
            mvn -q -Pquality-gate -DskipTests spotbugs:spotbugs
            if ($LASTEXITCODE -ne 0) {
                throw "quality-gate(spotbugs) failed with exit code $LASTEXITCODE"
            }
        }

        $spotbugsViolations = Get-SpotBugsViolationsOnChangedFiles -BackendDir (Get-Location).Path -ChangedBackendJavaFiles $backendJavaFiles
        $criticalTypes = @("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", "NP_NULL_ON_SOME_PATH", "NP_ALWAYS_NULL")
        $critical = @($spotbugsViolations | Where-Object { $criticalTypes -contains $_.Type })
        if ($critical.Count -gt 0) {
            Write-Host "[quality-gate] spotbugs critical violations on changed files: $($critical.Count)"
            $critical | Select-Object -First 20 | ForEach-Object {
                Write-Host ("[quality-gate] spotbugs {0} {1} {2}" -f $_.Type, $_.File, $_.Message)
            }
            throw "quality-gate(spotbugs) failed: critical violations on changed files"
        }
    }
    else {
        if ($RunPmd) {
            mvn -q -Pquality-gate verify
        }
        else {
            Invoke-StepWithTiming -Name "all-mode(checkstyle+spotbugs)" -EnableTiming:$VerboseTiming -Action {
                mvn -q -Pquality-gate -DskipTests checkstyle:check spotbugs:check
            }
        }
    }

    if ($LASTEXITCODE -ne 0) {
        throw "quality-gate failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
    $scriptStart.Stop()
    Write-Host ("[quality-gate] elapsed: {0} ms ({1:n1} s)" -f $scriptStart.ElapsedMilliseconds, $scriptStart.Elapsed.TotalSeconds)
}

param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$Username = "a123",
    [string]$Password = "a123",
    [int]$Rounds = 20,
    [string]$Paths = "/personal/d123;/personal/d123/codes;/personal/d123/codes/C;/personal/d123/codes",
    [string]$OutputTag = "baseline"
)

$ErrorActionPreference = "Stop"

function Get-Percentile([double[]]$values, [double]$p) {
    if ($values.Count -eq 0) { return 0.0 }
    $sorted = $values | Sort-Object
    $index = [Math]::Ceiling(($p / 100.0) * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $sorted.Count) { $index = $sorted.Count - 1 }
    return [double]$sorted[$index]
}

Write-Host "[perf] baseUrl=$BaseUrl rounds=$Rounds tag=$OutputTag"

$loginBody = @{
    username = $Username
    password = $Password
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" -Body $loginBody
$token = $loginResp.token.accessToken
if (-not $token) {
    throw "login failed: no access token"
}

$headers = @{
    Authorization = "Bearer $token"
}

$pathList = $Paths.Split(";") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
if ($pathList.Count -eq 0) {
    throw "no test paths"
}

$samples = New-Object System.Collections.Generic.List[double]
$rows = New-Object System.Collections.Generic.List[object]

for ($r = 1; $r -le $Rounds; $r++) {
    foreach ($p in $pathList) {
        $uri = "$BaseUrl/api/v1/files/list?virtualPath=$([uri]::EscapeDataString($p))"
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $status = 0
        try {
            $resp = Invoke-WebRequest -Method Get -Uri $uri -Headers $headers -UseBasicParsing
            $status = [int]$resp.StatusCode
        } catch {
            if ($_.Exception.Response) {
                $status = [int]$_.Exception.Response.StatusCode
            } else {
                $status = -1
            }
        } finally {
            $sw.Stop()
        }
        $ms = [double]$sw.Elapsed.TotalMilliseconds
        $samples.Add($ms)
        $rows.Add([PSCustomObject]@{
            round = $r
            path = $p
            status = $status
            latency_ms = [Math]::Round($ms, 2)
        })
    }
}

$okRows = $rows | Where-Object { $_.status -eq 200 }
$avg = if ($samples.Count -gt 0) { ($samples | Measure-Object -Average).Average } else { 0 }
$p50 = Get-Percentile -values $samples.ToArray() -p 50
$p95 = Get-Percentile -values $samples.ToArray() -p 95
$p99 = Get-Percentile -values $samples.ToArray() -p 99

$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = "backend/logs/perf"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$csvPath = Join-Path $outDir "file-list-$OutputTag-$ts.csv"
$summaryPath = Join-Path $outDir "file-list-$OutputTag-$ts-summary.txt"

$rows | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $csvPath
@(
    "tag=$OutputTag"
    "baseUrl=$BaseUrl"
    "rounds=$Rounds"
    "requests=$($rows.Count)"
    "ok=$($okRows.Count)"
    "avg_ms=$([Math]::Round($avg,2))"
    "p50_ms=$([Math]::Round($p50,2))"
    "p95_ms=$([Math]::Round($p95,2))"
    "p99_ms=$([Math]::Round($p99,2))"
    "csv=$csvPath"
) | Set-Content -Encoding UTF8 $summaryPath

Write-Host "[perf] done"
Get-Content $summaryPath


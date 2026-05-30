param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$MountName,
    [string]$Username = "a123",
    [string]$Password = "a123",
    [string]$Namespace = "personal",
    [switch]$AutoStartBackend,
    [int]$BackendReadyTimeoutSec = 90,
    [switch]$SkipMutations
)

$ErrorActionPreference = "Stop"

function Assert-Required {
    param([string]$Name, [string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "missing required argument: $Name"
    }
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [object]$Body
    )
    $jsonBody = if ($null -eq $Body) { $null } else { ($Body | ConvertTo-Json -Depth 8 -Compress) }
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -ContentType "application/json" -Body $jsonBody
}

function Wait-BackendReady {
    param(
        [string]$Url,
        [int]$TimeoutSec
    )
    $start = Get-Date
    while ($true) {
        try {
            $resp = Invoke-WebRequest -Uri "$Url/api/v1/system/ping" -Method GET -TimeoutSec 5
            if ($resp.StatusCode -eq 200) {
                return
            }
        }
        catch {
            if ($_.Exception.Response) {
                $status = [int]$_.Exception.Response.StatusCode
                if ($status -eq 401 -or $status -eq 403 -or $status -eq 404) {
                    return
                }
            }
        }
        if (((Get-Date) - $start).TotalSeconds -ge $TimeoutSec) {
            throw "backend not ready within $TimeoutSec seconds: $Url"
        }
        Start-Sleep -Seconds 2
    }
}

function Invoke-WebDav {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [byte[]]$BodyBytes
    )
    try {
        $params = @{
            Uri        = $Url
            Headers    = $Headers
            TimeoutSec = 20
        }
        $params["Method"] = $Method.ToUpperInvariant()
        if ($null -ne $BodyBytes) {
            $params["Body"] = $BodyBytes
            $params["ContentType"] = "application/octet-stream"
        }
        return Invoke-WebRequest @params
    }
    catch {
        $status = $_.Exception.Response.StatusCode.value__
        $text = ""
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $text = $reader.ReadToEnd()
        }
        catch {
        }
        throw "webdav request failed: $Method $Url status=$status body=$text"
    }
}

Assert-Required -Name "MountName" -Value $MountName

if ($AutoStartBackend) {
    Write-Host "[webdav-e2e] backend auto-start requested."
    & (Join-Path $PSScriptRoot "start-backend.ps1")
}

Write-Host "[webdav-e2e] probing backend health..."
Wait-BackendReady -Url $BaseUrl -TimeoutSec $BackendReadyTimeoutSec
Write-Host "[webdav-e2e] backend is ready."

Write-Host "[webdav-e2e] login with test account..."
$login = Invoke-Json -Method "POST" -Url "$BaseUrl/api/v1/auth/login" -Headers @{} -Body @{
    username = $Username
    password = $Password
}
$accessToken = $login.token.accessToken
if ([string]::IsNullOrWhiteSpace($accessToken)) {
    throw "login succeeded but access token is empty"
}
$authHeaders = @{
    Authorization = "Bearer $accessToken"
}
$basicToken = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Username}:${Password}"))
$davAuthHeaders = @{
    Authorization = "Basic $basicToken"
}

$davBase = "$BaseUrl/dav/$Namespace/$MountName"
$probeName = "webdav-e2e-probe.txt"
$probeMoved = "webdav-e2e-probe-moved.txt"
$probeUrl = "$davBase/$probeName"
$probeMovedUrl = "$davBase/$probeMoved"

Write-Host "[webdav-e2e] OPTIONS..."
$opt = Invoke-WebDav -Method "OPTIONS" -Url $davBase -Headers $davAuthHeaders -BodyBytes $null
Write-Host ("[webdav-e2e] OPTIONS status={0}" -f $opt.StatusCode)

Write-Host "[webdav-e2e] PROPFIND depth=1..."
$pfHeaders = @{
    Authorization = "Basic $basicToken"
    Depth         = "1"
}
try {
    $pfBodyFile = Join-Path $env:TEMP "mpfm-webdav-propfind.xml"
    Set-Content -LiteralPath $pfBodyFile -Value '<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:allprop/></d:propfind>' -Encoding UTF8
    $pfOutFile = Join-Path $env:TEMP "mpfm-webdav-propfind.out"
    $code = & curl.exe -sS -o $pfOutFile -w "%{http_code}" -X PROPFIND `
        -H "Authorization: Basic $basicToken" `
        -H "Depth: 1" `
        -H "Content-Type: text/xml" `
        --data-binary "@$pfBodyFile" `
        "$davBase"
    $statusCode = [int]$code
}
catch {
    throw "PROPFIND failed: $($_.Exception.Message)"
}
if ($statusCode -ne 207) {
    throw "PROPFIND expected 207, actual $statusCode"
}
Write-Host "[webdav-e2e] PROPFIND passed."

if (-not $SkipMutations) {
    Write-Host "[webdav-e2e] PUT create probe file..."
    $putHeaders = @{
        Authorization  = "Bearer $accessToken"
        "If-None-Match" = "*"
    }
    $putHeaders["Authorization"] = "Basic $basicToken"
    $putResp = Invoke-WebDav -Method "PUT" -Url $probeUrl -Headers $putHeaders -BodyBytes ([byte[]][Text.Encoding]::UTF8.GetBytes("webdav-e2e-content"))
    if ($putResp.StatusCode -ne 201) {
        throw "PUT expected 201, actual $($putResp.StatusCode)"
    }

    Write-Host "[webdav-e2e] GET probe file..."
    $getResp = Invoke-WebDav -Method "GET" -Url $probeUrl -Headers $davAuthHeaders -BodyBytes $null
    if ($getResp.StatusCode -ne 200 -and $getResp.StatusCode -ne 206) {
        throw "GET expected 200/206, actual $($getResp.StatusCode)"
    }

    Write-Host "[webdav-e2e] MOVE probe file..."
    $moveHeaders = @{
        Authorization = "Basic $basicToken"
        Destination   = $probeMovedUrl
        "If-Match"    = "*"
        Overwrite     = "T"
    }
    $moveResp = Invoke-WebDav -Method "MOVE" -Url $probeUrl -Headers $moveHeaders -BodyBytes $null
    if ($moveResp.StatusCode -ne 201 -and $moveResp.StatusCode -ne 204) {
        throw "MOVE expected 201/204, actual $($moveResp.StatusCode)"
    }

    Write-Host "[webdav-e2e] DELETE moved probe file..."
    $delHeaders = @{
        Authorization = "Basic $basicToken"
        "If-Match"    = "*"
    }
    $delResp = Invoke-WebDav -Method "DELETE" -Url $probeMovedUrl -Headers $delHeaders -BodyBytes $null
    if ($delResp.StatusCode -ne 204) {
        throw "DELETE expected 204, actual $($delResp.StatusCode)"
    }
}

Write-Host "[webdav-e2e] all checks passed."

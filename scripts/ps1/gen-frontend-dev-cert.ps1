param(
    [string]$CertDir = "new_frontend/certs",
    [string]$CertName = "frontend-dev",
    [string]$HostName = "localhost",
    [int]$ValidityDays = 365,
    [string]$StorePass = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$targetDir = Join-Path $repoRoot $CertDir
if (-not (Test-Path -LiteralPath $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

if ([string]::IsNullOrWhiteSpace($StorePass)) {
    $securePass = Read-Host -Prompt "请输入证书口令(StorePass)" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePass)
    try {
        $StorePass = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$p12Path = Join-Path $targetDir "$CertName.p12"

if (Test-Path -LiteralPath $p12Path) {
    $backupPath = "$p12Path.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Move-Item -LiteralPath $p12Path -Destination $backupPath -Force
    Write-Host "[frontend-cert] existing certificate found, moved to backup: $backupPath"
}

Write-Host "[frontend-cert] generating p12 => $p12Path"
keytool -genkeypair `
  -alias $CertName `
  -keyalg RSA `
  -keysize 2048 `
  -storetype PKCS12 `
  -keystore $p12Path `
  -storepass $StorePass `
  -validity $ValidityDays `
  -dname "CN=$HostName, OU=frontend, O=mpfm, L=Shanghai, ST=Shanghai, C=CN" `
  -ext "SAN=dns:$HostName,ip:127.0.0.1"
if ($LASTEXITCODE -ne 0) {
    throw "[frontend-cert] keytool generate failed (exit=$LASTEXITCODE)."
}

Write-Host "[frontend-cert] verify certificate summary..."
keytool -list -v -keystore $p12Path -storetype PKCS12 -storepass $StorePass | Select-String -Pattern "Alias name:|Valid from:|SHA256:|Owner:|SubjectAlternativeName" -Context 0,0
if ($LASTEXITCODE -ne 0) {
    throw "[frontend-cert] keytool verify failed (exit=$LASTEXITCODE)."
}

Write-Host "[frontend-cert] done."
Write-Host "[frontend-cert] set in new_frontend/.env:"
Write-Host "VITE_DEV_HTTPS=true"
Write-Host "VITE_DEV_HTTPS_PFX_FILE=./certs/$CertName.p12"
Write-Host "VITE_DEV_HTTPS_PFX_PASSPHRASE=<你刚输入的口令>"

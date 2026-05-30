param(
    [string]$CertDir = "backend/certs",
    [string]$CertName = "keystore",
    [string]$Alias = "mpfm-local",
    [string]$HostName = "mpfm.local",
    [int]$ValidityDays = 365
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$targetDir = Join-Path $repoRoot $CertDir
if (-not (Test-Path -LiteralPath $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

$p12Path = Join-Path $targetDir "$CertName.p12"
if (Test-Path -LiteralPath $p12Path) {
    $backupPath = "$p12Path.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Move-Item -LiteralPath $p12Path -Destination $backupPath -Force
    Write-Host "[backend-cert] existing certificate found, moved to backup: $backupPath"
}

$securePass = Read-Host -Prompt "请输入后端 keystore 口令(StorePass)" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePass)
try {
    $storePass = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}

Write-Host "[backend-cert] generating p12 => $p12Path"
keytool -genkeypair `
  -alias $Alias `
  -keyalg RSA `
  -keysize 2048 `
  -storetype PKCS12 `
  -keystore $p12Path `
  -storepass $storePass `
  -validity $ValidityDays `
  -dname "CN=$HostName, OU=backend, O=mpfm, L=Shanghai, ST=Shanghai, C=CN" `
  -ext "SAN=dns:$HostName,dns:localhost,ip:127.0.0.1"
if ($LASTEXITCODE -ne 0) {
    throw "[backend-cert] keytool generate failed (exit=$LASTEXITCODE)."
}

Write-Host "[backend-cert] verify certificate summary..."
keytool -list -v -keystore $p12Path -storetype PKCS12 -storepass $storePass | Select-String -Pattern "Alias name:|Valid from:|SHA256:|Owner:|SubjectAlternativeName" -Context 0,0
if ($LASTEXITCODE -ne 0) {
    throw "[backend-cert] keytool verify failed (exit=$LASTEXITCODE)."
}

Write-Host "[backend-cert] done."
Write-Host "[backend-cert] set in .env:"
Write-Host "MPFM_TLS_ENABLED=true"
Write-Host "MPFM_TLS_KEYSTORE_PATH=file:./backend/certs/$CertName.p12"
Write-Host "MPFM_TLS_KEYSTORE_TYPE=PKCS12"
Write-Host "MPFM_TLS_KEYSTORE_PASSWORD=<你刚输入的口令>"
Write-Host "MPFM_TLS_KEY_ALIAS=$Alias"
Write-Host "MPFM_SERVER_PORT=8443"

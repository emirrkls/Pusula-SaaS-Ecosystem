$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot
$projectDirectory = [IO.Path]::GetFullPath($PSScriptRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
$targetDirectory = [IO.Path]::GetFullPath((Join-Path $projectDirectory 'target'))
if ($targetDirectory -ne (Join-Path $projectDirectory 'target')) { throw 'Unsafe build target.' }
if ((Test-Path -LiteralPath $targetDirectory) -and ((Get-Item -LiteralPath $targetDirectory).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Refusing to clean a linked build target.' }
$versionLine = Get-Content -LiteralPath 'src/main/resources/app-version.properties' | Where-Object { $_ -match '^version=' }
$appVersion = ($versionLine -replace '^version=', '').Trim()
if ($appVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid desktop version.' }

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Pusula Desktop Kurulum (MSI) Derleme Sistemi (PowerShell)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Adim 1: Proje Maven ile derleniyor (Eski dosyalar siliniyor)..." -ForegroundColor Yellow
mvn --batch-mode --no-transfer-progress clean verify
if ($LASTEXITCODE -ne 0) {
    Write-Host "[HATA] Maven derleme basarisiz oldu!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nAdim 2: Bagimliliklar ve ana uygulama jpackage icin hazirlaniyor..." -ForegroundColor Yellow
if (!(Test-Path "target\jpackage-input")) {
    New-Item -ItemType Directory -Force -Path "target\jpackage-input" | Out-Null
}
Copy-Item "target\frontend-desktop-1.0-SNAPSHOT.jar" -Destination "target\jpackage-input\" -Force
Copy-Item "target\lib\*" -Destination "target\jpackage-input\" -Recurse -Force

Write-Host "`nAdim 3: JPackage ile bagimsiz Native Installer uretiliyor..." -ForegroundColor Yellow
if (Test-Path -LiteralPath (Join-Path $targetDirectory 'installer')) {
    $installerDirectory = [IO.Path]::GetFullPath((Join-Path $targetDirectory 'installer'))
    if (-not $installerDirectory.StartsWith($targetDirectory + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe installer target.' }
    if ((Get-Item -LiteralPath $installerDirectory).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing to remove a linked installer directory.' }
    Remove-Item -LiteralPath $installerDirectory -Recurse -Force
}

# Win-shortcut and win-menu added for MSI
Write-Host "JPackage calisiyor... Lutfen bekleyin (Bu islem bir kac dakika surebilir)..." -ForegroundColor Magenta
# app-version.properties ile senkron tutun
jpackage --type msi --name "Pusula Servis Yonetimi" --app-version $appVersion --description "Pusula Desktop Application" --vendor "Pusula" --icon "src\main\resources\app.ico" --dest target\installer --input target\jpackage-input --main-jar frontend-desktop-1.0-SNAPSHOT.jar --main-class com.pusula.desktop.Launcher --win-shortcut --win-menu --win-dir-chooser --win-upgrade-uuid "A35C7210-561A-4BB9-A499-5D4047EC0CF3"

if ($LASTEXITCODE -ne 0) {
    Write-Host "[HATA] JPackage olusturma basarisiz oldu!" -ForegroundColor Red
    exit $LASTEXITCODE
}

$installerPath = "target\installer\Pusula-Servis-Yonetimi-$appVersion.msi"
Copy-Item "target\installer\Pusula Servis Yonetimi-$appVersion.msi" `
    -Destination $installerPath -Force

if ($env:PUSULA_SIGN_CERT_SHA1) {
    $signTool = Get-Command signtool.exe -ErrorAction Stop
    & $signTool.Source sign /sha1 $env:PUSULA_SIGN_CERT_SHA1 /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 $installerPath
    if ($LASTEXITCODE -ne 0) { throw "MSI dijital imzalama basarisiz oldu." }
} else {
    Write-Warning "PUSULA_SIGN_CERT_SHA1 tanimli degil; MSI imzasiz olusturuldu. Canli dagitimda kod imzalama sertifikasi kullanin."
}

$installerHash = (Get-FileHash -Algorithm SHA256 $installerPath).Hash.ToLowerInvariant()
Write-Host "SHA-256: $installerHash" -ForegroundColor Cyan

Write-Host ""
Write-Host "========================================================" -ForegroundColor Green
Write-Host "[BASARILI] Kurulum dosyasi basariyla olusturuldu!" -ForegroundColor Green
Write-Host "Konum: frontend-desktop\$installerPath" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green

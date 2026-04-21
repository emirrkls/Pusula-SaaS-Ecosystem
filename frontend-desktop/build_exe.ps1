$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Pusula Desktop Kurulum (MSI) Derleme Sistemi (PowerShell)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Adim 1: Proje Maven ile derleniyor (Eski dosyalar siliniyor)..." -ForegroundColor Yellow
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "[HATA] Maven derleme basarisiz oldu!" -ForegroundColor Red
    pause
    exit $LASTEXITCODE
}

Write-Host "`nAdim 2: Bagimliliklar ve ana uygulama jpackage icin hazirlaniyor..." -ForegroundColor Yellow
if (!(Test-Path "target\jpackage-input")) {
    New-Item -ItemType Directory -Force -Path "target\jpackage-input" | Out-Null
}
Copy-Item "target\frontend-desktop-1.0-SNAPSHOT.jar" -Destination "target\jpackage-input\" -Force
Copy-Item "target\lib\*" -Destination "target\jpackage-input\" -Recurse -Force

Write-Host "`nAdim 3: JPackage ile bagimsiz Native Installer uretiliyor..." -ForegroundColor Yellow
if (Test-Path "target\installer") {
    Remove-Item -Recurse -Force "target\installer"
}

# Win-shortcut and win-menu added for MSI
Write-Host "JPackage calisiyor... Lutfen bekleyin (Bu islem bir kac dakika surebilir)..." -ForegroundColor Magenta
jpackage --type msi --name "Pusula Servis Yonetimi" --app-version 3.0.0 --description "Pusula Desktop Application" --vendor "Pusula" --icon "src\main\resources\app.ico" --dest target\installer --input target\jpackage-input --main-jar frontend-desktop-1.0-SNAPSHOT.jar --main-class com.pusula.desktop.Launcher --win-shortcut --win-menu --win-dir-chooser

if ($LASTEXITCODE -ne 0) {
    Write-Host "[HATA] JPackage olusturma basarisiz oldu!" -ForegroundColor Red
    pause
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "========================================================" -ForegroundColor Green
Write-Host "[BASARILI] Kurulum dosyasi basariyla olusturuldu!" -ForegroundColor Green
Write-Host "Konum: frontend-desktop\target\installer\Pusula Servis Yonetimi-3.0.0.msi" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
pause

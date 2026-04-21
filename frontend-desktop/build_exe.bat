@echo off
echo ========================================================
echo Pusula Desktop Kurulum (MSI/EXE) Derleme Sistemi
echo ========================================================
echo.
echo Adim 1: Proje Maven ile derleniyor (Eski dosyalar siliniyor)...
call mvn clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [HATA] Maven derleme basarisiz oldu!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Adim 2: Bagimliliklar ve ana uygulama jpackage icin hazirlaniyor...
if not exist "target\jpackage-input" mkdir "target\jpackage-input"
copy target\frontend-desktop-1.0-SNAPSHOT.jar target\jpackage-input\ >nul
xcopy target\lib\* target\jpackage-input\ /s /y >nul

echo.
echo Adim 3: JPackage ile bagimsiz Native Installer (Kurulum Sihirbazi) uretiliyor...
if exist "target\installer" rmdir /s /q "target\installer"

REM JPackage komutu:
call jpackage --type msi --name "Pusula" --app-version 3.0.0 --description "Pusula Servis Yönetimi" --vendor "Pusula" --icon "src\main\resources\app.ico" --dest target\installer --input target\jpackage-input --main-jar frontend-desktop-1.0-SNAPSHOT.jar --main-class com.pusula.desktop.Launcher --win-shortcut --win-menu --win-dir-chooser

if %ERRORLEVEL% neq 0 (
    echo [HATA] JPackage olusturma basarisiz oldu! Lutfen WiX Toolset kurulu oldugundan veya JDK surumunuzden emin olun.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================
echo [BASARILI] Kurulum dosyasi (Pusula-3.0.0.msi) basariyla olusturuldu!
echo Konum: frontend-desktop\target\installer\
echo Bu MSI dosyasini kendi Java'si icinde bagimsizca sirketlere gonderebilirsiniz!
echo ========================================================
pause

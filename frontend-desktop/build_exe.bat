@echo off
setlocal
pushd "%~dp0"
powershell.exe -NoProfile -File "%~dp0build_exe.ps1"
set "pusulaBuildExit=%ERRORLEVEL%"
popd
exit /b %pusulaBuildExit%

@echo off
set VPS_IP=168.231.104.133
set /p VPS_USER="Enter VPS Username: "
set JAR_PATH=backend\target\pusula-backend-0.0.1-SNAPSHOT.jar
set REMOTE_PATH=/home/%VPS_USER%/pusula/

if not exist "%JAR_PATH%" (
    echo JAR file not found at %JAR_PATH%
    echo Please build the backend first.
    pause
    exit /b 1
)

echo Deploying to %VPS_IP%...

echo Uploading JAR file...
scp "%JAR_PATH%" "%VPS_USER%@%VPS_IP%:%REMOTE_PATH%"

if %ERRORLEVEL% NEQ 0 (
    echo Upload failed.
    pause
    exit /b 1
)

echo Restarting backend service...
ssh "%VPS_USER%@%VPS_IP%" "sudo systemctl restart pusula-backend && sudo systemctl status pusula-backend"

echo Deployment complete!
pause

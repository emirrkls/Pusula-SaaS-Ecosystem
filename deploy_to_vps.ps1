# Deploy to VPS Script
$VPS_IP = "168.231.104.133"
$VPS_USER = Read-Host "Enter VPS Username"
$JAR_PATH = "backend\target\pusula-backend-0.0.1-SNAPSHOT.jar"
$REMOTE_PATH = "/home/$VPS_USER/pusula/"

# Check if JAR exists
if (-not (Test-Path $JAR_PATH)) {
    Write-Error "JAR file not found at $JAR_PATH. Please build the backend first."
    exit 1
}

Write-Host "Deploying to $VPS_IP..."

# Upload JAR
Write-Host "Uploading JAR file..."
scp $JAR_PATH "$VPS_USER@$VPS_IP`:$REMOTE_PATH"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Upload failed."
    exit 1
}

# Restart Service
Write-Host "Restarting backend service..."
ssh "$VPS_USER@$VPS_IP" "sudo systemctl restart pusula-backend && sudo systemctl status pusula-backend"

Write-Host "Deployment complete!"

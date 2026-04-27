#!/bin/bash
# VPS deployment helper
# Run this on your VPS to start backend safely with required secrets

set -e  # Exit on any error

echo "Starting Pusula VPS deployment..."

# Configuration
DB_NAME="pusula_db"
DB_USER="postgres"
DB_PASSWORD="${DB_PASSWORD:-}"
JWT_SECRET="${JWT_SECRET:-}"
GOOGLE_WEB_CLIENT_ID="${GOOGLE_WEB_CLIENT_ID:-}"

if [ -z "$DB_PASSWORD" ]; then
  echo "ERROR: DB_PASSWORD is required."
  echo "Set it before running: export DB_PASSWORD='...'"
  exit 1
fi

if [ -z "$JWT_SECRET" ]; then
  echo "ERROR: JWT_SECRET is required."
  echo "Set it before running: export JWT_SECRET='...'"
  exit 1
fi

echo "Step 1/3: Verifying database..."
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1; then
  sudo -u postgres createdb "$DB_NAME"
fi
sudo -u postgres psql -c "ALTER USER $DB_USER PASSWORD '$DB_PASSWORD';"

echo "Step 2/3: Verifying database tables..."
sudo -u postgres psql -d $DB_NAME -c "\dt"

echo "Step 3/3: Starting backend on port 8080..."
DB_PASSWORD="$DB_PASSWORD" JWT_SECRET="$JWT_SECRET" GOOGLE_WEB_CLIENT_ID="$GOOGLE_WEB_CLIENT_ID" \
  java -jar /root/app.jar --spring.profiles.active=vps

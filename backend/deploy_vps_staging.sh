#!/bin/bash
# Complete VPS Deployment Script with Sample Data
# Run this on your VPS to set up staging environment

set -e  # Exit on any error

echo "🚀 Starting Pusula VPS Deployment with Sample Data..."

# Configuration
DB_NAME="pusula_db"
DB_USER="postgres"
DB_PASSWORD="pusula123"

echo "📦 Step 1/5: Resetting database..."
sudo -u postgres psql <<EOF
DROP DATABASE IF EXISTS $DB_NAME;
CREATE DATABASE $DB_NAME;
ALTER USER $DB_USER PASSWORD '$DB_PASSWORD';
EOF

echo "📋 Step 2/5: Creating schema..."
sudo -u postgres psql -d $DB_NAME -f /root/pusula_vps_schema.sql

echo "🌱 Step 3/5: Loading sample data..."
sudo -u postgres psql -d $DB_NAME -f /root/pusula_sample_data.sql

echo "✅ Step 4/5: Verifying database..."
sudo -u postgres psql -d $DB_NAME -c "\dt"

echo "🎯 Step 5/5: Starting backend..."
echo ""
echo "Sample users created:"
echo "  • admin / password (COMPANY_ADMIN)"
echo "  • tech1 / password (TECHNICIAN)"
echo ""
echo "Starting backend on port 8080..."
echo ""

DB_PASSWORD="$DB_PASSWORD" java -jar /root/app.jar --spring.profiles.active=vps

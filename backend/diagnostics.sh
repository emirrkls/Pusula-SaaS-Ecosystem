#!/bin/bash
# Pusula Backend Diagnostics Script
# Run this on your VPS to diagnose connection issues

echo "======================================"
echo "Pusula Backend Diagnostics"
echo "======================================"
echo ""

# Check if Java is installed
echo "[1/8] Checking Java installation..."
if command -v java &> /dev/null; then
    java -version 2>&1 | head -n 1
    echo "✓ Java is installed"
else
    echo "✗ Java is NOT installed"
fi
echo ""

# Check if PostgreSQL is running
echo "[2/8] Checking PostgreSQL status..."
if systemctl is-active --quiet postgresql; then
    echo "✓ PostgreSQL is running"
else
    echo "✗ PostgreSQL is NOT running"
fi
echo ""

# Check if port 8080 is listening
echo "[3/8] Checking if port 8080 is listening..."
if ss -tlnp | grep -q ':8080'; then
    echo "✓ Port 8080 is listening"
    ss -tlnp | grep ':8080'
else
    echo "✗ Port 8080 is NOT listening"
fi
echo ""

# Check firewall status
echo "[4/8] Checking firewall rules..."
if command -v ufw &> /dev/null; then
    sudo ufw status | grep 8080
elif command -v firewall-cmd &> /dev/null; then
    sudo firewall-cmd --list-ports | grep 8080
else
    echo "No standard firewall detected (ufw/firewalld)"
fi
echo ""

# Check if backend service exists
echo "[5/8] Checking backend service status..."
if systemctl list-unit-files | grep -q pusula-backend; then
    systemctl status pusula-backend --no-pager
else
    echo "⚠ Backend service not configured as systemd service"
fi
echo ""

# Test database connection
echo "[6/8] Testing database connectivity..."
if command -v psql &> /dev/null; then
    if PGPASSWORD='password' psql -U postgres -h localhost -d pusula_db -c '\q' 2>/dev/null; then
        echo "✓ Database connection successful"
    else
        echo "✗ Database connection failed"
        echo "  (This might be normal if you haven't set up the DB yet)"
    fi
else
    echo "⚠ psql not found"
fi
echo ""

# Test API endpoint
echo "[7/8] Testing API endpoint (localhost)..."
if command -v curl &> /dev/null; then
    response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/login 2>/dev/null || echo "000")
    if [ "$response" != "000" ]; then
        echo "✓ API is responding (HTTP $response)"
    else
        echo "✗ API is NOT responding"
    fi
else
    echo "⚠ curl not found"
fi
echo ""

# Check backend logs if service exists
echo "[8/8] Recent backend logs (last 20 lines)..."
if systemctl list-unit-files | grep -q pusula-backend; then
    sudo journalctl -u pusula-backend -n 20 --no-pager
else
    echo "⚠ No systemd service logs available"
fi
echo ""

echo "======================================"
echo "Diagnostics Complete"
echo "======================================"

# =====================================================
#  PUSULA VPS DEPLOYMENT GUIDE
#  Server: Hostinger VPS, Ubuntu 22.04
# =====================================================

## 1. SSL Sertifikası (Let's Encrypt + Certbot)

```bash
# Certbot kurulumu
sudo apt update
sudo apt install certbot python3-certbot-nginx -y

# SSL sertifikası al (domain'inizi değiştirin)
sudo certbot --nginx -d api.pusulatech.com --non-interactive --agree-tos -m admin@pusulatech.com

# Otomatik yenileme cron'u (Certbot bunu otomatik ekler)
sudo systemctl enable certbot.timer
```

## 2. Nginx Reverse Proxy Konfigürasyonu

`/etc/nginx/sites-available/pusula` dosyasını oluşturun:

```nginx
server {
    listen 80;
    server_name api.pusulatech.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.pusulatech.com;

    ssl_certificate /etc/letsencrypt/live/api.pusulatech.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.pusulatech.com/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    # Security headers
    add_header X-Frame-Options SAMEORIGIN always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # File upload limit (for signatures, photos)
    client_max_body_size 20M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket support (for future real-time features)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Static file serving for uploads
    location /uploads/ {
        alias /opt/pusula/uploads/;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

```bash
# Aktif et
sudo ln -sf /etc/nginx/sites-available/pusula /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## 3. SaaS Migration'ları VPS'e Uygula

```bash
# V2 (Plans + Features)
psql -U pusula_user -d pusula_db -f /opt/pusula/migrations/V2__saas_plans_and_features.sql

# V3 (Inventory barcode)
psql -U pusula_user -d pusula_db -f /opt/pusula/migrations/V3__inventory_barcode.sql

# V4 (Production readiness — isReadOnly, indexes)
psql -U pusula_user -d pusula_db -f /opt/pusula/migrations/V4__production_readiness.sql
```

## 4. Backend Deployment

```bash
#!/bin/bash
# /root/restart-backend.sh

echo "🔄 Stopping Pusula Backend..."
pkill -f 'pusula-backend' || true
sleep 2

echo "📦 Building..."
cd /opt/pusula/backend
mvn clean package -DskipTests -Dspring.profiles.active=vps

echo "🚀 Starting..."
nohup java -jar -Dspring.profiles.active=vps \
    -Xmx512m -Xms256m \
    target/backend-0.0.1-SNAPSHOT.jar \
    > /var/log/pusula/app.log 2>&1 &

echo "✅ Pusula Backend started. Logs: /var/log/pusula/app.log"
```

```bash
chmod +x /root/restart-backend.sh
mkdir -p /var/log/pusula
```

## 5. Firewall

```bash
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP (redirect to HTTPS)
sudo ufw allow 443/tcp   # HTTPS
sudo ufw enable
```

## 6. İyzico Sandbox → Production Geçişi

1. https://merchant.iyzipay.com adresinden Production API key alın
2. `application-vps.properties` dosyasında güncelleyin:
   ```
   iyzico.api.key=YOUR_PRODUCTION_KEY
   iyzico.api.secret=YOUR_PRODUCTION_SECRET
   iyzico.base.url=https://api.iyzipay.com
   ```
3. Webhook URL'i İyzico panelinden kaydedin:
   `https://api.pusulatech.com/api/payment/webhook/iyzico`

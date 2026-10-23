# Second Brain Server — Oracle Cloud Deployment Guide

Deploy Spring Boot + PostgreSQL on a free Oracle Cloud ARM VM with HTTPS.

---

## Step 0: Build the jar locally (test it works)

```bash
cd second-brain-server
./gradlew clean bootJar
# Produces: build/libs/second-brain-server-0.0.1-SNAPSHOT.jar
```

---

## Step 1: Create the Oracle Cloud VM

1. Sign up at cloud.oracle.com (Always Free tier)
2. Create Instance → choose:
   - **Shape:** Ampere ARM (VM.Standard.A1.Flex) → 2 cores, 12GB RAM (within free limit)
   - **Image:** Ubuntu 22.04
   - **Networking:** assign a public IPv4
3. Download the SSH private key when prompted
4. Under the VCN's **Security List**, add Ingress Rules to open ports:
   - `22` (SSH) — usually open by default
   - `80` (HTTP)
   - `443` (HTTPS)

---

## Step 2: SSH into the VM

```bash
chmod 600 your-key.key
ssh -i your-key.key ubuntu@YOUR_PUBLIC_IP
```

---

## Step 3: Install Java 21 + PostgreSQL

```bash
sudo apt update && sudo apt upgrade -y

# Java 21
sudo apt install -y openjdk-21-jdk
java -version   # verify 21

# PostgreSQL
sudo apt install -y postgresql postgresql-contrib
sudo systemctl enable --now postgresql
```

---

## Step 4: Set up the database

```bash
sudo -u postgres psql

CREATE DATABASE secondbrain_db;
CREATE USER sbuser WITH PASSWORD 'CHANGE_THIS_STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON DATABASE secondbrain_db TO sbuser;
\c secondbrain_db
GRANT ALL ON SCHEMA public TO sbuser;
\q
```

Then run your schema:
```bash
# Upload V1__create_initial_schema.sql to the VM (scp), then:
sudo -u postgres psql -d secondbrain_db -f V1__create_initial_schema.sql
```
(Or let Flyway auto-run it on app startup if Flyway is configured.)

---

## Step 5: Upload and run the jar

From your PC:
```bash
scp -i your-key.key build/libs/second-brain-server-0.0.1-SNAPSHOT.jar ubuntu@YOUR_PUBLIC_IP:/home/ubuntu/app.jar
```

On the VM, create a systemd service so it runs 24/7 and restarts on crash/reboot:

```bash
sudo nano /etc/systemd/system/secondbrain.service
```

```ini
[Unit]
Description=Second Brain Server
After=network.target postgresql.service

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu
ExecStart=/usr/bin/java -jar /home/ubuntu/app.jar
Restart=always
RestartSec=10

Environment=JWT_SECRET=your-strong-jwt-secret-here
Environment=DB_PASSWORD=CHANGE_THIS_STRONG_PASSWORD
Environment=DB_USERNAME=sbuser
Environment=DATABASE_URL=jdbc:postgresql://localhost:5432/secondbrain_db
Environment=GEMINI_API_KEY=your-gemini-key
Environment=YOUTUBE_API_KEY=your-youtube-key
Environment=AI_DAILY_LIMIT=50

[Install]
WantedBy=multi-user.target
```

Start it:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now secondbrain
sudo systemctl status secondbrain   # check it's running
sudo journalctl -u secondbrain -f   # view logs
```

Your app now runs on `http://localhost:8080` inside the VM.

---

## Step 6: Install nginx + HTTPS

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

Create nginx config:
```bash
sudo nano /etc/nginx/sites-available/secondbrain
```

```nginx
server {
    listen 80;
    server_name yourdomain.com;   # or your public IP if no domain yet

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable it:
```bash
sudo ln -s /etc/nginx/sites-available/secondbrain /etc/nginx/sites-enabled/
sudo nginx -t          # test config
sudo systemctl restart nginx
```

---

## Step 7: Get free HTTPS certificate

**Requires a domain name** pointing to your VM's IP (buy one cheap from Namecheap/Cloudflare, ~$10/yr, or use a free subdomain from DuckDNS).

```bash
sudo certbot --nginx -d yourdomain.com
```
Certbot auto-configures nginx for HTTPS and auto-renews the cert. Done — you now have `https://yourdomain.com`.

---

## Step 8: Point the app at it

In the React Native app `.env`:
```
EXPO_PUBLIC_API_BASE_URL=https://yourdomain.com/api
```

---

## Updating the app later

```bash
# Build new jar locally
./gradlew clean bootJar
# Upload
scp -i your-key.key build/libs/*.jar ubuntu@YOUR_IP:/home/ubuntu/app.jar
# Restart on VM
sudo systemctl restart secondbrain
```

---

## Notes

- The rate limit filter reads `X-Forwarded-For` — nginx sets this correctly (see config above), so rate limiting works properly behind the proxy.
- Firewall: Oracle has BOTH the cloud Security List AND an OS-level firewall (iptables). If ports seem blocked, run:
  `sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT` and same for 80.
- Free ARM instances are in high demand — if "out of capacity," retry or pick a different availability domain.

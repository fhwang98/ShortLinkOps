#!/bin/bash
set -euxo pipefail

apt-get update -y
apt-get install -y ca-certificates curl gnupg git docker.io docker-compose-v2 nginx certbot python3-certbot-nginx dnsutils

systemctl enable docker
systemctl start docker

usermod -aG docker ubuntu

systemctl enable --now certbot.timer || true

mkdir -p /var/www/html
mkdir -p /etc/letsencrypt/renewal-hooks/deploy

cat >/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh <<'RELOAD_NGINX'
#!/bin/bash
set -euo pipefail

systemctl reload nginx
RELOAD_NGINX

chmod 0755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

cat >/etc/nginx/sites-available/shortlinkops.conf <<'NGINX_CONF'
server {
    listen 80;
    listen [::]:80;

    server_name ${domain_name} ${www_domain_name};

    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location / {
        proxy_pass http://127.0.0.1:${app_upstream_port};
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX_CONF

rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/shortlinkops.conf /etc/nginx/sites-enabled/shortlinkops.conf
nginx -t
systemctl enable nginx
systemctl restart nginx

cat >/usr/local/bin/shortlinkops-issue-cert.sh <<'CERT_SCRIPT'
#!/bin/bash
set -euo pipefail

PRIMARY_DOMAIN="${domain_name}"
WWW_DOMAIN="${www_domain_name}"
EXPECTED_PUBLIC_IP="${expected_public_ip}"
CERTBOT_EMAIL="${certbot_email}"
LOG_FILE="/var/log/shortlinkops-certbot.log"

log() {
  echo "$(date -Is) $1" | tee -a "$LOG_FILE"
}

resolve_ip() {
  dig +short "$1" A | awk 'NF { print; exit }'
}

current_public_ip() {
  TOKEN="$(curl -fsS -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600" || true)"

  if [ -n "$TOKEN" ]; then
    curl -fsS -H "X-aws-ec2-metadata-token: $TOKEN" "http://169.254.169.254/latest/meta-data/public-ipv4" || true
  else
    curl -fsS "http://169.254.169.254/latest/meta-data/public-ipv4" || true
  fi
}

if [ -f "/etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem" ]; then
  log "인증서가 이미 발급되어 있습니다."
  systemctl disable --now shortlinkops-certbot.timer >/dev/null 2>&1 || true
  exit 0
fi

CURRENT_PUBLIC_IP="$(current_public_ip)"
PRIMARY_IP="$(resolve_ip "$PRIMARY_DOMAIN")"
WWW_IP="$(resolve_ip "$WWW_DOMAIN")"

if [ "$CURRENT_PUBLIC_IP" != "$EXPECTED_PUBLIC_IP" ]; then
  log "Elastic IP가 아직 EC2에 연결되지 않았습니다. current=$CURRENT_PUBLIC_IP expected=$EXPECTED_PUBLIC_IP"
  exit 75
fi

if [ "$PRIMARY_IP" != "$EXPECTED_PUBLIC_IP" ] || [ "$WWW_IP" != "$EXPECTED_PUBLIC_IP" ]; then
  log "DNS A 레코드가 아직 Elastic IP로 전파되지 않았습니다. primary=$PRIMARY_IP www=$WWW_IP expected=$EXPECTED_PUBLIC_IP"
  exit 75
fi

if [ -n "$CERTBOT_EMAIL" ]; then
  certbot --nginx --non-interactive --agree-tos --redirect --email "$CERTBOT_EMAIL" -d "$PRIMARY_DOMAIN" -d "$WWW_DOMAIN"
else
  certbot --nginx --non-interactive --agree-tos --redirect --register-unsafely-without-email -d "$PRIMARY_DOMAIN" -d "$WWW_DOMAIN"
fi

systemctl reload nginx
log "Let's Encrypt 인증서 발급과 HTTP to HTTPS 리다이렉트 설정이 완료되었습니다."
systemctl disable --now shortlinkops-certbot.timer >/dev/null 2>&1 || true
CERT_SCRIPT

chmod 0755 /usr/local/bin/shortlinkops-issue-cert.sh

cat >/etc/systemd/system/shortlinkops-certbot.service <<'CERT_SERVICE'
[Unit]
Description=ShortLinkOps Let's Encrypt 인증서 발급
After=network-online.target nginx.service
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/shortlinkops-issue-cert.sh
CERT_SERVICE

cat >/etc/systemd/system/shortlinkops-certbot.timer <<'CERT_TIMER'
[Unit]
Description=ShortLinkOps Let's Encrypt 인증서 발급 재시도

[Timer]
OnBootSec=3min
OnUnitActiveSec=10min
AccuracySec=1min
Persistent=true

[Install]
WantedBy=timers.target
CERT_TIMER

systemctl daemon-reload
systemctl enable --now shortlinkops-certbot.timer
/usr/local/bin/shortlinkops-issue-cert.sh || true

mkdir -p /opt/shortlinkops
chown ubuntu:ubuntu /opt/shortlinkops

cat >/opt/shortlinkops/README.txt <<'EOT'
ShortLinkOps EC2 호스트 준비가 완료되었습니다.

다음 단계:
1. ShortLinkOps 저장소를 clone합니다.
2. .env.prod.example을 기준으로 .env를 만들고 SHORTLINKOPS_BASE_URL=${shortlinkops_base_url} 값을 설정합니다.
3. 프로젝트 README의 Docker Compose 배포 명령을 실행합니다.
EOT

chown ubuntu:ubuntu /opt/shortlinkops/README.txt

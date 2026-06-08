#!/bin/bash
set -euo pipefail

NGINX_CONF="${1:-/etc/nginx/sites-available/shortlinkops.conf}"
TMP_FILE="$(mktemp)"

cleanup() {
  rm -f "$TMP_FILE"
}

trap cleanup EXIT

if [ ! -f "$NGINX_CONF" ]; then
  echo "Nginx 설정 파일을 찾을 수 없습니다: $NGINX_CONF" >&2
  exit 1
fi

if grep -q "location \^~ /actuator" "$NGINX_CONF"; then
  nginx -t
  systemctl reload nginx
  exit 0
fi

awk '
  BEGIN {
    inserted = 0
  }

  !inserted && $0 ~ /^[[:space:]]*location[[:space:]]+\/[[:space:]]*\{/ {
    print "    # 외부에서는 Actuator 엔드포인트를 노출하지 않습니다."
    print "    location ^~ /actuator {"
    print "        return 404;"
    print "    }"
    print ""
    inserted = 1
  }

  {
    print
  }

  END {
    if (!inserted) {
      exit 42
    }
  }
' "$NGINX_CONF" > "$TMP_FILE"

install -m 0644 "$TMP_FILE" "$NGINX_CONF"
nginx -t
systemctl reload nginx

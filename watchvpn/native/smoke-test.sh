#!/usr/bin/env bash
# Starts linux/amd64 mihomo with a synthetic VLESS+Reality config (no real server).
# Verifies mixed HTTP, SOCKS5 and REST. TUN fd passing cannot be exercised here
# (no Android VpnService). Handshake is NOT verified.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BIN="${ROOT}/watchvpn/native/bin/mihomo-linux-amd64"
WORKDIR="$(mktemp -d)"
trap 'kill "$PID" 2>/dev/null || true; rm -rf "$WORKDIR"' EXIT
VER=v1.19.34
if [[ ! -x "$BIN" ]]; then
  mkdir -p "$(dirname "$BIN")"
  curl -fL --retry 3 "https://github.com/MetaCubeX/mihomo/releases/download/${VER}/mihomo-linux-amd64-compatible-${VER}.gz" | gunzip > "$BIN"
  chmod 755 "$BIN"
fi
cat > "${WORKDIR}/config.yaml" <<'YAML'
mixed-port: 17890
socks-port: 17891
allow-lan: false
bind-address: 127.0.0.1
mode: rule
log-level: error
ipv6: false
external-controller: 127.0.0.1:19090
secret: smoketestsecretvalue
geodata-mode: false
geo-auto-update: false
find-process-mode: off
dns:
  enable: true
  ipv6: false
  enhanced-mode: fake-ip
  fake-ip-range: 198.18.0.1/16
  default-nameserver:
    - 8.8.8.8
  nameserver:
    - 8.8.8.8
proxies:
  - name: synthetic-nl
    type: vless
    server: 127.0.0.1
    port: 1
    uuid: 11111111-1111-4111-a111-111111111111
    network: tcp
    tls: true
    udp: true
    flow: xtls-rprx-vision
    servername: example.test
    client-fingerprint: chrome
    reality-opts:
      public-key: 0123456789abcdef0123456789abcdef0123456789a
      short-id: abcd1234
proxy-groups:
  - name: PROXY
    type: select
    proxies:
      - synthetic-nl
rules:
  - MATCH,PROXY
YAML
"$BIN" -d "$WORKDIR" -f "${WORKDIR}/config.yaml" > "${WORKDIR}/mihomo.log" 2>&1 &
PID=$!
ok=0
for i in $(seq 1 40); do
  if curl -fsS -H "Authorization: Bearer smoketestsecretvalue" http://127.0.0.1:19090/version >/tmp/watchvpn-mihomo-version.json; then
    ok=1
    break
  fi
  sleep 0.25
done
if [[ "$ok" != 1 ]]; then
  echo "mihomo API did not open" >&2
  cat "${WORKDIR}/mihomo.log" >&2 || true
  exit 1
fi
python3 - <<'PY'
import json, socket
print("version", open("/tmp/watchvpn-mihomo-version.json").read().strip())
for port in (17890, 17891, 19090):
    s=socket.socket(); s.settimeout(1)
    s.connect(("127.0.0.1", port)); s.close()
    print("listen", port, "ok")
print("SMOKE_OK listeners_only tun_fd_unverified tunnel_unverified")
PY

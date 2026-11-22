#!/usr/bin/env bash
# Starts linux/amd64 mihomo with a synthetic VLESS+Reality config (no real server).
# Verifies mixed HTTP, SOCKS5 and the REST API listen. Tunnel handshake is NOT verified.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BIN="${ROOT}/native/mihomo/bin/mihomo-linux-amd64"
WORKDIR="$(mktemp -d)"
trap 'kill "$PID" 2>/dev/null || true; rm -rf "$WORKDIR"' EXIT
if [[ ! -x "$BIN" ]]; then
  VER="$(cat "${ROOT}/native/mihomo/VERSION")"
  mkdir -p "$(dirname "$BIN")"
  curl -fL --retry 3 "https://github.com/MetaCubeX/mihomo/releases/download/${VER}/mihomo-linux-amd64-compatible-${VER}.gz" | gunzip > "$BIN"
  chmod 755 "$BIN"
fi
cat > "${WORKDIR}/config.yaml" <<'YAML'
mixed-port: 27890
socks-port: 27891
allow-lan: false
bind-address: 127.0.0.1
mode: rule
log-level: error
ipv6: false
external-controller: 127.0.0.1:29090
secret: smoketestsecretvalue
geodata-mode: false
geo-auto-update: false
find-process-mode: off
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
  if curl -fsS -H "Authorization: Bearer smoketestsecretvalue" http://127.0.0.1:29090/version >/tmp/mihomo-version.json; then
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
import json, socket, urllib.request
print("version", open("/tmp/mihomo-version.json").read().strip())
for port in (27890, 27891, 29090):
    s=socket.socket(); s.settimeout(1)
    s.connect(("127.0.0.1", port)); s.close()
    print("listen", port, "ok")
req=urllib.request.Request("http://127.0.0.1:29090/proxies", headers={"Authorization":"Bearer smoketestsecretvalue"})
proxies=json.load(urllib.request.urlopen(req, timeout=3))
assert "synthetic-nl" in proxies.get("proxies", {}), proxies.keys()
print("proxy node registered")
print("SMOKE_OK listeners_only tunnel_unverified")
PY

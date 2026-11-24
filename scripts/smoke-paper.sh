#!/usr/bin/env bash
# Boots a Paper server long enough to confirm BlazeMCLogin enables, then stops it.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-1.21.11}"
JAR_NAME="${2:-BlazeMCLogin-Paper-1.21.x.jar}"
JAVA_BIN="${3:-java}"
WORKDIR="$ROOT/local/smoke-$VERSION"
mkdir -p "$WORKDIR/plugins"
if [[ ! -f "$ROOT/build-output/$JAR_NAME" ]]; then
  echo "Missing $ROOT/build-output/$JAR_NAME — run ./gradlew copyBuildOutput first" >&2
  exit 1
fi
cp "$ROOT/build-output/$JAR_NAME" "$WORKDIR/plugins/"
UA="BlazeMCLogin-smoke/1.0.0 (https://github.com/vladgaming166-prog/BlazeMCLogin)"
if [[ ! -f "$WORKDIR/paper.jar" ]]; then
  echo "Downloading Paper $VERSION via Fill v3..."
  python3 - "$VERSION" "$WORKDIR/paper.jar" "$UA" << 'PY'
import json, sys, urllib.request
version, dest, ua = sys.argv[1], sys.argv[2], sys.argv[3]
url = f"https://fill.papermc.io/v3/projects/paper/versions/{version}/builds"
req = urllib.request.Request(url, headers={"User-Agent": ua})
with urllib.request.urlopen(req, timeout=30) as resp:
    builds = json.load(resp)
stable = next((b for b in builds if b.get("channel") == "STABLE"), builds[0])
dl = stable["downloads"]["server:default"]["url"]
print("GET", dl)
req2 = urllib.request.Request(dl, headers={"User-Agent": ua})
with urllib.request.urlopen(req2, timeout=120) as resp, open(dest, "wb") as out:
    out.write(resp.read())
print("saved", dest)
PY
fi
echo "eula=true" > "$WORKDIR/eula.txt"
# Delay /stop until the plugin has had time to enable. Piping stop immediately
# races Paper world generation and can hide enable failures.
(
  cd "$WORKDIR"
  (sleep 55; echo stop) | timeout 180s "$JAVA_BIN" -Xmx1G -jar paper.jar --nogui
) | tee "$WORKDIR/boot.log" || true
if grep -qiE "BlazeMCLogin failed to enable|Error occurred while enabling BlazeMCLogin|Could not load 'plugins/.*BlazeMCLogin" "$WORKDIR/boot.log"; then
  echo "SMOKE FAILED: plugin failed to load" >&2
  grep -A 25 -i "BlazeMCLogin" "$WORKDIR/boot.log" | tail -50 >&2
  exit 1
fi
if grep -q "BlazeMCLogin 1.0.0 enabled" "$WORKDIR/boot.log"; then
  echo "SMOKE OK: plugin enabled on Paper $VERSION"
  exit 0
fi
echo "SMOKE FAILED: BlazeMCLogin did not report enable" >&2
grep -i "BlazeMCLogin\|ERROR\|Exception" "$WORKDIR/boot.log" | tail -40 >&2 || true
exit 1

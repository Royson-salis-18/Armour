#!/usr/bin/env bash
# Runs on the CI emulator: installs the app, starts the web remote, opens a short-lived public tunnel and publishes the
# link to the ci-live branch. Blocks until the time limit, then exits (and the machine is thrown away).
set -u
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
MINUTES=${LIVE_MINUTES:-40}
P=com.armor.app
cp -r scripts/live /tmp/live
pip install -q pillow
TOKEN=$(python3 -c "import secrets;print(secrets.token_urlsafe(18))")

adb install -r -g "$APK"
adb shell pm grant $P android.permission.ACCESS_BACKGROUND_LOCATION || true
adb shell settings put system screen_off_timeout 1800000
adb shell settings put global hide_error_dialogs 1
adb emu geo fix 77.5946 12.9716
adb shell am start -n $P/.MainActivity >/dev/null

python3 /tmp/live/serve.py 8765 "$TOKEN" "$MINUTES" > /tmp/serve.log 2>&1 &
SERVER=$!
sleep 4
curl -sL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o /tmp/cloudflared && chmod +x /tmp/cloudflared
/tmp/cloudflared tunnel --no-autoupdate --url http://127.0.0.1:8765 > /tmp/tunnel.log 2>&1 &
URL=""
for i in $(seq 1 60); do URL=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' /tmp/tunnel.log | head -1); [ -n "$URL" ] && break; sleep 2; done
echo "tunnel: ${URL:-none}"

# Publish the link where the person (or Claude) can read it: a one-file branch.
mkdir -p /tmp/pub && cd /tmp/pub && git init -q && git config user.name "github-actions[bot]" && git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
printf '%s\nstarted: %s\nminutes: %s\n' "${URL:+$URL/$TOKEN/}" "$(date -u +%FT%TZ)" "$MINUTES" > live.txt
git add live.txt && git commit -qm "Live emulator link" && git remote add origin "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" && git push -q -f origin HEAD:ci-live
echo "published"
cd - >/dev/null

wait $SERVER
printf 'ended: %s\n' "$(date -u +%FT%TZ)" > /tmp/pub/live.txt
cd /tmp/pub && git add live.txt && git commit -qm "Live emulator ended" && git push -q -f origin HEAD:ci-live

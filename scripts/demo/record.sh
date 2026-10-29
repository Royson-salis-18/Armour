#!/usr/bin/env bash
# Records the ARMOR demo on an emulator, one screen recording per scene. Each scene lasts at least as long as its
# narration (narration/durations.txt from narrate.py). Output: video/<scene>.mp4 (or video/<scene>/frames as fallback).
set -u
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
P=com.armor.app
SERVER=${ARMOR_SERVER:-https://armor-ride.onrender.com}
NAR=${NAR:-narration}
OUT=video
mkdir -p "$OUT"

size=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${size%x*}; H=${size#*x}
echo "screen ${W}x${H}"

find_node() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > /tmp/ui.xml
  python3 - "$1" /tmp/ui.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[1].lower()
try:
    nodes = list(ET.parse(sys.argv[2]).iter('node'))
except Exception:
    sys.exit(0)
label = lambda n: ((n.get('text') or '') + '|' + (n.get('content-desc') or '')).lower()
hit = next((n for n in nodes if want in label(n).split('|')), None) or next((n for n in nodes if want in label(n)), None)
if hit is not None: print(*re.findall(r'\d+', hit.get('bounds')))
PY
}
tap_text() { b=$(find_node "$1"); [ -z "$b" ] && { echo "no node '$1'"; return 1; }; set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }
tap_at() { adb shell input tap $(( W * $1 / 100 )) $(( H * $2 / 100 )); }
swipe() { adb shell input swipe $(( W * $1 / 100 )) $(( H * $2 / 100 )) $(( W * $3 / 100 )) $(( H * $4 / 100 )) "${5:-400}"; }
geo() { adb emu geo fix "$1" "$2" >/dev/null; }
type_text() { adb shell input text "$(echo "$1" | sed 's/ /%s/g')"; }
nav() { case $1 in home) tap_at 21 92;; group) tap_at 40 92;; log) tap_at 59 92;; settings) tap_at 79 92;; esac; sleep 2; }

dur() { awk -v id="$1" '$1==id {print $2}' "$NAR/durations.txt"; }
now() { date +%s.%N; }

# Some emulator images can't run screenrecord; then each scene is captured as a series of screenshots instead.
adb shell screenrecord --time-limit 2 /sdcard/probe.mp4 >/dev/null 2>&1
PROBE=$(adb shell ls -s /sdcard/probe.mp4 2>/dev/null | awk '{print $1}')
if [ "${PROBE:-0}" -gt 0 ]; then MODE=record; else MODE=frames; fi
echo "capture mode: $MODE"

scene_start() {
  clear_dialogs
  SCENE=$1; T0=$(now)
  if [ "$MODE" = record ]; then
    adb shell screenrecord --bit-rate 6000000 --time-limit 180 "/sdcard/$SCENE.mp4" >/dev/null 2>&1 &
    REC=$!
  else
    mkdir -p "$OUT/$SCENE"
    ( i=0; while [ ! -f "$OUT/$SCENE/.stop" ]; do adb exec-out screencap -p > "$OUT/$SCENE/$(printf %05d $i).png"; echo "$(now)" >> "$OUT/$SCENE/times"; i=$((i+1)); done ) &
    REC=$!
  fi
  sleep 1.2
}
scene_end() {
  need=$(dur "$SCENE"); need=${need:-6}
  left=$(python3 -c "print(max(0.0, $need + 0.8 - ($(now) - $T0)))")
  sleep "$left"
  if [ "$MODE" = record ]; then
    adb shell pkill -INT screenrecord; wait $REC 2>/dev/null; sleep 1.5
    adb pull "/sdcard/$SCENE.mp4" "$OUT/$SCENE.mp4" >/dev/null && adb shell rm "/sdcard/$SCENE.mp4"
  else
    touch "$OUT/$SCENE/.stop"; wait $REC 2>/dev/null
  fi
  echo "scene $SCENE done"
}

# ---- setup -----------------------------------------------------------------------------------
# CI emulators are slow right after boot and the system then shows "Pixel Launcher isn't responding" over everything.
# Hide system error dialogs, let the device settle, and dismiss any that still appear before each scene.
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure anr_show_background 0 >/dev/null 2>&1 || true
clear_dialogs() {
  for i in 1 2 3; do
    if [ -n "$(find_node "isn't responding")" ]; then tap_text "Wait" || adb shell input keyevent 4; sleep 1; else break; fi
  done
}
sleep 25
clear_dialogs
adb install -r -g "$APK"
adb shell pm grant $P android.permission.ACCESS_BACKGROUND_LOCATION || true
adb shell settings put system screen_off_timeout 1800000
adb shell cmd uimode night yes >/dev/null 2>&1 || true
geo 77.5946 12.9716
adb shell am start -W -n $P/.MainActivity >/dev/null
sleep 6
clear_dialogs
# Name and number first, off camera, so the demo shows a set-up rider.
nav group
tap_text "Your name" && type_text "Royson"; adb shell input keyevent 4; sleep 1
tap_text "Your phone number" && type_text "98450 12345"; adb shell input keyevent 4; sleep 1
nav home

# ---- 1 intro ---------------------------------------------------------------------------------
scene_start 01-intro
sleep 3; swipe 50 70 50 40 900; sleep 2; swipe 50 40 50 75 900
scene_end

# ---- 2 group ---------------------------------------------------------------------------------
scene_start 02-group
nav group; sleep 2; swipe 50 70 50 45 900; sleep 2; swipe 50 45 50 70 900
scene_end

# ---- 3 contacts ------------------------------------------------------------------------------
scene_start 03-contacts
nav settings
for i in 1 2 3 4 5; do b=$(find_node "Add contact"); [ -n "$b" ] && break; swipe 50 75 50 35 500; sleep 0.6; done
swipe 50 66 50 40 500; sleep 0.8
tap_text "Name" && type_text "Mom"; sleep 0.5
tap_text "Phone number" && type_text "98765 43210"; sleep 0.8
adb shell input keyevent 4; sleep 0.6
tap_text "Add contact"; sleep 1.5
swipe 50 35 50 60 600; sleep 1
tap_text "Send test SOS text"; sleep 2
scene_end

# ---- 4 start ---------------------------------------------------------------------------------
scene_start 04-start
nav home; sleep 1
for i in 1 2 3; do b=$(find_node "SLIDE TO RIDE"); [ -n "$b" ] && break; swipe 50 75 50 30 300; sleep 1; done
set -- ${b:-0 0 0 0}; y=$(( ($2 + $4) / 2 ))
adb shell input swipe $((W*12/100)) $y $((W*96/100)) $y 1100
for i in $(seq 1 8); do geo $(python3 -c "print(77.5946 + $i*0.00018, 12.9716 + $i*0.00009)"); sleep 1.4; done
scene_end

# ---- 5 map -----------------------------------------------------------------------------------
scene_start 05-map
swipe 50 25 25 32 700; sleep 1.2
tap_text "Zoom in"; sleep 0.8; tap_text "Zoom in"; sleep 1.5
tap_text "Zoom out"; sleep 0.8; tap_text "Zoom out"; sleep 0.8; tap_text "Zoom out"; sleep 1.2
tap_text "Recentre"; sleep 1.5
tap_text "Map"; sleep 1.5; tap_text "Terrain"; sleep 3
tap_text "Terrain"; sleep 1.2; tap_text "Map"; sleep 2
scene_end

# ---- 6 panel ---------------------------------------------------------------------------------
scene_start 06-panel
b=$(find_node "Hide controls"); set -- ${b:-0 0 0 0}; hy=$(( ($2 + $4) / 2 ))
[ "$hy" -gt 0 ] && adb shell input swipe $((W/2)) $hy $((W/2)) $((hy + 500)) 450
sleep 4
tap_text "Show controls"; sleep 2.5
scene_end

# ---- 7 group live ----------------------------------------------------------------------------
R=$(curl -s -m 90 -X POST "$SERVER/api/rides" -H 'Content-Type: application/json' -d '{"name":"Sunday ride","riderName":"Kabir","phone":"+919800000002"}')
CODE=$(echo "$R" | python3 -c "import json,sys;print(json.load(sys.stdin)['code'])" 2>/dev/null)
KID=$(echo "$R" | python3 -c "import json,sys;print(json.load(sys.stdin)['riderId'])" 2>/dev/null)
KTOK=$(echo "$R" | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])" 2>/dev/null)
kabir() { curl -s -m 15 -X POST "$SERVER/api/rides/$CODE/$1" -H 'Content-Type: application/json' -d "{\"riderId\":\"$KID\",\"token\":\"$KTOK\",$2}" >/dev/null; }
echo "ride $CODE"
scene_start 07-group-live
if [ -n "$CODE" ]; then
  nav group
  tap_text "6-letter ride code" && adb shell input text "$CODE"; adb shell input keyevent 4; sleep 0.6
  tap_text "Join ride"; sleep 5
  nav home
  kabir message '"text":"Hi all, I am right behind you"'
  for i in $(seq 1 6); do kabir location "\"lat\":$(python3 -c "print(12.9712 + $i*0.00012)"),\"lon\":77.5952,\"acc\":8,\"speed\":7"; sleep 1.5; done
  tap_text "Chat"; sleep 3; tap_text "Close" || adb shell input keyevent 4; sleep 1
fi
scene_end

# ---- 8 voice ---------------------------------------------------------------------------------
front() { adb shell am start -n $P/.MainActivity >/dev/null 2>&1; sleep 1; }
front
scene_start 08-voice
tap_text "Talk to Armor"; sleep 6
kabir location '"lat":12.9722,"lon":77.5953,"acc":8,"speed":0'
scene_end

# ---- 9 safety --------------------------------------------------------------------------------
# Stay still away from Kabir until ARMOR asks; record from the moment it does.
front
for i in $(seq 1 60); do kabir location '"lat":12.9705,"lon":77.5950,"acc":8,"speed":0'; [ -n "$(find_node "I'm okay")" ] && break; sleep 3; done
scene_start 09-safety
sleep 6; tap_text "I'm okay"; sleep 2
scene_end

# ---- 10 outro --------------------------------------------------------------------------------
scene_start 10-outro
tap_text "Hide controls" >/dev/null; sleep 4
scene_end

adb logcat -d -s AndroidRuntime:E | grep -A8 "FATAL EXCEPTION" | head -30
ls -la "$OUT"

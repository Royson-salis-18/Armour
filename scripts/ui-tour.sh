#!/usr/bin/env bash
# Drives the debug APK on an emulator through a real ride and saves screenshots to screens/.
# Used by .github/workflows/ui-tour.yml; also works against any device on adb.
set -u
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
P=com.armor.app
OUT=screens
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
size=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${size%x*}; H=${size#*x}
echo "screen ${W}x${H}"

# Bounds of the first node whose text or content-desc contains $1, as "x1 y1 x2 y2".
find_node() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
  python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[1].lower()
nodes = list(ET.parse(sys.argv[2]).iter('node'))
label = lambda n: ((n.get('text') or '') + '|' + (n.get('content-desc') or '')).lower()
# An exact label wins over a longer one that merely contains it ("Phone number" vs "Your phone number").
hit = next((n for n in nodes if want in label(n).split('|')), None) or next((n for n in nodes if want in label(n)), None)
if hit is not None: print(*re.findall(r'\d+', hit.get('bounds')))
PY
}
tap_text() {
  b=$(find_node "$1"); [ -z "$b" ] && { echo "no node '$1'"; return 1; }
  set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
geo() { adb emu geo fix "$1" "$2" >/dev/null; }

adb install -r -g "$APK"
adb shell pm grant $P android.permission.ACCESS_BACKGROUND_LOCATION || true
adb shell dumpsys deviceidle whitelist +$P >/dev/null || true
adb logcat -c
geo 77.5946 12.9716
adb shell am start -W -n $P/.MainActivity
sleep 6
shot 01-home
# The Group tab (second item in the bottom bar): create or join a ride, no server address to type.
adb shell input tap $((W*40/100)) $((H*92/100)); sleep 3; shot 01b-group
adb shell input tap $((W*21/100)) $((H*92/100)); sleep 2

# Slide to ride: scroll down until the slider is on screen, then drag its thumb all the way across.
for i in 1 2 3; do b=$(find_node "SLIDE TO RIDE"); [ -n "$b" ] && break; adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 300; sleep 1; done
set -- ${b:-0 0 0 0}
y=$(( ($2 + $4) / 2 ))
adb shell input swipe $((W*12/100)) $y $((W*96/100)) $y 900
for i in $(seq 1 10); do geo $(python3 -c "print(77.5946 + $i*0.0002, 12.9716 + $i*0.0001)"); sleep 2; done
shot 02-ride

# Move the map: drag it to the side, then check the Recentre button appeared.
adb shell input swipe $((W/2)) $((H/4)) $((W/5)) $((H/3)) 600
sleep 3
shot 03-map-dragged

# Minimise the bottom panel by dragging its handle down.
b=$(find_node "Hide controls"); set -- ${b:-0 0 0 0}
hy=$(( ($2 + $4) / 2 )); echo "handle at y=$hy"
[ "$hy" -gt 0 ] && adb shell input swipe $((W/2)) $hy $((W/2)) $((hy + 500)) 400
sleep 2
shot 04-minimised
tap_text "Recentre" && sleep 2 && shot 05-recentred
tap_text "Show controls" && sleep 2 && shot 06-shown
for i in 1 2 3 4; do tap_text "Zoom in"; sleep 1; done; sleep 3; shot 07-zoomed-in
adb shell input swipe $((W/2)) $((H/4)) $((W*9/10)) $((H/5)) 500; sleep 2; shot 08-zoomed-dragged
for i in 1 2 3 4 5 6 7 8; do tap_text "Zoom out"; sleep 1; done; sleep 3; shot 09-zoomed-out

for v in Map Terrain; do :; done
# The voice assistant: tap the orange mic and check it listens (the emulator has no real microphone input).
adb shell input tap $((W*21/100)) $((H*92/100)); sleep 2
tap_text "Talk to Armor" && sleep 1 && shot 09b-assistant-listening
sleep 8; shot 09c-assistant-after

# Settings: add an emergency contact typed the Indian way, check it is readable and saved as +91, send a real test SMS.
adb shell input tap $((W*79/100)) $((H*92/100)); sleep 3
for i in 1 2 3 4 5; do b=$(find_node "Add contact"); [ -n "$b" ] && break; adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/3)) 400; sleep 1; done
adb shell input swipe $((W/2)) $((H*2/3)) $((W/2)) $((H/3)) 400; sleep 1
tap_text "Name" && adb shell input text "Mom" && sleep 1
tap_text "Phone number" && adb shell input text "98765%s43210" && sleep 1
shot 10-contact-typed
adb shell input keyevent 4; sleep 1
tap_text "Add contact"; sleep 2
shot 11-contact-added
tap_text "Test"; sleep 6
shot 12-test-sms
tap_text "Pick from contacts"; sleep 4
shot 13-contacts-app
adb shell input keyevent 4; sleep 1

echo "----- tile log -----"; adb logcat -d -s ArmorTiles:* | tail -20
echo "----- crashes -----"; adb logcat -d -s AndroidRuntime:E | grep -A12 "FATAL EXCEPTION" | head -60
adb shell pidof com.armor.app >/dev/null && echo "APP STILL RUNNING" || echo "APP NOT RUNNING (crashed?)"

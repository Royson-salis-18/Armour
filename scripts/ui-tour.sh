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
for n in ET.parse(sys.argv[2]).iter('node'):
    if want in (n.get('text') or '').lower() or want in (n.get('content-desc') or '').lower():
        print(*re.findall(r'\d+', n.get('bounds'))); break
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

# Minimise the bottom panel from its handle (the top edge of the dark sheet).
tap_text "RIDING" >/dev/null || true
b=$(find_node "Chat"); set -- ${b:-0 0 0 0}
python3 - "$OUT/ui.xml" > "$OUT/sheet-top.txt" <<'PY'
import re, sys, xml.etree.ElementTree as ET
print(min([int(re.findall(r'\d+', n.get('bounds'))[1]) for n in ET.parse(sys.argv[1]).iter('node') if (n.get('text') or '').upper().startswith(('RIDING', 'WITH THE', 'SEPARATED', 'GROUP OUT'))] or [0]))
PY
top=$(cat "$OUT/sheet-top.txt"); echo "status title at y=$top"
[ "$top" -gt 0 ] && adb shell input swipe $((W/2)) $((top - 60)) $((W/2)) $((top + 400)) 300
sleep 2
shot 04-minimised
tap_text "Recentre" && sleep 2 && shot 05-recentred

for v in Map Terrain; do :; done
echo "----- tile log -----"; adb logcat -d -s ArmorTiles:* | tail -20
echo "----- crashes -----"; adb logcat -d -s AndroidRuntime:E | tail -40

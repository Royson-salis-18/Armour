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
mkdir -p "$OUT/debug"; MISS=0
tap_text() {
  b=$(find_node "$1")
  if [ -z "$b" ]; then
    MISS=$((MISS+1)); echo "no node '$1' (debug/miss-$MISS.png)"
    adb exec-out screencap -p > "$OUT/debug/miss-$MISS-$(echo "$1" | tr -c 'A-Za-z0-9' '_').png"
    return 1
  fi
  set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
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
  wake
  clear_dialogs
  if [ "${KEEP_APP:-0}" != 1 ] && ! on_armor; then front; fi
  hide_kb
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
  adb exec-out screencap -p > "$OUT/debug/end-$SCENE.png"
  echo "scene $SCENE done; focus: $(adb shell dumpsys window | grep -m1 mCurrentFocus)"
}

# ---- setup -----------------------------------------------------------------------------------
# CI emulators are slow right after boot and the system then shows "Pixel Launcher isn't responding" over everything.
adb shell settings put global hide_error_dialogs 1
clear_dialogs() {
  for i in 1 2 3; do
    if [ -n "$(find_node "isn't responding")" ]; then tap_text "Wait" || adb shell input keyevent 4; sleep 1; else break; fi
  done
}
# Wake and unlock first: a black, locked screen makes every later step miss.
wake() { adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard >/dev/null 2>&1; }
front() { wake; adb shell am start -n $P/.MainActivity >/dev/null 2>&1; sleep 1.5; }
# The hang-up key doubles as the power button when no call is active, so only press it during a call.
hang_up() { adb shell dumpsys telephony.registry | grep -q 'mCallState=[12]' && adb shell input keyevent KEYCODE_ENDCALL; sleep 0.5; }
hide_kb() { adb shell dumpsys input_method | grep -q 'mInputShown=true' && adb shell input keyevent 4 && sleep 0.6; return 0; }
# Bring ARMOR back after another app (dialer, Messages) has been shown; those apps can take a few seconds to appear.
back_to_armor() { sleep "${1:-0}"; adb shell am force-stop com.google.android.apps.messaging; hang_up; front; hide_kb; }
on_armor() { adb shell dumpsys window | grep -m1 mCurrentFocus | grep -q "$P"; }
long_press() { b=$(find_node "$1"); [ -z "$b" ] && { echo "no node '$1'"; return 1; }; set -- $b; x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 )); adb shell input swipe $x $y $x $y ${2:-1900}; }
scroll_to() { for i in 1 2 3 4 5 6; do [ -n "$(find_node "$1")" ] && return 0; swipe 50 72 50 38 450; sleep 0.5; done; return 1; }
scroll_top() { for i in 1 2 3 4; do swipe 50 30 50 80 250; done; sleep 0.5; }
sleep 25
clear_dialogs
adb install -r -g "$APK"
adb shell pm grant $P android.permission.ACCESS_BACKGROUND_LOCATION || true
adb shell settings put system screen_off_timeout 1800000
# A contact in the phone's own address book, for "Pick from contacts".
adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n: >/dev/null 2>&1
RAW=$(adb shell content query --uri content://com.android.contacts/raw_contacts --projection _id | grep -o '_id=[0-9]*' | tail -1 | cut -d= -f2)
if [ -n "$RAW" ]; then
  adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$RAW --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:Dad >/dev/null
  adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$RAW --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:09900011223 --bind data2:i:2 >/dev/null
fi
geo 77.5946 12.9716
adb shell am start -W -n $P/.MainActivity >/dev/null
sleep 6
clear_dialogs

# ---- 1 intro ---------------------------------------------------------------------------------
scene_start 01-intro
sleep 3; swipe 50 70 50 40 900; sleep 2; swipe 50 40 50 75 900
scene_end

# ---- 2 setup checklist -------------------------------------------------------------------------
scene_start 02-setup
scroll_to "Set up"; tap_text "Set up"; sleep 6
swipe 50 70 50 45 700; sleep 2
adb shell input keyevent 4; sleep 1
scene_end

# ---- 3 profile -------------------------------------------------------------------------------
scene_start 03-profile
nav group
tap_text "Your name" && type_text "Royson"; sleep 0.5
tap_text "Your phone number" && type_text "98450 12345"; sleep 0.5
hide_kb; sleep 2
scene_end

# ---- 4 create ride + invite --------------------------------------------------------------------
scene_start 04-create
tap_text "Ride name (optional)" && type_text "Sunday coast run"; hide_kb; sleep 0.5
tap_text "Create ride"; sleep 8
CODE=$(adb shell cat /sdcard/ui.xml >/dev/null; adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb shell cat /sdcard/ui.xml | python3 -c "
import re,sys,xml.etree.ElementTree as ET
t=ET.fromstring(sys.stdin.read())
c=[n.get('text') for n in t.iter('node') if re.fullmatch(r'[A-HJ-NP-Z2-9]{6}', (n.get('text') or '').replace(' ',''))]
print(c[0].replace(' ','') if c else '')")
echo "our ride: $CODE"
tap_text "Invite riders"; sleep 4
adb shell input keyevent 4; sleep 1.5
scene_end

# Kabir joins our ride over the server (a second phone).
J=$(curl -s -m 90 -X POST "$SERVER/api/rides/$CODE/join" -H 'Content-Type: application/json' -d '{"riderName":"Kabir","phone":"+919800000002"}')
KID=$(echo "$J" | python3 -c "import json,sys;print(json.load(sys.stdin)['riderId'])" 2>/dev/null)
KTOK=$(echo "$J" | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])" 2>/dev/null)
kabir() { [ -n "$KID" ] && curl -s -m 15 -X POST "$SERVER/api/rides/$CODE/$1" -H 'Content-Type: application/json' -d "{\"riderId\":\"$KID\",\"token\":\"$KTOK\"${2:+,$2}}"; }
ride_json() { curl -s -m 15 "$SERVER/api/rides/$CODE"; }
# Kabir's phone keeps reporting every few seconds, like a real rider, from wherever /tmp/kabir_pos says he is.
echo "12.9718 77.5944 5" > /tmp/kabir_pos
( while [ ! -f /tmp/kabir_stop ]; do read la lo sp < /tmp/kabir_pos; kabir location "\"lat\":$la,\"lon\":$lo,\"acc\":8,\"speed\":$sp" >/dev/null; sleep 3; done ) &
# Find a control inside the ride panel by scrolling the panel itself (not the map above it).
sheet_find() { for i in 1 2 3 4 5 6; do [ -n "$(find_node "$1")" ] && return 0; swipe 50 66 50 52 350; sleep 0.5; done; return 1; }

# ---- 5 contacts ------------------------------------------------------------------------------
scene_start 05-contacts
nav settings
scroll_to "Pick from contacts"; swipe 50 60 50 45 400; sleep 0.5
tap_text "Pick from contacts"; sleep 4
tap_text "Dad"; sleep 3
scroll_to "Add contact"; swipe 50 62 50 42 400; sleep 0.5
tap_text "Name" && type_text "Mom"; sleep 0.4
tap_text "Phone number" && type_text "98765 43210"; sleep 0.6
hide_kb; sleep 0.4
tap_text "Add contact"; sleep 1.5
scene_end

# ---- 6 call / message / test ---------------------------------------------------------------
scene_start 06-call-message
for i in 1 2 3 4; do [ -n "$(find_node "Call Mom")" ] && break; swipe 50 35 50 65 400; sleep 0.5; done
tap_text "Call Mom"; sleep 6
back_to_armor 1
for i in 1 2 3 4; do [ -n "$(find_node "Message Mom")" ] && break; swipe 50 35 50 65 400; sleep 0.5; done
tap_text "Message Mom"; sleep 7
back_to_armor 1
for i in 1 2 3 4; do [ -n "$(find_node "Send test SOS text")" ] && break; swipe 50 35 50 65 400; sleep 0.5; done
tap_text "Send test SOS text"; sleep 3
scene_end

# ---- 7 rest of settings -----------------------------------------------------------------------
scene_start 07-settings
scroll_top; sleep 1.5
swipe 50 70 50 40 900; sleep 1.5
scroll_to "Blood group"; tap_text "Blood group" && type_text "O+"; hide_kb; sleep 0.5
tap_text "Allergies" && type_text "None"; hide_kb; sleep 1
swipe 50 70 50 40 900; sleep 1.5
scene_end

# ---- 8 ride start ------------------------------------------------------------------------------
scene_start 08-ride-start
nav home
for i in 1 2 3; do b=$(find_node "SLIDE TO RIDE"); [ -n "$b" ] && break; swipe 50 75 50 30 300; sleep 1; done
set -- ${b:-0 0 0 0}; y=$(( ($2 + $4) / 2 ))
adb shell input swipe $((W*12/100)) $y $((W*96/100)) $y 1100
for i in $(seq 1 8); do geo $(python3 -c "print(77.5946 + $i*0.00015, 12.9716 + $i*0.00008)"); echo "$(python3 -c "print(12.9714 + $i*0.00008, 77.5942 + $i*0.00015)") 7" > /tmp/kabir_pos; sleep 1.3; done
echo "12.97205 77.59565 0" > /tmp/kabir_pos
scene_end

# ---- 9 map views -------------------------------------------------------------------------------
scene_start 09-map
for v in Night Terrain Satellite Hybrid Map; do tap_text "$(cat /tmp/curview 2>/dev/null || echo Map)" >/dev/null || true; sleep 1; tap_text "$v"; echo "$v" > /tmp/curview; sleep 2.2; done
swipe 50 25 25 32 700; sleep 1
tap_text "Zoom in"; sleep 0.7; tap_text "Zoom in"; sleep 1.2; tap_text "Zoom out"; sleep 0.7; tap_text "Zoom out"; sleep 1
tap_text "Recentre"; sleep 1
tap_text "Hybrid" && sleep 1 && tap_text "Map"; sleep 1.5
scene_end

# ---- 10 panel ----------------------------------------------------------------------------------
scene_start 10-panel
b=$(find_node "Hide controls"); set -- ${b:-0 0 0 0}; hy=$(( ($2 + $4) / 2 ))
[ "$hy" -gt 0 ] && adb shell input swipe $((W/2)) $hy $((W/2)) $((hy + 500)) 450
sleep 4; tap_text "Show controls"; sleep 2.5
scene_end

# ---- 11 chat -----------------------------------------------------------------------------------
scene_start 11-chat
tap_text "Chat"; sleep 2
tap_text "Fuel is low"; sleep 1.5
tap_text "Message the group" && type_text "Pump in 5 km, see you there"; sleep 0.6
b=$(find_node "Message the group"); set -- ${b:-0 0 0 0}; adb shell input keyevent 66; sleep 0.5
tap_text "›" >/dev/null; sleep 1.5
kabir message '"text":"Got it, right behind you"' >/dev/null; sleep 5
hide_kb; sleep 0.8
adb shell input keyevent 4; sleep 1.5
front
scene_end

# ---- 12 need a hand ----------------------------------------------------------------------------
scene_start 12-help
tap_text "Need a hand"; sleep 4
HID=$(ride_json | python3 -c "import json,sys;d=json.load(sys.stdin);a=[x['id'] for x in d['alerts'] if x['kind']=='HELP' and not x.get('resolvedAt')];print(a[-1] if a else '')" 2>/dev/null)
[ -n "$HID" ] && kabir ack "\"alertId\":\"$HID\"" >/dev/null
sleep 6
tap_text "Kabir coming" || tap_text "Cancel help"; sleep 2
scene_end

# ---- 13 lead: team stop ------------------------------------------------------------------------
scene_start 13-lead
tap_text "Show details"; sleep 2
sheet_find "Team stop"; tap_text "Team stop"; sleep 1.5; tap_text "Fuel"; sleep 4
SID=$(ride_json | python3 -c "import json,sys;d=json.load(sys.stdin);print((d.get('teamStop') or {}).get('id',''))" 2>/dev/null)
[ -n "$SID" ] && kabir confirmStop "\"stopId\":\"$SID\"" >/dev/null
sleep 5
tap_text "Ride on"; sleep 2.5
tap_text "Just me"; sleep 1.5; tap_text "Break"; sleep 2.5
scene_end

# ---- 14 tools ----------------------------------------------------------------------------------
scene_start 14-tools
sheet_find "Report hazard"; tap_text "Report hazard"; sleep 1.5; tap_text "Pothole"; sleep 2.5
tap_text "Off the bike"; sleep 2.5; tap_text "Back on bike"; sleep 1.5
tap_text "Regroup here"; sleep 2.5
sheet_find "Repair"; tap_text "Fuel"; sleep 2; tap_text "Fuel"; sleep 1.5
sheet_find "Hide details"; tap_text "Hide details"; sleep 1.5
scene_end

# ---- 15 voice ----------------------------------------------------------------------------------
scene_start 15-voice
tap_text "Talk to Armor"; sleep 7
scene_end

# ---- 16 log ------------------------------------------------------------------------------------
scene_start 16-log
nav log; sleep 2; swipe 50 70 50 35 900; sleep 2
nav home
scene_end

# ---- 17 SOS ------------------------------------------------------------------------------------
scene_start 17-sos
long_press "Hold for SOS" 1900; sleep 5
# ARMOR starts ringing the nearest rider and the contacts through the phone app; hang up and return to show the SOS screen.
sleep 4; back_to_armor; sleep 3
tap_text "cancel SOS" || tap_text "I'm okay"; sleep 1.5; back_to_armor 1; sleep 3
scene_end

# ---- 18 automatic safety check -----------------------------------------------------------------
echo "12.9690 77.5930 0" > /tmp/kabir_pos
front
for i in $(seq 1 70); do [ -n "$(find_node "I'm okay")" ] && break; sleep 3; done
scene_start 18-safety
sleep 7; tap_text "I'm okay"; sleep 2.5
scene_end

# ---- 19 end ride -------------------------------------------------------------------------------
scene_start 19-end
tap_text "End"; sleep 2; tap_text "End ride"; sleep 4
swipe 50 60 50 40 600; sleep 3
scene_end

# ---- 20 outro ----------------------------------------------------------------------------------
scene_start 20-outro
tap_text "Done"; sleep 4
scene_end

touch /tmp/kabir_stop
adb logcat -d -s AndroidRuntime:E | grep -A8 "FATAL EXCEPTION" | head -30
ls -la "$OUT"

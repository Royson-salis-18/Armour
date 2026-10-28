# ARMOR — Advanced Rider Monitoring & Orientation Reasoning System

A safety app for motorcycle group rides. It watches the rider's own phone (GPS, accelerometer, gyroscope), notices
when someone is separated from the group and stopped, or has crashed, and escalates through fixed rules and timers
until help is alerted — **with no internet needed** and no extra hardware.

> Built from the *ARMOR Software Engineering Report* and the *A.R.M.O.R.S* deck. Android · Kotlin · Jetpack Compose · Room.

## What it does

| | |
|---|---|
| **Group awareness** | Live positions on an offline radar-style map, with the group's boundary drawn and separated riders flagged |
| **Stopped & separated?** | Asks "Are you OK?" → alerts the group if there's no answer → texts your emergency contacts if nobody acknowledges |
| **Crash detection** | High-g impact + sudden orientation change from the accelerometer and gyroscope → 30 s cancellable countdown → SOS |
| **Offline SOS** | SOS goes out as an **SMS with a map link** (needs cell signal, not internet), retried if it fails, with a follow-up if you cancel |
| **Quick status** | One tap to say Fuel / Break / Mechanical / Waiting / Photo — which also pauses the "are you OK?" check |
| **Voice routing** | Shows whether group voice would use the mobile network (online) or your helmet intercom (offline). ARMOR doesn't carry the audio itself; safety features run either way |
| **Audit trail** | Every decision is logged with its reason, stored on the phone, synced when online |

Emergency decisions are **never made by a learned model**. See [docs/SAFETY_LOGIC.md](docs/SAFETY_LOGIC.md) for the
exact rules, timers and failure modes.

## Try it in two minutes (no GPS, no second phone)

1. Open the repo root in **Android Studio** (JDK 17) and run the `app` configuration on an emulator or phone.
2. Tap **TRY A DEMO RIDE**. Four simulated riders cruise along a winding road with you.
3. Use the **Demo controls**:
   - **Stop my bike** → watch *Separated* → *Are you OK?* → *Group alerted* → *SOS sent* (the SMS text is shown, never sent in demo).
   - **Peers acknowledge: on/off**, then stop your bike → a rider "comes to help" and the SOS is held back.
   - **Crash!** → feeds a realistic impact + tumble through the *real* detector → 30 s countdown.
   - **Kabir stops** → his alert reaches you full-screen; tap **I'M GOING** to acknowledge it.
   - **Everyone rides** resets the scenario.

The demo uses shortened timers (8–15 s) and runs the same engine as a real ride; only the data source is simulated.

## Real rides

1. **Settings** → add at least one emergency contact (with country code) and check the emergency number (default `112`).
2. **Group** → enter your name. For a group, one rider taps *Create a new code* and shares it; everyone enters the same code and the same
   **Firebase Realtime Database URL** (see below). Solo riding and offline use need neither.
3. **Ride** → *Start ride*, allow Location, SMS and Notifications. **End the ride when you park** — otherwise a long stop is
   treated as a possible emergency.

### Online group sync (optional)

ARMOR talks to a Firebase Realtime Database over plain HTTPS/JSON, so there is no SDK, no `google-services.json`, and no
Play Services dependency.

1. Create a Firebase project → *Realtime Database* → copy its URL (`https://<project>-default-rtdb.firebaseio.com`).
2. Paste it into **Group → Firebase Realtime Database URL** on every rider's phone.
3. **Security:** the database is open to anyone with the URL until you set rules. "Test mode" is fine for trying it out;
   do not leave it that way. The layout it writes is documented in
   [`FirebaseRestTransport`](app/src/main/kotlin/com/armor/app/net/FirebaseRestTransport.kt).

## Build, test, run

```bash
./gradlew :core:test                    # all the safety logic: 100 tests, JVM only, needs no Android SDK
./gradlew :app:testDebugUnitTest        # demo-ride + Firebase-transport tests (Android SDK required)
./gradlew :app:assembleDebug            # the APK
./gradlew :core:test -Parmor.coreOnly=true   # core only, without configuring the Android module
```

Requirements: JDK 17+, Android Studio with SDK platform 35. Min SDK 26 (Android 8.0).

## Project layout

```
core/    Plain Kotlin/JVM. ALL safety-critical logic. No Android, no clock, no network. Fully unit-tested.
  geo/       distance, bearing, local projection, convex hull
  group/     ClusterAnalyzer — who is "with the group"
  motion/    StationaryDetector — is the rider stopped
  crash/     CrashDetector + CrashFilter (+ the CrashClassifier seam for an ML model)
  safety/    SafetyStateMachine — Normal → Verifying → GroupAlerted → SOS, and the crash countdown
  engine/    RideEngine — wires the above; the one class the Android layer talks to
  comms/     CommsPlanner — which transport carries voice
  sos/       SosMessages — the SMS text

app/     Android. A thin shell around :core.
  service/   RideService (foreground, location-type), ArmorRuntime (service ↔ UI bridge), ActionReceiver
  sensors/   GPS (LocationManager), accelerometer + gyroscope, Bluetooth audio, RideClock
  alert/     alarm + vibration, notifications, SMS sender
  net/       GroupTransport + Firebase REST implementation, connectivity monitor
  data/      Room (events, contacts), settings
  sim/       DemoRide — the scripted group ride
  ui/        Compose screens (dark theme, orange accents)
```

The split is deliberate: the logic that can hurt someone if it is wrong lives where it can be tested on any machine,
replayed second by second, and reasoned about without a phone.

## Requirements traceability

| Requirement (report / deck) | Where |
|---|---|
| Real-time rider tracking | `GpsLocationSource`, `RideEngine`, `RiderMap`, `GroupTransport` |
| Detect group clusters and outliers | `ClusterAnalyzer` |
| Identify stationary riders automatically | `StationaryDetector` |
| Verification timer → group alert → SOS | `SafetyStateMachine` |
| Quick rider status sharing | `QuickStatus`, quick-status row, published in `RiderReport` |
| Crash detection from accelerometer + gyroscope, no images | `CrashDetector` |
| AI only to reduce false positives | `CrashFilter` + `CrashClassifier` (can only dismiss, never override a stopped bike) |
| Deterministic, explainable emergency logic | state machine + event log with reasons |
| Offline-first; sync when connectivity returns | all logic on-device; Room log with `synced` flag; alert outbox |
| Escalate reliably without internet | SMS SOS with delivery reports and retries (`SmsSender`, `RideService`) |
| Hybrid voice: mobile network online, intercom offline | `CommsPlanner`, `BluetoothAudioMonitor`, Settings toggle |
| Dark theme, orange accents, group map, cluster boundaries, quick buttons, persistent SOS, big touch targets | `ui/` — SOS (hold 1.5 s) is on the Ride screen |
| Android · Kotlin · Compose · Sensor APIs · Location · Room · Firebase RTDB | as specified |
| State machines / layered / event-driven (deck) | `SafetyStateMachine`; UI → service → engine → effects |

### Where this deliberately differs from the documents

- **No Google Maps.** The deck lists Maps/Places/Directions. The map here is an offline radar view (relative geometry, no
  tiles, no API key) because the app has to work with no internet. Real map tiles would be an add-on, not a replacement.
- **Firebase via REST, no MQTT.** Fewer moving parts and no build-time secrets. MQTT was not implemented.
- **TensorFlow Lite is a seam, not a model.** `CrashClassifier` is where a trained model plugs in; none ships, because
  there is no training data. Without one, ambiguous impacts simply get the cancellable countdown.
- **Voice is routed, not carried.** ARMOR decides and shows which transport applies (mobile network vs. intercom) and
  keeps safety running in both. It does **not** implement a real-time audio call; that would be a separate project (WebRTC or similar).
- **"Overlay" alerts** use a full-screen takeover plus a full-screen-intent notification, not the `SYSTEM_ALERT_WINDOW` permission.

## How it was verified — and what wasn't

| | Status |
|---|---|
| `:core` — geometry, clustering, stationary, crash, filter, state machine, full-ride scenarios, SOS text | ✅ 100 tests, run here. Deliberately breaking six key rules makes tests fail. |
| `:app` demo ride through the real engine; Firebase transport against an in-process Firebase-style server | ✅ 18 tests, run here via a harness |
| `:app` Kotlin sources, incl. every Compose screen | ✅ compiled with the Compose compiler plugin against the real Android framework classes and real Compose APIs. It caught a genuine bug (`getSystemService` is nullable in the API 35 framework) |
| **Full Android build (AGP, Room codegen, resources, manifest merge)** | ❌ **Not run.** The Android Gradle Plugin and SDK are not downloadable in the environment this was written in. First sync in Android Studio may need small version or config fixes. |
| **On a device / emulator** | ❌ **Never run.** No screenshots were taken; layouts have not been looked at. |
| Real GPS, real sensors, real SMS, real Firebase | ❌ Not exercised. |
| Crash thresholds, GPS-radius and timing defaults | ❌ **Untuned.** They are reasoned starting points. Use the checklist in [docs/SAFETY_LOGIC.md](docs/SAFETY_LOGIC.md#field-testing-checklist-do-this-before-relying-on-it). |

**Do not rely on this to protect anyone until it has been field-tested.** It is a mini-project; treat it as one.

## Known limitations

- **Battery.** Continuous GPS plus 50 Hz sensors for crash detection is costly. Expect noticeable drain on a long ride.
- **Phone placement.** Crash detection assumes the phone is on the bike or the rider. A phone in a loose tank bag sees different shocks.
- **OEM battery savers** (Xiaomi, Huawei, Samsung…) can still stop a foreground service. Exclude ARMOR from battery optimisation.
- **Android 14+** requires you to allow *full-screen notifications* for alerts to light the lock screen (Settings → Apps → ARMOR → Notifications).
- **SMS permission** is a restricted permission on Google Play: fine for sideloading and a course project, not something that can simply be published to the Play Store.
- **Group data is only as fresh as the network.** Offline, other riders' positions go stale after 60 s and the group alert cannot be delivered; your own SOS text still can.
- **Clock skew** between phones affects staleness checks; phones that are NTP-synced are fine.
- Demo-only: simulated riders follow a fixed script.

## Google Maps

The ride screen uses a Google map when a key is configured, and the built-in offline map otherwise (or when offline).
Put your key in `local.properties` (git-ignored) as `MAPS_API_KEY=...`, or set the `MAPS_API_KEY` environment variable,
and enable "Maps SDK for Android" for it in Google Cloud. The map code (`ui/GoogleRiderMap.kt`) has only been
compile-checked against stand-ins for the maps libraries here; it has not been run with a real key or on a device.

On the ride screen the **Map** chip (bottom right of the map) switches between Map, Night, Terrain, Satellite and Hybrid. Terrain, Satellite and Hybrid are Google's built-in map types.

## Group rides on the ARMOR server

Instead of Firebase, riders can use the ARMOR FastAPI server (the `backend/` of the ARMOR repo). On the **Group** tab, enter
the server URL, then **Create ride** (you become the admin: you can start and end the ride for everyone) or **Join ride**
with the 6-character code. Positions, rider status, alerts, acknowledgements and the audit trail all go through that server.

Notes from running against the real server:

- It has no push channel, so the app polls.
- It has no alert concept, so alerts travel as `events` (`alert`, `acknowledgement`, and a `status_update` carrying
  `resolvedAlertId`); the newest event for an alert id wins.
- It answers 404 when a rider's status is set to the value it already has, which the client treats as success.
- It has **no authentication**: anyone who knows a ride's code, or its ids, can read it and post to it. Put it behind
  authentication before using it for real rides.
- Timestamps are naive UTC from the server's clock; a badly wrong server clock makes riders look stale.

`ArmorBackendIntegrationTest` runs the whole flow (create, join, positions, alert, acknowledge, resolve, end) against a live
server when `ARMOR_BACKEND_URL` is set, and is skipped otherwise. I ran it against the unmodified `backend/server.py`
with an in-memory MongoDB stand-in, not against a deployed server.

## Also in the ride screen

- **Need a hand?** asks the group for non-emergency help (fuel, a puncture). It never escalates to an SOS by itself and
  shows who is coming; cancel any time.
- **Ride summary** appears when a ride ends: distance, time, moving time, top and average moving speed.

## Lead, team stops, chat and voice

The person who creates a ride is its **lead** (the server records the creator; `GroupData.leadId`). On the ride screen:

- **Team stop** (lead): pick a reason (fuel, break, food, mechanical, weather, other). Every rider gets a full-screen alert
  and must confirm **I'm stopping**; the alert stays up until they do, even if someone else already has. Confirming sets
  the rider's stop status, so no "are you okay?" check fires while parked. The lead sees who has stopped and who is still
  moving, and ends it with **Ride on**, which clears everyone's stop status.
- **Just me** (lead): the lead stops alone; the group is told why and keeps riding. Any rider has the same **I'm stopping**.
- **Chat**: group messages with quick replies. Messages are de-duplicated by id, capped at 280 characters.
- **Voice**: the mic uses the phone's own speech recogniser (offline where the language pack is installed) and
  `VoiceCommands` maps the words to an action with plain phrase matching, not a model: it is deterministic and tested.
  Stops and messages need a tap to confirm; "I'm okay" is immediate; an **SOS is never sent by voice**.

Over the ARMOR server these travel as `events` (team stop = an `alert` event with `detail`, chat = a `status_update`
event carrying `message`). Over Firebase they live under `groups/{code}/messages` and `alerts`.

**Not built yet:** a pre-ride lobby that waits for the lead to start (the lobby exists only in the browser prototype), and
the lead handing over the lead role. The Android app's Group tab can create or join a ride, but starting the ride is still
each rider's own slide-to-ride.

A clickable browser prototype of the whole flow is in `docs/prototype/index.html` (open it in a browser). It simulates the
group and uses shortened timings; it is not the Android app.

## Safety features modelled on real rider apps

Researched against Life360 (crash detection with "are you OK?", silent SOS), EatSleepRIDE CrashLight (crash detection,
live location to family), REALRIDER / Triumph SOS (group rides, auto-pause against false alarms) and REVER (live friend
tracking). Added:

- **Hazard reporting** (pothole, oil, accident, animal, police, roadwork) shown to the group on the map, never as a
  full-screen interruption.
- **Low-battery warning** to the group once at 15% (cleared when charging): a dead phone means no crash detection.
- **Regroup point** set by the lead, with a one-tap **Navigate in Google Maps**.
- **Off the bike** pauses crash detection (walking, cafe stop) and resumes by itself above 6 m/s.
- **Silent SOS**: texts go out, no siren.
- **Medical ID** (blood group, allergies, conditions, medications) on the SOS screen and as a second SOS text.
- **Fatigue reminder** after 2 h of riding without a 10-minute break.
- **Safe-arrival watch**: contacts get "arrived safely", or "running late" 20 min past the expected time (core and
  service are done; the Android screen to set a destination is not built yet).
- **Roadside help** number and button.
- **Google Maps hand-off**: incoming alerts, hazards and the regroup point open turn-by-turn navigation in the Google
  Maps app (or the Maps web link if the app is missing).

## Web app (live demo build)

`web/` is a phone-browser version of ARMOR that works like the real app: real GPS,
a live OpenStreetMap map with every rider, ride codes and invite links, lobby
chat, lead-controlled team stops, "wait for me" catch-up requests, hazards,
regroup points, destinations with routes, SOS with crash detection, Google Maps
directions, and hands-free control over a Bluetooth helmet intercom (voice,
helmet buttons, air gestures). The states, buttons and transitions are
specified in [docs/ride-flows.md](docs/ride-flows.md).

```bash
cd web
npm test          # ride state machine tests
npm start         # http://localhost:8080
```

Six map views are available from the layers button: Map, Night, Streets, Terrain (OpenTopoMap contours), Satellite and
Hybrid (satellite with place names). The Map and Night views are OpenFreeMap vector tiles (OpenStreetMap data, no key) recoloured in code into a quiet
ride-hailing style: pale land, white roads, a dark route line and a distance label on the destination. If vector
tiles can't load it falls back to plain OpenStreetMap tiles. The Android app uses the same look through
`app/src/main/res/raw/map_style_light.json` on Google Maps.

It has no dependencies (Node 20+). Rides are kept in memory, so restarting the
server clears them. GPS, the microphone and the camera need HTTPS, so test on
phones through a deployed URL (Render, for example: build command `npm install`,
start command `node server.js`, root directory `web`).

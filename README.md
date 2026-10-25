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

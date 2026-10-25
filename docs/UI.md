# ARMOR — UI guide

> **About these images.** They are **design previews**, rendered from HTML that mirrors the Jetpack Compose layouts
> (same colours, wording and structure as `app/src/main/kotlin/com/armor/app/ui/`). They are **not captures from a
> device or emulator** — the Android build could not be run where this was written. Real spacing and the map's height
> will differ slightly (on a phone the map expands to fill the free space). The screens are produced from the same
> states the demo ride drives, so what you see on the demo should match them.

**Look and feel:** near-black background with a warm glow at the top, orange accents, rounded cards with hairline borders. Red and amber are reserved for safety states so they always mean something.
Controls are large and few — the rider may be wearing gloves.

## Navigation

Four tabs along the bottom: **Ride · Group · Events · Settings**. Anything urgent covers the whole screen (see "Alerts").

## Ride tab

| Before the ride | During a ride | Separated from the group |
|---|---|---|
| ![pre-ride](screens/01_pre_ride.png) | ![ride](screens/02_ride_with_group.png) | ![separated](screens/03_ride_separated.png) |

- **Before the ride:** a "Ready to ride?" card with green ✓ / amber ! for GPS, SMS, emergency contacts and group; a large **START RIDE** button and a quieter **Try a demo ride**.
- **Header:** the ARMOR wordmark, chips for GPS accuracy, online/offline and voice route, and **End** to finish the ride.
- **Status card:** one glance tells you where you stand — green *With the group* (how many riders, how spread out), red *Separated from the group* (how long you've been stopped), grey *Riding solo*. Your **speed** is on the right.
- **Radar map** (offline, north-up, centred on you): dashed range rings, the group's boundary as a soft blob, riders as lettered badges with name tags, a red ring on anyone who has fallen out of the group, greyed "last seen" riders.
- **Rider cards:** each other rider's initial, distance and direction (or the status they've set).
- **Quick status tiles** (one tap, tap again to clear): ⛽ Fuel, ☕ Break, 🔧 Repair, 🚦 Wait, 📷 Photo. Each pauses the "are you OK?" check for 15 minutes.
- **HOLD FOR SOS:** press and hold ~1.5 s (a progress fill shows it charging) so a bump or glove can't trigger it. Always on this screen.

## Alerts (full-screen)

| Are you OK? | Crash detected | SOS sent | A rider needs help |
|---|---|---|---|
| ![ok](screens/04_are_you_ok.png) | ![crash](screens/05_crash_countdown.png) | ![sos](screens/06_sos_sent.png) | ![help](screens/07_group_alert_incoming.png) |

- **Are you OK?** (amber) — you stopped away from the group. A **countdown ring** shows the time left; **I'M OK** cancels it, and with no answer the group is alerted.
- **Crash detected** (red) — ring countdown to SOS; **I'M OK — CANCEL** stops it.
- **SOS sent** (red) — a card shows whether the texts went out, **CALL 112** (your configured number), and **I'M OK — CANCEL SOS**, which also texts a follow-up to your contacts.
- **A rider needs help** (orange) — shown to the *other* riders: who, and a large distance and direction. **I'M GOING** tells the group you're responding (and holds back their SOS timer).

Alerts also light the lock screen, sound a looping alarm at full volume and vibrate.

## Group, Events, Settings

| Group | Events | Settings |
|---|---|---|
| ![group](screens/08_group.png) | ![events](screens/09_events.png) | ![settings](screens/10_settings.png) |

- **Group:** your group code shown large for sharing, your name, the 6-character code field (create or leave), the optional online-sync server, and who is in the group right now.
- **Events:** the audit trail as a timeline — every decision ARMOR made and *why*, colour-coded, kept on the phone.
- **Settings:** emergency contacts (who gets the SOS text), the number the Call button dials, crash sensitivity (Low 5 g / Medium 4 g / High 3 g), solo monitoring, keep-screen-on, and whether you use a helmet intercom.

## Demo ride controls

On **Ride** during a demo, a panel offers: *Stop my bike*, *Crash!*, *Kabir stops*, *Everyone rides*, and *Peers acknowledge: on/off* — enough to walk through every alert above in under a minute.

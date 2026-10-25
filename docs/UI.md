# ARMOR — UI guide

> **About these images.** They are **design previews**, rendered from HTML that mirrors the Jetpack Compose layouts
> (same colours, wording and structure as `app/src/main/kotlin/com/armor/app/ui/`). They are **not captures from a
> device or emulator** — the Android build could not be run where this was written. Real spacing and the map's height
> will differ slightly (on a phone the map expands to fill the free space). The screens are produced from the same
> states the demo ride drives, so what you see on the demo should match them.

**Look and feel:** dark background, orange accents. Red and amber are reserved for safety states so they always mean something.
Controls are large and few — the rider may be wearing gloves.

## Navigation

Four tabs along the bottom: **Ride · Group · Events · Settings**. Anything urgent covers the whole screen (see "Alerts").

## Ride tab

| Before the ride | During a ride | Separated from the group |
|---|---|---|
| ![pre-ride](screens/01_pre_ride.png) | ![ride](screens/02_ride_with_group.png) | ![separated](screens/03_ride_separated.png) |

- **Before the ride:** a checklist (GPS, SMS, emergency contacts, group), **START RIDE**, and **TRY A DEMO RIDE** (no GPS, no real messages).
- **Status pills:** GPS accuracy (or "GPS lost"), Online/Offline, and which voice route applies (mobile network / helmet intercom / none).
- **Banner:** green *With the group*, red *Separated from the group* (plus how long you've been stopped), grey *Riding solo*.
- **Radar map** (works offline, north-up, centred on you): rings show distance; the dashed blob is the group's boundary; a red ring marks a rider who has fallen out of the group; greyed riders are "last seen".
- **Rider chips:** each rider's distance and direction, and any status they've set.
- **Quick status** (one tap; tap again to clear): Fuel stop, Break, Mechanical issue, Traffic / waiting, Photo stop. Each pauses the "are you OK?" check for 15 minutes.
- **HOLD FOR SOS:** press and hold ~1.5 s so a bump or glove can't trigger it. Always on this screen.

## Alerts (full-screen)

| Are you OK? | Crash detected | SOS sent | A rider needs help |
|---|---|---|---|
| ![ok](screens/04_are_you_ok.png) | ![crash](screens/05_crash_countdown.png) | ![sos](screens/06_sos_sent.png) | ![help](screens/07_group_alert_incoming.png) |

- **Are you OK?** (amber) — you stopped away from the group. **I'M OK** cancels it; with no answer the group is alerted.
- **Crash detected** (red) — countdown to SOS; **I'M OK — CANCEL** stops it.
- **SOS sent** (red) — shows whether the texts went out, **CALL 112** (your configured number), and **I'M OK — CANCEL SOS**, which also texts a follow-up to your contacts.
- **A rider needs help** (orange) — shown to the *other* riders: who, how far and which direction. **I'M GOING** tells the group you're responding (and holds back their SOS timer).

Alerts also light the lock screen, sound a looping alarm at full volume and vibrate.

## Group, Events, Settings

| Group | Events | Settings |
|---|---|---|
| ![group](screens/08_group.png) | ![events](screens/09_events.png) | ![settings](screens/10_settings.png) |

- **Group:** your name, a 6-character group code (create or leave), the optional online-sync server, and who is in the group right now.
- **Events:** the audit trail — every decision ARMOR made and *why*, with timestamps, kept on the phone.
- **Settings:** emergency contacts (who gets the SOS text), the number the Call button dials, crash sensitivity (Low 5 g / Medium 4 g / High 3 g), solo monitoring, keep-screen-on, and whether you use a helmet intercom.

## Demo ride controls

On **Ride** during a demo, a panel offers: *Stop my bike*, *Crash!*, *Kabir stops*, *Everyone rides*, and *Peers acknowledge: on/off* — enough to walk through every alert above in under a minute.

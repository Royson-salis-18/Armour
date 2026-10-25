# ARMOR — UI guide

> **About these images.** They are **design previews**, rendered from HTML that mirrors the Jetpack Compose UI in
> `app/src/main/kotlin/com/armor/app/ui/` (same colours, wording, structure and proportions). They are **not captures
> from a device or emulator**: the Android build could not be run where this was written. Real spacing will differ
> a little (on a phone the map grows to fill free space, and the radar sweep and glows animate).

## Design language: "ARMOR System"

A rider's heads-up display, inspired by the status windows and quest alerts of *Solo Leveling* and by navigation-app maps.

- **Chrome:** cut-corner holographic panels with gradient hairline borders and a soft glow; `[ BRACKETED ]` small-caps labels; monospace readouts.
- **Palette:** deep navy-black with a cyan glow top-left and violet bottom-right; **cyan** for system chrome, **orange** for riders and the brand, and **red / amber / green** reserved for safety states so they always mean something.
- **Motion:** a slow radar sweep on the map; countdown rings whose ticks go dark as time runs out.
- **Controls:** large, few, and forgiving. SOS is press-and-hold with a charge bar so a bump or glove can't trigger it.

Everything is built from a handful of reusable pieces in `ui/Hud.kt` — `HudPanel`, `HudLabel`, `GlowText`, `SegmentBar`, `HexBadge`, `SignalBars`, `TickRing`, `HudButton`.

## Ride tab

| System check | Riding with the group | Separated |
|---|---|---|
| ![pre-ride](screens/01_pre_ride.png) | ![ride](screens/02_ride_with_group.png) | ![separated](screens/03_ride_separated.png) |

- **System check:** a hex emblem and a checklist (location, SOS messaging, emergency contacts, group link) with ✓ / ! diamonds, a glowing **INITIATE RIDE**, and **TRY DEMO RIDE** (no GPS, no real messages).
- **Header:** logo, `// SYSTEM ONLINE`, and three signal meters — **GPS** (accuracy), **NET**, **VOICE** (mobile network / intercom / none). **END** finishes the ride.
- **Tactical map** (works offline): north-up and centred on you, with a compass bezel, dashed range rings, a rotating radar sweep, **your route as a glowing trail**, riders as hex badges with name tags and **heading cones**, and the group's boundary as a glowing hologram. A rider who has fallen out of the group gets a red ring; riders not heard from recently turn grey and read "last seen". The panel's border takes the colour of your link state.
- **Velocity:** a large glowing speed readout with a segmented gauge and GPS accuracy.
- **Link:** `STABLE` (green) / `LOST` (red) / `NO LINK` or `SOLO` (grey) / `ASSISTED` (amber) / `CHECKING`, with a one-line reason (for example "Stopped 0:42"), a segment bar of riders in formation and the group's spread.
- **Rider cards:** hex badge, name, and distance + direction (or the status they've set).
- **Quick status:** ⛽ Fuel · ☕ Break · 🔧 Repair · 🚦 Wait · 📷 Photo (tap again to clear). Each pauses the "are you OK?" check for 15 minutes.
- **HOLD FOR SOS:** hold ~1.5 s; a white charge bar fills along the bottom edge.

## Alerts (full-screen)

| Are you OK? | Crash detected | SOS sent | A rider needs help |
|---|---|---|---|
| ![ok](screens/04_are_you_ok.png) | ![crash](screens/05_crash_countdown.png) | ![sos](screens/06_sos_sent.png) | ![help](screens/07_group_alert_incoming.png) |

Each alert dims everything else, tints the screen with its colour, and shows a framed notice with a tag (`SYSTEM NOTICE`, `EMERGENCY PROTOCOL`, `SOS ACTIVE`, `ALLY IN DISTRESS`).

- **Are you OK?** (amber): a 60-tick countdown ring, then **What happens next** — your group is alerted, the group gets a minute, SOS is texted. **I'M OK** cancels.
- **Crash detected** (red): countdown to SOS; **I'M OK — CANCEL**, or hold to send now.
- **SOS sent** (red): delivery status, **CALL 112** (your configured number) and **I'M OK — CANCEL SOS**, which also texts your contacts a follow-up.
- **A rider needs help** (orange, shown to *other* riders): who, a large distance and a bearing. **I'M GOING** tells the group you're responding.

Alerts also light the lock screen, play a looping alarm at full volume and vibrate.

## Group, Log, System

| Group | Log | System |
|---|---|---|
| ![group](screens/08_group.png) | ![log](screens/09_events.png) | ![system](screens/10_settings.png) |

- **Group:** your access code in a large glowing readout, your callsign, and the optional online-sync server.
- **Log:** every decision ARMOR made and *why*, as a colour-coded timeline, kept on the phone.
- **System:** emergency contacts, the number the Call button dials, crash sensitivity (Low 5 g / Med 4 g / High 3 g), solo monitoring, keep-screen-on and intercom.

## Demo ride controls

During a demo, a panel offers *Stop my bike*, *Crash!*, *Kabir stops*, *Everyone rides* and *Peers acknowledge: on/off* — enough to walk through every alert above in under a minute.

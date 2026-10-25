# ARMOR — UI guide

> **About these images.** They are **design previews**, rendered from HTML that mirrors the Jetpack Compose UI in
> `app/src/main/kotlin/com/armor/app/ui/` (same colours, wording, structure and proportions). They are **not captures
> from a device or emulator**: the Android build could not be run where this was written. Real spacing will differ a
> little. Tokens and components are documented in [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md).

## Design direction

A bespoke ARMOR identity rather than a stock dark theme: a shield-and-chevron **logo**, a drawn **rider-on-bike hero**
over a dotted "sound-wave" orb, **Barlow Condensed** capitals for headlines and numerals (instrument-cluster feel) with
**Inter** for reading text, **glass cards** with a lit top edge, a warm **glow** falling from the top of every screen
(orange normally, red in emergencies), a **tachometer dial** for speed, a **convoy strip** showing who is ahead and
behind, and a floating pill tab bar. **Only orange, red, black and grey** (plus white for text): orange means *brand and
attention*, red means *danger*. Art is drawn as vector paths in code, with no stock imagery or emoji.

> The images below are HTML **previews** of the Compose screens (same tokens, fonts and path data), not device captures.

A rider's phone is on a handlebar, so: large touch targets, very few controls per screen, and one obvious primary action.

## Ride tab

| Start | Riding with the group | Separated |
|---|---|---|
| ![start](screens/01_pre_ride.png) | ![ride](screens/02_ride_with_group.png) | ![separated](screens/03_ride_separated.png) |

- **Start:** wordmark, the rider hero over the dot orb, a two-tone headline ("Ride together. **Come home together.**"), three readiness chips (location, SOS text, contacts) and a **slide-to-ride** control (a stray tap or pocket can't start a ride). **Try a demo ride** sits below.
- **Map (full-bleed):** north-up and centred on you; works offline. Soft range rings over a faint street-grid texture, **your route as a glowing orange line**, you as the orange marker with a heading cone, other riders as grey avatars with a white ring, and the group's area as a soft white blob. A rider who has fallen away gets a red **!** badge. Chips float over the map for GPS and connection status, and **End**.
- **Bottom sheet:**
  - **Status:** "With the group · 5 riders within 230 m", or "Separated", "Riding solo", "<name> is on the way", with a **tachometer dial** (ticks every 5 km/h, red from 100).
  - **Convoy strip:** every rider placed along the direction of travel (ahead on the right). A rider who has dropped off gets a red ring and a dashed red link back to the group, labelled with the gap.
  - **Quick status:** round line-icon buttons — Fuel, Break, Repair, Wait, Photo. Selected turns orange. Each pauses the "are you okay?" check for 15 minutes.
  - **Hold for SOS:** red pill; hold ~1.5 s while it charges.

## Alerts (full-screen)

| Are you okay? | Crash detected | SOS sent | A rider needs help |
|---|---|---|---|
| ![ok](screens/04_are_you_ok.png) | ![crash](screens/05_crash_countdown.png) | ![sos](screens/06_sos_sent.png) | ![help](screens/07_group_alert_incoming.png) |

Every alert follows the same pattern, so the rider never has to work out what to do: an icon, a plain title, the key number (a countdown ring, or a distance), a short **what happens next** list, then **one obvious primary action**.

- **Are you okay?** (orange): countdown to alerting the group. **I'm okay** is the big white button; SOS is a quiet red outline.
- **Crash detected** (red): countdown to SOS. **I'm okay — cancel**, or hold to send now.
- **SOS sent** (red): who was texted, **Call 112** (your configured number) and **I'm okay — cancel SOS**, which also texts a follow-up to your contacts.
- **A rider needs help** (orange, shown to *other* riders): a large distance and bearing. **I'm going** tells the group you're responding.

Alerts also light the lock screen, play a looping alarm at full volume and vibrate.

## Group, Log, Settings

| Group | Log | Settings |
|---|---|---|
| ![group](screens/08_group.png) | ![log](screens/09_events.png) | ![settings](screens/10_settings.png) |

Standard grouped lists: **Group** shows your code large, your name and the optional sync server; **Log** is every decision ARMOR made and *why*; **Settings** has emergency contacts, crash sensitivity (Low 5 g / Medium 4 g / High 3 g) and monitoring toggles.

## Demo ride

A demo ride adds a *Demo controls* panel (Stop my bike, Crash, Kabir stops, Everyone rides, Peers acknowledge) so every alert above can be walked through in under a minute.

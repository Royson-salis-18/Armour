# ARMOR ride flows: states, controls and transitions

This is the behaviour contract for the ride experience (web app in `web/`,
same rules intended for the Android app). Every situation a rider can be in
maps to exactly one state. The **status card** at the top of the ride panel
always shows that state and its next action, so moving ↔ stopping is always
one tap or one voice command away.

## 1. Ride lifecycle

| Ride status | Who sees what | Way forward | Way out |
|---|---|---|---|
| **Lobby** | Code, invite link, rider list, chat, GPS lock indicator | Lead: **Start ride** | Anyone: back arrow → Leave. Lead: hand the lead to someone in the rider list |
| **Riding** | Live map, status card, plans, controls | Lead: **End ride** (All controls → Me) | Rider: **Leave ride**; lead leaving hands the lead to the next rider |
| **Ended** | Summary: distance, ride time, top and average speed, event log | **Done** → home | |

Joining: open the invite link (`/r/CODE`) or type the 6-character code. Reopening
the app rejoins the same ride as the same rider. A ride that has ended can't be joined.

## 2. My state (status card)

Priority runs top to bottom: the first row that matches is what you see.

| State | When | Status card shows | Primary action | Secondary action |
|---|---|---|---|---|
| **SOS** | My SOS or crash alert is open | Red, "SOS active", responders count | **I'm OK** (cancels, group told) | Call 112 |
| **Catching up / Group is waiting** | I asked the group to wait (and no team stop, or the stop is *for me*) | Distance to group | **I've caught up** | Cancel |
| **Team stop: confirm** | Lead called a team stop, I haven't confirmed | Full-screen alert + reason | **I've stopped** | Navigate to lead · Hide while finding a spot |
| **Team stop** | Team stop on, I've confirmed | Reason, N/M stopped | Lead: **Resume all** · Rider: **Ride on anyway** (asks to confirm) | Lead: **Cancel stop** |
| **Off the bike** | I marked myself off the bike | Crash detection paused | **Back on bike** | |
| **Stopped** | Personal stop | Reason, minutes stopped | **Start riding** | Change reason |
| **Riding** | Default | Speed | **Stop** (lead picks *whole team* or *just me*) | Lead: **Stop team** · Rider: **Wait for me** |

Automatic transitions, so a missed tap never strands you:

- Marked stopped or off the bike but moving above 18 km/h for 12 s: back to **Riding**, with a spoken notice.
- In a team stop as a rider and riding on for 25 s: you leave the stop and the group is told.
- Lead moving during a team stop: prompted to resume everyone.
- Catching up and within 250 m of another rider: **caught up** is sent automatically.
- More than 1.5 km from the group for 1 minute: a "Falling behind?" card offers **Ask them to wait**.
- Lead resumes: everyone who stopped *for the team* goes back to Riding. Riders who were already on a personal stop stay stopped.
- Lead calls a team stop: riders already stopped or off the bike count as confirmed.

## 3. Plans (always cancellable)

Active plans sit under the stats. Each has its own answer or cancel button.

| Plan | Who starts it | Who sees it | Answers / cancel |
|---|---|---|---|
| **Stop request** | Rider (Ask for a team stop) | Lead (+ requester) | Lead: **Stop team** (becomes a team stop) or **Decline** · Requester: **Cancel** |
| **Catching up** | Rider (Wait for me) | Everyone, spoken aloud | Lead: **Slow down** (message) or **Stop & wait** (team stop that exempts that rider) · Others: **Easing off** · Rider: **Caught up** / **Cancel** |
| **Need a hand** | Anyone | Everyone (banner + Go) | Others: **On it** · Requester: **Cancel** |
| **Team stop** | Lead | Everyone (full screen) | Lead: **Resume all** / **Cancel stop** · Riders: **I've stopped** / **Ride on anyway** |
| **Regroup point** | Lead (here / tap on map) | Everyone (map pin + Go) | Lead: **Cancel** |
| **Destination** | Lead (place search) | Everyone (map pin, route, ETA) | Lead: change or **Cancel** |
| **SOS / crash** | Anyone (hold SOS 1.5 s, crash countdown, or 3 s air-gesture cover) | Everyone (full screen, sound, vibration) | Rider: **I'm OK** · Others: **Navigate**, **I'm responding**, Call 112, Dismiss · Lead: **Clear** |

## 4. Controls, grouped

Always on screen while riding:

- **Status and main button** (top of the dock): current state plus the one thing to tap (Stop, Resume all, I've stopped...). A smaller grey button beside it: Stop team (lead) or Wait for me (rider).
- **Plans** under it, each with its own answer or cancel.
- **Bottom navigation bar**, five items: **Riders** · **Chat** · **Armor** (raised mic, talk to the assistant) · **SOS** (hold 1.5 s) · **Menu**.

Menu opens the full list:

- **Group**: Stop the team / Ask for a team stop · Wait for me / Slow the group · Voice chat · Need a hand · Regroup point · Riders · Assistant and helmet
- **Road**: Navigate · Report hazard · Destination · Map style · Nearby
- **Me**: Off the bike · Share location · Safety profile · Leave ride / End ride

Lead-only items are visible to riders but dimmed, saying who decides.

## 5. Hands-free (Bluetooth helmet intercom)

The intercom pairs as a normal Bluetooth headset, so ARMOR speaks in the helmet
and listens on the helmet mic.

| Input | Action |
|---|---|
| Intercom play/pause button | Talk to ARMOR |
| Intercom next-track | Status read-out (state, speed, distance to lead, ETA, unread messages, who needs help) |
| Intercom previous-track | Repeat the last thing ARMOR said |
| "Armor, …" (wake word mode) | Run the command without touching anything |
| Wave over the phone | Talk to ARMOR |
| Two waves | Status read-out |
| Cover the camera 3 s | SOS with a 10 s spoken countdown; say "cancel" to stop it |

Voice commands, and what they do in each state:

| Say | Riding | Stopped / off bike | Team stop | Notes |
|---|---|---|---|---|
| "Stop", "Stopping for fuel" | Personal stop (with reason) | Updates reason | Confirms the stop | |
| "Riding", "Let's go", "Resume" | Already riding | Start riding | Lead: resume all · Rider: told to say "ride on" | |
| "Team stop food" | Lead: team stop · Rider: stop request | same | | |
| "Wait for me", "Catching up" | Rider: catch-up request · Lead: "ease off" message | | | |
| "Caught up" | Clears catch-up | | | |
| "Cancel" | Cancels the newest of: SOS countdown, crash countdown, my SOS, stop request, catch-up, help request; lead: team stop | | | |
| "I'm OK" | Cancels countdown or SOS | | | |
| "Need a hand", "Flat tyre" | Help request | | | |
| "Pothole", "Oil", "Police", … | Hazard marker at my position | | | |
| "Regroup here" | Lead: sets regroup point | | | |
| "Responding" | Ack the nearest rider in trouble and open directions | | | |
| "Status", "Read messages" | Spoken read-out | | | |
| "Say …" | Sends a chat message | | | Anything unrecognised is also sent as a message |
| "SOS" | Asks to confirm, then SOS | | | Never fires on one word alone |

### Voice assistant and voice chat

Say **"Armor"** and then a command, in one go ("Armor, lets stop for a while nearby") or as two steps (the mic button lights
up for 8 seconds after the wake word). The assistant is on by default while riding and can be switched off under
More > Assistant and helmet.

| You say | What happens |
|---|---|
| "lets stop for a while nearby", "need a break", "I'm hungry", "need fuel", "can we stop somewhere" | Lead: team stop with the matching reason. Rider: stop request to the lead. Either way the sentence is also sent to the group chat |
| "voice chat" / "join voice chat" | Joins the group call. Everyone else gets a spoken prompt and a Join banner |
| "mute" / "unmute" | Mutes your mic in the call |
| "leave voice chat" / "hang up" | Leaves the call |

Voice chat is a WebRTC mesh between the riders in the call (comfortable up to about 6 riders). The server only passes
signalling messages. It uses Google's public STUN servers; riders behind strict carrier NAT may need a TURN relay, which
the server picks up from `TURN_URLS`, `TURN_USERNAME` and `TURN_CREDENTIAL` environment variables.

While the bike is moving above about 11 km/h, chat hides the keyboard and offers big quick replies and "Reply by voice".

## 6. Alerts and how loudly they arrive

| Event | Screen | Sound / vibration | Spoken |
|---|---|---|---|
| Team stop (to confirm) | Full screen | Yes | Always |
| Rider SOS / crash | Full screen | Yes | Always |
| Catch-up request | Plan card | Short vibration | Always |
| Hazard within 350 m | Top toast | Yes | Always |
| Help request | Banner | | When read-aloud is on |
| Chat | Toast + badge | | With helmet mode or read-aloud |
| Group events (joined, stopped…) | Chat log | | When read-aloud is on |

# ARMOR design system

Source of truth: `ui/Theme.kt` (colour tokens), `ui/Type.kt` (bundled Barlow Condensed + Inter, both SIL OFL, licences in `docs/licenses/`), `ui/Components.kt` (components) and `ui/Brand.kt` + `ui/ArtPaths.kt` (logo, rider hero, dot orb, speed dial, convoy strip, slide-to-start, floating nav, glow).

## Principles

1. **Calm over clever.** Neutral surfaces, one accent. Colour is reserved for meaning.
2. **One primary action per screen.** Especially in emergencies.
3. **Glanceable and glove-friendly.** Big numerals, 48 dp+ touch targets, short text.
4. **Consistent.** Everything comes from the tokens below; no ad-hoc hex values in screens.

## Tokens

**Colour** — orange, red, black and grey only.

| Token | Value | Use |
|---|---|---|
| `Bg` | `#0A0A0B` | App background |
| `S1` / `S2` / `S3` | `#151618` / `#1D1E21` / `#28292D` | Sheets and cards / controls / raised controls and dividers |
| `Line` | `#2B2C30` | Hairline borders |
| `OnBg` / `Muted` / `Dim` | `#F4F4F5` / `#A3A5AB` / `#6E7076` | Primary / secondary / tertiary text |
| `Orange` | `#FF6B1A` | Brand, selection, "you", attention, primary CTA |
| `Red` | `#E5342F` | Danger: SOS, crash, separated rider |
| `Ok` | `#EDEDEF` | "Fine" states (near-white; there is deliberately no green) |

**Type** (`Type`): Display 60 (countdowns) · Headline 28 · Title 22 · Speed 34 (tabular figures) · Body 15 · Label 13 · Caption 12. Sentence case, no decorative tracking.

**Shape and spacing:** 8-pt grid (4, 8, 12, 16, 20, 24). Radii: 10 controls, 24 cards, 28 sheets and buttons, full for chips and avatars.

**Elevation:** none. Depth comes from stepping up the surface tones (`Bg` → `S1` → `S2` → `S3`) and a barely-there vertical gradient on cards.

## Components

| Component | Variants / states | Notes |
|---|---|---|
| `PrimaryButton` | white (default), orange; optional arrow | 56 dp, full width. One per screen. |
| `SecondaryButton` | — | Surface fill, hairline border. |
| `HoldToSosButton` | filled, outlined | Charges while held (1.5 s), fires only when full. Outlined on alert screens where "I'm okay" is primary. |
| `CountdownRing` | colour = alert level | Arc shrinks as time runs out; time in the centre. |
| `Card2` / `ListCard` + `ListRow` | with/without icon, trailing text | Grouped lists with hairline separators. |
| `MapChip` | optional status dot | Floats over the map. |
| `Avatar` | any colour | Rider initial. Other riders are greys; you are orange. |
| `StatusIcon` | selected / unselected | Monochrome line icons, drawn in code. |
| `Pill` | selected | Segmented controls and demo actions. |

## Accessibility

- Text/background pairs meet WCAG AA on the dark surfaces (`OnBg` and `Muted` on `S1` ≥ 4.5:1); `Dim` is for tertiary captions only.
- State is never colour-only: every state carries a word ("Separated", "Stopped 0:42") and, for riders, a **!** badge.
- Touch targets are ≥ 48 dp; the SOS control is 58 dp tall and deliberately press-and-hold.
- Emergency copy is plain and short ("Are you okay?", "Crash detected", "SOS sent").

## Do / don't

| Do | Don't |
|---|---|
| Use `Orange` for the one thing to look at | Add more accent colours |
| Use `Red` only for danger | Use red for decoration or errors that aren't safety-related |
| Reuse `ListRow` and `Card2` | Hard-code colours or radii in a screen |
| Keep alerts to one primary action | Add secondary choices to an emergency screen |

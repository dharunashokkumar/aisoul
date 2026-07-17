# DESIGN.md — Premium Dark Motion System

> **How to use this file:** Paste this entire document into your AI prompt before asking for any screen.
> Then say: "Build [screen name] following DESIGN.md exactly. Do not deviate from the tokens."
> This is not a suggestion document. Every number is a rule. Adjectives are banned; numbers are law.

---

## 0. Design Philosophy (read this before generating anything)

This design language is about **restraint that signals confidence**. The app never begs for attention. It assumes the user's attention and rewards it.

The four laws, in priority order:

1. **One idea per screen.** If two elements compete for attention, delete one. Every screen has exactly one hero and everything else is supporting cast.
2. **Whitespace is the primary design material.** Not color, not illustration, not borders. If a layout feels empty, it is correct. Add 30% more space than feels comfortable, then stop.
3. **Motion is meaning, not decoration.** Every animation must answer "what did the user just do?" or "what changed?". Animation that answers neither gets deleted.
4. **Typography carries the personality.** The type does the talking. Color, iconography, and imagery whisper.

**The feeling to hit:** opening the app should feel like entering a quiet, expensive hotel lobby at night. Dim, calm, precise, slightly theatrical. Never loud, never busy, never bright.

**What this is NOT:** this is not "dark mode." Dark mode is a theme toggle. This is a dark-native system — every decision assumes darkness as the canvas.

---

## 1. Color Tokens

Use these exact values. Never introduce a color not listed here.

### Surfaces (elevation via lightness, never shadows)

| Token | Hex | Use |
|---|---|---|
| `surface-0` | `#0B0B0D` | App background. Near-black with a whisper of blue. Never pure #000. |
| `surface-1` | `#131316` | Cards, sheets, primary containers |
| `surface-2` | `#1B1B20` | Elevated elements: nested cards, active states, bottom sheets |
| `surface-3` | `#242429` | Highest elevation: modals, popovers, pressed states |

**Elevation rule:** higher = lighter, step of ~+8 lightness. NO drop shadows anywhere. Elevation is communicated only by surface lightness and a hairline border.

### Borders & dividers

| Token | Value | Use |
|---|---|---|
| `border-subtle` | `1px solid rgba(255,255,255,0.06)` | Card edges, default |
| `border-strong` | `1px solid rgba(255,255,255,0.12)` | Focused/active elements only |
| `divider` | `1px solid rgba(255,255,255,0.04)` | List separators. Prefer whitespace over dividers when possible. |

### Text

| Token | Value | Use |
|---|---|---|
| `text-primary` | `rgba(255,255,255,0.92)` | Headlines, key numbers. Never pure white — #FFF vibrates on dark. |
| `text-secondary` | `rgba(255,255,255,0.60)` | Body copy, descriptions |
| `text-tertiary` | `rgba(255,255,255,0.38)` | Captions, timestamps, disabled |
| `text-inverse` | `#0B0B0D` | Text on accent-filled buttons |

### Accent — pick ONE per app, never mix

| Option | Hex | Personality |
|---|---|---|
| `accent-brass` | `#C9A961` | Warm, premium, understated luxury |
| `accent-ice` | `#8FB8C9` | Cool, technical, precise |
| `accent-sage` | `#9CB89C` | Calm, organic, trustworthy |

**Accent budget: the accent color may occupy at most 5% of any screen's pixels.** It appears on: the single primary CTA, one key number or status, and focus states. Nowhere else. If the accent appears in more than 3 places on one screen, remove instances until only 3 remain.

### Semantic (use sparingly, desaturated)

| Token | Hex |
|---|---|
| `positive` | `#7FB58A` |
| `negative` | `#C97B6E` |
| `warning` | `#C9A961` |

Never use saturated red/green (#FF0000-style). Semantic colors are dusty, muted, adult.

### Banned

- Gradients as fills on buttons or cards. (One exception in §6.)
- Purple. Purple-blue gradients are the universal signature of AI-generated UI. Zero purple.
- Pure black `#000000` and pure white `#FFFFFF`.
- More than one accent color.
- Colored shadows, glows, neon.

---

## 2. Typography

Typography is 60% of this design system. Get this wrong and nothing else matters.

### Faces

- **Display + headlines:** `Satoshi` (Fontshare, free) — fallback `General Sans`, then `Inter`
- **Body + UI:** `Inter` — fallback system sans
- **Numbers/data (optional):** `JetBrains Mono` or tabular-nums of Inter for aligned figures

Never use: Roboto default, Open Sans, Lato, Montserrat, Poppins. These read as template.

### Scale (mobile-first, dp/sp)

| Role | Size | Weight | Letter-spacing | Line-height | Case |
|---|---|---|---|---|---|
| `display` | 40sp | 600 | -0.04em | 1.05 | lowercase |
| `headline` | 28sp | 600 | -0.03em | 1.1 | lowercase |
| `title` | 20sp | 600 | -0.02em | 1.2 | lowercase |
| `body` | 15sp | 400 | -0.01em | 1.5 | sentence |
| `caption` | 12sp | 500 | +0.02em | 1.3 | lowercase |
| `overline` | 11sp | 600 | +0.12em | 1.2 | UPPERCASE |
| `data-hero` | 48sp | 650 | -0.04em | 1.0 | tabular-nums |

**Rules:**
- Negative letter-spacing on everything above 16sp. This single rule does more for the aesthetic than any color choice.
- Maximum 2 weights per screen (400 + 600). Weight 700+ is banned — it looks cheap on dark.
- Headlines and labels are lowercase. Body text uses normal sentence case. Never Title Case Anywhere.
- `overline` (small uppercase, wide tracking) is the only uppercase element — used as tiny section labels above content, in `text-tertiary`.
- Big numbers are heroes. When a screen's core content is a number (a count, a price, a percentage), render it at `data-hero` size and let it dominate.

---

## 3. Spacing, Layout & Shape

### Spacing scale (4dp base)

`4, 8, 12, 16, 24, 32, 48, 64`

| Token | Value | Use |
|---|---|---|
| `space-screen` | 24dp | Screen horizontal padding. Never less. |
| `space-card` | 24dp | Padding inside cards |
| `space-stack` | 32dp | Vertical gap between cards/sections |
| `space-hero` | 64dp | Above and below the screen's hero element |

**When in doubt, double it.** A cramped premium design is a contradiction; it stops being premium.

### Shape

| Token | Value |
|---|---|
| `radius-card` | 24dp |
| `radius-button` | 16dp |
| `radius-input` | 14dp |
| `radius-chip` | 999dp (full) |

Radius is consistent and generous. Never mix 8dp and 24dp radii on the same screen.

### Layout rules

- **One card per viewport-ish.** Cards are large; roughly 1–1.5 cards visible at a time when scrolling. Dense card grids are banned.
- Screens open with the hero: either a `display` headline or a `data-hero` number, surrounded by `space-hero`.
- Max content width on any screen: the screen minus 48dp. No edge-to-edge content except full-bleed imagery.
- Lists: 20dp vertical padding per row, separated by whitespace or `divider` — never boxed rows.
- Bottom-anchor primary actions. The main CTA lives at the bottom of the screen, full-width minus `space-screen`, 56dp tall.
- Navigation is minimal: max 4 bottom-nav items, icon-only with 11sp labels, inactive at `text-tertiary`.

---

## 4. Motion (this is where the feeling lives)

Static screens following §1–3 will look good. Motion is what makes them feel expensive. **Every interactive element must have motion specs. No exceptions.**

### The one spring

All motion uses spring physics, not duration curves. One spring for the whole app:

```
Compose:  spring(dampingRatio = 0.75f, stiffness = 380f)
CSS/JS:   spring(mass: 1, stiffness: 380, damping: 30)  // e.g. Framer Motion
Flutter:  SpringDescription(mass: 1, stiffness: 380, damping: 30)
```

Where a duration is unavoidable (fades): `220ms, cubic-bezier(0.2, 0, 0, 1)`.

### Mandatory micro-interactions

| Interaction | Behavior |
|---|---|
| **Any tap-down** | Scale to `0.97`, on release spring back to `1.0`. Applies to every card, button, row. |
| **Button press** | Scale `0.96` + surface brightens one elevation step |
| **Screen enter** | Content staggers in: each section fades from `opacity 0, translateY 16dp` to rest, 40ms stagger between siblings, top to bottom |
| **Number appears** | Counts up from 0 (or from previous value) over ~700ms with ease-out. Big numbers NEVER just appear. |
| **Card expand** | Container transform — the card itself grows into the detail screen. Never a hard cut navigation. |
| **Pull to refresh** | Custom: subtle scale + fade on the hero, no default spinner |
| **List item removal** | Springs out horizontally + siblings spring up to close the gap |
| **Toggle/selection** | The selected state slides between options as a pill (shared element), never blinks between states |

### Haptics (Android)

| Event | Constant |
|---|---|
| Tap on any interactive element | `HapticFeedbackConstants.CONTEXT_CLICK` |
| Success / completion | `CONFIRM` |
| Error / boundary | `REJECT` |
| Selection change (pickers, tabs) | `SEGMENT_TICK` |

Motion without haptics is half the experience. Wire both together.

### Motion restraint rules

- Nothing animates unless the user caused it or data changed. No ambient looping animations, no pulsing, no shimmer except skeleton loading.
- Skeleton loaders: surface-2 blocks with a slow (1.4s) low-contrast shimmer, matching final layout exactly.
- Stagger reveals happen once per screen entry, never on every scroll.
- Respect `prefers-reduced-motion` / `Settings.Global.ANIMATOR_DURATION_SCALE = 0`: replace all movement with 150ms fades.

---

## 5. Components

### Primary button
56dp tall, full-width minus screen padding, `radius-button`, filled with the accent color, `text-inverse` at 15sp weight 600 lowercase. **One per screen maximum.**

### Secondary button
Same geometry, `surface-2` fill, `border-subtle`, `text-primary`.

### Ghost/text button
No fill, no border, `text-secondary`, brightens to `text-primary` on press.

### Card
`surface-1`, `border-subtle`, `radius-card`, `space-card` padding. Content hierarchy inside: `overline` label → headline or data → supporting `body` in `text-secondary`. Cards are tappable-looking only if tappable (border-strong on the interactive ones is not needed — the press animation communicates it).

### Input
`surface-1` fill, `radius-input`, 56dp tall, no visible border at rest; `border-strong` + accent caret when focused. Floating label animates from placeholder position to a `caption` above (spring, not linear).

### Status/data chip
Full radius, `surface-2`, 12sp, icon optional. Semantic chips use the semantic color at 15% opacity as fill with the semantic color as text.

### Empty states
An empty screen is a designed moment: one `headline` (lowercase, warm, direct — "nothing here yet"), one `body` line explaining the action that fills it, one ghost button. No sad illustrations, no giant icons.

### Bottom sheet
`surface-2`, top radius 28dp, 4dp×36dp grabber at `rgba(255,255,255,0.15)`, springs up from bottom, background dims to 60% black.

---

## 6. The Signature Moment (one per app)

Every app built on this system gets exactly **one** place where restraint breaks — one theatrical, memorable element. Choose ONE:

- A hero card with a slow-moving radial gradient mesh at ≤8% opacity behind its content (the single gradient exception)
- A physics-based interaction (a draggable card with real momentum, a number dial with weight)
- An orchestrated multi-part entrance for the single most important screen
- A grain/noise texture overlay (2–3% opacity) across surfaces for a filmic feel

Everything else in the app stays quiet so this one thing lands. If you're tempted to add a second signature, you've broken the system.

---

## 7. Copy Voice

Words are part of the design.

- All lowercase in headlines, buttons, labels. Sentence case in body copy only.
- Terse, confident, direct. "you're in." not "Congratulations! Your account has been successfully created!"
- No exclamation marks. Ever.
- No filler: "please", "simply", "just" are deleted on sight.
- Buttons say exactly what happens: "save changes", "join queue", "see menu" — never "submit", "OK", "continue".
- Errors state what happened and what to do: "couldn't load the menu. pull down to retry." No apologies, no "oops".
- Numbers over adjectives: "4 min wait", not "short wait".

---

## 8. Anti-patterns — instant fail conditions

If the generated UI contains any of these, regenerate:

1. Purple, or any blue-purple gradient
2. Drop shadows / `elevation` shadows / colored glows
3. More than one accent color, or accent covering >5% of the screen
4. Emoji in the UI
5. Title Case Labels Like This
6. Default Material 3 dynamic color, default Material shapes and type scale, visible ripple as the only feedback
7. Dense grids of small cards
8. Font weight 700+, or 3+ weights on one screen
9. Borders + shadows + fills all on the same element
10. Screens where nothing is clearly the hero
11. Instant hard-cut transitions between screens
12. A big number that appears without counting up
13. Stock illustrations, 3D blob characters, confetti
14. Center-aligned body paragraphs

---

## 9. Self-critique checklist (run before showing output)

Before presenting any screen, verify:

- [ ] Can I name this screen's single hero element in 3 words?
- [ ] Is the accent color used ≤3 times?
- [ ] Is every piece of text one of the 7 type roles in §2 — no ad-hoc sizes?
- [ ] Did I specify press animation + haptic for every tappable element?
- [ ] Would removing one element make this better? (Then remove it.)
- [ ] Does anything on this screen exist purely for decoration? (Then delete it.)
- [ ] Is there at least `space-stack` (32dp) of breathing room between sections?
- [ ] Squint test: in a blurred screenshot, does exactly one thing dominate?

---

## 10. Prompt template

When requesting a screen from an AI, use this structure:

```
Build the [screen name] screen following DESIGN.md exactly.

Screen's single job: [one sentence]
Hero element: [the one thing that dominates]
Content: [real content, real numbers — never lorem ipsum]
Signature moment applies here: [yes/no — only one screen in the app gets it]

Output: [Jetpack Compose / React + Tailwind / Flutter] with all motion
specs from §4 implemented, not commented as TODO.
```

Feed real content. "queue at boys' mess 2: 4 min" produces a designed screen; "Lorem ipsum dolor" produces a template.

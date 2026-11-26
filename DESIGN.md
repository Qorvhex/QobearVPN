# QobearVPN Design Contract (Nothing OS Aesthetic)

Dial: ENERGY 3 / RHYTHM 2 / MOTION 3
(Respects system `prefers-reduced-motion` / `ANIMATOR_DURATION_SCALE == 0` by falling back to crisp instant state swaps.)

---

## 1. Context (Audience, Environment, Purpose)
- **Audience:** Mobile Android users (Persian RTL primary + English LTR) connecting to TunnelBear OpenVPN nodes under restrictive DPI networks (Iran, China, Russia) or unrestricted global networks.
- **Purpose:** Fast, tactile, unmistakable connection state at a glance—with zero generic SaaS gradients, zero bloated chrome, and instant access to real OpenVPN TLS handshake latency, bypass regime switching, and per-app split tunneling.

## 2. Personality & "What We Are Not"
- **Nothing OS Hardware-Minimalist:** Deep matte OLED black canvases, crisp `1dp` geometric hairlines, dot-matrix pixel indicators, capsule pill controls, and disciplined monochrome hierarchy punctuated by Nothing Red (`#D71921`).
- **What We Are NOT:** No purple/indigo AI glows, no fake speed numbers, no intrusive popups, no decorative confetti or random gradients.

## 3. Color Palette & Measured Contrast Ratios

### Dark Theme (Default — OLED Matte)
| Role | Token | Hex | Contrast vs Canvas/Card |
|---|---|---|---|
| Canvas Background | `BgCanvas` | `#000000` | Main OLED canvas |
| Card / Surface | `SurfaceCard` | `#0E0E10` | `1dp` hairline `#27272B` |
| Secondary Panel | `SurfaceElevated` | `#18181B` | Subtle neutral container |
| Primary Text | `TextPrimary` | `#F5F5F7` | > 17.8:1 on `#0E0E10` (AAA) |
| Secondary Text | `TextSecondary` | `#A1A1AA` | 7.3:1 on `#0E0E10` (AAA) |
| Primary Accent | `NothingRed` | `#D71921` | Action focal & standby dot |
| Text on Accent | `OnAccent` | `#FFFFFF` | 5.28:1 on `#D71921` (AA) |
| Connected Status | `StatusConnected` | `#22C55E` | Always paired with label/icon |
| Warning / Probing | `StatusWarning` | `#F59E0B` | Always paired with label/icon |
| Disabled Surface | `SurfaceDisabled` | `#1F1F23` | Text `#71717A` (> 3.3:1, never accent!) |

### Light Theme (Paper Light)
| Role | Token | Hex | Contrast vs Canvas/Card |
|---|---|---|---|
| Canvas Background | `BgCanvas` | `#F4F4F6` | Matte paper canvas |
| Card / Surface | `SurfaceCard` | `#FFFFFF` | `1dp` hairline `#E2E2E6` |
| Secondary Panel | `SurfaceElevated` | `#EBEBEF` | Subtle neutral container |
| Primary Text | `TextPrimary` | `#111113` | > 18.0:1 on `#FFFFFF` (AAA) |
| Secondary Text | `TextSecondary` | `#52525B` | 7.4:1 on `#FFFFFF` (AAA) |
| Primary Accent | `NothingRed` | `#C8141C` | 5.9:1 on `#FFFFFF` (AA) |
| Disabled Surface | `SurfaceDisabled` | `#E4E4E7` | Text `#71717A` (3.7:1, neutral) |

## 4. Typography & Touch Targets
- **Persian UI:** `Vazirmatn` (Regular / Medium / Bold) with clean RTL spacing.
- **Dot-Matrix Hero & Status:** `DotGothic16` paired with custom Compose `Canvas` dot-matrix pixel grid (`DotMatrixConnectDial`, `DotMatrixPulseStrip`).
- **Telemetry / Numerals:** `SpaceMono` with `fontFeatureSettings = "tnum"` (`tabular-nums`) for ping ms, stopwatch, RX/TX speeds, and IP addresses so counters never jitter.
- **Touch Targets:** Minimum `48dp` height for primary buttons, `44dp` minimum for all interactive chips and list actions. Zero horizontal overflow at narrow widths (`320dp`+).

## 5. Project-Specific Workflows & States
- Every dynamic screen/component handles all 5 lifecycle states explicitly: `Default`, `Loading` (animated dot-matrix progress), `Empty` (actionable prompt), `Error` (non-technical Persian/English explanation + direct recovery button), and `Success` (haptic + transient badge/status confirmation).
- Disabled controls strictly drop to `SurfaceDisabled` with muted text (`#71717A`), never keeping bright `#D71921` backgrounds.

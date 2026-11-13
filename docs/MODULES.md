# Native Forge 1.8.9 modules

All 78 gameplay modules start disabled and unbound. Enable in a world using
`.toggle <id>`. A requested module waits at the title screen and
reactivates with a fresh scope after joining. Settings and bindings use the normal
Perihelion config, commands and [ClickGUI](CLICKGUI.md); there is no separate donor configuration.
The Client-category `clickgui` module opens the screen once and immediately returns
to disabled. It defaults to Right Shift and can be rebound like any other module.

| ID | Behavior | Settings (defaults) |
| --- | --- | --- |
| `fullbright` | Gamma-100 lightmap without changing saved brightness. | None |
| `sprint` | Hold sprint when focused with no screen; vanilla eligibility applies. | `cancel-invis=false` |
| `nofov` | Fixed dynamic FOV multiplier of 1; preserves the user's base FOV. | None |
| `wtap` | On a living target with hurtTime >= 6, reset sprint for one tick when already sprinting/holding sprint. Higher priority than Sprint. | `chance=100` (0–100%, independently rolled at attack and reset) |
| `nobob` | Suppress local camera/hand bobbing. | None |
| `nohurtcam` | Suppress local hurt and death camera tilt. | None |
| `timer` | Absolute client timer multiplier; Constant or two 100 ms Pulse windows. | `mode=constant`, `time-amount=1`, `time-amount-2=1` (0.1–5, steps 0.1; second visible in Pulse) |
| `antiblind` | Suppress blindness fog and darkening; potion remains active. | None |
| `fastplace` | Clamp remaining right-click cooldown for blocks, eggs and snowballs. | `delay=1` (0–3 ticks), `allow-blocks=true`, `allow-projectiles=true` |

The additional Yuri gameplay modules, modes, native adaptations and exclusions are listed in [YURI_PORT.md](YURI_PORT.md). The table above documents the original independent modules.

The seven subsequent additions and optional upgrades are documented in [DONOR_IMPROVEMENTS.md](DONOR_IMPROVEMENTS.md).

The opt-in OpenB4 modes and functional improvements to 16 existing modules, including
their controls, native adaptations and verification, are documented in
[OPENB4_GAMEPLAY_2026-10-22.md](OPENB4_GAMEPLAY_2026-10-22.md).

Inv Move (`invmove`) supports survival inventory and the listed chest/container
screens. Like Yuri, it excludes the creative inventory. Enable **Allow in GUIs**
(`.set invmove allow-in-guis true`, default `false`) to also move, jump, sneak and
use arrow-key rotation in other screens, including ClickGUI. Chat remains excluded.
Inventory/Chest modes and motion scaling apply to their containers; ordinary GUIs
do not start container packet modes. Singleplayer pause screens still pause the game.

Manager's **Start Delay (ms)** (`start-delay`, default 120, 0–500 in 10 ms steps)
waits after opening or reopening the inventory before sorting. The existing
**Delay (ms)** still separates actions; **Instant** skips both waits. Sorting
outside the inventory, when enabled, continues to use the ordinary action delay.

Aim Assist's Simple and Adaptive modes have **Reduce On Target**
(`reduce-on-target`, default off). Its **Reduce Factor** (`reduce-factor`, default
0.5, 0–1) reduces horizontal and vertical speed by that fraction while your
crosshair ray hits the selected target. Blocks and nearer entities obstruct it;
**Distance** limits the ray. These controls are hidden in Silent mode.

Clutch and the latest inventory, combat, Scaffold and rendering changes are documented in
[QUALITY_IMPROVEMENTS_2026-09-29.md](QUALITY_IMPROVEMENTS_2026-09-29.md).

The shared rotation fixes, final-position Scaffold aiming, Aura movement correction,
MLG item prediction and complete consumer audit are documented in
[ROTATIONS_AUDIT_2026-10-10.md](ROTATIONS_AUDIT_2026-10-10.md).

Examples:

```text
.toggle fullbright
.toggle sprint
.set sprint cancel-invis true
.set timer mode pulse
.set timer time-amount 0.8
.set timer time-amount-2 1.2
.toggle timer
.set fastplace allow-projectiles false
.toggle fastplace
```

WTap uses Yuri's sprint-reset algorithm, not a forward-key release. Chance is tested
at the attack and again at each eligible update until the reset occurs or a later
ineligible attack clears it. A zero chance clears pending work. Sprint resumes
according to vanilla input or the Sprint module after the one-tick suppression.
An external higher-priority shared claim can override these requests.

Timer Pulse uses the second speed before 100 ms, the first from 100 through 200 ms,
and keeps the previous speed on the update that resets the cycle. Slow client ticks
can skip a window, as in Yuri. The positive minimum avoids a client that cannot tick
to disable itself. Timer changes client timing, not server tick speed.

Visual behavior is listener-scoped: disable, world loss, shutdown or a callback fault
returns the hook to vanilla. Fullbright and NoBob never modify saved options.
Sprint/WTap/Timer claims expire and retire through the existing controls. FastPlace
only shortens a current cooldown; it does not synthesize clicks, place through blocks,
or undo already-submitted item actions. Its cooldown naturally continues after disable.

See [donor details and intentional fixes](DONORS.md) and [verification](VERIFICATION.md)
for exact tested behavior and limits.

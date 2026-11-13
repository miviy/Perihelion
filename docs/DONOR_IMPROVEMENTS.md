# Donor-inspired improvements — 2026-09-27

This batch brought Perihelion to **77 gameplay modules plus ClickGUI**. The later
[quality improvements](QUALITY_IMPROVEMENTS_2026-09-29.md) add Clutch and bring the total to 78 gameplay modules. The seven additions
start disabled and unbound. Existing defaults are preserved; new options on existing
modules are opt-in. All settings use the native config, commands and GUI.

## New modules

| ID | Behavior and default settings |
| --- | --- |
| `aimassist` | Manual combat assistance, without automatic attacks. `mode=view` (View/Silent), `range=3`, `fov=90`, `yaw-speed=5`, `pitch-speed=3` degrees/tick, `require-press=true`, `weapons-only=true`, `allow-tools=false`, `pause-mining=true`, `through-walls=false`. Uses independent player selection with the existing friend/bot checks; yields to Aura, Freecam, Free Look and building modules. Silent mode requires the supported walking-rotation hooks and corrects movement. |
| `autoclicker` | Repeat physical held attacks. `min-cps=8`, `max-cps=12`, `weapons-only=true`, `allow-tools=false`, `break-blocks=true`, `block-hit=false`, `block-ticks=2`. Stops for screens, lost focus and competing automation. Clicks run at most once per game tick, with no catch-up bursts after stalls; actual CPS is tick-quantized. |
| `bedplates` | Cached text labels listing nearby bed-defense materials. `range=24`, `vertical-range=12`, `layers=2`, `scan-budget=1024`, `show-distance=true`. Initial discovery and later scans are incremental, scan only loaded blocks, retain at most 128 beds and draw at most 32 labels. Material collection is limited to four discovered beds per tick and eight displayed material names per bed. Labels describe surrounding blocks, not a proof of exact defense topology. |
| `freecam` | Detached view entity, never a fake movement packet stream. `horizontal-speed=0.8`, `vertical-speed=0.6`, `max-distance=64` (blocks; speeds in blocks/tick). Aborts prior mining and suppresses local movement keys, attacks and queued mouse interactions; claims slot/rotation against automated actions. Restores its view entity on disable/fault/world change. Preserves the user's perspective option. Does not freeze gravity, damage or incoming server movement of the real player, and does not load remote chunks. |
| `chestaura` | Open nearby reachable chests. `range=4`, `delay=500` ms, `through-walls=false`, `move-fix=true`. Confirms openings by the resulting chest screen, remembers double chests, times out failures and backs off retries. Opened/retry caches are limited to 256 entries per activation. Yields to combat, building, Freecam, screens, sneaking and manual interaction; never toggles another module. |
| `autoeat` | Hunger-based food selection and low-health golden apples. `health=15`, `hunger=15`, `prefer-gapples=true`, `prefer-golden=true`, `check-inventory=false`, `cooldown=1000` ms. Excludes harmful foods, avoids repeated healing apples during regeneration, honors vanilla eating eligibility and stops its owned use on cleanup. Optional inventory refill uses one real swap into an empty hotbar slot, then rechecks before use; it never treats a main-inventory index as a hotbar slot. |
| `zoom` | Local FOV multiplier without writing the saved FOV. `fov=30`, `scroll=true`, `scroll-step=5`, `hide-hand=true`, `smooth-look=true`. Scroll is consumed only during focused gameplay. Smooth camera state is restored on release. Zoom is a normal toggle module; it has no default key. It can compound with another mod's zoom. |

## Existing-module options

- **FastPlace:** `validate-placement=false`, `skip-obsidian=false`,
  `skip-interactable=false`. Validation requires an actual reachable ray hit and
  vanilla placement eligibility. Item exclusions do not affect egg/snowball settings.
- **Aura:** `sort=original` (Original/Distance/Health/Hurt Time/FOV), `fov=360`,
  `require-press=false`, `weapons-only=false`, `allow-tools=false`, `allow-mining=true`.
  Original preserves the previous selection behavior; explicit sort also applies to
  Adaptive mode. Filters affect Aura only, not Bow Aimbot or projectile targeting.
- **WTap:** `mode=sprint_reset` retains the existing behavior. `forward_tap` adds
  `delay=5.5` and `duration=1.5` ticks with a 500 ms retrigger cooldown. Release,
  screens, mode changes and cleanup cancel pending work. Uses owned virtual input.
- **Scaffold:** `keep-y-on-press=false` requires physical use-item input when enabled;
  `no-keep-y-jump-boost=false`; `edge-sneak=false`, `edge-distance=0.13`;
  `block-counter=false`; `placement-highlight=false`. Highlights are short-lived,
  capped at 64, and added only after successful placement. Existing Keep Y/Speed
  eligibility still applies. Edge distance projects the player's support footprint
  in the movement direction; it is not a server-specific bridging guarantee.
- **Camera:** `no-nausea=false`; with fire suppression off, `fire-alpha=0.9`,
  `fire-offset=0`, `fire-red=255`, `fire-green=255`, `fire-blue=255`. Nausea changes
  the rendering query without removing the potion. Ordinary portal effects remain.

## Boundaries and rollback

Direct native attack/use/dig actions also honor the Freecam interaction gate; cleanup packets remain allowed. Anti Fireball now yields when its rotation claim loses.

The references supplied behavior ideas, not replacement frameworks. Perihelion's
activation ownership, control arbitration, thread boundaries and render scopes remain
in charge. No donor GUI/config/manager, recorded click pattern, branded asset or
server-specific bypass mode was imported in this change. See [DONORS.md](DONORS.md).

Before editing, 271 source/config/docs files were archived and their contents verified
against SHA-256 hashes in `.backups/before-donor-improvements-20260927-125031/`.
`scripts/restore-donor-improvements.py` verifies the archive and completed change
manifest in dry-run mode. `--apply` backs up affected current files before restoring
this baseline and refuses later edits that do not match the completed snapshot.
The earlier Yuri backup remains separate and unchanged.

Verification details, including the exact packaged artifact and limits, are recorded
in [VERIFICATION.md](VERIFICATION.md). These are local source/unit/packaged checks,
not authenticated multiplayer or extended manual gameplay certification.

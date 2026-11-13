# Verification

## Rotation correctness audit — 2026-10-10

`check assemble --offline` passes **219 tests** (155 core, 64 Forge), with zero
failures/errors/skips and passing architecture/distribution validation. Final
`forge/build/libs/Perihelion-0.1.0.jar`: **1,754,320 bytes**, SHA-256
`66205b2e7a1551d14fbe6ccb553cf438c7b45eff258ce8429d632478986c7f7e`.
The copies actually launched in both runs match this hash.

- Forge: `forge/run-smoke-forge-_5as2zy2/launch.log`.
- OptiFine M5: `forge/run-smoke-optifine-k9tzfqbn/launch.log`.
- Each: **100 native rotation checks**, **51 shared-control checks**, module
  lifecycle 364, gameplay 139, deep gameplay 125, quality gameplay 56, GUI 103,
  production HUD 55, render 55 and network 46.
- Actual walking/interaction packets establish look-before-use/attack order and
  matching angles for MLG, moving Scaffold, Aura and Anti Fireball. Native bucket
  and web prediction works while looking up, existing web catches are preserved,
  camera/selection restore, and the actual player renderer uses submitted pitch.
- Moving Scaffold covers 18 rotation/search/ray combinations. Existing deep
  fixtures also cover four bridging modes × two slot modes × three ray modes.
  The initial moving fixture failed before final-position aim was fixed; its run
  is retained at `forge/run-smoke-forge-5lcx2xus/launch.log`.
- Aura's native scaled movement and sprint jump pass in SILENT and STRICT.
  Unsent lower-owner actions are rejected after a higher owner releases. Control
  tests cover rewritten requests, nested/throwing callbacks, external writes,
  unfinished walking passes, world invalidation and shutdown during item actions.
- Two new unit tests cover 22,500 independent input/yaw-wrap combinations and the
  small-input rounding regression. No added per-frame search; Scaffold's expensive
  search and smoothing execute once per walking pass. Existing search microbenchmarks
  remain recorded in the evidence, without treating them as FPS measurements.
- Both full scenarios unload/rejoin and finish with subscriptions=0, queued=0 and
  cleared world/player/hooks. No unexpected errors, NPEs, missing-member, Mixin or
  out-of-memory failures were found. Forge signature/Twitch/audio warnings and
  deliberate HUD/module fault injections remain expected.

These checks prove native client behavior, local prediction and packet submission.
Authenticated multiplayer, server acceptance/damage prevention, delayed-packet
combinations and gameplay FPS remain unverified. SERVER rotation preserves the
camera; the local third-person pose now displays it. Existing settings/defaults
and the config schema are unchanged.

[Fixes and full consumer audit](ROTATIONS_AUDIT_2026-10-10.md),
[machine-readable evidence](verification/rotations-20261010.json).
Original source: `.backups/before-rotations-20260930-021758/` (342 originals).
`python3 scripts/restore-rotations.py` verifies the rollback; `--apply` checkpoints
current affected files and restores this batch. Later edits cause refusal instead
of silent overwrite. Isolated dry-run/refusal/restoration were verified in the
backup's `rollback-verification.json`. Rebuild after applying a source rollback.

## HUD screenshot fidelity — 2026-09-30

`check assemble --offline` passes **217 tests** (155 core, 62 Forge) with no
failures/errors/skips, plus architecture and distribution checks. Artifact:
`forge/build/libs/Perihelion-0.1.0.jar`, **1,747,854 bytes**, SHA-256
`8fd9bc3f5c418bcf4ac52b0c1105a56b2658de7e5a94e4f292b61d6cb3891f51`.

The same artifact passed the full packaged runs:

- Forge: `forge/run-smoke-forge-p1419hx4/launch.log`.
- OptiFine M5: `forge/run-smoke-optifine-v5y7q1ey/launch.log`.
- Each: **55 production-HUD checks**, GUI 103, module lifecycle 364, gameplay 139,
  deep gameplay 125, quality gameplay 56, render 55, controls 30, network 46.
- New checks cover matching colored/plain font metrics, bounded text cache,
  complete armor labels, team colors, health percentages, default spacing,
  font closure and atlas deletion. Two unit tests cover native navigator grouping,
  wrapping and module toggling. Existing config/drag/lifecycle tests still pass.
- **100 populated draws: 191.14 ms Forge / 111.63 ms OptiFine**, with zero game-data
  resamples. Smooth text has a higher draw cost than the previous native-font
  fixture (73.82 / 83.29 ms); the fixtures differ in styling and table content, so
  this is not a controlled FPS comparison. Atlas and formatted-text caches are
  bounded, and inactive owners release textures/listeners.
- World unload/rejoin and shutdown finish with subscriptions=0 and queued=0.
  No NPE, missing-member or out-of-memory failures were found. Known Forge
  signature/Twitch/audio warnings and deliberate exception tests remain.

Actual captures were compared with the Adjust screenshots: compact typography,
neutral flat panels, the five-category menu, a 224-pixel player table, and the
left-only stepped module outline. The initial capture exposed category/effects
overlap and clipped armor percentages; those are fixed in the accepted runs.
The in-world capture binds the actual game framebuffer and temporarily hides
fixture chat, avoiding stale-frame artifacts from the initial capture method.

Reference typography is unidentified, so this uses the existing smooth Sans
renderer without claiming an exact typeface. External statistics remain blank.
Optional panels can still overlap if enabled together on small screens; arrange
those in the editor. No authenticated multiplayer or gameplay FPS claim is made.

[Usage/settings](HUD.md), [machine-readable evidence](verification/hud-adjust-20260930.json),
[reference layout](verification/hud-adjust-reference-layout-forge.png),
[in-world Forge](verification/hud-adjust-in-world-forge.png),
[in-world OptiFine](verification/hud-adjust-in-world-optifine.png).

Snapshot: `.backups/before-hud-adjust-20260929-212153/`. Run
`python3 scripts/restore-hud-adjust.py` for a checksum-only dry run; `--apply`
restores the previous HUD after preserving current affected files. Isolated
restoration and later-edit refusal are checked in `rollback-verification.json`.
The earlier pre-HUD backup/helper remain intact.

## Common HUD and editor — 2026-09-29

`check assemble --offline` passes **215 tests** (155 core, 60 Forge), architecture,
wrapper, dependency and packaged-JAR verification. Final artifact:
`forge/build/libs/Perihelion-0.1.0.jar`, 1,736,390 bytes, SHA-256
`0a4e2a7ad2cfcf4f5d1f2be710d32097cd297f0f6d61be2ead085508496286e2`.

The same JAR passed full isolated Forge and Forge + OptiFine M5 runs:

- Forge: `forge/run-smoke-forge-88z26yub/launch.log`.
- OptiFine: `forge/run-smoke-optifine-fumvu24v/launch.log`.
- Each: **47 production-HUD checks**, GUI 103, module lifecycle 364, gameplay 139,
  deep gameplay 125, quality gameplay 56, rendering 55, controls 30, network 46.
- HUD-specific checks cover real armor rendering, potion durations, physical CPS,
  bounded nearby-player/notification data, target removal, GL restoration, viewport
  clamping, editor drag/release/hide/show/reset, GUI access, returning to the parent
  GUI, deactivation cleanup, and narrow-menu search separation.
- 100 repeated draws performed **zero data resamples**. Measured wall times were
  **73.82 ms Forge / 83.29 ms OptiFine** for the populated synthetic fixture. These
  include driver costs and do not establish a real-game FPS guarantee.
- Full unload/rejoin/shutdown completed with zero remaining subscriptions/tasks.
  No NPE, missing-method/field or out-of-memory errors were found. Deliberate
  exception/quarantine tests and legacy startup warnings remain visible in logs.

The first runtime attempt exposed stepped coordinate validation; normalized
positions now use continuous bounded settings, with config round-trip tests.
Later fixture corrections excluded hidden bounds and avoided clicking another
panel after deliberately overlapping it. Only the final passing runs above are
acceptance evidence. No authenticated multiplayer claim is made.

[Usage/settings](HUD.md), [machine-readable evidence](verification/hud-20260929.json),
[Forge HUD](verification/hud-production-forge.png),
[OptiFine HUD](verification/hud-production-optifine.png),
[editor](verification/hud-editor-forge.png),
[element menu](verification/hud-elements-forge.png).

Pre-change snapshot: `.backups/before-hud-20260929-145445/`. The guarded helper
`scripts/restore-hud.py` verifies hashes, preserves current affected files before
restoring, and refuses later edits. Its isolated restore/refusal result is retained
in the snapshot's `rollback-verification.json`.

## Gameplay quality improvements — 2026-09-29

Implemented the reviewed inventory protections, Bow Aimbot/AutoPot/Auto Projectile fixes,
fractional Aura/AutoClicker timing, smarter looting, optional Scaffold conditions and stack
selection, native Clutch, and a per-tick trajectory preview cache. Defaults, compatibility
and rollback are detailed in [QUALITY_IMPROVEMENTS_2026-09-29.md](QUALITY_IMPROVEMENTS_2026-09-29.md).

`check assemble --offline` passes **207 tests** (155 core, 52 Forge), with zero failures,
errors or skipped tests, plus architecture, wrapper, mappings and distribution verification.
The artifact is **1,704,000 bytes**, SHA-256
`a8084696ed4275082e753289db83d761aab2734c5d2372fe508f983d4dd9e0ed`.

Final packaged runs on that exact artifact and the completed regression fixtures:

- Forge 2318: `forge/run-smoke-forge-8zgefehc/launch.log` — PASS.
- Forge 2318 + OptiFine M5: `forge/run-smoke-optifine-8z5463y7/launch.log` — PASS.

Both pass **56 new quality**, **125 deep gameplay**, **139 existing gameplay**, **360
lifecycle**, **103 GUI**, **46 network**, **30 control** and **55 render** assertions.
They complete config reload, world unload/rejoin and shutdown with subscriptions=0,
queued=0, and cleared hook/world/player/network references. Neither final log contains a
NullPointerException, OutOfMemoryError, NoSuchMethodError or NoSuchFieldError. Expected
intentional fault-injection and legacy Forge/audio warnings remain.

The new packaged checks exercise protected empty destinations, named items and registry
IDs; disabled Manage roles; explicit inferior-item deletion while preserving utility
blocks; equipment-aware loot and partial-stack capacity; silent bow progress across eight
ticks and ownership loss; valid versus blocked flight arcs; mixed harmful potions and
screen cancellation; replaced projectile items; Scaffold stack selection/conditions;
knockback-only Clutch arming, actual strict placement and claim release. Plain unit tests
compare bow solutions against an independent step-by-step flight simulation and verify
average CPS at multiple rates with stall/clock-reset cases.

The trajectory probe measures drawing plus calculation in the same local fixture:
100 cached draws cost **5.26 ms Forge / 6.28 ms OptiFine**, versus **31.88 / 31.19 ms**
for 100 forced recalculations. This measures the repeated-work reduction, not game FPS or
performance on another computer. Prediction remains bounded at 300 steps per client tick.

Earlier attempts exposed fixture-only issues: the added module changed the expected count
from 78 to 79 including ClickGUI; the Clutch fixture added one deliberate velocity event;
and item-registry checks require Forge's transformed classloader rather than plain JUnit.
Those expectations were corrected without suppressing the underlying assertions.

The source rollback archive retains 307 original files. Rollback validation and an isolated
apply/later-edit-refusal check are recorded under the backup directory. Exact artifact,
log, fixture hashes and counts are in [verification/quality-improvements-20260929.json](verification/quality-improvements-20260929.json).
These local game fixtures and loopback checks are not authenticated multiplayer evidence.

## Named configs and dot commands — 2026-09-28

Implemented the Configs tab, explicit named JSON presets, local dot commands and local
Tab completion. Existing working settings continue to autosave only after changes;
loading a preset does not turn it into an automatically overwritten shared file.
See [CONFIGS.md](CONFIGS.md) for usage, sharing, storage semantics and rollback.

`check --offline` passes **202 tests** (155 core, 47 Forge), plus architecture, Java 8,
resource, distribution and explicit chat/Tab-completion Mixin mapping checks. The JAR is
**1,684,328 bytes**, SHA-256
`ac7b884ba4969905946f9132dc1f674f4e2947a962c6323aa797906c483df16e`.

Final full packaged runs on that exact artifact:

- Forge 2318: `forge/run-smoke-forge-4kml5g4v/launch.log`.
- Forge + OptiFine M5: `forge/run-smoke-optifine-bq3_vsw1/launch.log`.

Both pass **103 GUI**, **125 deep gameplay**, **139 existing gameplay**, **356 lifecycle**,
**46 network**, **55 render** and **30 controls** assertions. Both complete config round
trips, world unload/rejoin and shutdown with no retained Perihelion subscriptions or tasks.
The new GUI assertions exercise actual preset controls, packet-free dot command execution
and completion, confirmation/backup behavior, and small-window bounds at three menu scales.
The final Forge config screenshot was visually inspected. OS file associations/opening are
not part of the automated gameplay test. Existing intentional fault-injection and legacy
Forge/audio warnings remain; neither final run has a smoke failure marker.

The original 294-file source snapshot is retained. The rollback helper has been tested in
an isolated copy, including refusal to overwrite later edits. Exact evidence is in
[config-profiles-20260928.json](verification/config-profiles-20260928.json).

## Deep gameplay audit — 2026-09-28

The final artifact passes **194 tests** (147 core, 47 Forge), architecture/package checks,
and packaged full Forge 2318 and Forge + OptiFine M5 runs. It contains **77 gameplay
modules plus ClickGUI**. The JAR is **1,666,169 bytes**, SHA-256
`3da12fd1038bc55e133999066379c406e2abc1f0b370fa3b1694b4c756a00b9b`.

- Forge: `forge/run-smoke-forge-spechkx5/launch.log`.
- OptiFine: `forge/run-smoke-optifine-8gz3cocq/launch.log`.

Both pass **125 new deep gameplay assertions**, **139 existing gameplay**, **356 lifecycle**,
**46 network**, **55 render**, **30 controls** and **52 GUI** assertions. Both complete config
round trips, unload/rejoin and shutdown with cleared references, listeners and queued tasks.
The deep probe covers 24 Scaffold bridge/slot/ray combinations, Expand/Keep Y, stale
placement gates, Aura invalidation/switching/block modes, cumulative Back Track movement,
Ultra Safe reference comparisons and bounded Block ESP scanning.

Two earlier OptiFine runs on this same artifact passed gameplay/rejoin and exited normally
but omitted all fixture shutdown markers; they remain recorded as failed smoke runs.
The test-only shutdown hook now also writes an independent `shutdown-report.txt`.
The final run passes the unchanged launcher criteria and records successful cleanup in
both outputs. The earlier missing-output cause remains unconfirmed; the extra report
does not establish that an intermittent issue has been fixed.

Fix details, source differences, rollback and limitations are in
[DEEP_AUDIT_2026-09-28.md](DEEP_AUDIT_2026-09-28.md); exact run evidence and performance
samples are in [deep-audit-20260928.json](verification/deep-audit-20260928.json).
These are local synthetic fixtures, not authenticated multiplayer or universal donor parity.
The 285-file pre-audit backup is retained, with a guarded rollback helper at
`scripts/restore-deep-audit.py`.

## Selective donor improvements — 2026-09-27

The current registry contains **77 gameplay modules plus ClickGUI**. Seven new native
modules and the optional improvements are described in [DONOR_IMPROVEMENTS.md](DONOR_IMPROVEMENTS.md).
This section supersedes the artifact/counts in the historical sections below.

`check --offline` passed **187 Java 8 tests** (147 core, 40 Forge), including new
bounded scan traversal and forward-tap timing/cancellation regressions. Architecture,
wrapper, bytecode, resources and Mixin reference-map/package checks passed. The final
remapped JAR is **1,660,504 bytes**, SHA-256 `00efda5fdcbff064a488095ba36d2188c0d8d7fedf40c55aef9c4ef8b6cf4b0f`.

Both packaged `scenario=full` runs used that exact artifact:

- Forge 2318: `forge/run-smoke-forge-95s747l5/launch.log`.
- Forge + OptiFine M5: `forge/run-smoke-optifine-mf4ksi0i/launch.log`.

Both passed **356 module lifecycle**, **139 gameplay**, **46 network**, **55 render**,
**30 shared-control** and **52 GUI** assertions. They completed config round trips,
world unload/rejoin and shutdown with zero Perihelion listeners/queued tasks.
The gameplay additions check Aura FOV/weapon restrictions, independent AimAssist
selection and bounded rotation, silent convergence across tick expiry, detached camera
rotation/restoration, the actual transformed Freecam right-click gate, the actual Zoom
FOV path/scroll/restoration, Camera effect settings, Auto Eat start/cleanup and real
inventory refill, plus the Bed Plates scan/render path. Full module fault/world-cycle
probes include every new definition. Donor inventory coverage remains complete.

The checks found and fixed Auto Eat's incorrect assumption that `sendUseItem` reports
whether eating started: its boolean actually reports a stack change. The survival-state
fixture now verifies item use and cleanup. Source review also added the Freecam gate
for direct native attack/use/dig actions, aborts prior mining on activation, and makes
Anti Fireball yield when its rotation claim loses. Cleanup/release packets are preserved.

These checks do **not** cover authenticated multiplayer, prolonged manual play,
all physical input/CPS/block-hit combinations, every chest/server response, exhaustive
bed layouts, shader packs or arbitrary module combinations. Bed Plates render checks
prove execution/state cleanup, not visual layout quality at every scale. Expected
intentional fault-injection logs and legacy Forge signature/Twitch/sound messages remain;
the final runs have no smoke failure marker. No donor repository was modified.

Rollback: the verified 271-file pre-change snapshot is
`.backups/before-donor-improvements-20260927-125031/source.tar.gz`.
`python3 scripts/restore-donor-improvements.py` validates the archive and changed-file
manifest without restoring anything. The earlier Yuri backup remains intact.

## Yuri gameplay expansion — 2026-09-27

The inventory audit accounts for **86 donor definitions**: **65 native module mappings**
and **21 excluded presentation modules**. The production registry has **70 gameplay
modules plus ClickGUI**, all gameplay definitions disabled by default. This records module
coverage; detailed native adaptations are in [YURI_PORT.md](YURI_PORT.md).

`check` passed with **183 Java 8 tests** (147 core, 36 Forge). This includes packet snapshot
ownership, FIFO/delay/capacity/concurrent queue behavior, all included donor-to-registry
mappings, default world scoping, and bounded/finite Aura ML sample parsing. Architecture,
wrapper, bytecode, bundled-library, resource and Mixin-reference-map checks passed.
The remapped JAR is **1,606,034 bytes**, SHA-256
`9478b825b5475b7a1d978d2822cffa1e29b4d6786cc3ad1115474b7e9266f3ae`.

Packaged Forge 2318 (`forge/run-smoke-forge-7q7hx5fn/launch.log`) and Forge + OptiFine M5
(`forge/run-smoke-optifine-k5apvsb6/launch.log`) passed `scenario=full`, including world
creation, config round trips, unload, rejoin and zero retained Perihelion listeners/tasks
at shutdown. Both passed 328 production-module lifecycle assertions, 46 transformed
network assertions, the localhost TCP codec fixture, 55 render-scope assertions,
30 shared-control assertions and 52 GUI assertions. Both final runs passed **97 gameplay
assertions**, including explicit stale-world replay checks.

The focused gameplay checks invoke actual transformed Flight movement, Stasis movement
cancellation, Step restoration, Camera swing duration, Aura's controller attack and
Refill/Manager inventory transactions. They verify Free Look's independent camera state,
Blink snapshot hold/ordered flush and old-world discard, and nine native visual draw paths
with framebuffer/program/viewport/clear-color restoration. Visual draw events are explicitly
injected in the game's GL context; this proves execution and restoration, not pixel equality.
The fixture uses disposable client-side entities/items and does not contact a public server.

A real Shader ESP LWJGL buffer-size failure found by the draw probe was corrected. A
separate regression now feeds chunk and bulk packets to a retired handler after the
client-thread handoff. Those packets are ignored before touching the retired world. The
previous unload-time `NetHandlerPlayClient.func_147263_a` null-world failure did not recur
in the passing runs. Intentional HUD/module exception-quarantine tests still log their
expected failures. Legacy Twitch/sound-library startup/shutdown messages remain unrelated
to module correctness.

Limits: no authenticated multiplayer gameplay, server-specific bypass effectiveness,
exhaustive testing of every mode/combination, extended play session, or other GPU/driver
configuration is established by these checks. A passing smoke marker is not evidence of
those claims. See the [machine-readable evidence](verification/yuri-port-20260927.json).

## Inline setting value entry — 2026-09-26

Right-clicking a numeric, range, text, color channel or hex setting now edits
the value inside that setting's panel row. The large centered value dialog is
gone. The field has no fill, border or underline, and the original label stays
visible beside a blinking text caret. The value uses the selected GUI font at
the same size as nearby settings, with its text and caret snapped to framebuffer
pixels. Minecraft still handles typing, selection and paste. Enter applies
through the existing setting manager; invalid values keep
the editor open without changing the setting. Escape or a click outside cancels.
Keybind capture remains separate. The first typed character replaces the
displayed value without a bright selection block. The old value is hidden while
editing so it cannot show through the transparent field. The
[normal-scale capture](verification/clickgui-inline-hex.png) and
[1.05-scale capture](verification/clickgui-inline-hex-fractional.png) were
inspected from a bounded Forge + OptiFine M5 smoke run.

Normal `check` passed with **171 tests**. The packaged JAR is **1,245,564 bytes**,
SHA256 `c6976c5cb57d4501436bf18d17fd558accb54d01355020f0b7d195c9cf0d2a41`.
The Forge 2318 + OptiFine M5 world smoke at `forge/run-smoke-optifine-qwt6ry6b/`
passed **52 GUI checks**, including inline apply/cancel/invalid input, themed
caret placement at normal and fractional scale, panel
rendering and GL restoration, plus world unload/rejoin and clean shutdown.
Physical mouse use and other graphics setups remain outside this bounded run.

## Adjust-style consistency pass — 2026-09-26

The panel frontend now uses a higher-resolution cached font atlas for smoother
small text and keeps Minecraft's native font at its native size. Expanded settings
have one flat translucent section and one pixel-aligned separator. Slider tracks
snap to a single framebuffer pixel at fractional menu scales. Module hover and
expanded states use restrained neutral shading. The fresh-config panel opacity is
0.75; saved appearance preferences are respected.

The [default layout](verification/clickgui-adjust-consistency.png) and a
[clean Client settings capture at 1.05 scale](verification/clickgui-adjust-settings.png)
were inspected against the user-provided Adjust reference. The settings capture
uses a cleared test framebuffer and temporarily moves other panels out of view
so overlapping translucent panels do not obscure the font and lines. This is a
visual diagnostic, not a claim of pixel-identical output or live mouse testing.

Normal `check` passed with **171 tests**. The packaged JAR is **1,241,285 bytes**,
SHA256 `e9a51581147d1ef3d4cad96be1951593dc28aabd4c0f3195c48eabb9fb77e4e1`.
Forge 2318 + OptiFine M5 world smoke at `forge/run-smoke-optifine-ikpxginm/`
passed **43 GUI checks**, including all four fonts and GL restoration, plus
world unload/rejoin and clean Perihelion shutdown.

## Translucent expanded settings and text — 2026-09-26

The expanded-section tint and bevel now track the Panels opacity preference. A
0.6-opacity [smoke capture](verification/clickgui-translucent-settings.png) was
inspected: the inset remains translucent while keeping a distinct edge. The
capture intentionally overlaps Client and Render to exercise panel z-order;
the HUD event text is test-only. Panel text is slightly smaller, and visible
module names, keybinds, enum choices and color channel labels use normal case.
Stored module IDs, setting keys and enum values are unchanged.

Normal `check` passed with **171 tests**. The packaged JAR is **1,241,158 bytes**,
SHA256 `eff403bbe4e0d4f0498f1f7cfccc5736dd3b1fc9c538c59b8bf0b51f2021a34c`.
The Forge 2318 + OptiFine M5 world smoke run at
`forge/run-smoke-optifine-xm8q6zsh/` passed **43 GUI checks**, including all four
fonts and GL restoration, plus unload/rejoin and clean shutdown. Physical input,
other GPU setups and long play sessions remain outside this bounded check.

## Expanded-panel visual cleanup — 2026-09-26

Expanded module settings now sit in a single dark recessed section with a subtle
one-pixel bevel. Numeric tracks are one pixel high and use the chosen accent color;
the repeated slider backplates are removed. Rendering and interaction remain in
the existing panel frontend and setting controls. [Expanded Client capture](verification/clickgui-inset-settings.png)
was inspected; its HUD event label is test-only.

Normal `check` passed with **170 tests**. The packaged JAR is **1,239,551 bytes**,
SHA256 `5784f12a21080ecb6b63d34c7db787214c05db82e4aa906a5982070070c8f412`.
The Forge 2318 + OptiFine M5 world smoke run at
`forge/run-smoke-optifine-8lh_pdv4/` passed **43 GUI checks**, including GL state
restoration, plus unload/rejoin and clean Perihelion shutdown. This automated
capture does not cover extended physical mouse use or other graphics setups.

## ClickGUI readability — 2026-09-26

The panel layout now uses 120-pixel logical columns so setting names fit. Longer
labels are fitted within their rows without ellipses. Empty categories show only
their headers, and the default panel background is darker to keep overlapping
content legible. Hover help is description-only, appears after 300 ms, and can be
turned off under Client → ClickGUI → Tooltips. The wider layout uses a new panel
layout key; other saved preferences and gameplay settings remain intact.

Normal `check` passed with **170 tests** and the packaged JAR was verified:
`forge/build/libs/Perihelion-0.1.0.jar`, **1,239,378 bytes**, SHA256
`bb0f886697123dda17267292ba69bbdea521c8eb364a2bfd94734aceb2662169`.
The Forge 2318 + OptiFine M5 world smoke run at
`forge/run-smoke-optifine-eknhrd3z/` passed **43 GUI checks**, including tooltip
disable/persistence, all category panels, module and setting interaction,
scrolling, panel overlap, search, layout reset, fault handling and GL restoration.
It also completed unload/rejoin and shutdown with no remaining subscriptions or
queued work. [Default layout](verification/clickgui-readable.png) and
[expanded Client settings](verification/clickgui-readable-settings.png) were
visually inspected. The HUD event label in those captures belongs to the smoke
fixture. This is a bounded automated run, not a long physical-input playtest.
An earlier run of this exact JAR passed the GUI checks but the overall smoke
failed during the fixture's second world join after the previously observed
vanilla queued-chunk callback exception. The bounded rerun above completed.

## Compact GUI and Prism startup compatibility — 2026-09-25

The GUI now follows the reference's narrow rectangular columns, centered blue headers,
smaller type, translucent charcoal rows and minimal symbols. Branding, the Panels menu,
hide buttons and toolbar reset button are removed. Search defaults off; reset uses Ctrl-R
or `/perihelion gui reset`. A new frontend-only layout key gives the compact design fresh
positions while retaining gameplay and appearance preferences. [Screenshot](verification/clickgui-compact.png)
(the HUD event label is test-only).

Normal checks pass with **170 tests** (147 core, 23 Forge), plus distribution checks.
Artifact: **1,239,512 bytes**, SHA256
`97d386a4589c31abf4d4ff8b32bff739f3ef4f25168cb4d291e5488abef58d60`.

The supplied Prism log's fatal `PlayerControlsMixin.motionYaw` injection was reproduced
using the exact Raven (`f6e476a45d.jar`) and Animations JARs in an isolated offline run.
The dumped transformed walking method consists solely of Raven's replacement call and
return; no vanilla rotation reads remain. The two read redirects now tolerate that
replacement. A post-transform check requires both installed hooks before exposing
server-only rotation, preventing half-applied pairs and reporting one startup warning.
All other Mixin requirements remain strict. The current nine gameplay modules do not
request server-only rotation. Future modules must check `supportsServerRotation()`.

| Check | Directory | Outcome |
| --- | --- | --- |
| Original startup reproduction | `forge/run-smoke-forge-soe9s8xi` | Same fatal motionYaw injection; bounded termination |
| Fixed startup with Raven, Animations, OptiFine M5 | `forge/run-smoke-optifine-nf09_5m5` | Stable main menu, exit 0, STOPPED |
| Fixed normal Forge + OptiFine world/GUI | `forge/run-smoke-optifine-eqmh5g4i` | 42 GUI, 30 controls (including silent rotation), 84 modules, 55 rendering and 261 HUD checks; exit 0 |

The GUI screenshot was visually inspected. Existing world-fixture queued-chunk exceptions
remain. No existing Prism settings/accounts/mods were modified; replace its old Perihelion
JAR with this build to use the changes. The original full mod stack and active Raven
movement modules were not tested. Prism's low-memory warning is separate from the
reproduced fatal injection. Donor repositories were not changed.

Evidence: [compact-gui-compatibility.json](verification/compact-gui-compatibility.json).
Earlier sections record historical artifacts and the original GUI behavior.

## Panel ClickGUI — 2026-09-25

Normal `check` passed with **169 Java 8 unit tests** (147 core, 22 Forge), zero failures,
errors or skips. The eight new tests cover layout serialization/z-order/recovery/reset,
bounded scrolling, min-relative numeric snapping, rainbow values/codecs, default-binding
persistence and revoking input permission during a shared binding dispatch. Architecture,
wrapper, dependency boundaries, Java 8 bytecode and remapped distribution checks passed.

Artifact: `forge/build/libs/Perihelion-0.1.0.jar`, **1,236,434 bytes**, SHA256
`90338944bc0645c3675f7f44b1fa3bda203e02531b991a71be6998e7b65f509c`.

| Packaged world run | Evidence directory | Result |
| --- | --- | --- |
| Forge 2318 | `forge/run-smoke-forge-1gi__k69/` | PASS, 42 GUI checks, exit 0 |
| Forge 2318 + OptiFine M5 | `forge/run-smoke-optifine-fhl9bwz3/` | PASS, 42 GUI checks, exit 0 |

Both runs exercised the command opener and the normal Right Shift binding, all eight
core categories, four font renderers, GUI scales 0.75/1/1.5, real GL state restoration,
module toggles, all setting editor types, conditional visibility, drag capture outside
bounds, panel scrolling/collapse/dragging, overlap hit order, search/disable, hide/reset,
off-screen resize recovery, binding capture without gameplay activation, fault rendering,
and layout/preferences/rainbow persistence through config restart. Capture/hover state
is excluded from config. Screenshots were visually inspected on both Forge and OptiFine:
[Forge](verification/clickgui-forge.png), [OptiFine](verification/clickgui-optifine.png).
The diagnostic HUD text behind the toolbar belongs to the smoke fixture.

The same runs passed 84 gameplay-module, 55 rendering-helper, 30 shared-control and 261
HUD checks. They unloaded/rejoined the world, saved/reloaded config and shut down with
zero subscriptions or queued work. The existing vanilla queued-chunk callback exceptions
after fixture-driven unload remain; this is not a claim of error-free game logs. Fault
injections are deliberate. See the previous module verification section for that known
fixture issue and pre-GUI evidence.

Tests use the real transformed client with logical-coordinate inputs through the same
screen handlers used by Minecraft. Windows were unfocused; physical input sessions,
OS fullscreen switching, shader packs, unusual font/resource packs, other OS/GPU setups
and long-duration play were not tested. Packet/development scenarios were not repeated.
Logical JRE fonts can differ across operating systems; native text fields keep the
Minecraft font. No transition animations or full keyboard widget navigation were added.

The project is still untracked, so scope review used the pre-change file-hash inventory
and direct review of changed files instead of claiming a tracked Git diff. Changes are
limited to GUI composition/helpers, generic category/default-binding/color support,
related tests and documentation. All four donor worktrees are clean and unchanged.
[DONORS.md](DONORS.md) records the screenshot inspiration; no donor GUI source was copied.

See [the GUI guide](CLICKGUI.md) for controls and replacement boundaries, and
[clickgui.json](verification/clickgui.json) for artifact/fixture hashes and outcomes.
Earlier sections below describe historical artifacts.

## First native modules — 2026-09-25

Full `check` passed with **161 Java 8 unit tests** (143 core, 18 Forge), zero failures,
errors or skips. The eight new unit cases cover timer pulse boundaries/mode changes,
WTap chance/pending-reset rules, nine world-scoped default-disabled definitions,
visual listener retirement/rejoin and timer setting validation/visibility. Architecture,
dependency boundaries, packaged module entries, Java 8 bytecode and all seven Mixins'
SRG targets passed distribution verification.

Artifact: `forge/build/libs/Perihelion-0.1.0.jar`, **1,180,916 bytes**, SHA256
`6944c59d8d381794b519d5f20be402825c8c0b3abaab08edc9b1e4451dc3582a`.

| Packaged world run | Evidence directory | Result |
| --- | --- | --- |
| Forge 2318 | `forge/run-smoke-forge-nlh742ix/` | PASS, 84 module assertions, exit 0 |
| Forge 2318 + OptiFine M5 | `forge/run-smoke-optifine-nht632l1/` | PASS, 84 module assertions, exit 0 |

Both runs exercised the shipped definitions in a real integrated world:

- Fullbright's actual lightmap equals gamma 100, while saved brightness remains unchanged.
- Real FOV, hurt-camera and bobbing transforms switch to vanilla after disable.
- Blindness fog distance/color return to normal while the potion remains active.
- Actual timer speed restores its baseline; block/egg/snowball delays respect settings,
  including disabled projectile handling, zero delay, empty hand and disabled module.
- The transformed controller attack hook arms WTap; its reset outranks Sprint and expires
  after one control tick. Sprint yields when configured to exclude invisibility.
- Every module retires on a simulated runtime world transition, reactivates with a fresh
  context, is quarantined after a deliberate owned callback failure, and can be retried.
- Actual world unload/rejoin, config save/reload, command handling and shutdown complete,
  with zero remaining subscriptions/queued work and cleared adapter/hook references.

The existing 55 rendering-helper and 30 shared-control assertions and HUD fixture also
passed. Direct update-event posts make the module checks deterministic; they are not a
substitute for sustained physical gameplay. The windows reported `inputFocused=false`.
External multiplayer, knockback effectiveness, real keyboard movement, long-duration
play, shader packs, custom lightmaps and arbitrary mod combinations were not tested.
Packet-only and development scenarios were not rerun.

Log review also found vanilla queued chunk callbacks raising `NullPointerException`
after the smoke fixture unloads its world. The same pattern is present in the prior
HUD smoke logs (`run-smoke-forge-x86io8jw`, `run-smoke-optifine-157834np`), before these
modules existed. Both current runs still rejoined and shut down successfully; their
PASS markers do not mean the entire game log is error-free. Module-fault stack traces
in these runs are deliberate test injections. The initial new fixture run also exposed
an incorrect reflective fog-color field name, corrected before the passing runs.

Machine-readable evidence: [native-modules.json](verification/native-modules.json).
Behavior, setting defaults and intentional donor fixes: [MODULES.md](MODULES.md) and
[DONORS.md](DONORS.md). Earlier sections below describe historical artifacts.

## HUD rendering event — 2026-09-21

Full `check` passed with **153 Java 8 tests** (143 core, 10 Forge), zero failures,
errors or skips, including the HUD event's payload, distinct dispatch and owner
cleanup. Architecture boundaries, Java 8 bytecode and distribution checks passed.

Artifact at that update: `forge/build/libs/Perihelion-0.1.0.jar`, **1,154,477 bytes**, SHA256
`0dcaf7be03117aeb8259f75bf4a304cafc3ceec16003d271c5752d2b33a0749c`.

| Packaged world run | Evidence directory | Result |
| --- | --- | --- |
| Forge 2318 | `forge/run-smoke-forge-x86io8jw/` | PASS, exit 0, clean STOPPED |
| Forge 2318 + OptiFine M5 | `forge/run-smoke-optifine-157834np/` | PASS, exit 0, clean STOPPED |

Both runs verified actual `Post(ALL)` filtering, client-thread delivery, matching
partial ticks and scaled resolution, the live GUI projection, GUI scales 1 and 2,
F1 hiding while chat is open, cancelled overlays, and resumed drawing behind chat.
A deliberately throwing HUD listener restored its temporary GL state before the
next listener drew and was detached by the existing event-owner boundary. The
initial run exposed an overly strict exact-equality assertion for the native GL
projection; the fixture now uses a small floating-point tolerance.

The runs also passed the existing 55 rendering-helper and 30 shared-control
assertions, title/world/unload/rejoin, config/command checks and cleanup with no
remaining subscriptions or queued work. The production module registry stayed empty.
The test-only native-font label was visually inspected in both world captures:
[Forge](verification/hud-forge.png) and [OptiFine](verification/hud-optifine.png).

Machine-readable evidence is in [hud-rendering.json](verification/hud-rendering.json).
These checks cover isolated integrated worlds on this workstation. Window resizing,
other font/resource packs, shader packs, external servers and arbitrary mod combinations
were not tested; the packet-only fixture and development launch were not rerun.
No HUD components or production visual modules were added.

## Module and setting metadata — 2026-09-21

Full `check` passed with **138 Java 8 tests** (128 core, 10 Forge), zero failures,
errors or skips. New cases cover every setting factory's explicit display name,
metadata validation, typed module categories, and config/command compatibility
when labels and categories change. Stable IDs and config schema are unchanged.

Artifact at that update: `forge/build/libs/Perihelion-0.1.0.jar`, **1,152,321 bytes**, SHA256
`974c8564c70911d5ca8f13308a0bcdfd6aded38fac3fb383d20412c82c4a50f8`.
All smoke fixtures, including the migrated control fixture, compiled against that
artifact with `scripts/launch-smoke --prepare-only --scenario title`. The prepared
directory is `forge/run-smoke-forge-55i872uf/`; no client was launched for this metadata
change. Earlier graphical smoke results below apply to their recorded artifacts.

## Rendering helpers — 2026-09-21

Full `check` and the remapped distribution passed. **133 Java 8 unit tests passed**
(123 core, 10 Forge), with zero failures, errors or skips. The six new tests cover
scope cleanup/ownership and native-font coordinate/alpha handling.

Artifact at that update: `forge/build/libs/Perihelion-0.1.0.jar`, **1,151,086 bytes**, SHA256
`6d8f78bcfb1b0e73e3cc952170191145437fac66d714203f489bc10d6d35a3b9`.

| Packaged rendering run | Evidence directory | Result |
| --- | --- | --- |
| Forge 2318, `title` | `forge/run-smoke-forge-kbxto0fl/` | PASS, 55 render assertions, exit 0, clean STOPPED |
| Forge 2318 + OptiFine M5, `title` | `forge/run-smoke-optifine-qbmnl44s/` | PASS, 55 render assertions, exit 0, clean STOPPED |

The probes verified actual GL state across nested scopes, a deliberate exception,
raw calls that bypass Minecraft's cache, leaked matrix pushes, 100 repeated scope
uses, and font drawing. Default-font framebuffer samples were visually inspected:
[Forge](verification/rendering-forge.png) and [OptiFine](verification/rendering-optifine.png).
Plain, shadowed, formatted, Unicode and translucent labels rendered at native GUI
size; the zero-alpha label remained invisible. The production module registry stayed empty.

Machine-readable evidence is in [rendering.json](verification/rendering.json).
These are title-screen rendering tests on this workstation, not gameplay/ESP/Chams
tests or a cross-GPU/GUI-scale/font-pack guarantee. Shader-pack state is outside
the helpers' documented boundary. World, packet and development smoke scenarios
were not rerun for this change. The implementation reuses query buffers; no isolated
allocation or frame-rate benchmark is claimed.

## Shared-controls update — 2026-09-21

That update added activation-owned shared controls and included the
preceding self-disable, coalesced-config and expanded-setting changes. **124 Java 8
unit tests passed**: 120 core tests and 4 adapter restoration tests, with no
failures, errors or skips. Full `check` passed; after the shutdown-slot fix,
`:forge:check` passed again. Architecture/dependency boundaries and the five required
client Mixins, SRG mappings, Java 8 bytecode and final packaged entries were checked.

Artifact at that update: `forge/build/libs/Perihelion-0.1.0.jar`, **1,140,017 bytes**, SHA256
`b1d54d487c800853651c523dc45f8335e80e4d70b0d2eef3b4b1a09b23579963`.

| Packaged run | Evidence directory | Result |
| --- | --- | --- |
| Forge 2318, `world` | `forge/run-smoke-forge-iwntkohg/` | PASS, exit 0, clean STOPPED |
| Forge 2318 + OptiFine M5, `world` | `forge/run-smoke-optifine-dyx027bm/` | PASS, exit 0, clean STOPPED |

Each performed **30 transformed control assertions** in a real integrated world:
timer priorities/restoration; input fallback and unchanged physical fields/click
counts; actual sprint state; visible/spoofed slot arbitration and guarded item
actions; throwing-action restoration; walking packet rotation caches and unchanged
server-mode camera; view rotation and player phases; worker-thread fault fallback;
disable/stale handles; world cleanup; and direct shutdown inside a spoofed action.
The last case exposed a task-generation check that incorrectly skipped final slot
synchronization on shutdown; the fix was verified by both passing runs.

The world scenarios also verified title/world/rejoin, commands, asynchronous
config save/reload, clean shutdown, and empty production registry. The windows
reported `inputFocused=false`, so physical focused-window input and forced held-key
behavior still need an interactive check; core key priority/ownership tests passed.
External multiplayer, riding rotations, arbitrary mod combinations and completed
gameplay modules were not exercised. The existing packet fixture and development
launch were not rerun because this check focused on the new packaged control hooks.

The first sandboxed launch aborted in macOS Java window registration; normal
desktop access resolved that environmental issue. Compact current evidence is in
[shared-controls.json](verification/shared-controls.json). The original foundation
record below describes an older artifact and its broader initial checks.

## Original foundation record

Verified locally on **2026-09-20**, macOS 26.6.2 / Apple Silicon, Azul Java 8 `1.8.0_502` for tests and Minecraft, Temurin Java 17 for Gradle 8.4. This record separates core tests, transformed hooks and actual game launches.

## Distribution

- File: `forge/build/libs/Perihelion-0.1.0.jar`
- SHA256: `3de184d39360cfb3162a233e6e98b658dea067de1bcbf2ecbd03e62fd7713e68`
- Size: **1,094,336 bytes**; 668 ZIP entries.
- All Perihelion classes target Java 8. Bundled classes require no newer JVM.
- Core, Forge metadata, required legacy Mixin runtime, its license, the two client Mixins and generated reference map are present.
- No Minecraft, Forge, Gson, Guava, Netty, LWJGL, unshaded ASM, JUnit, benchmark or smoke-test classes are bundled.
- Rebuilding the distribution with all its tasks rerun produced the same hash.

`checkArchitecture`, `checkWrapper`, `:forge:verifyMixinRuntime` and `:forge:verifyJar` passed. Dependency verification was then run in ordinary strict mode, without generating new checksums. The two narrow verification exceptions are explained in [build notes](BUILD.md).

## Core regression suite

**58 tests, zero failures/errors**, compiled and executed using the Java 8 toolchain. Reports: `core/build/reports/tests/test/index.html` and `core/build/test-results/test/`.

| Area | Tests | Covered behavior |
| --- | ---: | --- |
| Events | 8 | Exact types, priority/ties, cancellation rollback, owner faults, staged listeners, mutation during dispatch, thread guard, fatal propagation, concurrent close |
| Modules | 8 | World cycles, activation rollback, explicit retry, network failure cleanup, broken cleanup, owned deferred work, duplicate IDs, reentrant lifecycle |
| Lifecycle | 5 | Idempotence, partial startup rollback, world identity/generation changes, shutdown during startup callbacks |
| Settings | 6 | Types, bounds, parsing, non-finite values, observer delivery, mutation guards |
| Configuration storage | 12 | Strict/bounded JSON, recovery, backups, newer schemas, failed writes, latest snapshot, shutdown flush |
| Configuration application | 7 | Unknown data, invalid values, reload races, failed observers, pending reload shutdown and unavailable queue |
| Input | 4 | Shared binding order, repeat suppression, held keys across focus/screens, lost release events, device validation |
| Commands | 4 | Quoting/escaping, validation, usage/completion, failure containment, setting-owner quarantine |
| Task queue | 3 | Capacity/drain bounds, producer handoff, stale generations, failure isolation and self-rescheduling |
| Connection hints | 1 | Normalization and complete Hypixel domain boundaries |

The shipped module registry is empty. Fault-injection and example modules exist only in test code.

## Actual launches

Each row below completed the full scenario, exited with code 0, and produced both PASS and clean STOPPED markers. Packaged runs used the distribution hash above.

| Mode | Evidence directory |
| --- | --- |
| Packaged Forge 2318 | `forge/run-smoke-forge-k9x9y4nu/` |
| Packaged Forge 2318 + OptiFine M5 | `forge/run-smoke-optifine-k9wsx334/` |
| Loom development `:forge:runClient` | `forge/run-smoke-development-forge-c7i7_2eo/` |

Every run directory contains `launch-manifest.json`, `result.json`, `launch.log` and title/world framebuffer captures. Generated worlds, settings and offline identities are isolated from existing game profiles. These directories are ignored by Git; this document and the [compact evidence record](verification/foundation.json) preserve the outcomes.

The scenarios verified a running core with zero production modules, 20 stable title-screen ticks, world creation, 40 stable world ticks, unload, return to menu, rejoin and 40 further world ticks. They checked client tick/render forwarding, world-generation transitions, local handling of valid and invalid `/perihelion` commands, exclusion of integrated-server traffic from raw multiplayer hooks, asynchronous save/reload in both world visits, configuration persistence and zero remaining subscriptions/tasks on shutdown. Direct assertions confirmed the adapter cleared its world/player/network-manager fields on unload and shutdown, and the static hook target was cleared. The probe also checked the version reported by Forge.

The OptiFine log confirms `OptiFine_1.8.9_HD_U_M5`. Its tested JAR SHA256 is `2739760a70fca6ae5c7dc817bcc6283f18f55e38fa23778da7f387a6b724b2c4`. It is not redistributed.

## Transformed packet checks

**46 assertions per full run** executed against the real LaunchWrapper/Mixin-transformed `NetworkManager`.

- Both outbound overloads and queued dispatch, exact counts and order.
- Cancellation completion-listener order, cancelled promise state and a still-usable channel.
- Inbound cancellation, client/server direction and PLAY filtering, local-channel exclusion.
- Raw callback recursion and failure handling; fatal errors propagate.
- Custom-payload buffer ownership.
- Inbound, outbound and completion thread identities.
- An ephemeral localhost TCP pair using Minecraft's actual framing/packet codecs, keepalive round trip, cancellation and Netty scheduling.

The TCP fixture enters PLAY directly with test handlers. It tests actual sockets and serialization, **not** a login/authentication handshake or an external multiplayer world.

## Performance evidence and limits

The standalone [dispatch benchmark](BENCHMARK.md) measured 0/1/100 listeners and zero measured steady-state allocation per reused-event post. This excludes event construction, registration and Minecraft work.

The final short packaged runs also recorded tick/frame intervals, heap snapshots, thread-allocation totals and timed core callbacks. Core callbacks consumed about 3.98 ms across 1,170 measured dispatches in the Forge run and 3.01 ms across 1,187 in the OptiFine run. Those totals include probe listeners. World-load spikes and process heap totals include Minecraft and are not attributable to Perihelion alone.

Zero remaining scopes/tasks, cleared adapter references and repeated module/world cycles support cleanup correctness. They are not a full heap-retention proof, long-duration soak test or controlled FPS comparison. Those measurements should be repeated around future features that allocate or render.

## Legacy issues resolved during verification

The tests caught and corrected Java 8 final-flush scheduling, lenient behavior in old Gson, reentrant activation after shutdown, and missing version metadata in Forge's development loader. A generated annotation constant now shares the same version property as packaged metadata.

The smoke harness also had two independent reporting/teardown races. Shutdown assertions now bypass Log4j's concurrently closing output appender. World teardown now uses Minecraft's `loadWorld(null)` directly: an earlier separate disconnect could stop the integrated server before its synchronous shutdown task ran. A [captured thread dump](verification/fixture-teardown-thread.txt) identified that wait inside the fixture call. Corrected runs completed with normal exit.

## Not established by these checks

- Hypixel login, authenticated multiplayer-world behavior, minigame logic or account-specific behavior.
- Other operating systems, other OptiFine builds or arbitrary mod combinations.
- Long-duration stability, forced-process-termination persistence or power-loss durability.
- Hosted GitHub Actions success; the workflow is present but has not run remotely.
- GUI, HUD, gameplay modules or late injection, which are outside this stage.

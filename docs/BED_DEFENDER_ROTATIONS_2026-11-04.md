# Bed Defender rotation and placement improvements — 2026-11-04

Reviewed Perihelion's Bed Defender, `BedDefensePlan`, shared rotation dynamics,
placement guards and walking receipts before comparing the read-only Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def` BedDefender and RotationManager sources.

## Resulting behavior

- The planner minimizes the actual yaw/pitch turn across valid support faces and
  face samples. Roofs no longer deliberately choose the largest angular change.
  A valid already-submitted ray is reused directly.
- Among reachable side cells, Bed Defender picks the smaller turn. Unreachable
  or player-occupied cells no longer stop planning for the entire shell.
  Reachable sides keep priority over roofs, and all eight original shell cells
  remain part of completion.
- Candidate rays are checked after sensitivity rounding. Candidate yaw is first
  unwrapped around the last reported server yaw, including the ±180-degree seam.
  The issued turn also starts at that reported angle, so camera yaw or a stale
  module-local angle cannot introduce extra full rotations when ownership changes.
- Bed Defender quantizes once per update and does not add FPS-dependent jitter.
  Its existing Speed value retains the same vector turn budget. The shared
  rotation engine and other modules' rotation behavior are unchanged.
- A valid submitted aim can place immediately in `AFTER_MOTION`. It no longer
  always waits until the next input update. This removes one input-pass wait from
  first placement (normally about 50 ms at 20 client updates per second), while
  preserving the previous-aim PRE path when it is already valid. No additional
  look packet is fabricated. The existing successful walking receipt, exact
  rotation owner, slot owner and strict support/face ray remain required.
- At most one valid attempt occurs per input update, including rejected clicks.
  Place Delay still measures time from that attempt; the default remains 80 ms.
- Effective reach is the smaller of Range and the current game controller's block
  reach. The planner and the final placement check use that same effective range.
- Bed-support clicks use the existing scoped sneak helper. When native sneaking
  is absent, START_SNEAKING precedes the click and STOP_SNEAKING follows it, with
  local input restored. This avoids activating the bed instead of placing wool,
  including while unfocused. A competing sneak owner prevents that bed click.

No module, dependency or setting was added. Existing defaults, visible hotbar
restoration, movement correction, GUI exclusions and completion cleanup remain.
Donor source and architecture were not modified or imported.

## Local verification and limits

Use `scripts/launch-smoke --scenario beddefender` for the focused packaged check.
It retains the existing building ownership/menu test and adds complete shells for
four bed orientations, three mouse sensitivities and two FPS values. It checks
first-walking-pass placement, look-before-click ordering, identical planning with
an additional camera revolution, bed-support sneak ordering, blocked first-cell
progress, configured delay, cancelled walking and completion cleanup.

Side walls can obstruct the bed top from ground level. The complete-shell fixture
orbits the sides, then stands on a placed side block for physically reachable
roof rays. It does not authorize placement through the side walls.

Exact artifact, test counts and the manual launch are recorded in
[`verification/beddefender-rotations-20261104.json`](verification/beddefender-rotations-20261104.json).
All 164 core and 447 Forge unit tests passed, as did architecture, wrapper/runtime
checksums and packaged-JAR verification. The packaged run passed 12 existing
building checks and 197 focused rotation/placement checks. All 24 complete-shell
cases placed their first block on their first submitted walking pass; config,
HUD and zero-subscription/zero-queue shutdown checks also passed.
These are local offline checks. No authenticated Hypixel/Grim acceptance, real
server completion speed, or anti-cheat bypass guarantee is established.

Pre-edit copies: `.backups/beddefender-rotations-20261104-061859/`.
Check for subsequent edits before restoring individual files.

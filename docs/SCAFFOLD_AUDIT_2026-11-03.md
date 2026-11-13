# Scaffold / Telly / diagonal movement audit — 2026-11-03

Inspected Perihelion's Scaffold, support search, ray validation, movement
correction, jump mixin and player ground-history hooks before comparing the
read-only Yuri reference `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
The audit covers Telly, its Hypixel Telly option, ordinary diagonal bridging,
Expand geometry and automatic versus held-jump movement.

## Confirmed issues and corrections

1. **Telly's placement latch did not reset between jumps.** The old reset required
   both preceding ground and air counters to be zero. After the first player
   update, one counter is always positive. Once startup or a threshold opened the
   gate, it stayed open across landings. `TellyTiming` now closes the latch on the
   current grounded update and retains the preceding air count for the airborne
   threshold. The 400 ms startup allowance is preserved. Normal Telly and Hypixel
   Telly now start a new placement window for each jump.
2. **Changing direction could miss normal Telly's placement tick.** An exact
   equality check could be missed when moving from the six-tick straight setting
   to the four-tick diagonal setting after tick four. The normal gate now opens
   once its current threshold has elapsed, then latches until landing. Held-jump
   thresholds and zero-delay settings follow the same rule. Hypixel's early-air
   four/five-tick window is retained.
3. **Diagonal detection described keys instead of world movement.** Any strafe
   key forced the diagonal branch. A 45-degree camera heading plus W+D travels
   along a cardinal axis, while a side-only input at a cardinal camera heading
   also travels along a cardinal axis. The detector now combines camera yaw with
   the uncorrected forward/strafe key vector. Opposite keys cancel. Silent move
   correction is not applied twice. The existing 20–70 degree diagonal tolerance
   and existing rotation-speed constants are preserved.
4. **Expand truncated diagonal offsets toward zero.** Casting the projected
   displacement to integer dropped fractional movement before accounting for
   the player's position inside the block. It could produce a zero offset at a
   diagonal crossing or asymmetric placement across directions. Expansion now
   projects the actual position into block cells, then subtracts the current
   cell. Cardinal-axis floating-point noise is suppressed. Expand remains off by
   default.

These correct donor-derived edge cases without introducing modules, settings,
dependencies or a replacement movement system. Donor source remains untouched.

## Jumping and configuration

The native jump impulse and eight-direction movement correction remain in place.
Automatic and held jumps are compared through the actual transformed
`onLivingUpdate` path across forward, backward, side and combined inputs, camera
headings including ±179 degrees, and silent-yaw offsets through 180 degrees.
Additional comparisons check ordinary diagonal jumps against Scaffold-disabled
vanilla physics. The test world disables creative flight so repeated manual
jump presses cannot toggle flying during the sweep.

The bundled `hypxile` profile still explicitly selects `scaffold.sprint=none`.
That independently disables sprint speed and sprint-jump acceleration. This
audit preserves that profile choice and all existing setting defaults.

## Verification

Run `scripts/launch-smoke --scenario scaffold` for the focused packaged checks.
They exercise jump physics and two-block corner placements in all four diagonal
directions, using Normal, Telly and Hypixel Telly with Normal and Strict rays.
Unit coverage includes repeated ground/air cycles, direction changes past a
threshold, Hypixel early windows, zero thresholds, real movement headings,
fractional/negative coordinates and cardinal-axis boundaries.

The final artifact, exact test totals and manual-client launch are recorded in
[`verification/scaffold-diagonal-20261103.json`](verification/scaffold-diagonal-20261103.json).
All 164 core and 447 Forge unit tests passed, together with architecture,
wrapper/runtime checksums and packaged-JAR verification. The packaged Java 8
Scaffold run passed 480 movement cases and 24 corner cases (5,041 focused
assertions), plus config/HUD checks and clean listener/queue teardown.
These are local offline checks; authenticated Hypixel/Grim behavior and sustained
public-server bridging are not established.

Pre-edit source copies are in
`.backups/scaffold-diagonal-20261103-213923/`. Restore individual files only after
checking for later edits; the backup is not an automatic rollback script.

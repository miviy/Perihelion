# Compatibility, terrain, performance and quality of life

Implemented the requested priorities 1–3 and Auto F5 / Name Protect from priority 4.
Perihelion lifecycle, events, controls, packet hooks, fonts and existing modules
were inspected before the read-only Yuri reference. Yuri remains clean at
v1.3.0, `6f5603e0c0eed253741bacdcd4135830c916602e`.

## Packet and rotation behavior

An activation-owned outbound barrier keeps walking, slot changes, attacks,
placements, digging/block release, animation and sneak/sprint actions in their
original sequence while a buffer holds the first dependent packet. A second
buffer cannot steal that sequence or release it by retiring. Replay still uses
the native bounded snapshots and client-thread, connection-bound cleanup.
Changing a delay cannot move a newer interaction ahead of an older look.

Deferred walking permits the matching action to join its buffer. Hard-canceled
walking does not authorize an action and restores the last-reported angle cache
so the same look is retried. This also covers a canceled camera look before a
module acquires rotation ownership. Overflow drops the backlog and requests a
fresh look; world retirement drops the old queue and its ordering ownership.
The native client-packet guard also rejects relative movement and teleport work
for a retired handler after vanilla's thread handoff. This extends the existing
chunk guard and fixes the unload race reproduced by the world-only run. The
final unload fixture explicitly delivers both packet types to the old handler.

The native TCP fixture uses real Minecraft PLAY codecs on localhost. It checks
decoded peer packet order with Fake Lag / Blink in both activation orders,
hard-canceled and deferred walking, delay changes, and Back Track with both
outbound buffers active. Real incoming relative movement is accumulated without
being applied early; retargeting flushes into the native game handler, and local
velocity retires the tracked snapshot on the client thread.

## Scaffold terrain and search

The extended fixture covers six support shapes (full block, lower/upper slabs,
normal/upside-down stairs, fence), three modes, two slot modes and both normal
and strict rays: 72 cases. It varies straight/diagonal movement, sensitivities,
reported FPS, and yaw wrap, then checks stale support/inventory/menu changes and
24 rapid toggles. Every successful placement requires a preceding walking
packet with the identical winning yaw and pitch.

Two failures found by those checks were corrected. Secondary search validates
mouse-rounded candidate angles, preventing a ray along a stair seam from
disappearing after rounding. Fence placement brackets the click with temporary
actual sneak input and ordered start/stop sneak packets; it restores the input
in `finally`. Minecraft otherwise consumes the fence's ordinary right click.

The Hypixel search previously made 4,551 primary ray attempts when no candidate
matched. Positive pitch offsets all clamp to the previously tested 90 degrees.
Skipping only those duplicates reduces the maximum to 2,331 attempts (48.8%
fewer). Yaw order, negative pitch order, first-match choice, fallback, turn
budgets, jitter, sensitivity rounding and action timing remain intact. A separate
original-loop reference checks first-match equality on reachable geometry.

## Auto F5 and Name Protect

Auto F5 defaults to On Aura / On Scaffold enabled. It switches first person to
rear third person while Aura has a target or Scaffold is enabled, then restores
the perspective it owned. Existing third-person views are preserved. Native
perspective requests yield to FreeLook / Freecam and respect manual F5 changes.
FreeLook now uses that same shared camera channel instead of direct competing
writes.

Name Protect replaces every exact occurrence of the local name with `You` in
rendered text, matching the donor. Vanilla drawing, width, trimming and wrapping
and native smooth GUI / formatted HUD fonts use the same transformation. Real
identity, stored text and outbound chat remain unchanged. The disabled font path
does not allocate a text event.

## Verification and limits

The build, remapped package and 231 unit tests pass (156 core, 75 Forge). Both
final Forge and OptiFine M5 full runs pass against the same 1,778,496-byte JAR,
SHA-256 `aa4e0ac5002d540bb9ff367f872da9793ffaf5269d0626444e3e48becfbb2ed8`.
The [verification ledger](COMPATIBILITY_QOL_VERIFICATION_2026-10-11.json) records
run paths, hashes, complete fixture markers and raw timing samples.

| Final fixture | Result in both variants |
| --- | --- |
| Compatibility, camera and fonts | 64 checks; real TCP packet order verified |
| Scaffold terrain | 72 cases, 335 checks; 66 original-loop first-match comparisons |
| Rotation pipeline / Yuri 1.3 behavior | 272 / 90 checks |
| Stale entity packets after unload | 2 deterministic checks |
| Live play | 125 actual ticks; eight-block bridge at each FPS cap |
| Shutdown / rejoin | References cleared; subscriptions and queued tasks zero |

Neither final log contains unexpected null-pointer, missing-member, Mixin,
verification or memory errors. Intentional listener-fault probes remain in the
logs and are expected. The investigation ledger preserves the earlier stairs,
fence and unload failures instead of treating their assertion markers as clean
runtime evidence.
The live profile walks and bridges in a private integrated world with a matching
server inventory/terrain, four visible player fixtures, three chests and HUD,
ESP, Block ESP and privacy enabled. It measures actual ticks and rendered frames
at 60 and 10 FPS caps, plus dispatch time and client-thread allocation.
In the final Forge run, the impossible-face search median was 1.135 ms versus
1.453 ms before pruning; these are 40-sample microbenchmarks. The populated
60-cap live phase had a 21.7 ms frame p95 and 3.92 microseconds mean dispatch;
the 10-cap phase had a 107.2 ms frame p95 and 6.09 microseconds mean dispatch.

These are controlled client and local-server results. They do not establish
acceptance by Hypixel or another public server, nor predict FPS on other hardware.
Search timings are a microbenchmark; live frame samples include the game,
rendering, scene setup and normal scheduling costs.

Source rollback is in `.backups/before-compat-qol-20261010-214117/`. Its original
source archive and hash manifest identify replaced files; the final verification
ledger records added/changed files. The package-check file was outside that
archive; `verify-distribution.gradle.original-reconstructed` restores its prior
expected-mixin list by removing the one added FontRenderer member, as described
in `gradle-rollback-note.json`. No donor files, managers, event buses, config
systems or global client architecture were copied or modified.

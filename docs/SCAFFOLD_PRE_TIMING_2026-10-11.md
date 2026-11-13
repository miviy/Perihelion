# Scaffold PRE placement timing

The supplied test-server screenshot reports attacking/placing on POST. Source
inspection found a matching native cause: Scaffold sent C08 placement and animation
from `AFTER_MOTION`, immediately after its C03 walking packet. Yuri's read-only
`ScaffoldModule.onPreUpdate` commits earlier, before its living/walking update.
The comparison uses the existing release checkout and verified main snapshot;
neither donor source nor global client architecture was modified or imported.

This is a packet-order issue. As a primary-source comparison, Grim's
[Post check](https://github.com/GrimAnticheat/Grim/blob/2.0/common/src/main/java/ac/grim/grimac/checks/impl/misc/Post.java)
tracks interactions after a movement packet and evaluates that pending sequence
at transaction boundaries. The screenshot does not identify the exact server-side
checker; source inspection establishes Perihelion's timing mismatch independently.

## Implementation

Scaffold computes the final plan/aim after movement during `BEFORE_MOTION`. That
walking pass submits the aim without placing. On the following `BEFORE_INPUT`,
Scaffold reuses the exact preceding aim for a freshly validated placement, then
continues collecting movement and its next walking aim. Placement/slot/animation
packets therefore precede the next movement packet. Initial activation waits for
one valid walking aim; it never inserts an extra movement packet immediately
before placement.

The native prior-walking permission is scoped to one subsequent input pass, the
same player/world/generation, unchanged position and reported angles, and the
identical activation-owned rotation request/value. Higher-priority ownership,
hard-canceled walking, changed position, an unsent angle, duplicate input passes,
camera detachment, resend requests and world retirement reject it. The S08 handler
now dispatches through the existing client-thread packet hook after thread handoff;
even a correction changing only rotation invalidates prior permission until a
fresh walking look. Retired handlers cannot apply that correction.

Placement rechecks menu/dead/freecam conditions, support/target, reach, inventory,
slot ownership and the required ray. Strict mode keeps server animation without a
local arm swing. Local item prediction uses the submitted prior angle and restores
camera/slot state in `finally`. Ordinary `withRotation`/`AFTER_MOTION` consumers
retain their existing contract. Buffered movement and PRE placement share the
existing FIFO sequence and connection-bound cleanup.

## Verification

Offline `check assemble` passes all 244 unit tests and the distribution, architecture,
wrapper and Mixin checks. The JAR is 1,793,896 bytes, SHA-256
`5c86f3b86fff4309937ab6070e627fc8af5af4dc411491a9e457469a209cb995`.
The packaged Forge full scenario passes in `forge/run-smoke-forge-f0ierfkr`.
[The final evidence ledger](SCAFFOLD_PRE_VERIFICATION_2026-10-11.json) records the passing OptiFine M5 full run in
`forge/run-smoke-optifine-w3vwiccf`, against identical JAR and fixture sources.
Both final logs contain no unexpected NPE, missing-member, Mixin, verification or
memory-error signatures. Unload/rejoin, actual live bridging and final cleanup pass.

The actual TCP fixture verifies preceding submitted look → PRE placement → next
walking packet, including Fake Lag replay. It checks unsent values, competing
owners, position changes, repeated input, hard cancellation/retry, camera restoration
and a real rotation-only S08 correction/ACK. Earlier canceled/deferred walking,
combined packet buffers and Back Track checks remain enabled. Compatibility checks:
78; rotation checks: 296. The six-shape/three-mode/two-slot/two-ray terrain matrix
passes 72 cases and 263 assertions, including fence sneak and stale support,
inventory and menu changes; 66 original-loop search comparisons pass.

Scaffold tests now assert no placement after the first walking packet and placement
before the next one. Terrain fixtures move between input and walking, matching the
actual living-update order. The obsolete requirement to retain the next rotation
claim after a successful placement was removed: once the target is occupied,
Scaffold may release its next aim. Previous-aim ownership/value is tested directly
in the TCP fixture. The first diagnostic run mixed a late correction ACK and the
previous fixture's short delay into the buffer assertion. Waiting for that ACK at
the peer and explicitly setting the test delay resolved the fixture isolation
failure; production code was unchanged for the passing rerun.

These tests establish local packet order, geometry and cleanup. The user's external
test server was not connected or tested, so disappearance of its warning still
needs an in-server retest with the rebuilt JAR. Acceptance by a particular anti-cheat
or immunity to other checks is not claimed.

## Backup

Before edits, 387 source/config/script/documentation files were archived and all
member hashes verified. `.backups/scaffold-pre-audit.json` identifies the archive,
SHA-256 and final changed-file manifest. The source rollback helper defaults to a
checked dry run, refuses later edits and backs up affected current files before
optional restoration. Generated artifacts and test worlds are outside rollback.

The intermediate OptiFine run passed all assertions but logged three unload-time
exceptions from S22 multi-block and S2F slot packets delivered to a retired handler.
Those two paths now use the existing after-thread-handoff identity guard. The unload
probe directly delivers a valid multi-block packet, slot packet and position/rotation
correction to the old handler, in addition to the earlier two entity cases. The new
`STALE_WORLD_PACKETS checks=3 PASS` marker verifies these cases. The intermediate
run remains in the investigation ledger instead of being called a clean runtime pass.

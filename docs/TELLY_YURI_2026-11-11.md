# Telly placement restored to Yuri PRE timing — 2026-11-11

The user reported that Telly placed a few blocks and then stopped extending the
bridge after the November 10 timing change. Their saved setup selects Telly,
Hypixel Telly, randomized rotations 7–10, rotation speed 10, Keep Y, Sprint None
and Normal rays. The correction uses read-only Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def` as its behavior reference.

## Reproduced regression

The new continuous test advances the actual player `onUpdate` for 160 updates
over a finite starting platform, with native movement and gravity. It holds
forward without teleporting the player between updates, and waits past the
400 ms Telly startup exemption. Against the preceding verified build
`3f7f9ea7f025cd28f66c3bcad109b0fa8ad809221e2a26d7aec4146a12a695cc`,
Hypixel Telly/Normal placed three blocks and fell after 38 updates, travelling
6.46 metres. The earlier short placement and packet-angle tests missed this.

Two differences caused the stall: queuing the aim added placement delay, and
requiring the previous walking angle for each moving click prevented the fresh
PRE aim from extending the next side face. Merely moving the planner earlier
while keeping the old-angle restriction still stalled. The trace preserved
both attempts.

## Yuri behavior and native implementation

Yuri's `EntityPlayerSP.onUpdate` dispatches `PreUpdateEvent` before movement.
`ScaffoldModule.onPreUpdate` searches current supports, calls `doRotations`, and
places using the current picked ray. `RotationManager.setRotations/smooth`
refreshes that ray immediately. The ordinary walking packet follows movement.
Relevant references also include `ScaffoldUtils.computeHitVec`,
`RayCastUtils.overBlock` and the Telly/sprint/jump branches.

Perihelion now searches the current world and prepares the current aim in
`BEFORE_INPUT`. The placement action uses that prepared aim, and the same aim
continues through input correction, jumping, movement and the walking packet.
The post-movement queue has been removed. Readiness is recalculated each input
update rather than inherited from the previous plan. Telly settings, rotation
speeds, Keep Y, native per-jump gates and diagonal/expand corrections remain.

`MinecraftControls.isInputRotationPrepared/withInputRotation` explicitly model
this PRE capability. It is available only inside `BEFORE_INPUT`, under the
winning live request, after an ordinary walk owned by that same activation has
established the current position/world/connection. World, player, position,
connection, resend and rotation-hook checks remain. Native hotbar scope,
current target/support/range checks and strict rays remain in `Placement`.
Changing the prepared request during a hotbar callback rejects the action.

Prepared input is distinct from a submitted-rotation receipt. The existing
`isRotationApplied`, previous-angle action APIs and other modules' placement
paths retain their existing contracts. No donor event bus, manager, config,
global rotation state or executable client was imported. Yuri remains unchanged.

## Sustained verification

The synthetic native/PLAY-peer matrix now includes six full 160-update bridges:
Normal, Telly and Hypixel Telly with Normal and Strict rays. Telly/Hypixel Telly
completed about 30 metres with 28 blocks per run, without falling. Existing
motion/packet, sprint-jump, 18 rotation/search, 72 partial-block terrain, 480
movement and 24 corner cases also passed.

A separate integrated-server phase runs ordinary game ticks and matches the
server terrain and inventory. It tests Telly and Hypixel Telly with both ray
modes for 160 real updates each. The server accepted **28, 30, 28 and 28 blocks**,
with **30.05, 31.64, 30.15 and 30.26 metres** travelled. The fixture checks server
blocks and continued height as well as the client view. All four runs passed.
The new server phase includes 640 bridging updates after a 35-update baseline.

The build passed 613 unit tests, architecture/wrapper/Mixin checks and packaged
JAR verification. The expanded focused run passed config/HUD and cleanup checks,
with `subscriptions=0 queued=0` and `stdoutError=false`. Exact artifacts, hashes,
logs and the persistent client launch are in
[`verification/telly-yuri-20261111.json`](verification/telly-yuri-20261111.json).
This establishes local single-player/server placement. Live Grim acceptance
still requires a fresh test of the updated build.

Backup: `.backups/scaffold-yuri-20261111/manifest.json`.

# Scaffold movement/packet rotation correction — 2026-11-10

Telly placement in this revision is superseded by the
[Yuri PRE timing correction](TELLY_YURI_2026-11-11.md), which adds sustained
bridging and server-accepted block checks after the user reported a regression.

The user reported Scaffold flagging Grim. Read the current native Scaffold,
input/jump/strafe hooks and walking receipts, plus the earlier diagonal audit,
before comparing the read-only Yuri and OpenB4 Scaffold implementations.

## Evidence and defect

The saved manual-client log from `run-manual-breaker-visual-z9j8wzfm` contains
65 Grim Simulation messages, plus GroundSpoof, AimDuplicateLook, AimModulo360,
AirLiquidPlace and TransactionOrder messages. That saved profile selects Telly,
Hypixel Telly, Keep Y, randomized rotations and Strict ray casting. It also has
other gameplay modules configured; the log is not proof that every flag was
caused by Scaffold. The user confirmed Grim, but has not confirmed that this
saved profile is their current setup.

The native Scaffold had a reproducible internal mismatch: it corrected input,
accelerated and jumped using the aim claimed during BEFORE_INPUT, then replaced
that aim in BEFORE_MOTION after physics. The walking packet therefore described
the same movement with a different yaw. The earlier auto/manual jump tests
compared those two input paths to each other, without comparing their movement
angle to the walking packet.

A new native living-update / serialized PLAY-peer regression failed against the
previous verified artifact `000d2d2eec5f290c0bcac64cd0f7ac9e6b14f5f1993649e1b337dcf1c5a65001`:

```text
NORMAL fix=true manual=false heading=0.0 update=0
strafe yaw=0.0 sent yaw=-163.34999
```

This is a confirmed consistency bug and a plausible contributor to Grim's
prediction flags. It is not a claim to have reproduced Grim's server checks.

## Fix

Scaffold retains the input aim through that update's strafe, sprint-jump and
walking packet. The post-movement search still uses the final moved position,
but queues its smoothed aim for the following input update. PRE placement still
requires the exact previously submitted angle, unchanged player position,
current connection, winning slot and valid current block geometry. Preparing a
new angle does not authorize a placement before that angle's walking receipt.

When the final search finds no placement, Scaffold keeps the angle already used
by this update's movement for its walking packet. It no longer drops that angle
at the last moment. Initial aim is unwrapped relative to the reported yaw to
avoid introducing a gratuitous full-turn normalization on activation.

Queued Hypixel search targets also receive a robustness check after mouse-step
rounding. A ray grazing an edge uses a validated point farther inside the actual
selected block bounds when available, including partial blocks. This avoids
continually aiming at a seam that disappears during the next movement step.

Move Fix now controls input-direction correction. Even when it is disabled,
Scaffold's native movement and jump use the yaw it sends, so disabling correction
does not produce a different physics basis. The disabled setting can therefore
change travel direction as the silent aim turns; that follows the chosen input
basis rather than preserving camera-relative travel with inconsistent packets.

This is a scoped native timing correction, not a donor architecture transplant
or a retune of Hypixel constants to claim Grim compatibility. Existing modes,
settings, Telly windows, support planning, camera independence and ownership
checks remain. The donor work was read-only: Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def` and OpenB4
`3d63944cc79be890c013cc79e5ed92d3533b48a5`.

## Verification

The new test compares actual StrafeEvent and jump yaw to accepted C03 walking
yaw over repeated real `onLivingUpdate` / walking calls. It covers Normal,
Telly, Hypixel Telly, Hypixel, Breezily and God Bridge; Move Fix on/off; Sprint
None/Vanilla; held and
automatic jumps; and headings 0, 45, ±179, 540 and 720 degrees. The focused suite
also runs the existing diagonal jump/corner checks, all rotation/search choices,
partial-block terrain, interrupted preparation and packet ordering. The old
rotation test now explicitly requires a new aim's own physics/walking update
before PRE placement, instead of requiring the former inconsistent early turn.

Exact final counts, artifact hashes, regression evidence and persistent manual
launch are in [`verification/scaffold-grim-20261110.json`](verification/scaffold-grim-20261110.json).
These are local native movement/packet tests, not live Grim acceptance. Remaining
server-specific flags need a fresh live run with the updated build to attribute.

Final local verification passed **613 unit tests** (164 core + 449 Forge),
**288 motion/packet cases** (4,740 assertions, including 132 actual sprint-jump
impulses), **18 rotation/search cases**,
**72 terrain cases** (263 assertions), **480 movement cases** (5,041 assertions)
and **24 corner cases**: 882 Scaffold cases in total. Config/HUD, packaged-JAR,
wrapper/Mixin checksum, architecture and clean shutdown checks passed. The
vanilla baseline fixture uses a matched prior reported heading; the separate
walking matrix exercises ±360 activation transitions without relaxing the
original movement-comparison tolerances. The updated persistent client uses a
separate profile with the saved Telly settings and other gameplay modules off.

The expanded final run reports `stdoutError=true` from the shutdown output
stream check; its independent shutdown file confirms that references, hooks,
listeners and queues were cleared, with no failure marker. The preceding run
of the identical production artifact reported `stdoutError=false`. Both results
are preserved; this output-stream status is distinct from the passing feature
assertions and is not evidence of live Grim acceptance.

Pre-edit files are hashed in `.backups/scaffold-grim-20261110/manifest.json`.
Do not restore over later edits without comparing their contents.

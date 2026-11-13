# Yuri 1.3.1 projectile parity

Reference: Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`,
`modules/impl/combat/{BowAimbotModule,AutoProjectileModule}.java`,
`managers/impl/{BadPacketsManager,SlotManager}.java`.

Bow Aimbot retains the donor's nearest reachable visible, living, non-bot player
and keeps that target until invalid. Range defaults to 40, bounded 5–100; Through
Walls and Predict default on. The solver includes the 0.16-block launch offset,
0.1-block eye offset, draw-dependent speed, 0.99 drag and 0.05 gravity, 120-tick
integration, one-degree low-arc search, 24 bisections and three horizontal
prediction refinements. Through Walls controls the donor's direct eye-to-goal
block ray. A native view-rotation claim replaces direct camera field mutation.

Auto Projectile retains the enabled Aura target requirement, configured range,
strict seek-range ceiling and hurtTime < 3 start gate. Snowballs/eggs take priority
over rods regardless of slot order; rods are eligible through six blocks. Delays
are disabled through three blocks, then 550/450/350/250ms at the inclusive 5/6/7
block boundaries. Both projectile types use launch speed 1.5 and gravity 0.03;
throwables use 0.99 drag and rods 0.92. The source's half-degree low-arc search,
60-tick simulation, four horizontal prediction refinements and optional direct
line-of-sight check are retained.

The throw sequence holds an immutable initial solved aim. It uses the source's
1.5-degree rotation threshold, releases server sword blocking when necessary,
waits for an owned submitted aim, and swaps only while performing the throw.
Pending throws retain the initial target rather than restarting when Aura selects
another target or hurt/range changes during the wait. Selected stacks and entity
identity are still validated. Rotations and normal movement correction remain held
while throwing when the initial aim needed rotation. Scoped visible/server slot
claims restore after two updates for snowballs/eggs or four for rods. Finishing
restores Aura immediately and retains sword suppression for the finish tick.

The packet gate reproduces the source's sword-use/release flag plus the requested
BadPackets attack/inventory flags. Ordinary C07/C08 block actions do not prevent a
new throw. Only C16 OPEN_INVENTORY_ACHIEVEMENT is inventory traffic. Own unblocking
does not create another block wait; C03 clears the packet window and C09 clears
server sword-block state. Suppression cancels foreign sword use/releases throughout
the active throw and finish-tick cooldown.

Native integration differences:

- PRE actions require this activation's exact prior walking aim to be owned and
  submitted. Yuri immediately throws when already aligned and unblocked; a newly
  owned native request waits one walking boundary even in that case. Aiming and
  unblocking share that first boundary rather than adding separate delays. No
  throw is moved to a post-motion phase.
- Typed rotation, hotbar and key claims replace donor global overrides. Visible
  selection and server spoofing use native restoration and connection ownership.
  Native GUI/focus, building/freecam, death/spectator, stale-entity and changed-stack
  checks can abort an action that the donor would attempt. The finite native wait
  cap prevents indefinite ownership when prior submission cannot be established.
- All packet flags are primitive synchronized state; raw listeners never consult
  live entities/settings. Teardown clears gate history. Monotonic time replaces
  wall-clock time and activation-scoped state replaces donor lifetime globals.

`ProjectileTimingTest` covers exact range/delay, rotation and retention boundaries;
`ThrownTrajectoryTest` checks launch tick order, target-height residuals, both drags
and unreachable shots; `ProjectilePacketGateTest` covers packet windows, own release,
suppression and teardown. The packaged gameplay probe separately checks native Bow
and projectile arcs. Unit/policy checks and client probes do not establish
authenticated multiplayer or public-server bypass success.

The [third check](YURI_131_THIRD_CHECK_2026-10-18.md) additionally verifies actual
mining Auto Swap during projectile aiming. Both weapon and tool selection must
yield to the shared native busy supplier until the routine ends.

Current integration evidence and limits:
[YURI_131_THIRD_CHECK_2026-10-18.json](YURI_131_THIRD_CHECK_2026-10-18.json).

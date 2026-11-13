# Yuri 1.3.1 ghost combat behavior

Read-only reference: Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`,
`modules/impl/ghost/{AutoClicker,TriggerBot,Reach,HitboxExpand,KeepSprint,BlockHit}Module.java`
and `utils/player/RayCastUtils.java`. Adapted source has AGPL-3.0-only attribution.

All settings retain donor names, defaults, bounds, and steps. Native definitions
are in Combat because Perihelion uses a shared category for combat modules.

- Auto Clicker and Trigger Bot retain Min/Max CPS 8/14 (1–25, 0.5), Weapons Only
  off, and the Gaussian distribution with integer setting conversion, clamped
  tails, ±0.4 jitter, and rounding. Reversed/equal bounds use Max CPS directly.
  Both queue exactly one vanilla key press at a due client tick; they do not
  directly attack or claim a held Attack key. Trigger Bot requires an entity
  selection. Auto Clicker requires physical Attack input and preserves Break
  Blocks off: when enabled, a block selection is left to physical vanilla mining.
  Weapons Only permits swords and tools. Trigger's timer resets on activation;
  Auto Clicker's timer survives ordinary toggles, matching the donor.
- Reach retains random Min/Max Range 3/3 (3–6, 0.01). Entity selection changes
  its final range cutoff without changing block reach. Creative selection also
  respects an explicitly raised custom range, as in the donor.
- Hitbox Expand retains Amount 0 (0–6, 0.1). It changes the temporary selection
  collision border, leaving all real entity boxes intact. The donor's actual
  renderer condition checks the viewing entity's player/invisibility state;
  this condition is preserved rather than replacing it with a target-player test.
- Reach and Hitbox Expand request a normal 4.5-block, unexpanded right-click
  ray. A native RightClickEvent runs after LocalInteractionEvent policy and
  before vanilla reads objectMouseOver. ManualRaycast uses the last reported
  walking yaw/pitch; a newly pending rotation is never treated as submitted.
  It preserves the donor's block/entity nearest-hit logic and partial-tick eye
  geometry, while right-click selection is restored after the vanilla action
  when the world, player, and temporary selection still match. This avoids a
  persistent crosshair overwrite and preserves detached-camera cancellation.
- Keep Sprint retains Attack Motion 1 (0–1, 0.05) and Chance 100 (0–100, 1),
  applied to the native successful sprint-hit slowdown hook.
- Block Hit retains Range 3 (1–6, 0.05), Block Chance 100, Block Delay 30 ms,
  Combo Delay 30 ms (both 1–300, step 1). An in-range sword hit starts the wait;
  PRE motion checks the exact donor delay plus [0, 0.01) ms jitter and bad-packet
  gate. Physical Forward is required when the combo starts. Blocking uses a
  priority-45 scoped Use Item claim, released after its hold delay and on
  activation cleanup. Repeated hits before blocking restart the combo wait.

Mouse-over and right-click settings are event-local and activation-owned. Every
ray starts at vanilla defaults, and disabling, faulting, or retiring the world
detaches the listeners without mutating entity geometry. Native time is monotonic;
Auto Clicker's physical Attack check respects the configured key binding.

`GhostClickTimingTest` covers Gaussian tails/jitter/rounding, reversed bounds,
fractional setting conversion, and click-period boundaries.
`MouseSelectionLifecycleTest` drives the actual native Reach/Hitbox definitions
through enable/disable/world retirement/re-entry and checks event values and
subscription counts. Server reach acceptance and anti-cheat behavior remain
separate live-server evidence.

Completed integration evidence and remaining limits: [YURI_131_VERIFICATION.json](YURI_131_VERIFICATION.json).

# Rotation audit — 2026-10-10

The walking hooks already supplied the requested yaw/pitch to vanilla packets and
last-reported caches. The failures were in movement correction, placement timing,
local item prediction and action ownership. Those paths now use Perihelion's
existing controls and activation lifecycle.

## Fixed failures

| Failure | Resulting behavior |
| --- | --- |
| Movement correction rounded already-scaled inputs to integers. Sneaking/item-use values such as 0.2 or 0.3 became zero. | A continuous change of input basis preserves world direction, analog magnitude and diagonal acceleration across wrapped angles. Shared correction covers Scaffold, Aura SILENT, Aim Assist SILENT, Bed Defender, Breaker, Chest Aura and Derp. |
| A higher-priority action could release its rotation after walking; a lower module then became the winner and acted using an angle that was never submitted. | The adapter retains the exact request and immutable rotation used by that walking pass. Rotated actions require that same request/value to remain eligible and winning. Clearing/replacing the request, a new input phase, world changes and shutdown invalidate authorization. |
| Scaffold selected an angle before movement and traced it from the new position after movement. A native movement fixture reproduced a missed support face. | Input decisions stay before movement. The final support plan, search and smoothing run at BEFORE_MOTION from the final player position, before its walking packet. Expensive rotation searches and smoothing run once per walking pass. Only Breezily also needs a preliminary placement plan for its input decisions. |
| MLG replaced the picked block but the bucket performed its own ray trace from the camera angle. | Native item actions temporarily use the submitted yaw/pitch and matching held stack, then restore the camera and picked target in finally blocks. MLG water and web work when the camera looks upward. Removed items, menus, dead players and Freecam yield safely; an existing web catch is preserved. Auto Extinguish shares the corrected downward action. |
| Anti Fireball requested its aim and attacked before the walking packet. | Prepare before motion; revalidate the fireball and attack after the matching walking rotation. The existing range and 50 ms throttle remain. |
| Aura STRICT corrected normal acceleration but used camera yaw for sprint jumps. | STRICT uses its winning aim for acceleration and sprint-jump direction. SILENT preserves the user's world movement direction for both, including slow movement. NONE remains an intentional setting. |
| Silent rotation never appeared in the local third-person model. | Renderer-only reads display the submitted body/head yaw and pitch, with wrapped interpolation. Camera/entity view fields and other players are unchanged. Release, menus, world changes and shutdown fall through to vanilla. |

`MinecraftControls.withRotation` is synchronous, client-thread-owned, rejects
nested actions, and preserves a camera change made by the action instead of
overwriting it. Shutdown/failure restoration is covered by transformed tests.
The pair captured for walking remains atomic if callbacks update/fault its owner.
A failed vanilla walking pass is cleared at the next input phase.

## Rotation consumers reviewed

| Consumer | Audit outcome |
| --- | --- |
| Scaffold | Final-position aim and shared movement correction; actual rotation required by placement. |
| Aura | Submitted-owner guard, SILENT correction, STRICT sprint-jump fix; target, item, GUI and range revalidation retained. |
| No Fall MLG / Auto Extinguish | Submitted-owner guard and scoped native bucket/web ray trace; existing web protection retained. |
| Auto Pot | Downward packet action requires the submitted rotation and winning slot. Existing timing and potion selection remain. |
| Clutch / Block In / Bed Defender | Inherit the corrected Placement transaction: submitted rotation, current support/reach, winning slot and local block orientation. |
| Breaker / Chest Aura | Destruction/opening requires the submitted rotation; shared movement correction preserves scaled inputs. |
| Auto Projectile | Submitted-owner guard and scoped native item use with its owned hotbar claim. |
| Long Jump fireball sequence | Its use packet requires the submitted downward rotation and winning slot. |
| Anti Fireball | Look-before-attack ordering, current entity and range checks. |
| Minigame Aim | Submitted-owner guard; native right-click calculations use the matching angle. |
| Aim Assist | VIEW updates the camera; SILENT updates walking packets and uses corrected movement. Manual clicks keep vanilla picking behavior. |
| Bow Aimbot | Existing VIEW Lock versus silent aim, target/arc checks, smoothing and manual release remain. This audit does not synthesize bow releases. |
| Derp | Shared movement correction and submitted third-person pose; no automatic item action. |
| Freecam | Highest-priority original-body rotation retained; other automated actions yield. A separate view entity cannot authorize an ordinary walking action. |
| Trajectories | Reads the winning aim for its existing bounded preview; no automatic action or new per-frame search. |

SERVER rotation intentionally keeps the first-person camera still. Use a module's
VIEW/Lock option for visible camera aiming. The rendered local model now shows
silent aim in third person. No rotation defaults, key bindings, ranges, CPS or
configuration schema were changed by this audit.

## Evidence and limits

- Gradle `check assemble --offline`: **219 tests** (155 core, 64 Forge), zero
  failures/errors/skips, plus architecture and packaged-distribution validation.
- Two movement tests independently compare **22,500** scaled/diagonal/yaw-wrap
  combinations against world acceleration, plus the small-input zeroing case.
- **51 shared-control checks** on transformed Minecraft cover real walking
  caches, view preservation, request rewrites, competing owners, unfinished-pass
  cleanup, nested actions, exceptions, external camera writes, world invalidation
  and shutdown inside combined item/rotation actions.
- **100 focused native rotation checks** capture actual C03/C08/C02 submissions
  on the disposable integrated connection. They cover MLG water/web prediction
  while looking upward, picked-target restoration, existing web protection,
  real player-renderer pitch, moving Scaffold across 3 rotation modes × 3 search
  modes × 2 ray modes, Aura ray casting and SILENT/STRICT acceleration/jump paths,
  competing-owner release, and Anti Fireball look-before-attack ordering.
- Existing deep gameplay covers all four Scaffold bridging modes, both slot
  modes and all three ray settings; gameplay, quality, HUD, config, GUI, network,
  module-fault cleanup, unload/rejoin and shutdown checks also pass.

Both Forge and OptiFine M5 run the same final JAR. Exact hashes, run paths and
counts are in [verification](VERIFICATION.md) and
[machine-readable evidence](verification/rotations-20261010.json).

These are native client, renderer, packet-submission and local prediction checks.
They do not establish server acceptance, damage prevention on an authenticated
server, server-specific module behavior, multiplayer latency handling or FPS.
Packet-buffering modules can intentionally delay/filter transmission; the
submitted-owner check does not acknowledge receipt or bypass those queues. Their
combinations still need a separate multiplayer exercise. Existing slow rotation
settings can legitimately take several ticks to reach a strict ray target.

## Rollback

Before edits, **342 source files** were archived in
`.backups/before-rotations-20260930-021758/source-only.tar.gz`, SHA-256
`e72f4d747eb8d8ebd133ccee1d3c134e5a9e7cc7de0d29dd4015ef915309a506`.
Per-file originals and final affected-file hashes are retained alongside it.

Run `python3 scripts/restore-rotations.py` for a verification-only dry run. Add
`--apply` to restore this batch after preserving current affected files in another
checkpoint. The helper refuses if later edits would be overwritten. Isolated
dry-run, later-edit refusal and actual restoration results are in that backup's
`rollback-verification.json`. Rebuild after restoring; generated JARs, caches and
worlds are not source rollback targets. Earlier HUD backups remain intact.

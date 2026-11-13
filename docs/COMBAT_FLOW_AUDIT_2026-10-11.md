# Combat behavior and client flow audit

## Findings and changes

Legit Velocity is a combination of a jump attempt and extra Aura attacks. Yuri's
current implementation attempts a jump only when grounded, sprinting, outside
liquid/web and without Jump Boost. Its attack reduction requires enabled Aura and
a target within three blocks. The original native port's `Only Sprinting` setting
governed attacks, while jumping always required sprinting. The label did not make
that distinction clear.

- Added `jump-reset` and `jump-only-sprinting` to Velocity, enabled by default to
  retain the earlier Legit intent. The separate Jump Reset module takes precedence.
- Kept existing saved IDs, enum values and Cancel/Custom defaults. Renamed the
  displayed `reduce`, `only-sprinting`, `reduce-when-can-attack` and `attack-times`
  controls to describe Aura reduction, its sprint gate, delay and input opportunities.
  Those dependent settings hide when reduction is off.
- Pending jumps, reduction attempts, slowdown history and claims clear on setting
  transitions. `Ignore On Fire` now applies consistently to Legit jumps and Intave
  attacks; the native web guard is included.
- Reduction requires live enabled Aura, an eligible current target, item-use and
  menu guards, reach, field of view and optional attack delay. With rotations enabled,
  PRE reduction uses the precise prior submitted rotation and verifies its ray still
  hits the target. It yields to competing rotations, rejects unsent/moved aim, and
  restores the next desired rotation after the action. Aura's explicitly selected
  no-rotation behavior remains available.
- Added standalone `jumpreset` under Combat: one normal jump-input pulse on positive
  local horizontal/upward S12 knockback, without Aura attacks or packet cancellation.
  It requires grounding at reception and consumption; there is no deferred landing
  jump. A candidate expires after 150 ms and is consumed on the next input opportunity.
  Physical jump state is preserved. Activation cleanup retires input and observers.
- Jump Reset skips menus, item use, sneaking, death, riding, flying, liquids, webs,
  ladders, Jump Boost and conflicting Scaffold, Clutch, Speed, Flight, Long Jump,
  Stasis or Freecam. Cancel/Custom Velocity suppress it. Legit's embedded jump shares
  these eligibility guards rather than competing with those modules.
- ClickGUI search includes stable IDs, settings, descriptions and enum modes; multiple
  terms can be combined, including `velocity legit` or `jumpreset nearby player`.
  Ctrl/Cmd+F enables and focuses search even when its toolbar was hidden.
  Escape first clears the filter, and an empty result displays a recovery hint.
  Conditional or filtered-out inline editors retire before rendering or committing.
  Existing panel layout, row sizing, themes and config lifecycle are retained.

## Usage

For a pure jump reset, enable **Combat → Jump Reset** and leave Velocity disabled.
Defaults: Chance 100%, Only Sprinting off, Nearby Player on (4.5 blocks), Ignore On
Fire on. It is disabled by default, like other gameplay modules. Turn Nearby Player
off for a local knockback fixture without another player.

For the combined donor-style behavior, select Velocity's Legit mode. Turn Attack
Reduction off to remove the extra Aura attacks, or disable its Jump Reset toggle
when using a separate movement routine. Only Sprinting on the standalone module
and Jump Only Sprinting in Velocity govern their respective jump attempts.

## Source references and scope

- [Yuri Velocity](https://github.com/unleg1t/Yuri/blob/ae94d7b70ce8735b0337d01afbbe567d5a1de714/src/main/java/ddlc/yuri/modules/impl/combat/VelocityModule.java),
  [Legit](https://github.com/unleg1t/Yuri/blob/ae94d7b70ce8735b0337d01afbbe567d5a1de714/src/main/java/ddlc/yuri/modules/impl/combat/velocity/impl/LegitVelocity.java),
  [Intave](https://github.com/unleg1t/Yuri/blob/ae94d7b70ce8735b0337d01afbbe567d5a1de714/src/main/java/ddlc/yuri/modules/impl/combat/velocity/impl/IntaveVelocity.java).
  Exact reference files and SHA-256 manifest are outside source sets in
  `.references/velocity-yuri-ae94d7b70ce8735b0337d01afbbe567d5a1de714`.
- [Local Arsenic JumpReset reference](https://github.com/ArsenicClient/Arsenic/blob/d6f3a3d40a6d3f14826a42002abf343c61c46ecb/src/main/java/arsenic/module/impl/ghost/JumpReset.java).
  The current upstream Arsenic implementation differs: it detects hurt/swings and
  changes vertical motion before vanilla applies velocity. That technique was not
  ported; this module uses Perihelion's normal input path.
- Read-only OpenMyau-Plus `module/modules/Velocity.java` and Project-Coolware
  `modules/impl/combat/Velocity.java` were also inspected for input/conflict behavior.

This pass covers combat knockback, Aura reduction, their input/lifecycle conflicts
and ClickGUI discovery/editing. It does not claim identical implementation of all
donor modules. Remote anti-cheat acceptance and knockback reduction effectiveness
on a particular server require a live server test. Normal local input and packet
ordering checks do not establish a bypass. S27 explosion handling is unchanged.

## Verification and rollback

Current check details, distribution fingerprint, smoke results and any investigation
failures are recorded in `COMBAT_FLOW_VERIFICATION_2026-10-11.json`.

Before edits, 423 source/config/documentation files were archived and each archived
member was checked against a SHA-256 manifest:
`.backups/before-combat-flow-20261012-030602/source.tar.gz`, archive SHA-256
`f267ab4641166170336b2d69b1467efa0afd97489f15e24dbe9f24daa4ce384b`.
`python3 scripts/restore-combat-flow.py` verifies the original snapshot and affected
files without modifying anything. Its explicit `--apply` path refuses later edits
and backs up the current affected files before restoration. Generated JARs and
worlds are preserved; restored source must be rebuilt.

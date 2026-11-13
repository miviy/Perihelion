# Bug and performance review — October 13, 2026

Reviewed the current Perihelion checkout, its module registry, gameplay families,
shared controls, activation cleanup, event dispatch, inventory policies and packet
buffers. Compared against the unchanged local Yuri v1.3.0 checkout
`6f5603e0c0eed253741bacdcd4135830c916602e` and the already-cached module snapshot
at `50d7322b85cb8de096241077ca3130e9e9715d20`. All 88 cached module hashes match
the existing source ledger. This is a pinned comparison, not a claim about the
latest upstream branch. [Module comparison](YURI_MODULE_COMPARISON_2026-10-13.md).

## Fixed bugs

| Priority | Trigger and failure | Correction and regression evidence |
| --- | --- | --- |
| P2 | Change Long Jump from Fireball to Mospixel while enabled. Its persistent rotation/hotbar claims survive, holding pitch at 90 and the fireball slot. Changing back also retains the old tick counter and can miss its tick-two throw. | Activation-owned setting observers clear claims/counters and finish the old ordered packet sequence on the same live connection. Native checks cover immediate claim release, visible-slot restoration and another throw after switching back. The pre-fix package failed the new claim-release assertion. |
| P2 | Disable an item category in NCP No Slow while using that item. Local slowdown honors the toggle but C07 RELEASE_USE_ITEM still interrupts server use. | Local slowdown and NCP packets now share the same item-category predicate. Native checks cover disabled/enabled food, swords, bows and potions. This repairs an inherited Yuri behavior; enabled categories retain their PRE packet behavior. |
| P2 | Anti Fireball encounters an entity still in the loaded list after death/removal. It can claim high-priority aim for that invalid entity although the action refuses it. | Preparation now rejects dead/replaced world identities. Native regression verifies a dead loaded fireball does not take rotation; the existing live-fireball PRE attack/order check still passes. |
| P2 | Breaker or Bed Defender searches for a bed more than two blocks above/below the player, within configured range. The shared scanner only visits vertical offsets -2 to +2. | Search now covers the configured radius in all three axes, prunes candidates outside the current distance bound before querying blocks, and avoids unloaded chunks. Native checks find a raised bed at range 6 and reject it at range 3. Yuri scans vertical range too. |

## Performance changes

- LootPolicy counts blocks, eggs/snowballs and arrows once per inventory snapshot,
  rather than scanning up to 40 slots again for every resource candidate. A chest
  full of 54 resource stacks changes from up to 2,160 counting visits to 40 per
  selection pass. The policy is still rebuilt after an inventory transaction;
  it does not reuse stale counts across mutations. Threshold, combined projectile,
  zero-target, refreshed-count, armor-upgrade and partial-stack checks pass.
- InventoryAnalysis caches its private enum iteration array. It no longer clones
  `Role.values()` for each nonempty stack. LootPolicy caches its equipment-role
  array, and InventoryPolicy's item allowlist uses direct identity comparisons
  instead of allocating an array plus an Arrays.asList wrapper per query. Item
  identities remain resolved at call time, preserving Minecraft bootstrap behavior.
- Anti Fireball retires attack history using each stored entity's world ID and
  identity. It no longer traverses the entire loaded entity list separately for
  every previously attacked UUID. The selection scan remains linear in loaded
  entities; history pruning is linear in retained attack history.

These are reductions in repeated work and allocations. No controlled before/after
FPS benchmark was performed, so an FPS increase or percent speedup is not claimed.
Existing native performance fixtures still run as part of the full suite.

## Remaining code-review finding

**Resolved in the subsequent [Breaker whitelist implementation](BREAKER_WHITELIST_2026-10-13.md).**
The following describes the pre-follow-up finding; the original build/run evidence
below remains a historical baseline.

**P2 — Breaker whitelist is tied to enable position.**
`BuildingModules.Breaker.onEnable` captures the nearest bed within 16 blocks into
an activation-local `own` variable. Enabling it next to an enemy bed excludes that
bed; enabling it before the home bed is available leaves `own` null for the entire
activation. Yuri's BreakerWhitelistManager acquires a bed independently of module
enable and resets acquisition on the game's “Protect your bed” announcement.
Perihelion needs a native match-scoped whitelist acquisition/reset policy for that
behavior. The current implementation is not equivalent own-bed protection.
This pass records the gap instead of silently adding a server-message heuristic
or copying the donor's global manager. See the corresponding comparison row.

The exposed modes in many modules match Yuri, but this does not establish parity
for every setting, helper algorithm or combination. Notable option/presentation
omissions and retained historical features are listed separately in the comparison.

## Verification

The baseline build passed. A clean offline rebuild reran all tasks and passed
**281 unit tests**: 156 core and 125 Forge, zero failures/errors/skips, architecture
checks, wrapper/Mixin checks and distribution validation. The final incremental
build covers the subsequent bed-scan correction. Packaged Forge and OptiFine M5
full-suite outcomes, source hashes, artifact hash, exact run paths and failed
attempts are recorded in [the evidence ledger](BUG_PERFORMANCE_VERIFICATION_2026-10-13.json).

New smoke regressions are in ModuleActionProbe, QualityGameplayProbe and
RotationPipelineProbe. Existing packet, rotation, Scaffold terrain, combat flow,
HUD/GUI, unload/rejoin and shutdown checks also run. The package is tested in an
isolated offline world with controlled network fixtures. Authenticated multiplayer,
public-server acceptance and server-specific bypass effectiveness remain untested.

The first sandboxed launch aborted before game initialization. A pre-fix run then
reproduced the Long Jump claim bug. One intermediate run failed a fixture assertion
that checked a C03 subclass by its exact simple name; the fixture now checks
assignability. These failures are preserved in the ledger. Legacy audio and
expected deliberate fault-injection messages are recorded separately from unexpected
errors. A successful later suite does not erase the initial native startup failure.

## Recovery and scope

Only Perihelion files were changed. Donor repositories remain read-only; Yuri's
HEAD and clean status were checked. No public server was contacted.
The entire project is currently untracked in its Git repository, so Git diff is
not a reliable change/recovery baseline for this task.

Nine affected baseline files were archived and every member SHA-256 verified in
`.backups/before-review-20261013-104755/source.tar.gz` with `manifest.json`.
BuildingModules has a separately verified `building-source.tar.gz` and
`building-manifest.json` in the same directory, captured before its edit.
The final change manifest records additions and before/after hashes; preserve
subsequent edits before restoring any saved file. No automatic rollback or commit
was performed.

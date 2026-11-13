# Gameplay verification and bug audit — 2026-09-28

The audit inspected the native implementation first, then compared the relevant Yuri
sources and exercised the packaged client in disposable local worlds. The user approved
small fixes and subsequently authorized larger changes that could be implemented safely.
No donor repository was changed. There remain intentional native adaptations; this report
is not a claim that every mode is identical to Yuri or effective on every server.

## Fixes

| Area | Failure or avoidable cost | Result |
| --- | --- | --- |
| Aura item use | A removed held stack during item use caused a null-pointer exception. | Null-safe eligibility checks pause combat and release the target/rotation/block state. |
| Aura action phase | A target could die or be removed, a screen could open, or the weapon requirement could become false between preparation and attack. | Recheck the current world entity identity, health, targeting/input restrictions and FOV before acting. |
| Aura predictive block | Swing sampling was skipped on attack-ready ticks because it was inside a short-circuited condition. | Sample every eligible predictive update; bounded history, target/mode resets, and constant-time projection. Legit/Predictive attacks also require the appropriate released-use state. |
| Aura switching | The first tick skipped the first candidate; list changes could shift the selected index. | Start at the first eligible candidate and retain entity identity until the configured tick delay expires. |
| Aura NCP block | The post-motion block path ignored Only Block If Hurt. | Honor the configured hurt threshold in that path too. This intentionally fixes an inherited Yuri inconsistency. |
| Scaffold Expand | Planning aimed ahead, but the placement gate still checked only the block directly below the player. | Use Yuri's first-gap expansion offset for both planning and the empty-space checks, allowing placement ahead while standing on support. |
| Scaffold Keep Y | A stationary midair tick overwrote the remembered bridge plane. | Preserve the plane through that pause; apply the donor's three-block eligibility limit and deeper empty-space checks. |
| Scaffold Strict | Placement always submitted the planned face center. | Revalidate the winning rotation and submit the actual ray intersection in Strict mode. |
| Shared placement | Stale plans could act out of reach, after a screen opened, or after inventory/support changes. Container activation could count as placement. | Revalidate range, inputs, block item, placement eligibility and support. Respect the immediate local interaction gate, and count success only when the target becomes occupied. This also protects Block In and Bed Defender. |
| Ultra Safe search | Every search repeated trigonometry and allocated vectors/rotations for many impossible candidates. | Cache axis calculations per search and add an allocation-free broad phase; preserve candidate order, exact game intersection tests and fallback ordering. |
| Back Track | Each buffered relative move was added to the original position, losing earlier deltas. | Accumulate against the pending position; teleports replace that position. Retain it across ticks for the same target and retire it on reset. State belongs to the tracking snapshot, so retired work cannot overwrite another target's state. |
| Block ESP | Maximum radius scanned 226,981 block positions in one callback. | At most 4,096 cursor visits per tick, loaded blocks only, with incremental discovery and refresh. The update-rate setting is the pause after a completed sweep. |
| Block ESP / Bed Plates | Starting far below the player delayed discovery at their current height. | Scan the player's level first, then alternate above/below it without skipping cells. |
| Friends / AntiBot | Candidate checks repeatedly parsed the entire friends list and compiled the same name regex. | Cache normalized names by setting value, reuse the compiled pattern, and skip FOV vector work when that check is disabled. Existing membership semantics are retained. |

All changes remain on the native lifecycle, settings, controls and event APIs. No modules
were added to or removed from the production registry: **77 gameplay modules plus ClickGUI**.

## Evidence

The pre-fix packaged run `forge/run-smoke-forge-p1cqr_3x/launch.log` reproduced the four
stale Aura action cases, the empty-held-item exception, and out-of-reach placement. An
initial fixture run also exposed a reflected-method signature error, which was corrected.
A later switching fixture had a duplicate synthetic entity caused by immediate remove/re-add;
the fixture now removes that duplicate before re-adding. Neither fixture error was treated
as a production bug.

`JAVA_HOME="$PWD/.tools/java17/Contents/Home" GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew check --offline`
passes **194 tests**: 147 core and 47 Forge. Added coverage includes 20,000 deterministic
ray-box comparisons against the game's exact intersection routine, parallel/boundary rays,
predictive swing history and resets, friend setting invalidation, and complete centered scan
traversal. Architecture, wrapper, Java 8 bytecode, resources and remapped JAR/Mixin checks pass.

The new packaged-game probe has **125 assertions**, in addition to the existing **139 gameplay**,
**356 module lifecycle**, **46 network**, **55 render**, **30 shared-control** and **52 GUI** checks.
Its cases include:

- Aura invalidation between phases, all six block modes' state/release paths, NCP hurt gating,
  predictive samples on attack-ready ticks, and switching through entity-list changes.
- Actual placements for **24 combinations**: Normal/Telly/Breezily/God Bridge × Client/Server
  slot × None/Normal/Strict ray, plus Expand and Keep Y regressions.
- Three rotation searches on reachable support, and eight comparisons of optimized Ultra Safe
  against a separate exhaustive reference (full block/slab, primary/fallback face, opposite
  pitch/yaw extremes).
- Placement claim loss, missing support, screen/interaction cancellation, substituted item,
  container activation, out-of-reach plan and already-filled target.
- Back Track's production packet-selection/position function with real movement/teleport
  packet objects, signed cumulative moves, intervening client tick, unrelated entity, actual
  client attack callback, disable and re-enable. These calls do not establish remote-server
  queue timing.
- Maximum-range Block ESP first-tick work, alongside the existing Bed Plates render/lifecycle
  checks. The scan-order unit test covers the complete volume; every possible bed layout is
  not exercised in-game.

The final artifact is `forge/build/libs/Perihelion-0.1.0.jar`, **1,666,169 bytes**, SHA-256
`3da12fd1038bc55e133999066379c406e2abc1f0b370fa3b1694b4c756a00b9b`.
Final launch paths and performance samples are recorded in
[the machine-readable evidence](verification/deep-audit-20260928.json).

The complete final runs are `forge/run-smoke-forge-spechkx5/launch.log` and
`forge/run-smoke-optifine-8gz3cocq/launch.log`. Both pass all the assertions above,
configuration round trips, world unload/rejoin and shutdown with cleared references,
zero subscriptions and zero queued tasks.

Two preceding OptiFine runs on the same artifact (`run-smoke-optifine-zzg2bier` and
`run-smoke-optifine-futm1bpu`) passed gameplay/rejoin and exited with code zero, but
omitted all fixture shutdown markers. Their recorded results remain failed. The test-only
shutdown hook now also writes an independent `shutdown-report.txt`; the final OptiFine
run records success in both outputs and `stdoutError=false`. The launcher's pass condition
was not weakened. The earlier missing-output cause remains unconfirmed, so the final pass
does not prove that the intermittent reporting issue is resolved.

Performance samples time seven synchronous calls in a controlled local fixture, not FPS.
The pre-fix impossible-face Ultra Safe search had median **30.86 ms**, maximum **50.15 ms**.
The final Forge sample was **13.86 ms** median / **39.37 ms** maximum, and OptiFine was
**10.27 ms** / **33.54 ms**. Other post-fix samples vary with warm-up and system load;
the evidence file retains those samples too. The optimization preserves the exhaustive search and therefore is not a hard
execution-time cap. Block ESP has a fixed work-count budget, not a wall-clock deadline.

## Remaining differences and limits

- Yuri's Aura mode selector is not wired to `TargetManager.setMode` anywhere in this donor
  revision; that manager also has its own 200 ms switch timer. Perihelion keeps its functional
  Single/Switch/Adaptive selector and existing tick-based Switch Delay, now stable under list
  changes. Reproducing that donor wiring defect would disable intended behavior.
- Perihelion deliberately retains bounded support search, shared-control arbitration, owned
  cleanup and bounded connection-specific packet queues. Non-Strict Scaffold uses the planned
  face center rather than Yuri's randomized hit offsets/support ordering. Partial support
  shapes, obstructed corner cases and every combination of placement modules are not proven
  equivalent by the flat-world matrix.
- NCP Only Block If Hurt, cumulative Back Track positions and successful-placement accounting
  intentionally correct donor inconsistencies. No claim of exact packet-for-packet equality
  is made. Aura's learned aim presets remain native adaptations documented in YURI_PORT.md.
- AutoClicker keeps its existing maximum of one action per game tick; configured CPS remains
  tick-quantized. No new click-pattern or packet-timing modes were introduced in this audit.
- Movement, inventory, packet, visual and recovery sources received broader review and the
  full registry lifecycle/fault/world-transition checks. This does not constitute exhaustive
  behavioral testing of all 77 modules' settings, tower modes or arbitrary combinations.
- There was no authenticated multiplayer testing, server-specific bypass validation, prolonged
  manual play, or testing of other GPUs/shader packs. Existing intentional fault-injection
  logs and legacy Forge/Twitch/sound warnings must be distinguished from new module failures.

## Reverting this audit

The verified pre-audit snapshot contains **285 source files**:
`.backups/before-deep-audit-20260927-224355/source.tar.gz`. Earlier port/improvement backups
are retained. The audit's changed-file manifest includes only this audit's affected files.

From the repository root, `python3 scripts/restore-deep-audit.py` checks the archive and
current files without modifying them. `python3 scripts/restore-deep-audit.py --apply` first
backs up current affected files, restores their original versions, and removes this audit's
listed additions. It refuses restoration if an affected file has later edits. Rebuild after
restoring; generated JARs, worlds and caches are not restored by this source rollback.

Rollback was exercised in a disposable copy: all **14 modified files** returned to their
original hashes, all **9 added files** were removed, the pre-rollback checkpoint preserved
every affected file, and a simulated later edit correctly stopped restoration before any
changes. The actual source remained intact. The real checkout also passed the dry run.

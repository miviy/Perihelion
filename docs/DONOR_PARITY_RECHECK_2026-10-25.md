# B4/Yuri parity recheck — 2026-10-25

Rechecked **all 96 registered module IDs**, with native implementations/hooks
inspected before their relevant pinned donor bodies. This was a focused second
pass over rotations, timing, packets, input, targeting, inventory actions and
cleanup, cross-referencing the prior complete body ledger. It is not a claim that
every setting combination or multiplayer interleaving was executed.

Read-only references: Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def` and OpenB4
`3d63944cc79be890c013cc79e5ed92d3533b48a5`. Neither donor was modified or executed.
No new user setting, module, dependency, config default or profile was added.

## Corrections made in this pass

| Area | Confirmed difference and correction |
| --- | --- |
| Shared Yuri rotations | Match target-branch selection, square-root vector length, per-axis move calculation and float conversion, compound jitter conversion after the full expression, movement predicate and sensitivity-rounding/clamp order. A deterministic reference found an exact float difference at sample 9,930 (`-36.953148` versus `-36.95315`). All 20,000 finite reference samples now produce identical float results and random-consumption counts. Native finite settled/zero-speed behavior remains. |
| Scaffold/Telly and Clutch precision | Sample rotation speeds as source floats before smoothing. Restore Hypixel `6.75f/4.8f`, Telly `3.6f/3.45f/2.8f`, and NCP `1.84f` constants. Cast random Scaffold offsets after their full double expressions. Prior Telly auto-jump/sprint/ground-return corrections remain; their transformed physics regressions are rerun. Saved Sprint None remains None. |
| Aura action window | The block-since-walk latch now observes fresh current-client PLAY facts on local channels too. C08 use/place, mining and foreign release mark it; the next walking attempt resets it. Preserve the synchronous own-Hypixel-release exception and replay exclusion. The previous packaged JAR failed the actual local C08 regression. |
| Anti Fireball | Use Yuri's game-atan2/float target-angle calculation, preserving the existing matching walking-angle/attack gate. |
| Target Strafe | Match full-block support in the player's column at offsets 0–4, collision-edge strafe and single direction flip, game atan2/float lookup orbit arithmetic, and immediate retirement of inactive yaw overrides. Match the donor's sequential horizontal norm calculation rather than caching one norm. |
| Yuri Fireball LongJump | LEGIT/LAG now smooth at source speed 10 from actual reported walking angles, including a large incoming turn and handoff after competing ownership. Tick-two C08 still uses the immutable preceding walking angle; skipped tick-two uses are not retried. B4_AUTO is unchanged. |
| Librecraft NoFall | Mutate only the already-constructed C03 packet Y. Vanilla still chooses its packet type and caches the original landing position. Prepend the two source C04 positions using original packet X/Z. This removes the manufactured return-position packet on the next unchanged walk. Ground-only originals keep their wire type and dormant coordinates, matching the source quirk. Fresh main-thread/current-connection guards and replay exclusion remain. |
| NCP Speed | Match source float lookup trig and no-argument strafe's norm recalculation after X is assigned. For X=.1, Z=.2, yaw45°, source and the former cached-norm implementation differ by about 7.24% in resulting horizontal speed. This is scoped to NCP, not a global helper redesign. |
| Step / FastBreak precision | NCP Step uses source `.98F` for both predicted gravity steps. Native/Yuri FastBreak divides the percentage before multiplying `.3F`, preserving source float operation order. |
| Minigame Aim | Ray-test predicted candidate points before selecting the aim, so visibility and aim refer to the same point. Retain one native lead rather than copying Yuri's duplicated caller offset. Auto Ammo chooses the largest arrow stack, keeping the first slot on a tie and existing hotbar/cursor/container/GUI guards. |
| Trajectories | Use the interpolated render origin and fractional bow-use time. Cache keys include those inputs. For use-duration 2 and partial .9, the source speed `.311025` produces a path where the former integer-only `.21` speed hid it. |

## Verification

The [verification receipt](DONOR_PARITY_VERIFICATION_2026-10-25.json) pins the JAR,
unit results, final Forge/OptiFine runs, fixture hashes, donor revisions and rollback.
The [96-module ledger](DONOR_PARITY_RECHECK_2026-10-25.json) records every source
recheck and retained policy boundary.

**599 JUnit tests passed**: 164 core and 435 Forge, zero failures/errors/skips.
The deterministic rotation test covers 20,000 finite source-reference samples.
Both full packaged suites passed on SHA-256
`2b2470c696b86f42e2285420d5804f9bc81c105148e0c4d62ee8f2b816d0797a`
(12,754,797 bytes): Forge `run-smoke-forge-gzhj9m2q` and OptiFine M5
`run-smoke-optifine-fui6yr_6`. Each passed **518 focused audit assertions**,
**313 rotation checks**, **125 movement checks** and **263 terrain assertions
across 72 Scaffold cases** (66 reachable reference comparisons). All 59 smoke
fixture-source hashes match current files. Both cleanly retired all listeners and
queued work; no NPE, OOM, missing-member or Mixin-apply error signatures occurred.
Intentional fault-injection diagnostics and legacy platform warnings remain.

New packaged probes exercise actual transformed walking/cache behavior and decoded
PLAY TCP packets for NoFall; reported-angle handoff, tick-two packet order and
competing owners for LongJump; predicted visibility/selection, inventory swap and
trajectory render interpolation/cache/thresholds. Aura's actual local sends are
checked before switching to remote TCP fixtures. Existing full lifecycle,
rotation/input, combat, inventory, terrain and GUI suites remain enabled.

The failure ledger preserves the old Aura/NoFall reproductions and deterministic
rotation mismatch. It also distinguishes fixture setup repairs (missing helper
import, swapped-stack identity, abstract GUI instantiation), a staged agent test
compile, and the distribution allowlist update for the required packet-Y mixin.
These fixture/build failures are not reported as gameplay regressions.

## Explicit limits and retained differences

- Native lifecycle, owner priority, immutable submitted/previous-walk angles,
  hotbar/stack/ray/current-world guards and bounded replay remain authoritative.
- The donors disagree on some policies; their unsupported donor-only modes were
  not silently added. Breaker keeps actual hit faces, physical damage, home-bed
  protection and matching submitted aim rather than donor unconditional packets.
- Minigame Aim keeps native FOV/visible scheduling and a single prediction lead.
  Block In keeps coherent smoothed visible/server aim instead of Yuri's immediate
  unsmoothed camera assignment. That visual scheduling is deferred explicitly.
- No live **Grim Simulation** or authenticated Hypixel acceptance was tested. Local
  exact arithmetic and TCP serialization do not establish anti-cheat acceptance.
- A historical intermittent OptiFine bridge run placed zero blocks; its root cause
  remains unestablished even if current terrain/full runs pass.

## Recovery and manual client

Verified pre-edit source backup: `.backups/before-parity-recheck-20261024-195824/`,
904 files; archive SHA-256
`203fe738d1b9a26c656eb93a8bc42d179f0ce1832c92b2de8a1abf3adcc1e912`.
Run `python3 scripts/restore-parity-recheck.py` for guarded verification;
`--apply` first saves affected current files and restores only this pass's changes.
It refuses later edits and corrupt backups and preserves unrelated files.

The verified JAR is running without smoke probes in
`forge/run-manual-parity-recheck-8g5qg_ze`, PID 90469. Startup reports 96 modules
and successful OpenAL initialization. Native module/profile JSON was carried
forward from the previous manual game; credentials and unrelated mods were not
copied. The receipt is `.backups/parity-recheck-manual.json`. Process/window
verification is recorded separately from packaged smoke success. This manual
profile is offline; no authenticated multiplayer acceptance is implied.

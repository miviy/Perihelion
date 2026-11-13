# Yuri 1.3.1 module update

Reference: read-only Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
Previously audited Yuri reference: `50d7322b85cb8de096241077ca3130e9e9715d20`.
The refreshed checkout previously replaced local release `6f5603e0c0eed253741bacdcd4135830c916602e`.

## Scope and ownership

The current donor inventory contains 102 top-level module definitions. See
[yuri-1.3.1-modules.json](yuri-1.3.1-modules.json) for every path, blob/hash,
classification, and removed definitions. Seventy-seven donor definitions map to
native implementations. The native registry has 94 definitions,
including Perihelion features and retained historical extras. Coverage is a source
inventory, not evidence that every mode behaves identically at runtime.

The existing Perihelion menus, configuration store, HUD layout, event dispatch,
module registry, activation scopes and shared control arbitration remain native.
Source algorithms, state machines, geometry, trajectory solvers and drawing code
were reused/adapted where appropriate. No donor framework/global client is
imported. Current adapted sources carry AGPL attribution; its license is shipped
beside the retained MIT-era attribution. See [THIRD_PARTY.md](../THIRD_PARTY.md).

Twenty-two donor presentation definitions retain the earlier native-presentation
exclusion, including the new Custom Minecraft theme selector. Three earlier
features remain explicitly deferred: Insults, Skin Changer, and Keybind Info.
Those are not claimed as implemented. Auto Server's previously deferred behavior
is now implemented with activation-owned scheduling. WTap and Disabler, removed
upstream, remain optional historical native extras rather than being erased from
saved configurations. They are not advertised as current Yuri features.

## Updated behavior

| Module | Current implementation |
| --- | --- |
| Aura | Distance/Health/Angle-equivalent FOV/Hurt Time priorities, stable ID switch order and 0–10 tick switch speed, close-range Hypixel block/release scheduling and a fixed 125 ms due-tick cooldown, building-task priority, owned Displace rotation handoff. |
| Hit Select | Six current donor settings; two-tick player simulation, counter-hit prediction, damage/critical comparison, trade timeout and hurt-time interpolation. Replaces the old combo/pause implementation in the registry. |
| Velocity | Current Legit Reduce Ticks 1–3, independent visible close-range target selection, item-use deferral and sprint jump reset. Cancel/Custom/Intave remain, including corrected mode-specific Intave fire/liquid/web conditions; retired native options remain hidden for saved-config compatibility. |
| Scaffold | Current startup, Telly/Hypixel rotation rates, Keep Y boundaries, wall-avoiding support search, target face/hit selection, placed counters, jump/sprint/tower rules and partial-block guards. |
| Inv Move | Seven inventory/four chest modes, click-tick batching, pause/slow controls, 2/3-tick Blink release, arrow rotation and movement settings. Chest Blink starts after midair/potion eligibility. Ordered private snapshots stay connection/world bound. |
| Back Track | Basic New/Old, MMC and Hypixel policies, delayed FIFO/legacy movement batches, real-position following, correction/velocity flushes, safe live capture-policy transitions and position visualization. |
| Auto Projectile | Current throwable-first/rod range policy, drag/gravity predictive solver, Line Of Sight setting, held aim, swap/restoration duration, sword suppression and packet-fact windows. It still requires an Aura target, as the actual donor does. |
| Bow Aimbot | Current player target retention and nearest valid acquisition, draw-speed/hand-origin solver, horizontal motion lead, Predict setting and straight-line visibility check, through native view ownership. |
| Aim Assist | Simple/Adaptive frame equations and filters, target priorities/aim areas, horizontal/vertical controls, strafe scaling, hysteresis and elapsed-frame timing. |
| Auto Clicker | Current Gaussian CPS sampler and queued vanilla press cadence, weapons and block-breaking options. |
| Block In | Current enclosure/support path search, retry/failure state, strength priority, aim interpolation, delay and completion options, percentage fade and middle-click mode. |
| Clutch | Current shift-held catch behavior and rotation/delay settings, replacing the earlier knockback-triggered variant. |
| Auto Swap | Current attack-attribute and mining scoring, Motion PRE selection after the earlier PRE attack, selection lifetime through the next control tick, client/server/restoration choices and independent projectile suppression. |
| Anti Fireball | First-loaded candidate strictly within twelve blocks, inclusive three-block attack range, startup-only 50 ms delay, source clamped aim/smoothing, seen IDs, scalar action-window gating and one native notice per nearby period. |
| Blink / Lag Range | Third-person markers, source activation/range anchors and client-accepted positional-send estimate; Lag Range samples only on new range entry and releases at Motion PRE on movement/target/range/duration end. Initial Lag POST arm and 100 ms lease remain. |
| Refill | Splash potions only, one shift-click per greater-than-50ms interval, immediate optional close when full, with click cooldown retained across toggles. |
| Auto Pot | Source first-effect whitelist/health/nonzero active-duration selection, staged smooth aim, Traditional movement yaw, deferred potion selection and Aura handoff; native action and malformed/mixed-harmful validation remain. |
| Auto Extinguish | Five warmup/two post-use wait ticks, tick-seven right-click/tick-ten restoration, Scaffold/liquid exclusions, smooth aim and movement correction. |
| No Fall | MLG uses smooth catch/return-to-view aim, movement correction and immediate live-mode retirement; water/web rays follow the submitted aim. Other modes retain prior behavior. |
| Breaker | Bed task can take priority over Aura. Match-scoped native home-bed protection remains. |
| Criticals | Current Legit selector is present as the donor's unbound/no-action mode; existing packet modes remain. |
| BedWars Utility | Current equipment notifications, rank/short-name warnings, event timers and bounded generator movement with manual-input cancellation. |
| Auto Server | Registration, replay, policy, report and excuse choices. The donor report worker is replaced with a bounded client-thread queue, canceled on disable/world retirement. |
| Camera | No Haste Swing normalizes local visual swing duration to six times Slowdown, including the donor's fatigue normalization behavior while enabled. |
| Name Tags | Formatted/custom target name and target health; no longer labels local health or target distance as that value. |
| Skeletons | Wrapped interpolated body/head yaw. Existing native pose ownership retained. |

New standalone native definitions also include Displace, Reach, Hitbox Expand,
Trigger Bot, Block Hit, Keep Sprint, Armor Ratio, Horns, Cat Ears, Basketball and
8-Ball Pool. The minigames reuse donor simulation/drawing behavior and native font
and rendering scopes. Horns use the native accent in place of Yuri's color
manager. Cat Ears retain the spring/mesh behavior with activation-owned rig and
GL display-list cleanup; the donor OptiFine shadow-preview distinction is not
exposed by vanilla Forge's renderer.

Unchanged donor modules keep their existing native implementations. The source
inventory records changes rather than inventing updates for unchanged modules.
The current Item Delays projectile whitelist correction and the additional input
key binding support were already provided by native code.

## Native differences and limits

- Every automated rotated action validates the submitted rotation and owning
  request identity. New ownership waits for a completed walking boundary before
  a PRE action. This can add an initial tick relative to a donor action that uses
  a global/current angle, including the aligned Auto Projectile fast path.
- PRE placement/item/attack actions remain before native movement/walking;
  bypass modes do not obtain a new post-walking action phase merely to hide this
  ownership difference. Fresh world/entity/item/ray checks can reject work the
  donor would send after a stale state transition.
- Buffer capacity, handler identity, world identity, generation checks and
  teardown cancellation are native. Overflow retires/faults instead of replaying
  an unbounded backlog. Disconnects never replay old snapshots into a new session.
- Native movement correction preserves analog inputs and shared ownership.
  GUI typing screens do not gain arbitrary movement/arrow rotation from Inv Move.
- Normal potion/MLG completion now retains the selected item as in the donor.
  Interrupted routines still release owned requests. Broader competing persistent
  and restoring hotbar interactions remain an explicit audit item. Anti Fireball
  retains native fresh-ray/ownership checks, bounded IDs and world retirement;
  its nearby notice uses local chat instead of donor popup UI.
- Time intervals use monotonic clocks. Random algorithms retain donor ranges and
  sampling policy; separate random runs cannot have byte-identical trajectories.
- Existing native targeting/friend/team policy and extra optional settings are
  retained where documented in the module-specific notes. Retained Perihelion
  modules are not evidence of a donor feature still existing upstream.

Current accounting: [102-definition coverage ledger](YURI_131_RECOVERY_COVERAGE_2026-10-18.json).

Module details: [Displace](YURI_131_DISPLACE.md),
[Back Track](YURI_131_BACKTRACK.md), [Inv Move](YURI_131_INVMOVE.md),
[projectiles](YURI_131_PROJECTILES.md), [ghost helpers](YURI_131_GHOST.md),
[combat/Scaffold review](YURI_131_REVIEW.md), [Aim Assist/Block In](YURI_131_ASSISTS.md).

## Verification

The [latest loop](YURI_PARITY_LOOP_3_2026-10-18.md) reproduces and corrects a
persistent-potion/restoring-hotbar regression and corrects Lag Range sampling.
Its 424 unit tests and full Forge/OptiFine suites pass on a coherent checkpoint:
verified pre-account source plus all eight current parity source/fixture edits.
Both runtimes pass 112 recovery/hotbar cases, 66 position/render cases, 44 fireball
cases, 310 rotation cases and the existing full suite, with eleven bridge blocks
at both frame caps and complete native shutdown cleanup.

The shared checkout is concurrently receiving account-manager additions. Combined
build attempts failed first on then-missing account UI types and later in shared
Loom cache setup. **A stable combined build and packaged verification remain pending.**
The latest isolated artifact was not copied over the evolving canonical distribution.

Verified parity artifact: `forge/build/libs/Perihelion-yuri-loop3-isolated-20261018.jar`
(2,249,733 bytes), SHA-256
`d80fc7ffe84881a5afcb1d813e08556e4b438a0d49109808e3e856da1414ae12`.
[Current JSON evidence](YURI_PARITY_LOOP_3_2026-10-18.json) distinguishes tested
sources, matching live owned files, concurrent changes and failed combined builds.

The [second loop](YURI_PARITY_LOOP_2_2026-10-18.md),
[first loop](YURI_PARITY_LOOP_1_2026-10-18.md) and numbered earlier reports retain
artifact-specific historical evidence. The earlier intermittent zero-block
OptiFine bridge failure remains unexplained; no production Scaffold fix is claimed.
The [requirements ledger](YURI_PARITY_REQUIREMENTS.json) preserves all 102 source
definitions, three deferred features, native differences and unfinished requirement
checks. No donor runtime or authenticated server was run. Full mode/setting/interaction
equivalence, absence of regressions and live anti-cheat acceptance remain unverified.

## Rollback

The newest narrow helper, `scripts/restore-yuri-loop3.py`, restores only the eight
loop-3 source/fixture changes and its helper addition. It preserves unrelated
account/UI work and passes dry-run, isolated restoration, later-edit refusal and
checkpoint-hash checks. No live rollback was applied. See the latest report.

The older full-update rollback below retains its historical scope and guards; it
was not refreshed to include concurrent account-manager changes.

A hash-verified 572-file original source snapshot is stored at
`.backups/before-yuri-131-20261017-100824/source.tar.gz` with its manifest.
Archive SHA-256:
`b5e6e23849ee4c520ab671212bbf9198e31f361203764d9c0af214ca7785e33c`.
The changes manifest identifies only affected files. Two unchanged preexisting
subproject build scripts omitted by that snapshot are explicitly preserved and
never treated as additions to remove. Seventeen files from separate concurrent
config/profile and UI work are also excluded from the Yuri rollback and preserved.

Run `python3 scripts/restore-yuri131.py` for a dry run. `--apply` first saves a
hash-verified checkpoint of current affected files, then restores originals and
removes this update's additions. It refuses files edited since the final snapshot,
so later work must be preserved/merged first. The helper passed isolated dry-run,
apply, later-edit-refusal, original-hash restoration, build-script preservation and
checkpoint-hash checks. It has not been applied to the working client. Generated
JARs/caches/worlds remain; rebuild after restoration.

The current loop build includes the HUD/GUI source and fixture edits that
were outside the earlier final-check artifact. Source hashes and current UI
regressions match this build. The overlapping GuiHookProbe rollback guard still
protects its later UI edits; see the current report for the isolated restore scope.

The later UI edit to `GuiHookProbe.java` overlaps an earlier update file. The
working-tree rollback correctly refuses it until it is preserved/merged; the
reported restoration/dry-run passes apply to the isolated tested checkpoint.

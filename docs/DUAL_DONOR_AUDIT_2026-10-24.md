# B4 and Yuri implementation audit — 2026-10-24

The current registry has **96 modules**, and each has a source-comparison row in
[the complete module ledger](DUAL_DONOR_AUDIT_2026-10-24.json). The comparison
inspected native implementations before their relevant donor bodies/helpers.
This covers existing native functionality, not every feature in either donor.

Read-only references:

- Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
- OpenB4 `3d63944cc79be890c013cc79e5ed92d3533b48a5` (`u20`).

No new user settings, modules, dependencies or global donor infrastructure were
added. Existing settings and profile defaults remain. Donor checkouts remain
unchanged. No live Grim Simulation or authenticated Hypixel acceptance is claimed.

## Corrections

| Area | Confirmed defect and correction |
| --- | --- |
| Scaffold Telly jump | Automatic jump ran before vanilla sprint eligibility. Move it to the Strafe phase, after input/sprint processing, as Yuri does. Keep rotation/hotbar ownership gates. The old packaged build failed with **zero sprint impulses** and forward motion `0.06956039771757005`; updated packaged tests require exactly one impulse and the same first-jump forward motion as held manual jump. |
| Telly sprint and return timing | Vanilla sprint now applies Yuri's pre-input sprint state once, then retires that claim at the input hook so vanilla can validate corrected input without a later forced reassertion. None/NCP suppression also owns the sprint key. Capture ground-return eligibility before physics rather than reading changed ground state during motion. Keep startup, diagonal, held-jump and Hypixel flight-latch thresholds. Sample the effective prephysics sprint claim/state for return eligibility; do not infer sprint from a key alone. The Telly physics fixture also requires the Vanilla mode to jump with a sprint impulse while the physical Sprint key is released. |
| NORMAL movement correction | Yuri chooses eight legal digital directions before sprint/item slowdown. Native previously rotated analog acceleration later. Correct input in MovementInputEvent, supply only yaw at Strafe, and preserve native post-sneak/analog magnitude. This applies through shared controls to Scaffold, Clutch, Block In, Bed Defender, Breaker, Derp, Legit Speed and Yuri Fireball Long Jump; Aura Silent and Displace use the corresponding source-specific path. |
| Silent helper direction | Silent and the other winning correction owners now supply server yaw to direct direction/strafe helpers as well as jump impulses. Scaffold expansion explicitly uses camera yaw and raw keys, matching its separate donor geometry path. Previously digital-corrected input could be interpreted again against camera yaw. Add actual helper-direction, direct-strafe and losing-owner regressions. The visible Aim Assist mode remains named **Silent**; the internal `B4_SILENT` config ID is retained for saved profiles. |
| Continuous yaw | Preserve continuous yaw through Yuri smoothing, previous origin, noise and sensitivity quantization. Avoid numeric 360-degree jumps when crossing ±180 or multiple turns. Manual Displace and Long Jump down/throw aims also preserve continuous yaw. Sensitivity rounding uses the direct source delta: a stale reported origin must not rebase selected -15 degrees to +345. Secondary Scaffold candidates are unwrapped around the previous aim before quantization. Existing default Rotation factories stay unchanged. |
| Sensitivity precision | Cast after Yuri's entire randomized-sensitivity affine expression. Early float rounding changed which grid step was selected at a demonstrated boundary (`sensitivity=.07`, random sample `.1`, yaw delta `.00850349385291338`). Regression requires one source grid step where the old calculation rounded to zero. |
| Target-angle precision | Add a source-specific game-atan2/float-conversion helper for Yuri calculate consumers: ordinary Aura aim, Clutch, Legit Velocity and Scaffold's Aura coordination. Keep ML whole-body/different donor projectile solvers on their own formulas. |
| Base speed | Yuri's no-argument base-speed helper uses a `.16` Speed-potion multiplier; native conflated it with the separate `.20` overload. Correct the no-argument path used by NCP Flight/Step and Scaffold speed logic. Unit vectors: `.2873`, `.333268`, `.379236` for levels 0/1/2. |
| Observed action windows | ActionWindow, Clutch, Legit Velocity and AutoProjectile now observe fresh current-client PLAY send facts, including local singleplayer channels. Raw cancellation remains separate. Slot, swing, inventory, attack, use/place and release/mining categories are explicit; C08 stacks are defensive snapshots. Replays, stale connections and foreign threads cannot create fresh facts. Ordinary mining deliberately remains excluded from Yuri AutoProjectile's eligibility gate. |
| AutoProjectile transport | Separate observed eligibility from raw transport cancellation, so recording a sword release does not make the same remote release appear redundant. TCP test permits the first valid release and rejects the repeated release. |
| BackTrack | Session epochs and target identity prevent delayed foreign-thread snapshots from crossing restarted OLD→NEW→OLD sessions or reused numeric entity IDs. |
| B4 Guard / Block Hit | Exclude native Vanilla packet autoblock along with NCP/Hypixel, matching B4's packet-Aura exclusion. Add real-module negative/resume coverage. |
| Stealer | Dynamic delay now uses physical player-to-chest distance and the current candidate's slot index, timed from the previous transfer. It no longer uses slot-grid distance or the previous candidate's delay. Startup/retry/miss gates and native inventory safeguards remain separate. |
| Bed Defender | Restore Yuri's full vector turn budget (the old path divided speed by ten and clamped each axis), sensitivity patch, left-first shell, first missing target, enum support order and 25 face samples. Side faces minimize angular difference; roof faces maximize it. Match x/z radius 8 with y ±2, roof sneak, source replaceable set and delay after valid attempts, including rejected placement. Clear pending plans, wool/rotation/sneak claims while a menu is open. Native submitted-aim, ray, connection and hotbar guards remain. |
| Ambience | Restore the original daylight-cycle rule, including frozen zero, and preserve signed server time semantics. Only advance a running snapshot by elapsed whole ticks and only restore the captured world. |
| Trajectories | Cache includes effective yaw/pitch, launch position and item-use duration; changing aim within the same tick now updates the path. Unchanged inputs retain the bounded cached path; cleanup releases it. |

## Comparisons that remain intentionally different

Both donors cannot define one identical policy where they disagree. The ledger
records these boundaries per module:

- Native ownership, cleanup, submitted-angle identity, immutable pending actions,
  current connection/world checks and bounded packet queues remain authoritative.
- B4-only KillAura interaction/swap/dual strategies, BedAura Instant/Swap spoofing,
  and its seven NoFall packet modes were not added. Native NoFall's three modes
  map to Yuri; its optional pickup uses selected B4 WaterBucket behavior.
- Friends keeps saved names protected while disabled, matching B4's global list.
  Yuri requires its Friends module enabled. Disabling native Friends stops
  middle-click edits; the audit does not guess a new product policy.
- Native Breaker uses actual hit faces, physical tool/hardness and submitted-aim
  guards. Yuri's constant UP face and start/end-only aim schedule and B4's separate
  packet strategies are not claimed as identical native behavior.
- Minigame Aim retains native FOV filtering, prediction/offset and visible-aim
  scheduling. These differ from Yuri and remain explicit source differences.
- HUD/GUI/theme architecture and cosmetic presentation stay native. Provider
  HTTP/authentication internals and pixel-for-pixel donor visuals are outside
  this module-behavior comparison.

The saved `hypxile` Scaffold profile inspected during diagnosis selects
**Sprint: None**. That independently limits speed. It was not silently changed;
use the desired existing Sprint mode when testing Telly. The profile is evidence
of saved configuration, not proof of the settings active in the user's session.

## Verification and remaining limits

The [verification receipt](DUAL_DONOR_VERIFICATION_2026-10-24.json) pins the final
artifact, unit counts, complete Forge/OptiFine runs, fixture hashes and rollback.

Final results: **594 unit tests** (164 core, 430 Forge), zero failures/errors/skips. Both complete packaged Forge and OptiFine M5 suites passed on JAR SHA-256 `888a851119572e44cb4926a9bb1efc25405f1e1e41095ea86310d87e96d741a2`. Each run passed **430 focused audit assertions**, **313 rotation checks**, and **263 terrain assertions across 72 Scaffold cases** (66 cases had a reachable reference-angle comparison). Both shut down with zero retained listeners/queued work and zero NPE, OOM, missing-member or Mixin-apply error signatures. Legacy platform warnings and intentional fault injection remain in the logs.

The full suites exercise shared controls, real transformed input/physics/walking,
rotation ownership, network loopback packet order, Scaffold terrain searches,
inventory/combat interactions, visual cleanup and lifecycle retirement. Selected
regressions are evidence for those branches; source coverage across all 96 IDs is
not exhaustive settings/mode/server parity.

Preserved failure evidence under `.backups/dual-donor-audit/` includes the old
Telly reproduction, fixture compilation/obfuscated-lookup/occluded-view setup
repairs, sensitivity boundary selection and an initially incorrect ordinary-mining
expectation corrected against Yuri's explicit exclusion. No production gate was
broadened to make that expectation pass. Any full-suite failure is retained with
its investigation in the receipt/report, not silently discarded.

A previous audit recorded an intermittent OptiFine bridge placing zero blocks.
A passing current run alone does not establish that historical root cause. Live
Grim flags, latency, every settings combination, exact two-client trace parity,
and authenticated server behavior remain unverified. No dedicated serialized
Librecraft NoFall landing-sequence regression was found; this is a coverage gap.

Before this work, 891 source files were archived and verified:
`.backups/before-dual-donor-audit-20261023-211159/source.tar.gz`, SHA-256
`61b42da6191d46302c5ef8b5f29457bfa02447c061ec097459c51fe177ef6c94`.
The guarded `scripts/restore-dual-donor-audit.py` defaults to a dry run and refuses
later edits to affected files. Its isolated restore tests covered all **49 affected files** (13 additions, 36 modifications), rejected later edits and a corrupted archive, and preserved unrelated work.

The verified JAR is left running in `forge/run-manual-dual-donor-audit-npvs8b_r` (offline test profile, PID 76900). Only the production JAR is installed; smoke properties/probes are absent. Previous module/profile JSON was copied without changing the originals. Startup reached all 96 modules and process liveness was checked. This launch logged duplicate OpenAL initialization and fell back to **No Sound**; interactive audio remains unavailable in this session.

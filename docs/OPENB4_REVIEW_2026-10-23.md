# OpenB4 implementation review — 2026-10-23

This follow-up rechecks the 2026-10-22 ports against read-only OpenB4 u20,
`3d63944cc79be890c013cc79e5ed92d3533b48a5`. The reported Silent Aim Assist
failure was a Grim **Simulation** flag. No exact violation trace or server test
was supplied, so the source discrepancies below are confirmed defects, not proof
of the cause or resolution of that particular flag.

## Findings and corrections

| Area | Original discrepancy | Correction |
| --- | --- | --- |
| Silent input | Generic continuous input rotation at `moveFlying` differed from donor's eight-direction mapping before sprint eligibility. | A local-player post-input event applies source loop/tie ordering and max input magnitude. `moveFlying` uses the same server yaw without rotating inputs twice. Manual B4 Displace uses this path; legacy movement correction remains. |
| Silent rotation values | Shared rotation objects normalized sensitivity-rounded yaw to ±180, introducing a 360-degree change at the wrap. | An opt-in continuous-yaw server request preserves the rounded value; existing rotation factories retain their prior behavior. |
| Silent snap | Maximum-speed aim could unwrap around the camera instead of nearby server yaw. | Desired box angles unwrap against reported server angles, including max-speed and snap paths. |
| Silent timing | Every renderer pick could resample aim/jitter or consume Displace's pending snap. B4 overrides only `Minecraft.runTick` picking. | Tick picks are explicitly marked; ordinary render picks cannot change pending aim or consume snap. |
| Silent selection | A winning request could change its value after selection without invalidating that selection. | Selection retains and checks the exact immutable request value, with source float direction and strict range boundary. |
| Manual Displace recovery | The offset remained through another walking pass after its queued attack succeeded. | Consume the manual hold after replay, allowing Silent/camera recovery before physics and the next walking packet. Aura's existing hold path is retained. |
| Manual Displace tick boundary | The one-tick claim expired before replay, and the old probe omitted that boundary. | Renew the identical submitted value before checking prior walking ownership; the regression now crosses the real control-expiration boundary. |
| Naming/config | Mode displayed as B4 Silent. | Mode/dropdown display Silent and its speed control displays Speed. Stored `b4_silent` / `b4-speed` tokens stay compatible. |
| Auto Clicker | Button handoffs and invalid-item paths could leave the opposite attack/use claim active. Queued clicks bypassed losing claims. | Clear the inactive claim on each branch and queue one-shot presses only under winning input ownership. |
| Block Hit | A held use claim did not reproduce B4 `KeyUtils.press`'s queued vanilla press; cooldown could consume the whole guard window. | Queue the winning use press as well as holding use; test actual sword-use and release packets with a nonzero cooldown. |
| Blink | Any deferred interaction could disable Blink even if another buffer owned the packet. | Release authority belongs to Blink's own eligible capture, retaining client-thread ordered flush. |
| Long Jump | Enabling the hidden B4 mode through a Fireball toggle could leave the legacy activation branch running. | Retire the activation on branch-changing settings. |
| Long Jump ordering | Throwing before local physics differed from donor `preMotion`. | A narrowly scoped submitted-aim guard permits the already-sent immutable angle during `BEFORE_MOTION`, after physics and before the next walking packet. Other action guards remain strict. |
| Long Jump/recovery math | Ray and boost calculations used different precision than B4. | Match the donor's float angle/ray conversion, including Water Bucket pickup. |
| No Slow packet facts | Action history depended on multiplayer interception, and slot eligibility could use a desired, unsent spoof. | Observe fresh client-thread send attempts, including local channels, independently of raw interception. Track attempted C09 and applied S09 slots; exclude buffer replay and foreign/off-thread connections. |
| No Slow timing | Ground mode sampled input-phase air history instead of the donor's post-physics walking history. | Update air history before PRE walking callbacks; regressions cover landing and the 10/11-tick threshold. |
| Fast Break | Cooldown/reset logic was incorrectly gated on hitting a block, retaining stale INCREMENT progress over air. | Clamp cooldown independently and reset released attack history even over air. |
| Fast Break tool | Incremental hardness used the visible tool while server spoofing could select a different tool. | Resolve the effective server hotbar stack. |
| Safe Walk | Prediction read physical keys before native input claims were applied. | Read effective key states after claims are applied. |
| Auto Swap | Opted-in hover timing reset across pauses, and mining scores omitted Efficiency. | Preserve source hover samples across pauses, reset on eligible missing rays, and apply Efficiency²+1 only to base mining strength greater than one. Native weapon behavior is retained. |

No modules, dependencies or user settings were added. The send-fact bridge is
observational; raw packet interception, buffering and cancellation retain their
existing boundary. BedWars state, shop guards,
finished-use bridge, ClickTiming, LagRange and JumpReset were rechecked; native
ownership, bounded queues and cleanup remain intentional host boundaries.

## Verification and recovery

Verified pre-review backup: `.backups/before-openb4-review-20261023-130502/`,
879 files; archive SHA-256
`aaacc776a95db1977499ab0fdced740bac13a9013838f59084e96e5b27413fe5`.
The new guarded helper is `scripts/restore-openb4-review.py`; it preserves the
earlier port and restores only this review's affected files after hash checks.

`check assemble --offline` passed **570 unit tests**: 164 core and 406 Forge,
with zero failures, errors or skips. This includes 52 B4-specific unit tests.
The distribution verifier checks the new input mixin and exact remapped input,
tick-pick and send-overload targets.

Both packaged **full** scenarios passed, including 125-tick local live-play
coverage and shutdown with zero subscriptions/queued tasks:

- Forge: `forge/run-smoke-forge-z5sgl3lt/`.
- OptiFine M5: `forge/run-smoke-optifine-tg3bhwzu/`.
- Each run passed **381 B4 checks**: building 50, combat/network 55,
  movement/BedWars 96, manual combat 180. Both used the same production JAR
  and their recorded fixture-source hashes match the final source.

Regressions exercise 24 walking packets crossing +180 with repeated render picks;
real local input, sprint eligibility, sneak magnitude and item slowdown;
Displace claim expiration, single replay and immediate recovery; click arbitration;
serialized C08 sword use and C07 release with a four-tick cooldown; inverse buffer
ownership; observed versus desired slots, landing and air ticks 10/11, replay and
thread guards; post-physics fireball ordering; and effective tools/inputs.

The probes also needed corrections: inactive-window sampled keys must be explicit,
each fixture must own its sword/geometry prerequisites, and TCP writes must reach
the peer decoder before packet-order assertions. Unique stream markers establish
those boundaries. Two real `runTick` calls remain in both Forge and core tick
counts; only explicitly synthetic events are subtracted. Intentional fault tests
and legacy platform warnings in the full logs are distinct from shutdown checks.

Verified artifact: `forge/build/verified/openb4-review-20261023/Perihelion-0.1.0.jar`,
12,733,623 bytes, SHA-256
`f3d7fcffc8d35fffd2be04c56cff8a6d1e74ef4c0ded780772b78a314a03ec94`.
The guarded rollback covers 47 affected files (12 additions, 35 modifications).
Its isolated restore, later-edit refusal, corrupt-archive refusal, unrelated-file
preservation and live-source preservation are tested; normal invocation is a dry run.
Exact results and evidence hashes are in
[the verification receipt](OPENB4_REVIEW_VERIFICATION_2026-10-23.json).

Live Grim Simulation behavior must still be retested on the relevant server with
the same configuration. Local source parity and packet-order tests cannot certify
current anti-cheat acceptance.

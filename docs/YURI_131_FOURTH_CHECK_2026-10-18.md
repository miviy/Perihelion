# Yuri 1.3.1 deeper movement and packet check

Historical artifact: superseded by [the final check](YURI_131_FINAL_CHECK_2026-10-18.md).

Continued after the third-pass Forge and OptiFine runs, inspecting native code
before the read-only Yuri reference `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
A fresh 651-file source archive was verified before edits; its archive SHA-256 is
`76ffa27a88877107b23d4b085ecda9c4893858d399653ca82fe64d6a7f552390`.

## Additional mismatches repaired

- **Player timing:** Speed, Step, Long Jump, Inv Move, Scaffold and Clutch used activation-relative air timing.
  A native player-owned history now advances at the transformed input boundary,
  even with those modules disabled. PRE routines read the preceding update's
  counters; later strafe/motion routines read current counters. Enabling midair or
  changing modes therefore does not restart the player's ground/air sequence.
  Fireball Long Jump stops horizontal motion on the first completed grounded tick,
  rather than every grounded update. Auto Pot uses the same player-owned grounded
  history, and Inv Move retains the preceding air count on the landing update.
  Telly now preserves the donor latch on established ground; only the source
  zero-air/zero-ground transition clears it.
- **Strafe speed:** Motion Flight, Vanilla Speed and Mospixel's first air tick now
  set event acceleration instead of directly forcing a full-speed vector. This
  retains the source's analog input, 0.98 diagonal factor and existing yaw event.
  NCP Flight also retains its one shared random horizontal-motion multiplier in
  `[0, 1/2000)` instead of discarding old motion completely.
- **Librecraft:** jump factor considers the wrapped angle between movement and the
  winning rotation; the hurt-time speed override requires enabled Aura with a
  target, rather than a last-attacker reference. Ground jumps own a one-tick false
  jump-key claim, check airborne state before applying their boost, and release
  that claim/timer on a mode change. Native control arbitration stays in place.
- **Auto Pot:** attack interval and last-throw deadline persist across ordinary
  toggles, so enabling cannot bypass a pending throw cooldown. Item/GUI safety
  guards remain, while allowed item use no longer resets the attack interval.
- **NCP Speed correction pause:** the first correction starts a 60-update pause.
  Another correction during that pause keeps its elapsed progress, matching the
  source flag/timer behavior. Corrections in other modes do not start this pause.
- **Polar/Intave:** grounded auto-jump now respects the source web exclusion.
  Intave's two first-air-tick multiplications preserve the source operation order.
- **Stasis:** movement-packet suppression starts at activation rather than waiting
  for the first input callback. The look-packet and hurt-state exceptions remain.

No donor event bus, manager, configuration system or global client was imported.
The player history, shared requests, lifecycle cleanup and packet hooks are native.
The main UI and HUD remain Perihelion's.

## Additional checks

Six additional unit cases cover PRE/current counter boundaries, takeoff, landing,
separate player histories, an already-airborne Clutch and Telly ground latching. The transformed-client movement probe covers actual analog and
diagonal `moveFlying`, NCP Flight's random bounds, web exclusions, midair enables,
mode changes, correction pause updates 59/60, rotation wrap/45-degree boundaries,
Aura combat gating, jump/timer cleanup, Step tick 2/3, Long Jump and Stasis timing.
Regen and Fast Use are checked at the real local channel write boundary for exact
C06 counts, threshold/use-count boundaries and disabled categories. Stasis uses
real serialized TCP packets to check immediate suppression, look-only freeze,
release tick, refreeze and hurt exceptions. Existing PRE ordering, 310 rotation,
Scaffold, projectile, buffering and lifecycle probes continue to run.

The first development run failed a Fast Use fixture assertion: calling
`setItemInUse` again with the same active stack leaves its original use count.
The fixture now clears the previous use before setting the next count. Production
Fast Use was not changed. That failed run remains in the JSON evidence ledger.

## Source review and retained limits

Also compared Step gravity prediction, Flight/Water Walk packet Y offsets,
Safe Walk input policy, Eagle's operator precedence and delayed release, Anti Void,
No Slow, Long Jump packet categories, LagManager/Blink flags and native queue
capture/replay/close paths. No Slow deliberately keeps the previously documented
fix that respects disabled item categories. Bounded FIFO queues, dependent-action
ordering, connection/world retirement, native targeting/cursor guards, rotation
submission ownership, and strict saved-setting validation remain native choices.
Blink's position-render option and the three deferred definitions remain recorded
in the existing inventory/comparison; this pass does not claim all donor-visible
features are implemented. Native Phase uses its existing bounded collision/ray
queries, and Eagle keeps a sampled release deadline instead of resampling every
update. Those geometry/random timing adaptations are not exact algorithm identity.

These tests establish selected source, unit, transformed-client and local packet
properties. They do not establish exact two-client runtime equivalence, every
mode/setting combination, authenticated multiplayer or anti-cheat acceptance.
The final 2,228,682-byte JAR has SHA-256
`95e04d0268b235267219c1a4e03b9027945d64965fb3c0388bf5c2860180ae58`.
All 389 unit tests pass (160 core, 229 Forge). Full Forge and OptiFine M5 runs pass
on this identical artifact:

- `forge/run-smoke-forge-ixfj3v1k/result.json`.
- `forge/run-smoke-optifine-vv6iz6_0/result.json`.

Both pass 123 movement checks (10 accounted network events), 19 inventory/velocity
checks (four TCP events), 11 Refill checks, 34 current-update checks, 85 compatibility
checks, 310 rotation checks and the existing 72 Scaffold terrain cases. The client
clears references and stops with zero subscriptions and queued work. Expected
engine warnings, including nine OpenAL buffers at shutdown, remain in the logs.

Artifact, test totals, source hashes and run paths are recorded in
[YURI_131_FOURTH_CHECK_2026-10-18.json](YURI_131_FOURTH_CHECK_2026-10-18.json).

Separate config/profile work appeared in this shared workspace during the audit.
It was preserved, included in the final build, and excluded from Yuri rollback's
owned-change list. The earlier third-pass runtime artifact is retained in its
isolated smoke directories, with a different hash. Full unit XML is archived
before smoke's single config-dump test can overwrite Gradle's ordinary results.

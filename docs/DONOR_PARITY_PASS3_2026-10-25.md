# B4/Yuri implementation audit — third pass, 2026-10-25

This pass concentrates on ownership handoffs, module toggles, packet windows and
connection changes across the same **96 registered module IDs**. Current native
implementations were inspected first, then relevant pinned donor implementations.
It cross-references the previous complete body and second-pass ledgers; unchanged
presentation exclusions were not reinterpreted as missing functional ports.

Donors remain read-only: Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def` and
OpenB4 `3d63944cc79be890c013cc79e5ed92d3533b48a5`. No donor code/build was executed.
No new setting, module, dependency or profile default was added.

## Confirmed corrections

| Area | Before and after |
| --- | --- |
| Rotation connection ownership | An applied or previous walking-angle receipt could survive changing the live network manager while the player/world stayed the same. Bind the walking attempt and applied receipt to their manager, check current/open connection and world for previous-angle access, and reject callbacks that would use an old connection's receipt. The prior verified JAR reproduced the actual handoff failure. |
| Packet-only NoFall position | Librecraft correctly changes the outgoing packet Y without changing vanilla's cache, but the previous-angle guard only checked the earlier motion event Y. It could therefore authorize a ray action from local Y199 after encoding Y200. Track the fresh planned positional packet order per current connection and require that position to match before binding a previous walking aim. Ground-only packets do not repair the mismatch; an ordinary correctly positioned packet does. A hard-cancelled positional walk also invalidates that binding until another positional walk repairs it; deferred queues and angle-only cancellation remain separate. Fresh planned order preserves intentionally deferred walking/action sequences such as LongJump LAG. Librecraft's source packet triplet/type/cache remain unchanged. |
| Auto Apple / competing hotbar | Auto Apple could retain Use while another module selected a fire charge or replaced its apple stack. Remember the original apple slot/object, verify ownership and validity, and retire owned use/slot claims on loss. A late BEFORE_ACTIONS check catches a lower-priority BEFORE_INPUT callback taking the hotbar after Auto Apple's earlier check. Physical input still falls through. |
| Freecam cleanup | Same-world cleanup restored a previously captured camera even when that entity was dead, unloaded or replaced under the same entity ID. Preserve a live registered prior camera; otherwise fall back to the current local player. Do not overwrite a view Freecam no longer owns. B4 restores the local view unconditionally; preserving a valid prior native view remains intentional. |
| Minigame Aim / detached view | Support inventory swaps could still run during Freecam, even though Freecam canceled the heal use itself. Gate planning/support and actions while Freecam or another detached view owns interaction, clearing aim and sneak claims. Resume after local view/ownership returns. This corrects native integration, rather than importing donor global ownership. |
| Manager / Auto Armor timing | Manager reset its last action to zero on every enable, allowing an immediate extra transaction. Auto Armor restarted its whole delay on every enable, losing the elapsed remainder. Both now keep a construction-initialized action timer across ordinary toggles, as Yuri's module-lifetime TimerUtils does. Existing per-action and container reset behavior remains. |
| NoSlow action-window lifetime | B4's sent-use/entity/dig/placement flags exist globally until the next ordinary C03. Native NoSlow's activation-local mask forgot actions when enabled or when a mode/item toggle changed. Keep the fresh current-client action mask in native shared controls regardless of listener/module state. NoSlow reuses that mask while retaining activation-owned mode/buffer state. Replay, stale-manager and foreign-thread calls cannot manufacture fresh actions. |
| Buffer connection ownership | Raw events previously carried no manager identity. A still-open foreign manager could contribute snapshots to a current buffer; a changed manager inside the same handler could also redirect queued replay. Attach actual source identity to real raw events, reject nonmatching sources before capture, and bind flush to the manager captured at activation. Apply this to shared PacketBufferScope consumers and BackTrack's custom queue/C02 position updates. Preserve native raw-thread, local-channel, cancellation, replay and reentrancy behavior. |

These include donor timing/window mismatches and native integration defects.
Neither donor's global architecture resolves native ownership requirements.

## Verification

[The per-module ledger](DONOR_PARITY_PASS3_2026-10-25.json) records the focused
source coverage and retained differences. [The verification receipt](DONOR_PARITY_PASS3_VERIFICATION_2026-10-25.json)
pins the artifact, final tests, source hashes, donors and guarded rollback.

**599 JUnit tests passed** (164 core, 435 Forge), with zero failures, errors or
skips. The retained deterministic Yuri rotation reference covers 20,000 finite
samples.
Both final full packaged suites passed on SHA-256
`541e10af6579214b6806951eb30bdd3e01d42efa5729408a5421fa2903cd5738`
(12,757,395 bytes): Forge `run-smoke-forge-omt7izq7` and OptiFine M5
`run-smoke-optifine-mpkw2f71`. Each passed **607 focused audit assertions**,
including NoFall/receipt **40**, Auto Apple **10**, visual ownership **13**,
inventory/NoSlow timing **31** and packet-source ownership **22**. Each also
passed **313 rotation checks**, **125 movement checks** and **263 terrain
assertions across 72 Scaffold cases** (66 reachable reference comparisons).
All 63 fixture-source hashes match current files. Both retired all listeners and
queued work cleanly; no NPE, OOM, missing-member or Mixin-apply error signatures
occurred. Intentional fault injection and legacy platform warnings remain.

The expanded probes exercise actual transformed walking and item/container hooks,
real PLAY TCP serialization, same-pass control handoffs, stack replacement,
transaction timer remainders, camera identity/cleanup, and foreign packet sources.
The previous full rotation, Telly physics, terrain, combat, inventory, GUI and
lifecycle suites remain enabled. Failures and fixture repairs are retained in
`.backups/parity-pass3-20261025/failures.json`.

## Limits and retained policy boundaries

- Source coverage across 96 IDs is a focused review, not exhaustive settings,
  every interleaving, or exact two-client runtime equivalence.
- No authenticated Hypixel or live **Grim Simulation** acceptance was tested.
- Native lifecycle, priorities, immutable submitted/previous-angle gates, guarded
  hotbar/stack/ray actions, bounded queues and cleanup remain authoritative.
- Existing unsupported donor-only modes, native Breaker policy, Minigame Aim's
  single lead/FOV/visible scheduling, and Block In's smoothed visible/server aim
  remain explicit differences. Visual redesign was outside this pass.
- Fresh planned position checks establish intended queue order, not server
  acknowledgement. They do not turn a TCP fixture into live-server proof.
- Source-less outgoing scalar observations remain outside this buffer ownership change; their existing diagnostics/marker semantics are not claimed to be manager-bound.
- Source-null synthetic raw events remain compatible for internal tests. Real
  NetworkManager hooks always attach their actual source identity.
- The historical intermittent OptiFine zero-placement run remains unresolved;
  passing current tests does not establish its root cause.

## Recovery and manual testing

Verified pre-edit source archive: `.backups/before-parity-pass3-20261025-151800/`,
917 files, SHA-256
`cb5340b1032055f2a8e4fd0d19b3cff48c027872cf2c8794bcb3c2a049494e71`.
`python3 scripts/restore-parity-pass3.py` verifies the guarded rollback;
`--apply` saves affected current files before restoring this pass only. Later
edits and corrupt backups are refused; unrelated work remains intact.

The verified JAR was launched without smoke probes in
`forge/run-manual-parity-pass3-xrdpgd5k`, PID 7792. Native module/profile JSON
was carried forward from the previous manual client, including its current
module state. Credentials and unrelated mods were not copied. Process/window
verification is recorded in `.backups/parity-pass3-manual.json`; this offline
manual profile does not imply authenticated multiplayer acceptance.

The manual client switched to **No Sound** after a duplicate OpenAL-context
startup error. The verified process and 1280×748 on-screen window remain open.
This launch limitation is separate from the passing packaged module suites.

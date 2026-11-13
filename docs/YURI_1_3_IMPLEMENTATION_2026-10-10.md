# Yuri 1.3 behavior updates

Latest source follow-ups and behavior corrections are recorded in
[the October 11 audit](YURI_LATEST_AUDIT_2026-10-11.md). This report
preserves its original implementation and verification baseline.

Follow-up compatibility, terrain, performance, Auto F5 and Name Protect work is
recorded in [the current report](COMPATIBILITY_QOL_2026-10-11.md). The verification
below remains the historical baseline before those follow-ups.

Implemented the recommended gameplay changes in Perihelion after inspecting its
existing modules, activation lifecycle, input/walking hooks and shared controls.
The donor checkout remains clean at v1.3.0, revision
`6f5603e0c0eed253741bacdcd4135830c916602e`. No donor files were edited or committed.

Behavior references are the release's Breaker, Scaffold, AutoSwap, FreeLook,
LegitSpeed and BlockESP modules, ScaffoldUtils and RotationUtils. Aura's Hypixel
autoblock follows the immediate upstream fix at
`c4d18a89b857139974bb0aac6dfdae6eeec08c61`, explicitly selected by the user.
These revisions identify the comparison sources; no donor framework or assets
were imported. The current donor source license is AGPL-3.0; older attribution
and the historical module ledger retain their original revision provenance.

## Implemented behavior

| Module | Settings and behavior |
| --- | --- |
| Breaker | Auto Tool defaults to true. Scores efficiency enchantments only when raw block strength exceeds 1, applies the shears floor of 5 for beds/cloth, and falls back to the first shears/axe/pickaxe for beds. Off uses the current slot. Tool and rotation claims release on interruption; mining hardness uses the tool advertised to the server. |
| Scaffold | Adds Hypixel, Hypixel Larp Mode default true, placement timer steering, movement-relative face search, revised Hypixel Telly speeds/gates, lateral-key diagonal detection and the Hypixel tower jump-boost guard. Hypixel uses effective Legit sprint behavior without overwriting the saved sprint setting. |
| Aura | Adds Hypixel and Auto Block CPS (8 default, 1–10 range), using the upstream timing fix. Legit holds Use only at hurtTime 6–10 and distance <=3, waits for item-use release before attacking, and uses fixed 200 ms attack intervals. Existing Predictive and advanced aim settings remain available. |
| AutoSwap | Adds Swap Back, false by default in Client mode. A winning persistent client selection becomes the new baseline. Swap Back true restores the original unless the user selected something else. Server mode always restores server selection and leaves the visible slot alone. Combat selection requires Aura attack eligibility. |
| Legit Speed | On moving ground ticks outside water/lava/web and without manual jump, requests movement-facing yaw and the player's pitch at rotation speed 10. Jump/movement correction uses that request only while it wins; Aura and Scaffold take priority. |
| FreeLook | Hold defaults to true; false supports toggle use. Captures initial pitch without inversion, applies inversion to mouse deltas, and interpolates between mouse samples using render partial ticks. Leaves player angles alone, yields to Freecam and restores owned perspective. |
| BlockESP | Filled, Outline, Line Width and Client Color controls. Draws double chests and each complete bed once with joined bounds; removes broken beds during bounded scanning and checks bounds against the current world before drawing. Keeps native scoped GL restoration. |

## Rotation and action contracts

- Movement yaw uses the same forward/back/left/right precedence as Yuri, including
  opposite-key combinations and wrapped headings. It does not derive a second
  heading from already corrected strafe input.
- Scaffold's new search starts at movement yaw +90 and pitch 90. It visits yaw
  offsets 0, -1, +1 through +/-20, then +/-22 through +/-90 in steps of two.
  Pitch offsets use 0, -1, +1 through +/-20, clamped to the legal range. The first
  ray matching both support block and face wins; normal search is the fallback.
- Turning has one Euclidean yaw/pitch budget, with shortest-path yaw wrapping,
  speed *18 plus the donor's small random allowance, mouse sensitivity rounding
  anchored to last reported packet angles, and the donor's small FPS-dependent
  perturbations. The random FPS loop limit is recalculated on each iteration,
  matching the donor. Rounding and perturbations clamp pitch at +/-90; settled turns
  stay finite. Scaffold and Aura use this path; unrelated placement modules keep
  their existing turn settings.
- Hypixel steering retains the desired yaw separately from the smoothed angle:
  a placement starts timer 10, and grounded non-jumping steering selects movement
  yaw +50 at timer 8 when angular difference exceeds 60, otherwise at timer 9.
  This retained aim can continue turning while placement is unavailable.
- Hypixel speeds are 6.5 for diagonal/manual jump and 4.8 otherwise. Hypixel Telly
  uses 4.8 diagonal, 3.25 manual jump and 2.5 straight; its grounded moving reset
  uses speed 10 and camera yaw when jumping or not sprinting. It opens placement
  on the first airborne tick, matching the release's early-air gate.
- Placement recomputes from the position after movement, and commits only after
  the identical winning rotation was submitted by the walking pass. Strict
  placement rechecks the face ray; slot transactions keep local item traces and
  server selection consistent and restore on failure.
- Larp yaw (camera +125) and desired pitch are renderer-only. They do not replace
  the packet rotation, camera orientation or entity fields.
- Scaffold's normal correction also directs the actual sprint-jump impulse using
  its winning aim. Jump callbacks are distinguished from geometry-direction
  queries, keeping expansion in the camera/input basis. Auto-jump and tower use
  the preceding aim before the final post-movement aim is calculated.
- Optimized Hypixel autoblock arms in the final 50 ms before an attack deadline,
  blocks on the following interval, and releases its owned use on the next input
  pass. It uses C08/C07, without the release tag's random C09 slot sequence. Native
  attacks/block starts happen after a matching walking packet. Blocking yields
  to another rotation/use owner or a conflicting server slot and rechecks range.

Native adaptations preserve Perihelion's ownership and fault/world cleanup. In
particular, FreeLook captures fresh state on native world reactivation instead of
changing Perihelion's existing enabled-module rejoin policy. The donor's temporary
position writes and global rotation display flag were not adopted.

## Verification

Final artifact: `forge/build/libs/Perihelion-0.1.0.jar`, 1,766,622 bytes, SHA-256
`3eccad2ac01f5e097b5d600adcc34506f68865a6d38415a01f00be64e8cc8430`.
Both final game runs used this identical packaged artifact. Smoke fixture classes
are excluded from the distributable.

| Check | Result |
| --- | --- |
| Offline `check assemble` | PASS; 227 unit tests (155 core, 72 Forge), zero failures/errors/skips; architecture, wrapper, mixin and packaged-JAR checks pass. |
| Packaged Forge 11.15.1.2318, Java 8, Minecraft 1.8.9 | PASS full scenario; Yuri 1.3 checks=90, rotations checks=120; local-world unload/rejoin and final cleanup pass. Run: `forge/run-smoke-forge-5dtysc3c`. |
| Same package with OptiFine 1.8.9 M5 | PASS full scenario; Yuri 1.3 checks=90, rotations checks=120; local-world unload/rejoin and final cleanup pass. Run: `forge/run-smoke-optifine-uvpipifr`. |
| Donor ledgers | PASS; 86 historical definitions at their pinned revision and 88 release definitions accounted for. |
| Launcher shutdown-report regression | Four isolated launcher cases pass: closed stdout, independent shutdown failure, missing cleanup report, and normal stdout. Failure cases remain rejected. |
| Donor integrity | Clean tracked/untracked Git status at `6f5603e0c0eed253741bacdcd4135830c916602e`; no implementation edits. |

The launcher now consumes the probe's existing independent shutdown report as well
as stdout. One run completed every gameplay check but was reported as failed when
a JVM shutdown hook closed stdout before cleanup markers printed. Its independent
report confirmed clean shutdown (`stdoutError=true`); the launcher correction
retains failure detection and is covered by the four cases above. Final reruns
pass with the corrected launcher; the client artifact was unchanged by this fix.

The new packaged-game fixture exercises scored tools, Auto Tool off/interruption,
slot competition/persistence/manual changes/server restoration, camera sampling
and Hold/toggle, movement-facing rotation and actual sprint-jump vectors, new
Scaffold modes across client/server and normal/strict rays, grounded steering,
model-versus-packet yaw, Aura blocking/attack packet order and competing owners,
Legit hurt/range boundaries, and joined storage bounds.

The older Scaffold matrix now advances real walking passes to allow vector-limited
turns to converge. It no longer assumes separate full-speed yaw and pitch jumps in
one packet. Existing rotation/input/cleanup regression probes remain enabled.

The inventory check now validates the 86-module historical ledger against its
pinned Git revision, and the 88-module release ledger independently. Optional
new modules and chat/report/download features remain explicitly deferred; removal
of a donor file does not remove a native feature.

These checks use disposable local worlds. They establish local behavior, native
hooks and packet order; no public-server anti-cheat acceptance was tested.

Final logs contain intentional fault-isolation fixture errors and the existing
Forge signature, unavailable Twitch integration and audio-shutdown warnings.
No unexpected exception or unload-time `NetHandlerPlayClient` NPE appeared in
these two runs. That observation does not establish that every possible unload
sequence is free of the previously reported issue.

The production added-line check found no new external HTTP endpoint, process
execution, credential handling or file-I/O path. These gameplay ports include no
donor account/OAuth, automatic report/chat or skin-download implementation and add
no dependencies. The separate [quick donor security review](YURI_1_3_REVIEW_2026-10-10.md)
retains its source-only scope and dependency findings.

Machine-readable [verification evidence](YURI_1_3_VERIFICATION_2026-10-10.json)
records artifact identity, run results and hashes of the 32 changed source/script
files against the pre-implementation snapshot.

Source rollback archive: `.backups/before-yuri13-20261010-202950/source-only.tar.gz`.

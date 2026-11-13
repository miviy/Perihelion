# Yuri recovery and interaction check

Reference: unchanged read-only Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`. Native code was inspected first.
A fresh 671-file source archive was verified before edits at
`.backups/before-yuri-recovery-check-20261018-142551/source.tar.gz`; SHA-256 `256454f7312d526a8738a81813fbf3ee9eaffc304e8b412996be2cd9fac9f9df`.

## Corrections

| Path | Correction |
| --- | --- |
| Auto Pot | Separate start/aim updates, speed-10 source-derived smoothing, Traditional strafe/jump yaw, no early potion selection, submitted pitch greater than 85 before throwing. Its busy state now pauses Aura, including rotation-free Aura. Interruption/timeout/disable cleanup releases the handoff. |
| Auto Extinguish | Five complete warmup ticks and two complete post-use ticks: click on tick seven, restore on tick ten. Scaffold and either liquid at the feet prevent startup. Smooth aim and Normal movement correction now use native owned requests. |
| MLG No Fall | Smooth catch aim and return toward the current camera, including combined yaw/pitch turns that take several updates. Correct movement yaw and immediately retire state on a live mode change. |
| Shared water/web use | Ray trace along the previously submitted aim, matching the temporary item-use view instead of forcing a vertical ray. |

No donor framework, event bus, module manager, configuration system or global
client was imported. The donor remains clean. Native UI/HUD work was preserved.
The current build includes the UI source/fixture changes that were outside the
previous final-check artifact; all production and fixture hashes match this build.

## Evidence

All **407 unit tests pass** (160 core, 247 Forge), with zero failures, errors or
skips. The build also passes architecture, wrapper, pinned Mixin checksum and
packaged-JAR checks. Unit XML was archived before the smoke launcher ran its own
single-test inventory dump. The new recovery assertions are runtime cases.

Both full packaged runs pass on one identical JAR:

- Forge: `forge/run-smoke-forge-s7m0mt46/result.json`.
- OptiFine M5: `forge/run-smoke-optifine-jtctfllw/result.json`.

Each passes **53 recovery checks**: exact serialized potion walking-aim /
slot / use / native restore order; Aura pause; combined-angle smoothing;
Traditional analog-input preservation; interruption/removal/ownership timeout;
water/lava and Scaffold exclusions; every warmup tick; click/restoration stage
boundaries; actual local water placement; the exact block-top / item-use C08 pair;
and MLG movement/return-to-view/live-mode cleanup. TCP keepalive sentinels are
included in raw event accounting and do not change aim or slot state.

Existing full-suite checks also pass: 310 rotation checks, 124 movement checks,
47 final-helper checks, 19 inventory/velocity cases, 11 Refill cases, 11 Hypixel
timing cases, 10 Displace handoffs, and all 72 Scaffold terrain cases (66 ordered
reference matches). Module startup, UI/config, world retirement/rejoin and native
shutdown cleanup pass; shutdown leaves zero subscriptions/queued work and clears
world/player/manager references. Existing engine/audio warnings remain separate.
Forge reports shutdown `stdoutError=true`: its dedicated `shutdown-report.txt`
contains the complete native cleanup result even though the final STOPPED line is
absent from its launch log. The stream error cause is not established; OptiFine
reports `stdoutError=false`. Both processes exit successfully.

Tested artifact: `forge/build/libs/Perihelion-yuri-recovery-20261018.jar` (2,238,033 bytes).
SHA-256: `7356e54ddbd5e9ead0ea1400072bac937f61f0f12669787e3d66ce8129052f7a`. The canonical `Perihelion-0.1.0.jar` has the same hash.
The [JSON evidence](YURI_131_RECOVERY_CHECK_2026-10-18.json) records all hashes,
archived unit results, current source fingerprints and development attempts.

One development run failed because the new fixture expected a single bucket C08.
Source inspection and the subsequent TCP run established vanilla's block interaction
plus item-use pair. The assertion now checks both exact directions and order;
production was not changed to hide that fixture error. The earlier successful pair
had 46 cases before the additional MLG correction and is historical evidence only.

## Remaining differences and coverage

The [102-definition ledger](YURI_131_RECOVERY_COVERAGE_2026-10-18.json) retains
77 native mappings, 22 presentation exclusions and three deferred definitions
(Insults, Skin Changer, Keybind Info). Inventory/fingerprint coverage and module
startup do not prove every mode's equivalence. This pass concentrated its new
source/runtime comparisons on recovery and item/Aura interactions. Regen/Fast Use
conditions and packet counts were reread and remain covered by existing probes.

Native submitted-rotation identity, first walking boundary, fresh item/entity/ray
checks, owned slot restoration, queue bounds/FIFO/session guards, target/friend
policy, safe potion/cursor checks and GUI typing restrictions remain. In particular,
Auto Pot restores its original slot while Yuri leaves the potion selected.
Independent randomness, monotonic time and shared-control/event scheduling also
prevent a claim of exact timing across every competing-module combination.

Anti Fireball is another explicit source-level gap: native code selects an eligible
nearby fireball and uses a per-attack 50ms throttle/fresh-ray check. Yuri stops at
the first loaded fireball within 12 blocks, does not reset its stopwatch on attack,
and consults its global bad-packet window. This pass retains the native policy;
that module is not claimed to behave exactly like the donor. Blink/Lag Range
position-render options and the deferred features also remain absent.

No authenticated server was joined. Exact two-client runtime equivalence,
exhaustive mode/setting/interleaving coverage and anti-cheat acceptance remain
unverified. Local passing checks are not proof that a bypass works on a server.

The original full-update rollback remains hash guarded. Isolated restoration and
later-edit refusal are rechecked; the live helper still deliberately refuses a
newer overlapping GuiHookProbe UI edit until it is preserved/merged. No working
client rollback was applied, and unrelated UI/config paths remain preserved.

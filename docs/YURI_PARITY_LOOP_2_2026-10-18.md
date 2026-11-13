# Yuri regression and behavior loop 2

This checkpoint closes selected missing features and behavior differences. The
original audit remains unfinished: 77 native mappings still need complete
requirement-level evidence, 22 presentation definitions remain excluded by the
user's UI choice, and Insults, Skin Changer and Keybind Info remain deferred.
See the [requirements ledger](YURI_PARITY_REQUIREMENTS.json).

Reference: clean, read-only Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
A fresh pre-edit archive contains 685 verified files at
`.backups/before-yuri-loop-2-20261018-193945/source.tar.gz`, SHA-256
`99467ece174a46a0a83c8c54437874437071565f45a987ed1fd9664a12ad002d`.
Perihelion was inspected before behavior was reimplemented through native lifecycle,
settings, controls, packet ownership and rendering. Donor code was not executed or edited.

## Corrections

| Area | Behavior |
| --- | --- |
| Blink marker | Adds Render Blink Pos, default true. Capture the first Motion PRE position once per activation; retain it across movement/pulses; retire on disable and recapture on reenable. Draw only in third person. |
| Lag Range marker | Adds Render Lag Pos, default true. Blink mode draws its range-entry anchor; Lag mode draws the last positional movement allowed through the client send path. Draw only while the source lag cycle is active and in third person. |
| Accepted position | Publish an immutable coordinate tuple after cancellation/defer decisions. Look/ground-only and rejected movement do not advance it. Accepted replay and Netty-thread dispatch do. No raw local events or silent-replay redispatch are added. |
| Lag Range release | Movement stop, target loss, Blink range exit and Lag duration end flush at Motion PRE, before another walking packet. Initial Lag POST arming and the one-time 100 ms spoof lease remain. |
| Auto Pot selection | Use the first listed effect, the source good-effect whitelist plus jump, healing health gate, and exact nonzero active-duration rule. A later useful effect cannot override the first effect's rejection. |

Markers use the source 0.6 by 1.8 bounds and 60/40 opacity with Perihelion's native
accent and scoped GL restoration. The position is a client-send estimate, not a
server acknowledgement. Native malformed/mixed-harmful potion checks, fresh
world/item/entity/ray validation, ownership and bounded FIFO/session guards remain.
No donor event bus, manager, config system, UI architecture or assets were imported.

## Evidence

All **421 unit tests pass** (160 core, 261 Forge), zero failures/errors/skips.
Four new scalar-position unit cases cover immutable tuples, invalid coordinates,
retirement and publication from another thread. Existing Lag Range timing tests
now assert the source PRE endings, visible-cycle lifetime, POST arming and lease.
Architecture, wrapper, pinned Mixin, packaged-JAR and assemble checks also pass.
Full unit XML was archived before smoke inventory tests overwrote ordinary results.

| Full packaged suite | Result | Evidence |
| --- | --- | --- |
| Forge / Minecraft 1.8.9 / Java 8 | Pass | `forge/run-smoke-forge-q76hnxyz/result.json` |
| Same JAR with OptiFine M5 | Pass | `forge/run-smoke-optifine-t_1cluk5/result.json` |

Both use identical production/fixture sources and copied JAR bytes. Each passes:

- 66 position/render/release checks through transformed hooks and a PLAY TCP peer:
  accepted/rejected/look-only/background/replayed movement; Blink anchors, pulses,
  toggles and both third-person views; live mode changes; Lag lease replay and
  active-cycle lifetime; all four PRE release reasons and the following walking
  packet before POST. GPU sample queries establish actual drawing/hidden states;
  complete native GL snapshots match before and after every query.
- 96 recovery/Auto Swap checks, including eight new first-effect/whitelist/order,
  healing-health and exact zero/negative active-duration potion cases. Native
  mixed-harmful rejection remains tested.
- Existing 44 fireball, 310 rotation, 124 movement and 47 final-helper checks,
  72 Scaffold terrain cases and the full module/packet/UI/config/lifecycle suite.

Forge placed 10/11 bridge blocks at 60/10 FPS caps; OptiFine placed 11 at both.
Both stop with zero subscriptions/queued work, cleared references and
`stdoutError=false`. Existing engine/audio/overload warnings remain separate.

Artifact: `forge/build/libs/Perihelion-yuri-loop2-20261018.jar` (2,248,976 bytes).
SHA-256: `94964c074ca09a47915143cfac8ba1672a4a062b18faf8917d17c188e578f5cd`.
The canonical JAR matches. [JSON evidence](YURI_PARITY_LOOP_2_2026-10-18.json)
records source/unit/fixture/JAR/launch hashes, source provenance and development attempts.

The first probe setup failed on a missing static import; no game launched. A
full Forge pass on the intermediate marker/phase artifact preceded the potion
selection correction. Final evidence uses the later artifact on both runtimes.

## Remaining work and rollback

An earlier [loop 1 OptiFine run](YURI_PARITY_LOOP_1_2026-10-18.md) placed zero
blocks at the 60-cap bridge phase. Its cause remains unestablished, despite later
passing diagnostics and suites. No production Scaffold fix is claimed.

The next source check is Lag Range's extra random-delay sampling on every PRE;
Yuri samples only when entering a new range cycle. Competing persistent/restoring
hotbar and rotation interleavings, adjacent item predicates, and settings/mode
transitions across all mappings also remain. Selected checks do not prove
exhaustive equivalence or absence of regressions. No donor runtime or authenticated
server was run; exact two-client parity and live anti-cheat acceptance are unverified.

The full-update rollback is rechecked in isolation for dry run, restoration,
later-edit refusal, preserved UI/config paths and recovery checkpoint hashes.
The live helper still deliberately refuses the newer overlapping GuiHookProbe UI
edit until preserved/merged. No live rollback was applied. Isolated source
restoration is not evidence of a rebuilt rollback artifact.

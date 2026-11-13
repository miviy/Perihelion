# Yuri regression and behavior loop 1

This is a verified checkpoint in an unfinished audit. It does not establish that
all 77 mapped modules, every mode or every interaction is identical or bug-free.
The [requirements ledger](YURI_PARITY_REQUIREMENTS.json) preserves the original
102-definition scope: 77 native mappings, 22 presentation exclusions and three
deferred definitions. Perihelion's UI remains native.

Reference: unchanged read-only Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
Perihelion was inspected first. No donor client/build was run or donor file edited.
The pre-edit archive contains 675 hash-verified files at
`.backups/before-yuri-loop-1-20261018-144540/source.tar.gz`, SHA-256
`f30ebde71498fc126626def32a797c31363b772613043ae667da25010ff9ae67`.

## Corrections

| Area | Resulting behavior |
| --- | --- |
| Auto Swap regression | Auto Pot's Aura pause no longer suppresses independent mining. Projectile suppression remains separate. |
| Auto Swap phase/lifetime | Select at Motion PRE after the earlier PRE attack. Retain the owned selection across the next control tick. Client persistent, client restoring and server spoof choices retain their native cleanup and manual-input rules. |
| Anti Fireball | First loaded candidate strictly within twelve blocks masks later candidates; attack includes exactly three blocks. Use the source clamped aim/speed, startup-only inclusive 50 ms clock, toggle-persistent seen IDs and one notice per eligible nearby period. |
| Packet facts | Ordinary local and remote PLAY dispatch updates scalar action facts, even before consumer enable or when later cancelled. Actual Netty-thread sends are observed without exposing off-thread game controls. Silent snapshot replay does not count again. |
| Recovery slots | Normal completed potion and MLG selections persist as in Yuri. Interrupted routines still release owned controls and source safety checks remain. |

Native first-walking-boundary identity, same-position checks, fresh item/entity/ray
validation, bounded collections/queues, FIFO/session retirement and GUI/cursor/manual
input policies remain. The nearby notice uses native local chat instead of donor
popup presentation. No donor architecture, manager or event bus was imported.

## Verification

All **417 unit tests pass**: core 160, Forge 257; zero failures, errors or skips.
Architecture, wrapper, pinned Mixin checksum, packaged-JAR and assemble checks pass.
Ten unit cases were added relative to the previous 407-test recovery checkpoint.
Full XML results were archived before smoke-launch inventory tests overwrote the
ordinary Forge results directory.

| Packaged runtime | Result | Evidence |
| --- | --- | --- |
| Forge / Minecraft 1.8.9 / Java 8 | Pass | `forge/run-smoke-forge-vgwhkn9_/result.json` |
| Same JAR with OptiFine M5 | Pass | `forge/run-smoke-optifine-euyse7kw/result.json` |

The two runs use identical production and smoke-fixture source hashes. Each passes:

- 88 recovery/Auto Swap assertions, including actual PRE C02 attack → Motion PRE
  C09 selection → walking order, next-control-tick lifetime, all three slot policies,
  potion/mining and projectile suppression, normal completion and interrupted cleanup.
- 44 fireball/packet assertions: candidate/range boundaries, exact response order,
  source aim, persistent seen IDs, every action category, ignored packets, cancelled
  attempts, silent replay, actual Netty-thread sends, local raw-bus exclusion and notices.
- Existing 310 rotation, 124 movement, 47 final-helper and 72 Scaffold terrain cases,
  plus prior module, buffering, UI/HUD/font/config and lifecycle/rejoin coverage.
- Eleven actual bridge blocks at both 60 and 10 FPS caps. Shutdown leaves zero
  subscriptions/queued work, clears retained references and reports `stdoutError=false`.

Artifact: `forge/build/libs/Perihelion-yuri-loop1-20261018.jar` (2,243,582 bytes),
SHA-256 `d1c89753f82bb4bb4206f9ef3242bcdd5ce0ff789800a962b91ad1b6fe692066`.
The canonical `Perihelion-0.1.0.jar` matches. [JSON evidence](YURI_PARITY_LOOP_1_2026-10-18.json)
records production/fixture/unit/JAR/run hashes and the complete development history.

## Failure history and remaining work

One earlier OptiFine run (`forge/run-smoke-optifine-1nqmw5mi`) failed the 60-cap
bridge phase with distance `2.262499988079071` and zero blocks. Same-production
Forge and OptiFine diagnostic reruns passed both caps, as did the final artifact.
The cause remains unestablished; **no production Scaffold fix is claimed**.
Added diagnostics retain placement/correction counts, position/focus/slot, submitted
rotation/ownership, support plan and strict ray. Assertion thresholds were unchanged.

Development attempts also exposed fixture compile/reflection errors, an assertion
that included a preparation tick, raw-event accounting of a deliberate cancellation,
and the older Auto Swap fixture's omitted Motion PRE boundary. A missing notification
type qualification was corrected after a compile failure. Java 8 font tests aborted
under the filesystem sandbox, then passed with the required desktop access. All
attempts remain in the JSON/logs; passing reruns do not erase them.

Continue the unresolved bridge investigation, competing rotation/hotbar and recovery
interleavings, then requirement-level settings/mode/transition checks across all
mappings. Blink/Lag Range position-render options and Insults, Skin Changer and
Keybind Info remain absent. Independent randomness, monotonic time, native arbitration
and lifecycle protections prevent a claim of identical timing in every combination.
No authenticated public server was joined. Live bypass acceptance and exact two-client
runtime equivalence remain unverified.

The full-update rollback's isolated dry-run/restoration, later-edit refusal and
recovery checkpoint hashes are rechecked separately. The live helper intentionally
refuses the newer overlapping GuiHookProbe UI edit until preserved/merged. No live
rollback was applied; unrelated UI/config files remain preserved. The isolated
rollback check verifies source restoration, not a rebuilt rollback artifact.

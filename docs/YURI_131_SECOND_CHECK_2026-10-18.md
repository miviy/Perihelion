# Yuri 1.3.1 second parity check

Historical artifact: superseded by [the third check](YURI_131_THIRD_CHECK_2026-10-18.md).

Compared native code first, then the unchanged read-only Yuri reference
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def`. The previous audit's artifact and
107 affected-file hashes were unchanged at the start of this pass. A fresh
641-file source archive was hash-verified before edits; its path and archive hash
are recorded in the JSON evidence report.

## Additional mismatches repaired

- **Intave Velocity environmental conditions.** The native shared guard incorrectly
  applied liquid/web and Ignore On Fire exclusions to both Intave variants.
  Yuri's Latest variant has neither exclusion, and Intave 13 excludes liquid/web
  but does not exclude fire. The native listener now uses those mode-specific
  conditions, including the original 0.52 grounded / 0.8 airborne / 0.6 Latest
  multipliers and the one-use successful-hit slowdown flag. Ignore On Fire still
  protects Cancel/Custom packet handling. The existing native attack event
  already restricts production callbacks to living targets.
- **Inv Move chest Blink eligibility.** Collection previously started before the
  configured midair and speed-potion checks. It now starts after eligibility, as
  in Yuri. This does not change inventory-open Blink or Close mode's send-callback
  collection, transaction FIFO, release countdowns or session checks.

Added `SecondParityProbe`: transformed living-target attack callbacks for Latest
in normal/web/water/fire conditions, successful-hit flag consumption, Intave 13
on ground/in air/in web/in water/in fire/without hurt, and actual localhost TCP
look-packet capture for disallowed midair, disallowed speed potion and eligible
chest Blink. Disable must replay the original held packet and release queue
ownership. Its measured TCP events join the existing strict global event ledger.

## Other source comparisons

Rechecked Cancel/Custom S12 handling; Legit Velocity target, item-use and previous
rotation gates; Inv Move click rejection, same-tick batching, finish/pause and
2/3-update release boundaries; click sampling and one-press tick cadence;
Block Hit's PRE-motion delays and packet-fact gate; projectile delay thresholds,
rotation tolerance, held-item recovery; Displace hold/cooldown/void-side math;
and Scaffold's search order and quantized ray validation.

The Inv Move phase suspicion was ruled out: Yuri posts `PreUpdateEvent` before
`super.onUpdate()`, which subsequently updates movement input and posts
`MoveEvent`. Advancing transaction state before consuming the movement stop is
therefore correct. No pause-order rewrite was made.

## Verification and limits

`check`, pinned-Mixin checksum and packaged-distribution verification pass.
All 376 unit tests pass (156 core, 220 Forge, zero failures/errors/skips).
The identical 2,218,965-byte JAR passed both complete packaged scenarios:

| Runtime | Evidence |
| --- | --- |
| Forge / Java 8 | `forge/run-smoke-forge-abmyz3px/result.json` |
| Forge + OptiFine M5 / Java 8 | `forge/run-smoke-optifine-3i8skb4w/result.json` |

Both runs pass all 18 second-check regressions with exactly three new TCP
packet events, 310 rotation checks, 11 Hypixel timing checks, Displace handoff,
Hit Select phases, Back Track transitions, 72 Scaffold terrain cases, 95-module
lifecycle, UI/HUD, live bridging, config and rejoin. Both stop with zero
subscriptions/queued work and cleared world/player/handler references. No new
unexpected missing-class/member, NPE, stack-overflow or OOM signature appears.
Engine/FML/Twitch/OpenAL warnings and intentional fault-injection messages remain
in the raw logs. No failed build or runtime attempt occurred in this second pass.

JAR SHA-256: `c593a4594f682723a693fbe5b3ea980dd78fc3f9ee693df484021cd95445f028`.
The [JSON report](YURI_131_SECOND_CHECK_2026-10-18.json) records source/fixture
fingerprints, exact logs and final rollback checks. The full-update rollback
helper is verified by dry run, isolated restore, later-edit refusal, preservation
of the two unchanged build files and recovery-checkpoint hashes; it has not been
applied to the working client.
The current inventory remains 102 donor definitions: 77 native mappings,
22 presentation exclusions and three deferred features (Insults, Skin Changer,
Keybind Info). Perihelion's menus, HUD and architecture remain native.

These checks do not establish exhaustive two-client equivalence or live server
acceptance. Native differences remain: submitted-rotation ownership and its first
walking boundary; private bounded FIFO snapshots and session/handler checks;
monotonic time and independent randomness; targeting/friend policy; restricted
GUI movement; and the existing living-target attack-hook scope. Tests in this
pass exercise that living-target path. Donor source was neither changed nor run.

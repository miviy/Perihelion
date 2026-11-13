# Latest Yuri source audit — October 11, 2026

Scaffold's placement phase has since been corrected for the reported POST warning.
See [PRE timing](SCAFFOLD_PRE_TIMING_2026-10-11.md). The earlier artifact and
verification below remain a historical baseline before that correction.

The comparison is pinned to upstream main [`50d7322b85cb8de096241077ca3130e9e9715d20`](https://github.com/unleg1t/Yuri/commit/50d7322b85cb8de096241077ca3130e9e9715d20), including the Scaffold fix published during the audit. The read-only donor checkout stays at v1.3.0, `6f5603e0c0eed253741bacdcd4135830c916602e`. Current upstream source is AGPL-3.0. No donor checkout, framework or assets were changed or imported into the build.

Perihelion modules, lifecycle, shared controls, packet hooks and existing verification were inspected first. An ignored `.references` snapshot contains 341 Java reference files, checked against the exact upstream Git blobs and SHA-256 fingerprints. This audit data is outside every source set.

## Coverage and limits

[The current module ledger](yuri-latest-modules.json) accounts for all 88 definitions: 63 map to registered native features, 21 retain the earlier presentation exclusions, and four retain their explicit deferral: AutoServer, Insults, KeybindInfo and SkinChanger. Earlier native features removed upstream remain available. This preserves the existing scope decisions.

The complete release-to-main delta was reviewed, including changed helpers. Source fingerprints identify upstream changes; registry tests verify native definitions. They do not prove that every setting, mode, module combination or public-server bypass is equivalent. Unchanged upstream bodies retain their earlier native implementations and documented differences. Perihelion uses its own lifecycle, event timing, settings, fault cleanup, controls and rendering.

## Corrections

| Module | Current native behavior |
| --- | --- |
| Hit Select | Replaces the incoming-hit/chance model with the latest nine settings: Pause, Only In Combo, low-health escape, Fake Swing, Force First Hit, critical/jump gates, swap reset and damage reset. Pause steps by 50 ms. Uses fresh hurt edges, 750 ms hit association, 1,250 ms combo expiration and 170 ms damage escape. First-hit entries expire after 10 seconds. History belongs to the activation; the actual attack target is used and retargeting resets its hurt edge. |
| Auto Projectile | Target Range is 10 by default, with range 4–15, retaining Aura's seek/distance/hurt eligibility. A copied packet window avoids attack/inventory/block conflicts. Sword use is released before taking the projectile slot on the next walking interval. Other sword block/release packets and attacks yield during throw/recovery. Eggs/snowballs retain the slot for two ticks, rods for four, then a one-tick cooldown; pending work is bounded to 12 ticks. Aura and Auto Swap yield through a native dependency and shared claims. |
| Aura | Retains optimized C08/C07 blocking and owned local-use cleanup. Adds the latest block-packet attack-window guard and local block reassertion after an authorized walking pass. Projectile work suspends its rotation/use and attack eligibility without donor global flags. Cleanup stops only use owned by Aura. |
| Stealer | Armor remains eligible with equal/better equipped or stored armor. Keeps native upgrade ordering, inventory-capacity checks, resource targets and bounded transactions. |
| Scaffold | Includes the newest 400 ms Telly jump safety window, latched Hypixel diagonal opening at airborne tick 1 versus straight ticks 1–3, and diagonal turn speed 3.3. Strict placement sends server animation without a local arm swing. Keeps submitted-rotation checks, post-movement planning, rounded terrain rays, ordered fence sneak and duplicate-ray pruning. |
| Derp | Client mode renders both yaw and pitch through the native model-orientation hook, including with no packet rotation active. Preserves camera/entity angles and restores immediately on disable. |
| Minigame Aim | Halloween skull interactions include a swing and filled world highlight. Actions still wait for a matching submitted rotation. |
| Skeletons | Reuses copied pose arrays, prunes dead/removed player identities and clears the activation's weak map during cleanup. |

The game test found a real projectile recovery failure: vanilla `sendUseItem` returns whether local item state changed, even after sending C08. Treating that return as send success restored the slot too early for unchanged creative stacks. Recovery now follows an authorized attempted use; this does not claim server acknowledgment or damage.

The projectile guard preserves non-sword manual releases. Menus, spectator/dead state, changed targets/inventory, lost claims and canceled walking abort pending actions. Native buffering keeps its bounded ordering and connection/world-generation checks. Fonts, branding, shader appearance and repaired donor bugs remain differences described in [the original port](YURI_PORT.md) and [compatibility report](COMPATIBILITY_QOL_2026-10-11.md).

## Verification

The offline build/distribution checks and 244 unit tests pass: 156 core, 88 Forge, zero failures/errors/skips. New checks cover registration/defaults, hurt/combo boundaries, first-hit expiry, packet conflicts, release ownership and Telly timing. `python3 scripts/yuri-latest-audit.py --check-upstream` validates the current source snapshot and detects later upstream changes; `python3 scripts/yuri-port-inventory.py` validates the historical ledgers separately.

The final 1,792,381-byte JAR has SHA-256 `4981575cef33ab83c91c8f3f901119142cea42b2ac8709388d1e40c5f2918e04`. Forge's full packaged run passes: `forge/run-smoke-forge-xw0w0v7g`. It exercises 27 new behavior checks, 98 Yuri 1.3 checks, 274 rotation checks and 72 Scaffold terrain cases/335 checks, with 66 original-loop first-match comparisons. Actual throw packets follow walking, slot recovery lasts two ticks, sword release precedes a throw interval, interruptions release claims, client Derp displays pitch, and strict Scaffold retains server animation. Unload/rejoin, stale-entity guards and zero-subscription/zero-queue shutdown pass.

The identical package and fixture sources also pass the OptiFine M5 full run,
`forge/run-smoke-optifine-aamdg195`, with the same 27/98/274 behavior checks and
72 terrain cases. Complete hashes are recorded in [the evidence ledger](YURI_LATEST_VERIFICATION_2026-10-11.json). The initial fixture compile failure and creative-stack recovery failure are retained separately. The first OptiFine run passed all assertions and Perihelion cleanup but exited -6 after a native SIGSEGV in `libobjcMsgSend.dylib` on the macOS AppKit thread; its crash report is retained. That run is failed, regardless of PASS markers. Any later successful run does not resolve every possible native shutdown sequence.

These are transformed-client, private integrated-world and localhost-codec checks. Authenticated multiplayer and public-server anti-cheat acceptance have not been tested. Matching source behavior cannot establish bypass effectiveness.

## Backup and rollback

Before edits, 375 source/config/script/documentation files were archived and every member hash verified: `.backups/before-yuri-latest-20261011-205438/source.tar.gz`, SHA-256 `b48d34b2c9d01049651aa9526f0eec08713e16d6cee84258bcbc520f4ddbc468`. Final `changes.json` identifies only affected files. Run `python3 scripts/restore-yuri-latest.py` for a checked dry run. Optional `--apply` first backs up affected current files and refuses later edits. Generated JARs, reference caches and smoke worlds are outside source rollback; rebuild after restoring. No rollback is applied automatically.

Rollback verification passed: dry run, isolated restoration of changed originals/removal of additions, refusal of later edits without partial writes, and current-workspace preservation.

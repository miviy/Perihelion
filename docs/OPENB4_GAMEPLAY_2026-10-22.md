# OpenB4 gameplay improvements — 2026-10-22

## Scope and provenance

Read-only reference: `../donor_clients/OpenB4/OpenB4/`, OpenB4 `u20`,
`3d63944cc79be890c013cc79e5ed92d3533b48a5`.

This pass adds functional options to existing Perihelion modules. The module registry is
unchanged. Existing settings IDs and default behavior remain selected. No new standalone
minigame, script, diagnostic, cosmetic or visual module was added. In particular, bed beams,
equipment nametags and projectile drawings are outside this pass.

Perihelion owns all lifecycle, event dispatch, settings, control requests and packet storage.
Donor managers, event bus, globals, unbounded packet lists and background world access were
not imported. The source was inspected, not built or executed.

Verified pre-change backup:
`.backups/before-openb4-gameplay-20261022-140913/source.tar.gz`, 845 files,
SHA-256 `806262d882a88419a5f3e2e917bb4326f2d29f0a153c67ae32514b23d0a72fb4`.

## Implemented behavior and controls

| Existing module | Added controls / modes | Behavior represented |
| --- | --- | --- |
| Auto Clicker | `click-mode`: NATIVE (default), B4_LEFT, B4_RIGHT, B4_BOTH; `inventory-cps`: 0 (default), visible for B4 | B4 press/release scheduling, slow phases, damage-triggered scheduling, continuous mining, right-click block repetition, Shift+held-left inventory/chest transfers. Existing min/max CPS and Weapons Only are reused. Donor jitter and block-hit extras stay at their zero defaults. |
| Block Hit | `block-mode`: AFTER_ATTACK (default), B4_GUARD | B4 default proactive guard: hurt time at most 2 ticks, 3-tick hold, 5-tick cooldown, enemy ray/range checks and manual-right-click cooperation. Uses the existing Range control. No lag or random-duration settings added. |
| Reach | `activation`: empty by default; WEAPON, MOVING, SPRINTING | All selected donor activation predicates must pass. No predicate changes the existing min/max range settings. |
| Aim Assist | existing Mode gains B4_SILENT; `b4-speed`: 50, B4-only | B4 nearest-yaw selection, quadratic rate, common yaw/pitch divisor, sensitivity grid, default 3-step yaw jitter and 90% closest-point weighting. Reuses range/FOV/player/mob/invisibility/team controls. B4 preset uses swords, both axes, through-block selection and no click/stop-on-target/breaking gates, matching those donor defaults. Legacy-only controls are hidden in this mode. |
| Displace | `activation`: AURA (default), B4_MANUAL, BOTH; existing angle maximum extends to 180 | B4 manual/AutoClicker interception, camera-relative displaced yaw, default attack suppression during cooldown/hurt exclusion, one replay after a submitted walking rotation, weighted six-block drop-side rays and silent AimAssist snap/handoff. Existing directional modes and cooldown/item/player/hurt controls are reused. |
| Jump Reset | `mode`: STANDARD (default), B4 | B4 received-velocity versus sprint-jump impulse test; require at least 3% predicted horizontal benefit, skip recent nearby explosions, consume one input opportunity. Uses existing Chance. Sprint is mandatory in B4, and the native Nearby Player/Sprint switches are hidden there. |
| Blink | `release-on`: empty by default; interaction multi-select | Release/disable after the selected attack, inventory click, mining or real block placement is captured. Triggering traffic remains in the native ordered queue until the client-thread release. The broken donor timeout and unused cancel-knockback setting were not imported. |
| Lag Range | existing Mode gains B4 | Default B4 approach profile: speed-potion-adjusted 8.5-block start range, dot-product/motion gates, close/far 100/200 ms delay with ±12 ms jitter, 5 s sessions with donor jitter, 4 retreat ticks or 3 missing ticks, item/building gates, interaction release at the next movement packet. Legacy band/delay controls are hidden for this fixed profile. |
| Fast Break | `progress-mode`: NATIVE (default), PRE, POST, INCREMENT; `break-multiplier`: 1–2, default 1 | B4 PRE `1-1/speed`, POST `1/speed`, and incremental relative-hardness scaling. Existing delay is reused. The old progress seeding remains unchanged in NATIVE mode. |
| FastPlace | `block-refill`: OFF (default), SAME_TYPE, ANY_BLOCK | B4 hotbar block-stack selection at the default low-stack threshold of one, largest eligible stack and descending-index tie handling. This is separate from native Auto Swap's tool/weapon purpose. |
| Safe Walk | `mode`: LEGACY (default), EDGE, SNEAK; B4-only block/pitch/tolerance controls | B4 next-footprint prediction using prior motion plus input acceleration, donor edge tolerances and either edge clamping or a scoped sneak claim. Defaults: blocks only, minimum pitch 65, medium tolerance. Minimum pitch 19 disables the pitch gate as in the donor. |
| NoFall MLG | `pickup-water`: false (default), MLG-only | Track successfully placed water and the resulting empty bucket, then attempt first-grounded pickup with the same slot, downward ray and preceding submitted aim. Preserve native water/web clutch and rotation recovery. |
| Auto Swap | `tool-hover-delay`: 0 (default); `skip-interactable`: false (default) | B4 hover delay and interactable exclusion for mining tools; existing weapon selection, visible/spoofed swaps and competing item routines remain owned natively. |
| No Slow | existing Mode gains BLINK, GROUND, INVALID | B4 food/drink/milk/bow eligibility, bow-arrow and kit-selector exclusions; Blink slowdown through tick 13, buffering from 14 through 33 and release at 34; Ground air-use state, partial slowdown and jump suppression; Invalid's three-tick packet pattern. Existing item switches are reused; no additional settings. |
| Long Jump | Fireball Mode gains B4_AUTO; B4-only `b4-horizontal-boost` and `b4-vertical-motion`, default zero | B4 reactive fireball sequence: prepare item/aim, throw after the required motion passes, wait for a nearby explosion, suppress its ordinary application, apply configured source boost for one tick and restore/release. Timeout is 750 ms. Zero means no corresponding motion override, as in B4; positive values are needed to request propulsion. |
| BedWars Utility | `b4-alerts`: false; `b4-shop`: false | One default-alert preset covers bows, armor/enchants, pearls/fireballs, potions/invisibility, milk, obsidian, rotation items and water buckets, using source transition/permanent flags and 2 s throttling. Milk lasts 600 ticks, followed by 40 finished ticks, shown in one replaceable native chat line. Optional shop middle-click purchase and wheel resource transfer use donor GUI guards. |

## Native integration and deliberate boundaries

- New narrow events cover left-click attempts, GUI mouse input, completed local item use and
  scaled item slowdown. Position correction, explosion and block-change packets are handled
  after vanilla's client-thread handoff; stale handlers cannot mutate the current world.
- Aim selection requires the corresponding native rotation owner. The explicit
  AimAssist-to-Displace handoff is allowed only for that same target under Displace ownership.
  An unrelated higher-priority owner cannot borrow a silent target.
- Manual Displace waits for its exact prior walking rotation, temporarily applies it to the
  local attack calculation and restores the camera afterward. It revalidates world, entity,
  crosshair and competing building activity before replay.
- Native safeguards still reject stale generations, unavailable connections, detached-camera
  actions and competing high-priority controls. Jump Reset also retains native environmental
  and competing-movement exclusions, but B4 ignores the native fire exclusion as the donor
  does. These are intentional host compatibility limits. Existing CPS and Chance values are
  reused rather than silently replaced by donor defaults (8–14 CPS and 100% in a fresh native
  config versus B4's 11–13 CPS and 80%).
- All buffering uses native bounded encoded queues (8,192 packets / 16 MiB), connection/world
  identity and ordered client-thread replay. This differs from B4's global unbounded transport.
- Native Friends/AntiBot state replaces donor global identity registries. Server-specific
  BedWars checks use the donor's observed sidebar/team conventions; they require live-server
  confirmation before claiming current service compatibility.
- Fireball Auto uses native `BEFORE_ACTIONS` and the preceding submitted aim. B4 throws
  during `preMotion`; the two preparation passes, nearby explosion response and timeout
  match the selected source policy, but intra-tick ordering is a native adaptation. Fixed
  donor defaults are used for one boost tick, motion addition/inversion/strafe/stop options.
  This describes the October 22 artifact. The [October 23 review](OPENB4_REVIEW_2026-10-23.md)
  corrects the throw to native `BEFORE_MOTION`, after physics, under an exact prior-aim guard.
- NoSlow adds only Blink, Ground and Invalid with the source's zero-slow setting. Other
  source combinations are outside this pass. BedWars milk's chat presentation replaces
  the donor HUD without importing extra visual controls.
- This implements selected coherent B4 policies, not every possible combination of donor
  switches. No current public-server bypass, greater combat success, or measured mining/
  movement speed is inferred from static equivalence or local tests.

## Exclusions

- New standalone modules (GhostHand, AutoLadder, NoRotate, AutoPlace, Freeze, Speed Builders,
  other minigames, scripts, packet viewers, radar and extra ESP target types).
- Visual upgrades, including nametags, beams, tool icons, damage/fall overlays and cosmetics.
- Additional Scaffold/Tower/Speed/Aura/BedAura/AntiKnockback strategy collections. The existing
  native implementations already provide these capabilities; this pass does not add a large
  family of speculative server-specific switches.
- NoSlow Jump/Float/Interact/Sneak variants, which require additional coupled placement,
  rotation and input behavior; no approximate implementation or empty mode label was added.
- Donor defects and unavailable backends, including Blink's stalled timeout, unused options,
  Duels Stats/IRC/assistant placeholders, unsafe AutoRequeue dispatch and script blacklist.

## Verification

Final coordinated `check assemble --offline` passes 553 tests (162 core, 391 Forge), zero
failures/errors/skips, with architecture, wrapper, pinned Mixin and packaged-JAR verification.
The 38 B4 unit tests cover source equations, boundaries, state transitions and cleanup.

Both packaged **full** scenarios passed on the same artifact:

| Runtime | Run directory | B4 assertions | Result |
| --- | --- | --- | --- |
| Forge 1.8.9 | `forge/run-smoke-forge-re2g_lh4` | 202 | Full PASS; unload/rejoin PASS; shutdown subscriptions=0, queued=0, stdoutError=false |
| Forge + OptiFine M5 | `forge/run-smoke-optifine-t5d2b6_i` | 202 | Full PASS; unload/rejoin PASS; shutdown subscriptions=0, queued=0, stdoutError=false |

Per run: building 36, combat/network 40, movement/BedWars 67, manual combat 59.
The broader suites also cover existing module lifecycle/fault cleanup, shared controls,
rotation/action ordering, packet ordering, gameplay, inventory movement, GUI/HUD/configs
and stale-world delivery. Intentional fault-injection logs are part of those fixtures.

Verified artifact: `forge/build/verified/openb4-gameplay-20261022/Perihelion-0.1.0.jar`,
12,722,031 bytes, SHA-256
`963826182a2733a18044f11cc0138cdf5da37d4bf2b335132ec6bc170dd000c1`.
The [machine-readable verification record](OPENB4_GAMEPLAY_VERIFICATION_2026-10-22.json)
records artifact, fixture/run manifests, test counts and evidence limits.

The guarded rollback helper `scripts/restore-openb4-gameplay.py` defaults to a dry run and
refuses later edits or unsafe paths. It passed 22 isolated guard checks and a separate
restoration of an actual 60-file change snapshot in an isolated tree, including later-edit
refusal, checkpoint verification and preservation of unrelated files. The live tree was not
rolled back. Run it without arguments to review; `--apply` saves current affected files
before restoring the pinned originals and removing only listed additions. Rebuild afterward.

The dedicated `scripts/launch-smoke --scenario b4` invokes four probes against the remapped
artifact: B4BuildingProbe, B4CombatHookProbe, B4MovementBedWarsProbe and B4ManualCombatProbe.
The same probes run before the existing full gameplay fixture in `--scenario full`.

Unit vectors independently check donor timing/decision equations. Packaged probes use real
transformed selection, controller/input operations, walking packets, local PLAY loopbacks,
item use and lifecycle cleanup. These evidence levels do not substitute for authenticated
Hypixel gameplay.

## Corrections found during review and execution

- Used B4's float yaw/pitch and Minecraft sine/cosine lookup for finite-range target rays,
  and its exact multiplication grouping at the LagRange dot-product threshold. Boundary
  fixtures distinguish both from the mathematically similar alternatives.
- Restored the shared donor knockback hook's `0 < motionY < 4500` gate, and narrowed B4
  mob targeting to hostile mobs, golems and slimes. Passive animals and villagers are
  rejected. Tests also exercise all equipment slots for invisible targets and reused IDs.
- Switching Block Hit modes immediately clears the prior use claim. LagRange release is
  classified inside its own packet capture with session ownership; a late raw callback or
  another module's buffered packet cannot stop a replacement session. Replaced entity IDs
  count as missing targets rather than retaining the old object.
- Water pickup explicitly performs block interaction followed by item use, as B4 does.
  The first fixture incorrectly moved to its landing without submitting that position;
  it now sends the walking boundary while retaining real bucket/source-water assertions.
- Inventory clicking is tested in survival inventory. The initial creative-world fixture
  redirected to Creative inventory, correctly excluded by B4, and was corrected without
  expanding production eligibility.
- BedWars state survives temporary scoreboard absence. B4 reset uses its exact seven
  source start-message patterns and chat-type/leading-space guards. A covered diamond
  leggings alert avoids a duplicate legacy warning. The Forge item-finish bridge is
  exercised with consumed milk, a different replacement item and a remote player.

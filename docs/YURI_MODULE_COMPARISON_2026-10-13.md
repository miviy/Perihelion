# Yuri / Perihelion module comparison — October 13, 2026

Historical comparison: later behavior corrections and remaining differences are recorded in [the final check](YURI_131_FINAL_CHECK_2026-10-18.md).

Comparison references: read-only Yuri v1.3.0
`6f5603e0c0eed253741bacdcd4135830c916602e`, and the existing cached source snapshot
`50d7322b85cb8de096241077ca3130e9e9715d20` used in the October 11 follow-up.
The latter is hash-checked, not refreshed from upstream in this run. The older
`0a4bd400784ef529d238311de5ce867e815794e2` ledger identifies retained modules
removed from the later donor. The [source/evidence ledger](BUG_PERFORMANCE_VERIFICATION_2026-10-13.json)
records the comparison inputs and hashes.

Perihelion's built-in registry contains **82 world-scoped definitions**, including
HUD. Forge also registers its separate ClickGUI definition: **83 in the client**.
Both the release and cached follow-up inventories contain **88 donor modules**:
**63 mapped native equivalents, 21 presentation exclusions, 4 explicit deferrals**.
The historical inventory contains 86: 65 mappings and 21 presentation exclusions.
Registration coverage is not behavioral equality. See [the bug review](BUG_PERFORMANCE_REVIEW_2026-10-13.md)
for this pass's fixes and remaining finding.

## Most significant differences

| Module | Yuri | Current Perihelion | Assessment |
| --- | --- | --- | --- |
| Breaker whitelist | A separate manager acquires a bed and resets on the match announcement. | Runtime-owned tracking while disabled, anchored loaded-bed discovery, world/system-announcement resets and PRE action guards. | P2 gap fixed in the [whitelist follow-up](BREAKER_WHITELIST_2026-10-13.md); native validation and waiting behavior are deliberate differences. |
| Breaker / Bed Defender bed range | Searches vertical offsets within its search range. | Now searches the full native radius, prunes by distance and avoids unloaded chunks; previously limited to ±2 vertically. | Fixed this pass; reach/ray and actual support-face checks remain native. |
| Hit Select | v1.3 has Chance, Combo Delay, Reset Timeout, prediction/idle settings. | Uses the newer nine-setting Pause/combo/first-hit/critical/jump model. | Version difference, not missing implementation of the old model. |
| Aura | Donor target management, click/block modes, shared global state and recording facilities. | Native target sort/FOV/input gates, additional Hypixel block mode, bounded ML sample input, shared arbitration and immutable prior-aim PRE actions. | Many options match; target ordering, ML operations, and combined-module timing differ. |
| Manager | Inferior/trash dropping with role sorting. | Dropping disabled by default; extra named/lore/slot/item protections and explicit drop controls. | Intentional inventory-preservation default; enabling it alone will not drop items like Yuri. |
| Stealer | Sequential filtered looting with timing/miss options and Grab Mouse. | Extra upgrade ordering, resource thresholds, capacity checks and bounded transactions; no Grab Mouse setting. | Intentional extension plus an omitted input/presentation option. |
| No Slow NCP | Sends RELEASE_USE_ITEM for any item in use, even if its type toggle is off. | Sends it only for enabled item types; local slowdown uses the same filter. | Inherited bug repaired this pass. |
| Blink / Fake Lag / Lag Range / Long Jump Lag | Donor global queues/managers, donor buffer visibility. | Bounded immutable snapshots, connection/world identity checks and dependent action ordering. No Blink/Lag Range position render option. | Intentional lifecycle/ordering adaptation and a visible feature gap. |
| Name Tags | Custom font, local-client/user badge, and a number derived from local-player health. | Native font, actual target distance plus heart glyph, adjustable background opacity; no user/client badge. | Substantial visible content difference. |
| ESP / Target ESP / Ambience | More visual style/alpha/font options, image assets, sky/fog/light-map features. | Native geometry/fonts/shaders, fewer style controls; Ambience only changes time. | Functional visual coverage, not visual/option parity. |

## Common implementation differences

All native modules use Perihelion's settings/config, event phases, activation scopes,
fault cleanup and shared controls. Rotation/slot/timer/input ownership can cause a
lower-priority module to yield. PRE interactions use validated previous submitted
aim; Scaffold retains post-movement preparation with its own PRE placement path.
Packet buffering is bounded and session-scoped and excludes integrated-server,
login/status and Forge proxy traffic. None of these facts establishes acceptance by
a particular server. Donor event buses, module/config managers and singleton state
were not imported.

## Per-module ledger

Rows describe concrete differences found in this pass or established by inspecting
the current native implementation and its earlier port reports. “No additional
difference established” means the shared architecture differences still apply;
it is not a full equivalence claim. Nested modes are represented by their parent.
Sources are under donor `src/main/java/ddlc/yuri/modules/impl/` and native
`forge/src/main/java/perihelion/integration/minecraft189/module/`.

| Native module | Compared behavior / difference |
| --- | --- |
| Aura | Single/Switch/Adaptive, Normal/ML/None aim; additional sort/FOV/manual-input and native Hypixel-block settings. ML presets/data are bounded; donor recording/file manager absent. PRE ownership and projectile conflict handling differ from global flags. |
| Auto Extinguish | Native prior-aim and hotbar scopes, interruption guards and water ray; actions can yield to competing claims. |
| Auto Pot | Health/delay/aim timeout retained; rejects harmful potion mixtures and uses native prior-aim authorization. |
| Auto Projectile | Client/Server swap modes retained; range and throw/recovery guards follow cached newer Yuri rather than release-only behavior. Native PRE actions, bounded pending state and stack/target/claim validation. |
| Back Track | Uses activation-local copied target state, accumulates relative packet deltas, and flushes on target/session changes. Buffers S14/S18 movement for all entities while active, as the donor does; not only the target. |
| Bow Aimbot | Native drag/gravity/motion solver and trajectory obstruction checks; algorithm differs from Yuri's helper. Target filtering reuses native Aura filters. |
| Criticals | Packet/Mospixel/NCP modes retained; explicit position-spoof modes remain non-vanilla packet sequences. No additional difference established this pass. |
| Hit Select | Current nine-setting follow-up model; release's prediction/idle/chance model replaced, not exposed alongside it. |
| Lag Range | Lag/Blink and delay/packet toggles retained; bounded native buffer. Donor Render Lag Pos option absent. |
| Target Strafe | Native Aura target dependency and activation-local orbit state. Sprint-jump boost follows winning server yaw; walking uses orbit yaw. |
| Tick Base | Same burst/repayment band and timer pattern; shared timer request releases when returned to normal and cannot overwrite higher-priority timer ownership. |
| Velocity | Cancel/Custom/Legit/Intave retained. Native Legit has explicit embedded Jump Reset controls, standalone Jump Reset precedence and separate Aura reduction controls. |
| WTap | Donor sprint-reset chance behavior plus an extra timed Forward Tap mode, delay/duration and eligibility controls. |
| Anti Bot | Tab/name/age/latency/FOV checks retained; native target filter rather than donor global bot list. No additional difference established. |
| Auto Swap | Client/Server and Swap Back retained; native server slot participates in hardness/durability/held-tool checks with visible-slot restoration. |
| BedWars Utility | Notifications use native chat; donor Sword/Armor Reveal and Invisibility Status names map to native Swords/Armor/Potion Invis. Game-message heuristics remain server-dependent. |
| Derp | Native model hook handles client yaw and pitch; shared rotation in Server mode. No copied global model angles. |
| Disabler | Implements the donor's sole exposed inventory mode directly; no Mode multiselect because there is only one implementation. Manager's own handshakes are exempt. |
| Friends | Adds persisted native Names text setting and cached normalized lookup; middle-click retained. |
| Input Fixes | Native physical input claims restore held movement after screen closure; clears native click delay. No additional difference established. |
| Fast Place / Yuri ItemDelays | Native ID/name is Fast Place; extra filtering controls, with eggs and snowballs both covered by projectile option. |
| Minigame Aim | Zombie/Halloween, prediction/FOV/item routines retained with native prior-aim actions and render/HUD ownership. Server scoreboard/message assumptions still limit behavior. |
| Timer | Constant/Pulse patterns retained; owned timer arbitration/cleanup and monotonic pulse timing. |
| Flight | Motion/NCP exposed modes retained with native movement/motion hooks. No additional difference established. |
| Long Jump | Fireball Legit/Lag and implicit Mospixel retained; redundant one-option Mospixel Mode selector absent. Mode switches now retire old claims, flush ordered backlog and restart counters. |
| No Slow | Vanilla/NCP only, matching exposed donor modes; commented experimental/Hypixel code is not a selectable feature. NCP now respects item toggles. |
| Phase | Normal/Polar modes retained through native collision/motion hooks. Explicit spoof behavior preserved. No additional difference established. |
| Safe Walk | Edge clamping through native movement hooks; no additional difference established. |
| Speed | Vanilla/Legit/Polar/Intave/NCP/Librecraft exposed modes retained; owned timer/rotation and native mode-change restoration. Server behavior unverified. |
| Sprint | Native held-sprint claim yields in menus/unfocused view and preserves vanilla rules; Cancel Invis retained. |
| Stasis | 45 frozen ticks and one release tick retained; activation-owned packet suppression and motion restoration. |
| Step | Vanilla/NCP retained; restores captured original step height during cleanup. No additional difference established. |
| Water Walk | Vanilla/NCP retained, including donor liquid collision behavior and offsets. No additional difference established. |
| Anti Fireball | Native previous-aim PRE attack; ignores dead/replaced entities and prunes attack history by world ID/identity. |
| Anti Void | Vanilla/NCP responses retained; native world queries and motion hooks. No additional difference established. |
| Auto Armor | Same additive armor/protection selection and drop-old-then-equip behavior; one transaction per delay and native container/cursor guards. |
| Bed Defender | Wool shell, delay/tolerance and movement fix retained. Native spoofed slot, support/ray checks and full-radius loaded-world bed search. Show Progress option absent. |
| Blink | Constant/Pulse, optional incoming traffic retained; bounded ordered immutable snapshots. Retains backlog instead of donor per-tick reset. Render Blink Pos option absent. |
| Breaker | Actual hit face, native controller/hardness and spoofed tool selection; full-radius search repaired. Match-scoped home tracking now persists across toggles and resets at world/match start; fixed-origin, loaded/coherent acquisition and unknown-home waiting differ from Yuri. Progress Bar option absent. Vanilla/Legit are also effectively the same path in this donor revision. |
| Eagle | Delay/block/direction/sneak checks and donor eligibility expression retained; activation-owned sneak claim and physical-input restoration. |
| Fast Break | Same speed/delay concepts; narrow native controller access instead of replacing vanilla classes. No additional difference established. |
| Fast Use | Same selected-item update burst; explicit extra walking packets use current entity angles. Combining spoof modes with silent aim is not an equivalence guarantee. |
| Inv Move | Donor's sole exposed Vanilla behavior implemented directly; no redundant mode selector. Native physical movement claims and cleanup. |
| Manager | Extra protection/drop settings, safe defaults, bounded instant transactions and cached per-action scoring. Role/slot names remain native. |
| No Fall | Vanilla/MLG/Librecraft modes retained; native MLG held-stack/ray/previous-aim validation preserves existing web catches. Explicit spoof modes keep their sequences. |
| Refill | Hotbar swap mode 2 replaces Yuri's mode 4 that drops potions. Auto Close retained. |
| Regen | Threshold and packet burst retained; native motion phase. Extra walking packets use current entity angles, like Fast Use. |
| Scaffold | Native settings, shared controls, support search and bounded geometry; matches newer Telly follow-up. Extra search/validation paths and native count display; not donor global counters/assets. |
| Stealer | Extra smart upgrade/resource policies and capacity checks; resource counts now cached per selection snapshot. Trash toggle and full-inventory retry behavior intentionally repaired. Grab Mouse absent. |
| Ambience | Time/real-time only; donor colored fog/light map and shader sky/modes absent. |
| Auto F5 | Owned perspective respects manual changes and yields to Free Look/Freecam; On Aura/On Scaffold retained. |
| Block ESP | Chest/bed toggles and filled/outline controls retained; incremental loaded-world bed scan instead of unbounded initial volume. Max-range discovery can take multiple ticks. |
| Breadcrumbs | Native bounded fading geometry instead of donor rendering utility; no additional behavior difference established. |
| Camera | Hand transforms/swing slowdown/effect suppression retained; native independent visual modules plus extra nausea and fire-color/alpha/offset controls. |
| Chams | Native player base models/optional tile entities; armor layers are not forced through walls. |
| ESP | Box/Corners and core player/chest/health/held-item views retained. Donor separate health/outline alpha and width, held-item background/custom-font controls absent; native colors/fonts. |
| Free Look | Native held/toggle binding, independent camera state and owned perspective; uses native input and cleanup. |
| Name Protect | Local rendered name replaced with You via native text event; no donor identity/config framework. |
| Name Tags | Actual target distance, native font/heart and opacity; donor local-health number, custom font and client/user badge absent. |
| Shader ESP | Glow/Outline implemented with activation-owned native framebuffer/shader; donor shader framework absent. |
| Skeletons | Native copied pose cache and scoped lines/colors; no donor model manager. No additional behavior difference established. |
| Target ESP | Points/Ghost/Image/Sigma concepts via native geometry; donor Image Mode assets/options absent. |
| Trajectories | Native first block/entity impact simulation; caches a path per tick instead of recomputing every frame. Preview can lag within a tick; physics implementation differs. |

## Retained historical modules and native extras

Yuri v1.3 and the cached follow-up no longer contain **Auto Apple, Auto Chat,
Block In or Fake Lag**; Perihelion retains them from the earlier donor. Auto Chat
starts with an empty registration password. Auto Apple adds an eating timeout;
Block In uses native spoofed slots/strict placement; Fake Lag uses native ordered
buffering. Keeping these is a deliberate feature difference.

Perihelion also has native **Aim Assist, Auto Clicker, Auto Eat, Chest Aura,
Bed Plates, Clutch, Freecam, Zoom, Jump Reset**, independent **Fullbright, No FOV,
No Bob, No Hurt Cam, Anti Blind**, and its own **HUD / ClickGUI**. These are not
missing Yuri ports. Several came from other-client behavioral references.

## Explicitly absent donor modules

**Deferred:** AutoServer, Insults, KeybindInfo, SkinChanger. They have no registered
native equivalents in the existing scope; their absence is not hidden as coverage.

**Presentation exclusions:** DiscordRPC, GUIClicker, ToggleSounds, Animations,
Capes, ChinaHat, ClickGUI, ClientInfo, Effects, Hotbar, ImageRenderer, MediaInfo,
ModList, MotionBlur, Pentagram, PostProcessing, Scoreboard, SessionInfo, TargetHud,
Watermark, YuriChat. Perihelion's own GUI/HUD can cover some related needs but is
not a reproduction of these modules.

Omitted position markers/progress bars and richer visual settings remain separate
from these top-level exclusions: mapping a gameplay module does not mean every
one of its visual options exists. No additional features were added solely to
make the module counts match.

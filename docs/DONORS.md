# Donor references

## Minecraft font rendering (2026-11-14)

Inspected Perihelion's shared GUI/HUD font adapters, then read-only OpenB4 `u20`
(`3d63944cc79be890c013cc79e5ed92d3533b48a5`), especially
`CategoryComponent`, `ModuleComponent`, `SliderComponent`, `InfoHud` and
`MixinFontRenderer`. Ordinary donor text uses Minecraft's current `FontRenderer`,
its measured widths and its native optional shadow; there is no replacement
Minecraft font atlas or font-smoothing algorithm in that path.

Perihelion retains that native resource-pack/Unicode renderer. At menu/HUD scales
of at least one physical pixel per bitmap texel, the Minecraft face rounds its
effective texel size to whole screen pixels and applies the same correction to
width, height, centering and drawing, choosing the smaller size at exact half steps
to retain compact HUD rows. This prevents alternating stroke thickness
under fractional scaling without changing saved menu/HUD scale or layout values.
Subpixel layouts retain their compact text size; their bitmap texels cannot all
occupy whole screen pixels. Other font families keep their existing rasterizers.

GUI shadows now delegate to the native font's shadow pass for Minecraft text,
including formatted colors/reset codes. Shared native drawing preserves caller
framebuffer alignment instead of rounding a second time in local GUI units.
No donor source, assets, managers or Mixins were copied. The packaged font probe
compares rendered regular/bold, formatted, Unicode and shadowed pixels against
vanilla at whole-pixel sizes across eight effective scales. Menu/HUD font paths
use Minecraft's exact GUI scale rather than estimating it from rounded window
dimensions, so tight labels retain their full measured width. Arbitrary replacement
resource packs and other GPUs remain unverified.

Validation: `check assemble --offline` passed 621 tests. Packaged Forge (854×480)
and OptiFine M5 (1280×720) UI runs passed, including 64 native pixel/metric
comparisons each, font/weight selection, layout/config persistence, controls,
resize recovery, world reload and clean shutdown. Captures were visually inspected;
the Minecraft Elements label fits its unchanged toolbar button.

## Inv Move GUI option (2026-10-22)

Inspected Perihelion's module/shared controls first, then Yuri's unchanged read-only
`modules/impl/movement/InvMoveModule.java` at
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def`. Yuri explicitly returns for
`GuiContainerCreative`, so the reported creative-inventory exclusion is expected
and remains unchanged. Its ordinary-GUI physical input and arrow rotation were
missing from the native module. Added **Allow in GUIs** (`allow-in-guis`), off by
default to preserve existing behavior. When enabled, other screens use the native
activation-owned movement/jump/sneak/rotation claims; chat and creative inventory
remain excluded. Container mode switches, transaction queues, motion scaling and
jump restrictions retain their container scope. No donor framework was imported,
and singleplayer pause screens retain vanilla pausing. Targeted transformed-client
regressions cover GUI opt-in/out, container settings, creative/chat exclusions,
input release and activation cleanup.

Validation: `check assemble --offline` passed (511 tests), and the packaged Forge
world smoke passed. `scripts/launch-smoke --scenario invmove` passed 21 focused
checks on both Forge and OptiFine M5. The broader OptiFine world run stopped at
`GuiFontProbe`'s native-glyph assertion before reaching gameplay; no UI change was
made for that failure. These are local integrated-world checks.

## UniversalAccountManager — 2026-10-18 / 2026-10-19

Behavior reference: [sc6the/UniversalAccountManager](https://github.com/sc6the/UniversalAccountManager),
revision `9e9b2479af877d90a4618bb4bb685009e19d789a`, cloned at the user's request
into `../alt mannges/UniversalAccountManager/` and kept unchanged/read-only.
Perihelion's runtime, menus, theme, rendering, config and threading were inspected
before the donor's auth, account-list, cookie, store, profile, skin and observed-ban
paths. No donor builds, scripts or bundled JARs were executed. No license file was
present; no donor source, assets, binaries or framework were redistributed.

Reimplemented the account list and editing/undo/validation flows; both Microsoft
OAuth clients, device/browser sign-in, token/cookie/offline/launcher imports;
profile name/skin tools and favorites/default/local override; observed Hypixel
ban duration; Localts, Nicealts and Fernan catalogues, purchase confirmations,
delivery recovery/import selection, durable imported/dead-item tracking,
history scans and provider-specific tools. The native runtime owns bounded
serialized work and cleanup, client-thread session writes, an encrypted account
vault separate from shared presets, menu hooks and textures. No donor event bus,
manager, config system, global client or UI theme was imported.

The account UI follows Perihelion/Adjust preferences with a quiet header, a single
padded account list, three primary actions and secondary menus. Native fields,
font metrics, theme colors/opacity/scale and close-time input cleanup are retained.
See [features, deliberate adaptations, rollback and evidence](ACCOUNTS_2026-10-18.md).
Authenticated providers, purchases/refunds, remote profile mutation and live
Hypixel behavior remain separate from synthetic protocol and native UI checks.

## Yuri recovery and interaction check (2026-10-18)

Inspected native recovery/item/Aura paths first, then unchanged read-only Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def`. Corrected Auto Pot's staged smooth
aim, Traditional movement correction, deferred slot selection and Aura handoff;
Auto Extinguish's five/two wait stages, Scaffold/liquid exclusions and movement;
and MLG smooth catch/return-to-view state and live-mode cleanup. Shared water/web
actions now ray trace along their submitted aim. Perihelion scopes, settings,
packet ownership and UI remain native; donor source/frameworks were not changed.
See the [recovery check](YURI_131_RECOVERY_CHECK_2026-10-18.md) for current
artifact/runtime evidence and explicit remaining differences, including Anti
Fireball policy. Exact two-client equivalence and live bypass acceptance remain
unverified.

## Yuri final boundary/source check (2026-10-18)

Inspected native helpers before the unchanged Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def`. Corrected Tick Base cooldown/float
boundaries; Lag Range's POST arming, delay and one-shot spoof lease; Criticals mode
clock; Derp smoothing/persistent yaw; Input Fixes Sneak/held state; Target Strafe
jump yaw; Eagle release sampling/latch; Phase collision/ground queries; Water Walk
footprint; and Timer pulse-clock persistence. Native scopes, controls and packet
ownership remain. No donor architecture was imported or donor source changed.
See the [final check](YURI_131_FINAL_CHECK_2026-10-18.md) for evidence, complete
inventory accounting and explicit remaining gaps; exact runtime equivalence and
live server acceptance are not claimed.


## Yuri 1.3.1 deeper movement and packet check (2026-10-18)

Compared native modules first, then the unchanged pinned Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def`. Added native player-owned
PRE/current ground history for Speed/Step/Long Jump; corrected event acceleration,
diagonal/analog behavior, NCP Flight residual motion, Librecraft rotation/Aura/jump
conditions, NCP correction pause progress, web jump exclusions and immediate
Stasis packet suppression. Native ownership, lifecycle, UI and bounded packet
queues remain. The [fourth check](YURI_131_FOURTH_CHECK_2026-10-18.md) records
selected regressions, artifact evidence and explicit retained differences; it does
not establish exact donor equivalence or live server acceptance. No donor source
or framework was changed/imported.


## Supplied Yuri config presets (2026-10-18)

Converted the user's `my config.json` into the ordinary `hypxile` preset, matching
50 modules and 225 settings against Perihelion's existing GUI controls. Inspected
native definitions before consulting the unchanged Yuri reference at
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def` for names and changed controls. Added
`default` from untouched native definitions and one-time installation receipts so
both presets can be edited/deleted normally. No module implementations, setting
definitions, donor files or donor configuration architecture were changed.
See [mapping, omissions and validation](HYPXILE_CONFIG.md).

## Yuri 1.3.1 third parity check (2026-10-18)

Rechecked less-covered inventory, activation-toggle, cross-module and packet
paths against unchanged read-only `1cb86ad94ce1f01aa9b715577163c8ecc47b7def` after a
verified 644-file snapshot. Refill now closes a full hotbar independently of its
click cooldown and retains the timestamp across toggles. Auto Swap's mining path
yields to the native Auto Projectile busy supplier. Added real inventory-transfer
and actual projectile/mining regressions. The TCP smoke helper also keeps original
integrated-server keepalives on their original connection, with a forced routing
check; production FIFO logic remains unchanged. See the [third check](YURI_131_THIRD_CHECK_2026-10-18.md)
for evidence and explicit native differences, including retained slider increments
and cursor/container guards. No donor framework or global architecture was added.

## Yuri 1.3.1 second parity check (2026-10-18)

Rechecked native behavior against the same read-only `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`
reference after a new verified 641-file snapshot. Corrected Intave Velocity's
mode-specific fire/liquid/web conditions and moved chest Blink collection after
Inv Move's midair/speed-potion eligibility checks. Existing native rotation,
queue, session, lifecycle and UI policies remain. Added transformed attack and
actual localhost TCP capture/flush regressions. See the [second check](YURI_131_SECOND_CHECK_2026-10-18.md)
for source comparisons, final evidence and remaining differences; this does not
claim exhaustive runtime equivalence or public-server bypass acceptance.

## Yuri 1.3.1 rotation, timing and packet audit (2026-10-17)

Rechecked native code against the unchanged read-only reference
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def` after a verified 638-file snapshot.
Corrected Aura's due-tick cooldown reset, first configured delay and own Hypixel
release classification; moved Hit Select to client tick START; and repaired Back
Track's live legacy/timed-policy transition so an infinite-deadline packet cannot
strand the queue. That last change deliberately fixes an edge case in the donor.
Native submitted-rotation identity, FIFO snapshots, session checks and UI remain.

Added actual Displace/Aura handoff checks and serialized localhost TCP cases for
Hypixel release/attack order, foreign block traffic, foreign dig traffic during
release, and Back Track policy transitions. See the [follow-up audit](YURI_131_AUDIT_2026-10-17.md)
and its evidence report for final validation and limits. Source comparison and
local probes do not establish exact two-client parity or server acceptance.

## Yuri 1.3.1 module update (2026-10-17)

Inspected native implementations first and compared the refreshed read-only Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def` with the prior audited
`50d7322b85cb8de096241077ca3130e9e9715d20`. A 572-file source backup was
hash-verified before edits. Updated native gameplay behavior, added the current
ghost helpers/Displace/Armor Ratio/head cosmetics/local minigames, and adapted
source math, simulation and state-machine code under the current donor's AGPLv3
terms. The donor checkout is unchanged; no donor registry, event bus, config
system or global client architecture was copied. Native activation cleanup,
shared control arbitration and submitted-rotation identity remain authoritative.

The [complete inventory](yuri-1.3.1-modules.json) records all 102 definitions and
remaining presentation exclusions/deferred features. [Implementation and limits](YURI_131_IMPLEMENTATION.md)
records settings, timing/rotation changes, native differences, rollback and the
verification status. Source coverage is not a claim of identical two-client
runtime behavior or public-server bypass acceptance.

## OpenB4 implementation review (2026-10-23)

Rechecked the functional ports after a reported Silent Aim Assist Grim Simulation
flag. Corrected tick-versus-render aim ownership, continuous sensitivity-rounded
yaw, source eight-direction input mapping, queued click ownership, buffer capture
attribution, mining/tool state, NoSlow send/slot/air history and fireball
phase/lifecycle behavior. Visible mode
name is now Silent; saved tokens remain compatible. The native post-input event
and narrowly bounded prior-angle motion action preserve native control/world
ownership without importing donor architecture. See
[review findings and verification](OPENB4_REVIEW_2026-10-23.md). Live anti-cheat
acceptance is unverified.

## OpenB4 functional improvements (2026-10-22)

Adapted selected gameplay differences from read-only OpenB4 `u20`, revision
`3d63944cc79be890c013cc79e5ed92d3533b48a5`, into existing native modules.
The module registry and prior default behaviors remain. Compact opt-in modes cover
manual combat/clicking, predictive edge behavior, mining progress, replenishment,
water recovery, contextual packet release, item-use movement, reactive fireball jumping
and BedWars information/shop operations. No donor global architecture, unbounded
transport, assets, scripting loader or unsafe AutoRequeue authority path was imported.

[Implementation, settings, exact selected policies, exclusions and verification](OPENB4_GAMEPLAY_2026-10-22.md).

## OpenB4 reference onboarding (2026-10-17)

Added [OpenB4](https://codeberg.org/abusez/OpenB4) alongside Yuri and the other
read-only references at `donor_clients/OpenB4/` from the workspace root.
The checkout is detached at `3d63944cc79be890c013cc79e5ed92d3533b48a5`
(upstream `u20`); its source project is the nested `OpenB4/` directory.
This adds a behavior reference for future native ports. No B4 modules, donor
architecture, assets or dependencies were added to Perihelion, and no client
was built, launched or installed. No license file was present in this revision;
do not assume the recovered source or assets permit redistribution.

An offline source security review found no embedded RAT, automatic token theft,
webhook exfiltration or hidden downloader in the reviewed code, but identified
two security defects:

- **Script safety filter bypass:** imported Java can reach unrestricted OS
  command execution. Scripts are compiled and instantiated during reload,
  including the first in-world tick, before their modules are enabled. Treat
  third-party scripts as executable code; do not reuse this loader as a sandbox.
- **AutoRequeue command confusion:** a connected server can supply a chat
  `RUN_COMMAND` value that AutoRequeue sends through local chat-command dispatch.
  With AutoRequeue and Chat Commands enabled, this can toggle modules or rename
  the selected profile without a click. Future ports must validate the expected
  server and queue-command grammar and preserve local command authority.

The sole bundled JAR, `OpenB4/libs/mixin-0.7.11-SNAPSHOT.jar`, has SHA-256
`da3d6e47b9c12b5a312d89b67bc27e2429d823c09cde8a90299e9fdcc4eefc20`,
matching Perihelion's existing pinned Mixin runtime. ZIP/class metadata and
signature verification were inspected; the signature check warned about the
timestamp certificate chain and expired timestamp. Source and binary metadata
inspection are not antivirus clearance: no signature-based antivirus engine
was installed, and no dynamic malware execution or resolved dependency/CVE
scan was performed. The build uses a dynamic Loom version selector, so later
build downloads are not covered by this source pin. Donor source stays unchanged.

The completed [security report](/Users/wmcul/.codex/state/plugins/codex-security/scans/OpenB4/3d63944cc79be890c013cc79e5ed92d3533b48a5_20261017T133534Z_pn1sjg5r/report.md)
records two medium-severity findings and partial dependency assurance. All 220
Java sources were reviewed. The workbench warns that its initial snapshot changed:
scan registration occurred before materializing the pinned checkout. The source
review used the final clean checkout at the exact revision above, not another
revision or edited donor source.

## Breaker match-scoped home-bed whitelist (2026-10-13)

Inspected native registry/lifecycle/events/controls and the existing bed scanner,
then read Yuri v1.3.0 `6f5603e0c0eed253741bacdcd4135830c916602e`
`BreakerWhitelistManager`, `BreakerModule` and their packet/bed definitions.
Reimplemented independent match observation through a runtime-owned native
EventOwner and bounded loaded-world scanning. A fixed spawn origin retries late
bed discovery without recentering at an enemy. Captured home/cover protection
persists across module toggles and block loss, resets on world change or the
recognized system announcement, and gates pending PRE digging on match identity.

The native implementation requires coherent bed halves and waits while Whitelist
has no home classification. It filters normal player chat/action bars and duplicate
start messages. These are deliberate differences from the donor's unbounded
first-match scan and unknown-home behavior. No donor framework/static manager,
config or repository was copied or edited. See [behavior, limits and recovery](BREAKER_WHITELIST_2026-10-13.md)
and [verification](BREAKER_WHITELIST_VERIFICATION_2026-10-13.json).

## Bug/performance review and module comparison (2026-10-13)

Inspected native module families, shared controls, inventory policies, event dispatch
and packet scopes before comparing the unchanged Yuri v1.3.0 checkout
`6f5603e0c0eed253741bacdcd4135830c916602e` and hash-verified cached follow-up
`50d7322b85cb8de096241077ca3130e9e9715d20`. Fixed Long Jump's stale claims/counters
on live mode changes, made NCP No Slow honor its item toggles, rejected dead/stale
Anti Fireball targets, and restored full vertical bed-search range while pruning
distant/unloaded candidates. Inventory resource counting and hot-path allocations
were reduced without caching across transactions; fireball history now uses world
identity lookup. No donor architecture or repository files were copied or edited.

No Slow's disabled-category packet fix intentionally differs from the donor's
unconditional release behavior. Native action ownership, actual hit faces, packet
scope limits and cleanup remain authoritative. The comparison explicitly records
Breaker whitelist differences, omitted sub-options, older retained features and
the four deferred modules. This run does not claim current-upstream, complete
behavioral parity, FPS improvement or public-server bypass proof.

See [review/fixes](BUG_PERFORMANCE_REVIEW_2026-10-13.md),
[module differences](YURI_MODULE_COMPARISON_2026-10-13.md) and
[verification evidence](BUG_PERFORMANCE_VERIFICATION_2026-10-13.json).

## Adjust default appearance comparison (2026-10-12)

Rechecked Perihelion's existing menu, preset browser, HUD typography, player table and popup against all seven read-only screenshots in the workspace's `adjust_visuals/` folder and the user's attached player-menu image. A read-only subagent compared successive native captures, identifying excessive column width, equal wrapped-row clipping, heavy optional demo styling and oversized utility controls. The native defaults now use regular gray text, compact blue headers, smaller glyphs and controls, a peer Configuration list and short-category stacks. Section colors, selection colors and weights require the off-by-default Custom text style toggle. The player menu keeps the existing native actions and data availability while bringing its portrait, heading and row proportions closer to the reference.

This is visual reimplementation through existing settings, layout, font caches and scoped rendering. The seven native categories and current module classification are preserved. No Adjust font or source was supplied, and no donor code, framework or assets were copied. All reference folders remain read-only. See [comparison and verification](ADJUST_FIDELITY_2026-10-12.md).

## Cross-module PRE interactions and rotation movement (2026-10-12)

Inspected Perihelion's interaction emitters, native input/walking hooks, placement
and hotbar scopes before comparing read-only Yuri v1.3.0
`6f5603e0c0eed253741bacdcd4135830c916602e`: `BreakerModule`,
`BedDefenderModule`, `AntiFireballModule`, `AutoProjectileModule`, `AutoPotModule`,
`MLGNoFall`, `LongJumpModule` and `MinigameAimModule`. Their pre-update actions
informed the phase audit. Also inspected Arsenic
`d6f3a3d40a6d3f14826a42002abf343c61c46ecb` `AutoBlockIn`, ghost `Clutch` and
world `Clutch` for live ray/face validation and placement lifecycle differences.

Added a native `BEFORE_ACTIONS` phase after Perihelion collects and applies all
input/rotation/slot claims, before vanilla input movement and walking. Migrated
the remaining post-walking attack, dig, placement and item-use routines to this
phase; Scaffold's existing PRE placement stays intact. A shared scoped helper
uses the actual previous submitted aim, checks the native owner/position/session,
and preserves callback cleanup or replacement values. Native ray checks and
controller/hotbar scopes remain authoritative. Breaker uses the actual hit face;
it does not copy the donor's constant UP face. Shared analog movement correction
and server-yaw sprint jumps now cover the other silent aiming tools. Target
Strafe retains its orbit direction while its jump boost follows the winning yaw.

No donor event bus, module/config manager or global architecture was copied.
Donor checkouts remain unchanged. Rates, targeting settings, tool selection and
activation cleanup remain native. Explicit packet/movement-spoof modes keep
their existing semantics; PRE ordering does not establish compatibility with
every anti-cheat. See the [module ledger and verification](MODULE_ACTION_ORDER_2026-10-12.md).

## Aura PRE action order and Silent sprint jumps (2026-10-12)

Inspected the native Aura, hit-slowdown, input/walking hooks and submitted-rotation
ownership first. Compared read-only Yuri v1.3.0
`6f5603e0c0eed253741bacdcd4135830c916602e` `AuraModule.onPreUpdate`,
`EntityPlayerSP.onUpdate` and `RotationManager.onJump`; current upstream Aura at
`542c4f77d9788af4a75e2471a1a1d4b7f2d10b11` still attacks in pre-update.
Native Aura now performs clicks/attacks before input/movement, using Perihelion's
identity/position/world-checked previous rotation and scoped cleanup. Silent
sprint-jump acceleration now follows the winning server yaw, matching Yuri's
Normal movement fix. Default Fake blocking, settings, rates and reach are retained.
No donor framework, global manager or source assets were copied. Local donors
remain unchanged. Grim's primary Post/Simulation sources informed the symptom
diagnosis; local verification is distinct from live Grim-server proof.
See [diagnosis, differences and verification](AURA_ORDER_2026-10-12.md).

## Unified GUI, Configs, HUD editor and fonts (2026-10-12)

Inspected Perihelion's native menu, preset browser, HUD editor, atlas/caret and renderer paths first. The user's local Adjust screenshots supply compact floating panels, muted-blue category headers, translucent neutral surfaces, small controls and purple/salmon HUD accents. Arsenic `d6f3a3d40a6d3f14826a42002abf343c61c46ecb` `ConfigsComponent`/`Theme` and Project-Coolware `29d1e0974a49f81e829ab12ecbeada0c145f0d1a` `ThemeModule` were read as theme/font ownership references.

Reused native preferences, module metadata, config operations, HUD snapshots, scoped GL state and font ownership. Added one shared chrome helper, compact preset/editor layouts and selected-font rendering across Modules, Configs and HUD/editor/chat. Current Sans, Minecraft and Monospace use the existing font preference ID; legacy Serif remains compatible. Long labels are measured and truncated, Unicode fallback is per unsupported codepoint, and themed text entry/caret geometry follows the selected face. No donor architecture, cloud config service, font assets or dependencies were copied. Donor repositories remain read-only and unchanged. [Implementation, visual review and verification](UI_THEME_2026-10-12.md).

## Chat HUD interaction and player board menus (2026-10-12)

Inspected the native HUD renderer/editor, config settings and Forge GUI input path
first. Arsenic `d6f3a3d40a6d3f14826a42002abf343c61c46ecb` `visual/HUD.java`
explicitly retains its overlay in `GuiChat`; its editor supplied a read-only check
of drag offsets. The user's Adjust screenshots supplied player-table headings,
including health, team, statistics, ping and seen/time columns. No source or
assets were copied.

Chat now draws the enabled HUD through the native Forge GUI draw contract, above
the chat screen, with activation-owned pointer/menu state. Dragging commits through
the existing normalized settings. Player-row right-click offers native targeted
Freecam, Hypixel's report menu, pinning, official API stats and local player details.
Column choices remain native HUD settings in the GUI. Project-Coolware
`29d1e0974a49f81e829ab12ecbeada0c145f0d1a`'s
`render/Freecam.java` was inspected for detached-camera input/cleanup behavior;
Perihelion keeps its own camera entity, shared-control suppression and ownership.
The official Hypixel API v2 and report support article supply the external contracts;
no donor API helpers, event bus, module manager or config system were imported.
Missing server statistics stay unavailable, and locally observed time is labeled
accurately. Donor repositories remain unchanged. See
[implementation and verification](HUD_CHAT_2026-10-12.md).

## Velocity, Jump Reset and module flow (2026-10-11)

Inspected the native combat/input/settings paths first, then Yuri's current
`ae94d7b70ce8735b0337d01afbbe567d5a1de714` Velocity definitions and Legit/Intave
implementations, Arsenic's read-only `d6f3a3d40a6d3f14826a42002abf343c61c46ecb`
`ghost/JumpReset.java`, and the OpenMyau-Plus/Project-Coolware velocity alternatives.
Legit combines a sprint jump with Aura attacks; it is not an independent jump-only
module. Added native Jump Reset following the movement-input/nearby-player behavior
of the local Arsenic reference, with bounded one-input state, typed settings,
activation-owned controls/observers, lifecycle cleanup and movement conflict guards.

Made embedded jump and Aura reduction settings explicit, retained saved setting
IDs, corrected fire/web and setting-transition guards, and routed PRE reduction
through the previous submitted Aura rotation. ClickGUI now searches module settings
and modes, clears a search with Escape, and retires editors when their settings hide.
No donor files or global architecture were changed or imported. Current upstream
Arsenic uses a different pre-velocity motion technique; it was examined but not
ported. Local input/packet checks do not establish remote anti-cheat acceptance.
[Behavior, evidence and rollback](COMBAT_FLOW_AUDIT_2026-10-11.md).

## Scaffold PRE placement timing (2026-10-11)

The user's test-server screenshot reported placement on POST. Inspection confirmed
Scaffold committed in native `AFTER_MOTION`; read-only Yuri commits from its earlier
`PreUpdateEvent`. Moved placement to the next native `BEFORE_INPUT`, using the exact
preceding submitted aim, native slot transactions and fresh world/inventory/ray
checks. Added scoped prior-walking authorization and correction invalidation;
no donor manager, event bus or global architecture was copied. Donor files remain
unchanged. [Implementation and evidence](SCAFFOLD_PRE_TIMING_2026-10-11.md) records
the packet ordering, stale/canceled cases, terrain checks and remaining server limits.

## Latest Yuri main comparison (2026-10-11)

Compared the full inventory and release-to-main delta against upstream
`50d7322b85cb8de096241077ca3130e9e9715d20`, including the Scaffold fix
published during the audit. Corrected Hit Select, Auto Projectile, Aura's packet
window, Stealer armor acceptance, Scaffold Telly/strict swing, Derp model pitch,
Halloween interactions and Skeletons pose retention through native lifecycle,
settings, controls and rendering hooks. The donor checkout remains read-only;
the verified reference snapshot is excluded from the build.

[Audit and limits](YURI_LATEST_AUDIT_2026-10-11.md) records the corrections,
all 88 module classifications, verification and rollback. Coverage and local
packet checks remain separate from identical-source or public-server bypass
claims. Earlier presentation exclusions and four deferred features remain
explicit. No donor client architecture was imported into the implementation.

## Compatibility and quality of life (2026-10-11)

Implemented the requested packet-buffer compatibility, expanded Scaffold terrain
checks, measured search optimization, Auto F5 and Name Protect through native
activation ownership, shared controls and rendering hooks. Read-only references
are Yuri v1.3.0 `6f5603e0c0eed253741bacdcd4135830c916602e`, especially
`render/AutoF5Module.java`, `render/NameProtectModule.java`, its vanilla/custom
font hooks, and the Scaffold module / search helpers. Existing native buffering,
walking authorization, camera restoration and font paths were inspected first.

Auto F5 retains the donor triggers and defaults with native camera arbitration.
Name Protect keeps the donor's literal local-name-to-`You` transformation across
drawing and measurement. The Scaffold duplicate-ray pruning preserves its first
match; the rounded-angle and temporary fence-sneak fixes address failures found
in the expanded native fixture. Donor repositories were not modified, and no
donor client architecture was imported. See
[behavior and verification](COMPATIBILITY_QOL_2026-10-11.md).

## Yuri 1.3 behavior updates (2026-10-10)

Implemented Breaker tool scoring/Auto Tool, Scaffold Hypixel and revised Telly
rotations/timing/model pose/tower guard, Aura Legit and optimized Hypixel blocking,
AutoSwap persistence, Legit Speed movement-facing rotations, FreeLook Hold and
camera interpolation, and BlockESP joined bounds/render controls. Inspected native
architecture first and used activation-owned requests, native events, walking
packet validation and conditional restoration throughout.

Comparison source is Yuri v1.3.0 `6f5603e0c0eed253741bacdcd4135830c916602e`.
The user selected Aura's immediate upstream fix at
`c4d18a89b857139974bb0aac6dfdae6eeec08c61`. Current donor source is AGPL-3.0;
the existing MIT-era provenance below is preserved. Donor repositories stayed
read-only during implementation. No donor module manager, event bus, config system,
global rotation manager, assets or binaries were imported.

[Implementation and verification](YURI_1_3_IMPLEMENTATION_2026-10-10.md) records
behavior details, native timing/lifecycle adaptations, verification limits and
rollback. The historical inventory is now revision-aware; the separate 1.3 ledger
identifies optional/unrequested additions as deferred.

## Yuri 1.3 reference refresh and review (2026-10-10)

At the user's explicit request, refreshed the existing `donor_clients/Yuri`
working tree from `0a4bd400784ef529d238311de5ce867e815794e2` to the exact
[v1.3.0 release](https://github.com/unleg1t/Yuri/releases/tag/v1.3.0),
`6f5603e0c0eed253741bacdcd4135830c916602e`. This checkout replacement is the
authorized exception to the workspace's read-only donor rule; no donor source was
hand-edited and no donor commit was created.

Inspected current Perihelion modules/controls before triaging changed donor modules.
[The review](YURI_1_3_REVIEW_2026-10-10.md) records native follow-up priorities,
complete change inventories, a quick source/dependency security check and the
old live-donor inventory script's expected revision mismatch. No Perihelion code,
existing mappings or donor assets were imported or changed. Upstream LICENSE now
contains AGPL-3.0 and adds a branding notice; earlier implementation/data attribution
below remains tied to the old MIT revision.

## Rotation correctness audit (2026-10-10)

Inspected Perihelion's shared controls, walking/input hooks, item transactions and
all rotation consumers first. The read-only
[Yuri checkout](https://github.com/unleg1t/Yuri/tree/0a4bd400784ef529d238311de5ce867e815794e2)
at `0a4bd400784ef529d238311de5ce867e815794e2` supplied behavioral comparisons:
`managers/impl/RotationManager.java`, `utils/player/MoveUtils.java`,
`utils/player/RotationUtils.java`, `modules/impl/player/ScaffoldModule.java`,
`modules/impl/player/nofall/impl/MLGNoFall.java`, and
`modules/impl/player/AntiFireballModule.java`, under `src/main/java/ddlc/yuri/`.

Retained atomic aim, camera-preserving server rotation, movement correction,
Scaffold modes/searches/speed controls, MLG water-before-web selection and its
2.9 fall-distance threshold, existing-web protection, and Anti Fireball's range
and throttle. Adaptations address native hook timing: movement correction consumes
scaled input, Scaffold's final plan uses the position after movement, rotated
actions match the walking owner/value, and local bucket/block prediction runs with
the submitted angle. Anti Fireball acts after walking instead of before it.
Renderer-scoped model angles preserve the local camera. No donor global LookEvent,
rotation manager, framework, settings/config system or renderer architecture was
copied. All new state belongs to native activation controls/the existing adapter;
donor files remained unchanged. See [the audit](ROTATIONS_AUDIT_2026-10-10.md) for
tested behavior, consumer coverage, remaining transmission limits and rollback.

## Rendering helpers (2026-09-21)

After inspecting Perihelion's render bridge and extension boundaries, the read-only
Yuri checkout at `0a4bd400784ef529d238311de5ce867e815794e2` was consulted:
`utils/render/RenderUtils.java`, `utils/render/ESPUtils.java` (under
`src/main/java/ddlc/yuri/`) and its 1.8.9 `GlStateManager`/`FontRenderer` sources.
The relevant needs were depth/blend/texture/line-width cleanup, reusable query
buffers, and font alpha behavior. API availability was independently checked against
Perihelion's pinned mapped Minecraft dependency and LWJGL documentation.

The scoped snapshots, cache synchronization, tests and native-font wrapper are new
Perihelion implementations. No donor renderer, custom font atlas, event framework
or module was copied; no donor files were changed. Yuri's MIT attribution and
reference URL are recorded below. See [rendering helpers](RENDERING.md) for the
supported state boundary rather than treating donor rendering defaults as guarantees.

## Shared controls (2026-09-21)

Perihelion's lifecycle, ownership, events and client-thread queue were inspected
before consulting the read-only [Yuri repository](https://github.com/unleg1t/Yuri/tree/0a4bd400784ef529d238311de5ce867e815794e2),
commit `0a4bd400784ef529d238311de5ce867e815794e2`. These sources supplied requirements:

- `managers/impl/SlotManager.java`: visible/server item selection and restoration.
- `managers/impl/RotationManager.java`: local-view versus server rotation needs.
- `modules/impl/movement/SprintModule.java` and `modules/impl/combat/WTapModule.java`: held input versus actual sprint suppression.
- `modules/impl/player/ScaffoldModule.java`: concurrent rotation, slot, sprint and timer use.
- `modules/impl/player/guimove/NormalInvMoveMode.java`: physical polling with a screen open.
- `modules/impl/player/BreakerModule.java`: temporary slots for item-dependent actions.

Paths are relative to `src/main/java/ddlc/yuri/`. Yuri is MIT licensed, Copyright
(c) 2026 Unlegit Inc.; see its `LICENSE` at that revision. No source or substantial
code portions were copied. Perihelion reimplements these needs using its existing
activation owners, cleanup stacks and world generations, a small deterministic
arbiter and narrow 1.8.9 hooks. No donor framework, packet behavior mode or complete
module was imported, and no donor repository was modified.

## First native modules (2026-09-25)

Inspected Perihelion's composition root, lifecycle, owned listeners, typed settings,
shared controls, fault boundaries and smoke fixtures before the donor implementations.
Primary reference: [Yuri](https://github.com/unleg1t/Yuri/tree/0a4bd400784ef529d238311de5ce867e815794e2),
commit `0a4bd400784ef529d238311de5ce867e815794e2`. Paths below are relative to
`src/main/java/`. These are independent implementations, with no donor framework,
manager, configuration system or unrelated utility copied. Donor checkouts were read-only.

| Native module | Donor reference and retained behavior | Deliberate differences |
| --- | --- | --- |
| Fullbright | `ddlc/yuri/modules/impl/render/CameraModule.java`: Full Bright uses gamma 100. | Override the renderer's gamma read. Equivalent vanilla lightmap output without writing/saving gamma 100 to user options; disable/fault restores normal reads immediately. |
| Sprint | `ddlc/yuri/modules/impl/movement/SprintModule.java`: hold sprint; Cancel Invis defaults false. | Activation-owned priority-10 input request; yields with a GUI, lost game focus or excluded invisibility, without modifying physical key state. Vanilla movement eligibility still applies. |
| NoFOV | No equivalent found in Yuri. Project-Coolware's `com/github/cooldood/modules/impl/render/NoFOV.java` and `mixins/net/minecraft/client/entity/AbstractClientPlayerMixin.java` establish a dynamic FOV multiplier of 1. | New owned event hook, scoped to the local player and disabled by default. User FOV, water/death camera effects and OptiFine zoom remain independent. |
| WTap | `ddlc/yuri/modules/impl/combat/WTapModule.java`, Yuri `net/minecraft/client/multiplayer/PlayerControllerMP.java` attack timing: hurtTime >= 6, sprint/key eligibility, chance at both attack and reset update (default 100%). | Activation-local pending state; priority-100 sprint/key suppression for one tick outranks Sprint, then releases. Ineligible attacks, zero chance, GUI, disable and world changes clear stale work. Does not copy Yuri's stale unsprint flag or mutate the physical key. |
| NoBob | No equivalent found in Yuri; inspected vanilla/Yuri `EntityRenderer.setupViewBobbing`. | New owned hook cancels local bobbing (view and hand) while preserving the saved View Bobbing option. |
| NoHurtCam | Yuri Camera's No Hurt Camera and `EntityRenderer.hurtCameraEffect`: skip local hurt/death tilt. | Owned cancellable event; other viewed entities retain their camera behavior. |
| Timer | `ddlc/yuri/modules/impl/misc/TimerModule.java`, `utils/client/TimerUtils.java`: Constant/Pulse, both speeds default 1, 0.1 steps, maximum 5; second speed for <100 ms, first through 200 ms, hold previous speed on reset update. | Positive minimum 0.1 instead of freezing at 0; monotonic clock; fresh activation/constant-to-pulse window; shared absolute priority-10 request and baseline restoration. Pre-input phase, with tick fallback while riding. |
| AntiBlind | Yuri Camera's No Blindness and `EntityRenderer.setupFog`. | Skip only visual blindness queries in fog distance AND fog color. Preserve the potion and normal fog setup instead of Yuri's early return with stale GL fog. Does not remove nausea, fire overlay or other effects. |
| FastPlace | `ddlc/yuri/modules/impl/misc/ItemDelaysModule.java`: pre-motion clamp of right-click delay, default 1, range 0–3; Allow Blocks/Allow Projectiles true; blocks, snowballs, eggs. | Parenthesize projectile eligibility so eggs respect the toggle. GUI/null-hand guards. No persistent override: vanilla continues decrementing/resetting the cooldown after disable. |

Secondary comparisons (behavior review only):

- Project-Coolware `29d1e0974a49f81e829ab12ecbeada0c145f0d1a`, NoFOV module/hook above.
  No license file was found at the checkout root; no source was copied.
- Arsenic `d6f3a3d40a6d3f14826a42002abf343c61c46ecb`,
  `arsenic/module/impl/visual/FullBright.java`, `NoHurtCam.java`. Its integer gamma
  capture and potion-removal approach were not adopted.
- OpenMyau-Plus `52c8492c68ed14a0dd6137d0c21f912c463af8d9`,
  `myau/module/modules/Wtap.java`, `FastPlace.java`. Its forward-input suppression,
  delay/duration timing and placement filters change the requested Yuri behavior;
  those modes and supporting managers were not imported.

Yuri is MIT licensed, Copyright (c) 2026 Unlegit Inc. A courtesy copy of its full
notice ships in `META-INF/licenses/Yuri.txt`. Perihelion uses only its existing
module registry, event bus, settings/config, activation cleanup and shared controls.
New infrastructure is limited to typed attack/visual events and narrow Minecraft
hooks/access to the transient item cooldown. All nine modules are world-scoped and
start disabled/unbound. See [module usage](MODULES.md) and [verification](VERIFICATION.md).


## Panel ClickGUI (2026-09-25)

The user-provided Adjust Client screenshot supplied visual direction: compact rectangular
category panels, dark translucent backgrounds and restrained header accents. No Adjust
code, assets or fonts were supplied or copied. The GUI is an independent implementation
using Perihelion's module registry, setting metadata, safe mutations, fault information,
input manager, existing config writer and rendering scopes. No donor GUI framework or
manager was imported, and donor repositories remained unchanged. The fonts are the
Minecraft renderer and cached JRE logical fonts; no third-party font file is redistributed.
See [ClickGUI](CLICKGUI.md) for implementation boundaries and controls.


### Compact visual revision (2026-09-25)

The same user-provided Adjust screenshot now guides centered blue category headers,
narrow panels with small gaps, smaller text and muted rows. Removed the branding,
Panels menu, per-panel hide buttons and toolbar reset button. Reset remains a keyboard
shortcut/local command. No donor assets or code were copied or changed.


### Readability revision (2026-09-25)

The additional user-provided client screenshots highlighted cramped setting names and
intrusive help text. The panel frontend now uses wider compact columns, complete labels,
short description-only tooltips after a short delay and a Client setting to hide them.
Empty categories use just their header. This remains Perihelion-native UI work; no donor
source, font or asset was copied.

### Adjust-style consistency pass (2026-09-26)

The user-provided Adjust panel screenshot was rechecked for its flat translucent
bodies, centered blue headers, muted text and limited line work. Perihelion's
panel frontend now uses smoother cached glyphs, one flat expanded section and
pixel-aligned slider separators. This is visual reimplementation only; no Adjust
assets or donor GUI code were copied.

## Gameplay module expansion (2026-09-27)

Perihelion's existing module definitions, settings, activation ownership, shared controls,
render scopes and smoke probes were inspected first. The read-only Yuri checkout at
`0a4bd400784ef529d238311de5ce867e815794e2` supplied the behavior reference for all 65
non-presentation top-level modules. [The full ledger](yuri-modules.json) records every one
of Yuri's 86 top-level modules, including 21 exclusions. [YURI_PORT.md](YURI_PORT.md)
records mode coverage, adaptations, intentional fixes and verification limits.

Primary source paths, relative to `src/main/java/ddlc/yuri/`:

- `modules/impl/combat/`, `modules/impl/movement/`, `modules/impl/player/`, the non-client
  modules in `modules/impl/misc/` and gameplay visuals in `modules/impl/render/`, including
  the nested movement/disabler mode implementations exposed by their parent modules.
- `utils/player/{ScaffoldUtils,RayCastUtils,RotationUtils,MoveUtils,PlayerUtils,InventoryUtils}.java`
  and relevant item, aim, targeting and render utilities for algorithms and constants.
- `managers/impl/{RotationLearnerManager,RotationManager,SlotManager,BlinkManager,LagManager}.java`
  for behavioral contracts only. These managers and their global state were not copied.
- The donor's modified vanilla player, controller, renderer and network classes to locate
  equivalent hook points. Perihelion implements narrow Forge/Mixin hooks instead of replacing
  Minecraft classes.

New native implementations live under `integration/minecraft189/module/gameplay/`, with
scoped packet snapshots/queues in `network/` and typed hooks in `event/` and `mixin/`.
No donor module framework, event bus, config, GUI, command system, global manager, font atlas
or branded image/shader framework was imported. Existing Perihelion architecture remains
responsible for every module's activation, setting changes, fault handling and cleanup.

**Copied data:** Yuri's `src/main/resources/assets/minecraft/yuri/rotationpresets/{legit,ai}.csv`
rotation presets are redistributed as
`assets/perihelion/rotationpresets/legit.csv` and `ai.csv`. The donor's MIT copyright and
permission notice, Copyright (c) 2026 Unlegit Inc., remains in
`forge/src/main/resources/META-INF/licenses/Yuri.txt` and the built distribution.
All other substantial work in this expansion is native behavioral reimplementation.
Donor repositories were not modified or committed.

## Selective donor improvements (2026-09-27)

Inspected the current native modules, shared controls, lifecycle, render scopes and
verification fixtures before reimplementing the selected behaviors. Local references:

- **Arsenic** `d6f3a3d40a6d3f14826a42002abf343c61c46ecb`, MIT, Copyright (c) 2022
  Arsenic Client Development: `arsenic/module/impl/ghost/AimAssist.java`,
  `impl/visual/BedPlates.java`, `impl/player/AutoEat.java` under `src/main/java/`.
  Used manual aim assistance, bed-defense material information and food-selection
  requirements. The native implementation uses owned view/server rotation, budgeted
  block scanning and real bounded inventory transactions. The donor's unbounded
  initial chunk scan and inventory-index-as-hotbar behavior were not retained.
- **OpenMyau-Plus** `52c8492c68ed14a0dd6137d0c21f912c463af8d9`, repository license
  GPL-3.0: `src/main/java/myau/module/modules/{AutoClicker,ChestAura,FastPlace,Wtap,
  KillAura,Scaffold}.java`. Inspected observable feature options and edge cases:
  manual held clicking, chest opening, placement filters, timed forward release,
  target sorting/input restrictions and building feedback. New code uses Perihelion
  APIs and fresh activation-local algorithms; no donor source, click-pattern data,
  packet mode implementations or supporting framework was copied.
- **Project-Coolware** `29d1e0974a49f81e829ab12ecbeada0c145f0d1a`: `src/main/java/com/
  github/cooldood/modules/impl/render/{Freecam,Zoom,NoRender}.java` and related
  rendering hooks. No root license file was found. Only camera/zoom/effect behavior
  was used as a reference; no source/assets were copied. The native detached view
  does not use the donor's global fake-position state or cleanup early return.

The seven new native definitions and settings are documented in
[DONOR_IMPROVEMENTS.md](DONOR_IMPROVEMENTS.md). Existing default behavior remains
available. All additions use the existing registry/config/event/lifecycle systems.
No donor repository was edited or committed. No external server was contacted.

## Gameplay verification and corrections (2026-09-28)

Compared the native implementation with Yuri revision
`0a4bd400784ef529d238311de5ce867e815794e2`, especially
`modules/impl/combat/{AuraModule,BackTrackModule}.java`,
`modules/impl/player/{ScaffoldModule,EagleModule}.java`,
`utils/player/{ScaffoldUtils,PlayerUtils,BlockUtils,FriendUtils}.java`, and
`managers/impl/{TargetManager,BadPacketsManager}.java`. These remain read-only references.

Reimplemented the first-gap Scaffold expansion/Keep Y gates and always-sampled predictive
blocking through native scoped state. Optimized Ultra Safe without changing candidate or
fallback order; a separate exhaustive packaged-game reference checks full blocks and slabs.
Strict placement now uses its validated ray intersection. Fixed invalid/stale combat and
placement actions, cumulative Back Track movement, and bounded visual scanning/target-filter
costs. NCP hurt gating and cumulative Back Track intentionally correct donor defects;
Perihelion's working Aura mode selector is retained instead of importing the donor's unwired
manager setting. No donor framework or new assets were copied. The complete scope, remaining
behavior differences, evidence and rollback are in [the audit report](DEEP_AUDIT_2026-09-28.md).

## Gameplay quality improvements (2026-09-29)

After reviewing the native implementations, compared these local read-only references:

- Yuri `0a4bd400784ef529d238311de5ce867e815794e2`:
  `modules/impl/combat/{AutoProjectileModule,BowAimbotModule,AutoPotModule,AuraModule}.java`.
  Retained Aura-combo projectile behavior while correcting misleading range options and
  revalidating actions through native controls. No donor framework changes were imported.
- Arsenic `d6f3a3d40a6d3f14826a42002abf343c61c46ecb`:
  `module/impl/ghost/BowAimbot.java`, `module/impl/player/{InvManager,AutoPot}.java`, and
  `module/impl/world/Clutch.java`. Referenced draw-dependent prediction, explicit drop
  permission and knockback-triggered block catches. The new Clutch is an independent,
  bounded implementation using Perihelion Placement and activation-owned controls;
  it also validates a projected landing footprint.
- OpenMyau-Plus `52c8492c68ed14a0dd6137d0c21f912c463af8d9`:
  `module/modules/{ChestStealer,InvManager}.java`. Referenced inventory-aware equipment
  selection and supply targets. Reimplemented scoring/selection with native shared item
  classification, per-action snapshots and actual merge-capacity checks.
- Project-Coolware `29d1e0974a49f81e829ab12ecbeada0c145f0d1a`:
  `modules/impl/movement/Scaffold.java`. Referenced optional held-input/pitch conditions
  and largest-stack selection. Existing Perihelion placement/ray checks are retained.

Paths above are relative to each donor's Java package under `src/main/java`. No donor code,
assets, event buses, module managers or config systems were copied in this batch. Donor
repositories were not changed. New settings and intentional compatibility/default changes
are listed in [QUALITY_IMPROVEMENTS_2026-09-29.md](QUALITY_IMPROVEMENTS_2026-09-29.md).

## Common HUD and editor (2026-09-29)

Perihelion's `HudRenderEvent`, `HudText`, `RenderState`, module ownership, settings,
ClickGUI and existing overlay fixtures were inspected first. The seven local
`adjust_visuals/` screenshots supplied the translucent panel, compact typography,
lavender/salmon accent, stepped active-list border and player-table direction.

Read-only behavioral references:

- [Yuri](https://github.com/unleg1t/Yuri/tree/0a4bd400784ef529d238311de5ce867e815794e2):
  `src/main/java/ddlc/yuri/modules/impl/render/ModListModule.java`,
  `WatermarkModule.java`, `ClientInfoModule.java`, and `TargetHudModule.java`.
  Used for width ordering, mode suffixes, optional filtering, client info, target
  health/equipment presentation, positioning, and cleanup requirements.
- [OpenMyau-Plus](https://github.com/IamNespola/OpenMyau-Plus/tree/52c8492c68ed14a0dd6137d0c21f912c463af8d9):
  `src/main/java/myau/module/modules/HUD.java` and `TargetHUD.java`.
  Used for configurable accents/backgrounds, tick-maintained module lists,
  target selection/presentation, optional toggle alerts and position controls.

The HUD, bounded snapshots, normalized positions and editor are new Perihelion
implementations. They use native module/config/event ownership and rendering;
no donor manager, event bus, config code, shaders, font atlases or images were
copied. No donor files were modified. The nearby-player table uses loaded game
information only; it does not replicate Adjust's external player statistics or
server-specific event timers. See [HUD usage](HUD.md) for supported behavior.


## HUD screenshot fidelity (2026-09-30)

Follow-up comparison against `adjust_visuals/Screenshot 2026-09-15 at
11.10.31 AM.png`, `11.10.33 AM.png`, `11.11.41 AM.png`, `11.10.34 AM.png` and
`5.24.27 PM.png` (filenames use a narrow no-break space before AM/PM) corrected
oversized text, decorative header bars, tinted backgrounds, and the list outline.
The five-category navigator, compact player table, heads/health-percent/team
columns, flat two-line notifications and left-only stepped outline follow the
visible reference. Perihelion keeps its name. Exact font identity and external
player statistics are unavailable; the native GUI Sans atlas is reused, and
external-statistics cells stay empty.

Existing Yuri/OpenMyau sources listed above remain the behavior references.
Native 1.8.9 rendering, scoreboard and framebuffer signatures were checked against
Perihelion mappings and Yuri's read-only Minecraft source for the packaged fixture.
All new rendering/navigation uses Perihelion module settings, lifecycle cleanup,
GL scopes and GUI font code. No donor code, font assets or client architecture was
copied, and no donor files were modified.

## Movable GUI utilities and grouped player board (2026-10-12)

The user-provided `Screenshot 2026-10-12 at 10.54.00 AM.png` supplies the player table and context-card direction: head/name card, Tags, Analysis and Hypixel groups. The reference's Whitelist, Target, Spectate, Violations, Statistics and Report labels are present. Whitelist edits native Friends; Target is only a local board tag; Violations identifies the missing provider. Existing Freecam/report/stats actions retain their native lifecycle and explicit API workflow. No Adjust code, assets or font was supplied or copied.

Read-only references inspected after the Perihelion frontend: Yuri `6f5603e0c0eed253741bacdcd4135830c916602e`, `api/gui/click/novoline/CategoryTab.java` and `config/ConfigTab.java` for draggable/right-clickable headers; Arsenic `d6f3a3d40a6d3f14826a42002abf343c61c46ecb`, `module/impl/visual/HUD.java` for HUD positioning/visibility. Geometry is reimplemented through native `UtilityLayout` and hidden ClickGUI settings; GUI/HUD/font state uses the existing module manager, setting mutations, cleanup and GL scopes. Donor repositories are unchanged. Logo and Tab GUI are now independent HUD elements. See [GUI/HUD revision](GUI_HUD_2026-10-12.md).


## HUD element customization — 2026-10-13

Read-only references: `OpenMyau-Plus/src/main/java/myau/module/modules/HUD.java`
(color modes, row backgrounds, left/right alignment, sidebar variants, lowercase
names and mode-suffix separators) and Project-Coolware's `ArrayListModule.java`
(theme colors, measured width sorting and right anchoring). No donor files changed.

Perihelion implements these common array-list options through its typed HUD settings,
normalized draggable bounds, tick snapshots, shared color/enum/slider controls and
owned render state. Basic retains the original appearance. Custom adds per-element
client-theme/solid/gradient/rainbow colors, compact/panel/minimal styles, text color,
shadow and opacity. Array-list settings include alignment, sort, border, spacing,
background, lowercase names, suffix format and visual-module filtering. The HUD
editor now has HUD, Basic/Custom and Options controls; right-click opens customization.
No donor managers, event buses, config writers, fonts, shaders, blur or glow are imported.

Pre-edit sources have a SHA-256-verified backup in
`.backups/before-hud-options-20261013/manifest.json`.

## Modern-Lazify player overlay — 2026-10-13

Behavior reference: [MichaelStetson/Modern-Lazify](https://github.com/MichaelStetson/Modern-Lazify)
at commit `50428890f1f6754ad5b0859293d9421420c3ac9b`. Perihelion's existing HUD,
Hypixel lookup, settings and chat interaction were inspected first. Read-only
source snapshots under ignored `.references/modern-lazify-50428890/` include
`PlayerStatsService`, `BedwarsStats`, `LegacyPlayerStats`, `LegacyGameplayTracker`,
`LegacyDenickSupport`, `LegacySettingCatalog`, `BedwarsMonitor`, `LazifyHud`,
`LazifyCommands`, `LazifyClient` and `LazifyConfig`. No donor checkout changed.

Reimplemented with Perihelion-owned Java 8 snapshots/workers/settings: Abyss,
Prism, Bordic, official Hypixel fallback, Urchin and Seraph transport contracts;
BedWars modes/stars/ratios/streaks; Bordic batched periods; attributed tags;
nick hints; tab roster, encounters, sorting/filtering, hidden players and paging;
party/join/ONLINE/final-kill/combat tracking and optional local alerts. The original
Adjust-style layout, native lifecycle, editor, Friends, pins and report selection
remain the integration boundary. Missing fields are never invented; UUID mismatches
and provider errors are rejected. No Fabric mixins, donor event bus, manager,
config writer, UI architecture, keys or assets are bundled. The upstream tree has
no root license file; this entry records behavioral/API provenance without asserting
a donor license. No new third-party dependency is added.

See [usage, scope and verification](MODERN_LAZIFY_2026-10-13.md). Pre-edit sources
are hash-verified in `.backups/before-modern-lazify-20261013-154135/`.


## Yuri regression and behavior loop — 2026-10-18

Reference: unchanged read-only Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
Perihelion was inspected before the donor's `misc/AutoSwapModule`,
`player/AntiFireballModule`, `combat/AutoPotModule`, `player/nofall/impl/MLGNoFall`,
`BadPacketsManager`, `SlotManager` and rotation helpers. The fresh source checkpoint
is `.backups/before-yuri-loop-1-20261018-144540/source.tar.gz`: 675 verified files,
SHA-256 `f30ebde71498fc126626def32a797c31363b772613043ae667da25010ff9ae67`.

- Corrected the preceding recovery change's unintended suppression of mining
  Auto Swap during potion aiming. Its projectile pause now has an independent
  native supplier; Aura still yields to both item routines.
- Moved Auto Swap selection to native Motion PRE, after the earlier PRE attack,
  and retained the activation-owned slot through the next control tick. Native
  client/server/restoration choices and manual-input ownership remain.
- Reimplemented Anti Fireball's first-loaded candidate strictly within twelve
  blocks, inclusive three-block attack range, startup-only 50 ms stopwatch,
  source eye-height aim and speed, seen UUID persistence and once-per-nearby
  notice. The notice uses Perihelion local chat; no donor popup UI was imported.
- Added scalar outgoing action-window facts at shared dispatch for local and
  remote PLAY traffic, including cancelled attempts and background dispatch.
  Silent replay is excluded. A separate target callback preserves the native
  game-controls client-thread guard; no raw local packet events are published.
- Completed potion and MLG slot choices now use native persistent hotbar
  selection, matching the source's normal completion. Owned rotation, fresh
  item/entity/ray checks, interrupted-routine cleanup and world retirement remain.

No donor event bus, module manager, config system, global client, fonts or assets
were imported. All donor files remain unchanged. The audit retains bounds and
session ownership instead of reproducing unbounded donor collections/queues.
See [iteration evidence](YURI_PARITY_LOOP_1_2026-10-18.md) and the
[ongoing requirements ledger](YURI_PARITY_REQUIREMENTS.json). An intermittent
OptiFine bridging failure remains recorded even after diagnostic reruns pass;
local tests do not establish exhaustive equivalence or live server acceptance.

## Yuri buffered-position and potion follow-up — 2026-10-18

Reference remains clean, read-only Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
After native inspection, reviewed Blink, Lag Range, LagManager, PacketUtils,
RenderUtils, Auto Pot and PlayerUtils. A fresh 685-file archive was hash-verified:
`.backups/before-yuri-loop-2-20261018-193945/source.tar.gz`, SHA-256
`99467ece174a46a0a83c8c54437874437071565f45a987ed1fd9664a12ad002d`.

Added the previously missing Blink/Lag Range position markers with their source
anchor/active-cycle/third-person rules, 0.6 by 1.8 dimensions and 60/40 opacity.
Native theme accent and RenderState replace donor color/render architecture.
Accepted positional sends publish immutable scalar tuples after cancellation;
accepted buffer replay advances the estimate without raw event redispatch.
Look/ground-only and rejected movement do not invent or advance the estimate.

Source tracing also corrected Lag Range's end-of-cycle release from POST to
Motion PRE on movement stop, target loss, Blink range exit and Lag duration end.
The initial Lag POST arm and one-time 100 ms spoof lease remain. Auto Pot now
uses the source first listed effect, good-effect whitelist plus jump, healing
health gate and exact nonzero active-duration test. Native malformed/mixed-harmful
potion checks remain. No shared inventory policy was speculatively changed.

All changes use native lifecycle, settings, scoped listeners, shared controls,
packet FIFO/session guards and GL restoration. No donor framework/manager or
assets were imported and no donor repository changed. See the
[second loop evidence](YURI_PARITY_LOOP_2_2026-10-18.md). Broader parity and live
server acceptance remain unverified; the earlier intermittent bridge failure
remains recorded.


## Yuri sampling and hotbar handoff regression — 2026-10-18

Reference remains read-only Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
Native controls/timing were inspected before LagRangeModule, MathUtils, AutoPot,
AutoSwap and SlotManager. A 691-file source archive was verified at
`.backups/before-yuri-loop-3-20261018-195734/source.tar.gz`, SHA-256
`7f437a822ed7d32dd9dafbb157ab20bcb8a300817e23fd3dcbb21b91b0492cc1`.

Lag Range now samples the source ThreadLocalRandom delay only when entering a
new range cycle, including Blink, retaining fixed/reversed endpoint behavior.
The native hotbar adapter now preserves an eligible restoring handoff while a
persistent potion/MLG choice wins. Uncontested persistent completion still stays
selected. Manual-input detection, priority, expiry and activation/world retirement
remain native. An actual-module test fails on the previous JAR's restoration,
then passes on the corrected checkpoint for client, server and manual-input paths.

A separate account-manager task began editing/building the same checkout. The
coherent validation copy uses the verified pre-account baseline plus all eight
current parity edits. It passes 424 unit tests and both full packaged suites;
combined-checkout integration remains pending. No account files were changed.
The narrow `scripts/restore-yuri-loop3.py` restores only this pass's code and
preserves unrelated UI/account work. See [scope and evidence](YURI_PARITY_LOOP_3_2026-10-18.md).
No donor framework/global architecture was imported, and the donor was not executed.
## Selectable Raven visuals — 2026-10-19

Read-only OpenB4 reference: `../donor_clients/OpenB4/OpenB4/`, revision
`3d63944cc79be890c013cc79e5ed92d3533b48a5`. Perihelion's existing frontend/HUD
were inspected first. `CategoryComponent`, `ModuleComponent`, `SliderComponent`,
`ButtonComponent`, `HudModule`, `TargetHudModule.legacy` and `InfoHud` informed a
native **Raven** style selectable beside the default **Adjust** style in
ClickGUI settings. The Modern-Lazify `LazifyHud` snapshot at
`50428890f1f6754ad5b0859293d9421420c3ac9b` was also checked for existing overlay
measurement/position behavior.

Raven adapts Minecraft typography, centered rows, dark edged panels, active-row
accents, thicker sliders, compact HUD text and a legacy health-bar target card.
Existing settings, layout recovery, config persistence, input/GL cleanup and HUD
snapshots remain owned by Perihelion. This is visual adaptation, not exact pixel
parity with OpenB4's animated rounded menu. Donor architecture, console, timers,
animations and gameplay behavior were not imported. Donors remain unchanged.

See `CLIENT_STYLES_2026-10-19.md` for scope, the verified pre-edit backup,
guarded rollback and verification evidence.

## Independent Raven presentation — 2026-10-20

The prior Raven palette/border adaptation is superseded by independent
`RavenScreen`, `RavenTheme`, `RavenControls` and `RavenHudRenderer` presentation.
Adjust has its own frontend; only native settings/data, measurement/input and
low-level drawing primitives remain shared. Raven owns every HUD element and
returns before Adjust drawing. Separate panel/tool layouts prevent style state
from crossing over.

The same read-only OpenB4 revision
`3d63944cc79be890c013cc79e5ed92d3533b48a5` was re-inspected after the current native
implementation, including actual `GuiModule` palette/opacity, category item icons,
module/setting components and Frost HUD theme. Donor architecture and background
work were not imported; the donor checkout remains unchanged. See
`RAVEN_INDEPENDENT_2026-10-20.md` for scope, backup/rollback and verification.


## B4 and Yuri module implementation audit (2026-10-24)

Inspected all 96 registered native implementations before their relevant pinned
Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def` and OpenB4
`3d63944cc79be890c013cc79e5ed92d3533b48a5` source bodies/helpers. The
[complete per-module ledger](DUAL_DONOR_AUDIT_2026-10-24.json) records source
scope, supported modes, intentional differences and unverified behavior.

Corrected Telly jump/sprint/ground-return phases; Yuri NORMAL pre-sprint input
correction and direct-helper yaw; continuous smoothing and full-expression
sensitivity precision; selected Yuri game-atan2 consumers and noarg Speed potion
factor; current-client local action facts with separate raw packet cancellation;
BackTrack session identity; B4 Guard's packet-Aura exclusion; Stealer's physical
chest distance/current-candidate delay; Bed Defender's face-grid/order/turn budget,
menu claim retirement and attempt delay; Ambience's signed time/daylight cleanup;
and effective-aim trajectory caching. Yuri-derived additions keep AGPL provenance.

Behavior uses native module lifecycle, events, settings, shared controls,
submitted-aim guards, scoped hotbar selection and bounded packet handling. No
donor event bus/config/global architecture or new setting/module/dependency was
imported. Donor repositories remain read-only. See the [detailed report and
limits](DUAL_DONOR_AUDIT_2026-10-24.md) and [coordinated verification
receipt](DUAL_DONOR_VERIFICATION_2026-10-24.json); source coverage is not a claim
of all-mode runtime equivalence or live Grim/Hypixel acceptance.

## B4/Yuri parity recheck — 2026-10-25

Rechecked the 96 native registered modules against the same read-only Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def` and OpenB4
`3d63944cc79be890c013cc79e5ed92d3533b48a5` references. Current native bodies/hooks
were inspected first. The focused second pass cross-references the earlier body
ledger; it does not claim exhaustive all-settings or live-server equivalence.

Corrected Yuri turn distribution/float/jitter/sensitivity order, sampled rotation
speeds and Scaffold/Telly float constants; Aura's fresh local block-action window;
Anti Fireball target-angle arithmetic; Target Strafe full-block support, collision
edge motion and orbit arithmetic; Yuri LEGIT/LAG LongJump reported-angle handoff;
Librecraft NoFall packet-only landing mutation; NCP Speed's source sequential
horizontal norm and lookup trig; Step gravity float constants and FastBreak float
operation order; predicted Minigame Aim visibility, largest-stack Auto Ammo; and
interpolated/fractional-charge trajectory caching. Yuri-derived source behavior
remains under its recorded AGPL-3.0-only provenance. The new client-thread C03
preflight uses native activation listeners and existing connection/generation/
thread/replay guards; raw packet interception and donor architecture are not
imported.

Native owner/previous-walk/stack/ray/cleanup safeguards remain authoritative.
Minigame Aim deliberately keeps one prediction offset rather than Yuri's duplicate
caller offset. Block In's coherent smoothed visible/server aim differs from Yuri's
immediate raw camera assignment; this visual scheduling remains deferred. Native
Breaker policies and unsupported donor-only modes remain explicit exclusions.
No user setting, module or dependency was added. See the [recheck report](DONOR_PARITY_RECHECK_2026-10-25.md),
[96-module ledger](DONOR_PARITY_RECHECK_2026-10-25.json) and
[verification receipt](DONOR_PARITY_VERIFICATION_2026-10-25.json).

## B4/Yuri third implementation pass — 2026-10-25

Rechecked the same 96 registered module IDs at pinned Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def` and OpenB4
`3d63944cc79be890c013cc79e5ed92d3533b48a5`, concentrating on transitions and native
ownership. Donors remained read-only; existing source/body ledgers remain part of
the comparison and unsupported presentation/mode boundaries stay explicit.

Yuri Manager/Auto Armor keep their module-lifetime action-timer semantics. B4
NoSlow reuses a current-client fresh action window that survives module and mode
toggles. Native integration corrections bind walking receipts and buffered replay
to their actual connection, prevent previous-angle actions after a packet-only
NoFall position divergence or a hard-cancelled positional walk, retire Auto Apple's use claim on hotbar/stack loss,
validate Freecam's prior camera identity, and suppress Minigame Aim inventory/use
actions during detached view ownership. These use native lifecycle/events/shared
controls, not donor buses/managers/config/global architecture. No setting, module
or dependency was added. Existing AGPL provenance applies to Yuri-derived logic.

See [the detailed third-pass report](DONOR_PARITY_PASS3_2026-10-25.md),
[96-ID ledger](DONOR_PARITY_PASS3_2026-10-25.json), and
[verification receipt](DONOR_PARITY_PASS3_VERIFICATION_2026-10-25.json). Local
arithmetic, ordering and lifecycle evidence does not establish live Grim/Hypixel
acceptance or exhaustive parity.


## Per-element HUD presentation and Array list controls — 2026-10-26

Inspected the native HUD/editor before the read-only references:

- OpenMyau-Plus `52c8492c68ed14a0dd6137d0c21f912c463af8d9`,
  `src/main/java/myau/module/modules/HUD.java`: independent font, spacing,
  suffix separators, background opacity, gradients and optional blur controls.
- Arsenic `d6f3a3d40a6d3f14826a42002abf343c61c46ecb`,
  `src/main/java/arsenic/module/impl/visual/HUD.java`: width sorting and
  separate background/bar choices for module rows.
- Project-Coolware `29d1e0974a49f81e829ab12ecbeada0c145f0d1a`,
  `src/main/java/com/github/cooldood/modules/impl/client/ArrayListModule.java`:
  restrained row backgrounds and consistent theme colors.

These common presentation ideas are reimplemented through Perihelion's typed
settings, HUD snapshots, editor controls, font cache, normalized drag positions
and activation cleanup. No donor renderer, manager, settings framework, blur
pipeline or global architecture is imported. Donor checkouts remain unchanged.

Every native element has independent font, size, text colors, text shadow, panel
colors/gradient, opacity, corners, optional blur, and panel-shadow opacity/spread. Array list adds functional
spacing in New visuals, row padding, a connected text-fitted silhouette or full
rectangular background, optional borders,
lowercase and mode separators. The HUD-owned blur takes one scene copy per
pass and shades only enabled regions; it is disabled by default and releases
its shader and texture on teardown. Text/Panel/Content tabs keep the editor
compact. Legacy rendering paths remain separate. See [HUD usage](HUD.md).

## Yuri Manager / Aim Assist update — 2026-11-03

Inspected the native modules first, then Yuri's October 25 change
[`31b026c3aa328a05142e1b5524556a9a10dff08a`](https://github.com/unleg1t/Yuri/commit/31b026c3aa328a05142e1b5524556a9a10dff08a),
merged as `faea989c5d889ab1b4e2a03820159ec6c811b703` (PR #18).
Reference files: `modules/impl/player/ManagerModule.java`,
`modules/impl/ghost/AimAssistModule.java`, and `utils/player/RayCastUtils.java`.
The local Yuri checkout stays unchanged at `1cb86ad`; the newer modules were
read through GitHub. Existing Yuri-derived aiming remains AGPL-3.0-only.

- Manager adds **Start Delay (ms)**, default 120, range 0–500 in 10 ms steps.
  The native motion listener tracks each inventory-screen opening before its
  eligibility checks. Sorting waits for both opening and per-action delays;
  Instant bypasses both. Reopening or reactivating starts a fresh opening delay.
  The existing module-lifetime per-action clock is preserved. Intentional donor
  correction: the opening delay applies only inside the inventory. Yuri resets
  its opening timer every motion outside the inventory, which otherwise prevents
  non-Instant background sorting with a positive Start Delay.
- Aim Assist adds **Reduce On Target** (off) and its conditional **Reduce Factor**
  (0.5, range 0–1 in 0.01 steps). Simple and Adaptive multiply both effective
  speeds by `1 - factor` when the selected target is the visible ray's first
  collidable entity, within Distance and before blocking geometry. This uses the
  camera's actual view, not the silent selection or vanilla attack-reach pick.
  Sampling runs once per native START tick; native activation cleanup, null/world
  guards and target identity checks prevent stale reductions. Silent mode keeps
  its existing behavior and hides these controls.

No donor event bus, manager, settings/config system or global architecture was
imported. Existing profiles load the new defaults through native settings.
The focused packaged check is `scripts/launch-smoke --scenario yuri-update`;
it covers actual container swaps, timer gates, world/entity occlusion, range,
both speed axes, modes, retargeting and cleanup. Local checks do not establish
authenticated server acceptance.

Verification: all 164 core and 444 Forge unit tests passed, as did architecture,
wrapper/runtime checksum and packaged-JAR checks. The focused Java 8 Minecraft
run passed 24 update assertions and its normal menu/config/HUD/teardown checks.
The ray uses explicit camera angles: vanilla `EntityLivingBase.getLook()` follows
head yaw, and the off-target game assertion caught that difference during this
port. The final artifact and run pointers are in
[`verification/yuri-update-20261103.json`](verification/yuri-update-20261103.json).

## Scaffold / Telly diagonal corrections — 2026-11-03

Inspected the native Scaffold, player ground history, movement correction and jump
hooks, then the unchanged Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`
`modules/impl/player/ScaffoldModule.java`, `managers/impl/RotationManager.java`,
`utils/player/ScaffoldUtils.java` and patched Minecraft ground counters.

Corrected four donor-derived edge cases: the placement latch's unreachable
post-startup reset, missed exact Telly thresholds after direction changes,
camera/strafe-key diagonal classification, and truncated Expand offsets.
Landing now closes each airborne placement window; normal thresholds use elapsed
comparison; diagonal timing follows the raw input's world heading; expansion
projects the player's fractional position into block coordinates. Hypixel early
windows, startup allowance, sprint choices, speed constants and defaults remain.
The jump mixin, native control ownership and shared correction pipeline are not
replaced. No donor architecture or global state was imported.

See [the audit and local test scope](SCAFFOLD_AUDIT_2026-11-03.md) and
[verification receipt](verification/scaffold-diagonal-20261103.json).

## Bed Defender turn / placement improvements — 2026-11-04

Inspected the native planner, rotation engine, placement scopes and walking
receipts before comparing read-only Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def`,
`modules/impl/player/BedDefenderModule.java` and
`managers/impl/RotationManager.java`.

Intentional improvements over donor policy: minimize roof turns instead of
maximizing them, compare valid support faces and reachable side targets, reuse
an already-valid ray, and validate sensitivity-rounded candidates. Native
Bed Defender now turns from the actual reported server angle and quantizes once
without FPS-dependent jitter. A strict, owned ray can place immediately after
its walking packet, retaining previous-aim PRE placement and a single attempt
budget. Place Delay and existing settings/defaults remain. Reach is capped to
the active game controller; bed-support clicks use native scoped sneaking to
avoid bed activation and preserve packet ordering, even when unfocused.

The eight-cell shell, side-before-roof preference, native movement correction,
hotbar ownership and retirement rules remain. No donor event bus, manager,
configuration system or global architecture was imported. See the
[behavior and verification scope](BED_DEFENDER_ROTATIONS_2026-11-04.md) and
[artifact receipt](verification/beddefender-rotations-20261104.json).

## Breaker layered-defense repair, B4 behavior review — 2026-11-04

Inspected Perihelion first, then unchanged OpenB4
`3d63944cc79be890c013cc79e5ed92d3533b48a5` BedAura, BlockBreaker, BlockUtils,
ItemUtils/AutoTool and Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def` Breaker.
B4 is the primary behavior reference for fastest material/tool choice, both bed
halves, cover-to-bed handoff, mining packet ownership, interruption and the short
post-break wait. Native costed ray paths extend this to layered cover and avoid
the old inner-neighbor and hidden-air false-exposure stalls.

Reimplemented through Perihelion-owned activation state, submitted aim, hotbar
claims, native damage calculation, home tracking and cleanup. Same-position
block changes, server corrections, tools, lost ownership, GUI/world changes and
reentrant sends retire old progress. Zero-hardness blocks and vanilla slowdowns
are handled explicitly. Connection/packet identity scopes replace B4's global
sending flag. No donor architecture or unsafe script/AutoRequeue path is used.

Existing modes/settings/defaults are preserved; Legit and Hypixel now peel
reachable layers and Vanilla remains exposed-only. Donor Instant/Swap timing
shortcuts, ground spoofing and skipped verification are excluded. See the
[B4 mapping, intentional differences and second-pass checks](BREAKER_B4_2026-11-04.md)
and [verification receipt](verification/breaker-b4-20261104.json).

## Breaker Visual mode and expanded BedAura controls (2026-11-04)

Follow-up to the layered Breaker repair: re-read OpenB4 BedAura/BlockBreaker,
BlockUtils/PlayerUtils and the actual GUI settings at pinned revision
`3d63944cc79be890c013cc79e5ed92d3533b48a5`. Reimplemented Instant/Swap countdowns,
Legit progress floor, Swap cap, FOV/range/rate, cover/visibility, animation/effect,
interaction, slowdown, ground, verification, Aura, knockback and Speed-pause
controls through native settings/events/shared controls and owned cleanup.
Added an independent presentation-only Visual mode with ordered route/tool/time
guidance. Donor repositories remain unchanged and were not built/executed.
See [behavior/settings mapping and limits](BREAKER_VISUAL_B4_2026-11-04.md) and
[verification](verification/breaker-visual-b4-20261104.json). Native submitted-rotation/tool ownership remains mandatory; default peeling
requires visible hits, while Break Near Block off allows B4 direct-bed targeting.
Defaults preserve layered
cover peeling and own-bed protection. The previous repair's excluded-settings
list is superseded by this follow-up; live-server equivalence is unverified.

## Scaffold movement/packet consistency (2026-11-10)

Inspected native Scaffold and walking/input receipts first, then compared pinned
read-only Yuri and OpenB4 Scaffold timing. Fixed a native phase mismatch: movement
and jumping used the input aim while the same update's walking packet could use
a newly smoothed post-movement aim. The latter is now queued for the next input
update; the current yaw remains owned through physics and walking. Move Fix off
still uses the sent yaw for native physics. No donor architecture or global
rotation state was imported, and donor files were not changed or executed.
See [the reproduced failure and scope](SCAFFOLD_GRIM_2026-11-10.md) and
[verification](verification/scaffold-grim-20261110.json). Local consistency is
tested separately from unverified live Grim acceptance.

## Telly current PRE placement from Yuri (2026-11-11)

Re-read pinned Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def` Scaffold,
RotationManager, ScaffoldUtils, RayCastUtils and the actual player update order
after the user reported interrupted Telly rows. Restored current PRE planning,
smoothing and placement through a distinct native input-phase capability;
removed the queued post-movement aim and previous-angle-only placement
restriction for Scaffold. Other submitted-rotation APIs retain their contracts.
Native scope/position/connection/tool/ray validation remains. Donor code stays
read-only and was not executed. A continuous test reproduced the old three-block
failure, and four actual integrated-server Telly runs accepted 28–30 blocks each.
See [Yuri mapping and regression evidence](TELLY_YURI_2026-11-11.md) and
[verification](verification/telly-yuri-20261111.json). Live Grim is unverified.


# Yuri gameplay modules

Latest source follow-ups and behavior corrections are recorded in
[the October 11 audit](YURI_LATEST_AUDIT_2026-10-11.md). This report
preserves its original implementation and verification baseline.

Reference: Yuri revision `0a4bd400784ef529d238311de5ce867e815794e2` (MIT).
All 86 top-level donor definitions are accounted for in [yuri-modules.json](yuri-modules.json):
65 have a native implementation or existing equivalent; 21 client presentation modules are excluded.
Perihelion now registers 70 gameplay modules plus its existing ClickGUI. All gameplay modules
start disabled and unbound. This is implementation coverage, not a claim that every mode has
been verified on a multiplayer server or that server-specific bypasses remain effective.

The implementation uses Perihelion's settings, commands, config, activation scopes,
client/network events and shared controls. Donor event buses, managers, client singleton,
configuration, GUI, commands, fonts and branding are not imported. Modes and setting names
are available through the existing ClickGUI and `/perihelion set` commands.

## Coverage

| Family | Native IDs and main behaviors |
| --- | --- |
| Combat targeting | `aura`: Single/Switch/Adaptive; CPS/ranges, ray and target filters, Normal/ML/None rotations, advanced aim variation, None/Strict/Silent movement correction, Fake/Vanilla/NCP/Legit/Predictive/None blocking; `antibot`: tab/name/age/ping/FOV filters; `friends`: persisted name list and middle-click toggling. |
| Combat timing | `criticals`: Packet/Mospixel/NCP; `velocity`: Legit/Intave/Cancel/Custom; `hitselect`; `targetstrafe`; `tickbase`; `lagrange`: Lag/Blink; `backtrack`; existing `wtap`. |
| Combat items | `autoapple`, `autopot`, `autoextinguish`, `autoprojectile`, `bowaimbot`, `antifireball`. Item selection, rotation and held input use shared claims. Healing and aiming yield to Scaffold where appropriate. |
| Movement | `flight`: Motion/NCP; `speed`: Vanilla/Legit/Polar/Intave/NCP/Librecraft; `step`: Vanilla/NCP; `waterwalk`: Vanilla/NCP; `noslow`: Vanilla/NCP; `phase`: Normal/Polar; `longjump`: Fireball Legit/Lag or Mospixel; `safewalk`, `stasis`, `eagle`, existing `sprint` and `timer`. |
| Packets and recovery | `blink`: Constant/Pulse with optional incoming packets; `fakelag`: selected packet delays; `antivoid`: Vanilla/NCP; `nofall`: Vanilla/MLG/Librecraft; `regen`, `fastuse`. |
| Building | `scaffold`: Normal/Telly/Breezily/God Bridge, Normal/Randomized/Old rotations, Normal/Secondary/Ultra Safe searches, sprint/tower/ray/slot modes; `blockin`, `beddefender`, `breaker`: Vanilla/Hypixel/Legit. Placement plans are revalidated and commit only under winning slot/rotation claims. |
| Inventory | `manager`: sort slots and discard inferior/unwanted items; `stealer`: chest filters, delays, distance/position scaling, miss clicks and auto-close; `refill`, `autoarmor`, `invmove`, `fastbreak`, existing `fastplace` (Yuri ItemDelays). |
| Utilities | `autoswap`: Client/Server selection for tools and weapons; `derp`: Client/Server rotation; `inputfixes`; `disabler`: the donor's exposed inventory mode; `autochat`: registration/play prompts; `bedwarsutility`: equipment/invisibility notices; `minigameaim`: Zombie/Halloween aiming, prediction and item routines. |
| Gameplay visuals | `ambience`, `blockesp`, `breadcrumbs`, `trajectories`, `esp`, `nametags`, `targetesp`, `shaderesp`, `freelook`, `chams`, `skeletons`, `camera`. Existing `fullbright`, `nofov`, `nobob`, `nohurtcam`, `antiblind` remain independently usable. |

NoSlow's commented-out donor mode files are not exposed by Yuri's module and are not added
as selectable modes. Disabler likewise exposes only the inventory behavior present in the
referenced donor registry. Existing Sprint, Timer and WTap retain their native cleanup and
small intentional fixes documented in [DONORS.md](DONORS.md).

## Native adaptations and differences

- Shared claims arbitrate competing rotation, hotbar, timer, sprint and input changes. A
  lower-priority module can yield; blindly combining incompatible movement modes is not a
  parity guarantee. Raw callbacks read copied state rather than live entities or GUI state.
- Packet buffers hold immutable encoded PLAY snapshots, bounded at 8,192 packets/16 MiB
  per scope (2 MiB per snapshot). They replay in capture order on the client thread. Disable
  flushes only on the same live connection/world generation; disconnect/world replacement
  discards stale data. Overflow faults the module and discards its backlog. Replayed packets
  bypass raw interception to avoid a second buffer capturing the same packet indefinitely.
  Original cancelled send-completion listeners are not reattached to a replay.
- Raw packet modules retain Perihelion's non-local PLAY connection boundary. They do not
  intercept integrated-server traffic, login, status or Forge proxy packets. Integrated-world
  gameplay checks therefore do not prove remote-server Blink/FakeLag behavior.
- Yuri's Blink per-tick buffer reset would discard packets; the native queue retains them
  until flush. Refill uses hotbar-swap mode 2 instead of Yuri's drop mode 4. Stealer's trash
  toggle accepts trash when enabled, and it stops retrying a full inventory. Instant inventory
  loops have a transaction budget so rejected moves or duplicate destination slots cannot hang
  the client. Manager's own open/close packet identities are exempt from Disabler suppression.
- Scaffold searches use eye rays without temporarily changing player position. Ultra Safe
  retains Manhattan candidate ordering and rejects rays outside the support cube before doing
  a world trace. Placement support search is limited to nearby reachable blocks. Native
  placement counters/claims are separate from Yuri's shared global counters. Tower and bridge
  modes still depend on the actual world and server rules.
- Auto Swap's server-selected mining tool participates in vanilla hardness, held-tool matching,
  durability and break cleanup. The visible slot is restored around strength calculations.
- Aura ML has native `ml-preset` and `ml-samples` settings. `legit.csv` and `ai.csv` donor data
  are included with attribution. Custom data is semicolon-separated `yaw,pitch` pairs. The
  donor's global recording commands/file manager are not imported. Invalid/nonfinite rows are
  skipped, parsing and retained samples are bounded, and an empty model falls back to normal aim.
- Visuals use native text and scoped render state. Target ESP retains its four visual concepts
  using native geometry rather than the donor's branded image assets and glow-dot textures.
  ESP/name-tag font appearance is therefore different. Ambience implements local time;
  donor-branded shader skies and theme-colored fog are omitted as client appearance features.
  Chams covers player base models and optional tile entities; armor render layers are not
  forced through walls. Shader ESP uses a native silhouette outline/glow shader rather than
  Yuri's shader framework. These are functional visual ports, not pixel-identical copies.
- Free Look uses its assigned held key (an unbound toggle can be disabled manually). Camera
  exposes hand transforms, swing slowdown and visual effect suppression while the existing
  independent visual modules remain available.
- Auto-disable settings retire on the next native world activation after a world change.
  Cleanup happens at world loss; no stale packet, target or control state survives the transition.
- Auto Chat starts with an empty registration password. Supply one explicitly to enable that
  behavior. Server messages and game-specific prompts still depend on the server's language
  and protocol. No automatic messages were sent to a public server during development.

## Excluded presentation modules

DiscordRPC, GUIClicker, ToggleSounds, Animations, Capes, ChinaHat, ClickGUI, ClientInfo,
Effects, Hotbar, ImageRenderer, MediaInfo, ModList, MotionBlur, Pentagram, PostProcessing,
Scoreboard, SessionInfo, TargetHud, Watermark and YuriChat. Perihelion keeps its own GUI,
fonts, theme, config and HUD facilities.

## Backup and rollback

Before the port, 192 nonignored project files were copied to
`.backups/before-yuri-20260926-171117/source.tar.gz`. Each archive member was checked against
`manifest.json`; archive SHA-256:
`c9182e52ba0e3212b12b1e3c18be9bc981127de4e32963ac5ca36ffd0f3333cb`.
The backup excludes generated output, runtime worlds, caches, tools and `.git`.
Run `python3 scripts/restore-yuri-backup.py` for a checksum-verified dry run. The optional
`--apply` mode first backs up the affected current files and refuses to overwrite later edits.
See that directory's `RESTORE.txt` and `changes.json` for restoration details. Preserve any
subsequent edits before restoring; no automatic destructive rollback is run by this port.

## Verification

`python3 scripts/yuri-port-inventory.py` checks donor inventory completeness and native IDs.
`./gradlew check` tests core/adapter behavior and validates the packaged distribution.
The isolated packaged Forge/OptiFine probes exercise lifecycle cleanup plus selected actual
movement, combat, inventory, packet replay and rendering paths. See [VERIFICATION.md](VERIFICATION.md)
for the exact passing runs and remaining limitations. No authenticated multiplayer session
or server-specific bypass effectiveness has been verified.

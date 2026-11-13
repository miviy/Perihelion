# Default and hypxile presets

`default` is a snapshot of the existing untouched module and GUI definitions before
the working configuration loads. Fresh startup behavior is unchanged. `hypxile`
starts with those same defaults, then applies the compatible values from the
user-supplied `my config.json`. Neither preset selects itself or overwrites an
existing working setup. Both are normal JSON profiles in `config/perihelion/configs/`.

The conversion maps **50 of 54 source modules and 225 setting values**, including
settings for disabled modules. The 12 supported enabled modules are Anti Bot,
Anti Fireball, Auto Swap, Back Track, Bow Aimbot, Fast Break, Hit Select, Input
Fixes, FastPlace (Yuri's Item Delays), Sprint, Velocity and WTap. Aura remains
disabled with **R** as its binding; Scaffold remains disabled with **B**. ClickGUI
keeps its normal **Right Shift** binding. All other native modules retain defaults.

Only values reachable through native GUI controls are overridden. Conditional
controls are included when a normal mode/toggle can reveal them. Permanently hidden
legacy values are left at defaults. No module code or setting definitions changed.
The resource is already converted native JSON; this does not add a general Yuri importer.

## Mappings with different labels or representations

| Source | Native control |
| --- | --- |
| Item Delays | FastPlace: Delay 0, Allow Blocks true, Allow Projectiles false |
| Auto Armor / Eagle / Manager / Long Jump delay | Existing Delay (ms) controls |
| Back Track Max Delay 80 | Delay 80 ms, retaining the native Basic/New defaults |
| Breaker Break Mode / Breaker Range | Mode / Range |
| BedWars Sword Reveal / Armor Reveal / Include Stone | Notify Sword / Notify Armor / Include Stone Swords; closest current warning controls |
| Manager Manage Blocks | Manage block |
| Inv Move Mode VANILLA | Inventory VANILLA and Chest & Others VANILLA |
| Scaffold Sprint Mode / Tower Mode | Sprint / Tower |
| Scaffold Block Counter SIMPLE | Block Counter true; native counter has a boolean control |
| Aura rotation speeds 5.500000081956387 / 8.00000011920929 | Existing slider values 5.5 / 8.0 |

Native controls absent from the source retain native defaults. This includes newer
Hit Select controls, Velocity Reduce Ticks=2, Bow Aimbot prediction, inventory
protections and GUI/HUD preferences. Matching a setting does not recreate an older
version's behavior or establish server acceptance.

## Unavailable controls left unchanged

| Module | Source options omitted |
| --- | --- |
| Discord RPC, GUI Clicker, Insults, Toggle Sounds | Entire modules unavailable; Auto Chat is a different feature and is left unchanged |
| Bed Defender | Show Progress |
| BedWars Utility | Invisible Check, Invisibility Status |
| Blink | Render Blink Pos |
| Bow Aimbot | Targets, Mode, Lock View, Min Rotation Speed, Max Rotation Speed |
| Breaker | Progress Bar |
| Disabler | Mode selection; there is no native GUI selector |
| Hit Select | Chance, Combo Delay, Reset Timeout, Predict, Predict Reach, Swing Threshold, Idle Attack, Idle CPS |
| Lag Range | Render Lag Pos |
| Long Jump | Mode MOSPIXEL selector; the native Fireball and Fireball Mode controls are mapped |
| Stealer | Grab Mouse |
| Velocity | Reduce, Attack Times, Only Sprinting, Reduce When Can Attack are permanently hidden legacy keys; they are not overridden |
| All modules | Yuri's `hidden` flag has no native GUI equivalent and is not imported |

The exact mapped property list, omissions and source SHA-256 are recorded in
[the mapping ledger](verification/hypxile-config-mapping.json). The packaged resource is
`forge/src/main/resources/assets/perihelion/configs/hypxile.json`.

## Installation and verification

Installation uses the existing serialized config writer and creates files only when
there is no same-name preset, backup, deletion recovery or installation receipt.
Each preset has its own hidden receipt, independent of profile contents and
`client.json`. Updating, deleting, loading `default`, reloading the working config,
or restarting cannot reinstall a successfully installed/deleted preset. Ordinary
profile saves remain explicit and retain existing backups/recovery behavior.

Regression coverage checks untouched startup/default equality, exact profile
round trips without fallback warnings, source bindings and enabled states, GUI
reachability for every override, user edits, deletion/restart, existing files,
link rejection and malformed-file preservation. The packaged GUI smoke also checks
both presets in the normal browser and the installed resource's converted value.

Final `check assemble --offline` passed **383 tests** (160 core, 223 Forge), with
zero failures, errors or skips, plus architecture, dependency checksum and JAR
checks. Packaged **Forge and OptiFine M5 UI smokes passed**: 343 GUI assertions
each, config CRUD, HUD/render checks, unload/rejoin and full cleanup. All 291
module/setting classes are byte-identical to the prior packaged client; only
config installation/storage and bootstrap classes changed. All 255 source
properties were accounted for as mapped or omitted, and the original file's hash
remains unchanged. Full evidence is in
[config-presets-20261018.json](verification/config-presets-20261018.json).

The Forge smoke's independent shutdown report recorded `stdoutError=true`;
all cleanup assertions passed. The OptiFine run recorded `stdoutError=false`.
The deliberately throwing HUD fixture is expected. These local tests do not
establish authenticated or public-server acceptance.

Built artifact: `forge/build/libs/Perihelion-0.1.0.jar`, SHA-256
`cafd0ba2fd01f757a4a8b2e3aa75c3cc9624d78260d2570b969bf8502175edfc`.

Pre-change source snapshots are hash-verified under
`.backups/config-presets-20261018/` (`before.tar.gz` and `before-followup.tar.gz`).
The original Desktop file and donor repositories remain unchanged.

# Selectable client visuals

The initial presentation described below is superseded by the independent Raven
frontend and HUD in [RAVEN_INDEPENDENT_2026-10-20.md](RAVEN_INDEPENDENT_2026-10-20.md).
The verification below records the earlier version.

Right Shift opens the ClickGUI. In **Misc**, right-click **ClickGUI**, then expand
**Client style** and select **Adjust** or **Raven**. The setting is also searchable
as `client style` when the search bar is enabled.

Adjust is the default and preserves the existing presentation. Raven uses native
Minecraft text, taller centered module rows, dark bordered headers, blue active
rows, beveled buttons and thicker slider rails. The same theme applies to Configs
and HUD editing. Raven's HUD has a compact watermark, edged module list, classic
text/health-bar target card, text-only effects and beveled keystrokes.

Switching changes presentation immediately and persists through the ordinary
client config and named presets. Each style has its own saved panel layout, so
Minecraft font widths do not overlap panels from the Adjust layout. Switching
preserves module states, HUD selection/positions, custom colors and the saved
Adjust font. The Font control is
shown in Adjust; Raven uses Minecraft text for consistent pixel metrics. Custom
text colors and weights remain available in both styles. Resizing and changing
style remeasure controls and recover panels using the existing layout rules.

## References and boundaries

OpenB4 is a read-only behavior reference at
`../donor_clients/OpenB4/OpenB4/`, commit
`3d63944cc79be890c013cc79e5ed92d3533b48a5`. Relevant sources are
`gui/CategoryComponent.java`, `gui/ModuleComponent.java`, `gui/SliderComponent.java`,
`gui/ButtonComponent.java`, `module/HudModule.java`,
`module/render/TargetHudModule.java` and `client/InfoHud.java` under
`src/main/java/xyz/daikirai/`.

The existing Adjust overlay and its Modern-Lazify reference
(`50428890f1f6754ad5b0859293d9421420c3ac9b`, `LazifyHud.java`) were inspected for
measured text, row/header treatment and position behavior. All rendering remains
native to Perihelion. This is an adapted visual style rather than an exact copy
of OpenB4's current rounded/animated menu. Donor module managers, events, threads,
config code, branding animations and command console are not imported.

The title and its native menu family keep their existing presentation. This
first style selection covers the in-game ClickGUI, its Configs/HUD tools and HUD.

## Backup and reversal

Before editing, 742 source/build/script/documentation files were archived and
each member was checked against its SHA-256 manifest in
`.backups/before-client-styles-20261019-110718/`. Archive SHA-256:
`974abcf20e41b8d6e78985b957149cacc243d6d89449ab4a85b006b6e920729d`.

`python3 scripts/restore-client-styles.py` verifies the backup and affected files
without editing. `--apply` saves another checkpoint and restores only this
change's files; it refuses later edits. Rebuild after restoration. Configs,
worlds, credentials, caches and donor folders are outside the rollback scope.

## Verification

- `check assemble --offline` passed 460 tests (161 core, 299 Forge), with zero
  failures/errors/skips; architecture, wrapper, pinned Mixin and packaged-JAR
  verification also passed.
- Final artifact: `forge/build/verified/client-styles-20261019/Perihelion-0.1.0.jar`,
  SHA-256 `e2cb48898a2520d2ee93adf30a1b70606f2fb056dec5e2dfdc3380d4ce15dda3`.
- Same-artifact native UI smokes passed on Forge (`forge/run-smoke-forge-9jj6xv05`)
  and OptiFine M5 (`forge/run-smoke-optifine-curp1ai7`). Each passed 471 client-style
  checks, 345 existing GUI checks, 88 GUI regressions and 93 production-HUD checks,
  plus title/menu, unload/rejoin and clean shutdown checks (`subscriptions=0`,
  `queued=0`, `stdoutError=false`).
- The style fixture drives the real Client style dropdown; tests shared chrome
  and effective-font identity in Modules, Configs and HUD editing; checks default
  panels for overlap; exercises measured HUD bounds and extreme drag recovery at
  320x240, 427x240 and the live resolution; compares GL snapshots; and round-trips
  a named preset while preserving the saved Adjust font.
- Framebuffers `ui-raven.png`, `ui-raven-configs.png`, `ui-raven-hud-editor.png`,
  `ui-raven-hud.png` and `ui-adjust-restored.png` are in those run directories.
  Raven GUI/HUD captures were visually reviewed. Screenshot review of the first
  iteration exposed shared-layout overlaps; independent style layouts and a
  regression assertion fixed that before the final runs.
- Guarded rollback passed dry-run, later-edit-refusal and isolated-restore checks;
  the live workspace was verified unchanged by that restoration test.

These are offline local Minecraft and logical input-callback checks. They do not
establish physical input behavior on every system, arbitrary resource-pack/GPU
compatibility or live-server behavior. The final test client is a separate
production-only offline preview with Raven selected and a local creative world;
no probe mod or automatic shutdown is installed there. Exact results are in
`CLIENT_STYLES_VERIFICATION_2026-10-19.json`.

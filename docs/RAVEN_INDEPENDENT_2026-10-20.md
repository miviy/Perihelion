# Independent Raven presentation

The initial Raven option used the Adjust screen with conditional decoration and
several shared HUD draw branches. This revision replaces that visual path.

- `PanelScreen` draws Adjust. `RavenScreen` draws Raven. Both use
  `ModulePanelScreen` for native module/setting state, layout persistence, input,
  capture and cleanup; the common controller contains no screen presentation.
- Raven has its own floating icon categories, closed blue/purple frames, active
  rainbow outlines, black translucent fill, centered Minecraft module names,
  native wordmark, toolbar and binding dialog. Default categories start closed
  down the left edge, as in OpenB4. Left-click an icon or right-click its title to
  open; drag the title to move. Existing module toggle/settings/binding actions
  remain native.
- `RavenControls` paints every setting kind through an explicit early dispatch.
  Adjust's widget drawing is never called for a Raven canvas. Measured rows,
  constraints, validation, edits and mutation stay owned by native controls.
- `RavenTheme` configures its entire palette and paints utility surfaces itself.
  Configs and HUD editing share their native interaction controllers and use this
  independent Raven chrome and setting painter.
- `RavenHudRenderer` measures and draws all ten HUD elements, navigation and the
  player popup. `HudRenderer` supplies native snapshots and hitbox/anchor logic,
  and returns through Raven before entering Adjust's draw scope. A guard rejects
  Raven falling through to an Adjust element.

The setting remains **Misc > ClickGUI > Client style > Adjust / Raven**. Switching
replaces the frontend, preserves native settings, restores the saved Adjust font,
and uses independent panel and utility-window positions for each style. Raven's
new panel-layout version starts with the new category arrangement rather than
loading the previous Adjust-shaped grid.

## Read-only references

OpenB4: `../donor_clients/OpenB4/OpenB4/`, revision
`3d63944cc79be890c013cc79e5ed92d3533b48a5`.

Relevant sources below `src/main/java/xyz/daikirai/`:
`module/GuiModule.java`, `module/Category.java`, `gui/CategoryComponent.java`,
`gui/ModuleComponent.java`, `gui/SliderComponent.java`, `gui/ButtonComponent.java`,
`module/HudModule.java`, `module/render/TargetHudModule.java`, `util/Theme.java`,
and the corresponding HUD helpers. The visual reference includes actual frame
colors/opacity, icon categories, text states, slider treatment and the default
Frost HUD palette. Explicit native color customization still wins.

No donor runtime, event bus, module/config system, console, credential handling or
background tasks are copied or executed. Perihelion's own module inventory and
existing provider data are retained. Fonts, GL scopes and input cleanup remain
native. This work covers in-game client visuals; the title/menu family is outside
this change.

## Reversal

Before editing, 746 source/build/script/documentation files were archived and
every member hash verified in
`.backups/before-raven-independent-20261020-120745/`. Archive SHA-256:
`adc04e68c56914801662818183a2d78ba97c09f3ccbf14c2d794d58657c2cb0f`.

Run `python3 scripts/restore-raven-independent.py` for a verification-only dry run;
`--apply` saves current affected files, refuses later edits, and restores this
revision's source changes only. Rebuild afterward. It does not restore or delete
worlds, user configs, credentials, donor repositories or generated artifacts.

## Verification

- `check assemble --offline` passed 461 tests: 161 core and 300 Forge, with zero
  failures/errors/skips. Architecture, wrapper, pinned Mixin and packaged-JAR
  checks passed.
- Artifact: `forge/build/verified/raven-independent-20261020/Perihelion-0.1.0.jar`,
  SHA-256 `3dff0e002efd9ee7e6186edaacdd0c0c3d0a195e1e358342581fe88c00f919be`.
- Same-artifact native UI runs passed on Forge (`forge/run-smoke-forge-t7d_2ki0`)
  and OptiFine M5 (`forge/run-smoke-optifine-o26trntv`). Each passed 507 style
  checks, including real dropdown frontend replacement, all ten Raven HUD
  elements, no Adjust draw fallback, independent widget painter selection,
  actual textured icon pixels, full armor text measurement, category opening,
  small-screen/drag bounds, GL restoration and named-preset persistence.
- Existing GUI, text theme, Tab GUI, HUD, config, unload/rejoin and clean-shutdown
  checks passed too. Both runs ended with `subscriptions=0`, `queued=0` and
  `stdoutError=false`.
- Actual `ui-raven.png`, `ui-raven-settings.png`, `ui-raven-category.png`,
  `ui-raven-configs.png` and `ui-raven-hud-editor.png` framebuffers were reviewed.
  Review caught and fixed untextured category icons, clipped armor text and
  footer/panel overlap. Long boolean labels use donor-style text states without
  a colliding checkbox. Raven has its own alpha setting; Adjust opacity controls
  are hidden in Raven.
- A production-only offline preview with Raven selected and the isolated creative
  world is left running. It has no probe mod or automatic shutdown.

These checks cover packaged local Minecraft and native logical input callbacks,
not every physical input device, resource pack, shader pack, GPU or live server.
Exact result paths and markers are in
`RAVEN_INDEPENDENT_VERIFICATION_2026-10-20.json`.

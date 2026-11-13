# Compact UI and TabGUI cleanup — 2026-10-14

Settings use consistent 14px minimum rows, measured text heights and smaller control gutters. Values share a row when they fit and stack when they need space. Category panels keep their compact width. Shorter presentation labels, sentence case and the smaller Minecraft menu font retain readable labels without widening the menu.

Enabled ClickGUI modules have a subtle, theme-colored background instead of a left accent line. Hover adds a separate faint overlay so the enabled state remains visible. Boolean settings use row selection shading without on/off text.

The HUD editor has just Hud and Elements controls. Basic/Custom and the duplicated per-element appearance settings were removed. Global theme controls, visibility, positions and functional overlay options remain. Array list options contain alignment, sorting, row spacing, module modes and hide-visual controls. Popups size themselves to their measured content and open only when an element has useful options.

TabGUI keeps a permanent 65 × 66 category anchor while module and settings panes open beside it. Transient pane dimensions do not change the saved position or drag anchor. Panes choose an available side, scroll inside the viewport and draw above other HUD cards. Settings support booleans, numbers, enums, ranges, colors, multiselects and text through the native setting mutation path. Settings input owns keyboard focus and releases it when closed, hidden, unloaded or disabled.

## TabGUI controls

| Context | Controls |
| --- | --- |
| Categories/modules | Up/Down selects; Right opens; Enter toggles a module; Left closes |
| Module settings | Right from the module list opens settings; Up/Down selects; Enter edits or toggles; Left/Escape returns |
| Editing a value | Left/Right changes; Enter finishes; Escape cancels |
| Range/color/multiselect | Up/Down selects a component; Left/Right changes it; Space also toggles a multiselect choice |
| Text | Type a draft; Backspace removes a character; Delete clears; Enter saves; Escape cancels |

## Verification

`check assemble --offline` passed: 319 unit tests (156 core, 163 Forge), zero failures/errors/skips. Geometry tests cover fixed anchors near both edges and the center, small and large viewports, compact settings, scrolling and Unicode edits. Native tests cover actual input focus, setting writes, keyboard-repeat restoration and cleanup.

Both packaged UI scenarios passed with the final artifact at 854 × 480:

| Check | Forge | OptiFine M5 |
| --- | --- | --- |
| GUI interactions, fonts and compact bounds | 327 | 327 |
| TabGUI anchors, settings and input | 91 | 91 |
| Shared UI themes and fractional scale | 233 | 233 |
| Production HUD | 84 | 84 |
| HUD rendering integration | 261 | 261 |
| Render state and native fonts | 61 | 61 |
| Unload/rejoin and shutdown | PASS | PASS |

Runs: `forge/run-smoke-forge-mkxjyn3i` and `forge/run-smoke-optifine-f5jtmb3b`. Both finish with cleared references/hooks, zero subscriptions/queued tasks and `stdoutError=false`. The logs retain the existing legacy Forge signature/Twitch startup messages and OpenAL shutdown warning (`3 Buffers not deleted`). The `hud-smoke-broken` listener error is an intentional fault-isolation probe. These are local native UI results.

The main agent and a visual-review subagent inspected the final screenshots. Labels and values fit, the smaller HUD popup has five readable controls, and the TabGUI settings pane is unobstructed.

Artifact: `forge/build/libs/Perihelion-0.1.0.jar` (1,974,881 bytes). SHA-256: `7c35ed86343c1e14a50a2a12f844bf4f0e9224820e0eab25fef90c70ee010e81`.

A verified source backup is retained in `.backups/before-clean-ui-20261014-145921/` with a per-file hash manifest.

- [Compact ClickGUI settings](../forge/run-smoke-forge-mkxjyn3i/clickgui-settings.png)
- [Enabled module highlighting](../forge/run-smoke-forge-mkxjyn3i/clickgui-enabled-row.png)
- [Smaller Minecraft text](../forge/run-smoke-forge-mkxjyn3i/clickgui-readable-minecraft.png)
- [Reduced HUD options](../forge/run-smoke-forge-mkxjyn3i/hud-arraylist-options.png)
- [TabGUI settings](../forge/run-smoke-forge-mkxjyn3i/tab-gui-settings.png)
- [OptiFine TabGUI settings](../forge/run-smoke-optifine-f5jtmb3b/tab-gui-settings.png)

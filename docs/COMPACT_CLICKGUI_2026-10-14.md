# Compact ClickGUI — 2026-10-14

Opening module settings, dropdowns or color controls no longer widens category panels or changes the menu scale and neighboring panel positions. A category uses its normal module-name width in the selected font. Explicit font changes and screen resizing still recover the layout normally.

Settings flow inside that width. Toggles use a small checked indicator; long labels wrap at word boundaries; numeric labels and option values stack when they cannot share a row. Dropdown entries and text values use their measured row heights for scrolling and clicking. Multi-select summaries show selected/total, range controls use Min/Max, and color hex values omit the redundant prefix. Keybind rows omit the Bind prefix when needed to show the key name. Text size stays unchanged.

Control positions are updated even below the viewport, so scrolled rows, inline editors and conditional settings use current geometry. Existing drag, collapse, search, scroll and Configs/HUD navigation remain available. This replaces the setting-driven panel expansion described in the historical READABLE_GUI_CONFIGS_2026-10-13 report.

## Verification

`check assemble --offline` passed 308 tests (156 core, 152 Forge), with zero failures, errors or skips. New regression cases cover narrow wrapped labels, stacked numeric values, selection after wrapped dropdown entries, and color-picker hit positions after a wrapped title.

The native GUI probe opens all production modules across all four fonts at menu scales 0.75 and 1.5 and verifies that every panel retains its x/y/width and the menu retains its scale. It also verifies full Ender chests and Through walls labels, scrolling, inline entry, conditional visibility and configuration operations.

Final artifact: `forge/build/libs/Perihelion-0.1.0.jar`.
SHA-256: `5e3b50f85b5b341d52af521e6a7a55cc0d0f81f304fbe4d8f225bfa1ec6c8a21`.

Both final packaged UI runs passed at 854 × 480 with exit code zero and the artifact hash above:

| Check | Forge | OptiFine M5 |
| --- | --- | --- |
| GUI interactions and stable panel bounds | 331 | 331 |
| UI typography, navigation and fractional scale | 240 | 240 |
| Production HUD | 84 | 84 |
| Scenario, unload/rejoin and teardown | PASS | PASS |

Runs: `forge/run-smoke-forge-_ikdogbp` and `forge/run-smoke-optifine-o3bx2w91`. Both clear world/player/network-manager references and the installed hook, and finish with zero subscriptions and queued tasks. Screenshots were visually inspected for compact settings in Sans and Minecraft fonts. These checks exercise local GUI callbacks and an integrated world.

- [Compact settings](../forge/run-smoke-forge-_ikdogbp/clickgui-settings.png)
- [Block ESP in Sans](../forge/run-smoke-optifine-o3bx2w91/clickgui-readable-sans.png)
- [Block ESP in Minecraft font](../forge/run-smoke-forge-_ikdogbp/clickgui-readable-minecraft.png)

The final OptiFine shutdown report has `stdoutError=true`: the probe's stdout stream reported a write error during shutdown. Its fallback report still records the successful reference/hook cleanup and STOPPED assertions, and the launcher reports PASS. This output-stream warning remains unresolved. Both runs also log the OpenAL warning `3 Buffers not deleted` at audio shutdown. These warnings are retained separately from the passing GUI evidence.

A 640 × 360 OptiFine attempt stopped before ClickGUI in the existing HUD fixture: “GUI scale 2 was not refreshed.” Minecraft cannot select GUI scale 2 at that height. That run provides no ClickGUI evidence. Native GUI validation uses 854 × 480. Sandboxed macOS Java launches also aborted during AWT registration; desktop-enabled runs are used for native results.

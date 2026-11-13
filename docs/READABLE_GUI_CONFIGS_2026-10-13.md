# Readable labels and separate Configs — 2026-10-13

Panels now measure their full module names, setting labels, options and value controls with the selected font. Expanded panels widen as needed; their rendering, mouse hit areas, scrolling and screen-edge recovery use the same width. Long text values wrap into complete lines. Inline entry keeps its existing font, row and horizontal cursor scrolling. The one-pixel scrollbars are retained.

The attached Block ESP example is covered explicitly: **Ender chests** and **Through walls** render completely in Sans, Minecraft and Monospace, including the smaller OptiFine window. Font size and optional bold/color defaults are unchanged.

**Configs** now opens a dedicated `ConfigScreen`, like the HUD editor. No module panels or vanilla chat appear behind its controls. Its preset list and actions open together; full preset names determine the required width, and status messages wrap. Done/Escape returns to the same menu. Left-drag/right-click still move/collapse the dialog header. Preset input owns keys while focused, Escape first leaves that field, and closing releases the screen's font textures and held keys. Save/load/update/delete retain the native asynchronous operations and confirmations.

**ClickGUI** and **Input Fixes** are grouped under **Misc**. The main menu has six populated categories. The empty legacy Client category remains compatible with old metadata/layouts and takes no space on screen. Category values are definition metadata, so existing module settings, bindings and enabled state retain their IDs and behavior.

## Verification

`check assemble --offline` passed with 281 unit tests: 156 core and 125 Forge, no failures/errors/skips. The same saved artifact passed packaged native UI scenarios in Forge (1192 × 794, GUI scale 2) and OptiFine M5 (854 × 480, Auto).

| Check | Forge | OptiFine M5 |
| --- | --- | --- |
| GUI behavior, config operations and complete Block ESP labels | 242 | 242 |
| Shared fonts, fractional scale, Configs navigation/cleanup and chat suppression | 230 | 240 |
| Production HUD | 83 | 83 |
| HUD chat interaction | 88 | 88 |
| Native render helpers | 55 | 55 |
| UI scenario, unload/rejoin and teardown | PASS | PASS |

Both runs clear references/hooks and finish with zero subscriptions/queued work. Each contains the expected intentional HUD fault fixture; neither contains a smoke failure marker. This is local/integrated-world UI evidence, not authenticated multiplayer or full gameplay proof.

Runs: `forge/run-smoke-forge-pe4d3j_v` and `forge/run-smoke-optifine-l8p4e3sl`.

- [Complete Minecraft labels](../forge/run-smoke-forge-pe4d3j_v/clickgui-readable-minecraft.png)
- [Separate Configs screen](../forge/run-smoke-forge-pe4d3j_v/ui-configs-sans.png)
- [Main menu with client controls in Misc](../forge/run-smoke-forge-pe4d3j_v/ui-default-adjust.png)

Saved tested JAR: `forge/build/verified/readable-configs-20261013/Perihelion-0.1.0.jar`.

SHA-256: `a6fe11e7cf50169047dbedd7253f1f8127de75ae1ab5ea8eff4769ae79ebc093`.

The source backup is `.backups/before-readable-configs-20261013-042812/source.tar.gz`: 285 entries were individually hash-verified. The adapter's pre-edit source is separately preserved under that backup's `extras/` with its checksum. The affected-file manifest records this pass's files; donor/reference folders were read-only throughout.

Detailed hashes and native results: [verification JSON](READABLE_GUI_CONFIGS_2026-10-13.json).

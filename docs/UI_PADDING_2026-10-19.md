# GUI and HUD padding — 2026-10-19

Module/category headers, module names and expand markers now use the selected font's visible glyph metrics for vertical centering. Bind labels have the same inset as their settings. Sliders, inline setting edits, color controls and config name fields use consistent text placement.

Tab GUI category/module rows, setting labels/values, hints and toggles are centered within their existing rows. Player tables, effects, keystrokes/CPS, notifications, target cards and info text use the same font-aware alignment. The watermark is centered vertically and its editor bounds follow its measured text instead of a fixed minimum width.

Panel sizes, row hit areas, scrolling, saved layouts and module behavior are preserved. No donor files were used or changed.

Validation: `check assemble --offline` with `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` passed 458 tests (161 core, 297 Forge), with zero failures, errors or skips. Headless mode avoids macOS AWT application-registration failure in the restricted test runner; actual rendered fonts are checked separately in native Minecraft.

Final artifact: `forge/build/libs/Perihelion-0.1.0.jar`, SHA-256 `4092cdd703cb84d9de1691348ccfe632f1c61b2ca0d30610d1e6f724afeb84f2`.

The initial Forge UI run passed at `forge/run-smoke-forge-hu2bd4oe` before the final watermark/info-card refinements. The final OptiFine M5 UI run passed at `forge/run-smoke-optifine-27vtzfai`: GUI 345 checks, Tab GUI 91, UI themes 254, GUI regressions 88 and production HUD 93. Screenshots were inspected for module headers, expanded bind/settings rows, config rows and HUD/Tab GUI alignment. Both native runs completed unload/rejoin and clean shutdown with zero subscriptions/queued tasks and `stdoutError=false`.

- [Final HUD and Tab GUI spacing](../forge/run-smoke-optifine-27vtzfai/ui-hud-editor-sans.png)
- [Final Minecraft-font module panels](../forge/run-smoke-optifine-27vtzfai/ui-modules-minecraft.png)
- [Final Tab GUI setting rows](../forge/run-smoke-optifine-27vtzfai/tab-gui-settings.png)

A separate production-only client at `forge/run-smoke-forge-hhsfbqx1` uses the final artifact and a copy of the isolated test world. Its smoke probe is disabled and it has no automatic shutdown timeout.

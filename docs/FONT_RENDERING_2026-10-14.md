# Font rendering and simple switches — 2026-10-14

Minecraft text uses 75% of the native font size throughout the shared menu and HUD fonts. Width measurement, bold text, formatted HUD text, Unicode fallback and drawing use the same scale. The regular game/chat font remains managed by Minecraft. Other menu fonts retain their size and now use fractional glyph advances measured from the same antialiased 4× atlas used for drawing.

Menu module/setting/enum labels use sentence case, including camel-case names. Toolbar labels use Hud rather than HUD. Toggles use a small track with a thumb that moves left/right; no on/off text or x glyph is needed. The same indicator is used for setting toggles, dropdown choices, Rainbow, the HUD enable button and HUD element visibility. Values and bindings keep their stored IDs and behavior.

HUD option controls are measured before computing popup height and scroll position, so font transitions use current row geometry. The compact category width behavior from the preceding change remains in place.

## Verification

`check assemble --offline`: 308 tests (156 core, 152 Forge), zero failures/errors/skips. Presentation regressions cover sentence-case acronyms, enum names and camel-case labels.

Both packaged native UI scenarios passed at 854 × 480:

| Check | Forge | OptiFine M5 |
| --- | --- | --- |
| GUI interactions and stable panel bounds | 332 | 332 |
| Fonts, fractional scale and shared screens | 239 | 239 |
| Render helpers, scale metrics and GL state | 61 | 61 |
| Production HUD | 84 | 84 |
| Unload/rejoin and shutdown | PASS | PASS |

The render probe explicitly checks Minecraft width/height at 0.75× native, mixed Unicode advances for all four faces, and GL/matrix restoration after drawing. Existing UI probes exercise switches, conditional visibility, inline entry, dropdown selection, dragging, collapse, scrolling, config operations, all four fonts and menu scales 0.75/1.5. Shared typography also runs at fractional scale 1.05. Minecraft and Sans screenshots were visually inspected.

Runs: `forge/run-smoke-forge-e4gngfw8` and `forge/run-smoke-optifine-giipm00x`. Both finish with cleared world/player/network-manager references and hooks, zero subscriptions/queued tasks, and `stdoutError=false`. The existing OpenAL audio shutdown warning (`3 Buffers not deleted`) remains separate from the passing UI/render checks.

Artifact: `forge/build/libs/Perihelion-0.1.0.jar`. SHA-256: `81831906bca961b5319fefdde793b0299931cd562201bc21ccc8b376a1f7ed08`.

- [Smaller Minecraft text and switches](../forge/run-smoke-forge-e4gngfw8/clickgui-readable-minecraft.png)
- [Sentence-case menu and updated font spacing](../forge/run-smoke-forge-e4gngfw8/clickgui-settings.png)
- [HUD editor with the shared controls](../forge/run-smoke-optifine-giipm00x/ui-hud-editor-minecraft.png)

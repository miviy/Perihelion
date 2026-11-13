# Toolbar and Array list spacing — 2026-10-15

Hud, Modules and Configs use the same measured button width and consistent 4px gaps. The previous default toolbar positions migrate to the new spacing; independently dragged utility tabs retain their saved positions. The HUD editor's two controls also share an equal width and 4px gap.

Array list row height follows the selected font with a compact minimum, and visible text is centered using that font's ink metrics. The row-spacing setting applies uniformly to drawing and drag bounds. Label gutters are symmetric. The options popup uses equal 4px side insets and balanced padding below its header and after the final control; rendering, scrolling, inline editing and clicks use the same geometry.

`check assemble --offline` passed with 319 tests (156 core, 163 Forge), zero failures/errors/skips. Both packaged native UI runs passed: GUI 329 checks, TabGUI 91, UI themes 236, production HUD 93, render state 61. The added native checks cover equal default toolbar gaps, equal button widths across font weights, and Array list spacing/bounds with Minecraft, Sans and Mono. Both scenarios passed unload/rejoin and shutdown with cleared references/hooks, zero subscriptions/queued tasks and `stdoutError=false`. Existing Forge/Twitch startup messages, intentional HUD fault-isolation output and the OpenAL shutdown warning remain separate from these passing checks.

Runs: `forge/run-smoke-forge-il9ztt54` and `forge/run-smoke-optifine-phc6coj7`. Forge screenshots were visually inspected.

Artifact: `forge/build/libs/Perihelion-0.1.0.jar`, 1,975,216 bytes, SHA-256 `61e09bdbe0bed68b5015cc7a54cb959ee09a07330e6df3ed0939e866bc24957b`.

- [Consistent toolbar](../forge/run-smoke-forge-il9ztt54/clickgui.png)
- [Array list options](../forge/run-smoke-forge-il9ztt54/hud-arraylist-options.png)
- [Array list with Sans](../forge/run-smoke-forge-il9ztt54/hud-arraylist-spacing-sans.png)
- [Array list with Minecraft](../forge/run-smoke-forge-il9ztt54/hud-arraylist-spacing-minecraft.png)

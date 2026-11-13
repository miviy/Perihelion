# GUI bug fixes — 2026-10-18

- Escape closes ClickGUI even with a search or HUD action menu open. Escape returns
  from Configs and the HUD editor to their parent, including when Elements or Options
  is open. An active inline edit or key binding still consumes its first Escape to
  cancel the draft. Closing ClickGUI cancels pending toolbar clicks.
- Hide visual modules refreshes the Array list before its next render in both the
  editor preview and the production HUD, without waiting for a world snapshot.
  Render modules are excluded while gameplay modules stay visible.
- Category panels have a minimum width of 132 logical pixels, up from 80. Fonts and
  text sizes are unchanged; long labels retain the existing wrapping fallback.
- Opening module settings lets a panel grow beyond its initial stacked-layout
  height. Every expanded panel uses the same 64% viewport-height cap and stops
  before the screen bottom, then scrolls. Panel positions and widths stay stable
  during expansion.
- Modules, Configs and the HUD editor now keep separate viewport metadata. Visiting
  an editor at a different logical scale cannot rescale the main toolbar or another
  editor's controls. Saved dragged positions and equal toolbar gaps are retained.

## Verification

`check assemble --offline` passed 407 tests (160 core, 247 Forge), with no failures,
errors or skips. The existing `.backups/yuri131-pinned-mixin.init.gradle` selected
the verified local Mixin dependency; checksum and distribution checks remained
enabled. Java 8 font tests and native game launches used macOS desktop access.

Both packaged UI runs passed GUI 343, TabGUI 91, UI themes 254, production HUD 93,
render state 61, and the new GUI regressions 88. The new regressions exercise the
game's virtual keyboard callback, screen transitions across four fonts and three
scales, exact toolbar coordinates, expansion bounds, and the real Hide Visuals
control with both renderers. Both runs passed unload/rejoin and shutdown with
cleared references, zero subscriptions/queued tasks, and `stdoutError=false`.

- [Forge result](../forge/run-smoke-forge-t56kjcmn/result.json)
- [OptiFine M5 result](../forge/run-smoke-optifine-a2ab1gmu/result.json)
- [Wider setting labels](../forge/run-smoke-forge-t56kjcmn/clickgui-readable-sans.png)
- [Expanded settings](../forge/run-smoke-forge-t56kjcmn/gui-expanded-settings.png)
- [Hide Visuals preview](../forge/run-smoke-forge-t56kjcmn/gui-hide-visuals.png)

Artifact: `forge/build/verified/gui-fixes-20261018/Perihelion-0.1.0.jar`, 2,235,023
bytes, SHA-256 `64c1a4f92a0048f8c42e2764d59c945a40a7644535697bf013ae3b56bba63e4f`.
The regular `forge/build/libs/Perihelion-0.1.0.jar` contains the same bytes.

The pre-change source backup is `build/review/gui-bugs-20261018/before.tar.gz`.

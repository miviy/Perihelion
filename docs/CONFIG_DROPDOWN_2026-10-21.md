# Config dropdown

Updated 2026-10-22. Config presets now behave like module rows in the New visuals frontend.

- Left-click a preset to load it using the current visual/keybind choices.
- Right-click to expand or collapse its settings directly underneath the row.
- Inline settings include Load visuals, Load keybinds, Load config, Save current, Delete config, Open file, and Copy path.
- New config expands an inline name field and Save new/Cancel actions. Enter saves; Escape cancels the focused draft.
- File tools expands the folder and refresh actions in the same list.
- Save current and Delete config require a second click to confirm.

The Configs panel uses the same 110-pixel width, 17-pixel module rows, measured setting rows, checkboxes, expansion arrows, settings background, clipping, scrollbar and active theme fill as the module categories. Expanded content remains inside the panel and scrolls normally. Other categories remain interactive, and an expanded config does not take over keyboard input.

Visuals and keybinds are preserved unless their load choices are enabled. The visual choice includes Render modules, HUD, ClickGUI and Themes. Native asynchronous preset storage, recovery copies, current-setup persistence, and the removed separate manager button are unchanged.

A verified source backup of the preceding UI is retained in `.backups/config-inline-20261021/`.

## Verification

- `check assemble --offline`: 511 tests, zero failures/errors/skips.
- Packaged Forge and OptiFine M5 UI smokes: PASS, each with 182 inline config checks and clean unload/rejoin/shutdown.
- Native checks cover regular row expansion/collapse, access to lower actions by scrolling, normal input to other categories, all four visual/keybind combinations, validation and CRUD, 13 fonts, small viewports, and Ocean/Rose themes.
- Inspected framebuffer captures confirm the settings stay inside the category panel and use its width and theme.

Evidence: [verification](verification/config-inline-20261022/verification.json), [Ocean](verification/config-inline-20261022/config-inline-ocean.png), [Rose](verification/config-inline-20261022/config-inline-rose.png).

Verified JAR: `forge/build/verified/config-inline-20261022/Perihelion-0.1.0.jar`

SHA-256: `ce2c233320fb998ad8c1d53f5367efcc99f9bf3fab0e3b763935ac26fe4df33a`.

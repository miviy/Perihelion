# Main-menu presentation

The title uses bespoke geometric vector lettering, an orbital P emblem with a slow
moving glint and a warm-to-silver finish, flat charcoal buttons with subtly rounded
corners and warm-gray hover edges, and irregular animated accretion detail.
The sky includes two depths of twinkling stars, sparse cross-shaped sparkles and
occasional shooting stars. Differential rotation carries bright knots along the disk
and lensed bands; dust flecks follow curved trails that accelerate inward and fade
at capture. The shader bounds particle work and rejects fragments outside each
short trail before evaluating its light kernels. The fixed-function fallback also
includes stars, shooting stars and inward trails.
Cursor motion eases into bounded scene translation (8 horizontal / 6 vertical
logical pixels), disk tilt, star parallax and lighting. Control bounds stay fixed.
Only the client name and its P emblem appear as branding. The complete logo is
centered over the 176-pixel button group, with a stable lower edge and a clear gap
above the first control. Its lettering and position remain fixed during animation.
Button labels reuse the logo's original geometric letterforms at a smaller scale,
with softer contrast and per-label visible-ink centering.
The existing selected HUD/editor fonts and account-form preferences remain scoped
to their original owners. The logo and button atlas share `MenuLetterforms`, with no external typeface asset.

Native world/server lists, dialogs, options, language and mod screens retain their
original instances and controls. Presentation hooks change backgrounds, button
surfaces, high-level labels and text-field borders. Native button drag callbacks
still execute; their knob texture quads become themed rails/thumbs. Text editing,
caret/selection and opaque list masks remain native. Account forms retain selected
fonts, with drawing and measurement scaled together by 1.2 for readability.

The theme is owned by the existing runtime adapter, tracks only descendants of the
title, and is inactive with a world loaded. Shaders, atlases and hover state are
released on leaving the flow or stopping the runtime. The title also releases its
own GPU resources when closed. The accepted prior sources are saved and hash-checked
under `.backups/menu-polish-accepted-v2/` and `.backups/menu-cosmos-2026-10-20/`.

## Cosmos verification

- `check assemble --offline`: 461 tests; no failures, errors or skips. The existing
  Java 17 test-runner and pinned local Mixin init scripts were used.
- Packaged artifact: `forge/build/libs/Perihelion-0.1.0.jar`, SHA-256
  `44e4c70efa145f4f6f8b5efb7b63183dcfef2a2b104e459b920685b47173974e`.
- Forge UI run: `forge/run-smoke-forge-p79d7yyb/result.json`, passed.
- OptiFine M5 UI run: `forge/run-smoke-optifine-6ar1dc4v/result.json`, passed.
- Each run passed 202 title checks, 83 menu-family checks, 345 existing GUI checks,
  native account checks, actual-world scope checks, world rejoin and clean teardown.
- Menu checks include visible star cores, fixed-time repeatability, wordmark disposal,
  animated frame differences, fixed-time pointer response,
  eased convergence/reversal, fixed hitboxes, populated/scrolled lists, native
  button references, server add/edit, direct-connect text selection, world rename,
  create/delete cancellation and actual FOV/audio slider drag callbacks.

Actual framebuffer captures are in those run directories: `title-screen.png`,
`menu-mouse-right.png`, `menu-worlds.png`, `menu-worlds-scrolled.png`,
`menu-servers.png`, `menu-server-edit.png`, `menu-options.png`, `menu-sounds.png`
and `accounts-list.png`. Timed scene captures are `cosmos-meteor.png`,
`cosmos-infall.png` and `cosmos-infall-later.png`. The verified artifact is also
preserved in `forge/build/verified/menu-cosmos-20261020/`.
Visual review recommended the quieter primary control,
softer disk detail, precise wordmark and larger account text; those changes are
included. The latest review also found the star density, inward trails and orbital
wordmark balanced and legible.

Fixtures use synthetic save metadata and loopback server entries in isolated run
directories. Logical mouse callbacks and real rendering are exercised; these runs
do not establish external-server authentication or compatibility with arbitrary
third-party menus, shader packs, GPUs or resource packs.

## Centered logo revision

The logo now uses drawn geometric letterforms with matching cut terminals and
rounded bowls, plus a separate orbital P emblem and a single animated glint.
The full mark uses the controls' horizontal center and a stable bottom edge;
controls and their hitboxes retain their accepted positions. The accepted prior
logo source is backed up in `.backups/menu-wordmark-2026-10-20/`.

- `check assemble --offline`: 461 tests, no failures, errors or skips.
- Artifact SHA-256: `c72e54923ae4a7014b155ac2dfb263a188149de789b1bfb0845007cb53bdbc08`.
- Preserved artifact: `forge/build/verified/menu-wordmark-20261020/Perihelion-0.1.0.jar`.
- Forge title run: `forge/run-smoke-forge-nti69fx8/result.json`, passed.
- OptiFine M5 title run: `forge/run-smoke-optifine-kt2qurur/result.json`, passed.
- Both runs pass 207 title, 83 native menu-family and 61 rendering checks.
  New framebuffer assertions measure the actual logo ink's center against the
  native button group's center, horizontal containment, screen fit and clear gap
  above the controls. The animated mark also preserves GL state and releases its
  texture on leaving the title.
- The OptiFine harness reported `stdoutError=true` during shutdown; all assertions
  passed and the process exited zero with subscriptions and queue cleared. No new
  shader or render failure appeared in that run's log. Forge reported false.

`title-screen.png` in those run directories shows the final composition.
This logo revision ran the title scenario; the cosmos section above records the
previous full UI and actual-world verification separately.

## Previous button theme verification

Shared main-menu/native-menu buttons now use smoked-charcoal gradients, fine warm
metal edges, opposing cut corners matching the logo terminals, and eased warm
hover light. The title's labels use measured, lightly spaced uppercase lettering.
Button bounds, gaps, IDs, enabled state and native input/drag callbacks remain
unchanged. Disabled controls retain a quieter neutral edge and muted text.

- Prior sources: `.backups/menu-buttons-2026-10-20/`, hash-verified.
- `check assemble --offline`: 461 tests, no failures, errors or skips.
- Artifact SHA-256: `722c7928e14f8bde8d9709af4be07435759deb7162974d5dfed484e12e8a684a`.
- Preserved artifact: `forge/build/verified/menu-buttons-20261020/Perihelion-0.1.0.jar`.
- Forge full UI: `forge/run-smoke-forge-r9cfvaeq/result.json`, passed.
- OptiFine M5 full UI: `forge/run-smoke-optifine-hbjsv3vh/result.json`, passed.
- Both pass 207 title checks, 83 menu-family checks, existing GUI regressions,
  actual-world scope/rejoin checks and teardown, with `stdoutError=false`.
- Native captures: `title-screen.png`, `menu-button-hover.png`, `menu-worlds.png`,
  `menu-options.png`, `menu-sounds.png`, and `accounts-list.png` in both run folders.
- Visual review found the button forms, color, hover strength, text spacing and
  label insets cohesive and readable. Static review found no input, GL-state or
  current-size geometry regression.

## Simplified button presentation

Shared buttons use flat charcoal faces, three-pixel corner radii, thin neutral
borders and a restrained warm-gray hover edge. Main labels use regular sans-serif
lettering with natural capitalization and spacing at the previously accepted
size. Hover eases the colors while the surfaces and text remain stationary.
Native controls and hitboxes are preserved.

- Prior source backup: `.backups/menu-buttons-clean-2026-10-20/`, hash-verified.
- `check assemble --offline`: 461 tests, no failures, errors or skips.
- Artifact SHA-256: `4114ef4f8d254f46989715845b1ec16b09e727849f3b7444df5deefb511b490e`.
- Preserved artifact: `forge/build/verified/menu-buttons-clean-20261020/Perihelion-0.1.0.jar`.
- Forge full UI: `forge/run-smoke-forge-x7iebzeu/result.json`, passed.
- OptiFine M5 title: `forge/run-smoke-optifine-l14y6zik/result.json`, passed.
- Each passes 207 title and 83 native menu-family checks; the Forge UI run also
  passes actual-world scope, GUI regressions and world rejoin. Current rendered
  idle/hover states are `title-screen.png` and `menu-button-hover.png` in both folders.

## Logo-matched button typography

The title and menu button atlas now share the original vector outlines in
`MenuLetterforms`. The logo's existing letterforms are unchanged; the alphabet
and numerals are extended in the same style for menu labels. Lowercase label
characters render with the matching uppercase forms. Buttons use a smaller
6.4-pixel logical cap height, softer neutral text, and actual-ink centering.
Accents/punctuation retain normalized system-font coverage, and extended Unicode
keeps the native fallback. Button surfaces, input handlers and hitboxes retain
the accepted simple presentation.

The menu atlas is owned and released by the existing `GuiFonts` lifecycle.
Selected HUD/editor fonts and account-form preferences retain their original
owners. No external typeface resource is included in this build.

- Prior sources: `.backups/menu-labels-2026-10-20/`, hash-verified.
- `check assemble --offline`: 461 tests, no failures, errors or skips.
- Artifact SHA-256: `2945e4bb1b9dc40698d1afab76f6619d289c690ec27ece1851d02f9333ca3334`.
- Preserved artifact: `forge/build/verified/menu-logo-font-20261020/Perihelion-0.1.0.jar`.
- Forge full UI: `forge/run-smoke-forge-ncfwxfdo/result.json`, passed.
- OptiFine M5 full UI: `forge/run-smoke-optifine-_ng_eg0g/result.json`, passed.
- Both pass 234 title checks, 83 native menu-family checks, existing GUI/actual-world
  regressions, world rejoin and teardown, with `stdoutError=false`.
- New framebuffer checks measure horizontal/vertical ink centering for every main
  button label, check visible rendering and GL restoration, and check that the
  label atlas is released on leaving the title.
- Final captures: `title-screen.png`, `menu-button-hover.png`, `menu-options.png`,
  `menu-servers.png`, and `menu-sounds.png` in both folders.

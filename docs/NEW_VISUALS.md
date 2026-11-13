# New visuals

Main menu > Client Settings offers Open GUI and the saved Old/New visuals choice.
New uses the Perihelion dropdown GUI. Old retains Adjust and Raven, including their
saved colors, fonts, layouts and detailed appearance settings.

## Dropdown GUI

- Drag a category header to move its panel; right-click it to collapse or open it.
- Left-click a module to toggle it. Right-click to expand its settings in place.
- Middle-click a module, or click its expanded Bind row, to change its binding.
- Use the permanent search field or Ctrl+F to filter all categories.
- Scroll a panel for more modules or settings. Right-click a numeric value to type
  an exact value; Enter applies and Escape cancels. Other native setting editors
  retain their existing behavior.
- HUD opens the HUD editor in a world; Configs opens presets. Both return to the
  dropdowns. Escape returns to Client Settings when opened there, or closes the
  in-game GUI. Ctrl+R or `.gui reset` resets only the active generation's layout.

The GUI uses the selected font, bold right-aligned category titles,
left-hand icons, rounded charcoal panels, white dropdown arrows, and full-width
warm orange enabled rows. Search and tools live in a small bottom dock. A seventh
Configs column lists real local profiles, loads them on click, and opens the full
manager. No Script category or unsupported features are invented. ClickGUI exposes
only Visuals, Menu scale, Corner radius, Font, Bold and Tooltips under New. Gameplay settings
remain available. Old appearance values
stay saved without affecting the new theme. The dropdown layout is stored in
`dropdown-layout-v2`, independently of Adjust and Raven. Large windows arrange the
categories in one row; smaller windows wrap them with shared drawing/input bounds.

## Motion and rendering

Module expansion, category/Configs collapse, hover, toggles, opening, and scrolling
use bounded exponential easing. One clock read advances screen-owned animation
records once per rendered frame. Rendering and native input share the displayed
expansion heights; outgoing settings remain clipped while closing. Active slider,
text editor, binding, and drag interactions freeze expansion/scroll geometry.
Repeated layout reload/reset releases discarded panel records.

The world blur owns two cached quarter-resolution targets and a small separable
shader. It does not replace the game's shader group. Unsupported GL/FBO capability
or an unexpected framebuffer uses the unblurred world. The blur restores the
incoming framebuffer/read framebuffer, viewport, program, textures, stencil,
blend/depth/scissor and write-mask state. Icons rasterize at their actual framebuffer
size and reuse one atlas until scale changes. A nine-slice shadow is built once
per open screen; all screen-owned GL resources release on close and recreate
when returning from child screens. Font atlases remain runtime-owned and are
generated lazily on first use. New text uses hinted grayscale glyphs at the final
framebuffer size, integer pixel advances and one-to-one sampling. Its bounded
eight-atlas cache safely restores evicted faces when reused. Old visuals retain
their original font renderer and metrics.

The supplied Moon/Novoline screenshots informed the presentation. No donor source
code was used. The HUD retains the compact native New visuals implementation.

## HUD

The new watermark, active-module list, coordinates and potion effects use compact
text with a small shadow for contrast in the world. Target, key and notification
panels use flat translucent rectangles where grouping helps readability. The new
HUD uses a fixed palette and spacing plus the selected New font and Bold value;
its old colors, opacity, font, logo and shadow
choices remain saved for Old visuals. Element visibility, positioning, array-list
alignment/sort, suffixes, filters and player-overlay operations remain available.

The HUD editor and normal rendering use the same measurements. Switching visual
generations cancels an active HUD drag. Old GUI/HUD rendering and appearance
controls remain accessible through Client Settings.

## Implementation and verification

The dropdowns use Perihelion's existing panel controller, setting editors, font
atlases, render scopes and input lifecycle. No donor code or dependency was added.
The prior dropdown stage is backed up with a verified SHA-256 manifest in
`.backups/moon-gui-20261020/`. The new panel layout has its own revision so saved
positions from the earlier design do not distort the new defaults. Old layouts
retain their existing settings.

`NewVisualsProbe` covers four viewport sizes, title round trips, category
collapse/drag, search, exact edits, bind cancellation, the restricted appearance
controls, HUD/editor/config navigation and GL-state restoration. Legacy probes
continue to cover Adjust and Raven. `GuiAppearanceTest` checks generation and
layout persistence, the fixed new theme and restoration of saved old preferences.

Previous dropdown checkpoint verification on 2026-10-20 passed 467 tests (161 core, 306 Forge), with
no failures, errors or skips. Packaged UI runs passed with Forge at 1280x720 and
OptiFine M5 at 854x480: new dropdown/title 65 checks, new world/editor 14, legacy
GUI 345, old styles 507, and production HUD 112. Both runs shut down with zero
subscriptions/queued tasks and `stdoutError=false`. External servers were not
tested.

The JAR and verification report are in
`forge/build/verified/dropdown-visuals-20261020/`. SHA-256:
`6737408f8c979a53ca176058e73051c18c40191f8b24910cecff45f14c730b9e`.
The interactive OptiFine review is in `forge/run-smoke-optifine-fqbwvn4_/`, uses
the same JAR and an offline test identity, and has the smoke probe removed. The HUD
is enabled in that preview's separate configuration for testing after joining a
world.


## Moon-style checkpoint, 2026-10-21

Two visual review rounds compared actual collapsed, expanded, partial-animation,
and in-world captures to the supplied Moon reference. Final visual grade: 8.9/10;
no visual blockers. This is a close style match, with Perihelion's real modules,
categories and native controls. Small windows wrap four columns plus three with
capped scrolling bodies. Synthetic animation captures clear every frame to avoid
compositing trails that would not occur with the game's world redraw.

`check assemble --offline` passed 472 tests (161 core, 311 Forge), no failures,
errors or skips. Final packaged Forge (1280x720, GUI scale 2) and OptiFine M5
(854x480, Auto scale) UI runs both passed: new title/input 71 checks, new world
16 checks, native blur/resources/reset stress 31 checks plus actual close-time GL
deletion assertions, and the existing Adjust/Raven/account/HUD regressions.
Cleanup reports confirm zero subscriptions and queued tasks. Forge emitted the
known shutdown stdout diagnostic (`stdoutError=true`); its independent shutdown
report passed. OptiFine reported `stdoutError=false`. An earlier isolated run
never reached the title fixture before timeout; it is not counted as verification.

Warmed GUI draw-submission samples (48 per mode, harness included):

| Runtime | Settled median / p95 | Moving median / p95 | Allocated bytes/frame, settled / moving |
| --- | --- | --- | --- |
| Forge, 1280x720 | 0.906 / 1.174 ms | 0.885 / 1.011 ms | 6002 / 4764 |
| OptiFine, 854x480 | 0.587 / 0.819 ms | 0.572 / 0.727 ms | 4642 / 4532 |

These are local draw submission measurements, not GPU-completion or FPS tests.
External servers were not tested.

The verified artifact, captures, native manifests/logs and machine-readable
report are in `forge/build/verified/moon-visuals-20261021/`. SHA-256:
`09d567b52a5fa03ff645b207c068a41ba9b30e0a2a503d1788475a1f0b4fa78b`.
The production OptiFine review is left open in
`forge/run-smoke-optifine-7r1amkfv/`, with the smoke mod removed and HUD enabled
only in its separate preview config.


## Font selector and Bold

New visuals offers **Font** and **Bold** alongside Visuals, Menu scale and
Tooltips. Choices: Minecraft, Sans, Serif, Monospace, Inter, Roboto, Roboto Condensed,
Open Sans, Lato, Montserrat, Poppins, Monaspace Neon, and JetBrains Mono Nerd.
Roboto and Inter provide the closest additional choices to the supplied reference.
Roboto Condensed is the New default; bold category titles remain the default.

The selected family and Bold value apply immediately to dropdown labels,
settings, inline/search editors, child GUI controls and the New HUD/editor.
Minecraft keeps its native pixel font. The fonts from upstream are bundled as
unmodified static Regular/Bold pairs, with original licenses and pinned
checksums in `assets/perihelion/fonts/provenance.json` and `THIRD_PARTY.md`.
No font installation or runtime network request is needed.

Old and New font/weight preferences are independent. New uses `modern-font` and
`modern-bold`; Old retains `font`, custom text style and text weight. Controls
remeasure from the selected face, and active editing/dragging cancels on font
changes. Category geometry stays fixed. Dock button widths grow from measured
text so Minecraft Bold keeps every label complete.

Font atlases own their GPU texture IDs directly. No TextureManager entry retains
the generated pixel arrays after upload. Closing a font owner deletes its GPU
textures. This fixes the existing DynamicTexture retention exposed by repeatedly
opening editors with the larger font list. Tests cover temporary-owner deletion
and unchanged texture-manager map size, in addition to the full repeated screen
navigation matrix. Unit font-metric tests run headlessly; real rendering checks
run in native Minecraft.


The earlier font checkpoint used the full 4x glyph atlas, linear sampling, and a white RGB mask
at every alpha value. This prevents transparent black pixels from darkening thin
stroke edges. Additional lower-resolution mip levels were rejected in visual
comparison because they softened the text; the final renderer has none. Glyph
sizes, advances, centering and native Minecraft bitmap rendering are unchanged.

The final font build passed 479 tests (161 core, 318 Forge), with no failures,
errors or skips. Packaged Forge at 1280x720 / GUI scale 2 and OptiFine M5 at
854x480 / Auto both passed the 13-family regular/bold rendering matrix,
265 title-font checks, 14 HUD-font checks, the existing UI/navigation checks,
texture ownership/deletion and GL-state checks. Both shutdown reports show
zero subscriptions, zero queued tasks and `stdoutError=false`. Independent
review of the final framebuffer captures found no font clipping or alignment
blockers. These are local UI checks; external servers and game FPS were not tested.

The final artifact, captures, manifests and report are in
`forge/build/verified/gui-fonts-20261021/`. SHA-256:
`fce02a2954cafb841ade2bce02cd346a4a4d35112c4a3068a81c2bc57d92375e`.
The interactive production client is open in
`forge/run-smoke-optifine-gfqsnxsh/`, with the smoke mod removed and HUD enabled
only in its isolated preview config. Startup reached `Perihelion started:
modules=95` and the Java process remained running after initialization.

## Reference clarity follow-up, 2026-10-21

The New default is now bundled Roboto Condensed, matching the narrow regular
letterforms in the supplied reference more closely. Body text is larger and
category titles are proportionally quieter. New GUI, child controls and HUD
text rasterize at the final framebuffer size with grayscale antialiasing,
integer pixel advances and one-to-one nearest sampling. This replaces shrinking
the fixed 4x raster through fractional transforms. Old visuals and Minecraft's
native bitmap renderer retain their paths. Explicit saved font choices remain
respected.

The New palette uses warm graphite panels and orange enabled rows. Boolean and
choice settings show a box and an orange tick; labels reserve a measured checkbox
column and wrap when needed. Icons are stronger original category shapes (sword,
runner, bust, globe, eye, gear and document), rasterized at their actual screen
size. Icon/shadow textures have direct GPU ownership and release on close.

New pixel fonts retain at most eight GPU atlases per owner. Evicted referenced
faces safely regenerate on reuse, and closing the owner deletes the resident
textures. No CPU atlas images remain retained. Font, weight and resolution
changes update measurements and cancel incompatible editor drags. New formatted
HUD bold uses the actual bold face, and its shadow falls one physical pixel away.

The final offline build passed 482 tests (161 core, 321 Forge), with zero failures,
errors or skips. Packaged UI checks passed with Forge at 1280x720 / GUI scale 2
and OptiFine M5 at 854x480 / Auto: 343 title-font checks across 13 families and both
weights, 18 HUD-font checks, navigation/input, legacy styles, resource lifetime,
bounded cache reuse and GL-state checks. Both shutdown reports have zero queued
tasks/subscriptions and `stdoutError=false`. Independent review of both sizes,
regular/bold HUD and in-world captures found no remaining visual blockers.

Warmed local GUI CPU draw submission: Forge median 0.879 ms settled / 0.863 ms
moving; OptiFine 0.635 ms / 0.596 ms. These include the harness and measure neither
GPU completion nor game FPS. External servers were not tested.

The artifact, captures, native logs/manifests and report are in
`forge/build/verified/reference-clarity-20261021/`. SHA-256:
`1ea659200d363270f33f23831843728cf7792dc936278d6e320b65bfc968e85d`.
The isolated production OptiFine preview is in
`forge/run-smoke-optifine-ixy8e0l2/`, with the smoke probe removed and HUD enabled.
The pre-change source is preserved in the verified archive
`.backups/reference-clarity-20261021/before.tar.gz`.

## Shared client theme

`ModernTheme` owns the fixed warm graphite/orange palette, rounded surfaces,
inputs, active buttons and checkbox presentation for New visuals. Accounts,
account tools/store/delivery screens, config actions, HUD elements/editor and
title/native menu controls now use this shared presentation. The dropdown's
layout and typography remain as in the reference clarity checkpoint.

The HUD uses orange branding and slim active-module edges, graphite target,
key and notification panels, orange health/pressed states, and matching player
panels and editor controls. Its bounds and interaction layout are retained.
Account and config origins, fields and buttons snap to framebuffer pixels;
config fitting measures the selected face at its actual final scale.

New title/menu buttons use the selected font rather than the custom logo
letterforms. Native menu inputs keep vanilla text editing, selection and focus,
while sharing the selected font's drawing, scrolling and mouse cursor placement.
Colored/bold labels keep that family instead of switching the complete label to
Minecraft's bitmap font. Bold uses the real selected-family face; font/style
measurements remain matched to rendering. Existing Old/Raven presentation and
the native account/config operations are retained.

Long inline/native inputs follow the moving selection endpoint. Clicking at the
left edge of a scrolled input preserves the drawn viewport, while Shift+End/Home
scrolls the selected range into view without invalid caret bounds.

The pre-change source archive and manifest are in
`.backups/client-theme-20261021/`; the native input mixin also has a separate
original copy in that directory.

The final shared-theme build passed 485 tests (161 core, 324 Forge), zero failures,
errors or skips. Its packaged Forge 1280x720 / scale 2 and OptiFine M5 854x480 /
Auto UI checks passed, including 347 font checks, 96 native menu/input checks,
18 HUD-font checks, account/config navigation, Old/Raven regressions and clean
shutdown (zero subscriptions/queued tasks, `stdoutError=false`). Final captures
cover the New config panel, HUD element checkboxes, accounts, native menu inputs
and colored/bold text. Local fixtures do not verify authenticated providers or
external servers; draw-submission samples do not measure game FPS.

Artifact, captures, manifests/logs and report:
`forge/build/verified/client-theme-20261021/`. SHA-256:
`d8bb678e1ac756ccda8041d535b38d05b47f5851e0c503003081cd9a81fc229a`.
The isolated interactive OptiFine client is left open in
`forge/run-smoke-optifine-3h36j94o/`, with the smoke mod removed and HUD enabled in
its separate preview config.


## Rounded corners and resolution scaling, 2026-10-21

New GUI settings include one **Corner radius** slider (0–10, default 5). Zero
uses square corners. Category/config dropdowns, search, dock buttons and shared
New GUI controls use this value; existing panel sizes, row hit areas, dragging
and scrolling remain unchanged. Old visual settings retain their own behavior.

The shadow follows the live rounded outline and draws only outside the panel.
It uses geometry instead of stretching a small blurred texture. A screen-owned
OpenGL 2.0 alpha mask clips enabled rows, hover fills, expanded settings, inline
editors and scrollbars to the same rounded contour. It creates no extra
framebuffer and restores the incoming shader program. On unsupported hardware,
dropdowns use square corners so every label and control remains visible.
Closing the GUI deletes the mask program.

New outline fonts rasterize at the final physical text size, including window,
Minecraft GUI and menu scales. The previous 64-pixel ceiling could shrink text
at high resolutions. Compact glyph shelves now support 256-pixel text rasters;
logical dimensions remain correct above the practical or hardware ceiling.
Glyph atlases share family/weight/style/physical-size keys, so small resolution
changes reuse a raster when its physical size stays the same. Residency is
bounded to eight atlases and 96 MiB per owner, with 32 lightweight layout faces.
Minecraft retains its native bitmap appearance.


Plain menu-button captions now center using their visible glyph ink, including
side bearings and descenders. Body row baselines retain their existing alignment.
The menu pixel probe measures the selected font and the native GUI projection.

This stage passed 495 tests (161 core, 334 Forge). Final packaged UI runs passed
with Forge at 1280x720 / GUI scale 2 and OptiFine M5 at 854x480 / Auto. Each run
passed 72 corner checks, including collapse/fallback/resource cleanup, 442 title
font checks across all 13 families and both weights, and 18 HUD font checks.
Each also rendered the actual panel into a 3840x2160 framebuffer at Auto scale 9
and Menu scale 1.5, with 98-pixel body glyphs and correct corner clipping. This is
an offscreen 4K check; macOS limits the native window dimensions on this desktop.
CPU draw submission medians were approximately 1.02 ms on Forge and 0.86 ms with
OptiFine; these harness samples do not measure FPS or GPU completion.

The verified archive and guarded rollback are in
`.backups/corners-scaling-20261021/`. The final packaged artifact and native
receipts are in `forge/build/verified/corners-scaling-20261021/`.

## Themes and persistent selections, 2026-10-21

New visuals include **Misc → Themes**. Left- or right-click opens its settings;
the palette applies immediately without enabling a gameplay module. The 20
presets are Ember, Copper, Sunset, Aurora, Ocean, Glacier, Forest, Sage, Dune,
Coffee, Rose, Cherry, Coral, Lavender, Iris, Arctic, Cobalt, Honey, Graphite and
Pearl. **Custom** exposes a color count of One, Two or Three and only the
corresponding color controls. Switching presets preserves custom colors.

Each custom color opens a compact saturation/value area, hue strip and editable
`#RRGGBB` field. Picker dragging clamps at its edges and ends on mouse release.
The palette uses opaque static colors; it adds no rainbow, alpha or animation
settings. Shared controls, config/account screens, native menu controls and HUD
accents follow the same saved palette. Enabled module rows and accent lines
interpolate the selected stops. Panel surfaces stay graphite, and accent/fill
contrast is bounded so dark and pale custom colors remain readable.

Selection lists have darker recessed surfaces and small flat checkboxes with
accent-colored geometry ticks. Selecting or reselecting a value leaves the list
open, including when a font change remeasures the layout. Left-clicking an open
header keeps it open; right-clicking its header or an option closes it without
changing the selection. Old/Raven interactions and presentation retain their
existing paths.

Palettes are immutable and cached until their saved inputs change. This feature
adds no framebuffer, shader, animated texture or per-frame color-picker image.
The verified pre-change archive and guarded rollback are in
`.backups/themes-20261021/`.

The packaged build passed 511 tests (162 core, 349 Forge), with zero failures,
errors or skips. Native full UI runs passed at 1280x720 / GUI scale 2 on Forge
and 854x480 / Auto on OptiFine M5, including 120 theme/title checks and 18 HUD
theme checks per run. A final small-viewport title run passed 123 theme checks,
including scrolling to and clicking the last Custom option without closing the
list. These runs also retain the 442 title-font, 18 HUD-font and offscreen
3840x2160 rendering checks. Shutdown reports have zero queued tasks and retained
subscriptions, with `stdoutError=false`.

Independent native-image review at both sizes found no checkbox, picker,
text-fit or HUD alignment blockers. Warmed CPU draw submission medians were
1.014 ms settled / 0.961 ms moving on Forge and 0.763 ms / 0.756 ms on OptiFine;
these include the harness and do not measure GPU completion or game FPS.
The packaged artifact, native receipts and captures are in
`forge/build/verified/themes-20261021/`.

## Reference controls and typography, 2026-10-22

New checkboxes use a 10-unit dark square and a larger white tick, replacing the
8-unit box and palette-colored mark. Boolean rows, selection lists, config
options, HUD editing and account delivery share the same drawing and row
centering. Label measurements reserve the larger control column using the same
row layout and hit testing. Panel widths, scrolling and persistent selections
retain their existing behavior.

Disclosure arrows are joined open chevrons, about 7.2 by 4.3 units, rather than
small filled triangles. They retain the existing expansion rotation, snap their
centers to physical pixels and use geometry rather than font characters.

Roboto Regular is the new default based on the supplied reference's wider
letterforms. Category titles are slightly smaller and follow the same Bold
preference as body text. Existing saved font choices remain valid. Final-size
font rasterization and bounded caches retain their existing implementation.

The final packaged build passed 511 tests (162 core, 349 Forge; no failures,
errors or skips). Native full UI runs passed with Forge at 1280x720 / GUI scale
2 and OptiFine M5 at 854x480 / Auto. Each passed 466 title-font checks across all
13 families and both weights, 123 theme-selection checks, 18 HUD-font checks,
18 HUD-theme checks, 183 inline-config checks and the offscreen 3840x2160
rendering check. The config probe forces a compact viewport before checking
overflow; tall layouts may correctly fit every action without scrolling.
Independent review at both native sizes found no control, font-fit, centering
or picker-overlap blockers.

The controls add only a few immediate geometry vertices and retain existing
resource ownership. Warmed CPU draw submission medians were 1.018 ms settled /
0.990 ms moving on Forge and 0.750 ms / 0.739 ms on OptiFine. These harness
samples do not measure GPU completion or game FPS. The verified source backup
and guarded rollback are in `.backups/reference-controls-20261022/`; the final
JAR, captures and receipts are in
`forge/build/verified/reference-controls-20261022/`.

## Directional drag sway, 2026-10-22

New GUI category panels keep their immediate, pointer-owned positions while a
small directional lean responds to horizontal and vertical movement. The pose
leans toward travel, reverses with direction changes and gently returns to flat
when movement stops or the button is released. Its target is limited to 1.4
degrees, with a 1.6-degree hard limit. The critically damped response uses elapsed
time and has no perpetual idle shake. It adds no settings.

The effect rotates around the header grab point. Saved positions still use the
shared native drag/recovery controller. Re-grabbing preserves the real pointer
anchor, and new interactions, resize, reset, font changes and close cancel the
pose. Hit routing respects the visible tilted panel and the controls above it.

The existing rounded alpha mask inverse-maps the panel pose in framebuffer
coordinates, so row clipping also follows the lean, including nested Minecraft
font transforms. The animation adds no framebuffer, image atlas or scaled text
cache; it uses the existing font rasters, a small transform and two extra mask
uniforms. Old/Adjust/Raven retain their existing behavior.

The final build passed 515 tests (162 core, 353 Forge). Full packaged UI runs
passed on Forge at 1280x720 / GUI scale 2 and OptiFine M5 at 854x480 / Auto.
Each passed 52 directional-drag checks covering immediate pointer tracking,
direction reversal, exact saved positions, rest/release, re-grabbing, vacant
tilted-edge clicks and overlapping config controls. Existing rounded-corner,
offscreen 4K font, GUI/HUD font and inline-config checks also passed. Native
captures show intact rounded bounds and a fully flat resting pose; tilted
raster text has the expected temporary pixel resampling during the transform.

Warmed CPU draw-submission medians were 1.087 ms settled / 1.090 ms during
expansion motion on Forge and 0.753 ms / 0.727 ms on OptiFine. These include the harness and do not
measure GPU completion or game FPS. Verified source backup and guarded rollback
are in `.backups/panel-drag-20261022/`; the final artifact, captures and native
receipts are in `forge/build/verified/panel-drag-20261022/`.

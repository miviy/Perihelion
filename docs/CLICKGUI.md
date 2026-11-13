# Panel ClickGUI

Open with **Right Shift** while in a world, or `.gui`. Rebind Misc's ClickGUI entry using the same binding editor as other modules. The opener
is an ordinary one-shot, world-independent client module; appearance settings work
while it is disabled. The default uses compact translucent charcoal panels, centered blue headers and regular gray text. Configs opens a separate preset screen with its list and actions, like the HUD editor. Compact HUD and Configs tabs can be moved independently; Done/Escape returns from Configs to the same module menu. Its controls use the same palette, font and opacity as the panels. Search is off by default.

## Controls

| Area | Interaction |
| --- | --- |
| HUD tab | Left-click opens the editor; left-drag moves; right-click opens Edit/Toggle/Reset |
| Configs tab | Left-click or right-click opens its own screen; left-drag moves |
| Config window header | Left-drag moves; right-click collapses/expands |
| Category header | Left-drag moves; right-click collapses/expands |
| Panel body | Mouse wheel scrolls only the hovered, frontmost panel |
| Module row | Left toggles; right expands settings and binding; middle edits binding |
| Setting | Boolean toggles; number slides; mode/multi-select expands inline |
| Number/range | Right-click edits in the setting row; Enter applies, Escape cancels |
| Range | Expand to independent minimum/maximum sliders; endpoints cannot cross |
| Color | Expand for saturation/value, hue, RGB, alpha, rainbow and speed; hex/direct channel entry supported |
| Binding | Press keyboard/mouse; Delete or Backspace clears; Escape cancels |
| Search | Type a name, description or category fragment; Ctrl-F focuses |
| Reset | Ctrl-R or `.gui reset` restores panel placement and expansion |
| Screen | Escape closes; does not pause the world |

An enabled module has brighter, configurable text and an accent marker. Boolean settings have distinct ON/OFF badges; enum and numeric values have their own aligned value area. A red marker/text and
short failure tooltip distinguish quarantine from ordinary disabled state. Full
exceptions remain in normal logs. Clicking a faulted module uses the manager's existing
explicit retry behavior; the GUI does not clear faults itself. Missing fault details
have a safe fallback. Broken visibility callbacks quarantine their defining module
and omit only the broken setting for that screen instance.

## Structure and replaceability

`ClickGuiService` is the Forge composition seam, owns cached fonts and installs the
local command. Its `Supplier<? extends GuiScreen>` factory defaults to `PanelScreen`;
`frontend(...)` can select an unrelated implementation later. No gameplay module imports
GUI code. The current service and preference module are UI-layer code, not a core GUI
framework. Another design may reuse the widgets/fonts or supply its own.

`PanelScreen` owns only the panel view, filtering and temporary interaction state.
`PanelLayout` is a separate, OpenGL-free layout model. `UtilityLayout` owns the compact tabs and config/editor window positions through the existing hidden ClickGUI setting. Categories come directly from
the populated module categories: Combat, Movement, Player, World, Render and Misc. World
contains building/mining/block tools; Player contains inventory, healing and item
tools; Movement contains travel and fall/edge protection; Combat contains targeting
and fight assistance. Misc contains network, minigame and client utilities, including ClickGUI and Input Fixes. The empty legacy Client category is hidden. Module rows are alphabetized within each group.
The Exploits category has been removed; old saved layout entries for it are
ignored while the remaining category positions and collapse states are retained.
The screen caches registry entries and widget definitions when constructed. It reads
current state from `ModuleEntry` and values/visibility from `Setting`; there is no
parallel module manager or setting value model.

`SettingControl.create` selects reusable boolean, numeric, enum/multi-select, range,
color and text controls from the existing setting metadata. All writes go through
`ModuleManager.setSetting`; numeric snapping uses the declared minimum/maximum/step
including a maximum that falls between valid steps. Numeric, range, text, hex and
color-channel direct entry appears in its own setting row and uses the existing
parser and validation. `KeybindEditor` provides reusable exclusive binding capture;
`Tooltip` shows short descriptions after 300 ms and can be disabled in Misc → ClickGUI. Panel widths are measured against full module/setting labels and their value controls, so labels do not become ellipses. Long text values wrap into complete rows; font size and weight remain the selected face. Inline expanded selectors/color
controls are part of panel content and its scroll height, avoiding floating popups
that send clicks to controls beneath them.

Expanded modules use one flat tinted section and a single top separator. Its tint
follows the Panels opacity setting, so translucent panels keep their see-through
appearance. Slider tracks and the separator are snapped to one framebuffer pixel
at fractional menu scales, with slider fill in the chosen accent color;
they do not each draw a separate shaded tile. This treatment belongs only to the
panel frontend, so other ClickGUI designs can render the same setting metadata
differently.

Generic core additions include definition-time default bindings and optional immutable rainbow data on `ColorValue`. The legacy Client category remains compatible with old layout data; current client controls use Misc. None contains panel
geometry, widgets, fonts or frontend dependencies. Input dispatch now stops immediately
when an action revokes permission, such as opening a screen.

## Rendering, input and search

Panels draw back-to-front and hit-test in reverse order. Clicking or dragging brings
the target to the front; that same list determines both rendering and input. Headers
stay outside each body's framebuffer scissor rectangle. Bodies are limited by screen
height and the space below their header; every expanded setting contributes to scrolling.
Wheel steps are bounded and deterministic, without animation or inertia. Scrollbar
thumbs are one framebuffer pixel wide at every GUI/menu scale; HUD player menus use
the same thin rendering.

One active slider/header drag, inline text editor or binding capture owns input. Captured
sliders clamp values while the pointer leaves the control. Additional clicks during a
drag are ignored. While editing, Enter applies, Escape cancels, and a click outside
the row cancels without toggling anything underneath. The existing value stays
readable; the first typed character replaces it, while clicking into the field
allows editing at the cursor. The inline field has no fill, border or underline;
the original row label stays visible beside the blinking caret. It draws text
with the selected GUI font at the same size as the neighboring settings,
including at fractional menu scales. Keybind capture uses its own prompt and
takes input before panels.
Opening clears vanilla held keys and blocks Perihelion bindings; search text and binding
capture therefore cannot toggle gameplay modules. The normal input bridge re-arms only
after the screen closes, accounting for keys still held.

A single existing `RenderState` scope protects each complete GUI draw, including matrices,
scissor, texture state and the Minecraft GL cache. No per-control GL snapshots, blur,
particles, shader effects or render-thread file writes are added. Font atlases are built
once per selected font per runtime, cached and deleted on shutdown. The Font setting applies to Modules, Configs, the HUD editor and the live/chat HUD.
**Current** retains the existing Sans face; **Minecraft** uses the native game/resource-pack font;
**Monospace** uses the JRE monospace face. Serif remains available for older saved preferences.
No font downloads or redistributed font files are required.
Panel labels use smaller, four-times-resolution cached glyphs; the Minecraft font
choice uses Minecraft's native renderer. Enum choices and key names use normal case
without changing their stored values. Latin glyphs use 4x antialiased atlases;
other Unicode and Minecraft formatting fall back
to the native renderer. Logical GUI scaling and framebuffer scissor scaling are separate.

Search matches cached lowercase module names (including their readable forms),
descriptions and category names. Match
flags update only when text changes. Results filter existing panels and temporarily
expand matching collapsed categories without changing their saved preferences.
Disabling Search bar in Misc → ClickGUI clears the filter, removes its row and gives
that space back to panels. An empty query returns to the saved collapse state.

## Preferences and persistence

Misc → ClickGUI contains world background, panel/header opacity, accent/text/enabled
colors, GUI scale, font, search and tooltip toggles. Every color uses the same picker, with
independent alpha/rainbow/speed controls. No animation framework is included.

Everything uses `config/perihelion/client.json` and its existing debounce, async writes,
backup, reload and safe setting application. Appearance values are ordinary settings.
The hidden `clickgui.panel-layout-v3` string is this frontend's own layout JSON containing
category IDs, positions, collapsed states and z-order. Other frontends can use
their own setting without changing module implementations or the config architecture.
Module/widget expansion, scroll offsets, text queries, hover, cursor and active captures
are temporary and never persisted.

Dragging saves on release; collapse/reset save through the same preference setting.
Closing captures the latest layout. The initial layout centers closely spaced columns, Render first, and stacks short categories below other short lists. Long categories retain more visible rows. Each panel scrolls within its slot, so upper lists do not cover lower category headers. Untouched defaults reflow on resize; dragging a panel gives it the existing floating viewport. Older manually saved layouts remain usable. Reset changes only panel placement/collapse/order. The v3 layout key keeps this frontend's positions separate from older layouts; other preferences and
gameplay settings are retained. Loading,
resize callbacks, fullscreen/GUI scale changes and reopening recover off-screen headers.
Recovery does not clamp every motion event during an active drag; release restores a
reachable header. Tiny screens may still require overlapping panels and scrolling.

## Verification and remaining polish

See [verification](VERIFICATION.md) for the tested artifact and actual Forge/OptiFine M5
launches. The bounded probe checks behavior and GL state, not exact pixels. Unit tests
cover pure layout recovery/order/serialization, scroll bounds, numeric snapping, default
binding persistence, focus revocation and color serialization.

Logical font families vary between operating systems. Search, config names and inline entry use the selected face for text, selections and caret placement. Unsupported Unicode glyphs use native fallback without changing the rest of a custom-font label; codepoint boundaries are preserved when scrolling or trimming. Small windows can require wrapped columns and scrolling; full fixed labels set the required panel width. Long text values wrap, while inline input scrolls horizontally. No per-widget keyboard navigation or
animated transitions are implemented. Shader packs, unusual resource-pack fonts, other
GPUs/operating systems, physical input sessions and long-duration play need broader
manual testing. These are limits of this pass, not guarantees from the smoke results.

## Unified auxiliary screens

Configs has its own `ConfigScreen`, with the shared category-header gradient, translucent charcoal fill, selected/hover rows and flat controls. The module panels are absent from this screen. Its preset list and actions open together; the dialog sizes to full preset names and text. Done/Escape returns to the same parent menu. The dialog header retains left-drag and right-click collapse. Existing autosave, async
operations and update/delete confirmations are preserved. Layout keeps actions reachable
at small/fractional scales. The HUD editor uses the same controls and Elements list,
with a compact toolbar instead of vanilla button textures and a readable backed hint. Vanilla chat rendering is suppressed while Modules/Configs or HUD editor is open; chat history and GuiChat remain available. Done/Escape returns to its
parent view. Fonts, theme colors and opacity follow Misc → ClickGUI preferences.

Preset naming owns keyboard input while focused. Escape leaves the name field; after
clearing any active filter or inline editor, Escape closes the GUI normally.

See [theme and font verification](UI_THEME_2026-10-12.md) for native captures, checks and recovery.

Search reserves its input area only while enabled. With Search disabled, panels can be dragged to the two-pixel top margin. Utility tabs recover on resize, retain custom placement and keep their default spacing at fractional menu scales. Ctrl-R also resets utility geometry. See [the GUI/HUD revision](GUI_HUD_2026-10-12.md) for the latest screenshots and verification.

## Text customization

Open **Misc → ClickGUI** and expand its settings. **Custom text style** is off by default: the GUI uses the regular gray text, restrained selection and blue headers from the Adjust references. Enable it to reveal and apply optional colors and weights. Stored custom choices remain available when switching it off.

**Section text** changes category and window headings; **Selected text** changes active tabs, selected presets, expanded modules and selected setting choices. **Selection highlight** changes selected/hover backgrounds and text selections; its alpha controls highlight strength. **Helper text** changes bindings, descriptions and muted labels. **Text**, **Enabled modules** and **Accent** are also part of custom styling. **Font**, opacity and menu scale remain available independently.

**Text weight** selects Regular or Bold GUI text. **Bold sections** gives headings their own bold face while the body remains regular. These controls update immediately and save through ordinary config/presets. Bold uses actual JRE bold glyphs or Minecraft's native bold renderer with matching measurements and Unicode fallback; row size stays the same. Utility captions expand only as needed to fit the selected font/weight. GUI weights and text colors apply to module/config screens and HUD editor controls; in-game HUD colors keep their own settings.

See [readable labels and separate Configs verification](READABLE_GUI_CONFIGS_2026-10-13.md) for the latest behavior, captures and artifact. The earlier [Adjust comparison](ADJUST_FIDELITY_2026-10-12.md) records the visual baseline. Earlier [text customization captures](GUI_TEXT_2026-10-12.md) demonstrate optional styles.

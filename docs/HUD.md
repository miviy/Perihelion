# HUD

Open **Right Shift → HUD** to focus the HUD module in the ClickGUI. You can also
right-click **Visual/Render → HUD**. In New visuals, the HUD shortcut temporarily
raises that category for more editing room; **Modules** or closing the GUI restores
its saved position. Left-click the HUD module to turn the whole
overlay on or off, or use **`.hud on`** / **`.hud off`**. HUD starts disabled on new
configurations.

**Render → Target HUD** is a separate module and works while the main HUD is off.
It shows only entities you attack. **Hide delay (s)** keeps the card visible after
the last attack (3 seconds by default; 0.1–10 seconds). Dead or unloaded targets
clear immediately. **Design** offers Perihelion and 32 designs from Yuri, B4,
Arsenic, Coolware and Myau. Donor designs offer **Original**, **Client** and
**Custom** themes; Custom exposes accent colors and font controls. Background
and Position reuse your saved appearance and placement. See the
[design inventory and intentional differences](TARGET_HUD_DESIGNS.md).

Each HUD piece uses the same row style as a regular module: **left-click** toggles
it and **right-click** opens its dropdown. The first setting is **Enabled**, followed
by its content settings. **Text** contains fonts, size and text colors;
**Background** contains panel colors, opacity, padding, corners, blur and shadows;
**Position** contains horizontal/vertical placement and **Reset position**. Positions
range from 0 (left/top) to 1 (right/bottom), accounting for the element's size.
You can configure disabled pieces without displaying them. Collapsing a dropdown
never changes an enabled state or a saved value.

**General** contains **Allow moving in chat** (on by default), Hide with F3 and
Hide in menus. Turning chat movement off stops HUD dragging immediately; player
actions and normal chat input remain available. Old visuals also expose their
shared colors here; New visuals use the client theme and each element's appearance.
Array-list controls live only under Array list. Nearby players groups its extra
controls into Statistics, Provider keys, Filters, and Highlights and alerts.
Conditional controls appear when relevant: gradient colors, blur strength, shadow
details, mode separators and optional provider key files. **Reset appearance** in
an element's Background section resets its text/background without changing its
content, visibility or position. Existing configs and presets retain the same IDs.

**Arrange on screen** at the bottom opens a drag-only preview with no editor toolbar,
buttons, Elements menu or right-click settings. Drag a HUD piece to place it; Escape
saves the last drag and returns to the module GUI. The legacy editor remains
available through **`.hud edit`** in a world. Every appearance and position setting is available in
the module GUI; the editor is no longer required for customization. Both Old
frontends and New visuals use these same grouped controls.

Opening **T/chat** keeps the enabled HUD visible. Left-drag a visible HUD element
while chat is open; its position saves on release, or when closing chat mid-drag.
Right-click a **player row** for grouped Tags (Whitelist, Target and Pin), Analysis (Spectate, Violations and Player details), and Hypixel (Statistics and Report). The popup includes a player head, name, available ping and cached first-login date. Language remains unavailable. Configure **Columns** under **Render → HUD → Nearby players** in the GUI.
The player table uses compact rows and a centered Players heading. The context card uses a larger portrait, a larger player name, regular section labels and a nearly opaque charcoal background so chat text does not compete with its actions. The separate Logo uses larger text than the Tab GUI and keeps matching drag bounds. GUI text colors and boldness remain optional under Misc → ClickGUI → Custom text style.
Escape closes a HUD popup first. Clicks outside the HUD, chat typing and chat scroll
retain their normal behavior. Right-clicking in chat does not hide an element.
Resizing cancels an unfinished drag; disabling HUD or leaving the world retires
interaction state.

Drag an element to move it. Positions save on release, or when closing mid-drag.
The editor toolbar contains **HUD ON/OFF** and **Elements**.
The directions strip and Reset/Done buttons are removed. Left-click an Elements entry
to show or hide it; right-click an entry (or a visible preview) to customize it.
Escape closes an inline edit, then the customization panel, then Elements, before
returning to the previous GUI. The panel header also returns to Elements.
The toolbar and options use the same theme and selected font as Modules/Configs.
Left-drag the HUD editor header to move its controls; right-click it to collapse/expand them. Resizing cancels an unfinished drag
and recovers the saved positions for the new resolution.

| Element | Content | Default when HUD is enabled |
| --- | --- | --- |
| Logo | Compact Perihelion name; optional FPS/ping | On |
| Tab GUI | Independent six-category keyboard navigator | On |
| Array list | Enabled modules with mode suffixes; width sorting and an optional continuous background | On |
| Coordinates and speed | Floored XYZ, horizontal blocks/second, direction | On |
| Target card | Aura target, otherwise crosshair target; player head, health, absorption and distance | On; hidden without a live target |
| Armor | Helmet through boots, native item icons and remaining durability percentages | On; hidden without equipped armor |
| Potion effects | Localized names, amplifier and remaining duration | On; hidden without effects |
| Keystrokes and CPS | Movement actions, physical mouse button states, physical left/right clicks in the last second | Off |
| Nearby players | Heads, names, health percentage, colored teams; cached Hypixel statistics; missing fields show `--` | Off |
| Toggle notifications | Up to four recent module changes, lasting three seconds | Off |

The ClickGUI shrinks an enlarged menu when needed to keep Search and its three
toolbar buttons separate.

The editor previews empty enabled elements; the target preview uses your player.
Optional panels can overlap on small screens when enabled together; drag them into
your preferred arrangement. Individual panels stay within the viewport and clip
long content. The vanilla hotbar, scoreboard and chat are preserved.

## Appearance and settings

Use the module dropdowns above for normal customization. The optional drag editor
also keeps its existing right-click controls: right-click an element or its
**Elements** entry. That popup has
**Text**, **Panel**, and (where relevant) **Content** tabs (**Layout** for the
Array list). The essentials are shown first; **Advanced** reveals secondary
controls. **Reset look** in Advanced restores that element's appearance defaults
without moving it or changing provider settings. These changes work
immediately in both the editor preview and the live HUD, without a Basic/Custom
mode switch. Existing positions, enabled states and preset loading are preserved.

- **Text:** independent font (Client follows the GUI, or select any bundled
  family), bold for independent fonts, 65–175% size, text shadow, Client/Solid/
  Gradient/Rainbow colors, and two native color pickers for gradients.
- **Panel:** Auto/None/Solid/Gradient backgrounds, independent background colors
  and opacity, horizontal/vertical gradients, padding and corner radius. Panel
  shadows have independent opacity and spread controls under Advanced; they are
  off by default. **Extra padding** adds space beyond the normal content padding.
- **Blur:** each element has its own toggle and strength (1–16), off by default.
  Blur is independent of opacity, so a translucent or text-only element can
  still blur its background. One shared scene texture is captured per HUD
  pass, then a separable Gaussian filter removes detail without sparse sample
  bands. The tint, blur and shadow follow the same rounded joined outline,
  with fractional edge coverage. Only enabled regions are shaded. Unsupported shader/framebuffer
  contexts use the configured background without blur.
- **Array list content:** left/right alignment, width/alphabetical sorting,
  0–12 pixels of row spacing, 1–12 pixels of horizontal padding, connected
  text-fitted backgrounds, optional None/Left/Right/Top/Outline accent
  borders, lowercase, module-mode suffixes with Space/Brackets/Parentheses/
  Dash separators, and visual-module filtering. New visuals now honor row
  spacing. The default has zero extra row spacing, two units of text padding,
  subtle corner rounding and a 30% near-black background that fits
  each line of text. It always forms one connected shape, with no gaps or
  separate pills. Outline traces only the outside edge of the whole list.
  Background, blur and shadow follow that same text-fitted shape, including
  convex and concave corners. Font, row
  spacing and padding are the primary controls; sorting, alignment, suffix style
  and accent borders are under Advanced.
- **Other content:** logo design, actual packaged version, optional local clock and FPS/ping details, and the existing player-table
  columns, filters and provider controls remain in their element's Content
  tab. Every element receives the shared text and panel controls.

Scaling includes icons, background, blur and drag bounds. Player-row clicks
and scrolling use the same transformed geometry. Text is rasterized at its
final pixel scale. New visuals use compact visible-glyph leading and a HUD
density independent of the menu scale, so a large settings menu does not enlarge
the overlay. The GUI's font and other HUD elements keep their own choices.
The editor only outlines the hovered or dragged element rather than boxing every preview.

Customization panels reuse native setting widgets, scroll when needed, and save
through the existing config/preset system. Right-click a slider or color value for
transparent inline input; Enter submits it and Escape cancels the edit. A popup
consumes clicks before the HUD underneath. Resizing closes transient popup input.


- **Accent / Secondary accent:** native color pickers, including their rainbow option.
  Default colors are muted purple and salmon, matching the Adjust reference palette.
- **Category navigator:** on. Up/Down selects, Right opens modules, Enter toggles
  through the normal module lifecycle, Left closes. It only responds in gameplay
  while Tab GUI is visible. Logo visibility and position are independent. It uses
  the same Combat, Movement, Player, World, Render and Misc groups as the module
  menu, with alphabetized rows. Client preferences and the HUD's own toggle stay
  in the GUI. The Exploits tab is removed; no modules are re-registered.
  Module lists and setting panes share the same background, row grid, text insets,
  and selection highlighting. Module lists show only module names, with no keyboard
  instruction footer. The selected module uses a highlight without
  a `>` marker. Changes use the same live settings as ClickGUI in both directions;
  opening or closing a pane does not move the saved category anchor.
- **Watermark details:** off. Enable to append FPS and ping/Local.
- **Array list borders:** optional in its Background dropdown (Layout in the editor); off by default.
- **Panel opacity and text shadow:** independent per element in Text/Panel.
  The legacy global opacity still supplies the Auto background in Old visuals.
- **Hide with F3 / Hide in menus:** on by default. Chat remains visible and
  interactive; Hide in menus applies to other menus. F1 is still respected.
- **Show modes:** on. Uses a module's primary `mode` enum when present.
- **Hide visual modules:** off. The HUD and Client actions themselves never appear.
- **Visible rows:** 1–32, default 8. **Range (blocks):** 8–128 blocks, default 64.
- **Columns:** health, team and the original statistics columns by default;
  Ping, Observed time and Last seen are optional. Names always stay visible.
  Column choices use the existing HUD config and presets. Table headings, values and names use the selected font's measurements.

Each Position dropdown edits the same two normalized position settings used by dragging. All settings use
the existing working config, change-based autosave and named preset system; there
is no separate HUD file or disk writer. `.hud reset` resets positions; `.hud off`
disables the renderer and releases its listeners, snapshots and font textures.

## Player actions and Hypixel setup

- **Spectate (Freecam):** starts the existing detached camera three blocks behind
  the clicked loaded player. Close chat to fly with WASD, Space and Shift. Body
  input and interaction remain suppressed. Right-click that player again and choose
  **Stop spectating**, or disable **Render → Freecam**, to return. Target loss or a
  world change retires the camera. This is local camera movement; it does not put
  the account into Hypixel's server spectator mode. Only already loaded players
  are available, and the camera stays within Freecam's configured distance from
  its target.
- **Report to Watchdog / staff:** on Hypixel, opens the official `/report username`
  selection GUI. Choose a reason and submit using that server GUI. The shortcut
  uses the live player's plain username and never picks a reason or submits a
  report on its own. It is unavailable on other servers.
- **Pin / Unpin:** pinned UUIDs sort before other players, even when the row limit
  is small. Distance and loaded-player eligibility still apply. Up to 64 pins belong
  to the active HUD session and clear on deactivation or world changes.
- **View Hypixel stats:** an explicit lookup fills the card and caches values for
  the board. Select **Hypixel stats game** in Render → HUD (Bed Wars or SkyWars).
  Values are overall totals. Streak and final-kill fields show `--` when unpublished.
- **Player Details:** native health, team, distance, ping, armor, held item, locally
  observed time and last seen. Escape closes any player popup before chat.

Obtain an API key from the [Hypixel Developer Dashboard](https://developer.hypixel.net/).
Save only the key in a local UTF-8 text file. **Render → HUD → Hypixel key file**
accepts its absolute path, or a path relative to the running Minecraft folder.
The default is `config/perihelion/hypixel-api-key.txt`. Only the path is saved in
config/presets; the key is read by the lookup worker and sent in the `API-Key`
header to the fixed HTTPS [official v2 player endpoint](https://api.hypixel.net/).
The file is never displayed or logged by the client. Changing the key path clears
cached results and ignores replies from the previous path.

One request can run at a time, without a backlog of player lookups. Successful
results cache for five minutes, failed results for ten seconds, with at least a
one-second interval between new requests. Rate-limit headers and HTTP 429 impose
backoff; HTTP 403 displays an invalid-key error. The worker uses five-second
connection/read timeouts, a 2 MiB response bound and no redirects. Disabling HUD
or changing worlds closes the worker and active connection. The API cache is
capped at 64 UUIDs; it never fetches automatically while rendering.

## Data and performance

HUD data is sampled every four client ticks (5 Hz at the normal timer rate).
Rendering does not rescan players, rebuild inventory snapshots or sort modules.
Movement-key highlighting reads the current key bindings; CPS counts physical
press edges, not automated attacks. Speed includes the active client timer speed.

The player lookup scans only loaded player entities, retains at most 12 rows with pinned players first and then nearest players, and never sorts the world's entity list. Effects are capped at 12 rows,
notifications at four, and each click history at 128 entries. Hidden elements skip
their sampling work outside the editor. Disabled HUD has no active listeners.
HUD text follows the client font by default; each element can instead select any of the 13 bundled/native families. Custom glyph atlases are built lazily and owned by the active HUD/editor; Minecraft uses the game font. Each element's formatted-text cache is capped at 256 entries; glyph atlases are shared;
color codes retain the same font and width. Unsupported Unicode falls back to the
native font. Closing the editor or deactivating HUD deletes its atlas. Text and
items share one reusable GL state scope per HUD pass. Optional blur captures one owned scene texture per HUD pass and shades only
enabled regions. Shadows use geometry without extra textures. An activation-owned worker runs explicit Hypixel lookups outside the game thread.

Player health percentages and team colors use loaded game data. Missing watermark
ping is `--`. K/D, WS, FK/D and W/L use cached API values for the selected stats game;
missing fields stay `--`. Bed Wars event schedules are not fabricated.

Observed time counts this active HUD's local observations, rather than claiming a
player's full server online time. A detail card retains the last sampled values if
its player leaves the board, and uses UUID identity to avoid switching to another
player. Observation history is capped at 256 UUIDs and clears on deactivation.

The reference layout uses a 65-pixel category menu, tight 11-pixel rows, a
a player table whose columns expand to fit the chosen font, flat neutral black translucent panels, and a two-pixel
screen margin (logical GUI pixels). Default players sit beneath the watermark;
default effects sit above the bottom-left footer. Other saved positions are kept.
Armor labels fit a full `100%`. Existing user colors are preserved. The screenshots
do not identify their exact typeface, and show multiple color/outline variants;
Current retains the existing smooth Sans face; the optional fonts use their own measured widths. No exact reference typeface is claimed.

## Verification and recovery

See [appearance verification](verification/hud-adjust-20260930.json) and the earlier
[HUD verification](verification/hud-20260929.json) for the exact JAR and test
results. Unit tests cover drag coordinates, bounds, click expiry, bounded history,
config persistence and reset behavior. The packaged fixture checks native item
rendering, target removal, bounded player/notification lists, editor controls,
GL restoration, repeated drawing, and activation cleanup.
The [chat interaction audit](HUD_CHAT_2026-10-12.md) and
[current verification](HUD_CHAT_VERIFICATION_2026-10-12.json) cover the chat fix,
native chat lifecycle, popups, draft preservation and closing-screen input guards.

The pre-appearance source snapshot is `.backups/before-hud-adjust-20260929-212153/`.
Run `python3 scripts/restore-hud-adjust.py` to verify this rollback; add `--apply` to
restore the previous HUD appearance. The earlier pre-HUD snapshot and
`scripts/restore-hud.py` are also retained; its later-edit guard correctly refuses
while these newer appearance changes are present.
The helper refuses to overwrite later edits, backs up current affected files before
restoring, and leaves generated worlds/caches/JARs intact. Rebuild after restoration.

## GUI/HUD revision (2026-10-12)

Logo retains the old `watermark` config keys; Tab GUI has separate visibility and normalized position settings. The default Tab GUI sits below Logo, and the default player table sits below both. Existing custom positions are preserved. Distance is now a selectable column; new defaults also include observed time and Seen. **Online time*** counts local observations, not a player's full server session. Alternating table rows, stronger headings, target markers and aligned values make the fields easier to distinguish.

Whitelist edits the existing persistent Friends names through normal module settings. Target is a bounded, activation-owned board tag and highlight; it does not select an automated combat target. Pins keep their existing priority behavior. Violations opens an explicit unavailable-data view because no violation provider exists. Statistics requests remain explicit and use the existing owned API worker; first-login is read from its cached official player response. No language, violations or full online-session data is invented.

See [GUI/HUD revision and verification](GUI_HUD_2026-10-12.md).

The [HUD customization verification](HUD_OPTIONS_VERIFICATION_2026-10-13.json) records
the tested artifact, settings regression results and packaged local UI smoke run.

## Modern-Lazify provider statistics (2026-10-13)

The Adjust-style table now supports automatic Hypixel BedWars player stats from
Abyss, Prism and Bordic, optional official Hypixel fallback, Urchin/Seraph tags,
per-mode streaks and Bordic daily/weekly/monthly data. Configure providers and
local key-file paths in the player element's Options. Sorting, filters, hidden
players, encounters and optional party/nick/tag tracking use native HUD settings.
Scroll the header/body in chat for columns/rows. `.hud refresh` reloads statistics;
`.hud unhide` restores hidden rows. [Usage, provider setup and verification](MODERN_LAZIFY_2026-10-13.md)
describes requirements and unavailable-data behavior; it supersedes the older
explicit-only statistics descriptions above.


The [2026-10-26 verification](verification/hud-clean-20261026.json) records the
current HUD style/blur artifact, 607 tests and packaged Forge/OptiFine UI runs.
Screenshot checkpoints are retained beside the verified JAR in
`forge/build/verified/hud-clean-20261026/screenshots/`.
For a guarded rollback, run `python3 scripts/restore-hud-clean.py` first for a
dry run; `--apply` restores the pre-change sources only if no later edit would
be overwritten. Rebuild after restoring.

## Compact HUD refinement (2026-11-03)

New visuals default to a compact continuous Array list, with no added row gaps,
two units of lateral padding, subtle rounding and a near-black 30% background.
Glyph-based leading keeps text readable while reducing the row height. The Logo
uses a bold client name and muted metadata.

Existing saved appearance values remain available. To apply the new defaults,
right-click an element, open **Advanced**, and choose **Reset look**. This keeps
the element's saved position and visibility.

The [verification report](verification/hud-refine-20261103.json) records the
608 unit checks, packaged Forge and OptiFine UI passes, native blur pixel checks,
and screenshot review.

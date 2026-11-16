# Target HUD designs

Select **Render → Target HUD → Design**. The original Perihelion card remains
available alongside 32 donor designs. All use Perihelion's single attacked target,
hide delay, saved position/scale, editor, configuration and lifecycle. Looking at
an entity alone does not acquire it. Text-only source designs stay text-only;
portrait designs support players and living mobs.

**Design** uses the source style's short name (for example Adjust or Pulsive),
while saved configuration tokens stay unchanged. **Scale** directly adjusts the
card from 0.65 to 1.75; **Hide delay** controls how long it stays after an attack.
**Theme** offers Original brand colors, Client colors/font, or Custom accents.
Custom keeps the design's typography. **Arrange on screen** moves the card.
The editor uses this same short setting list, without Text, Background or Position
groups. Existing placement and scale values are preserved.

Original uses Minecraft where the source does, and bundled licensed Inter where
donors used SF, Product Sans, DM Sans or Tahoma. Donor fonts/assets are not
redistributed, so these are native adaptations rather than pixel-identical copies.

## Source inventory

All references were inspected read-only. Paths below are relative to the named
donor checkout in `../donor_clients/`.

| Donor and revision | Source | Options |
| --- | --- | --- |
| Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def` | `src/main/java/ddlc/yuri/modules/impl/render/targethud/impl/` and `TargetHudModule.java` | Yuri, Pulsive, Adjust, Atmosphere, Astolfo, Old Astolfo, Novo, Old Novo, Exhibition, Old Exhibition, Raven B4, Old Raven B4 (12) |
| OpenB4/OpenB4 `3d63944cc79be890c013cc79e5ed92d3533b48a5` | `src/main/java/xyz/daikirai/module/render/TargetHudModule.java` | Modern, Legacy (2) |
| Arsenic `d6f3a3d40a6d3f14826a42002abf343c61c46ecb` | `src/main/java/arsenic/module/impl/visual/TargetHUD.java` | Face, Simple (2) |
| Project-Coolware `29d1e0974a49f81e829ab12ecbeada0c145f0d1a` | `src/main/java/com/github/cooldood/modules/impl/render/TargetHUD.java` | Flat (Mode1), Island (Mode2) (2) |
| OpenMyau-Plus `52c8492c68ed14a0dd6137d0c21f912c463af8d9` | `src/main/java/myau/module/modules/TargetHUD.java` | Default, Raven Modern, Raven Legacy, Face, 3D, Simple, Circle, BlueDev, Astolfo, Exhibition, Moon, Rise, Neverlose, Tenacity (14) |

Myau's additional case-sensitive `Astolfo` alias resolves to an unimplemented draw
branch and paints nothing; it is intentionally excluded. Similar-looking designs
remain separately selectable because their source geometry, colors or details differ.

## Native implementation and corrections

`TargetHudDesigns` contains presentation only. It shares the existing font atlas,
render-state guard, clipping, portraits, item renderer, shader blur and shadows.
There is one bounded health/damage animation state, reset on target/design changes,
expiry and disposal. No donor event bus, managers, packet code, config system,
custom font loader, permanent target list or external dependency is imported.

Pulsive uses a single clipped silhouette with a square internal header seam, and
rounded outlines retain antialiased coverage at fractional scales. Custom accent pickers reuse the client theme picker in New visuals.

Text faces are sized at the final framebuffer scale, including HUD density and
per-design text transforms. The same metrics measure and paint each label;
native Minecraft texels retain whole-pixel sizing and text origins snap to the
framebuffer grid. B4 cards reserve their health/status suffix when trimming names.

Source geometry that under-reported its painted bounds is normalized (Pulsive,
Novo, Old Novo and Exhibition). Neverlose reserves space for its right health
ring. Long names are measured and clipped; widths are bounded and pass through
normal HUD placement/scale recovery. Placeholder/player-only source portraits
use the existing native mob model renderer. Entity pose, RenderManager settings,
depth/scissor and GL state are restored after portraits and equipment.

The source health interpolation, damage trails and flashes are expressed through
one frame-time-based animation state. Source entry/exit animation systems, follow
modes, grids and target ESP are not imported; visibility retains the native module's
attack-only behavior and configurable hide delay. Selectable transition animations
remain future work.

## Verification

`launch-smoke --scenario targethud` exercises the packaged/remapped renderer in
an isolated Minecraft world: all 33 designs with a player and horse in Old/New
visuals, the Raven frontend path, long names at small/large scales, all theme and
background choices, health loss/recovery, hide expiry, GL state and resource cleanup.
It writes 132 framebuffer captures and `target-hud-designs.json`. These images are
visually inspected against source drawing geometry; donor clients are not run.
The standard `ui` scenario checks the shared settings/editing/dragging paths.

Yuri adaptations retain AGPL-3.0 attribution; Myau's GPL-3.0 and Arsenic's MIT
notices ship in `META-INF/licenses/`. B4/Coolware have no root license in these
snapshots; their source is used to study layout, with drawing expressed through
Perihelion's existing primitives. See [third-party notices](../THIRD_PARTY.md).

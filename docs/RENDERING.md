# Rendering helpers for Minecraft 1.8.9

The helpers live in `forge`, under `perihelion.integration.minecraft189.render`.
`HudRenderEvent` lives in the core and is forwarded by the existing Forge adapter.
There are no production modules, GUI, font atlas or separate rendering/event systems.
The core remains free of Minecraft and LWJGL dependencies.

## HUD drawing event

Subscribe to `HudRenderEvent` through the activation's `ModuleContext.listen` for
in-game 2D drawing. The Forge adapter forwards only `RenderGameOverlayEvent.Post`
with `ElementType.ALL`, after Forge's `GuiIngameForge.renderGameOverlay` has called
`setupOverlayRendering()` and completed the vanilla overlay. Other overlay element
events are ignored. No Mixin or second event bus is needed.

The event is immutable, non-cancellable and delivered on the client thread. It holds
only primitives: `partialTicks()`, `scaledWidth()`, `scaledHeight()` and `scaleFactor()`.
Dimensions and scale come from that Forge event's `ScaledResolution`, not a cached
window size or an extra resolution allocation. Width and height use Minecraft's
rounded-up GUI dimensions; they are not framebuffer pixels. Scale can change with
the window, GUI-scale option and Unicode-font settings.

Delivery means one event per completed HUD overlay pass, after the vanilla HUD and
before the current in-game screen is drawn. It continues behind chat/inventory/pause
screens, but is suppressed when F1 hides the GUI or no world/player exists. Title
menus do not receive it. If another mod cancels Forge's `Pre(ALL)`, Forge skips the
overlay and its `Post(ALL)`, so Perihelion receives no HUD event. The adapter follows
this pipeline and does not render a replacement overlay.

`RenderTickEvent` remains a frame timing event, without a GUI-projection contract.
`WorldRenderEvent` remains the 3D drawing event. These are distinct event types; do
not draw the same HUD from both the frame and HUD events. The adapter skips creating
a `HudRenderEvent` when there are no subscribers.

Use the normal scaled GUI coordinates in a HUD callback and scope each listener's
temporary GL changes with the helpers below. The event does not reset GL state
between arbitrary unscoped listeners or repair another mod's broken rendering state.

## State lifetime

Keep a `RenderState` instance in each renderer. Open it inside the render callback,
before changing state, and close it with try-with-resources:

```java
private final RenderState renderState = new RenderState();

void renderLines() {
    try (RenderState ignored = renderState.open()) {
        RenderStyle.lines(false, 1.5f, 0xff80c0ff);
        // Draw the batch here, with the world projection/camera already established.
    }
}
```

Restoration runs before an exception reaches the existing event-owner fault boundary.
Do this inside each module's listener: a guard around the whole event dispatch would
let a failing listener contaminate the next listener before restoration.
Do not register an open render scope with `ModuleContext.own`: its lifetime is the
draw call, not the module activation.

Scopes capture the entry state rather than assuming vanilla defaults. Nesting uses
separate instances and must close in reverse order. Reopening the same active instance,
closing out of order, and use from a non-client thread fail explicitly. A closed instance
can be reused; repeated close is harmless. Do not save aliases returned by `open()` for
later use, and never keep a scope open across frames. An active OpenGL 1.4 or newer
compatibility context is required.

Each instance allocates its direct query buffers once. Open/close do not create scope
objects, collections or buffers. They query GL at the batch boundary, which can still
cost driver time; use a scope per render batch, not per vertex/entity/glyph. This is
an allocation design, not a measured FPS guarantee.

## Restoration boundary

`RenderState` restores:

- Depth testing, comparison function, write mask and depth range.
- Blending, separate RGB/alpha factors and equations; alpha testing and its function/reference.
- Lighting, fog, culling, color material, normalization, rescale-normal and color-logic enable flags;
  cull mode, logic operation and shade model.
- Polygon-offset fill enable, factor and units; line width and line smoothing enable.
- Current color, color write mask, scissor enable and rectangle.
- Active texture unit; 2D texture enable, binding and texture matrix on the default and lightmap units.
- Modelview/projection matrices, current matrix mode, and excess pushes on those two stacks
  and the default/lightmap texture stacks, including pushes skipped because drawing threw.

For cached state, restoration updates both `GlStateManager` and the GL driver. This
also handles raw GL calls leaving Minecraft's cache stale for the listed values. A
plain `glPopAttrib()` does not synchronize Minecraft's cache; it is not used here.
The API signatures were checked against the pinned mapped 1.8.9 dependency and
[LWJGL 2's GL11 API](https://legacy.lwjgl.org/javadoc/org/lwjgl/opengl/GL11.html).

This is a fixed-function drawing boundary, not a snapshot of the entire graphics
context. It does not restore framebuffer/viewport, shader programs/uniforms, stencil
configuration, VBO/client-array state, material/light/fog parameters, texture sampling
parameters or contents, texture units beyond default/lightmap, or lightmap coordinates.
Do not delete textures owned by another renderer or pop matrix entries belonging to
an enclosing renderer. Deleted objects and lost lower matrix entries cannot be recovered.
Custom shaders or full entity-renderer calls need additional, explicit scopes for the
state they change; OptiFine shader-pack internals are not covered.

Keep `glBegin`/`glEnd` and tessellator start/finish exception-safe separately. GL state
cannot be restored while an immediate-mode primitive is still open. Do not swallow an
exception and leave a tessellator batch unfinished.

`RenderStyle.lines(throughWalls, width, argb)` establishes untextured, alpha-blended
lines without depth writes. `throughWalls=false` retains depth testing. Width must
be finite and positive; the driver may clamp it to its supported range. `text()`
establishes textured, alpha-blended overlay drawing. Both disable lighting, lightmap,
fog, culling, color logic and polygon offset, and require an open state scope.
They do not establish a camera or screen projection.

## Text and font quality

`HudText` uses the current Minecraft `FontRenderer` for both drawing and measurement.
It keeps vanilla/resource-pack glyphs, Unicode fallback, formatting codes, language
handling and shadow behavior. It does not hold a second font cache or change texture
filters. `width()` and `height()` return native GUI-coordinate metrics.

```java
private final HudText text = new HudText();
private final RenderState textBatch = new RenderState();

// Register during onEnable: scope.listen(HudRenderEvent.class, this::renderLabels).
void renderLabels(HudRenderEvent event) {
    try (RenderState ignored = textBatch.open()) {
        text.drawInScope("Perihelion", event.scaledWidth() - text.width("Perihelion") - 8,
                8, 0xffffffff, true);
        text.drawInScope("Status", 8, 8 + text.height() + 3, 0xffb0c8df, false);
    }
}
```

`draw()` is a convenience that supplies its own reusable state scope for one label.
`drawInScope()` shares the caller's scope for a batch and avoids repeated GL snapshots.
Coordinates snap to whole GUI pixels, and non-finite coordinates are rejected. Use
native font size and Minecraft's integer GUI scale; arbitrary fractional matrix scaling
of this bitmap font can still blur it and is intentionally not a feature of this helper.
Font appearance still follows the selected font/resource pack; no custom smooth font
is bundled.

Colors are **ARGB**, including explicit alpha: `0xffffffff` is opaque white and
`0x00ffffff` is transparent. Vanilla interprets alpha values 0–3 as unspecified/opaque.
The wrapper skips zero alpha and clamps 1–3 to 4/255, avoiding an opaque flash at the
end of fades. The text preset enables glyph texturing, uses ordinary source-alpha
blending, disables depth writes and removes lighting/fog tint before drawing. It
restores the entry state afterward, including whichever font page texture was bound.

## Verification

`RenderStateTest` covers nested exception cleanup, instance reuse, double close,
out-of-order close, reentry rejection, thread ownership and failed capture.
`HudTextTest` checks coordinate alignment and vanilla's low-alpha edge case.
These unit tests do not create an OpenGL context.

The separate `RenderHookProbe` runs in the isolated Minecraft title smoke scenario.
It compares real GL state before and after nested scopes and an intentional exception,
checks Minecraft cache behavior, leaked matrix pushes and repeated reuse, then draws
plain/shadowed/formatted/Unicode/transparent text. The run writes `render-helpers.png`
for visual inspection. Test fixture classes are excluded from the distributable.
`HudHookProbe` additionally exercises actual overlay events in the world scenario:
Forge event filtering, current scaled dimensions/projection, client-thread delivery,
GUI scales 1/2, F1 with chat open, cancelled overlays, resumption behind chat, and a
throwing listener whose state scope restores GL before the next listener draws.
See [smoke instructions](../scripts/smoke/README.md) and the recorded
[HUD event evidence](verification/hud-rendering.json) and
[original rendering-helper evidence](verification/rendering.json) for tested artifacts and limits.

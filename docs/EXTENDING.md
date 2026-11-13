# Adding a future feature

The client ships the native modules listed in [MODULES.md](MODULES.md). These rules apply to further features.

1. Describe the behavior, event timing and world/packet assumptions before porting code. Check the donor's license and attribution requirements.
2. Give the module a stable lowercase ID, non-blank human-readable name, description and a `ModuleCategory` constant. Choose `requiresWorld=true` whenever callbacks need a player/world.
3. Construct settings once with `runtime.tasks()::checkThread`. Register them through the module's `setting(...)` method before registering the module. Set visibility rules during construction; module registration freezes the definitions. See [setting types, metadata and formats](SETTINGS.md).
4. Register the module explicitly in the startup definitions callback in the Forge composition root. Do not add scanning or another singleton registry.
5. Subscribe through the `ModuleContext` supplied to `onEnable`. Its owner gates and removes listeners automatically. Register other activation resources immediately with `context.own(resource)` (`AutoCloseable`) or `context.onCleanup(callback)` (`Runnable`). These are released even when enable fails; `onDisable` still runs only after a successful enable.
6. Use `listenNetwork` only when raw packet timing is necessary. Callbacks may run concurrently and must not access live Minecraft state. Copy immutable data and use `deferForWorld` for subsequent world work.
7. Keep game-specific behavior in `integration.minecraft189`. Keep Forge annotations/events in `bootstrap.forge`; adapt each new hook once into a core or game-specific event.
8. Reuse settings, commands, bindings and the config document. Add narrowly scoped shared helpers only when behavior needs them; do not import a donor's entire utility tree.
9. Add regression cases for enable/disable, missing world, reconnect, listener failure and stale deferred work. Keep examples and fault-injection modules in test sources.
10. Run `check`, then repeat packaged Forge and OptiFine smoke tests if hooks or loader behavior change. A successful dev launch does not prove SRG remapping works in the distribution.

## Thread and lifetime rules

`ModuleInfo.category()` returns a core `ModuleCategory`, not a free-form string.
The fixed categories are `COMBAT`, `MOVEMENT`, `PLAYER`, `WORLD`, `RENDER` and `MISC`;
each exposes a human-readable `displayName()`. Use Player for player/inventory
actions, World for block/world interactions, Render for visual features, and Misc
for general utilities that do not fit the other groups. Test fixtures use Misc
rather than introducing a production Test category.

Categories are definition metadata, not config keys. Changing a category does not
rename the module, change its persisted state, or alter activation behavior. There
is no category registry, string parsing, GUI dependency or persisted enum ordinal.
Settings likewise have explicit labels separate from stable IDs; see
[setting definitions](SETTINGS.md).

For rotations, held inputs, hotbar slots, timer speed and sprint state, use
`context.controls()` instead of direct Minecraft writes. Allocate reusable
activation handles, submit from owned callbacks, and yield actions when a needed
claim loses. See [shared controls](SHARED_CONTROLS.md) for priorities, expiration,
player phases, and `MinecraftControls.withHotbar` for item-dependent actions.

- Construct/start/stop runtime and change modules/settings on the client thread.
- `ModuleEntry` and registry views are client-thread owned. A settings value can be read from another thread, but not mutated there.
- Modules must treat network listeners already in progress during disable as concurrent work. Do not release shared mutable resources out from under such callbacks.
- Avoid retaining worlds, players, entities, screens or packet buffers in long-lived services. Store IDs/copied data where practical; generation-bound deferred work prevents using a previous world.
- `ModuleContext` belongs to one activation. Do not reuse it after disable or cache it as a global service.
- Register module observers with `context.observe(setting, listener)`, including when observing another module's setting. The activation gates callbacks, owns removal and receives observer faults. `context.own(setting.onChange(listener))` only owns removal; it cannot attribute callback failures. Use `runtime.modules().setSetting(moduleId, setting, value)` for command, config and future editor mutations; see [observer and mutation contracts](SETTINGS.md#observers-and-mutations).
- Never call another module's `onEnable`/`onDisable` or mutate state flags directly. Go through `ModuleManager`.

## Self-disable

Call `context.requestDisable()` on the client thread when a one-shot module finishes its
work in `onEnable`. The request does not interrupt the hook: return explicitly if there
is no more work to do. After the hook returns successfully, the manager clears the
requested enabled preference, runs normal disable/cleanup, and reports the change for
configuration persistence. Listeners never become active for that activation, and its
deferred work is discarded. No task-queue drain is needed.

If enable throws after requesting disable, normal enable-failure handling wins: the
module stays faulted, resources are released, and `onDisable` is skipped. A failure in
the subsequent disable/cleanup also faults the module. Repeated requests, including
requests during cleanup or from an old context, have no effect on later activations.

Requests from an already-enabled activation disable it immediately. Network callbacks
must hand off with `context.defer(context::requestDisable)` and check whether the queue
accepted the task. Direct reentrant calls to `ModuleManager.setEnabled`/`toggle` during
lifecycle hooks remain errors; use the scoped request for self-disable.

## Activation cleanup

Register resources and callbacks on the client thread, immediately after acquiring them. `own` returns the resource for use. Each registration is released once per activation; do not register the same resource twice or also release it manually. Every enable receives a fresh context.

On disable, world loss, suspension, shutdown or quarantine, listeners detach first, then `onDisable` runs when applicable, then owned resources and callbacks run in reverse registration order. A failed enable skips `onDisable` but still releases everything already registered. Keep unregistered partial initialization locally exception-safe.

Cleanup runs on the client thread. A network fault detaches listeners immediately and leaves resource cleanup to the manager's client-thread fault processing. Ordinary cleanup failures do not stop other callbacks or modules: the original failure stays primary and additional failures are suppressed. A cleanup failure during normal disable faults the module. Faults reported during cleanup join the same failure report; the module stays `DISABLING` until cleanup ends. Fatal JVM errors still propagate.

Registrations against an ended context are released immediately on the client thread, including registrations made during cleanup. If that immediate release fails, registration throws `IllegalStateException` with the cleanup failure as its cause. This prevents late resources from leaking; it does not make an old context reusable.

## Changes that need an explicit design decision

For temporary rendering state, use the lexical scopes and native-font helpers in
[rendering helpers](RENDERING.md). Read their fixed-function restoration boundary before
calling a full entity renderer or introducing shaders.
Use `HudRenderEvent` for in-game drawing in scaled GUI coordinates and
`WorldRenderEvent` for 3D rendering. `RenderTickEvent` is for frame timing; it does
not promise a GUI projection. Register HUD listeners through the module's activation
scope and keep render-state scopes inside each listener.

Packet replay/delay/replacement, silent sends, late injection, profile migration,
server/minigame detection, GUI state and HUD layout each need their own semantics.
The existing foundation leaves them open deliberately; adding one should not create
a second event bus, scheduler, config store or input registry.


## Client frontends and default bindings

A definition may call protected `defaultBinding(Keybind)` before registration. Most
modules remain unbound. Missing config binding data uses this default; an explicitly
saved `NONE` stays unbound. Bind changes still go through `ModuleManager.setBinding`.
Input dispatch stops if an earlier action revokes input permission (for example,
opening a screen), so subsequent shared-key targets cannot leak into gameplay.

[ClickGUI architecture](CLICKGUI.md) describes the replaceable panel frontend. Do not
import that frontend in a gameplay module. Supply ordinary settings/visibility metadata
and let the frontend select controls. A new `GuiScreen` can use the same registry and
safe mutation API directly, with or without the existing widgets. Set the screen factory
on `ClickGuiService.frontend(...)` at composition time; no additional GUI registry is
needed before a second design exists. Keep its layout in its own client preference
setting, using the existing config store.

# Foundation architecture

## Boundaries

`ClientRuntime` is the composition root. It explicitly constructs the task queue, two event buses, module manager, input manager, configuration controller/store and command dispatcher. Definitions register through a constructor callback before configuration is applied. There is no classpath scanning, dependency-injection container or dynamic plugin system.

`core` has no Minecraft, Forge, Mixin, Netty or LWJGL dependency. `forge` houses both the loader adapter and Minecraft 1.8.9 integration; Forge-specific references stay in `perihelion.bootstrap.forge`. Source import checks and distribution bytecode checks enforce the core boundary. Minecraft utilities have a narrow purpose: context availability, main-thread assertion, chat output and monotonic timing.

A future loader creates the same runtime on the client thread, supplies a `ClientLog` and config path, forwards its own tick/world/input/render callbacks, registers the local command, and installs a `HookBridge.Target`. Most game integration can remain. An actual injector still needs its own classloader and transformation solution; this foundation does not implement late attachment or class retransformation.

## Lifecycle and ownership

States are `NEW → STARTING → RUNNING → STOPPING → STOPPED`, with `FAILED` for initialization failure. Start and stop are idempotent while in their respective states; a stopped instance is not restarted. A replacement loader can construct a new instance.

1. Register definitions without activation.
2. Read and validate configuration; apply settings, bindings and requested states.
3. Attach setting observers and start the module manager.
4. Publish the running target and attach Forge events/commands.
5. Reconcile world-dependent modules when both player/world and a live connection exist.

Shutdown first detaches the static hook target and Forge adapter, then closes module activation scopes before invoking cleanup. It clears events, input, setting observers, connection references and pending work. The configuration writer gets at most two seconds to finish the final immutable snapshot. Ordinary cleanup failures are logged while other cleanup continues. There is no guarantee of persistence after forced process termination.

An `EventOwner` represents one activation. Subscriptions remain inactive until `onEnable` succeeds, and are removed before `onDisable`. Faulted scopes close immediately on the dispatch thread; module cleanup occurs on the client thread. A queued callback or deferred module action checks its activation owner before running. An already-running network callback cannot be interrupted by disable: copy only immutable values, avoid live game state, and return promptly.

`ModuleContext` adds a client-thread resource stack to that same activation: `own(AutoCloseable)` and `onCleanup(Runnable)` register cleanup in reverse acquisition order. The manager retires the `EventOwner`, attempts `onDisable` for a successful activation, then drains the stack once, including on failed enable. Ordinary cleanup failures are aggregated into the module's fault without skipping remaining resources. Resource cleanup never runs on the network dispatch thread.

Modules distinguish requested state from actual state. World-dependent modules move to `WAITING_FOR_WORLD` on world loss while keeping the requested preference. Failures move the affected module to `FAULTED` with requested state false. Explicit enable retries it. Test-only modules exercise these rules; the shipped registry contains none.

## Event contracts

`HudRenderEvent` is the client-thread 2D drawing point forwarded from Forge's
`RenderGameOverlayEvent.Post(ALL)`, after the vanilla overlay. Its immutable primitive fields carry partial
ticks, scaled GUI width/height and scale factor from that overlay pass. F1 and
missing world/player suppress it; open in-game screens draw afterward. Cancelled
overlays have no completed pass to forward. `WorldRenderEvent` remains the 3D
render point; `RenderTickEvent` carries frame timing rather than a GUI projection.
Individual HUD listeners own their temporary GL state through
[rendering scopes](RENDERING.md); an outer dispatch guard cannot isolate a bad
listener from the next listener. With no HUD subscribers, no core HUD event is allocated.

`ModuleContext.controls()` creates activation-owned requests for rotations, input,
hotbar selection/spoofing, timer speed and actual sprint state. The core resolves
highest priority, then earliest claim, without Minecraft imports. Requests use the
existing owner gate, cleanup stack and world generation. The 1.8.9 adapter applies
winners at tick/player boundaries and restores its state on release. The runtime
owns that adapter through `ClientRuntime.own`, closing it after modules on shutdown
or startup rollback. See [shared control contracts and module usage](SHARED_CONTROLS.md).

- Exact event class dispatch; no superclass/interface delivery.
- Higher priority first; registration order resolves ties.
- Immutable listener arrays rebuilt only on registration/removal. No reflection, sorting or listener-list allocation during dispatch.
- Subscription additions become visible on the next dispatch; removal gates subsequent invocations even in an existing snapshot.
- Client events assert the client thread. Raw network events explicitly allow concurrent callback threads.
- Only events with cancellable hooks extend `CancellableEvent`. Cancelled events skip ordinary listeners; observers may opt in.
- If a listener fails, its new cancellation decision is rolled back, previous valid cancellation remains, its owner is quarantined, and unrelated listeners continue.
- `VirtualMachineError` and `ThreadDeath` propagate. Ordinary `Exception` and nonfatal `Error` are isolated at owned boundaries.

Forge provides client tick START/END, render tick START/END, world-render completion, input and world/connection signals. FML's bus is the same object as the main Forge bus in 2318; the adapter registers once. The render events expose context and draw nothing.

## Settings and storage

Settings have stable IDs, explicit immutable display names, descriptions, defaults and one validation path shared by commands and configuration. Values are immutable booleans, integers, finite decimals, enums, bounded strings, numeric ranges, RGBA colors or enum selections. Numeric definitions expose bounds and optional steps; immutable metadata exposes types and available choices. Definition-time visibility predicates run on the client thread and do not affect value validity or persistence. Module registration freezes visibility rules. Writes and observer registration assert the client thread; value reads are volatile. Observers receive committed, normalized values in registration order. Raw observers continue delivery after ordinary failures and surface aggregated failures to the caller. See [setting contracts and formats](SETTINGS.md).

`ModuleContext.observe` wraps that same observer list with the activation's existing `EventOwner` gate and fault handler, and owns removal through the client-thread cleanup stack. A failure retires the observing activation immediately, so its later callbacks are skipped even when observing another module's setting. `ModuleManager.setSetting` is the shared command/config/future editor boundary: validate before mutation, preserve the committed value, then process queued owner faults after notification. Raw observer failures retain the legacy fallback of quarantining the setting's defining module. No setting-to-module inference is needed for owned callbacks.

Setting and module changes only mark `ConfigController` dirty and reset a monotonic 750 ms debounce. At client tick END, after that quiet period, the controller captures one snapshot on the client thread and submits it immediately to `ConfigStore`; there is no second debounce. No snapshots or save tasks are created per change. Status includes both uncaptured changes and pending/failed writes. Explicit save bypasses the debounce, and shutdown always captures final state, including module cleanup changes, before the bounded writer flush.

`ConfigStore` copies submitted documents and serializes and writes through one daemon writer. Its direct `save(document)` API still debounces already-built documents. It writes/fsyncs a temporary file, preserves a valid backup and replaces the primary atomically where supported. Non-atomic replacement is a documented filesystem fallback, not a power-loss guarantee.

Input JSON is bounded to 4 MiB and nesting depth 64. Parsing rejects duplicate keys, comments, malformed UTF-8 and trailing/lenient JSON. Unknown root/module/setting entries survive round trips. Invalid individual values fall back to defaults with diagnostics. Corrupt primary files are preserved before backup recovery. A newer schema or inability to safely preserve unreadable data disables writes.

Reload discards uncaptured edits and pending debounced writes, and reads after already-running writes. Automatic snapshot capture pauses until reload finishes. Reload parses before applying, suspends modules, applies validated values on the client thread, and resumes eligible modules. Changes made while reload is pending invalidate its result and remain dirty for saving. A broken setting observer quarantines its module while other definitions continue. Pending reloads are completed exceptionally on shutdown or a full/unavailable client queue.

## Input, context and deferred work

Bindings identify keyboard or mouse plus an integer code, with an explicit unbound value. Press edges toggle modules in registration order. Repeats, unfocused windows and open screens do not toggle. A physically held key across a focus/screen transition needs a release before it can activate again.

The adapter detects changes in world/player/network-manager identity and connection openness. Each context change advances a generation. `ModuleContext.deferForWorld` rejects work whose generation is stale; `defer` still rejects inactive owners. The queue allows 1,024 pending tasks and drains at most 128 per tick. It never creates a worker pool or blocks Netty waiting for the game thread.

Connection context distinguishes disconnected, single-player and multiplayer. Hypixel detection is a normalized hostname suffix hint with a domain boundary; it is not authentication, minigame detection or a server API.

## Packet hooks

Narrow `NetworkManager` injections and Minecraft shutdown, key-state, held-slot and player-update hooks use a required Mixin config and generated SRG reference map. There are no overwrites or custom ASM transformers. Control hooks preserve vanilla packet selection and synchronization; they do not add a packet replacement path.

Outbound interception occurs at private `dispatchPacket`, where both public send overloads and queued sends converge. This gives one callback per dispatched packet and preserves queue order. A queued packet's callback runs on the eventual flush caller, not necessarily its original sender. Inbound interception occurs at typed `channelRead0`, before packet delivery.

Eligibility requires a running runtime, an open non-local client connection, a play handler and a registered PLAY packet. Login/status packets, server-side managers, integrated-server channels and Forge proxy packets are excluded.

Raw listeners may inspect and cancel. They must not retain mutable payloads, mutate world/player state, block, or recursively send packets. Copy needed data and defer game work through the module scope. A recursion guard makes recursive sends fail within the offending owner's boundary; ordinary unrelated delivery continues. The Forge layer now supplies activation-owned `PacketBufferScope` and immutable encoded `PacketSnapshot` values for the gameplay modules. Queues are bounded and retain capture order. Replay occurs only on the client thread, validates the original connection/world generation, and bypasses raw hooks through a narrowly scoped `RawHookBridge.replay` call. Raw callbacks themselves still cannot replay or send recursively. See [Yuri port contracts](YURI_PORT.md).

Cancelling an outbound packet creates and cancels a channel promise for the caller's completion listeners, in their original order, without closing the channel. The raw callback scope ends before those listeners run, so completion listeners may send. Cancelling an inbound custom payload releases its otherwise unconsumed buffer once. Outbound payloads are not released: vanilla permits reusable packet instances.

A callback returning means interception has finished, not that Minecraft has processed the packet on its client thread.

## Performance and diagnosis

The original tick/render/network bridges skip event creation when their event type has no subscribers. Added narrow gameplay mixins dispatch typed events only in their relevant local-player/render paths. They perform no disk I/O, packet dumping, classpath scanning, entity scanning or listener-list rebuilding. World context compares references; full keyboard state is scanned only on focus/screen eligibility transitions. Settings/binding changes and configuration snapshots are cold operations.

Timing is opt-in through `/perihelion status timing on`. Listener logs include owner, event class and thread; quarantine prevents repeated failures from that activation. Configuration errors remain visible through status and the game log. Bounded tasks prevent unbounded Netty-to-game accumulation. Do not infer a frame-rate guarantee from dispatch benchmarks; consult the recorded verification limits.

## Gameplay integration

`GameplayModules` explicitly registers native definitions alongside the original modules. Helpers
share item scoring, placement planning and drawing without introducing a second registry.
Movement, item slowdown, outgoing movement values, main-thread packet handling, camera,
model/label/tile rendering, hand transforms, swing duration and attack slowdown use typed
events. Main-thread packet hooks run after `PacketThreadUtil` hands delivery to the client.
Model and tile depth overrides are lexical render scopes; Shader ESP additionally owns and
restores its framebuffer, shader program, viewport and clear color.

The client packet hooks reject retired-handler delivery after the vanilla thread handoff.
Queued single/bulk chunk packets also check the bound world/handler before touching the
world, preventing the previously observed unload-time null-world chunk failure.

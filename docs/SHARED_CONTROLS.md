# Shared Minecraft controls

Modules claim shared state through their activation's `ModuleContext.controls()`.
`SharedControls` arbitrates immutable values in the Java 8 core. One
`MinecraftControls` instance applies results through Forge 1.8.9 hooks. The
composition root constructs it and can inject it into future game modules.
Sprint, WTap and Timer use this API; see [native modules](MODULES.md).

## Ownership and priorities

Create handles once in `onEnable`, then reuse them in owned callbacks. Highest
integer priority wins; the earliest-created handle wins ties. Updating or renewing
a handle never changes tie order. An unset handle makes no request. Boolean
`false` actively suppresses a state; it does not release it. Visible and spoofed
slots share one channel. Yaw and pitch form one atomic rotation request.

- `set(value)` holds until cleared, retired, or invalidated by a world change.
- `setForTicks(value, n)` holds for this client tick and the next `n - 1` ticks.
  Tick START expires values before deferred work and callbacks. Renew one-tick
  requests from tick/player callbacks; a one-tick value set during enable may
  expire at the next START before any player update consumes it.
- `clear()` yields while retaining the reusable handle.
- `isWinning()` tests whether this handle owns its channel.
- `value()` returns this handle's eligible value, including when it loses.
- `close()` permanently retires a handle and is idempotent. Activation cleanup
  already owns it. A retired handle's `set` returns `false`.

Arbitration reads and all mutations require the client thread. Network callbacks
must defer copied data through the module context, using `deferForWorld` when the
work belongs to a particular world.

Claims stay invisible until enable succeeds. Disable, faults, failed enable,
self-disable, reload suspension and world-dependent deactivation retire the same
existing `EventOwner`. Network faults make claims ineligible immediately; removal
and game restoration run on the client thread. Re-enable creates fresh handles.
Old contexts cannot affect their successors. World generations invalidate values
even for modules that stay enabled outside a world; those modules must explicitly
submit new values. Expired handles remain reusable until activation ends, so do
not allocate handles every tick.

Game writes reconcile at tick START/END and the player phases below. Key queries
and vanilla held-item synchronization resolve the current winner. Cleanup cannot
undo submitted actions/packets. Runtime-owned adapter cleanup restores state on
direct runtime shutdown and startup rollback as well.

## Channels and game behavior

| Claim | Value | Minecraft 1.8.9 behavior |
| --- | --- | --- |
| `rotation(priority)` | `Rotation.view(yaw, pitch)` | Applies the camera before movement and walking packets. Release keeps the current view without snapping to an old angle. |
| `rotation(priority)` | `Rotation.server(yaw, pitch)` | Supplies one yaw/pitch pair to vanilla walking packets, delta checks and last-reported caches, preserving local view and movement direction. |
| `key(action, priority)` | Boolean | Overrides `KeyBinding.isKeyDown`. Release uses the current vanilla state. Physical `pressed` fields and `isPressed` click counts remain intact. |
| `hotbar(priority)` | `HotbarSelection.visible(slot)` | Selects local slot 0–8 and synchronizes it through the vanilla controller. |
| `hotbar(priority)` | `HotbarSelection.spoof(slot)` | Selects the effective server slot through vanilla `syncCurrentPlayItem`, preserving visible selection. |
| `timerSpeed(priority)` | Positive finite Float | Applies one absolute speed; competing requests do not multiply. |
| `sprinting(priority)` | Boolean | Controls actual sprint state, independently of the sprint key, for temporary sprint suppression. |

Inputs include forward/back/left/right, jump, sneak, sprint, attack and use-item.
Input overrides require a live world and focused OS window. Screens do not
automatically suppress explicit requests, allowing InventoryMove-style behavior.
Modules choose eligible screens and can read configured keyboard or mouse bindings
using `MinecraftControls.physicalKeyDown(action)`. This reads raw hardware, never
the override. One-shot clicks/actions still need the appropriate game API.

Timer, visible-slot and sprint restoration capture a baseline when first applied.
Changing winners preserves it. A different manual/external value observed between
writes becomes the new baseline. Release restores only when the current value
still equals the adapter's last write. An external write of an identical value
cannot be distinguished from its own write.

Server rotation covers the non-riding walking-packet path. Aim selection,
smoothing, movement correction and riding packets remain module/vanilla concerns.
The local third-person model displays the submitted silent angle through scoped
renderer reads. Camera picking remains vanilla; automatic native item actions can
use `withRotation` for their internal ray tracing and orientation. Modules remain
responsible for action geometry.
Slot spoofing retains ordinary C09 synchronization/deduplication. It does not
rewrite arbitrary packets or spoof inventory windows. External mods that write
these fields/send their own packets do not participate in this arbitration.

## Player phases and item actions

Subscribe through `context.listen(PlayerControlEvent.class, ...)`:

1. `BEFORE_INPUT`: collect input, sprint, timer and movement-related view requests
   before the local non-riding player's living update.
2. `BEFORE_MOTION`: collect final aim/hotbar requests, then apply winners and
   capture one rotation pair before vanilla walking packets.
3. `AFTER_MOTION`: walking packets have been submitted; perform rotated actions
   only when `isRotationApplied(rotationRequest)` and the required slot claim win.

Scaffold commits placement during the following `BEFORE_INPUT`, before that tick's
walking packet. `previousRotation(request)` exposes the preceding submitted aim
only in this window and at the same player position. Renew that exact immutable
value, check `isPreviousRotationApplied(request)`, and use `withPreviousRotation`
inside the hotbar transaction. A new unsent angle, competing owner, repeated input
pass, changed position, server correction, canceled walking, resend request or
world retirement cannot reuse that permission. See
[Scaffold PRE timing](SCAFFOLD_PRE_TIMING_2026-10-11.md) for the packet-order fix.

Collection listeners finish before the adapter applies results. Use the action
phase when an operation depends on completed collection. A captured rotation pair
remains coherent if its owner faults during packet dispatch. These phases do not
guarantee server receipt.

Call `minecraftControls.withHotbar(slotRequest, stack -> { /* game action */ })`
from an owned client-thread callback. It returns `false` when that runtime's claim
no longer wins. Otherwise it synchronizes the effective slot, temporarily aligns
local held-item calculations, supplies the selected stack (possibly null), runs
the callback synchronously, then restores local selection in `finally`. Throwing
callbacks and shutdown inside the action restore safely. Actual connection
identities guard the final sync, preventing an old-world restore on a new
connection. Nested actions are rejected. Do not retain the mutable stack across
callbacks. Ordinary failures propagate to the module's existing fault boundary.

An illustrative activation using a reusable timer claim:

```java
@Override protected void onEnable(ModuleContext context) {
    ControlRequest<Float> timer = context.controls().timerSpeed(20);
    context.listen(ClientTickEvent.class, phase -> {
        if (phase != ClientTickEvent.START) return;
        if (shouldUseTimer()) timer.setForTicks(speed.get().floatValue(), 1);
        else timer.clear();
    });
}
```

For combined aim/item actions, check `isRotationApplied(rotationRequest)` and use
`withHotbar` for its slot claim. `isWinning()` alone cannot authorize an action:
another owner may have supplied that walking packet and then released its claim.
The applied check matches both the request identity and its immutable value, and
expires at the next input/walking pass or world/activation/shutdown invalidation.
It records walking submission, not delivery through packet queues or server receipt.

Inside `withHotbar`, use `withRotation(rotationRequest, () -> { /* native action */ })`
when a vanilla item needs the player's local yaw/pitch for its own ray trace or
orientation. This temporarily aligns only the acting player's current fields and
restores them in `finally`; an external camera write is preserved. The callback is
synchronous, client-thread-only and cannot nest another rotation action. Exceptions
propagate to the existing module fault boundary. Neither helper replays packets.

Losing either required claim should yield the action. Priorities
express the operation: a temporary item action or sprint reset can outrank a
background preference. Do not give every module the largest priority.

## Scope and testing

Actual sprint state was added because holding the key and resetting an existing
sprint are different operations. Movement vectors, target choice, inventory-window
transactions, camera zoom, packet scheduling and rendering state need additional
semantics and remain outside this change. No donor framework or cross-version
abstraction was introduced; see [donor references](DONORS.md).

Core tests cover ownership, priorities/ties, channels, expiration, stale worlds and
activations, failed/self-disable enable, disable/callback faults, cleanup failures
and thread guards. Adapter restoration has pure unit tests. The packaged `world`
smoke scenario exercises transformed hooks, including shutdown inside a spoofed
action. See [verification](VERIFICATION.md) for outcomes and coverage limits.


### Replaced walking methods

Some clients replace `EntityPlayerSP.onUpdateWalkingPlayer` completely. The two rotation
read redirects are optional in that case, while the other hooks retain strict requirements.
A Mixin plugin checks the transformed method for both installed redirects and exposes
`MinecraftControls.supportsServerRotation()`. If either is missing, server-only rotation
is unavailable and one startup warning explains why. Neither half of a rotation request
is applied. Future modules requiring silent rotation must check this capability. Normal
Forge/OptiFine retains the packet rotation hooks and unchanged camera behavior. View
rotation still updates the local camera; the other client's own rotation behavior may
subsequently override it. This does not promise compatibility between two active clients'
movement modules.

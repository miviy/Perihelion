# Yuri 1.3.1 Aim Assist and Block In

Reference: Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`.
The reused algorithms carry AGPL-3.0-only attribution. Perihelion retains its
module lifecycle, settings, events, targeting policy and control arbitration.

## Aim Assist

The current Simple and Adaptive algorithms replace the earlier native tick-based
visible/silent selector. Settings now match the donor's player/mob/invisible/team
filters, mouse/sword/block-break conditions, target priority and area, distance,
angle, horizontal/vertical speed and strafe scaling. Simple integrates elapsed
millisecond steps, capped at 30 per frame. Adaptive uses the donor prediction,
acceleration, drift, snap hysteresis and frame interval equations. Random samples
retain source distributions; separate runs will not produce identical samples.

HUD render events calculate the camera delta. Calculation temporarily uses the
vanilla `setAngles` behavior, restores both current and previous view fields, then
commits the resulting VIEW request only when its activation still owns the
winning request, player and session. A stronger rotation owner prevents both
current and interpolation-field mutation. The committed frame claim is cleared
immediately so a later physical mouse turn cannot be overwritten next tick.

This is visible assistance, with no obsolete silent mode. Native target/friend
policy and ownership can reject a target or view change the donor would accept.
The transformed smoke probes exercise actual frame commits, independent target
selection, stronger-owner yielding and disable cleanup.

## Block In

The enclosure path, support geometry, strength ordering, retry/failure sets,
rotation interpolation, delay, completion and middle-click behavior adapt the
current donor algorithm. Percentage drawing uses native font/render scopes.
Packet callbacks publish immutable block-action facts into an activation-owned
window instead of reading live Minecraft state on the network thread.

A pending placement binds target, support, face, slot, stack identity, immutable
rotation, player/world identity and preparation position. It holds that exact
angle across a completed walking boundary. PRE execution checks the same winning
request and submitted aim, recomputes the ray against the bound support/face and
validates current geometry and inventory before one native placement. Movement,
a GUI, session retirement or a replaced stack invalidates stale work. A plan
expires after 12 input passes. Swing/animation is emitted once by native placement.

This can add an initial walking tick and reject stale work accepted by Yuri's
global rotation state. Actions stay in the native PRE phase; no later action
phase is introduced to hide that difference. Packaged tests cover actual packet
ordering and rotation ownership; they do not establish server bypass behavior.

Completed integration evidence and remaining limits: [YURI_131_VERIFICATION.json](YURI_131_VERIFICATION.json).

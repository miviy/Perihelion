# Yuri 1.3.1 Back Track parity

The later [follow-up audit](YURI_131_AUDIT_2026-10-17.md) adds live legacy/timed-policy transition draining. Same-policy delay edits preserve existing deadlines; transitions retire capture state and flush the original FIFO before reacquiring. This avoids a donor edge-case stall.

Reference: Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`,
`src/main/java/ddlc/yuri/modules/impl/combat/BackTrackModule.java`.
Native implementation: `BackTrackModule`, with packet-independent `BackTrackState`.
Adapted source is marked AGPL-3.0-only; the donor license is bundled at
`META-INF/licenses/Yuri-AGPL-3.0.txt`.

| Mode | Traffic held | Timing | Range |
| --- | --- | --- | --- |
| Basic / New | All inbound vanilla play packets | Each packet delayed by Delay, default 400ms, 40–2000ms in 10ms steps | Configured Min Range / Max Range, default 2–4 |
| Basic / Old | All entity relative movement and teleports; local velocity or correction requests a full flush | Batch held until Delay elapses or flush is requested | Configured range |
| MMC | All inbound vanilla play packets | Each packet delayed 1000ms | Fixed 0–8 |
| Hypixel | Same traffic and flush conditions as Basic / Old | Batch held 250ms | Configured range |

Style and Delay are visible only for Basic; ranges are hidden for MMC. Range
checks include both endpoints and measure from interpolated local eyes to the
target's real server position. The real position begins from `serverPosX/Y/Z`,
then follows the selected target's relative packet deltas and teleports in exact
1/32-block integer coordinates. Other entities' movement is held without changing
the selected target's real position.

Each pre-input update checks flush requests, target validity/range, legacy expiry,
target changes and due packets in donor order. HUD rendering also releases due
packets or requested flushes. Switching targets flushes the previous backlog before
initializing the next target. Only the legacy modes move a C02 interaction's target
to its real position. The marker uses the donor's fixed 0.6 by 1.8 bounding box,
through-wall fill alpha 60/255, outline alpha 40/255 and 0.5px outline.

Native integration differences:

- Packets are privately encoded `PacketSnapshot` values in a FIFO
  `OrderedPacketQueue`, capped at 8192 packets and 16MiB. Overflow discards the
  backlog and faults the activation. The donor retains unbounded mutable packets.
- Replay runs only on the client thread against the original live handler, world
  and runtime generation, checking ownership before every packet. World teardown,
  replacement connections, closed channels and retired activations drop stale
  work. Ordinary disable flushes only while those ownership checks still pass.
- Raw callbacks use copied policy values and primitive target/server coordinates;
  they never read live settings or entities. A C02 sent from another thread hands
  off only an immutable target ID and position for a guarded client-thread update.
- Native nearest-target selection through Aura's existing filters replaces Yuri's
  global TargetManager when Only Kill Aura is disabled. Only Kill Aura requires
  enabled native Aura. The native HUD accent colors the marker in place of Yuri's
  two global theme colors, and `RenderState` restores the complete GL state.
- The preexisting `max-delay` config key remains, with the donor's Delay label.
  Monotonic milliseconds replace wall-clock milliseconds. Native event fault
  handling reports packet replay failures instead of silently swallowing them.

`BackTrackStateTest` covers mode-specific capture/timing, exact legacy expiry,
inclusive ranges, MMC overrides, integer position tracking, target resets, flush
FIFO ordering and delay changes. `OrderedPacketQueueTest` separately covers queue
bounds and concurrent FIFO behavior. Build/test execution and client rendering,
connection teardown and live-server observations must be reported separately;
these policy checks do not establish public-server bypass success.

Completed integration evidence and remaining limits: [YURI_131_VERIFICATION.json](YURI_131_VERIFICATION.json).

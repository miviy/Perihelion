# Yuri 1.3.1 inventory transaction audit

Reference: Yuri `1cb86ad94ce1f01aa9b715577163c8ecc47b7def`,
`modules/impl/movement/InvMoveModule.java` and `managers/impl/BlinkManager.java`.
Native implementation uses `InvMoveModule`, `InventoryMoveState`, existing shared
input/rotation controls and activation-owned encoded packet buffers.

The packet audit corrected three concrete differences:

- Opening inventory Blink or clicking in Close mode starts collection during the
  original send callback. The opening/click packet enters the same queue immediately;
  collection no longer waits for the next client tick. Blink captures all outbound
  traffic, including keepalives and transaction confirmations, as in the donor.
- Ordinary delayed transactions use native event-aware sends and the shared
  outbound ordering barrier. Close mode's explicit bypass-close action uses
  `RawHookBridge.replay` on the client thread, matching the donor's separate
  bypass path rather than accidentally joining the held blink backlog.
- Held click snapshots belong to the handler, world and runtime generation from
  activation. Every replay rechecks those identities and the open channel.
  Teardown irreversibly retires transaction state, drops snapshots and prevents
  late network callbacks or setting observers from reviving the old activation.

Legit still opens first, releases one original-tick click batch per update, then
closes when the cursor is empty or close was requested. Legit Slow adds its finish
tick pause. Blink releases two updates after close; Close mode uses its three-update
release countdown and separate bypass close. Invalid empty-cursor interactions do
not open a transaction. Count and byte overflow fault the activation instead of
replaying a partial transaction. Pending Legit movement stops also remain active
after the GUI closes, and sprint suppression precedes optional movement eligibility.

`InventoryMoveStateTest` covers batched click order, separate click ticks, held
cursor/close behavior, Legit Slow, invalid clicks, initial blink activation,
release countdowns, bypass-close identity, foreign blink ownership, queue bounds
and irreversible retirement with late callbacks. These are policy tests; packet
transport, GUI movement and live-server behavior require separate runtime evidence.

The [second parity check](YURI_131_SECOND_CHECK_2026-10-18.md) corrects chest
Blink collection eligibility: unlike inventory-open Blink, chest collection must
wait until the midair and speed-potion checks pass. Its localhost TCP regressions
cover blocked eligibility, allowed capture, and disable flushing the original
packet. The source PreUpdate precedes movement input, confirming the existing
transaction-advance / movement-stop order.

Current integration evidence and remaining limits:
[YURI_131_SECOND_CHECK_2026-10-18.json](YURI_131_SECOND_CHECK_2026-10-18.json).

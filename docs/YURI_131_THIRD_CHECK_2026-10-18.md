# Yuri 1.3.1 third parity check

Historical artifact: superseded by [the deeper fourth check](YURI_131_FOURTH_CHECK_2026-10-18.md).

Compared native code first, then the unchanged read-only reference
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def`. The preceding artifact and 110 rollback
file hashes were unchanged when this pass began. A fresh 644-file source archive
was verified before edits; its path and hash are recorded in the JSON report.

## Additional behavior repaired

- **Refill close timing and activation toggles.** Yuri checks whether the hotbar is
  full before its greater-than-50ms click delay, and its last-click timestamp
  survives ordinary disable/re-enable. Native Refill now does both. It no longer
  waits for another click deadline merely to close a full inventory or permits
  another click by toggling the module. Native held-cursor protection remains.
- **Auto Swap while Auto Projectile is aiming.** The mining-tool branch bypassed
  the combat-only busy check. It now consults the same native item-routine busy
  supplier before selecting either tools or weapons. The actual aiming stage
  therefore cannot change tools, and mining swaps resume after the routine ends.
  This reproduces Yuri's `AutoSwapModule.shouldSwap` policy through the existing
  native supplier/control architecture.

The new Refill runtime probe covers pending click delay, timestamp preservation,
rapid toggles, closing a full hotbar during cooldown, Auto Close off, cursor
protection, an actual splash-potion shift-click and ordinary-potion exclusion.
The existing projectile runtime fixture now enables mining Auto Swap against a
real stone target and pickaxe during actual aiming, then verifies normal tool
selection resumes after projectile suppression ends.

## Packet-test isolation repaired

The first full run failed an exact-count Fake Lag assertion after the two expected
packets arrived in FIFO order followed by an unsolicited C00 keepalive. The
controlled peer temporarily replaces `NetHandlerPlayClient.netManager`; vanilla's
background integrated-server keepalive handler replies through that field, so an
unrelated reply could enter the test TCP peer. This explains the observed extra
packet without establishing a production reorder.

`GameplayLoopback` now routes original local-channel keepalives through their
original manager while the fixture peer is installed. The route is removed after
restoring the original manager. A forced S00 regression observes exactly one
matching C00 write on the original channel. This preserves the exact FIFO/count
assertions and the global event ledger; it does not filter unexpected packets out
of assertion results or change production buffering. The failed run is preserved
in the evidence report, separate from subsequent passes.

The first Refill probe also omitted normal `GuiInventory` screen initialization;
its close callback therefore threw before completing. The fixture now initializes
the screen through `setWorldAndResolution`. Diagnostic attempts are retained in
the report; production GUI-close code was not changed to accommodate the test.
The creative smoke world also redirects an initialized inventory to the creative
container. The Refill fixture now selects survival explicitly and restores the
original controller mode, capabilities and container after the checks.

## Other source comparisons and retained differences

Checked Auto Armor scoring, chest/container delays and native transaction guards;
Auto Pot selection, effect validation, downward rotation and pending-action
cleanup; Bow Aimbot target validity/retention; projectile mode cancellation,
packet flags, wait/recovery and activation cleanup; mining/weapon score and slot
restoration; and the source timer's inclusive elapsed-time comparison.

Auto Armor's donor slider increment is 10ms and Auto Pot's is 5 units; the retained
native sliders use integer increments. The donor property permits off-increment
saved values, so replacing native validation with strict 10/5 steps would reject
previously valid saved settings. These remain explicit native UI/validation
choices; no core settings redesign was made. Auto Armor's broader container/cursor
protection and Auto Pot's safe potion-effect validation also remain native guards.

## Verification and limits

Both full packaged runs passed with the same 2,218,962-byte JAR, SHA-256
`25d0255346ac88ebfb9f24aaa4738d9861d2f4e215393dc544428ff39e18ea27`:

- Forge: `forge/run-smoke-forge-x2uui8m3/result.json`.
- OptiFine M5: `forge/run-smoke-optifine-xwe9_6sn/result.json`.

Both pass 11 Refill checks, 34 current-update checks, 85 compatibility checks,
310 rotation checks, 18 second-pass regressions, 11 Hypixel timing checks and
10 Displace handoff checks. Full unit/build verification is recorded in the JSON.
The test launcher runs a one-test config dump and replaces Forge test XML;
full `check` was refreshed afterward, but concurrent config testing replaced its
ordinary XML again. The fourth report archives complete unit XML before smoke.

Final evidence is collected in
[YURI_131_THIRD_CHECK_2026-10-18.json](YURI_131_THIRD_CHECK_2026-10-18.json).
The donor inventory still contains 102 definitions: 77 native mappings,
22 presentation exclusions and three deferred features (Insults, Skin Changer,
Keybind Info). Perihelion's menus and HUD remain native.

Local source/unit/transformed-client/TCP checks do not establish an exhaustive
mode/setting matrix, exact two-client runtime equivalence or server acceptance.
The submitted-rotation boundary, native shared ownership, queue bounds, session
checks, friend/target policy, living-target attack event, cursor/container guards,
monotonic clocks and independent random sampling remain documented differences.

# Yuri final source and boundary check

Reference: unchanged, read-only Yuri
`1cb86ad94ce1f01aa9b715577163c8ecc47b7def`. Perihelion was inspected first.
The preceding artifact/source hashes matched the fourth report before this pass.
A fresh 659-file source archive was verified before edits; archive SHA-256:
`66b78ce660acba8c7370ea914e0ef6405b255baae47e6d16694897bca122c6e8`.

## Additional corrections

| Area | Confirmed mismatch and correction |
| --- | --- |
| Tick Base | Aura-disabled updates now ease the timer without advancing cooldown; source float range edges and the before-easing tolerance check are preserved. |
| Lag Range | Lag mode arms at POST rather than its starting PRE walking pass. It uses the source per-packet delay and the single spoof call's 100 ms lease, without silently extending it to the selected target-cycle duration. Blink mode starts collecting in PRE and ends at range exit. Mode changes flush the old native ordered sequence. |
| Criticals | Only NCP motion updates prime NCP's seven-motion gate; time spent in Packet mode cannot prime its first critical sequence. |
| Derp | Yaw persists across toggles, increments use the source integer conversion, Server mode uses native source-derived smoothing from the current view on live mode changes, and movement/jump correction uses that submitted smooth yaw. |
| Input Fixes | Sneak is restored alongside movement keys after screens close. Held-input reconciliation lasts until physical release, and miss-click delay clears at PRE/POST motion. Native Sprint support and owned cleanup remain. |
| Target Strafe | Jump direction uses orbit yaw, matching walking orbit direction instead of another winning packet yaw. Aura must be enabled before its cached target is used. Native target selection remains. |
| Eagle | Each grounded update samples the source randomized release threshold. Once released it stays released; edge reentry restarts holding. Ineligible input releases the automatic hold. Airborne updates retain an active hold. |
| Phase | Normal mode uses exact block collision boxes with the five-tick age gate. Polar uses the source bounded nearby-block/50-block ground scan and position-based motion test, including its block-coordinate 1.26/1.1 thresholds. |
| Water Walk | The entire footprint is checked: mixed solid/liquid is rejected and adjacent liquid is detected when the center is air. |
| Timer | Its pulse clock persists across toggles and Constant mode, including the hold on the cycle-reset update. Output resumes through the native timer owner; zero-speed freezing remains excluded by native validation. |

These are native policy/state/query changes. No donor event bus, module manager,
configuration framework or global client was imported, and the donor was not
executed or edited. Perihelion's UI remains intact.

## Evidence and accounting

Final frozen artifact: `forge/build/libs/Perihelion-yuri-final-20261018.jar` (2,234,489 bytes),
SHA-256 `f65322626c459f8dfea03d3313cd36b33b9629c77109ec384f4f10764c5305d4`. All 405 unit tests pass (160 core, 245 Forge).
Both full packaged runs pass on this exact JAR:

- Forge: `forge/run-smoke-forge-6072x99t/result.json`.
- OptiFine M5: `forge/run-smoke-optifine-oix3clel/result.json`.

Both pass 47 new final-helper checks (seven accounted network events), 123
movement checks, 310 rotation checks, 19 inventory/velocity checks, 11 Refill
checks and all existing full-suite probes. Shutdown clears references and leaves
zero subscriptions and queued work. Existing engine/audio warnings remain
separate from native cleanup assertions.

The final JSON report records the artifact hash, archived unit XML, full Forge and
OptiFine manifests, fixture fingerprints, source hashes, rollback checks and
intermediate attempts. New unit cases cover cooldown pause, source float edges,
repayment/target loss, PRE/POST arming, 100 ms lease boundaries, mode retirement,
range edges, Eagle resampling/release and Timer phase preservation. The new
transformed-client probe checks real held-Sneak input, Derp smoothing/toggles,
orbit jump yaw, exact serialized NCP critical offsets, Lag Range lease/sequence
handling, mixed liquid footprints and Phase thresholds/packet emission.

The complete 102-definition ledger is retained: 77 native mappings, 22 excluded
presentation definitions and three deferred features (Insults, Skin Changer,
Keybind Info). The [final coverage ledger](YURI_131_FINAL_COVERAGE_2026-10-18.json)
records source fingerprints, native mappings and which modules received this
pass's focused comparisons versus unchanged prior-reviewed implementations.
A mapping, fingerprint or module-startup check is not proof of every mode's
behavior. Existing regression probes continue to cover rotations, placement,
projectiles, inventory, packet buffering, world retirement and UI/config behavior.

## Remaining differences

- Submitted-rotation identity, initial walking boundary, fresh action validation,
  shared ownership, analog movement correction, bounded FIFO queues, dependent
  packet ordering and world/connection retirement remain native requirements.
- Native target/friend policy, safe potion/cursor checks, restricted GUI movement,
  setting validation/increments and zero-speed rejection remain. For example,
  Target Strafe retains native Aura-target selection rather than the donor global
  manager's first target-list entry. No Slow respects disabled item categories.
- Native Blink preserves queued packets rather than reproducing the donor
  manager's repeated-enable queue clearing. It also retains safe capacity/fault
  behavior. Blink/Lag Range position-render options remain absent.
- Independent randomness, monotonic time, owned cleanup/reconciliation and local
  activation state can differ from a global mutable donor manager, especially
  with several competing modules or toggles within one tick.
- The three deferred definitions remain unimplemented. Presentation exclusions
  follow the request to retain Perihelion's UI; they are not claimed as ports.

Exact two-client runtime equivalence, an exhaustive mode/setting/interleaving
matrix and live anti-cheat acceptance are not established. This report makes no
claim that every remaining difference has been eliminated.

One smoke launch overlapped JAR replacement during a build and failed fixture
compilation against the partial archive. It was discarded and rerun after the
completed build. Earlier successful intermediate artifacts are also retained in
the evidence ledger; only the final identical-artifact pair is current evidence.

Later concurrent HUD/GUI edits appeared after the tested build. Those files are
preserved and excluded from the Yuri rollback, and their source differences are
recorded in the JSON. They are not part of this tested artifact or its unit/runtime
claims. The identical tested JAR is frozen under the dated filename above so a
separate UI rebuild cannot overwrite this evidence artifact.

The later UI work also edits smoke fixtures. Their tested hashes and compiled probe
JAR hashes are retained; current fixture differences are recorded separately.
`GuiHookProbe.java` overlaps an earlier Yuri update, so rollback deliberately
refuses that newer live edit until it is preserved/merged. Isolated restoration
of the tested checkpoint and later-edit refusal are verified; no live rollback
was applied. Non-overlapping UI files are excluded from restoration.

# Aura action order and sprint-jump correction — 2026-10-12

The user reported repeated Grim `Post … animation v1.8` and `Simulation` messages while using Aura, then confirmed default settings. Inspection found two native differences from Yuri that affect those defaults: actions ran after movement, and Silent movement fix left sprint-jump acceleration on the camera yaw.

## Diagnosis and source comparison

Grim's [Post check](https://raw.githubusercontent.com/GrimAnticheat/Grim/f5bbe9cf55a631f40b3d28d722d0f82b514e3403/common/src/main/java/ac/grim/grimac/checks/impl/misc/Post.java) tracks action packets that arrive after a walking/tick packet and before a valid transaction response. The animation label identifies a swing packet in that window. Perihelion invoked Aura's click/attack logic from `AFTER_MOTION`, creating that packet order. This is a confirmed code-level cause matching the reported Post label.

Grim's [Simulation check](https://raw.githubusercontent.com/GrimAnticheat/Grim/f5bbe9cf55a631f40b3d28d722d0f82b514e3403/common/src/main/java/ac/grim/grimac/checks/impl/prediction/OffsetHandler.java) reports a difference between predicted movement and observed movement. Perihelion's old attack phase applied successful sprint-hit slowdown after the client's movement had already happened. It also corrected sprint-jump yaw only in Strict mode, despite default Silent mode using server rotations for walking. Those are confirmed movement discrepancies. They are plausible contributors to the reported Simulation offsets; the screenshot does not isolate which occurred on that server, or identify its exact Grim version.

Read-only references:

- Local Yuri v1.3.0, `6f5603e0c0eed253741bacdcd4135830c916602e`: `AuraModule.onPreUpdate` selects/rotates/blocks/attacks before player movement; `RotationManager.onJump` uses the requested yaw for both Normal and Traditional movement fix.
- Current Yuri main, verified remotely at `542c4f77d9788af4a75e2471a1a1d4b7f2d10b11`: [Aura source](https://github.com/unleg1t/Yuri/blob/542c4f77d9788af4a75e2471a1a1d4b7f2d10b11/src/main/java/ddlc/yuri/modules/impl/combat/AuraModule.java) retains pre-update attacks. The local release donor remained untouched.
- Grim source snapshot `f5bbe9cf55a631f40b3d28d722d0f82b514e3403` supplies the flag definitions above; it is not claimed to be the test server's installed revision.

| Behavior | Previous Perihelion | Updated Perihelion |
| --- | --- | --- |
| Click, swing and attack phase | After walking packet | Before input/movement and the next walking packet |
| Rotation-dependent action | Current walking aim submitted in the same pass | Identical previously submitted aim, matched to owner, position, world and input window |
| Sprint-hit slowdown | Applied after movement | Native hit slowdown runs before subsequent movement |
| Silent sprint-jump boost | Camera yaw | Winning server-rotation yaw, as in Yuri Normal movement fix |
| Silent walking direction | Compensated to preserve requested direction | Preserved |
| Fake Auto Block | Visual pose | Preserved, with no use/release packets |

The PRE action temporarily uses the previously submitted rotation for ray tracing and native hit direction, then restores the next desired rotation and camera. FOV validation retains the real camera yaw during that temporary scope. Initial unsent rotations, repeated input windows, changed positions, competing owners and retired activations cannot authorize an attack. Cleanup during an attack does not restore an old claim. Existing target, item-routine, ray, delay and ownership checks remain in the native path.

Default settings remain unchanged: simulated mouse clicks, Fake Auto Block, Silent move fix and Keep Sprint off. CPS, reach, range, target selection and custom Keep Sprint behavior were not increased. Normal/ML rotations use the owned PRE guard; explicit None keeps the native manual-view click path. Real block modes also perform through the PRE path; their existing conditions and ownership rules remain, without claiming every mode is compatible with Grim.

## Verification

The offline `check assemble` build passes 267 tests (core 156, Forge 111). Native `AuraPreProbe` checks actual transformed Minecraft clicks/controller attacks and the outbound pipeline with a remote-player fixture, not a fabricated attack event. Its 22 assertions cover default mouse and direct hits, one swing/attack per due click, native 0.6 horizontal slowdown before movement, vanilla sprint reset, visual-only Fake blocking, an empty post-walking action window, initial unsent rotation rejection, repeated-input and changed-position rejection, disable during callback, and reactivation ownership.

Final Forge `run-smoke-forge-rz0r_emb` and OptiFine M5 `run-smoke-optifine-dzvdm74j` full runs both passed and exited cleanly with identical fixture sources and JAR SHA-256 `9d0d010c4530472a56fca47a8c8ebcfb930f4ad0271b0f2623220382d5470033`. Each passed Aura PRE 22, rotation/order 305, combat flow 49, GUI 111, theme/font 207, chat HUD 83 and production HUD 55 checks. All donor checkouts remained clean.

The rotation fixture verifies fractional walking movement and real sprint-jump acceleration for both Silent and Strict modes; jump boost follows server yaw in both. Existing Aura, Hypixel blocking, Velocity PRE reduction, cancellation/competing-owner, terrain, GUI, chat HUD, fonts, world unload/rejoin and cleanup fixtures remain included. Exact final packaged artifact and fixture hashes, run outcomes and native-runtime limitations are recorded in [AURA_ORDER_VERIFICATION_2026-10-12.json](AURA_ORDER_VERIFICATION_2026-10-12.json).

Fixture setup failures were repaired before interpreting results: two helper calls referenced private test methods; the new remote player had initially been registered before setting its position, so the targeting list saw it while chunk-based ray tracing could not; a fresh-click fixture also owns/restores the vanilla click cooldown. No production gameplay change was made in response to those setup failures. One run passed all assertions but exited with a native macOS `libobjcMsgSend` shutdown crash; it is retained as a failed process outcome and followed by unchanged-artifact verification.

Local packaged Forge/OptiFine integrated-world and loopback assertions do not prove the absence of flags on the user's authenticated Grim server. The two concrete client discrepancies are corrected; server validation still requires retesting this JAR under the reported conditions.

## Recovery

Before edits, 485 source/config/documentation files were archived and content/hash verified at `.backups/before-aura-order-20261012-151237/source.tar.gz`, SHA-256 `fbcf097cacb0acf1bd93d30516365b937970ae7bc9e398ef23917b37fc7cabc4`. The earlier built JAR was absent from the workspace at snapshot time; source recovery includes the complete UI/font and chat HUD work from the preceding task.

`python3 scripts/restore-aura-order.py` defaults to a dry run. `--apply` backs up current affected files, restores originals and removes additions. It refuses archive or manifest mismatches, unsafe paths and any affected file changed since the final snapshot. Generated artifacts remain intact; rebuild after restoring. Recovery checks run only in an isolated copy and preserve the working project.

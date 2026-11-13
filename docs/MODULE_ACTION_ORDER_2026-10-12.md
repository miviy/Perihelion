# Cross-module action order and movement — 2026-10-12

The user requested that the Aura packet-order and sprint-jump corrections also
cover other affected modules, specifically Fucker / Bed Breaker. Perihelion's
registered equivalent is **Breaker**, with Legit, Vanilla and Hypixel modes;
Hypixel also targets bed-covering blocks. This audit covers every native module
interaction emitter and every `PlayerControlEvent` phase use.

## Native phase and ownership

`BEFORE_INPUT` collects claims. The Minecraft bridge applies them and dispatches
the new `BEFORE_ACTIONS` phase, applies any resulting cleanup, then lets vanilla
consume input and move. `BEFORE_MOTION` prepares walking packets; `AFTER_MOTION`
is reserved for bookkeeping and complete ordered-buffer flushes. Preparing all
claims before actions prevents registration order from letting a lower-priority
tool act before a competing owner requests its rotation or slot.

Rotation-dependent PRE actions use the immutable aim actually submitted in the
preceding walking pass. `withPreviousAim` temporarily claims that value without
opening a camera scope, so a native hotbar/placement/rotation scope can safely
nest inside it. It rejects an initial unsent aim, changed player position,
repeated input window, invalid session, different request owner, or lost winning
claim. Its `finally` restores the next desired value only when the callback has
left that same claim untouched; clearing or changing a value is never undone.
Existing native previous-rotation checks also reject cancelled walking passes.

Shared movement correction preserves the requested analog walking direction
while using the winning silent yaw for movement and sprint-jump acceleration.
Existing Move Fix settings still govern modules that expose that setting.

## Module ledger

| Module | Change or audit result |
| --- | --- |
| Breaker: Legit / Vanilla / Hypixel | PRE digging, swing and tool synchronization; revalidate the actual prior ray, reachable target and hit face. START/STOP/ABORT use the face that was hit. Menus, death, Freecam, Scaffold and Aura targeting pause digging. Shared walking/jump correction honors Move Fix. |
| Bed Defender | PRE wool placement using the actual prior angle and strict support-face ray; retains shell plan, sneak, delay and tolerance settings. Shared walking/jump correction honors Move Fix. |
| Block In | PRE enclosure placement with previous submitted aim and strict ray; shared walking/jump correction. |
| Clutch | PRE strict catch placement; keeps the initial pending aim until it can be submitted, and clears claims after a successful catch. Existing knockback/window/environment guards remain. |
| Aura | Uses the new phase after all claims are collected. Keeps the tested PRE attack/slowdown, FOV camera handling, settings and previous-rotation wrapper. |
| Auto Clicker | PRE native click and block-hit use/release. Physical input, rate and attack/use ownership guards remain. |
| Anti Fireball | Prepares before input and attacks in PRE using the previous aimed entity intersection; revalidates entity identity, life and distance. Shared walking/jump correction. |
| Auto Projectile | PRE native throw under previous rotation and hotbar scopes; shared movement correction during aiming. Pending stack/target/menu invalidation also runs before actions and before walking, independently of whether prior aim is available. |
| AutoPot | PRE potion packet under previous submitted aim; retains useful-potion, slot, delay and aim-timeout checks. |
| Auto Extinguish | PRE native water use on the existing sixth aiming tick, using previous native direction. Cleanup remains bounded. |
| No Fall MLG | PRE water/web use with previous submitted aim and native mouse ray. Existing occupied-web/water/item/menu guards remain. |
| No Fall Vanilla | Defers the extra ground packet until walking preparation, after PRE interactions, so it does not open a post-walking window ahead of another tool's item use. |
| Fireball Long Jump | PRE fireball use on the existing second tick with prior aim, hotbar ownership and stack revalidation. |
| Auto Eat | PRE native item start after slot/use claims have been applied. Existing continuation, owned-item cleanup and inventory refill behavior remains. |
| Chest Aura | Prepares before input; PRE chest activation using the previous ray and actual face/vector. Uses native block reach for the ray; retains explicit Through Walls, acknowledgment/retry, delay and Move Fix behavior. |
| Minigame Aim | PRE Halloween/Zombie uses under prior aim/tolerance; native Zombie click scopes to previous direction. Shared walking/jump correction. |
| No Slow NCP | Releases item use in PRE at lower action priority, after automatic tools may start using an item. Existing mode behavior remains. |
| Target Strafe | Keeps its orbit movement yaw; a sprint jump uses the winning server rotation, or native camera yaw when no owner exists. |
| Scaffold | Already places in PRE under previous submitted rotation; retained. Terrain/native placement regressions still run. |
| Velocity / Jump Reset | Existing PRE reduction/input behavior retained and regression checked. |
| Bow/Aim Assist, Fast Place/Break, Auto Tool, inventory modules | No post-walking interaction emitter to migrate. Rotation-only behavior, native delay/progress changes, and inventory/controller operations remain in their existing pre-walking paths. |
| Blink / FakeLag / Backtrack buffers | Complete ordered replay remains intact. Blink's pulse flush is the only remaining module `AFTER_MOTION` listener; it does not create a new interaction out of order. |
| Criticals, Regen/Fast Use, Phase and other explicit movement spoof modes | Deliberate movement/physics modifications remain. PRE interaction order does not make their packet sequences vanilla-compatible or prove a bypass. |

## Verification

The offline `check assemble` build passes **273 tests** (core 156, Forge 117),
architecture checks and packaged-JAR validation. Native `ModuleActionProbe`
adds **74 assertions across 11 cases**: all three Breaker modes, Block In, Bed
Defender, Chest Aura, AutoPot, Auto Eat, Fireball Long Jump, actual water
extinguishing, and Auto Eat overlapping NCP No Slow plus Vanilla No Fall.
Each real step collects the native outbound pipeline and rejects interaction,
animation, digging and held-slot packets after its first walking packet.

The fixture also verifies immutable previous-aim ownership, nested native
direction scope, exact next-value/camera restoration, mutation/clear protection,
repeated input rejection, actual fractional `moveFlying` direction and real
sprint-jump impulses for Breaker, Block In, Bed Defender and Chest Aura.
Existing native fixtures cover Aura PRE 22, rotations/order 305, combat flow 49,
projectile stale-stack cleanup/throwing, Clutch, MLG, Anti Fireball, 72 Scaffold
terrain cases, GUI/font/chat HUD and unload/rejoin cleanup.

Final Forge `run-smoke-forge-jzzvracy` and OptiFine M5
`run-smoke-optifine-t0eaaf8k` full suites passed and exited with code 0, identical
fixture sources and JAR SHA-256
`9a7bd533f671ad641ebb76b08542f7d132f2eb1d9f2983cc51fb0f75e8700c2e`.
Both report cleared world/player/manager references, zero subscriptions/queued
tasks and `stdoutError=false`. Legacy Paul Lamb/OpenAL buffer warnings remain;
there are no unexpected NPE, missing member, verifier, Mixin, OOM or native
crash signatures in these final runs.

Exact final Forge/OptiFine runs, hashes, warnings, failures and recovery checks
are in [MODULE_ACTION_ORDER_VERIFICATION_2026-10-12.json](MODULE_ACTION_ORDER_VERIFICATION_2026-10-12.json).
These are transformed Minecraft tests in an isolated local world and controlled
network fixtures. They are **not an authenticated Grim-server run**, and do not
establish that every module or non-default setting bypasses every server.

An initial native run caught stale Auto Projectile claims after its stack changed
between preparation and walking; validation now clears them without emitting a
POST action. A later suite failure was an observer-lifetime bug in the smoke
harness: the already-completed HUD overlay probe was still registered during
synthetic gameplay rendering and quarantined itself, changing the listener
baseline. Closing it before those draws preserves all overlay and cleanup
assertions and retains the separately added UI-only scenario.

## Recovery

The initial source/config/docs archive contains 489 files and is content/hash
verified at `.backups/before-action-order-20261012-181643/source.tar.gz`, SHA-256
`a5d4be688bdbd268bb1312ec49093e87ff84e68995839d9763c373efe5718f64`.
The preceding built JAR is preserved as `previous.jar` in that directory.

The action-order rollback covers only this task's affected files. Other UI/HUD,
font and test changes appearing after the initial backup are preserved. The
shared smoke harness and donor ledger have separately verified baseline copies
from immediately before this task edited them. `scripts/restore-action-order.py`
defaults to a dry run, verifies archive/member/current-file hashes, and refuses
later edits. `--apply` first backs up affected files, restores the task baseline
and removes additions. Generated JARs/caches/worlds remain intact; rebuild after
restoring source.

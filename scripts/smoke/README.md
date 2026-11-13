# Client smoke runs

From the repository root:

```sh
scripts/launch-smoke --scenario full
scripts/launch-smoke --scenario full --optifine '/absolute/path/to/OptiFine_1.8.9_HD_U_M5.jar'
scripts/launch-smoke --development --scenario full
```

The launcher compiles this separate Forge probe and the packet/control fixtures in `forge/src/smoke/java` against the exact packaged Perihelion JAR. It places those test classes in a separate probe JAR inside a fresh `forge/run-smoke-*` directory, then launches cached Minecraft 1.8.9 and Forge 11.15.1.2318 with Java 8. No fixture becomes a production module or part of the distributable.

Each run has its own mods, world saves, configuration, options, log, native copies, and output files. The launcher reads cached Prism version metadata, libraries, and assets only. It does not read launcher accounts, copy existing settings/mod folders, modify an existing world, or connect to an external server. The identity is always the fixed offline `PerihelionTest` account. Optional OptiFine must be explicitly supplied. Repeat `--extra-mod /path/to/mod.jar` for
selected compatibility mods; these are copied into the isolated directory and their hashes
are recorded. Existing mod folders, configs and account data are not copied.

The default packaged artifact is `forge/build/libs/Perihelion-0.1.0.jar`. Override it with `--jar`. Supply `--prism-root`, `--java-home`, and `--natives` when the local cache/runtime differs. Java 8 must include `javac`; natives must match that JVM's architecture. `PERIHELION_PYTHON` overrides the wrapper's Python interpreter, and `PERIHELION_JAVA8_HOME` provides its Java 8 default. `--prepare-only` assembles a run and records its complete command and environment without launching it. `--stay-open` leaves the window open after the scenario marker until manually closed or the bounded timeout expires; a timeout still fails the run.

`--development` runs the actual Loom `:forge:runClient` task with the same isolated fixtures and scenarios. A disposable Gradle init script sets the run directory, offline identity, Java 8 executable, and architecture-matched native libraries. It does not change the checked-in build or install the production mod JAR into the development mods folder. Gradle needs Java 17, selected with `--gradle-java-home`, `PERIHELION_GRADLE_JAVA_HOME`, the project's private `.tools/java17/Contents/Home`, or `JAVA_HOME`. Development manifests record production source hashes; the packaged artifact hash identifies only the JAR used to compile the fixtures. Development success therefore validates compiled source classes, while packaged success validates the remapped distributable.

## Scenarios

The `world` and `full` scenarios also exercise `HudHookProbe` through actual Forge
overlay callbacks. The fixture compares per-element Forge events with core HUD
delivery, checks partial ticks/resolution and the live GUI projection, changes GUI
scale between 1 and 2, hides the GUI with chat open, cancels `Pre(ALL)`, and checks
that HUD drawing resumes behind chat. A deliberately throwing owned listener uses
`RenderState`; the next listener verifies restored GL state before drawing a test
label visible in the world captures. Options and chat are restored after the test.
The fixture is not a production module and is excluded from the distributable.

The cosmos presentation also checks visible star cores in the open sky,
repeatable rendering at a fixed time, and wordmark texture disposal on screen changes.
`menu-button-hover.png` captures the settled main-button highlight.
The logo check measures actual rendered ink against the button group center and
verifies containment, a clear vertical gap, and rendering-state restoration.
`cosmos-meteor.png`, `cosmos-infall.png` and `cosmos-infall-later.png` capture the
packaged shader at 0.55, 6.25 and 8.25 seconds without advancing the scene clock.

Every title phase also runs `RenderHookProbe`: real GL state and Minecraft cache
restoration after nested scopes, deliberate exceptions and leaked matrix pushes,
plus native-font metrics and drawing. `render-helpers.png` shows plain, shadowed,
formatted, Unicode and translucent text; `title-screen.png` remains the unmodified
menu capture. `MainMenuHookProbe` checks the custom title's button bounds at four
resolutions, GL restoration, keyboard navigation, hover, and round trips through
Singleplayer, Multiplayer, Accounts, Mods, Options and Language. It also compares
rendered frames at two animation times for both shader and fallback backgrounds
and checks shader disposal/recreation when leaving and returning. These fixtures
do not ship in the production JAR. They do not test
shader packs, arbitrary resource-pack fonts or every GPU/GUI-scale combination.

`MainMenuHookProbe` also checks eased cursor convergence, direction reversal,
unchanged control bounds, and a visible scene change from pointer input at fixed
animation time. `MenuFamilyHookProbe` confines save metadata and loopback server
fixtures to the isolated run. It exercises populated/scrolled world lists, rename
commit, create/delete cancellation, server add/edit, direct-connect text selection
and cancellation, preserved button references, narrow layouts, and actual FOV/audio
slider drag callbacks. Captures named `menu-worlds*`, `menu-servers.png`,
`menu-server-edit.png`, `menu-options.png` and `menu-sounds.png` show those native
screens. World scenarios also verify that menu presentation is inactive during play.

The `world` and `full` scenarios also run `ControlHookProbe` in the first world.
It checks timer priority/restoration, virtual-key fallback without physical-state
mutation, actual sprint state, visible/spoofed slots and guarded actions, real
walking rotation caches and camera behavior, player phases, fault/disable/world
cleanup, and shutdown inside a spoofed action. Its `inputFocused` marker reports
whether the window was focused: an unfocused run checks the key fallback path,
while a focused run also checks a forced held key. Fixtures use temporary runtime
and module instances and restore the production hook target afterward.

| Scenario | Assertions |
| --- | --- |
| `title` | Twenty stable main-menu ticks; correct Forge-loaded mod version; runtime RUNNING; ten registered, default-disabled production modules (nine gameplay plus ClickGUI); exact client tick forwarding; local core status/modules commands; framebuffer capture. |
| `world` | Title checks, isolated flat creative world creation, forty stable in-world ticks, local Forge command consumption including an invalid subcommand, unload, twenty stable menu ticks, rejoin the same isolated world, and forty further stable in-world ticks. Checks world readiness, connection kind, generation advancement, world events, and listener counts at each boundary. Both world visits complete an asynchronous configuration save/reload, polled from ordinary ticks without blocking on disk I/O. After unload, checks that the Forge adapter's cached world, player, and network manager references are null. |
| `packets` | Title checks and 46 transformed `NetworkHookProbe` assertions. Embedded channels cover overloads, queues, cancellation, listener ordering, raw callback recursion, ordinary/fatal errors, PLAY filtering, custom payload ownership, and callback threads. An ephemeral TCP listener on `127.0.0.1` additionally verifies Minecraft's actual packet framing and serialization, remote-channel filtering, packet delivery, cancellation, and Netty completion threads. |
| `full` | Title, transformed packet fixture, and world scenario. This is the default. |

All scenarios ask Minecraft to shut down normally. A separate JVM shutdown assertion checks STOPPED state, zero subscriptions, a closed/empty task queue, persisted configuration, cleared adapter world/player/network-manager caches, and a cleared installed hook target. These reference assertions check the named fields directly; they do not prove garbage collection or the absence of all retained objects. The wrapper requires both the scenario PASS marker and the shutdown STOPPED marker, no FAIL marker, exit code zero, and completion within the default 240-second timeout. `--timeout` changes that bound.

The new run directory contains `launch-manifest.json` with the artifact/fixture SHA256 values and exact command/environment, `launch.log`, `result.json`, and framebuffer PNGs for the title screen and visited worlds. These records distinguish the artifact and scenario actually exercised. The TCP fixture enters PLAY directly with test handlers; it is not a logged-in multiplayer world. A successful `full` run does not establish Hypixel authentication, external multiplayer server compatibility, long-duration stability, arbitrary GUI/HUD extensions, or untested gameplay behavior.

`PROFILE` markers record short tick/frame intervals, live process heap snapshots, client-thread allocation totals when supported, timed core dispatches, and remaining subscriptions/tasks after probe cleanup. Allocation and interval samples include Minecraft, world loading, and the probe. They are diagnostic evidence, not a controlled baseline comparison, a frame-rate guarantee, or a retained-memory leak measurement.

On macOS, a restricted execution sandbox can abort Java inside `_RegisterApplication` before a window exists. Run the script with normal desktop access to test the client. Do not change native versions or production code in response to that sandbox-only failure.

The world fixture calls Minecraft `loadWorld(null)` to initiate integrated-server teardown. Sending a separate disconnect immediately beforehand can race Forge's automatic server shutdown, leaving `initiateShutdown` waiting for a task on a stopped server. A captured thread dump identified that fixture race; the probe uses one teardown path.


The world scenario also runs `ModuleHookProbe` against the production module definitions.
It compares Fullbright's actual lightmap to gamma 100, exercises FOV/hurt/bob/fog
hooks, checks potion and option preservation, item cooldown filters, Sprint/WTap
priority, timer restoration, world reactivation, and deliberate callback-fault cleanup
for every module. Fixture changes to player/options/inventory are restored. These
checks do not measure multiplayer knockback or prove external-server compatibility.


The world scenario also runs `GuiHookProbe` with the actual packaged frontend. The
production GUI opens through the local command; a separate temporary runtime exercises
the default input binding, all setting widgets, conditional visibility, capture, panel
scrolling/collapse/dragging, overlap order, search, layout reset, fault display and config
restart. It renders every core category, all four cached fonts and three GUI scales and
compares live GL state around drawing. Off-screen recovery uses Minecraft's resize
callback. `clickgui.png` captures the production screen; the HUD probe label above the
panels is test-only. Logical-coordinate input calls reach the same methods used by
Minecraft callbacks, but do not prove physical keyboard/mouse interaction on every OS.

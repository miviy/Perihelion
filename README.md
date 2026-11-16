# Perihelion

Public GitHub repository: [miviy/Perihelion](https://github.com/miviy/Perihelion).
See [GitHub development](docs/GITHUB.md) for pushing updates, branches, pull
requests, and build downloads.

A Minecraft **1.8.9** client foundation, loaded as a client-only Forge mod. The core is plain Java 8 and can be tested without launching Minecraft. Seventy-eight native gameplay modules and a configurable HUD are included, covering Yuri's 65 non-presentation module definitions plus independent visual controls. See the [port coverage and differences](docs/YURI_PORT.md), including Aura, Blink, Scaffold, movement, inventory and gameplay visuals. All start disabled and use the shared lifecycle, settings, configuration and controls. See [module usage](docs/MODULES.md). The default [ClickGUI](docs/CLICKGUI.md) opens with **Right Shift** in a world or `.gui`. Its Client panel controls appearance; a different frontend can replace it without changing gameplay modules.

## Build

Install **JDK 17** for Gradle and **JDK 8** for compilation, tests and Minecraft. Run from this directory with `JAVA_HOME` pointing to JDK 17:

```sh
./gradlew --no-daemon clean check
```

Gradle normally discovers JDK 8 automatically. If needed:

```sh
./gradlew --no-daemon -Dorg.gradle.java.installations.paths=/absolute/path/to/jdk8 check
```

On the current development machine, the private Gradle runtime and cache can be selected with:

```sh
JAVA_HOME="$PWD/.tools/java17/Contents/Home" GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew --no-daemon check
```

`check` runs Java 8 unit tests, architecture and wrapper checks, creates the remapped distribution, and inspects its classes, metadata and Mixin reference map. Output:

```text
forge/build/libs/Perihelion-0.1.0.jar
```

Install that JAR into an isolated Minecraft 1.8.9 profile using Forge **11.15.1.2318**, the [official recommended release](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.8.9.html). Use the plain `Perihelion-…jar`; the `forge-…-dev*.jar` files are intermediate build artifacts. The distribution includes the core and the required legacy Mixin runtime. The Essential client is not required.

All selected versions are pinned. `gradle/verification-metadata.xml` records dependency checksums; the wrapper also checks the Gradle distribution. See [build notes](docs/BUILD.md) for legacy dependency and native-library details.

## Commands

Dot commands and their Tab completion run locally, including unknown commands. The legacy `/perihelion` alias remains available through Forge's client-command handler.

| Command | Purpose |
| --- | --- |
| `.help` | List commands |
| `.status` | Lifecycle, connection, listeners, pending work and config health |
| `.status timing on` / `off` | Enable or disable dispatch timing |
| `.gui` | Open the ClickGUI |
| `.accounts` | Open the themed account manager |
| `.hud edit`, `on`, `off`, `reset` | Arrange, enable, disable or reset HUD elements |
| `.modules` | List gameplay and client modules and their states |
| `.toggle <module>` | Change the requested enabled state |
| `.set <module> <setting> <value>` | Validate and update a setting; quote values with spaces |
| `.bind <module> <keyboard:code\|mouse:code\|none>` | Assign a toggle binding |
| `.config list`, `save <name>`, `load <name>`, `update <name>`, `delete <name>` | Manage named presets |
| `.config save` / `.config reload` | Save or reload the current setup |

The GUI has a **HUD** editor for movable overlays; see [HUD usage](docs/HUD.md).

**Accounts** is available from the main menu, Multiplayer and Singleplayer, or
`.accounts`. It shares the client's appearance settings and keeps credentials
separate from shared presets. See [account features and usage](docs/ACCOUNTS_2026-10-18.md).

The Configs list includes **default** (the unchanged fresh-install setup) and **hypxile**
(the supplied Yuri setup matched to existing GUI controls). Both are normal editable,
deletable presets. They install once, keep existing files, and never select themselves.
See [mapping and unavailable settings](docs/HYPXILE_CONFIG.md).

The GUI has a **Configs** tab for creating, loading, updating and sharing named JSON presets. See [config usage](docs/CONFIGS.md). Presets live in `config/perihelion/configs/`. Your current working configuration remains at `config/perihelion/client.json` inside the game directory. Saves are debounced and retain a valid `.bak`; corrupt files are preserved separately. Unsupported newer schemas are read-only. Reload reads the file after any already-running save and discards unsaved debounced changes; changes made during a pending reload cause that reload to be rejected.

## Structure

```text
core/src/main/java/perihelion/core/
  lifecycle/       composition root and config application
  event/           typed dispatch and subscription ownership
  module/          metadata, activation scopes and fault isolation
  control/         activation-owned shared state and priority arbitration
  setting/         typed values and centralized validation
  config/          bounded JSON parsing and durable storage
  command/         local command parsing, registry and completion
  input/           device/code bindings and press transitions
  concurrent/      bounded client-thread task queue
  context/         connection state and address hints
  diagnostics/     logging boundary and fatal-error policy
forge/src/main/java/perihelion/
  bootstrap/forge/ Forge entry point, command and event adapters
  integration/minecraft189/
                   game context, raw packet events and narrow Mixins
core/src/test/     Java 8 regression tests; no game dependency
core/src/benchmark/  optional event dispatch measurement
forge/src/test/    adapter state restoration tests
forge/src/smoke/   test-only transformed packet and control checks
scripts/smoke/    isolated client acceptance probe
```

## Development and verification

- `./gradlew :core:test` — core regression suite.
- `./gradlew :core:benchmark` — standalone dispatch measurement, excluded from normal checks and distribution.
- `./gradlew :forge:runClient` — Loom development client; Java 8 and matching LWJGL 2 natives required.
- `scripts/launch-smoke --scenario full` — isolated packaged client checks using a local Prism cache.
- `scripts/launch-smoke --scenario full --optifine /absolute/path/to/OptiFine_1.8.9_HD_U_M5.jar` — repeat with the compatibility target.

See [smoke-run instructions](scripts/smoke/README.md), [verification evidence and limits](docs/VERIFICATION.md), [architecture](docs/ARCHITECTURE.md), and [extension guide](docs/EXTENDING.md). GitHub Actions runs the build and core checks; graphical smoke tests remain explicit desktop runs.

Future modules should use [shared controls](docs/SHARED_CONTROLS.md) for rotations,
held inputs, hotbar selection/spoofing, timer speed and sprint state. These requests
belong to the module activation and retire automatically on disable, fault or
world change.

Future visual modules should use the [rendering helpers](docs/RENDERING.md) for
temporary GL state and native-font text. Reusable scopes restore the supported
state after exceptions. The panel GUI reuses these scopes, with reusable controls and
cached font atlases under `integration/minecraft189/gui/`.

The title screen uses a native Perihelion menu with an animated black hole,
simple dark buttons, and direct access to worlds, servers, accounts, mods, options
and language. Tab or arrow keys move focus; Enter opens the selected button. The
background uses a local GLSL shader with rotating accretion bands, a photon ring,
twinkling stars, occasional shooting stars, and dust trails spiraling inward,
with an animated fallback for clients without shader support. Screen closure releases
the shader and font textures; no assets are downloaded.

The black hole responds to the mouse with bounded, eased depth and lighting changes;
button hitboxes stay fixed. Shared menu buttons have flat charcoal faces, subtle
rounded corners and warm-gray hover edges. Title/native-menu button labels use
the logo's original geometric letterforms, with softer contrast and per-label
visual centering. Surfaces and text stay stationary.
The title has bespoke geometric lettering and an orbital P emblem with a slow-moving
glint and a warm-to-silver finish. The full logo is centered above the button group.
Quiet dark surfaces continue into native world/server lists, forms, options and account
screens. Native control instances, slider callbacks, editing, scrolling, list masks
and navigation remain intact. The presentation is scoped to the main-menu flow
and turns off in a world.
See [menu presentation and verification](docs/MAIN_MENU_POLISH_2026-10-19.md).

## License

Perihelion's original code is licensed under **GNU AGPL version 3 only**
(`AGPL-3.0-only`). See [LICENSE](LICENSE), [NOTICE](NOTICE), and
[third-party notices](THIRD_PARTY.md). Existing upstream notices remain in place.

Covered modified versions must meet the AGPL's corresponding-source requirements
when distributed or offered for remote network interaction. Use the license text
for the exact terms; this summary does not replace it.

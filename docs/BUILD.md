# Build and legacy platform notes

## Pinned baseline

| Component | Version |
| --- | --- |
| Minecraft | 1.8.9 |
| Forge | `1.8.9-11.15.1.2318-1.8.9` |
| MCP mappings | `stable_22` for 1.8.9 |
| Production/test runtime | Java 8 |
| Gradle runtime / wrapper | Java 17 / 8.4 |
| Essential Loom | 1.3.12 |
| Shadow | 8.1.1 |
| Pack200 adapter | 0.1.3 |
| Bundled Mixin runtime | `0.7.11-20180703.121122-1` |
| Mixin annotation processor | `0.8.5:processor` |
| Gson API | 2.2.4, supplied by Minecraft at runtime |
| JUnit | 4.13.2, tests only |
| Tested optional compatibility mod | OptiFine `1.8.9_HD_U_M5` |

Forge 2318 remains the [official recommended and latest 1.8.9 release](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.8.9.html). Its published universal-JAR SHA1 is `beda619c465af293e63952dd573c137c17c0a4cd`; the local dependency matched that value. Loom permits a supported Gradle/JDK build while targeting this old game. Do not replace it with current ForgeGradle instructions for a modern Minecraft version.

The legacy Mixin runtime keeps shaded ASM. Its newer annotation processor is used only during compilation. `verifyJar` rejects unshaded ASM or duplicated game libraries in the distribution, checks Java class versions, and requires the exact packet/shutdown SRG mappings. The Forge annotation version is generated from `modVersion` as a Java constant: the legacy development loader searches the class directory for metadata, while Gradle keeps processed resources in a separate directory. Relying only on `version.properties`/`mcmod.info` causes a false `1.0` version in development. The smoke probe verifies the version Forge actually loaded.

The dev JAR has named methods; the distributable uses Forge's SRG names. Test both paths when changing Mixins.

## Checksums

Gradle checks dependency bytes and metadata against `gradle/verification-metadata.xml`. The initial hashes were generated from the resolved pinned toolchain and reviewed against the selected dependency set. They detect subsequent changes; they are not a claim that every upstream binary received a source audit. See [Gradle's verification documentation](https://docs.gradle.org/current/userguide/dependency_verification.html).

Two narrowly scoped entries are handled separately:

- Gradle 8.4 fails to generate metadata for the timestamped legacy Mixin snapshot with a duplicate component key. That exact coordinate is exempted from Gradle's metadata mechanism, and `:forge:verifyMixinRuntime` verifies its JAR SHA256 **before compilation and bundling**: `da3d6e47b9c12b5a312d89b67bc27e2429d823c09cde8a90299e9fdcc4eefc20`. The downloaded bytes also matched Sponge's published SHA1 `7a670207bdb97db418118ad1e9bb42424ff3776d`. [Related Gradle 8.4 issue](https://github.com/gradle/gradle/issues/26803).
- Loom's exact locally generated merged Minecraft coordinate is trusted as a build output. Its input Forge artifacts and MCP maps retain checksum checks; generated output hashes must not be tied to one workstation.

The plugin classpath resolves four legacy Forge build-tool coordinates from
[NeoForged's official Maven repository](https://maven.neoforged.net/releases):
`installertools:1.2.0`, `DiffPatch:2.0.7` and `srgutils:0.4.1` under
`net.minecraftforge`, and `de.oceanlabs.mcp:mcinjector:3.8.0`. Their eight JAR and
metadata files were downloaded and matched the existing SHA-256 entries in
`gradle/verification-metadata.xml`. Exclusive repository filtering applies only
to these exact versions, avoiding unavailable Forge Maven responses during plugin
resolution. No versions, checksums or verification exceptions changed. The game,
Forge runtime and MCP mapping repositories remain unchanged.

The wrapper checks the official Gradle 8.4 distribution SHA256. `checkWrapper` separately checks the wrapper JAR against the [official wrapper checksum](https://services.gradle.org/distributions/gradle-8.4-wrapper.jar.sha256), `0336f591bc0ec9aa0c9988929b93ecc916b3c1d52aed202c7381db144aa0ef15`.

The verification metadata also covers Loom's LWJGL `2.9.4-nightly-20150209`
selection on a clean hosted runner, including Linux, macOS and Windows natives.
These hashes were checked against `libraries.minecraft.net`; published SHA1s and
the existing launcher metadata were compared where available.

When deliberately updating a dependency, inspect the version/source first, regenerate checksums with `./gradlew --write-verification-metadata sha256 check`, review the diff, and run normal `check` again without generation. Do not resolve a mismatch by disabling verification globally. Another operating system or an optional development task may require additional platform artifacts to be reviewed and added.

## Java and native libraries

Java 17 runs Gradle; a discoverable JDK 8 compiles source, executes tests and runs Minecraft. Auto-downloading JDKs is disabled. The workstation's private Java 17 and native setup is ignored by Git; a fresh machine must install its own runtimes.

LWJGL 2 is architecture-sensitive. On Apple Silicon, use Java 8 ARM64 with compatible LWJGL 2/JInput/OpenAL ARM64 natives, or a consistent x64/Rosetta environment. Mixing x86 native libraries with an ARM JVM cannot work. Do not add `-XstartOnFirstThread` to this LWJGL 2 configuration.

The checked-in `:forge:runClient` is the conventional Loom development task. For the current Apple Silicon setup, `scripts/launch-smoke --development --scenario full` copies matching natives and configures an isolated offline Loom run. It also supplies a disposable dev-launch-injector configuration because Loom's generated native path otherwise overrides the explicit ARM64 path. This is test-launch setup, not code distributed to users.

## Operational issues

- Dependabot skips Gradle 9+ wrapper proposals until the legacy Loom/Shadow build
  plugins can be migrated together. It also skips Gson version proposals: Minecraft
  supplies Gson 2.2.4, so changing the compile dependency alone neither updates nor
  verifies the game's runtime library. Any Gson migration must address runtime
  ownership and compatibility explicitly. Other Gradle dependencies and GitHub
  Actions updates remain enabled; repository security alerts are unchanged.
- A dependency resolution error is separate from a source error. Check the first missing coordinate and repository response; do not change pinned APIs blindly. Plugin repositories include Essential, Architectury, Fabric and Forge for the actual Loom dependency graph.
- Required Mixin transformations fail startup visibly. An absent refmap, wrong descriptor or wrong bytecode target must not be treated as a successful foundation launch.
- A client that runs only in development may still have remapping problems. Install/test the plain remapped `Perihelion-…jar` before claiming packaged compatibility.
- Smoke launch fixtures require a readable local Prism cache and do not download or distribute Minecraft or OptiFine.
- A clean local build was verified. The GitHub Actions workflow is provided, but no hosted CI run is claimed until the project is pushed and that workflow executes.

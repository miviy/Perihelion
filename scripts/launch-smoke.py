#!/usr/bin/env python3
"""Launch packaged or Loom development classes in isolated, offline Forge 1.8.9.

Requires an existing PrismLauncher cache. Reads version metadata, libraries, and
assets only; never reads accounts or copies existing game options. Extra mod JARs
must be selected explicitly for compatibility probes.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def die(message):
    raise RuntimeError(message)


def sha(path, algorithm="sha256"):
    digest = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def default_prism():
    if sys.platform == "darwin":
        return Path.home() / "Library/Application Support/PrismLauncher"
    if os.name == "nt":
        return Path(os.environ.get("APPDATA", str(Path.home()))) / "PrismLauncher"
    return Path(os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local/share"))) / "PrismLauncher"


def java_home(value):
    candidates = [value, os.environ.get("PERIHELION_JAVA8_HOME")]
    if sys.platform == "darwin":
        candidates.append("/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home")
    candidates.append(os.environ.get("JAVA_HOME"))
    suffix = ".exe" if os.name == "nt" else ""
    for candidate in candidates:
        if not candidate:
            continue
        home = Path(candidate).expanduser().resolve()
        executable = home / "bin" / ("java" + suffix)
        compiler = home / "bin" / ("javac" + suffix)
        if not executable.is_file() or not compiler.is_file():
            continue
        version = subprocess.run([str(executable), "-version"], capture_output=True, text=True)
        if version.returncode == 0 and 'version "1.8.' in version.stderr:
            return home, version.stderr.strip()
    die("Java 8 JDK required. Supply --java-home or PERIHELION_JAVA8_HOME.")


def metadata(prism, component, version):
    path = prism / "meta" / component / (version + ".json")
    if not path.is_file():
        die("Missing cached Prism metadata: " + str(path))
    return json.loads(path.read_text())


def development_command(args, run, java, natives):
    """Configure Loom's real runClient task without changing the checked-in build."""
    candidates = [args.gradle_java_home, os.environ.get("PERIHELION_GRADLE_JAVA_HOME"),
                  str(ROOT / ".tools/java17/Contents/Home"), os.environ.get("JAVA_HOME")]
    gradle_java = None
    for candidate in candidates:
        if not candidate:
            continue
        home = Path(candidate).expanduser().resolve()
        executable = home / "bin" / ("java.exe" if os.name == "nt" else "java")
        if executable.is_file():
            version = subprocess.run([str(executable), "-version"], capture_output=True, text=True)
            if version.returncode == 0 and 'version "17.' in version.stderr:
                gradle_java = home
                break
    if gradle_java is None:
        die("Development launch needs Java 17 for Gradle. Supply --gradle-java-home.")
    # Values travel as JSON data, never as interpolated Groovy/shell source.
    settings = {"runDir": run.name, "java": str(java / "bin" / ("java.exe" if os.name == "nt" else "java")),
                "natives": str(natives), "output": str(run), "scenario": args.scenario,
                "stayOpen": str(args.stay_open).lower(), "width": args.width, "height": args.height}
    (run / "development-settings.json").write_text(json.dumps(settings, indent=2) + "\n")
    init = run / "development.init.gradle"
    init.write_text('''import groovy.json.JsonSlurper
def smoke = new JsonSlurper().parse(new File(System.getProperty('perihelion.smoke.settings')))
gradle.afterProject { project ->
    if (project.path == ':forge') {
        def client = project.extensions.getByName('loom').runConfigs.getByName('client')
        client.runDir = smoke.runDir
        client.property 'java.library.path', smoke.natives
        client.property 'org.lwjgl.librarypath', smoke.natives
        client.property 'perihelion.smoke.output', smoke.output
        client.property 'perihelion.smoke.scenario', smoke.scenario
        client.property 'perihelion.smoke.stayOpen', smoke.stayOpen
        client.property 'log4j2.formatMsgNoLookups', 'true'
        client.property 'fabric.dli.config', new File(smoke.output, 'development-launch.cfg').absolutePath
        client.programArgs '--username', 'PerihelionTest', '--uuid', '00000000000000000000000000000001',
                '--userProperties', '{}', '--userType', 'legacy',
                '--width', smoke.width.toString(), '--height', smoke.height.toString()
        project.tasks.named('runClient').configure {
            executable = smoke.java
            minHeapSize = '256m'
            maxHeapSize = '1536m'
            doFirst {
                def generated = project.extensions.getByName('loom').files.devLauncherConfig
                def lines = generated.readLines('UTF-8').collect { line ->
                    if (line.startsWith('\\tjava.library.path=')) return '\\tjava.library.path=' + smoke.natives
                    if (line.startsWith('\\torg.lwjgl.librarypath=')) return '\\torg.lwjgl.librarypath=' + smoke.natives
                    if (line == '\\tundefined') return '\\t0'
                    return line
                }
                new File(smoke.output, 'development-launch.cfg').setText(lines.join('\\n') + '\\n', 'UTF-8')
            }
        }
    }
}
''')
    command = [str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")), "--no-daemon", "--console=plain",
               "-I", str(init), "-Dperihelion.smoke.settings=" + str(run / "development-settings.json"),
               ":forge:runClient"]
    environment = {"JAVA_HOME": str(gradle_java),
                   "GRADLE_USER_HOME": os.environ.get("GRADLE_USER_HOME", str(ROOT / ".gradle-home"))}
    return command, environment


def library_path(prism, definition):
    coordinate = definition["name"].split(":")
    group, artifact, version = coordinate[:3]
    classifier = "-" + coordinate[3] if len(coordinate) > 3 else ""
    path = prism / "libraries" / Path(group.replace(".", "/")) / artifact / version / (artifact + "-" + version + classifier + ".jar")
    if not path.is_file():
        die("Missing cached library: " + str(path))
    expected = definition.get("downloads", {}).get("artifact", {}).get("sha1")
    if expected and sha(path, "sha1") != expected:
        die("Cached library checksum mismatch: " + str(path))
    return path


def prepare(args):
    prism = args.prism_root.expanduser().resolve()
    built = args.jar.expanduser().resolve()
    if not built.is_file():
        die("Build the remapped client first; missing " + str(built))
    java, version = java_home(args.java_home)
    mc = metadata(prism, "net.minecraft", "1.8.9")
    forge = metadata(prism, "net.minecraftforge", "11.15.1.2318")
    lwjgl = metadata(prism, "org.lwjgl", "2.9.4-nightly-20150209")
    classpath = []
    for component in (forge, mc, lwjgl):
        for item in component.get("libraries", []):
            if "natives" in item:
                continue
            path = library_path(prism, item)
            if path not in classpath:
                classpath.append(path)
    classpath.append(library_path(prism, mc["mainJar"]))
    assets = prism / "assets"
    index = assets / "indexes" / (mc["assetIndex"]["id"] + ".json")
    if not index.is_file():
        die("Missing cached assets index: " + str(index))
    native_source = args.natives.expanduser().resolve() if args.natives else ROOT / ".tools/natives/macos-arm64"
    if not native_source.is_dir():
        die("Supply --natives with extracted LWJGL 2/JInput natives matching the Java 8 architecture.")
    variant = ("development-" if args.development else "") + ("optifine" if args.optifine else "forge")
    run = Path(tempfile.mkdtemp(prefix="run-smoke-" + variant + "-", dir=str(ROOT / "forge")))
    mods = run / "mods"
    mods.mkdir()
    natives = run / "natives"
    shutil.copytree(native_source, natives)
    if not args.development:
        shutil.copy2(built, mods / built.name)
    if args.optifine:
        optifine = args.optifine.expanduser().resolve()
        if not optifine.is_file():
            die("Missing explicit OptiFine JAR: " + str(optifine))
        shutil.copy2(optifine, mods / optifine.name)
    extra_mods = []
    for selected in args.extra_mod:
        source = selected.expanduser().resolve()
        if not source.is_file() or source.suffix.lower() != ".jar":
            die("Missing explicit extra mod JAR: " + str(source))
        if (mods / source.name).exists():
            die("Duplicate smoke mod filename: " + source.name)
        shutil.copy2(source, mods / source.name)
        extra_mods.append({"name": source.name, "sha256": sha(source)})
    classes = run / "probe-classes"
    classes.mkdir()
    cp = os.pathsep.join(map(str, classpath))
    # Compile fixtures against the exact artifact under test. They are never added
    # to the production source sets or distributable.
    sources = sorted((ROOT / "scripts/smoke").glob("*.java"))
    sources += sorted((ROOT / "forge/src/smoke/java").rglob("*.java"))
    subprocess.run([str(java / "bin/javac"), "-proc:none", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-cp", cp + os.pathsep + str(built),
                    "-d", str(classes)] + [str(source) for source in sources], check=True)
    with zipfile.ZipFile(mods / "perihelion-smoke-probe.jar", "w", zipfile.ZIP_DEFLATED) as jar:
        for compiled in classes.rglob("*.class"):
            jar.write(compiled, str(compiled.relative_to(classes)))
    # Only these test-specific options are written, in the new isolated directory.
    (run / "options.txt").write_text("enableVsync:false\nmaxFps:60\nsnooperEnabled:false\nfullscreen:false\n"
                                     "pauseOnLostFocus:false\nrenderDistance:2\n" +
                                     "guiScale:" + str(args.gui_scale) + "\n")
    cmd = [str(java / "bin/java"), "-Xms256m", "-Xmx1536m", "-Djava.library.path=" + str(natives),
           "-Dorg.lwjgl.librarypath=" + str(natives), "-Dlog4j2.formatMsgNoLookups=true",
           "-Dperihelion.smoke.scenario=" + args.scenario, "-Dperihelion.smoke.output=" + str(run),
           "-Dperihelion.smoke.stayOpen=" + str(args.stay_open).lower(), "-cp", cp,
           "net.minecraft.launchwrapper.Launch", "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker",
           "--username", "PerihelionTest", "--version", "1.8.9-forge-perihelion-smoke", "--gameDir", str(run),
           "--assetsDir", str(assets), "--assetIndex", mc["assetIndex"]["id"],
           "--uuid", "00000000000000000000000000000001", "--accessToken", "0",
           "--userProperties", "{}", "--userType", "legacy", "--width", str(args.width), "--height", str(args.height)]
    environment = {}
    if extra_mods:
        cmd.insert(1, "-Dmixin.dumpTargetOnFailure=true")
    if args.development:
        cmd, environment = development_command(args, run, java, natives)
    manifest = {"variant": variant, "mode": "development" if args.development else "packaged",
                "scenario": args.scenario, "java": version, "artifact": str(built),
                "window": {"width": args.width, "height": args.height, "guiScale": args.gui_scale},
                "artifactSha256": sha(built), "prismRoot": str(prism), "runDirectory": str(run),
                "fixtureSourceSha256": {str(source.relative_to(ROOT)): sha(source) for source in sources},
                "command": cmd, "environment": environment, "nativeSource": str(native_source),
                "createdAt": time.strftime("%Y-%m-%dT%H:%M:%S%z")}
    manifest["extraMods"] = extra_mods
    if args.development:
        manifest["artifactPurpose"] = "Fixture compilation only; game runs Loom development classes"
        development_sources = sorted((ROOT / "core/src/main").rglob("*")) + sorted((ROOT / "forge/src/main").rglob("*"))
        manifest["developmentSourceSha256"] = {str(source.relative_to(ROOT)): sha(source)
                                               for source in development_sources if source.is_file()}
    if args.optifine:
        manifest["optifineSha256"] = sha(args.optifine.expanduser().resolve())
    (run / "launch-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return run, cmd, manifest


def launch(args):
    run, cmd, manifest = prepare(args)
    print("Isolated run: " + str(run), flush=True)
    print(("Fixture compile artifact" if args.development else "Artifact") + " SHA256: " + manifest["artifactSha256"], flush=True)
    if args.prepare_only:
        print("Prepared only. launch-manifest.json contains the offline command and environment.", flush=True)
        return 0
    log = run / "launch.log"
    timed_out = False
    with log.open("wb") as stream:
        process = subprocess.Popen(cmd, cwd=str(ROOT if args.development else run), stdout=stream,
                                   stderr=subprocess.STDOUT, env=dict(os.environ, **manifest["environment"]),
                                   start_new_session=os.name != "nt")
        print("Launch PID: " + str(process.pid), flush=True)
        try:
            process.wait(timeout=args.timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            timed_out = True
            if os.name != "nt":
                os.killpg(process.pid, signal.SIGTERM)
            else:
                process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                if os.name != "nt":
                    os.killpg(process.pid, signal.SIGKILL)
                else:
                    process.kill()
                process.wait()
    contents = log.read_text(errors="replace")
    # The probe writes teardown assertions independently because another JVM
    # shutdown hook can close stdout before they reach the normal launch log.
    shutdown_report = run / "shutdown-report.txt"
    if shutdown_report.is_file():
        contents += "\n" + shutdown_report.read_text(errors="replace")
    markers = [line for line in contents.splitlines() if "[PERIHELION_SMOKE]" in line]
    for marker in markers:
        print(marker)
    passed = (not timed_out and process.returncode == 0 and "[PERIHELION_SMOKE] PASS scenario=" + args.scenario in contents
              and "[PERIHELION_SMOKE] STOPPED" in contents and "[PERIHELION_SMOKE] FAIL" not in contents)
    result = {"passed": passed, "timedOut": timed_out, "exitCode": process.returncode, "markers": markers,
              "screenshots": [str(path) for path in sorted(run.glob("*.png"))],
              "log": str(log), "shutdownReport": str(shutdown_report) if shutdown_report.is_file() else None,
              "artifactSha256": manifest["artifactSha256"], "mode": manifest["mode"]}
    (run / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print("Smoke " + ("PASSED" if passed else "FAILED") + ": " + str(log), flush=True)
    if not passed:
        print("\n".join(contents.splitlines()[-70:]))
    return 0 if passed else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prism-root", type=Path, default=Path(os.environ.get("PRISM_ROOT", str(default_prism()))))
    parser.add_argument("--jar", type=Path, default=ROOT / "forge/build/libs/Perihelion-0.1.0.jar")
    parser.add_argument("--java-home")
    parser.add_argument("--development", action="store_true", help="Run Loom :forge:runClient in the isolated directory")
    parser.add_argument("--gradle-java-home", help="Java 17 JDK for --development (game still uses Java 8)")
    parser.add_argument("--natives", type=Path, help="Extracted LWJGL 2/JInput natives matching the Java 8 architecture")
    parser.add_argument("--optifine", type=Path, help="Explicit local OptiFine M5 JAR; copied only into the isolated run")
    parser.add_argument("--extra-mod", type=Path, action="append", default=[], help="Explicit local compatibility mod JAR (repeatable); no settings or accounts copied")
    parser.add_argument("--scenario", choices=["title", "world", "packets", "ui", "targethud", "invmove", "yuri-update", "scaffold", "beddefender", "breaker", "b4", "full"], default="full")
    parser.add_argument("--width", type=int, default=854)
    parser.add_argument("--height", type=int, default=480)
    parser.add_argument("--gui-scale", type=int, choices=[0, 1, 2, 3], default=0,
                        help="Isolated Minecraft GUI scale; 0 uses Auto")
    parser.add_argument("--timeout", type=int, default=240)
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--stay-open", action="store_true", help="Retain the window after the marker until closed or timeout")
    args = parser.parse_args()
    if args.width < 640 or args.height < 360:
        parser.error("Smoke window must be at least 640 x 360")
    if args.timeout <= 0:
        parser.error("--timeout must be positive")
    try:
        return launch(args)
    except (RuntimeError, OSError, subprocess.CalledProcessError, ValueError) as failure:
        print("Smoke setup failed: " + str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())

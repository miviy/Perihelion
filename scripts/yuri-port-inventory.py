#!/usr/bin/env python3
"""Check every top-level Yuri module has an explicit native mapping or UI exclusion."""
from pathlib import Path
import json
import re
import subprocess

root = Path(__file__).resolve().parents[1]
donor = root.parent / "donor_clients/Yuri/src/main/java/ddlc/yuri/modules/impl"
ledger = json.loads((root / "docs/yuri-modules.json").read_text())
release = json.loads((root / "docs/yuri-1.3-modules.json").read_text())
prefix = "src/main/java/ddlc/yuri/modules/impl/"
historical = subprocess.check_output(["git", "-C", str(root.parent / "donor_clients/Yuri"), "ls-tree", "-r", "--name-only", release["legacy_revision"]], text=True).splitlines()
actual = {p.removeprefix(prefix) for p in historical if p.startswith(prefix) and re.fullmatch(r"[^/]+/[^/]+Module\.java", p.removeprefix(prefix))}
assert actual == set(ledger), (actual - set(ledger), set(ledger) - actual)
release_paths = subprocess.check_output(["git", "-C", str(root.parent / "donor_clients/Yuri"), "ls-tree", "-r", "--name-only", release["release_revision"]], text=True).splitlines()
current = {p.removeprefix(prefix) for p in release_paths if p.startswith(prefix) and re.fullmatch(r"[^/]+/[^/]+Module\.java", p.removeprefix(prefix))}
assert current == set(release["modules"]), (current - set(release["modules"]), set(release["modules"]) - current)
source = "\n".join(p.read_text() for p in (root / "forge/src/main/java/perihelion/integration/minecraft189/module").rglob("*.java"))
native = set(re.findall(r'super\(\s*(?:r|runtime),\s*"([a-z0-9]+)"', source))
native.update(re.findall(r'new ModuleInfo\(\s*"([a-z0-9]+)"', source))
for path, entry in ledger.items():
    assert entry["status"] in {"native-implementation", "excluded-client-ui"}, path
    if entry["status"] == "native-implementation":
        assert entry["native"] in native, (path, entry["native"])
    else:
        assert not entry["native"], path
for status in sorted({v["status"] for v in ledger.values()}):
    names = [Path(p).stem.removesuffix("Module") for p, v in ledger.items() if v["status"] == status]
    print(f"{status}: {len(names)}: {', '.join(names)}")
for path, entry in release["modules"].items():
    assert entry["status"] in {"native-implementation", "excluded-client-ui", "deferred"}, path
    if entry["status"] == "native-implementation":
        assert entry["native"] in native, (path, entry["native"])
    else:
        assert not entry["native"], path
print(f"PASS: {len(actual)} historical and {len(current)} release donor definitions accounted for; deferred modules are explicit. Native registry is also checked by BuiltinModulesTest.")

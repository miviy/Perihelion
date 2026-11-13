#!/usr/bin/env python3
"""Validate pinned latest-source fingerprints and native registration coverage.

The verified source snapshot lives in .references and is intentionally not built.
This checks inventories and fingerprints, not behavioral or anti-cheat equivalence.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--reference", type=Path)
parser.add_argument("--check-upstream", action="store_true")
args = parser.parse_args()
ledger = json.loads((root / "docs/yuri-latest-modules.json").read_text())
reference = args.reference or root / ledger["reference"]
if not (reference / "manifest.json").is_file():
    raise SystemExit("Latest source snapshot unavailable; supply --reference with its verified manifest.json.")
manifest = json.loads((reference / "manifest.json").read_text())
assert manifest["revision"] == ledger["revision"], "Wrong snapshot revision"
for name, entry in manifest["files"].items():
    data = (reference / name).read_bytes()
    assert hashlib.sha256(data).hexdigest() == entry["sha256"], name
    assert hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest() == entry["git_blob"], name
prefix = "src/main/java/ddlc/yuri/modules/impl/"
actual = {p[len(prefix):] for p in manifest["files"] if p.startswith(prefix)
          and re.fullmatch(r"[^/]+/[^/]+Module\.java", p[len(prefix):])}
assert actual == set(ledger["modules"]), "Latest module inventory mismatch"
for name, entry in ledger["modules"].items():
    fingerprint = manifest["files"][prefix + name]
    assert fingerprint["sha256"] == entry["donor_sha256"], name
    assert fingerprint["git_blob"] == entry["donor_git_blob"], name
    if entry["status"] != "native-implementation":
        assert not entry["native"], name
        continue
    native = re.escape(entry["native"])
    pattern = r'(?:super\(\s*(?:r|runtime),\s*"' + native + r'"|new ModuleInfo\(\s*"' + native + r'")'
    assert any(re.search(pattern, (root / p).read_text()) for p in entry["native_files"]), name
if args.check_upstream:
    output = subprocess.check_output(["git", "ls-remote", "https://github.com/unleg1t/Yuri.git", "HEAD"], text=True)
    assert output.split()[0] == ledger["revision"], "Upstream advanced; re-review its changes before claiming latest"
print(f"PASS: {len(manifest['files'])} verified Java reference files; {len(actual)} latest module definitions accounted for.")
print(ledger["classification"])
print("This is source/inventory evidence; run unit and packaged-game checks separately.")

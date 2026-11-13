#!/usr/bin/env python3
"""Verify or restore the pre-HUD-style snapshot, refusing to overwrite later changes."""
from pathlib import Path
import argparse, hashlib, json, shutil, time
root=Path(__file__).resolve().parents[1]
backup=root/'.backups/hud-clean-20261026'
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
args=argparse.ArgumentParser()
args.add_argument('--apply',action='store_true')
options=args.parse_args()
before=json.loads((backup/'manifest.json').read_text())
after=json.loads((backup/'after.json').read_text())
for rel,expected in before.items():
    if digest(backup/rel)!=expected: raise SystemExit('Backup differs: '+rel)
for rel,expected in after.items():
    if digest(root/rel)!=expected: raise SystemExit('Later change preserved; refusing restore: '+rel)
print('Verified snapshot and current changes:',len(after),'files')
if options.apply:
    saved=backup/('before-restore-'+str(time.time_ns()))
    for rel in after:
        source=root/rel
        if source.exists():
            dest=saved/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
        if rel in before:
            shutil.copy2(backup/rel,source)
        else:
            source.unlink()
    print('Restored source; rebuild before launching. Saved current files:',saved)
else:
    print('Dry run only. Add --apply to restore after review.')

#!/usr/bin/env python3
"""Check or restore the config dropdown snapshot, preserving later edits by refusing conflicts."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Restore after all guards pass')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    backup = root / '.backups/config-dropdown-20261021'
    entries = json.loads((backup / 'rollback.json').read_text())
    for name, hashes in entries.items():
        if digest(root / name) != hashes['after']:
            raise SystemExit('Refusing to overwrite later edits: ' + name)
        if hashes['before'] is not None and digest(backup / 'rollback-before' / name) != hashes['before']:
            raise SystemExit('Backup checksum mismatch: ' + name)
    if args.apply:
        for name, hashes in entries.items():
            path = root / name
            if hashes['before'] is None:
                path.unlink()
            else:
                shutil.copy2(backup / 'rollback-before' / name, path)
        print('Restored config UI and selective-load API. Rebuild before launching Minecraft.')
    else:
        print('All rollback guards passed. Use --apply to restore.')


if __name__ == '__main__':
    main()

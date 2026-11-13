#!/usr/bin/env python3
"""Check the Yuri 1.3.1 rollback, or apply it after verifying no later edits would be lost."""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
BACKUP = ROOT / '.backups/before-yuri-131-20261017-100824'
ARCHIVE_SHA = 'b5e6e23849ee4c520ab671212bbf9198e31f361203764d9c0af214ca7785e33c'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe_path(name):
    path = ROOT / name
    if Path(name).is_absolute() or '..' in Path(name).parts or path.is_symlink():
        raise SystemExit('Unsafe rollback path: ' + name)
    if ROOT not in path.resolve().parents:
        raise SystemExit('Rollback path escapes project: ' + name)
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Restore original files and remove Yuri 1.3.1 additions after backing up current affected files.')
    args = parser.parse_args()
    archive = BACKUP / 'source.tar.gz'
    if digest(archive.read_bytes()) != ARCHIVE_SHA:
        raise SystemExit('Backup archive checksum mismatch; refusing restoration.')
    original = json.loads((BACKUP / 'manifest.json').read_text())
    changes = json.loads((BACKUP / 'changes.json').read_text())
    preserved = json.loads((BACKUP / 'preserved-files.json').read_text())
    if set(changes) & set(preserved):
        raise SystemExit('Rollback overlaps a preserved file; refusing restoration.')
    originals = {}
    with tarfile.open(archive, 'r:gz') as source:
        for member in source.getmembers():
            if not member.isfile():
                continue
            name = member.name.removeprefix('./')
            safe_path(name)
            data = source.extractfile(member).read()
            if original.get(name) != digest(data):
                raise SystemExit('Archived member checksum mismatch: ' + name)
            originals[name] = data
    if set(originals) != set(original):
        raise SystemExit('Original manifest does not match archive.')
    for name, expected in changes.items():
        path = safe_path(name)
        if not path.is_file() or digest(path.read_bytes()) != expected:
            raise SystemExit('File changed since the Yuri 1.3.1 snapshot; preserve/merge later work first: ' + name)
    added = sum(name not in original for name in changes)
    print(f'Verified backup and {len(changes)} affected files: {added} additions, {len(changes)-added} modifications.')
    if not args.apply:
        print('Dry run only. Pass --apply to restore; current affected files will be backed up first.')
        return
    checkpoint = ROOT / '.backups' / ('before-yuri-131-rollback-' + datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-%f'))
    checkpoint.mkdir()
    with tarfile.open(checkpoint / 'affected-files.tar.gz', 'w:gz') as target:
        for name in changes:
            target.add(safe_path(name), arcname=name, recursive=False)
    (checkpoint / 'manifest.json').write_text(json.dumps(changes, indent=2) + '\n')
    with tarfile.open(checkpoint / 'affected-files.tar.gz', 'r:gz') as saved:
        checkpoint_hashes = {
            member.name: digest(saved.extractfile(member).read())
            for member in saved.getmembers() if member.isfile()
        }
    if checkpoint_hashes != changes:
        raise SystemExit('Recovery checkpoint did not match the verified files; refusing restoration.')
    for name, expected in changes.items():
        path = safe_path(name)
        if not path.is_file() or digest(path.read_bytes()) != expected:
            raise SystemExit('File changed during rollback preparation; preserve later work first: ' + name)
    for name in changes:
        path = safe_path(name)
        if name in originals:
            path.write_bytes(originals[name])
        else:
            path.unlink()
    print('Restored original source. Current affected files saved at', checkpoint)
    print('Generated JARs/caches/worlds were left intact. Rebuild before using the restored client.')


if __name__ == '__main__':
    main()

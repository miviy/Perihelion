#!/usr/bin/env python3
"""Check the Modern-Lazify overlay rollback, or apply it after verifying no later edits would be lost."""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
BACKUP = ROOT / '.backups/before-modern-lazify-20261013-154135'
ARCHIVE_SHA = '47e38e02f7526f81c69736ae66001033ce331ec20518c3b22ac51d854ade8722'
DONOR_SECTION_SHA = '83b4b028862fb6e676be8e5570e8e79a633c2af6e7867973e6f1d0f05797c312'


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
    parser.add_argument('--apply', action='store_true', help='Restore original files and remove Modern-Lazify overlay additions after backing up current affected files.')
    args = parser.parse_args()
    archive = BACKUP / 'source.tar.gz'
    if digest(archive.read_bytes()) != ARCHIVE_SHA:
        raise SystemExit('Backup archive checksum mismatch; refusing restoration.')
    original = json.loads((BACKUP / 'manifest.json').read_text())
    changes = json.loads((BACKUP / 'changes.json').read_text())
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
            raise SystemExit('File changed since the Modern-Lazify overlay snapshot; preserve/merge later work first: ' + name)
    donor_section = (BACKUP / 'donor-section.md').read_text()
    if digest(donor_section.encode()) != DONOR_SECTION_SHA or safe_path('docs/DONORS.md').read_text().count(donor_section) != 1:
        raise SystemExit('Donor documentation section changed; refusing restoration.')
    added = sum(name not in original for name in changes)
    print(f'Verified backup and {len(changes)} affected files: {added} additions, {len(changes)-added} modifications.')
    if not args.apply:
        print('Dry run only. Pass --apply to restore; current affected files will be backed up first.')
        return
    checkpoint = ROOT / '.backups' / ('before-modern-lazify-rollback-' + datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-%f'))
    checkpoint.mkdir()
    with tarfile.open(checkpoint / 'affected-files.tar.gz', 'w:gz') as target:
        for name in changes:
            target.add(safe_path(name), arcname=name, recursive=False)
    (checkpoint / 'manifest.json').write_text(json.dumps(changes, indent=2) + '\n')
    for name in changes:
        path = safe_path(name)
        if name == 'docs/DONORS.md':
            path.write_text(path.read_text().replace(donor_section, '', 1))
        elif name in originals:
            path.write_bytes(originals[name])
        else:
            path.unlink()
    print('Restored original source. Current affected files saved at', checkpoint)
    print('Generated JARs/caches/worlds were left intact. Rebuild before using the restored client.')


if __name__ == '__main__':
    main()

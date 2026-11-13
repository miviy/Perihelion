#!/usr/bin/env python3
"""Verify the OpenB4 gameplay rollback; use --apply to restore after saving affected files."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import tarfile
import uuid

ROOT = Path(__file__).resolve().parents[1]
BACKUP = ROOT / '.backups/before-openb4-gameplay-20261022-140913'
ARCHIVE_SHA = '806262d882a88419a5f3e2e917bb4326f2d29f0a153c67ae32514b23d0a72fb4'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe_name(name):
    if not isinstance(name, str) or not name or '\\' in name or '\0' in name:
        raise SystemExit('Unsafe rollback path: ' + repr(name))
    path = PurePosixPath(name)
    if not path.parts or path.is_absolute() or path.as_posix() != name or any(p in ('.', '..') for p in path.parts):
        raise SystemExit('Unsafe rollback path: ' + name)
    return path.parts


def safe_path(name):
    parts = safe_name(name)
    path = ROOT
    if path.is_symlink():
        raise SystemExit('Rollback project root must not be a symlink.')
    for part in parts:
        path = path / part
        if path.is_symlink():
            raise SystemExit('Symlink in rollback path: ' + name)
    if ROOT.resolve() not in path.resolve().parents:
        raise SystemExit('Rollback path escapes project: ' + name)
    return path


@contextmanager
def parent_fd(name):
    """Open every parent with O_NOFOLLOW; subsequent operations stay on these directories."""
    parts = safe_name(name)
    safe_path(name)
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
    fd = os.open(ROOT, flags)
    try:
        for part in parts[:-1]:
            child = os.open(part, flags, dir_fd=fd)
            os.close(fd)
            fd = child
        yield fd, parts[-1]
    finally:
        os.close(fd)


def read_file(name):
    with parent_fd(name) as (parent, leaf):
        fd = os.open(leaf, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=parent)
        with os.fdopen(fd, 'rb') as stream:
            metadata = os.fstat(stream.fileno())
            if not stat.S_ISREG(metadata.st_mode):
                raise SystemExit('Rollback requires a regular file: ' + name)
            return stream.read(), stat.S_IMODE(metadata.st_mode)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise SystemExit('Duplicate rollback manifest entry: ' + key)
        result[key] = value
    return result


def manifest(path):
    data = json.loads(path.read_text(), object_pairs_hook=unique_object)
    if not isinstance(data, dict):
        raise SystemExit('Rollback manifest must be an object: ' + str(path))
    for name, sha in data.items():
        safe_name(name)
        if not isinstance(sha, str) or re.fullmatch('[0-9a-f]{64}', sha) is None:
            raise SystemExit('Invalid rollback hash: ' + name)
    return data


def verify_current(changes):
    current = {}
    for name, expected in changes.items():
        if safe_name(name)[0] in ('.git', '.backups'):
            raise SystemExit('Protected rollback path: ' + name)
        try:
            data, mode = read_file(name)
        except OSError as error:
            raise SystemExit('Cannot safely read affected file: ' + name + ': ' + str(error))
        if digest(data) != expected:
            raise SystemExit('File changed since the OpenB4 snapshot; preserve/merge later work first: ' + name)
        current[name] = (data, mode)
    return current


def replace_file(name, data, mode):
    # Replace the inode instead of truncating it, preserving any unrelated hard-link copy.
    with parent_fd(name) as (parent, leaf):
        temporary = '.openb4-rollback-' + uuid.uuid4().hex
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                     mode & 0o777, dir_fd=parent)
        try:
            with os.fdopen(fd, 'wb') as stream:
                stream.write(data)
                stream.flush()
                os.fchmod(stream.fileno(), mode & 0o777)
                os.fsync(stream.fileno())
            os.replace(temporary, leaf, src_dir_fd=parent, dst_dir_fd=parent)
        finally:
            try:
                os.unlink(temporary, dir_fd=parent)
            except FileNotFoundError:
                pass


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true',
                        help='Back up affected files, restore originals and remove listed additions.')
    args = parser.parse_args(argv)
    archive = BACKUP / 'source.tar.gz'
    for path in (archive, BACKUP / 'manifest.json', BACKUP / 'changes.json'):
        safe_path(path.relative_to(ROOT).as_posix())
    if digest(archive.read_bytes()) != ARCHIVE_SHA:
        raise SystemExit('Backup archive checksum mismatch; refusing restoration.')
    original = manifest(BACKUP / 'manifest.json')
    changes = manifest(BACKUP / 'changes.json')
    originals = {}
    with tarfile.open(archive, 'r:gz') as source:
        for member in source:
            name = member.name
            safe_name(name)
            if not member.isfile() or name in originals:
                raise SystemExit('Unsupported or duplicate archived member: ' + name)
            data = source.extractfile(member).read()
            if original.get(name) != digest(data):
                raise SystemExit('Archived member checksum mismatch: ' + name)
            originals[name] = (data, member.mode)
    if set(originals) != set(original):
        raise SystemExit('Original manifest does not match archive.')
    current = verify_current(changes)
    added = sum(name not in original for name in changes)
    print(f'Verified pinned backup and {len(changes)} affected files: '
          f'{added} additions, {len(changes) - added} modifications.')
    if not args.apply:
        print('Dry run only. Pass --apply to restore after backing up affected files.')
        return
    safe_path('.backups')
    checkpoint = ROOT / '.backups' / ('before-openb4-gameplay-rollback-'
                 + datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-%f'))
    checkpoint.mkdir()
    saved = checkpoint / 'affected-files.tar.gz'
    with tarfile.open(saved, 'w:gz') as target:
        for name, (data, mode) in current.items():
            member = tarfile.TarInfo(name)
            member.size, member.mode = len(data), mode
            target.addfile(member, io.BytesIO(data))
    (checkpoint / 'manifest.json').write_text(json.dumps(changes, indent=2) + '\n')
    (checkpoint / 'affected-files.tar.gz.sha256').write_text(digest(saved.read_bytes()) + '\n')
    with tarfile.open(saved, 'r:gz') as check:
        checkpoint_hashes = {m.name: digest(check.extractfile(m).read()) for m in check}
    if checkpoint_hashes != changes:
        raise SystemExit('Rollback checkpoint verification failed; source remains unchanged.')
    # Recheck the entire set after saving, before changing any project file.
    verify_current(changes)
    for name in changes:
        if name in originals:
            data, mode = originals[name]
            replace_file(name, data, mode)
        else:
            with parent_fd(name) as (parent, leaf):
                os.unlink(leaf, dir_fd=parent)
    print('Restored original source. Current affected files saved at', checkpoint)
    print('Unlisted files, generated JARs, caches and worlds were left intact. Rebuild before use.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, tarfile.TarError) as error:
        raise SystemExit('Rollback refused: ' + str(error))

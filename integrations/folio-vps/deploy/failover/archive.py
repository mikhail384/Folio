#!/usr/bin/env python3
"""Create and validate a consistent Folio state archive without following links."""
from contextlib import closing
import json
import os
from pathlib import Path, PurePosixPath
import sqlite3
import sys
import tarfile
import tempfile

MAX_BYTES = 512 * 1024 * 1024


def make(source, dest):
    source = Path(source)
    with tempfile.TemporaryDirectory(prefix='folio-checkpoint-') as tmp:
        db = Path(tmp) / 'folio.sqlite'
        with closing(sqlite3.connect((source / 'folio.sqlite').resolve().as_uri() + '?mode=ro', uri=True)) as current:
            with closing(sqlite3.connect(db)) as snapshot:
                current.backup(snapshot)
        with tarfile.open(dest, 'w:gz') as archive:
            archive.add(source / 'secrets.json', arcname='secrets.json', recursive=False)
            archive.add(db, arcname='folio.sqlite', recursive=False)
            root = source / 'objects'
            if root.exists():
                for current, dirs, files in os.walk(root, followlinks=False):
                    dirs[:] = [d for d in dirs if not (Path(current) / d).is_symlink()]
                    for file in files:
                        path = Path(current) / file
                        if path.is_symlink() or not path.is_file():
                            raise ValueError('Unsafe object')
                        archive.add(path, arcname='objects/' + path.relative_to(root).as_posix(), recursive=False)


def restore(archive_path, destination):
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    seen = set()
    with tarfile.open(archive_path, 'r:gz') as archive:
        members = archive.getmembers()
        total = 0
        for member in members:
            path = PurePosixPath(member.name)
            if not member.isfile() or path.is_absolute() or '..' in path.parts or '.' in path.parts:
                raise ValueError('Unsafe archive entry')
            if member.name not in ('secrets.json', 'folio.sqlite') and not member.name.startswith('objects/'):
                raise ValueError('Unexpected archive entry')
            if member.name in seen:
                raise ValueError('Duplicate archive entry')
            seen.add(member.name)
            total += member.size
            if total > MAX_BYTES:
                raise ValueError('Checkpoint too large')
            target = destination.joinpath(*path.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as src, open(target, 'xb') as out:
                while data := src.read(1024 * 1024):
                    out.write(data)
                out.flush()
                os.fsync(out.fileno())
            os.chmod(target, 0o600)
    if not {'secrets.json', 'folio.sqlite'} <= seen:
        raise ValueError('Incomplete checkpoint')
    secrets = json.loads((destination / 'secrets.json').read_text(encoding='utf-8'))
    if not all(secrets.get(name) for name in ('passwordHash', 'jwk', 'cookieKey', 'serviceToken')):
        raise ValueError('Incomplete credentials')
    with closing(sqlite3.connect((destination / 'folio.sqlite').resolve().as_uri() + '?mode=ro', uri=True)) as db:
        if db.execute('PRAGMA quick_check').fetchone() != ('ok',):
            raise ValueError('Invalid database')
    (destination / 'objects').mkdir(exist_ok=True)
    if os.name != 'nt':
        for current, _, _ in os.walk(destination, topdown=False):
            fd = os.open(current, os.O_RDONLY)
            try:
                os.fsync(fd)
            finally:
                os.close(fd)


if __name__ == '__main__':
    mode, source, dest = sys.argv[1:]
    if mode == 'make':
        make(source, dest)
    elif mode == 'restore':
        restore(source, dest)
    else:
        raise SystemExit('Invalid operation')

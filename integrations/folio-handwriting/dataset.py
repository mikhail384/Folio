"""Prepare case-sensitive, time-ordered ink examples for Folio model experiments.

The public input is the CC BY 4.0 Russian Handwritings Tracked archive. Its
coordinates do not include pen-up flags; long timing gaps are estimates only.
No user's private examples are included unless explicitly passed as a file.
"""
import argparse
import csv
import io
import json
import math
from pathlib import Path
import re
import zipfile

import numpy as np

POINTS = 64
SOURCE = 'Russian Handwritings Tracked, Iatsenko and Smirnov, 2023, CC BY 4.0, doi:10.17632/3h6h5d7xg2.2'


def read_numbers(z, name):
    text = z.read(name).decode('utf-8-sig')
    return [float(v) for v in next(csv.reader(io.StringIO(text))) if v.strip()]


def inferred_paths(values, times):
    if len(values) % 2 or len(values) < 4:
        raise ValueError('invalid trajectory')
    points = np.asarray(values, dtype=np.float32).reshape(-1, 2)
    if not np.isfinite(points).all() or len(times) != len(points):
        raise ValueError('invalid timings')
    distances = np.linalg.norm(np.diff(points, axis=0), axis=1)
    nonzero = distances[distances > .01]
    typical = float(np.median(nonzero)) if len(nonzero) else 1
    paths, current = [], [points[0].tolist()]
    for i in range(1, len(points)):
        # The source does not record pen-up: infer a lift only on both a pause
        # and a displacement. This must not be advertised as ground truth.
        if times[i] >= 110 and distances[i-1] > max(3.0, typical * 1.8):
            if current:
                paths.append(current)
            current = []
        current.append(points[i].tolist())
    if current:
        paths.append(current)
    return paths


def normalize(paths, flip_y=False):
    allpoints = np.asarray([p[:2] for path in paths for p in path], dtype=np.float32)
    if len(allpoints) < 2 or not np.isfinite(allpoints).all():
        raise ValueError('empty ink')
    low, high = allpoints.min(axis=0), allpoints.max(axis=0)
    height = float(high[1] - low[1]); width = float(high[0] - low[0])
    if height < 1e-3 or width < 1e-3 or width / height > 5:
        raise ValueError('unusable glyph bounds')
    result = []
    for path in paths:
        points = np.asarray(path, dtype=np.float32)[:, :2].copy()
        points[:, 0] = (points[:, 0] - low[0]) / height
        points[:, 1] = ((high[1] - points[:, 1]) if flip_y else (points[:, 1] - low[1])) / height
        result.append(points)
    return result


def resample(paths, count=POINTS):
    if not paths or len(paths) > count // 2:
        raise ValueError('too many pen lifts')
    lengths = []
    for path in paths:
        delta = np.linalg.norm(np.diff(path, axis=0), axis=1)
        lengths.append(max(float(delta.sum()), .025))
    lengths = np.asarray(lengths)
    remaining = count - len(paths) * 2
    raw = remaining * lengths / lengths.sum()
    extra = np.floor(raw).astype(int)
    for index in np.argsort(-(raw - extra))[:remaining - int(extra.sum())]:
        extra[index] += 1
    out = []
    for path, extra_points in zip(paths, extra):
        n = int(extra_points) + 2
        if len(path) == 1:
            sampled = np.repeat(path, n, axis=0)
        else:
            distance = np.concatenate([[0.], np.cumsum(np.linalg.norm(np.diff(path, axis=0), axis=1))])
            if distance[-1] < 1e-8:
                sampled = np.repeat(path[:1], n, axis=0)
            else:
                grid = np.linspace(0, distance[-1], n)
                sampled = np.column_stack([np.interp(grid, distance, path[:, axis]) for axis in range(2)])
        for i, (x, y) in enumerate(sampled):
            out.append([float(x), float(y), 1. if i == n-1 else 0.])
    result = np.asarray(out, dtype=np.float32)
    if result.shape != (count, 3) or not np.isfinite(result).all():
        raise ValueError('invalid resampled ink')
    return result


def load_public(path):
    examples, skipped = [], 0
    with zipfile.ZipFile(path) as z:
        names = set(z.namelist())
        for name in sorted(names):
            match = re.fullmatch(r'w_(\d+)_\d+/([^/])', name)
            if not match or name + '_times' not in names:
                continue
            writer, label = match.groups()
            try:
                paths = inferred_paths(read_numbers(z, name), read_numbers(z, name + '_times'))
                samples = resample(normalize(paths, flip_y=True))
                examples.append((label, int(writer), samples))
            except (ValueError, IndexError, UnicodeError, csv.Error):
                skipped += 1
    return examples, skipped


def prepare(source, destination):
    examples, skipped = load_public(source)
    if not examples:
        raise ValueError('No usable data')
    labels = sorted(set(label for label, _, _ in examples))
    ids = {label: i for i, label in enumerate(labels)}
    x = np.stack([sample for _, _, sample in examples])
    y = np.asarray([ids[label] for label, _, _ in examples], dtype=np.int64)
    writers = np.asarray([writer for _, writer, _ in examples], dtype=np.int64)
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(destination, strokes=x, labels=y, writers=writers)
    metadata = {'format': 'folio-vector-research-v1', 'source': SOURCE, 'labels': labels,
                'points': POINTS, 'count': len(examples), 'skipped': skipped,
                'pen_up': 'inferred from timing and distance; no pressure in source'}
    destination.with_suffix('.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Prepared', len(examples), 'sequences across', len(labels), 'case-sensitive labels; skipped', skipped)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('archive')
    parser.add_argument('output')
    args = parser.parse_args()
    prepare(args.archive, args.output)

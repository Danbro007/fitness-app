#!/usr/bin/env python3
"""Download exercise GIF assets listed in the Android asset manifest.

The repository keeps the lightweight manifest in Git and ignores downloaded
GIF binaries. Exercise media is not covered by the dataset's MIT license. Run
this script only if you already hold the rights required by Gym Visual's terms.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "app/src/main/assets/exercise-media/manifest.json"
DEFAULT_ASSET_DIR = ROOT / "app/src/main/assets/exercise-media"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def existing_file_is_valid(path: Path, expected_sha256: str, expected_bytes: int) -> bool:
    if not path.exists() or path.stat().st_size == 0:
        return False
    if expected_bytes and path.stat().st_size != expected_bytes:
        return False
    if expected_sha256 and sha256(path) != expected_sha256:
        return False
    return True


def download(url: str, target: Path, timeout: int, retries: int) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    tmp = target.with_suffix(target.suffix + ".tmp")
    last_error: Exception | None = None

    for attempt in range(1, retries + 1):
        try:
            request = urllib.request.Request(
                url,
                headers={"User-Agent": "fitness-app-asset-downloader/1.0"},
            )
            with urllib.request.urlopen(request, timeout=timeout) as response:
                with tmp.open("wb") as handle:
                    while True:
                        chunk = response.read(1024 * 256)
                        if not chunk:
                            break
                        handle.write(chunk)
            tmp.replace(target)
            return
        except (OSError, urllib.error.URLError) as exc:
            last_error = exc
            if tmp.exists():
                tmp.unlink()
            if attempt < retries:
                time.sleep(min(2 * attempt, 8))

    raise RuntimeError(f"failed to download {url}: {last_error}")


def load_manifest(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Download exercise GIF assets from app/src/main/assets/exercise-media/manifest.json",
    )
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--asset-dir", type=Path, default=DEFAULT_ASSET_DIR)
    parser.add_argument("--limit", type=int, default=0, help="download only the first N assets")
    parser.add_argument("--force", action="store_true", help="redownload even when the local file is valid")
    parser.add_argument("--dry-run", action="store_true", help="print what would be downloaded without writing files")
    parser.add_argument(
        "--i-have-media-license",
        action="store_true",
        help="confirm that you already hold the rights required to download and use the exercise media",
    )
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--retries", type=int, default=3)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.dry_run and not args.i_have_media_license:
        print(
            "Refusing to download third-party exercise media without an explicit rights acknowledgement.\n"
            "Review THIRD_PARTY_NOTICES.md and Gym Visual's terms, then rerun with "
            "--i-have-media-license only if you already have the required permission.",
            file=sys.stderr,
        )
        return 2

    manifest = load_manifest(args.manifest)
    files = manifest.get("files", [])
    if args.limit > 0:
        files = files[: args.limit]

    downloaded = 0
    skipped = 0
    failed: list[str] = []

    for index, item in enumerate(files, start=1):
        url = item.get("remoteUrl")
        local_path = item.get("localPath")
        expected_sha256 = item.get("sha256", "")
        expected_bytes = int(item.get("bytes", 0) or 0)

        if not url or not local_path:
            failed.append(f"{index}: missing remoteUrl/localPath")
            continue

        target = args.asset_dir / local_path
        label = f"[{index}/{len(files)}] {local_path}"

        if not args.force and existing_file_is_valid(target, expected_sha256, expected_bytes):
            print(f"{label} exists")
            skipped += 1
            continue

        if args.dry_run:
            print(f"{label} would download {url}")
            skipped += 1
            continue

        try:
            print(f"{label} downloading")
            download(url, target, timeout=args.timeout, retries=args.retries)
            if not existing_file_is_valid(target, expected_sha256, expected_bytes):
                raise RuntimeError("downloaded file failed size or sha256 verification")
            downloaded += 1
        except Exception as exc:
            failed.append(f"{local_path}: {exc}")

    print()
    print(f"downloaded={downloaded} skipped={skipped} failed={len(failed)}")
    if failed:
        print("Failures:", file=sys.stderr)
        for message in failed:
            print(f"- {message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

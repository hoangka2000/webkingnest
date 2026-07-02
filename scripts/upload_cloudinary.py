#!/usr/bin/env python3
"""Upload public/images to Cloudinary and write a path manifest."""

from __future__ import annotations

import json
import os
import sys
import time
from pathlib import Path

import cloudinary
import cloudinary.uploader
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
IMAGES_DIR = ROOT / "public" / "images"
MANIFEST_PATH = ROOT / "cloudinary_manifest.json"
SUPPORTED = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"}


def configure() -> None:
    load_dotenv(ROOT / ".env")
    cloudinary.config(
        cloud_name=os.getenv("CLOUDINARY_CLOUD_NAME", "ln22f4im"),
        api_key=os.getenv("CLOUDINARY_API_KEY", ""),
        api_secret=os.getenv("CLOUDINARY_API_SECRET", ""),
        secure=True,
    )
    if not os.getenv("CLOUDINARY_API_KEY") or not os.getenv("CLOUDINARY_API_SECRET"):
        raise SystemExit("Missing CLOUDINARY_API_KEY or CLOUDINARY_API_SECRET in .env")


def load_manifest() -> dict[str, str]:
    if MANIFEST_PATH.exists():
        return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    return {}


def save_manifest(manifest: dict[str, str]) -> None:
    MANIFEST_PATH.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def upload_file(file_path: Path, folder: str) -> str:
    relative = file_path.relative_to(IMAGES_DIR).as_posix()
    public_id = f"{folder}/{Path(relative).with_suffix('').as_posix()}"
    result = cloudinary.uploader.upload(
        str(file_path),
        public_id=public_id,
        overwrite=True,
        resource_type="image",
    )
    return result["secure_url"]


def main() -> None:
    configure()
    folder = os.getenv("CLOUDINARY_FOLDER", "kingnest")
    manifest = load_manifest()
    files = sorted(
        path
        for path in IMAGES_DIR.rglob("*")
        if path.is_file() and path.suffix.lower() in SUPPORTED
    )

    print(f"Found {len(files)} images in {IMAGES_DIR}")
    uploaded = 0
    skipped = 0
    failed = 0

    for index, file_path in enumerate(files, start=1):
        local_key = f"/images/{file_path.relative_to(IMAGES_DIR).as_posix()}"
        if local_key in manifest:
            skipped += 1
            continue

        try:
            secure_url = upload_file(file_path, folder)
            manifest[local_key] = secure_url
            uploaded += 1
            print(f"[{index}/{len(files)}] OK {local_key}")
            if uploaded % 10 == 0:
                save_manifest(manifest)
            time.sleep(0.15)
        except Exception as exc:
            failed += 1
            print(f"[{index}/{len(files)}] FAIL {local_key}: {exc}", file=sys.stderr)

    save_manifest(manifest)
    print(f"Done. uploaded={uploaded} skipped={skipped} failed={failed}")
    print(f"Manifest: {MANIFEST_PATH}")


if __name__ == "__main__":
    main()

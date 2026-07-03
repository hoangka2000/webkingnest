#!/usr/bin/env python3
"""Rebuild cloudinary_manifest.json from Cloudinary Media Library folder Kingnest."""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

import cloudinary
from cloudinary import Search
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
LOCAL_IMAGES = Path("/Users/nguyendanghoang01112000/Desktop/Kingnest")
MANIFEST_PATH = ROOT / "cloudinary_manifest.json"
SUPPORTED = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"}
ASSET_FOLDER = "Kingnest"


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


def norm_stem(name: str) -> str:
    stem = Path(name).stem.replace(" ", "_").replace(",", "").replace("(", "").replace(")", "")
    return re.sub(r"_+", "_", stem).lower()


def fetch_kingnest_resources() -> list[dict]:
    resources: list[dict] = []
    cursor = None
    while True:
        search = Search().expression(f"folder:{ASSET_FOLDER}/*").max_results(500)
        if cursor:
            search = search.next_cursor(cursor)
        response = search.execute()
        resources.extend(response.get("resources", []))
        cursor = response.get("next_cursor")
        if not cursor:
            break
    return resources


def asset_folder_relative(asset_folder: str | None) -> str:
    value = (asset_folder or "").replace(f"{ASSET_FOLDER}/", "").replace(ASSET_FOLDER, "")
    return value


def match_resource(file_path: Path, folder_resources: list[dict]) -> dict | None:
    stem = norm_stem(file_path.name)
    for resource in folder_resources:
        public_id = resource["public_id"].lower()
        if public_id == stem or public_id.startswith(f"{stem}_") or stem in public_id:
            return resource
    for resource in folder_resources:
        if file_path.stem.replace(" ", "_") in resource["public_id"]:
            return resource
    return None


def build_manifest(resources: list[dict], local_root: Path) -> dict[str, str]:
    by_folder: dict[str, list[dict]] = {}
    for resource in resources:
        folder = asset_folder_relative(resource.get("asset_folder"))
        by_folder.setdefault(folder, []).append(resource)

    manifest: dict[str, str] = {}
    missing: list[str] = []
    files = sorted(
        path
        for path in local_root.rglob("*")
        if path.is_file() and path.suffix.lower() in SUPPORTED
    )
    for file_path in files:
        rel = file_path.relative_to(local_root).as_posix()
        local_key = f"/images/{rel}"
        folder = str(Path(rel).parent)
        if folder == ".":
            folder = ""
        resource = match_resource(file_path, by_folder.get(folder, []))
        if resource:
            manifest[local_key] = resource["secure_url"]
        else:
            missing.append(local_key)

    if missing:
        print("Missing Cloudinary matches:", file=sys.stderr)
        for item in missing[:20]:
            print(f"  - {item}", file=sys.stderr)
        raise SystemExit(f"Could not map {len(missing)} local files to Cloudinary.")

    return manifest


def main() -> None:
    configure()
    if not LOCAL_IMAGES.exists():
        raise SystemExit(f"Local image folder not found: {LOCAL_IMAGES}")

    resources = fetch_kingnest_resources()
    manifest = build_manifest(resources, LOCAL_IMAGES)
    MANIFEST_PATH.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Wrote {len(manifest)} entries to {MANIFEST_PATH}")


if __name__ == "__main__":
    main()

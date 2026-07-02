#!/usr/bin/env python3
"""Replace /images/ paths with Cloudinary URLs from manifest."""

from __future__ import annotations

import json
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "cloudinary_manifest.json"
TARGET_DIRS = [
    ROOT / "templates",
    ROOT / "public" / "js",
    ROOT / "public" / "css",
]
EXTENSIONS = {".html", ".js", ".css"}
FALLBACK_IMAGE = "/images/yentinh/tinhche1/trungbay.jpeg"


def html_encoded_path(local_path: str) -> str:
    relative = local_path[len("/images/") :]
    return "/images/" + "/".join(part.replace(" ", "%20") for part in relative.split("/"))


def build_replacements(manifest: dict[str, str]) -> list[tuple[str, str]]:
    pairs: list[tuple[str, str]] = []
    seen: set[str] = set()
    for local_path, cloud_url in manifest.items():
        for candidate in (
            local_path,
            html_encoded_path(local_path),
            local_path.lstrip("/"),
            html_encoded_path(local_path).lstrip("/"),
        ):
            if candidate and candidate not in seen:
                pairs.append((candidate, cloud_url))
                seen.add(candidate)
    pairs.sort(key=lambda item: len(item[0]), reverse=True)
    return pairs


def main() -> None:
    manifest: dict[str, str] = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    replacements = build_replacements(manifest)
    fallback_url = manifest.get(FALLBACK_IMAGE, manifest.get(unquote(FALLBACK_IMAGE), ""))
    updated_files = 0

    for directory in TARGET_DIRS:
        for file_path in directory.rglob("*"):
            if file_path.suffix.lower() not in EXTENSIONS:
                continue
            original = file_path.read_text(encoding="utf-8")
            content = original
            for local_path, cloud_url in replacements:
                content = content.replace(local_path, cloud_url)
            if fallback_url:
                content = content.replace('"/images/yentinh/tinh_che_L1.png"', f'"{fallback_url}"')
                content = content.replace("'/images/yentinh/tinh_che_L1.png'", f"'{fallback_url}'")
                content = content.replace("/images/yentinh/tinh_che_L1.png", fallback_url)
            if content != original:
                file_path.write_text(content, encoding="utf-8")
                updated_files += 1
                print(f"updated {file_path.relative_to(ROOT)}")

    print(f"Done. files={updated_files}")


if __name__ == "__main__":
    main()

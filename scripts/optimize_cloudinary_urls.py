#!/usr/bin/env python3
"""Add f_auto,q_auto,w_* transforms to hardcoded Cloudinary URLs in templates/CSS/JS."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app.cdn import optimize_cloudinary_url

TARGET_DIRS = [
    ROOT / "templates",
    ROOT / "public" / "js",
    ROOT / "public" / "css",
]
EXTENSIONS = {".html", ".js", ".css"}
MANIFEST_PATH = ROOT / "cloudinary_manifest.json"
PATTERN = re.compile(
    r"https://res\.cloudinary\.com/ln22f4im/image/upload/(?!f_auto)([^\s\"')]+)"
)


def optimize_text(content: str, width: int) -> str:
    def replacer(match: re.Match[str]) -> str:
        original = match.group(0)
        return optimize_cloudinary_url(original, width=width)

    return PATTERN.sub(replacer, content)


def main() -> None:
    updated_files = 0
    for directory in TARGET_DIRS:
        for file_path in directory.rglob("*"):
            if file_path.suffix.lower() not in EXTENSIONS:
                continue
            original = file_path.read_text(encoding="utf-8")
            width = 600 if "product" in file_path.name.lower() or "cart" in file_path.name.lower() else 1200
            content = optimize_text(original, width=width)
            if content != original:
                file_path.write_text(content, encoding="utf-8")
                updated_files += 1
                print(f"updated {file_path.relative_to(ROOT)}")

    if MANIFEST_PATH.exists():
        import json

        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        new_manifest = {
            key: optimize_cloudinary_url(url, width=1200)
            for key, url in manifest.items()
        }
        if new_manifest != manifest:
            MANIFEST_PATH.write_text(
                json.dumps(new_manifest, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            print(f"updated {MANIFEST_PATH.name}")

    print(f"Done. files={updated_files}")


if __name__ == "__main__":
    main()

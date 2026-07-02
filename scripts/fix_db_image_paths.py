#!/usr/bin/env python3
"""Normalize product/news image paths in PostgreSQL using Cloudinary manifest."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app.cdn import cdn_url, get_manifest, normalize_local_image_path
from app.database import NewsArticle, Product, get_db, parse_json_field


def fix_value(value: str | None) -> str | None:
    if not value:
        return value
    fixed = cdn_url(value)
    return fixed or value


def main() -> None:
    manifest = get_manifest()
    if not manifest:
        print("Warning: cloudinary_manifest.json not found; using URL builder only.")

    with get_db() as db:
        products = db.query(Product).all()
        for product in products:
            product.image = fix_value(product.image) or product.image
            gallery = parse_json_field(product.gallery, [])
            if isinstance(gallery, list):
                product.gallery = json.dumps(
                    [fix_value(item) or item for item in gallery],
                    ensure_ascii=False,
                )

        articles = db.query(NewsArticle).all()
        for article in articles:
            article.image = fix_value(article.image) or article.image

    print(f"Updated {len(products)} products and {len(articles)} articles.")


if __name__ == "__main__":
    main()

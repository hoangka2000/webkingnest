"""Export products from PostgreSQL to Facebook Shop catalog CSV."""

from __future__ import annotations

import csv
import sys
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.cdn import cdn_url
from app.config import get_settings
from app.database import Product, get_db
from app.services import facebook_purchase_options, normalize_product_type, public_product_slug

BRAND = "Kingnest - Yến Sào Khánh Hoà"
DEFAULT_INVENTORY = 100

FACEBOOK_COLUMNS = [
    "id",
    "title",
    "description",
    "availability",
    "condition",
    "price",
    "link",
    "image_link",
    "brand",
    "product_type",
    "inventory",
]

PRODUCT_TYPE_LABELS = {
    "yen-chung": "Yến chưng",
    "yen-tinh-che": "Yến tinh chế",
    "yen-tho": "Yến thô",
    "hop-qua": "Hộp quà",
}


def site_base_url() -> str:
    return get_settings().resolved_site_url()


def product_link(slug: str) -> str:
    public_slug = public_product_slug(slug)
    return f"{site_base_url()}/chi-tiet-san-pham?slug={quote(public_slug, safe='')}"


def facebook_price(amount: int) -> str:
    return f"{int(amount)} VND"


def facebook_product_type(product: Product) -> str:
    normalized = normalize_product_type(product.product_type)
    if product.category:
        return product.category.strip()
    return PRODUCT_TYPE_LABELS.get(normalized, normalized or "Yến sào")


def facebook_description(product: Product) -> str:
    text = (product.short_desc or product.title or "").strip()
    return text[:5000]


def facebook_image(product: Product) -> str:
    return cdn_url(product.image)


def catalog_rows_for_product(product: Product) -> list[dict[str, str]]:
    base = {
        "description": facebook_description(product),
        "availability": "in stock",
        "condition": "new",
        "link": product_link(product.slug),
        "image_link": facebook_image(product),
        "brand": BRAND,
        "product_type": facebook_product_type(product),
        "inventory": str(DEFAULT_INVENTORY),
    }

    rows: list[dict[str, str]] = []
    for option in facebook_purchase_options(product):
        rows.append(
            {
                **base,
                "id": option["id"],
                "title": option["title"],
                "price": facebook_price(int(option["price"])),
            }
        )
    return rows


def export_facebook_catalog(output_path: Path) -> int:
    rows: list[dict[str, str]] = []
    with get_db() as db:
        products = db.query(Product).order_by(Product.id.asc()).all()
        for product in products:
            rows.extend(catalog_rows_for_product(product))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FACEBOOK_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)

    return len(rows)


def main() -> None:
    output = ROOT / "facebook_catalog.csv"
    if len(sys.argv) > 1:
        output = Path(sys.argv[1]).expanduser().resolve()

    count = export_facebook_catalog(output)
    print(f"Exported {count} rows to {output}")


if __name__ == "__main__":
    main()

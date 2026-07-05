"""Replace An Thịnh Nhân branding with Yến Sào Khánh Hoà in PostgreSQL."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from sqlalchemy import create_engine, text

# Longest patterns first to avoid partial replacements.
REPLACEMENTS: list[tuple[str, str]] = [
    ("Kingnest - Yến Sào An Thịnh Nhân", "Kingnest - Yến Sào Khánh Hoà"),
    ("Kingnest – Yến Sào An Thịnh Nhân", "Kingnest – Yến Sào Khánh Hoà"),
    ("Yến sào KINGNest An Thịnh Nhân", "Yến Sào Khánh Hoà"),
    ("YẾN SÀO KINGNEST AN THỊNH NHÂN", "YẾN SÀO KHÁNH HOÀ"),
    ("KINGNEST – YẾN SÀO AN THỊNH NHÂN", "KINGNEST – YẾN SÀO KHÁNH HOÀ"),
    ("KINGNEST AN THỊNH NHÂN", "KINGNEST YẾN SÀO KHÁNH HOÀ"),
    ("YẾN SÀO AN THỊNH NHÂN", "YẾN SÀO KHÁNH HOÀ"),
    ("Yến Sào An Thịnh Nhân", "Yến Sào Khánh Hoà"),
    ("Kingnest An Thịnh Nhân", "Kingnest Yến Sào Khánh Hoà"),
    ("KINGNest An Thịnh Nhân", "Kingnest Yến Sào Khánh Hoà"),
    ("An Thịnh Nhân", "Yến Sào Khánh Hoà"),
    ("AN THỊNH NHÂN", "YẾN SÀO KHÁNH HOÀ"),
    ("Yến sào Khánh Hoà", "Yến Sào Khánh Hoà"),
]

NEWS_COLUMNS = ("title", "excerpt", "content_html")
PRODUCT_COLUMNS = (
    "title",
    "short_desc",
    "description",
    "benefits",
    "usage",
    "specs",
    "highlights",
    "need",
    "status",
    "content_html",
)


def load_database_url() -> str:
    env_path = ROOT / ".env"
    if not env_path.exists():
        raise SystemExit("Missing .env with DATABASE_URL")
    for line in env_path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if line.startswith("DATABASE_URL="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise SystemExit("DATABASE_URL not found in .env")


def apply_replacements(value: str | None) -> str | None:
    if not value:
        return value
    updated = value
    for old, new in REPLACEMENTS:
        updated = updated.replace(old, new)
    return updated


def migrate() -> None:
    engine = create_engine(load_database_url())
    updated_rows = 0

    with engine.begin() as conn:
        for table, columns in (
            ("news_articles", NEWS_COLUMNS),
            ("products", PRODUCT_COLUMNS),
        ):
            rows = conn.execute(
                text(f"SELECT id, {', '.join(columns)} FROM {table}")
            ).mappings().all()
            for row in rows:
                changes: dict[str, str] = {}
                for col in columns:
                    original = row[col]
                    if original is None:
                        continue
                    replaced = apply_replacements(str(original))
                    if replaced != original:
                        changes[col] = replaced
                if not changes:
                    continue
                set_clause = ", ".join(f"{col} = :{col}" for col in changes)
                params = {"id": row["id"], **changes}
                conn.execute(text(f"UPDATE {table} SET {set_clause} WHERE id = :id"), params)
                updated_rows += 1
                print(f"updated {table} id={row['id']}")

    print(f"\nDone. {updated_rows} row(s) updated.")

    with engine.connect() as conn:
        remaining = conn.execute(
            text(
                """
                SELECT 'news' AS src, id::text, slug FROM news_articles
                WHERE title ILIKE '%Thịnh Nhân%' OR excerpt ILIKE '%Thịnh Nhân%'
                   OR content_html ILIKE '%Thịnh Nhân%'
                UNION ALL
                SELECT 'product', id::text, slug FROM products
                WHERE title ILIKE '%Thịnh Nhân%' OR short_desc ILIKE '%Thịnh Nhân%'
                   OR description ILIKE '%Thịnh Nhân%' OR content_html ILIKE '%Thịnh Nhân%'
                   OR highlights ILIKE '%Thịnh Nhân%'
                """
            )
        ).fetchall()
        if remaining:
            print("WARNING: still contains 'Thịnh Nhân':")
            for row in remaining:
                print(" ", row)
        else:
            print("Verified: no 'Thịnh Nhân' left in news/products text fields.")


if __name__ == "__main__":
    migrate()

"""Lightweight Vercel build step (validates app imports before deploy)."""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def main() -> None:
    required = [
        Path("app/main.py"),
        Path("templates/Trangchu.html"),
        Path("public/css/responsive.css"),
        Path("requirements.txt"),
    ]
    missing = [str(path) for path in required if not path.exists()]
    if missing:
        raise SystemExit(f"Missing deploy files: {', '.join(missing)}")

    from app.main import app  # noqa: F401

    print("Kingnest build check OK")


if __name__ == "__main__":
    main()

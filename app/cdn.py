import json
import os
from functools import lru_cache
from pathlib import Path
from urllib.parse import unquote

from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "cloudinary_manifest.json"


class CdnSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    cloudinary_cloud_name: str = "ln22f4im"
    cloudinary_folder: str = "Kingnest"
    cdn_base_url: str = ""


@lru_cache
def get_cdn_settings() -> CdnSettings:
    return CdnSettings()


@lru_cache
def get_manifest() -> dict[str, str]:
    if not MANIFEST_PATH.exists():
        return {}
    return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))


def cloudinary_base_url() -> str:
    settings = get_cdn_settings()
    if settings.cdn_base_url.strip():
        return settings.cdn_base_url.rstrip("/")
    cloud = settings.cloudinary_cloud_name.strip() or "ln22f4im"
    folder = settings.cloudinary_folder.strip() or "Kingnest"
    return f"https://res.cloudinary.com/{cloud}/image/upload/{folder}"


def optimize_cloudinary_url(url: str, width: int = 1200) -> str:
    """Apply Cloudinary delivery transforms (WebP/AVIF, compression, resize)."""
    if "res.cloudinary.com" not in url or "/image/upload/" not in url:
        return url
    marker = "/image/upload/"
    base, rest = url.split(marker, 1)
    if not rest or rest.split("/")[0].startswith(("f_", "q_", "w_", "c_")):
        return url
    transform = f"f_auto,q_auto,w_{width}"
    return f"{base}{marker}{transform}/{rest}"


def normalize_local_image_path(path: str) -> str:
    value = path.strip()
    if value.startswith("images/"):
        value = f"/{value}"
    if not value.startswith("/images/"):
        return value
    relative = unquote(value[len("/images/") :])
    return f"/images/{relative}"


def cloudinary_path_from_local(local_path: str) -> str:
    relative = unquote(local_path[len("/images/") :])
    return "/".join(part.replace(" ", "%20") for part in relative.split("/"))


def cdn_url(path: str | None, width: int = 1200) -> str:
    if not path:
        return ""
    value = path.strip()
    if not value:
        return ""
    if value.startswith(("http://", "https://", "//")):
        return optimize_cloudinary_url(value, width=width)

    manifest = get_manifest()
    local_key = normalize_local_image_path(value)
    if local_key in manifest:
        return optimize_cloudinary_url(manifest[local_key], width=width)

    if local_key.startswith("/images/"):
        return optimize_cloudinary_url(
            f"{cloudinary_base_url()}/{cloudinary_path_from_local(local_key)}",
            width=width,
        )

    return value

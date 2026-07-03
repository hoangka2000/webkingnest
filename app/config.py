import os
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


def normalize_database_url(raw: str) -> str:
    if not raw:
        return ""
    url = raw.strip()
    if url.startswith("jdbc:postgresql://"):
        url = "postgresql://" + url[len("jdbc:postgresql://") :]
    if url.startswith("postgres://"):
        url = "postgresql://" + url[len("postgres://") :]
    return url


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "yen-sao-kingnest"
    checkout_base_url: str = "http://localhost:8080"

    database_url: str = ""
    db_url: str = ""
    db_username: str = "postgres"
    db_password: str = "postgres"

    mail_username: str = ""
    mail_password: str = ""
    mail_notify_to: str = ""
    mail_from_name: str = "Kingnest Đơn Hàng"
    site_url: str = ""

    def resolved_database_url(self) -> str:
        for candidate in (self.database_url, self.db_url, os.getenv("DATABASE_URL", "")):
            normalized = normalize_database_url(candidate)
            if normalized:
                return normalized
        return (
            f"postgresql://{self.db_username}:{self.db_password}"
            "@localhost:5432/kingnest"
        )

    def resolved_checkout_base_url(self) -> str:
        vercel_url = os.getenv("VERCEL_URL", "").strip()
        if vercel_url:
            return f"https://{vercel_url}"
        render_url = os.getenv("RENDER_EXTERNAL_URL", "").strip()
        if render_url:
            return render_url.rstrip("/")
        app_url = os.getenv("APP_CHECKOUT_BASE_URL", "").strip()
        if app_url:
            return app_url.rstrip("/")
        return self.checkout_base_url.rstrip("/")

    def resolved_site_url(self) -> str:
        for candidate in (self.site_url, os.getenv("SITE_URL", "")):
            value = candidate.strip()
            if value:
                return value.rstrip("/")
        return self.resolved_checkout_base_url()


@lru_cache
def get_settings() -> Settings:
    return Settings()

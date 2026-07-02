import json
import os
from contextlib import contextmanager
from typing import Any, Generator

from sqlalchemy import (
    BigInteger,
    Boolean,
    Column,
    DateTime,
    Integer,
    String,
    Text,
    create_engine,
    text,
)
from sqlalchemy.orm import Session, declarative_base, sessionmaker
from sqlalchemy.pool import NullPool

from app.config import get_settings

Base = declarative_base()

_engine = None
_SessionLocal = None


def _create_engine():
    url = get_settings().resolved_database_url()
    kwargs: dict[str, Any] = {"pool_pre_ping": True}
    if os.getenv("VERCEL") == "1":
        kwargs["poolclass"] = NullPool
    return create_engine(url, **kwargs)


def get_engine():
    global _engine
    if _engine is None:
        _engine = _create_engine()
    return _engine


def get_session_factory():
    global _SessionLocal
    if _SessionLocal is None:
        _SessionLocal = sessionmaker(bind=get_engine(), autoflush=False, autocommit=False)
    return _SessionLocal


class Product(Base):
    __tablename__ = "products"

    id = Column(BigInteger, primary_key=True)
    slug = Column(String, nullable=False, unique=True)
    title = Column(String, nullable=False)
    short_desc = Column("short_desc", String(1000))
    category = Column(String)
    price = Column(BigInteger, nullable=False)
    image = Column(String, nullable=False)
    gallery = Column(Text)
    benefits = Column(Text)
    usage = Column(Text)
    specs = Column(Text)
    description = Column(Text)
    highlights = Column(Text)
    product_type = Column("product_type", String)
    need = Column(Text)
    status = Column(Text)
    badge = Column(String)
    content_html = Column("content_html", Text)


class NewsArticle(Base):
    __tablename__ = "news_articles"

    id = Column(BigInteger, primary_key=True)
    slug = Column(String, nullable=False, unique=True)
    title = Column(String, nullable=False)
    category = Column(String, nullable=False)
    excerpt = Column(String(1000))
    image = Column(String, nullable=False)
    published_year = Column("published_year", String, default="2026")
    content_html = Column("content_html", Text, nullable=False)
    sort_order = Column("sort_order", Integer, default=0)


class Order(Base):
    __tablename__ = "orders"

    id = Column(BigInteger, primary_key=True)
    order_code = Column("order_code", String(64), nullable=False, unique=True)
    customer_name = Column("customer_name", String, nullable=False)
    customer_email = Column("customer_email", String, nullable=False)
    customer_phone = Column("customer_phone", String, nullable=False)
    customer_address = Column("customer_address", String(500), nullable=False)
    customer_note = Column("customer_note", String(1000))
    items_json = Column("items_json", Text, nullable=False)
    coupon = Column(String)
    subtotal = Column(BigInteger, nullable=False)
    discount = Column(BigInteger, nullable=False)
    total = Column(BigInteger, nullable=False)
    status = Column(String(32), nullable=False)
    payment_method = Column("payment_method", String(16), nullable=False)
    email_sent = Column("email_sent", Boolean, nullable=False, default=False)
    created_at = Column("created_at", DateTime, nullable=False)


def parse_json_field(value: str | None, fallback: Any):
    if not value:
        return fallback
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return fallback


@contextmanager
def get_db() -> Generator[Session, None, None]:
    session_factory = get_session_factory()
    db = session_factory()
    try:
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


def check_database_connection() -> None:
    with get_engine().connect() as conn:
        conn.execute(text("SELECT 1"))

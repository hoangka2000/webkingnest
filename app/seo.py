from __future__ import annotations

import os
from datetime import datetime, timezone
from typing import Any
from urllib.parse import quote
from xml.etree.ElementTree import Element, SubElement, tostring

from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import NewsArticle, Product
from app.services import public_product_slug

SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9"
SCHEMA_CONTEXT = "https://schema.org"
BRAND_NAME = "Kingnest - Yến Sào Khánh Hoà"
BRAND_SHORT = "Kingnest"
BRAND_DISPLAY = "Yến Sào Khánh Hoà"
PRIMARY_KEYWORD = "yến sào khánh hoà"
BRAND_TAGLINE = (
    f"{BRAND_DISPLAY} chuyên yến chưng sẵn, yến tinh chế, yến thô và hộp quà cao cấp "
    "tại Cam Ranh, Khánh Hòa. Giao hàng toàn quốc, tư vấn miễn phí."
)
PHONE = "+84564175414"
EMAIL = "nguyendanghoang551@gmail.com"
ADDRESS = {
    "streetAddress": "65 Lê Trọng Tấn, Phường Bắc Cam Ranh",
    "addressLocality": "Cam Ranh",
    "addressRegion": "Khánh Hòa",
    "addressCountry": "VN",
}
OG_IMAGE = (
    "https://res.cloudinary.com/ln22f4im/image/upload/f_auto,q_auto,w_1200/"
    "v1783077806/banner_chung_tmiyyw.png"
)
DEFAULT_KEYWORDS = (
    f"{PRIMARY_KEYWORD}, Yến Sào Khánh Hoà, yến sào Cam Ranh, yến chưng khánh hoà, "
    "mua yến sào khánh hoà, yến sào chính hãng, yến chưng sẵn, yến tinh chế, "
    "hộp quà yến sào, Kingnest"
)

PAGE_SEO: dict[str, dict[str, str]] = {
    "Trangchu.html": {
        "title": f"Yến sào Khánh Hoà chính hãng | Kingnest - Mua yến chưng Cam Ranh",
        "description": (
            f"Mua {PRIMARY_KEYWORD} chính hãng tại Kingnest - {BRAND_DISPLAY}. "
            f"{BRAND_TAGLINE}"
        ),
        "keywords": DEFAULT_KEYWORDS,
        "path": "/",
        "og_type": "website",
    },
    "San_pham.html": {
        "title": f"Sản phẩm {PRIMARY_KEYWORD} | Yến chưng, yến tinh chế Kingnest Cam Ranh",
        "description": (
            f"Danh sách sản phẩm {PRIMARY_KEYWORD} tại Kingnest: yến chưng sẵn, yến tinh chế, "
            "yến thô, hộp quà cao cấp. Giá tốt, chất lượng, giao nhanh toàn quốc."
        ),
        "keywords": (
            f"{PRIMARY_KEYWORD}, sản phẩm yến sào khánh hoà, yến chưng Kingnest, "
            "yến tinh chế Cam Ranh, hộp quà yến sào"
        ),
        "path": "/san-pham",
        "og_type": "website",
    },
    "gioi_thieu.html": {
        "title": f"Giới thiệu {BRAND_DISPLAY} | Thương hiệu yến sào Kingnest Cam Ranh",
        "description": (
            f"Tìm hiểu {BRAND_DISPLAY} - Kingnest, thương hiệu {PRIMARY_KEYWORD} uy tín "
            "tại Cam Ranh, Khánh Hòa. Yến sạch, chọn lọc kỹ, cam kết chất lượng."
        ),
        "keywords": (
            f"giới thiệu {PRIMARY_KEYWORD}, {BRAND_DISPLAY}, thương hiệu yến sào Cam Ranh, Kingnest"
        ),
        "path": "/gioi-thieu",
        "og_type": "website",
    },
    "Tin_tuc.html": {
        "title": f"Tin tức {PRIMARY_KEYWORD} | Kiến thức chọn yến Kingnest",
        "description": (
            f"Tin tức và kiến thức về {PRIMARY_KEYWORD}: cách chế biến, phân biệt yến thật giả, "
            f"lợi ích sức khỏe từ {BRAND_DISPLAY} - Kingnest."
        ),
        "keywords": (
            f"tin tức {PRIMARY_KEYWORD}, kiến thức yến sào, cách chế biến yến sào, Kingnest"
        ),
        "path": "/tin-tuc",
        "og_type": "website",
    },
    "Tin_tuc_chi_tiet.html": {
        "title": f"Tin tức {PRIMARY_KEYWORD} | {BRAND_DISPLAY} Kingnest",
        "description": (
            f"Đọc bài viết về {PRIMARY_KEYWORD}, sức khỏe và thương hiệu "
            f"Kingnest - {BRAND_DISPLAY} tại Cam Ranh, Khánh Hòa."
        ),
        "keywords": f"tin tức {PRIMARY_KEYWORD}, bài viết yến sào, {BRAND_DISPLAY}, Kingnest",
        "path": "/tin-tuc",
        "og_type": "article",
    },
    "Chi_tiet_san_pham.html": {
        "title": f"Mua {PRIMARY_KEYWORD} online | Sản phẩm yến sào Kingnest chính hãng",
        "description": (
            f"Chi tiết sản phẩm {PRIMARY_KEYWORD} tại Kingnest: giá, thành phần, công dụng, "
            "hướng dẫn sử dụng và đặt hàng online giao toàn quốc."
        ),
        "keywords": (
            f"mua {PRIMARY_KEYWORD}, yến chưng khánh hoà, sản phẩm yến sào Kingnest, {BRAND_DISPLAY}"
        ),
        "path": "/san-pham",
        "og_type": "product",
    },
    "Lien_he.html": {
        "title": f"Liên hệ {PRIMARY_KEYWORD} | Kingnest Cam Ranh - Hotline 0564175414",
        "description": (
            f"Liên hệ mua {PRIMARY_KEYWORD} tại Kingnest - {BRAND_DISPLAY}. "
            "Tư vấn sản phẩm, báo giá sỉ, đặt hàng yến sào. Hotline 0564175414, Cam Ranh, Khánh Hòa."
        ),
        "keywords": (
            f"liên hệ {PRIMARY_KEYWORD}, tư vấn yến sào Cam Ranh, mua yến sào khánh hoà, Kingnest"
        ),
        "path": "/lien-he",
        "og_type": "website",
    },
    "Gio_hang.html": {
        "title": f"Giỏ hàng | Mua {PRIMARY_KEYWORD} online - Kingnest",
        "description": f"Giỏ hàng đặt mua {PRIMARY_KEYWORD} Kingnest online, giao toàn quốc.",
        "keywords": f"giỏ hàng yến sào, đặt {PRIMARY_KEYWORD} online, Kingnest",
        "path": "/gio-hang",
        "og_type": "website",
    },
}

BREADCRUMBS: dict[str, list[tuple[str, str]]] = {
    "Trangchu.html": [("Trang chủ", "/")],
    "San_pham.html": [("Trang chủ", "/"), ("Sản phẩm", "/san-pham")],
    "gioi_thieu.html": [("Trang chủ", "/"), ("Giới thiệu", "/gioi-thieu")],
    "Tin_tuc.html": [("Trang chủ", "/"), ("Tin tức", "/tin-tuc")],
    "Lien_he.html": [("Trang chủ", "/"), ("Liên hệ", "/lien-he")],
    "Gio_hang.html": [("Trang chủ", "/"), ("Giỏ hàng", "/gio-hang")],
}

STATIC_SITEMAP_PATHS: list[tuple[str, str, str]] = [
    ("/", "daily", "1.0"),
    ("/san-pham", "weekly", "0.9"),
    ("/gioi-thieu", "monthly", "0.8"),
    ("/tin-tuc", "weekly", "0.8"),
    ("/lien-he", "monthly", "0.7"),
]


def site_base_url() -> str:
    return get_settings().resolved_site_url()


def google_site_verification() -> str:
    value = get_settings().google_site_verification.strip()
    if value:
        return value
    return os.getenv("GOOGLE_SITE_VERIFICATION", "").strip()


def _base_seo(meta: dict[str, str], canonical_path: str | None = None) -> dict[str, Any]:
    base = site_base_url()
    path = canonical_path or meta.get("path", "/")
    canonical = f"{base}{path}"
    return {
        "title": meta["title"],
        "description": meta["description"],
        "keywords": meta.get("keywords", DEFAULT_KEYWORDS),
        "canonical": canonical,
        "site_url": base,
        "og_image": OG_IMAGE,
        "og_type": meta.get("og_type", "website"),
        "google_site_verification": google_site_verification(),
    }


def seo_context_for(template_name: str) -> dict[str, Any]:
    meta = PAGE_SEO.get(
        template_name,
        {
            "title": f"{BRAND_NAME}",
            "description": (
                f"Mua {PRIMARY_KEYWORD} chính hãng tại Kingnest - {BRAND_DISPLAY}, Cam Ranh, Khánh Hòa."
            ),
            "keywords": DEFAULT_KEYWORDS,
            "path": "/",
            "og_type": "website",
        },
    )
    return _base_seo(meta)


def product_seo_context(product: dict[str, Any], slug: str) -> dict[str, Any]:
    title = f"{product['title']} | {PRIMARY_KEYWORD} Kingnest chính hãng"
    desc = (product.get("desc") or product.get("title") or "").strip()
    if PRIMARY_KEYWORD not in desc.lower():
        desc = f"Mua {product['title']} - {PRIMARY_KEYWORD} Kingnest. {desc}"
    if len(desc) > 155:
        desc = desc[:152] + "..."
    if not desc:
        desc = f"Mua {product['title']} - {PRIMARY_KEYWORD} chất lượng, giao toàn quốc."
    path = f"/chi-tiet-san-pham?slug={quote(slug, safe='')}"
    meta = {
        "title": title,
        "description": desc,
        "keywords": (
            f"{product['title']}, {PRIMARY_KEYWORD}, {BRAND_DISPLAY}, "
            "mua yến sào, Kingnest, yến chưng"
        ),
        "path": path,
        "og_type": "product",
    }
    ctx = _base_seo(meta, path)
    image = product.get("image") or OG_IMAGE
    ctx["og_image"] = image
    return ctx


def article_seo_context(article: dict[str, Any], slug: str) -> dict[str, Any]:
    title = f"{article['title']} | {PRIMARY_KEYWORD} Kingnest"
    desc = (article.get("desc") or article.get("title") or "").strip()
    if PRIMARY_KEYWORD not in desc.lower():
        desc = f"{desc} | {BRAND_DISPLAY} - Kingnest."
    if len(desc) > 155:
        desc = desc[:152] + "..."
    path = f"/tin-tuc-chi-tiet?slug={quote(slug, safe='')}"
    meta = {
        "title": title,
        "description": desc or f"Bài viết về {PRIMARY_KEYWORD} từ Kingnest - {article['title']}.",
        "keywords": (
            f"{article.get('category', 'tin tức')}, {PRIMARY_KEYWORD}, "
            f"{BRAND_DISPLAY}, Kingnest, kiến thức yến sào"
        ),
        "path": path,
        "og_type": "article",
    }
    ctx = _base_seo(meta, path)
    image = article.get("image") or OG_IMAGE
    ctx["og_image"] = image
    return ctx


def organization_schema(base: str) -> dict[str, Any]:
    return {
        "@context": SCHEMA_CONTEXT,
        "@type": "Organization",
        "name": BRAND_NAME,
        "alternateName": [BRAND_SHORT, BRAND_DISPLAY, PRIMARY_KEYWORD],
        "description": BRAND_TAGLINE,
        "url": base,
        "logo": OG_IMAGE,
        "image": OG_IMAGE,
        "email": EMAIL,
        "telephone": PHONE,
        "keywords": PRIMARY_KEYWORD,
        "address": {
            "@type": "PostalAddress",
            **ADDRESS,
        },
        "sameAs": [
            "https://www.facebook.com/share/194mLCUAQU/?mibextid=wwXIfr",
        ],
    }


def website_schema(base: str) -> dict[str, Any]:
    return {
        "@context": SCHEMA_CONTEXT,
        "@type": "WebSite",
        "name": BRAND_NAME,
        "alternateName": [BRAND_DISPLAY, PRIMARY_KEYWORD],
        "description": (
            f"Website chính thức mua {PRIMARY_KEYWORD} - Kingnest tại Cam Ranh, Khánh Hòa."
        ),
        "url": base,
        "inLanguage": "vi-VN",
        "potentialAction": {
            "@type": "SearchAction",
            "target": f"{base}/san-pham?q={{search_term_string}}",
            "query-input": "required name=search_term_string",
        },
    }


def local_business_schema(base: str) -> dict[str, Any]:
    return {
        "@context": SCHEMA_CONTEXT,
        "@type": "Store",
        "name": BRAND_NAME,
        "alternateName": [BRAND_DISPLAY, PRIMARY_KEYWORD],
        "description": (
            f"Cửa hàng {PRIMARY_KEYWORD} Kingnest tại Cam Ranh, Khánh Hòa. "
            "Yến chưng sẵn, yến tinh chế, hộp quà cao cấp."
        ),
        "image": OG_IMAGE,
        "url": base,
        "telephone": PHONE,
        "email": EMAIL,
        "priceRange": "$$",
        "address": {
            "@type": "PostalAddress",
            **ADDRESS,
        },
        "openingHoursSpecification": {
            "@type": "OpeningHoursSpecification",
            "dayOfWeek": [
                "Monday",
                "Tuesday",
                "Wednesday",
                "Thursday",
                "Friday",
                "Saturday",
                "Sunday",
            ],
            "opens": "08:00",
            "closes": "22:00",
        },
    }


def breadcrumb_schema(base: str, items: list[tuple[str, str]]) -> dict[str, Any]:
    return {
        "@context": SCHEMA_CONTEXT,
        "@type": "BreadcrumbList",
        "itemListElement": [
            {
                "@type": "ListItem",
                "position": index,
                "name": name,
                "item": f"{base}{path}",
            }
            for index, (name, path) in enumerate(items, start=1)
        ],
    }


def product_schema(product: dict[str, Any], slug: str, base: str) -> dict[str, Any]:
    return {
        "@context": SCHEMA_CONTEXT,
        "@type": "Product",
        "name": product["title"],
        "description": product.get("desc") or product["title"],
        "image": product.get("image") or OG_IMAGE,
        "sku": str(product.get("id", slug)),
        "brand": {"@type": "Brand", "name": BRAND_DISPLAY},
        "offers": {
            "@type": "Offer",
            "url": f"{base}/chi-tiet-san-pham?slug={quote(slug, safe='')}",
            "priceCurrency": "VND",
            "price": str(product.get("price", 0)),
            "availability": "https://schema.org/InStock",
            "seller": {"@type": "Organization", "name": BRAND_NAME},
        },
    }


def article_schema(article: dict[str, Any], slug: str, base: str) -> dict[str, Any]:
    return {
        "@context": SCHEMA_CONTEXT,
        "@type": "Article",
        "headline": article["title"],
        "description": article.get("desc") or article["title"],
        "image": article.get("image") or OG_IMAGE,
        "author": {"@type": "Organization", "name": BRAND_NAME},
        "publisher": {
            "@type": "Organization",
            "name": BRAND_NAME,
            "logo": {"@type": "ImageObject", "url": OG_IMAGE},
        },
        "mainEntityOfPage": f"{base}/tin-tuc-chi-tiet?slug={quote(slug, safe='')}",
        "inLanguage": "vi-VN",
        "articleSection": article.get("category", "Tin tức"),
    }


def json_ld_blocks_for_page(
    template_name: str,
    *,
    product: dict[str, Any] | None = None,
    product_slug: str = "",
    article: dict[str, Any] | None = None,
    article_slug: str = "",
) -> list[dict[str, Any]]:
    base = site_base_url()
    blocks: list[dict[str, Any]] = [organization_schema(base)]

    if template_name == "Trangchu.html":
        blocks.extend([website_schema(base), local_business_schema(base)])
    elif template_name == "Lien_he.html":
        blocks.append(local_business_schema(base))

    crumbs = list(BREADCRUMBS.get(template_name, [("Trang chủ", "/")]))
    if product and product_slug:
        crumbs = [
            ("Trang chủ", "/"),
            ("Sản phẩm", "/san-pham"),
            (product["title"], f"/chi-tiet-san-pham?slug={quote(product_slug, safe='')}"),
        ]
        blocks.append(product_schema(product, product_slug, base))
    elif article and article_slug:
        crumbs = [
            ("Trang chủ", "/"),
            ("Tin tức", "/tin-tuc"),
            (article["title"], f"/tin-tuc-chi-tiet?slug={quote(article_slug, safe='')}"),
        ]
        blocks.append(article_schema(article, article_slug, base))

    if len(crumbs) > 1 or template_name != "Trangchu.html":
        blocks.append(breadcrumb_schema(base, crumbs))

    return blocks


def robots_txt() -> str:
    base = site_base_url()
    return (
        "User-agent: *\n"
        "Allow: /\n"
        "Disallow: /api/\n"
        "Disallow: /checkout\n"
        "Disallow: /health\n"
        "Disallow: /gio-hang\n"
        f"Sitemap: {base}/sitemap.xml\n"
    )


def _sitemap_lastmod() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%d")


def _add_url(urlset: Element, loc: str, changefreq: str, priority: str) -> None:
    url = SubElement(urlset, "url")
    SubElement(url, "loc").text = loc
    SubElement(url, "lastmod").text = _sitemap_lastmod()
    SubElement(url, "changefreq").text = changefreq
    SubElement(url, "priority").text = priority


def build_sitemap_xml(db: Session) -> str:
    base = site_base_url()
    urlset = Element("urlset", xmlns=SITEMAP_NS)

    for path, changefreq, priority in STATIC_SITEMAP_PATHS:
        _add_url(urlset, f"{base}{path}", changefreq, priority)

    for product in db.query(Product).order_by(Product.id.asc()).all():
        slug = quote(public_product_slug(product.slug), safe="")
        _add_url(
            urlset,
            f"{base}/chi-tiet-san-pham?slug={slug}",
            "weekly",
            "0.8",
        )

    for article in db.query(NewsArticle).order_by(NewsArticle.sort_order.asc()).all():
        slug = quote(article.slug, safe="")
        _add_url(
            urlset,
            f"{base}/tin-tuc-chi-tiet?slug={slug}",
            "monthly",
            "0.7",
        )

    return tostring(urlset, encoding="unicode", xml_declaration=False)

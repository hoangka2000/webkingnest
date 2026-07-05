import json
import logging
import random
import smtplib
from datetime import datetime
from email.mime.text import MIMEText
from typing import Any
from urllib.parse import quote, urlencode

from sqlalchemy.orm import Session

from app.cdn import cdn_url
from app.config import get_settings
from app.database import NewsArticle, Order, Product, parse_json_field
from app.schemas import CreateOrderRequest

logger = logging.getLogger(__name__)

COUPON_DISCOUNTS = {
    "KINGNEST10": 0.10,
    "FACEBOOK5": 0.05,
}

YEN_CHUNG_PACK_PRICES = {
    "10hu": 350000,
    "30hu": 900000,
}

THUNG_PACK_PRICES = {
    "40hu": 1200000,
    "60hu": 1700000,
}

ARTICLE_SLUG_REDIRECTS: dict[str, str] = {
    "tam-nhin-thuong-hieu-yen-sao-an-thinh-nhan": "tam-nhin-thuong-hieu-yen-sao-khanh-hoa",
    "y-nghia-thuong-hieu-kingnest-an-thinh-nhan": "y-nghia-thuong-hieu-kingnest-khanh-hoa",
    "su-menh-cua-yen-sao-an-thinh-nhan": "su-menh-cua-yen-sao-khanh-hoa",
    "vi-sao-nen-lua-chon-yen-sao-kingnest-an-thinh-nhan": "vi-sao-nen-lua-chon-yen-sao-kingnest-khanh-hoa",
    "thong-diep-thuong-hieu-yen-sao-an-thinh-nhan": "thong-diep-thuong-hieu-yen-sao-khanh-hoa",
}

NEWS_IMAGE_KEYS = {
    "cach-che-bien-yen-sao-dung-cach": "che_bien",
    "tam-nhin-thuong-hieu-yen-sao-khanh-hoa": "tam_nhin",
    "y-nghia-thuong-hieu-kingnest-khanh-hoa": "y_nghia",
    "su-menh-cua-yen-sao-khanh-hoa": "su_menh",
    "vi-sao-nen-lua-chon-yen-sao-kingnest-khanh-hoa": "chon_Kingnest",
    "thong-diep-thuong-hieu-yen-sao-khanh-hoa": "thong_diep",
    "tac-dung-lam-dep-da-tu-yen-sao": "lam_dep",
    "cach-bao-quan-yen-sao-sau-khi-chung": "bao_quan",
    "loi-ich-cua-yen-cho-tre-nho": "tre_em",
    "cach-phan-biet-yen-sao-that-gia": "phan_biet",
    "cong-dung-tuyet-voi-cua-yen-sao": "cong_dung",
}


def normalize_product_type(product_type: str | None) -> str:
    if not product_type:
        return ""
    if product_type in {"yen-vien", "yen-rut-long"}:
        return "yen-tinh-che"
    return product_type


def public_product_slug(slug: str | None) -> str:
    if not slug:
        return ""
    if slug.startswith("yen-chung-"):
        return slug.replace("-6-hu", "-hu", 1)
    return slug


def slug_lookup_candidates(slug: str) -> list[str]:
    if not slug:
        return []

    candidates: list[str] = []
    seen: set[str] = set()

    def add(value: str) -> None:
        if value and value not in seen:
            seen.add(value)
            candidates.append(value)

    add(slug)
    add(public_product_slug(slug))
    if slug.startswith("yen-chung-") and "-6-hu" not in slug and "-hu" in slug:
        add(slug.replace("-hu", "-6-hu", 1))
    if "-6-hu" in slug:
        add(slug.replace("-6-hu", "-hu", 1))
    return candidates


def parse_facebook_catalog_id(catalog_id: str) -> tuple[str, str]:
    value = (catalog_id or "").strip()
    if not value:
        return "", ""
    if value.isdigit():
        return value, ""
    prefix, separator, suffix = value.partition("-")
    if separator and prefix.isdigit():
        return prefix, suffix
    return value, ""


def facebook_catalog_id(product_id: int | str, variant_id: str = "") -> str:
    base = str(product_id)
    variant = (variant_id or "").strip()
    return f"{base}-{variant}" if variant else base


def supports_yen_chung_packs(product: Product) -> bool:
    product_type = normalize_product_type(product.product_type)
    return product_type == "yen-chung" and product.slug != "hop-qua-yen-chung-6-hu"


def facebook_purchase_options(product: Product) -> list[dict[str, Any]]:
    base_price = int(product.price)
    if supports_yen_chung_packs(product):
        return [
            {
                "id": facebook_catalog_id(product.id, variant_id),
                "variantId": variant_id,
                "label": label,
                "title": f"{product.title} ({label})",
                "price": YEN_CHUNG_PACK_PRICES[variant_id],
            }
            for variant_id, label in (("10hu", "10 hũ"), ("30hu", "30 hũ"))
        ]
    return [
        {
            "id": facebook_catalog_id(product.id),
            "variantId": "",
            "label": "",
            "title": product.title,
            "price": base_price,
        }
    ]


def _variant_id_from_cart_id(cart_id: str) -> str:
    _product_key, variant_id = parse_facebook_catalog_id(cart_id)
    return variant_id


def _product_key_from_order_item(item_id: str, product_id: str | None) -> str:
    if product_id and product_id.strip():
        return product_id.strip()
    product_key, _variant_id = parse_facebook_catalog_id(item_id)
    return product_key or item_id


def _variant_label(variant_id: str) -> str:
    return {
        "10hu": "10 hũ",
        "30hu": "30 hũ",
        "40hu": "40 hũ",
        "60hu": "60 hũ",
        "100g": "100g",
        "50g": "50g",
    }.get(variant_id, "")


def _variant_price(product: Product, base_price: int, variant_id: str) -> int:
    product_type = normalize_product_type(product.product_type)
    if product_type == "yen-chung" and product.slug != "hop-qua-yen-chung-6-hu":
        return YEN_CHUNG_PACK_PRICES.get(variant_id, base_price)
    if product.slug == "thung-yen-gia-si":
        return THUNG_PACK_PRICES.get(variant_id, base_price)
    if product_type == "yen-tinh-che":
        if variant_id == "50g":
            return round(base_price * 0.5)
        if variant_id == "100g":
            return base_price
    return base_price


def _product_images(product: Product) -> tuple[str, list[str]]:
    gallery = parse_json_field(product.gallery, [])
    if not isinstance(gallery, list):
        gallery = []
    image = product.image or (gallery[0] if gallery else "")
    if not gallery and image:
        gallery = [image]
    return cdn_url(image), [cdn_url(item) for item in gallery]


def product_listing_map(product: Product) -> dict[str, Any]:
    image, _ = _product_images(product)
    purchase_options = facebook_purchase_options(product)
    return {
        "id": product.id,
        "catalogId": purchase_options[0]["id"],
        "slug": public_product_slug(product.slug),
        "title": product.title,
        "desc": product.short_desc,
        "price": product.price,
        "image": image,
        "type": normalize_product_type(product.product_type),
        "need": parse_json_field(product.need, []),
        "status": parse_json_field(product.status, []),
        "badge": product.badge,
        "purchaseOptions": purchase_options,
    }


def product_detail_map(product: Product) -> dict[str, Any]:
    image, gallery = _product_images(product)
    data = product_listing_map(product)
    data.update(
        {
            "category": product.category,
            "gallery": gallery,
            "benefits": parse_json_field(product.benefits, []),
            "usage": parse_json_field(product.usage, []),
            "specs": parse_json_field(product.specs, {}),
            "description": parse_json_field(product.description, []),
            "highlights": parse_json_field(product.highlights, []),
            "contentHtml": product.content_html,
            "image": image,
        }
    )
    return data


def find_product(db: Session, id_or_slug: str) -> Product | None:
    product_key, _variant_id = parse_facebook_catalog_id(id_or_slug)
    lookup = product_key or id_or_slug
    if lookup.isdigit():
        return db.get(Product, int(lookup))
    for candidate in slug_lookup_candidates(lookup):
        product = db.query(Product).filter(Product.slug == candidate).first()
        if product:
            return product
    return None


def list_products(db: Session) -> list[dict[str, Any]]:
    return [product_listing_map(p) for p in db.query(Product).all()]


def get_product_detail(db: Session, id_or_slug: str) -> dict[str, Any] | None:
    product = find_product(db, id_or_slug)
    return product_detail_map(product) if product else None


def get_related_products(db: Session, product: Product, limit: int = 4) -> list[dict[str, Any]]:
    current_type = normalize_product_type(product.product_type)
    others = db.query(Product).filter(Product.id != product.id).all()
    if not current_type:
        return [product_listing_map(item) for item in others[:limit]]
    same_type = [
        item
        for item in others
        if normalize_product_type(item.product_type) == current_type
    ]
    return [product_listing_map(item) for item in same_type[:limit]]


def canonical_article_slug(slug: str) -> str:
    return ARTICLE_SLUG_REDIRECTS.get(slug, slug)


def news_image(slug: str, detail: bool = False) -> str:
    key = NEWS_IMAGE_KEYS.get(slug, slug)
    folder = "chi_tiet" if detail else "ds_tintuc"
    return cdn_url(f"/images/tin_tuc/{folder}/{key}.png")


def news_summary(article: NewsArticle) -> dict[str, Any]:
    return {
        "slug": article.slug,
        "title": article.title,
        "category": article.category,
        "desc": article.excerpt,
        "image": news_image(article.slug),
        "date": article.published_year,
    }


def news_detail(article: NewsArticle) -> dict[str, Any]:
    data = news_summary(article)
    data.update(
        {
            "image": news_image(article.slug, detail=True),
            "success": True,
            "contentHtml": article.content_html,
        }
    )
    return data


def list_news(db: Session) -> list[dict[str, Any]]:
    articles = db.query(NewsArticle).order_by(NewsArticle.sort_order.asc()).all()
    return [news_summary(a) for a in articles]


def get_news_detail(db: Session, slug: str) -> dict[str, Any] | None:
    slug = canonical_article_slug(slug)
    article = db.query(NewsArticle).filter(NewsArticle.slug == slug).first()
    return news_detail(article) if article else None


def normalize_coupon(coupon: str | None) -> str | None:
    if not coupon or not coupon.strip():
        return None
    return coupon.strip().upper()


def calculate_discount(subtotal: int, coupon: str | None) -> int:
    if not coupon or subtotal <= 0:
        return 0
    rate = COUPON_DISCOUNTS.get(coupon)
    if rate is None:
        return 0
    return round(subtotal * rate)


def parse_product_quantities(products_param: str | None) -> dict[str, int]:
    result: dict[str, int] = {}
    if not products_param:
        return result
    for entry in products_param.split(","):
        entry = entry.strip()
        if not entry or ":" not in entry:
            continue
        product_id, qty_raw = entry.split(":", 1)
        product_id = product_id.strip()
        try:
            qty = int(qty_raw.strip())
        except ValueError:
            continue
        if product_id and qty > 0:
            result[product_id] = result.get(product_id, 0) + qty
    return result


def build_checkout_page_url(products_param: str, coupon: str | None) -> str:
    base = get_settings().resolved_checkout_base_url()
    params = {"step": "2", "products": products_param}
    if coupon and coupon.strip():
        params["coupon"] = coupon.strip()
    return f"{base}/gio-hang?{urlencode(params, quote_via=quote)}"


def build_checkout(db: Session, products_param: str | None, coupon: str | None) -> dict[str, Any]:
    requested = parse_product_quantities(products_param)
    line_items: list[dict[str, Any]] = []
    missing: list[str] = []
    subtotal = 0

    for product_key, quantity in requested.items():
        product_entity = find_product(db, product_key)
        if not product_entity:
            missing.append(product_key)
            continue
        product = product_detail_map(product_entity)
        variant_id = _variant_id_from_cart_id(product_key)
        base_price = int(product.get("price") or 0)
        price = _variant_price(product_entity, base_price, variant_id)
        variant_label = _variant_label(variant_id)
        title = product["title"]
        if variant_label:
            title = f"{title} ({variant_label})"
        line_total = price * quantity
        subtotal += line_total
        catalog_id = facebook_catalog_id(product["id"], variant_id)
        line_items.append(
            {
                "id": catalog_id,
                "productId": str(product["id"]),
                "variantId": variant_id,
                "variantLabel": variant_label,
                "slug": product["slug"],
                "title": title,
                "desc": product.get("desc"),
                "price": price,
                "image": product.get("image"),
                "quantity": quantity,
                "lineTotal": line_total,
            }
        )

    normalized_coupon = normalize_coupon(coupon)
    discount = calculate_discount(subtotal, normalized_coupon)
    total = max(0, subtotal - discount)

    return {
        "success": bool(line_items) and not missing,
        "products": line_items,
        "productQuantities": requested,
        "coupon": normalized_coupon or "No coupon applied",
        "couponApplied": bool(normalized_coupon and discount > 0),
        "subtotal": subtotal,
        "discount": discount,
        "shipping": 0,
        "total": total,
        "currency": "VND",
        "missingProducts": missing,
        "checkoutUrl": build_checkout_page_url(products_param or "", coupon),
    }


def generate_order_code(db: Session) -> str:
    date_part = datetime.now().strftime("%y%m%d")
    for _ in range(10):
        suffix = random.randint(1000, 9999)
        code = f"ORC-{date_part}-{suffix}"
        exists = db.query(Order).filter(Order.order_code == code).first()
        if not exists:
            return code
    raise RuntimeError("Không thể tạo mã đơn hàng")


def resolve_order_code(db: Session, requested: str | None) -> str:
    if requested and requested.strip():
        code = requested.strip()
        exists = db.query(Order).filter(Order.order_code == code).first()
        if not exists:
            return code
    return generate_order_code(db)


def parse_payment_method(value: str | None) -> str:
    if not value or not value.strip():
        return "BANK_TRANSFER"
    method = value.strip().upper()
    if method not in {"BANK_TRANSFER", "COD", "MOMO"}:
        raise ValueError("Phương thức thanh toán không hợp lệ")
    return method


def create_order(db: Session, request: CreateOrderRequest) -> Order:
    if not all(
        [
            request.customer_name.strip(),
            str(request.customer_email).strip(),
            request.customer_phone.strip(),
            request.customer_address.strip(),
        ]
    ):
        raise ValueError("Vui lòng nhập đầy đủ thông tin khách hàng")

    if not request.items:
        raise ValueError("Giỏ hàng trống")

    payment_method = parse_payment_method(request.payment_method)
    line_items: list[dict[str, Any]] = []
    subtotal = 0

    for item in request.items:
        if not item.id or item.quantity <= 0:
            continue
        product_key = _product_key_from_order_item(item.id, item.product_id)
        product_entity = find_product(db, product_key)
        if not product_entity:
            raise ValueError(f"Sản phẩm không tồn tại: {product_key}")
        product = product_detail_map(product_entity)
        variant_id = (item.variant_id or _variant_id_from_cart_id(item.id)).strip()
        variant_label = (item.variant_label or _variant_label(variant_id)).strip()
        base_price = int(product.get("price") or 0)
        price = _variant_price(product_entity, base_price, variant_id)
        line_total = price * item.quantity
        subtotal += line_total
        title = product["title"]
        if variant_label:
            title = f"{title} ({variant_label})"
        line_items.append(
            {
                "id": facebook_catalog_id(product["id"], variant_id),
                "productId": str(product["id"]),
                "slug": product["slug"],
                "variantId": variant_id,
                "variantLabel": variant_label,
                "title": title,
                "price": price,
                "quantity": item.quantity,
                "lineTotal": line_total,
            }
        )

    if not line_items:
        raise ValueError("Không có sản phẩm hợp lệ trong đơn hàng")

    coupon = normalize_coupon(request.coupon)
    discount = calculate_discount(subtotal, coupon)
    total = max(0, subtotal - discount)
    status = "COD_PENDING" if payment_method == "COD" else "CONFIRMED"

    order = Order(
        order_code=resolve_order_code(db, request.order_code),
        customer_name=request.customer_name.strip(),
        customer_email=str(request.customer_email).strip(),
        customer_phone=request.customer_phone.strip(),
        customer_address=request.customer_address.strip(),
        customer_note=(request.customer_note or "").strip() or None,
        coupon=coupon,
        subtotal=subtotal,
        discount=discount,
        total=total,
        payment_method=payment_method,
        status=status,
        email_sent=False,
        created_at=datetime.now(),
        items_json=json.dumps(line_items, ensure_ascii=False),
    )
    db.add(order)
    db.flush()
    send_order_email(order, line_items)
    return order


def order_response(order: Order) -> dict[str, Any]:
    return {
        "success": True,
        "orderCode": order.order_code,
        "status": order.status,
        "paymentMethod": order.payment_method,
        "emailSent": order.email_sent,
        "subtotal": order.subtotal,
        "discount": order.discount,
        "total": order.total,
        "coupon": order.coupon or "",
    }


def _payment_label(method: str) -> str:
    return {
        "COD": "Thanh toán khi nhận hàng (COD)",
        "MOMO": "MoMo",
        "BANK_TRANSFER": "Khách xác nhận đã chuyển khoản",
    }.get(method, method)


def send_order_email(order: Order, line_items: list[dict[str, Any]]) -> None:
    settings = get_settings()
    if not (settings.mail_username and settings.mail_password and settings.mail_notify_to):
        logger.warning("Bỏ qua gửi email: chưa cấu hình MAIL_USERNAME/MAIL_PASSWORD/MAIL_NOTIFY_TO")
        return

    subject_payment = {
        "COD": "COD",
        "MOMO": "MoMo",
        "BANK_TRANSFER": "Đã chuyển khoản",
    }.get(order.payment_method, order.payment_method)

    subject = f"[Kingnest] Đơn mới {order.order_code} - {subject_payment}"
    lines = [
        "Có đơn hàng mới từ website Kingnest",
        "=====================================\n",
        f"Mã đơn: {order.order_code}",
        f"Thời gian: {order.created_at.strftime('%d/%m/%Y %H:%M')}",
        f"Thanh toán: {_payment_label(order.payment_method)}",
        f"Trạng thái: {order.status}\n",
        "--- Khách hàng ---",
        f"Họ tên: {order.customer_name}",
        f"Email: {order.customer_email}",
        f"SĐT: {order.customer_phone}",
        f"Địa chỉ: {order.customer_address}",
    ]
    if order.customer_note:
        lines.append(f"Ghi chú: {order.customer_note}")
    lines.append("\n--- Sản phẩm ---")
    for item in line_items:
        lines.append(
            f"- {item.get('title', 'Sản phẩm')} x{item.get('quantity', 1)} = "
            f"{int(item.get('lineTotal', 0)):,} đ".replace(",", ".")
        )
    lines.append("")
    lines.append(f"Thành tiền: {order.subtotal:,} đ".replace(",", "."))
    if order.discount:
        coupon = f" ({order.coupon})" if order.coupon else ""
        lines.append(f"Giảm giá{coupon}: -{order.discount:,} đ".replace(",", "."))
    lines.append(f"TỔNG THANH TOÁN: {order.total:,} đ".replace(",", "."))

    message = MIMEText("\n".join(lines), "plain", "utf-8")
    message["Subject"] = subject
    message["From"] = f"{settings.mail_from_name} <{settings.mail_username}>"
    message["To"] = settings.mail_notify_to

    try:
        with smtplib.SMTP("smtp.gmail.com", 587) as server:
            server.starttls()
            server.login(settings.mail_username, settings.mail_password)
            server.send_message(message)
        order.email_sent = True
        logger.info("Đã gửi email đơn %s tới %s", order.order_code, settings.mail_notify_to)
    except Exception:
        logger.exception("Gửi email đơn %s thất bại", order.order_code)
        order.email_sent = False

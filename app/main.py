import os
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

from app.database import check_database_connection, get_db
from app.schemas import CreateOrderRequest
from app.services import (
    build_checkout,
    create_order,
    get_news_detail,
    get_product_detail,
    list_news,
    list_products,
    order_response,
)

ROOT = Path(__file__).resolve().parent.parent
TEMPLATES_DIR = ROOT / "templates"
PUBLIC_DIR = ROOT / "public"
IS_VERCEL = os.getenv("VERCEL") == "1"

app = FastAPI(title="Kingnest")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))

# Vercel serves public/ via CDN; mount css/js locally. Images are on Cloudinary.
if PUBLIC_DIR.exists() and not IS_VERCEL:
    css_dir = PUBLIC_DIR / "css"
    js_dir = PUBLIC_DIR / "js"
    if css_dir.exists():
        app.mount("/css", StaticFiles(directory=str(css_dir)), name="css")
    if js_dir.exists():
        app.mount("/js", StaticFiles(directory=str(js_dir)), name="js")


def render_page(template_name: str, request: Request, active_nav: str) -> HTMLResponse:
    return templates.TemplateResponse(
        template_name,
        {"request": request, "active_nav": active_nav},
    )


PAGE_ROUTES = {
    "/": ("Trangchu.html", "home"),
    "/trang-chu": ("Trangchu.html", "home"),
    "/Trangchu.html": ("Trangchu.html", "home"),
    "/san-pham": ("San_pham.html", "products"),
    "/san_pham.html": ("San_pham.html", "products"),
    "/San_pham.html": ("San_pham.html", "products"),
    "/chi-tiet-san-pham": ("Chi_tiet_san_pham.html", "products"),
    "/chi_tiet_san_pham.html": ("Chi_tiet_san_pham.html", "products"),
    "/Chi_tiet_san_pham.html": ("Chi_tiet_san_pham.html", "products"),
    "/tin-tuc": ("Tin_tuc.html", "news"),
    "/Tin_tuc.html": ("Tin_tuc.html", "news"),
    "/tin-tuc-chi-tiet": ("Tin_tuc_chi_tiet.html", "news"),
    "/Tin_tuc_chi_tiet.html": ("Tin_tuc_chi_tiet.html", "news"),
    "/gioi-thieu": ("gioi_thieu.html", "about"),
    "/gioi_thieu.html": ("gioi_thieu.html", "about"),
    "/lien-he": ("Lien_he.html", "contact"),
    "/Lien_he.html": ("Lien_he.html", "contact"),
    "/lien_he.html": ("Lien_he.html", "contact"),
    "/gio-hang": ("Gio_hang.html", "products"),
    "/Gio_hang.html": ("Gio_hang.html", "products"),
}


def _register_page(path: str, template: str, nav: str) -> None:
    async def page_handler(request: Request) -> HTMLResponse:
        return render_page(template, request, nav)

    app.add_api_route(path, page_handler, methods=["GET"], response_class=HTMLResponse)


for route_path, (template_name, active_nav) in PAGE_ROUTES.items():
    _register_page(route_path, template_name, active_nav)


@app.get("/api/products")
def api_products():
    with get_db() as db:
        return {"success": True, "products": list_products(db)}


@app.get("/api/products/{id_or_slug}")
def api_product_detail(id_or_slug: str):
    with get_db() as db:
        product = get_product_detail(db, id_or_slug)
        if not product:
            return JSONResponse(
                status_code=404,
                content={"success": False, "message": f"Không tìm thấy sản phẩm: {id_or_slug}"},
            )
        return {"success": True, "product": product}


@app.get("/api/news")
def api_news():
    with get_db() as db:
        return {"success": True, "articles": list_news(db)}


@app.get("/api/news-content/{slug}")
def api_news_content(slug: str):
    with get_db() as db:
        article = get_news_detail(db, slug)
        if not article:
            return JSONResponse(
                status_code=404,
                content={"success": False, "message": f"Không tìm thấy bài viết: {slug}"},
            )
        return article


@app.get("/checkout")
def checkout(products: str, coupon: str | None = None):
    with get_db() as db:
        return build_checkout(db, products, coupon)


@app.get("/checkout/redirect")
def checkout_redirect(products: str, coupon: str | None = None):
    with get_db() as db:
        data = build_checkout(db, products, coupon)
        return RedirectResponse(url=data["checkoutUrl"], status_code=302)


@app.post("/api/orders")
def api_create_order(payload: CreateOrderRequest):
    try:
        with get_db() as db:
            order = create_order(db, payload)
            return order_response(order)
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"success": False, "message": str(exc)})


@app.get("/health")
def health():
    try:
        check_database_connection()
        return {"ok": True, "database": "connected"}
    except Exception as exc:
        return JSONResponse(
            status_code=503,
            content={"ok": False, "database": "error", "message": str(exc)},
        )

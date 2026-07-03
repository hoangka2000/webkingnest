import os
from pathlib import Path

from fastapi import FastAPI, Request, Response
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, RedirectResponse, Response
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from sqlalchemy.exc import SQLAlchemyError

from app.database import check_database_connection, get_db
from app.schemas import CreateOrderRequest
from app.seo import (
    article_seo_context,
    build_sitemap_xml,
    json_ld_blocks_for_page,
    product_seo_context,
    robots_txt,
    seo_context_for,
)
from app.services import (
    build_checkout,
    create_order,
    find_product,
    get_news_detail,
    get_related_products,
    list_news,
    list_products,
    order_response,
    product_detail_map,
)

ROOT = Path(__file__).resolve().parent.parent
TEMPLATES_DIR = ROOT / "templates"
PUBLIC_DIR = ROOT / "public"
FAVICON_PATH = PUBLIC_DIR / "favicon.png"
IS_VERCEL = os.getenv("VERCEL") == "1"
API_CACHE = "public, s-maxage=120, stale-while-revalidate=600"

app = FastAPI(title="Kingnest")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


@app.exception_handler(SQLAlchemyError)
async def database_exception_handler(_request: Request, _exc: SQLAlchemyError) -> JSONResponse:
    hint = (
        "Kiểm tra biến DATABASE_URL trên Vercel (PostgreSQL cloud)."
        if IS_VERCEL
        else "Kiểm tra PostgreSQL local (docker start kingnest-postgres) và chạy uvicorn."
    )
    return JSONResponse(
        status_code=503,
        content={"success": False, "message": f"Không kết nối được database. {hint}"},
    )

# Vercel serves public/ via CDN; mount css/js locally. Images are on Cloudinary.
if PUBLIC_DIR.exists() and not IS_VERCEL:
    css_dir = PUBLIC_DIR / "css"
    js_dir = PUBLIC_DIR / "js"
    if css_dir.exists():
        app.mount("/css", StaticFiles(directory=str(css_dir)), name="css")
    if js_dir.exists():
        app.mount("/js", StaticFiles(directory=str(js_dir)), name="js")


def render_page(template_name: str, request: Request, active_nav: str) -> HTMLResponse:
    slug = (request.query_params.get("slug") or "").strip()
    product_id = (request.query_params.get("id") or "").strip()
    initial_product = None
    initial_article = None
    product_slug = ""
    article_slug = ""

    with get_db() as db:
        if template_name == "Chi_tiet_san_pham.html":
            key = slug or product_id
            if key:
                entity = find_product(db, key)
                if entity:
                    initial_product = product_detail_map(entity)
                    product_slug = entity.slug
        elif template_name == "Tin_tuc_chi_tiet.html" and slug:
            article = get_news_detail(db, slug)
            if article:
                initial_article = article
                article_slug = slug

    if initial_product and product_slug:
        seo = product_seo_context(initial_product, product_slug)
    elif initial_article and article_slug:
        seo = article_seo_context(initial_article, article_slug)
    else:
        seo = seo_context_for(template_name)

    json_ld_blocks = json_ld_blocks_for_page(
        template_name,
        product=initial_product,
        product_slug=product_slug,
        article=initial_article,
        article_slug=article_slug,
    )

    return templates.TemplateResponse(
        template_name,
        {
            "request": request,
            "active_nav": active_nav,
            "seo": seo,
            "json_ld_blocks": json_ld_blocks,
            "initial_product": initial_product,
            "initial_article": initial_article,
        },
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
def api_products(response: Response):
    response.headers["Cache-Control"] = API_CACHE
    with get_db() as db:
        return {"success": True, "products": list_products(db)}


@app.get("/api/products/{id_or_slug}")
def api_product_detail(id_or_slug: str, response: Response):
    response.headers["Cache-Control"] = API_CACHE
    with get_db() as db:
        entity = find_product(db, id_or_slug)
        if not entity:
            return JSONResponse(
                status_code=404,
                content={"success": False, "message": f"Không tìm thấy sản phẩm: {id_or_slug}"},
            )
        return {
            "success": True,
            "product": product_detail_map(entity),
            "related": get_related_products(db, entity),
        }


@app.get("/api/news")
def api_news(response: Response):
    response.headers["Cache-Control"] = API_CACHE
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


def _favicon_response() -> Response:
    if FAVICON_PATH.is_file():
        return FileResponse(FAVICON_PATH, media_type="image/png")
    return Response(status_code=404)


@app.get("/favicon.ico", include_in_schema=False)
@app.get("/favicon.png", include_in_schema=False)
def favicon() -> Response:
    return _favicon_response()


@app.get("/robots.txt", response_class=Response)
def robots() -> Response:
    return Response(content=robots_txt(), media_type="text/plain; charset=utf-8")


@app.get("/sitemap.xml", response_class=Response)
def sitemap() -> Response:
    with get_db() as db:
        xml = '<?xml version="1.0" encoding="UTF-8"?>\n' + build_sitemap_xml(db)
    return Response(content=xml, media_type="application/xml; charset=utf-8")


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

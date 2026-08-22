from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.openapi.docs import get_swagger_ui_html
from fastapi.staticfiles import StaticFiles
from sqlalchemy import select

from app import database
from app.api.routes import admin, auth, public, saglik
from app.config import settings
from app.core.security import hash_password
from app.models.admin_user import AdminUser

STATIK_KOK = Path(__file__).parent / "static"


def _ensure_default_admin() -> None:
    db = database.SessionLocal()
    try:
        existing = db.scalar(select(AdminUser).where(AdminUser.username == settings.admin_username))
        if existing is None:
            db.add(AdminUser(username=settings.admin_username, hashed_password=hash_password(settings.admin_password)))
            db.commit()
    finally:
        db.close()


@asynccontextmanager
async def lifespan(app: FastAPI):
    _ensure_default_admin()
    yield


# docs_url=None: FastAPI'nin hazır Swagger sayfası JS/CSS'i cdn.jsdelivr.net'ten çeker. Kapalı
# kurumsal ağda tarayıcı oraya ulaşamaz, sayfa BOŞ açılır — ve `/docs` bir dataset'in şemasını
# tanımlamanın belgelenmiş yolu olduğu için admin hiçbir yeni dataset'i kullanılabilir hale
# getiremez. Aşağıda kendi sayfamızı, imaja gömülü dosyalarla veriyoruz.
app = FastAPI(title="TTI Merkezi Veri Yönetim Sistemi", lifespan=lifespan, docs_url=None, redoc_url=None)

app.mount("/static", StaticFiles(directory=STATIK_KOK), name="static")


@app.get("/docs", include_in_schema=False)
def swagger_arayuzu():
    return get_swagger_ui_html(
        openapi_url="/openapi.json",
        title="TTI Merkezi Veri Yönetim Sistemi — API",
        swagger_js_url="/static/swagger-ui-bundle.js",
        swagger_css_url="/static/swagger-ui.css",
        # Varsayılan favicon fastapi.tiangolo.com'dan gelir — kapalı ağda takılır. Panelin
        # kendi favicon'u aynı origin'den servis ediliyor (Caddy /docs'u backend'e, gerisini
        # panele yönlendiriyor). Backend'e doğrudan gidildiğinde 404 olur; anında ve zararsız.
        swagger_favicon_url="/favicon.svg",
    )


# Panel üretimde AYNI origin'den servis ediliyor (Caddy yola göre ayırıyor), o yüzden CORS
# gerekmiyor ve liste boş geliyor. Middleware'i boş listeyle eklemek, ileride birinin "CORS
# açık ama çalışmıyor" diye saatlerce aramasına yol açar — o yüzden hiç eklemiyoruz.
if settings.cors_allowed_origins_list:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_allowed_origins_list,
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )

app.include_router(saglik.router)
app.include_router(auth.router)
app.include_router(admin.router)
app.include_router(public.router)

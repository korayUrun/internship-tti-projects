import os

from fastapi import APIRouter
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app import database
from app.config import settings

router = APIRouter(tags=["saglik"])


def saglik_govdesi() -> tuple[dict, int]:
    """Platform sözleşmesinin (YENI-PROJE.md §3) istediği sağlık raporu.

    Konteyner healthcheck'i bu ucu çağırır ve `dogrula.sh` §5 `surum` alanını okur.
    """
    try:
        # `database.engine` üzerinden: testler bu niteliği SQLite motoruyla değiştiriyor.
        with database.engine.connect() as baglanti:
            baglanti.execute(text("select 1"))
        veritabani_ayakta = True
    except Exception:
        veritabani_ayakta = False

    govde = {
        "durum": "ayakta" if veritabani_ayakta else "bozuk",
        # `docker ps` yalnızca konteynerin ne ile BAŞLATILDIĞINI gösterir; çalışan sürümü
        # sormanın güvenilir tek yolu bu alan.
        "surum": settings.surum,
        "veritabani": "ayakta" if veritabani_ayakta else "erisilemiyor",
        "sema": settings.db_schema,
        # Sözleşmede yok ama eksik `:Z` SELinux etiketini İLK YÜKLEMEDEN ÖNCE yakalayan tek
        # kontrol bu: etiket olmadan uygulama açılır, sağlık geçer, hata ancak ilk Excel
        # yazımında çıkar.
        "yuklemeYazilabilir": os.access(settings.yukleme_kok, os.W_OK),
    }
    # 200 dönmek healthcheck'i anlamsız kılar: konteyner "healthy" görünür, arıza ilk istekte çıkar.
    return govde, (200 if veritabani_ayakta else 503)


@router.get("/api/saglik")
def saglik() -> JSONResponse:
    govde, kod = saglik_govdesi()
    return JSONResponse(govde, status_code=kod)


@router.get("/health", deprecated=True)
def health() -> JSONResponse:
    """Eski ad. `/api/saglik` ile AYNI gövdeyi ve AYNI durum kodunu döner.

    Silinmedi çünkü PROJE.md ve eski notlar bu adresi anıyor; farklı bir gerçek dönmemesi için
    aynı fonksiyona delege ediyor. Caddy'de de backend'e yönlendirilir — yoksa dışarıdan gelen
    `GET /health` SPA'ya düşer ve `index.html` ile 200 döner, ki bu 404'ten kötüdür.
    """
    govde, kod = saglik_govdesi()
    return JSONResponse(govde, status_code=kod)

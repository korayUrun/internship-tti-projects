from collections.abc import Generator

from sqlalchemy import MetaData, create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import settings

# ⚠️ HAVUZ SINIRI AÇIKÇA BEYAN EDİLİR (YENI-PROJE.md §6).
#
# SQLAlchemy'nin varsayılanı 5 + 10 overflow = 15'tir ve aldatıcıdır. PostgreSQL her bağlantı
# için ayrı bir İŞLETİM SİSTEMİ SÜRECİ açar; `max_connections` sert bir sınırdır ve makinedeki
# TÜM projelerce paylaşılır. Havuzu sınırsız bırakan bir servis bütçeyi tek başına tüketir ve o
# an DİĞER servisler `sorry, too many clients` ile düşer — hata da yanlış projenin log'una çıkar.
#
# Bu tavan SÜREÇ BAŞINADIR: yalnızca `--workers 1` ile geçerlidir (bkz. deploy/giris.sh).
# İkinci bir uvicorn worker'ı beyanı sessizce ikiye katlar.
#
# Excel yükleme `run_in_threadpool` ile ayrı thread'lerde koşuyor ve o thread'ler de bu aynı
# havuzdan çekiyor — tavanın açık olmasının sebebi tam olarak bu.
#
# Havuz argümanları YALNIZCA PostgreSQL'e (QueuePool) verilir: SQLite bellek-içi motoru
# SingletonThreadPool kullanır ve `max_overflow`/`pool_timeout` argümanlarını TypeError ile
# reddeder. Testler SQLite'ta koşuyor; üretimde bu dal hiç çalışmaz.
_havuz_ayarlari: dict = {}
if not settings.database_url.startswith("sqlite"):
    _havuz_ayarlari = {
        "pool_size": settings.db_pool_size,
        "max_overflow": settings.db_max_overflow,
        "pool_timeout": 30,
        "pool_recycle": 1800,
    }

engine = create_engine(settings.database_url, pool_pre_ping=True, **_havuz_ayarlari)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


class Base(DeclarativeBase):
    # Şema MetaData'ya veriliyor: böylece her sorgu `data.datasets` diye TAM NİTELİKLİ çıkar ve
    # havuzdan gelen bir bağlantının oturum durumuna (search_path) bağımlılık kalmaz. Modellerdeki
    # `ForeignKey("datasets.id")` de aynı varsayılana çözülür — model dosyaları değişmez.
    #
    # Testlerde DB_SCHEMA="" verilir (SQLite şema tanımaz) ve burası None olur.
    metadata = MetaData(schema=settings.sema)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool, text

from app.config import settings
from app.database import Base
from app.models import *  # noqa: F401,F403  -- modeller Base.metadata'ya kaydolsun diye

config = context.config
config.set_main_option("sqlalchemy.url", settings.database_url)

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    url = config.get_main_option("sqlalchemy.url")
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        version_table_schema=settings.sema,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    sema = settings.sema
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}), prefix="sqlalchemy.", poolclass=pool.NullPool
    )
    with connectable.connect() as connection:
        if sema:
            # Şemayı MIGRATION açar, elle SQL çalıştırılmaz (YENI-PROJE.md §2). Gereken tek yetki
            # `grant create on database data to data` — tti-sunucu/provizyon/03-data.sql'de var.
            connection.execute(text(f'create schema if not exists "{sema}"'))
            # search_path: migration dosyalarındaki NİTELİKSİZ `op.create_table("datasets")`
            # çağrıları bu şemaya düşsün diye. Böylece yayımlanmış migration'lara `schema=`
            # argümanı eklemek gerekmiyor — Alembic'te düzenlenmiş bir migration checksum
            # tutmadığı için HİÇBİR YERDE fark edilmez, o yüzden dosyalara dokunmuyoruz.
            connection.execute(text(f'set search_path to "{sema}"'))

        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            # ⚠️ EN KOLAY UNUTULAN AYAR. Bu olmadan `alembic_version` tablosu `public`'e düşer
            # ve şema ayrımı sandığınızdan zayıf olur. EF Core'daki
            # `MigrationsHistoryTable("__EFMigrationsHistory", "<proje>")`nin karşılığı.
            version_table_schema=sema,
            include_schemas=bool(sema),
        )
        with context.begin_transaction():
            context.run_migrations()

        # ⚠️ BU COMMIT ATLANMAZ — ve atlandığında HİÇBİR HATA VERMEZ.
        #
        # Yukarıdaki `execute` çağrıları bağlantıyı zaten bir transaction'a sokar. Alembic,
        # kendisine hazır bir bağlantı verildiğinde ve o bağlantı ZATEN bir transaction
        # içindeyse, `begin_transaction()`ı no-op'a düşürür ve commit sorumluluğunu çağırana
        # bırakır. Commit edilmezse `connect()` bloğundan çıkarken SQLAlchemy geri alır:
        # Alembic "Running upgrade ... 0001, 0002" diye BAŞARILI log basar, veritabanı ise
        # bomboş kalır. (Bir kez yaşandı — 19 Ağustos 2026.)
        connection.commit()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()

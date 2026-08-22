# Backend — Merkezi Veri Yönetim Sistemi

FastAPI + SQLAlchemy + PostgreSQL. Detaylı ürün gereksinimleri için köke bakın: `../PRD.md`.

## Çalıştırma (Docker Compose)

```bash
cp backend/.env.example backend/.env   # zaten mevcut, gerekirse şifreleri değiştirin
docker compose up --build
```

- API: http://localhost:8000 (Swagger: `/docs`)
- Postgres: `localhost:5432` (user/pass/db: `tti`)
- İlk admin kullanıcı `.env` içindeki `ADMIN_USERNAME` / `ADMIN_PASSWORD` ile otomatik oluşturulur.

## Yerel geliştirme (Docker olmadan)

```bash
cd backend
python -m venv .venv
./.venv/Scripts/activate      # Windows
pip install -r requirements-dev.txt
# DATABASE_URL'i yerel bir Postgres'e işaret edecek şekilde .env içinde ayarlayın
alembic upgrade head
uvicorn app.main:app --reload
```

## Testler

```bash
pytest
```

Testler harici bir veritabanı gerektirmez; in-memory SQLite ile çalışır (`tests/conftest.py`).

## Yeni bir dataset tanımlama

`schema_definition`, Excel başlıklarını DB alanlarına eşler. Şemada tanımlı olmayan Excel kolonları
otomatik olarak yok sayılır.

```json
POST /admin/datasets
{
  "name": "kablo_bilgileri",
  "description": "Saha kablo listesi",
  "schema_definition": {
    "columns": [
      {"excel_header": "Kablo No", "field": "kablo_no", "type": "string", "required": true},
      {"excel_header": "Uzunluk", "field": "uzunluk_m", "type": "number", "required": true}
    ]
  }
}
```

## Uç noktalar

Detaylar için `/docs` (Swagger UI). Özet: `../PRD.md` madde 8.

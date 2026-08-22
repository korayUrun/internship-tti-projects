import os

os.environ.setdefault("DATABASE_URL", "sqlite:///:memory:")
# SQLite şema tanımaz: boş dize → Base.metadata.schema None olur ve `create_all` çalışır.
# Üretimde bu değişken "data"dır (tti-sunucu/docker-compose.yml).
os.environ.setdefault("DB_SCHEMA", "")
os.environ.setdefault("ADMIN_USERNAME", "admin")
os.environ.setdefault("ADMIN_PASSWORD", "test-password")
os.environ.setdefault("JWT_SECRET_KEY", "test-secret")
os.environ.setdefault("YUKLEME_KOK", ".test-yukleme")
os.environ.setdefault("SURUM", "test")

import shutil  # noqa: E402
from pathlib import Path  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from app import database  # noqa: E402
from app.config import settings  # noqa: E402
from app.main import app  # noqa: E402


@pytest.fixture()
def client():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    session_factory = sessionmaker(bind=engine, autoflush=False, autocommit=False)

    database.Base.metadata.create_all(engine)
    database.engine = engine
    database.SessionLocal = session_factory

    Path(settings.yukleme_kok).mkdir(parents=True, exist_ok=True)

    with TestClient(app) as test_client:
        yield test_client

    shutil.rmtree(settings.yukleme_kok, ignore_errors=True)


@pytest.fixture()
def admin_token(client):
    response = client.post(
        "/admin/login", json={"username": settings.admin_username, "password": settings.admin_password}
    )
    assert response.status_code == 200, response.text
    return response.json()["access_token"]

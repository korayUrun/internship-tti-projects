from typing import Annotated

from fastapi import Depends, Header, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.security import decode_access_token
from app.database import get_db
from app.models.dataset import Dataset
from app.models.permission import DatasetPermission

DbSession = Annotated[Session, Depends(get_db)]

_bearer_scheme = HTTPBearer(auto_error=False)


def require_admin(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer_scheme)],
) -> str:
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Kimlik doğrulama gerekli")
    username = decode_access_token(credentials.credentials)
    if username is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Geçersiz veya süresi dolmuş token")
    return username


def require_dataset_access(
    dataset_name: str,
    db: DbSession,
    x_api_key: Annotated[str | None, Header()] = None,
) -> Dataset:
    if not x_api_key:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "X-API-Key header gerekli")

    dataset = db.scalar(select(Dataset).where(Dataset.name == dataset_name))
    if dataset is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Dataset bulunamadı")

    permission = db.scalar(
        select(DatasetPermission).where(
            DatasetPermission.api_key == x_api_key,
            DatasetPermission.dataset_id == dataset.id,
        )
    )
    if permission is None:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Bu dataset'e erişim yetkiniz yok")

    return dataset


def accessible_dataset_ids(db: DbSession, x_api_key: Annotated[str | None, Header()] = None) -> list[str]:
    if not x_api_key:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "X-API-Key header gerekli")
    permissions = db.scalars(select(DatasetPermission).where(DatasetPermission.api_key == x_api_key))
    return [str(p.dataset_id) for p in permissions]

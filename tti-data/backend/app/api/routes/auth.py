from fastapi import APIRouter, HTTPException, status

from app.api.deps import DbSession
from app.core.security import create_access_token
from app.schemas.auth import LoginRequest, TokenResponse
from app.services.auth_service import authenticate_admin

router = APIRouter(tags=["auth"])


@router.post("/admin/login", response_model=TokenResponse)
def login(payload: LoginRequest, db: DbSession) -> TokenResponse:
    admin = authenticate_admin(db, payload.username, payload.password)
    if admin is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Kullanıcı adı veya şifre hatalı")
    token = create_access_token(subject=admin.username)
    return TokenResponse(access_token=token)

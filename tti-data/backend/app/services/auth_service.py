from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.security import verify_password
from app.models.admin_user import AdminUser


def authenticate_admin(db: Session, username: str, password: str) -> AdminUser | None:
    admin = db.scalar(select(AdminUser).where(AdminUser.username == username))
    if admin is None or not verify_password(password, admin.hashed_password):
        return None
    return admin

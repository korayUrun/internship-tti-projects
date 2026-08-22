import uuid
from datetime import datetime

from sqlalchemy import JSON, DateTime, String, Text, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Dataset(Base):
    __tablename__ = "datasets"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(String(100), unique=True, index=True, nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    schema_definition: Mapped[dict] = mapped_column(JSONB().with_variant(JSON(), "sqlite"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    uploads: Mapped[list["DatasetUpload"]] = relationship(back_populates="dataset", cascade="all, delete-orphan")
    records: Mapped[list["DatasetRecord"]] = relationship(back_populates="dataset", cascade="all, delete-orphan")
    permissions: Mapped[list["DatasetPermission"]] = relationship(back_populates="dataset", cascade="all, delete-orphan")

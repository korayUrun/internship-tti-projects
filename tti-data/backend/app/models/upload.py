import enum
import uuid
from datetime import datetime

from sqlalchemy import JSON, DateTime, Enum, ForeignKey, Integer, String, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class UploadStatus(str, enum.Enum):
    SUCCESS = "success"
    FAILED = "failed"
    ROLLED_BACK = "rolled_back"


class DatasetUpload(Base):
    __tablename__ = "dataset_uploads"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    dataset_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("datasets.id", ondelete="CASCADE"), nullable=False, index=True)
    uploaded_by: Mapped[str] = mapped_column(String(100), nullable=False)
    uploaded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    original_filename: Mapped[str] = mapped_column(String(255), nullable=False)
    backup_file_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    # values_callable: Postgres'teki enum etiketleri küçük harf (success/failed/rolled_back).
    # values_callable olmadan SQLAlchemy varsayılan olarak Python enum member adını (SUCCESS)
    # yazmaya çalışır ve Postgres'te "invalid input value for enum" hatası verir.
    status: Mapped[UploadStatus] = mapped_column(
        Enum(UploadStatus, name="upload_status", values_callable=lambda enum_cls: [member.value for member in enum_cls]),
        nullable=False,
    )
    row_count: Mapped[int] = mapped_column(Integer, default=0)
    validation_errors: Mapped[list | None] = mapped_column(JSONB().with_variant(JSON(), "sqlite"), nullable=True)

    dataset: Mapped["Dataset"] = relationship(back_populates="uploads")

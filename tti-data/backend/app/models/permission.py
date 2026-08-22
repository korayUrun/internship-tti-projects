import uuid

from sqlalchemy import ForeignKey, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class DatasetPermission(Base):
    __tablename__ = "dataset_permissions"

    # api_key + dataset_id birleşik primary key: aynı key birden fazla dataset'e
    # (birden fazla satırla) izinli olabilir — bir uygulama tek key ile çoklu
    # dataset'e erişebilsin diye (bkz. TTI Ticket: cable_data + sites_data + workers_data).
    api_key: Mapped[str] = mapped_column(String(64), primary_key=True)
    dataset_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("datasets.id", ondelete="CASCADE"), primary_key=True, index=True
    )
    app_name: Mapped[str] = mapped_column(String(100), nullable=False)

    dataset: Mapped["Dataset"] = relationship(back_populates="permissions")

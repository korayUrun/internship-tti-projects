from fastapi import APIRouter, Depends
from sqlalchemy import select

from app.api.deps import DbSession, accessible_dataset_ids, require_dataset_access
from app.models.dataset import Dataset
from app.models.record import DatasetRecord
from app.schemas.dataset import DatasetOut

router = APIRouter(prefix="/api", tags=["public"])


@router.get("/datasets", response_model=list[DatasetOut])
def list_accessible_datasets(db: DbSession, dataset_ids: list[str] = Depends(accessible_dataset_ids)) -> list[Dataset]:
    if not dataset_ids:
        return []
    return list(db.scalars(select(Dataset).where(Dataset.id.in_(dataset_ids))))


@router.get("/datasets/{dataset_name}/data")
def get_dataset_data(db: DbSession, dataset: Dataset = Depends(require_dataset_access)) -> list[dict]:
    records = db.scalars(
        select(DatasetRecord).where(DatasetRecord.dataset_id == dataset.id, DatasetRecord.is_active.is_(True))
    )
    return [record.row_data for record in records]

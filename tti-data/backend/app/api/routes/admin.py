import uuid

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile, status
from fastapi.concurrency import run_in_threadpool
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import DbSession, require_admin
from app.models.dataset import Dataset
from app.models.record import DatasetRecord
from app.models.upload import DatasetUpload
from app.schemas.dataset import DatasetCreate, DatasetOut, DatasetSummaryOut, SchemaDefinition
from app.schemas.upload import UploadHistoryOut, UploadResult
from app.services.dataset_service import (
    DatasetNotFoundError,
    UploadNotFoundError,
    delete_dataset,
    get_dataset_by_name,
    process_upload,
    rollback_to_upload,
    update_schema,
)

router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(require_admin)])


@router.get("/datasets", response_model=list[DatasetSummaryOut])
def list_datasets(db: DbSession) -> list[DatasetSummaryOut]:
    datasets = db.scalars(select(Dataset).order_by(Dataset.name)).all()
    summaries = []
    for dataset in datasets:
        last_upload = db.scalar(
            select(DatasetUpload)
            .where(DatasetUpload.dataset_id == dataset.id)
            .order_by(DatasetUpload.uploaded_at.desc())
            .limit(1)
        )
        summaries.append(
            DatasetSummaryOut(
                id=dataset.id,
                name=dataset.name,
                description=dataset.description,
                last_uploaded_at=last_upload.uploaded_at if last_upload else None,
                last_uploaded_by=last_upload.uploaded_by if last_upload else None,
            )
        )
    return summaries


@router.post("/datasets", response_model=DatasetOut, status_code=status.HTTP_201_CREATED)
def create_dataset(payload: DatasetCreate, db: DbSession) -> Dataset:
    existing = db.scalar(select(Dataset).where(Dataset.name == payload.name))
    if existing is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, "Bu isimde bir dataset zaten var")

    dataset = Dataset(
        name=payload.name,
        description=payload.description,
        schema_definition=payload.schema_definition.model_dump(),
    )
    db.add(dataset)
    db.commit()
    db.refresh(dataset)
    return dataset


def _get_dataset_or_404(db: Session, dataset_name: str) -> Dataset:
    try:
        return get_dataset_by_name(db, dataset_name)
    except DatasetNotFoundError as exc:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Dataset bulunamadı") from exc


@router.put("/datasets/{dataset_name}/schema", response_model=DatasetOut)
def update_dataset_schema(dataset_name: str, payload: SchemaDefinition, db: DbSession) -> Dataset:
    dataset = _get_dataset_or_404(db, dataset_name)
    return update_schema(db, dataset, payload)


@router.delete("/datasets/{dataset_name}", status_code=status.HTTP_204_NO_CONTENT)
def remove_dataset(dataset_name: str, db: DbSession) -> None:
    dataset = _get_dataset_or_404(db, dataset_name)
    delete_dataset(db, dataset)


@router.post("/datasets/{dataset_name}/upload", response_model=UploadResult)
async def upload_dataset(
    dataset_name: str,
    db: DbSession,
    admin_username: str = Depends(require_admin),
    file: UploadFile = File(...),
    force: bool = Query(default=False),
) -> UploadResult:
    dataset = _get_dataset_or_404(db, dataset_name)
    file_bytes = await file.read()

    # process_upload senkron ve ağır işler yapar (pandas parse + DB yazma). Doğrudan
    # await edilirse tek event loop'u bloke edip TÜM isteklerin (login dahil) donmasına
    # yol açar — bu yüzden ayrı bir thread'e devrediyoruz.
    return await run_in_threadpool(
        process_upload,
        db=db,
        dataset=dataset,
        file_bytes=file_bytes,
        original_filename=file.filename or "upload.xlsx",
        uploaded_by=admin_username,
        force=force,
    )


@router.get("/datasets/{dataset_name}/data")
def dataset_data(dataset_name: str, db: DbSession) -> list[dict]:
    dataset = _get_dataset_or_404(db, dataset_name)
    records = db.scalars(
        select(DatasetRecord).where(DatasetRecord.dataset_id == dataset.id, DatasetRecord.is_active.is_(True))
    )
    return [record.row_data for record in records]


@router.get("/datasets/{dataset_name}/history", response_model=list[UploadHistoryOut])
def dataset_history(dataset_name: str, db: DbSession) -> list[DatasetUpload]:
    dataset = _get_dataset_or_404(db, dataset_name)
    return list(
        db.scalars(
            select(DatasetUpload)
            .where(DatasetUpload.dataset_id == dataset.id)
            .order_by(DatasetUpload.uploaded_at.desc())
        )
    )


@router.post("/datasets/{dataset_name}/rollback/{upload_id}", response_model=UploadResult)
def rollback_dataset(
    dataset_name: str,
    upload_id: uuid.UUID,
    db: DbSession,
    admin_username: str = Depends(require_admin),
) -> UploadResult:
    dataset = _get_dataset_or_404(db, dataset_name)
    try:
        return rollback_to_upload(db, dataset, upload_id, performed_by=admin_username)
    except UploadNotFoundError as exc:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Geri dönülecek versiyon bulunamadı") from exc

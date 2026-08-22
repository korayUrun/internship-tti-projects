import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.models.dataset import Dataset
from app.models.record import DatasetRecord
from app.models.upload import DatasetUpload, UploadStatus
from app.schemas.dataset import SchemaDefinition
from app.schemas.upload import UploadResult, ValidationError
from app.services import backup_service
from app.services.excel_service import group_rows, parse_excel, row_count_dropped_significantly


class DatasetNotFoundError(Exception):
    pass


class UploadNotFoundError(Exception):
    pass


def get_dataset_by_name(db: Session, name: str) -> Dataset:
    dataset = db.scalar(select(Dataset).where(Dataset.name == name))
    if dataset is None:
        raise DatasetNotFoundError(name)
    return dataset


def _active_records(db: Session, dataset_id: uuid.UUID) -> list[DatasetRecord]:
    return list(db.scalars(select(DatasetRecord).where(DatasetRecord.dataset_id == dataset_id, DatasetRecord.is_active.is_(True))))


def update_schema(db: Session, dataset: Dataset, schema: SchemaDefinition) -> Dataset:
    dataset.schema_definition = schema.model_dump()
    db.add(dataset)
    db.commit()
    db.refresh(dataset)
    return dataset


def delete_dataset(db: Session, dataset: Dataset) -> None:
    """Dataset'i, tüm kayıtlarını/geçmişini (DB cascade ile) ve disk üzerindeki
    backup dosyalarını siler. Geri alınamaz."""
    dataset_name = dataset.name
    db.delete(dataset)
    db.commit()
    backup_service.delete_dataset_backups(dataset_name)


def _enrich_with_lookup(db: Session, schema: SchemaDefinition, records: list[dict]) -> list[dict]:
    """`lookup_dataset` ayarlıysa, group_by değeriyle başka bir dataset'te eşleşen
    kaydın `lookup_fields` alanlarını bu kayıtlara kopyalar (bkz. SchemaDefinition).
    Eşleşme bulunamazsa alan yine de eklenir, değeri null olur.
    """
    if not schema.lookup_dataset or not schema.lookup_key or not schema.lookup_fields:
        return records

    lookup_dataset = db.scalar(select(Dataset).where(Dataset.name == schema.lookup_dataset))
    if lookup_dataset is None:
        return records

    lookup_records = db.scalars(
        select(DatasetRecord).where(DatasetRecord.dataset_id == lookup_dataset.id, DatasetRecord.is_active.is_(True))
    )
    index = {record.row_data.get(schema.lookup_key): record.row_data for record in lookup_records}

    for record in records:
        match = index.get(record.get(schema.group_by))
        for field_name in schema.lookup_fields:
            record[field_name] = match.get(field_name) if match else None

    return records


def process_upload(
    db: Session,
    dataset: Dataset,
    file_bytes: bytes,
    original_filename: str,
    uploaded_by: str,
    force: bool = False,
) -> UploadResult:
    """Excel dosyasını doğrular ve geçerli satırlarla dataset_records'u değiştirir.

    Kısmi kabul: hatalı satırlar atlanır ve raporlanır, geçerli satırlar yine de yazılır.
    Sadece dosya yapısal olarak okunamazsa (zorunlu kolon hiç yok) ya da hiç geçerli
    satır kalmazsa upload tamamen reddedilir.
    """
    schema = SchemaDefinition.model_validate(dataset.schema_definition)

    upload = DatasetUpload(
        dataset_id=dataset.id,
        uploaded_by=uploaded_by,
        original_filename=original_filename,
        status=UploadStatus.FAILED,
        row_count=0,
    )

    if not schema.columns:
        upload.validation_errors = [
            {"row": 0, "field": "schema", "message": "Bu dataset için henüz kolon şeması tanımlanmadı."}
        ]
        db.add(upload)
        db.commit()
        db.refresh(upload)
        return UploadResult(
            upload_id=upload.id,
            status=upload.status,
            row_count=0,
            validation_errors=[
                ValidationError(row=0, field="schema", message="Bu dataset için henüz kolon şeması tanımlanmadı.")
            ],
        )

    parse_result = parse_excel(file_bytes, schema)

    if not parse_result.rows:
        upload.validation_errors = [e.model_dump() for e in parse_result.errors]
        db.add(upload)
        db.commit()
        db.refresh(upload)
        return UploadResult(
            upload_id=upload.id,
            status=upload.status,
            row_count=0,
            validation_errors=parse_result.errors,
        )

    # group_by ayarlıysa (örn. df_parent_data) düz satırlar burada parent+nested item
    # kayıtlarına dönüştürülür; aksi halde satırlar değişmeden kullanılır.
    records_to_store = group_rows(parse_result.rows, schema)
    records_to_store = _enrich_with_lookup(db, schema, records_to_store)

    previous_active = _active_records(db, dataset.id)
    warning = None
    if not force and row_count_dropped_significantly(len(previous_active), len(records_to_store), settings.row_count_drop_warning_ratio):
        warning = (
            f"Kayıt sayısı önceki versiyona göre önemli ölçüde azaldı "
            f"({len(previous_active)} -> {len(records_to_store)}). Devam etmek için force=true ile tekrar deneyin."
        )
        return UploadResult(upload_id=upload.id, status=UploadStatus.FAILED, row_count=len(records_to_store), warning=warning)

    upload.status = UploadStatus.SUCCESS
    upload.row_count = len(records_to_store)
    upload.validation_errors = [e.model_dump() for e in parse_result.errors] if parse_result.errors else None
    db.add(upload)
    db.flush()  # upload.id gerekli, henüz commit yok

    backup_service.save_original_file(dataset.name, upload.id, file_bytes)
    upload.backup_file_path = backup_service.save_records_snapshot(dataset.name, upload.id, records_to_store)

    for record in previous_active:
        db.delete(record)
    for row_data in records_to_store:
        db.add(DatasetRecord(dataset_id=dataset.id, row_data=row_data, is_active=True))

    db.commit()
    db.refresh(upload)

    return UploadResult(
        upload_id=upload.id,
        status=upload.status,
        row_count=upload.row_count,
        validation_errors=parse_result.errors,
    )


def rollback_to_upload(db: Session, dataset: Dataset, upload_id: uuid.UUID, performed_by: str) -> UploadResult:
    target_upload = db.scalar(
        select(DatasetUpload).where(DatasetUpload.id == upload_id, DatasetUpload.dataset_id == dataset.id)
    )
    if target_upload is None or not target_upload.backup_file_path:
        raise UploadNotFoundError(upload_id)

    rows = backup_service.load_records_snapshot(target_upload.backup_file_path)

    rollback_upload = DatasetUpload(
        dataset_id=dataset.id,
        uploaded_by=performed_by,
        original_filename=target_upload.original_filename,
        status=UploadStatus.ROLLED_BACK,
        row_count=len(rows),
        backup_file_path=target_upload.backup_file_path,
    )
    db.add(rollback_upload)

    for record in _active_records(db, dataset.id):
        db.delete(record)
    for row_data in rows:
        db.add(DatasetRecord(dataset_id=dataset.id, row_data=row_data, is_active=True))

    db.commit()
    db.refresh(rollback_upload)

    return UploadResult(upload_id=rollback_upload.id, status=rollback_upload.status, row_count=rollback_upload.row_count)

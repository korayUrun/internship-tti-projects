import uuid
from datetime import datetime

from pydantic import BaseModel

from app.models.upload import UploadStatus


class ValidationError(BaseModel):
    row: int
    field: str
    message: str


class UploadResult(BaseModel):
    upload_id: uuid.UUID
    status: UploadStatus
    row_count: int
    validation_errors: list[ValidationError] = []
    warning: str | None = None


class UploadHistoryOut(BaseModel):
    id: uuid.UUID
    uploaded_by: str
    uploaded_at: datetime
    original_filename: str
    status: UploadStatus
    row_count: int
    validation_errors: list[ValidationError] | None = None

    model_config = {"from_attributes": True}

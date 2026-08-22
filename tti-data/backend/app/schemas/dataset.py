import uuid
from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field


class ColumnType(str, Enum):
    STRING = "string"
    NUMBER = "number"
    BOOLEAN = "boolean"


class ColumnDefinition(BaseModel):
    excel_header: str = Field(..., description="Excel dosyasındaki başlık")
    field: str = Field(..., description="DB'de row_data içindeki alan adı")
    type: ColumnType = ColumnType.STRING
    required: bool = False


class SchemaDefinition(BaseModel):
    columns: list[ColumnDefinition]

    # Gruplama (opsiyonel): Excel'deki düz satırları, ortak bir alana göre gruplayıp
    # iç içe (nested) bir liste haline getirmek için. Örn. df_parent_data'da her satır
    # bir (cable_id, nosid, supplier_id) üçlüsü; group_by="cable_id" ile aynı cable_id'ye
    # sahip satırlar tek bir kayıtta, item_fields'daki alanlar da nested_field altında
    # bir liste olarak toplanır. group_by boşsa gruplama yapılmaz, satırlar düz kaydedilir.
    group_by: str | None = None
    nested_field: str = "items"
    item_fields: list[str] = []

    # Lookup/enrichment (opsiyonel): grup anahtarına karşılık gelen alanları başka bir
    # dataset'ten çekip parent kaydına ekler. Örn. df_parent_data'nın kendi Excel'inde
    # capacity/addressA/addressB yok; bunlar cable_data'da group_by değeriyle (cable_id)
    # eşleşen kaydın nosid'i üzerinden bulunup kopyalanır. Eşleşme, upload anında yapılır
    # ve bir anlık görüntü (snapshot) olarak kaydedilir — cable_data sonradan değişirse
    # df_parent_data'nın güncellenmesi için tekrar yüklenmesi gerekir.
    lookup_dataset: str | None = None
    lookup_key: str | None = None
    lookup_fields: list[str] = []


class DatasetCreate(BaseModel):
    name: str
    description: str | None = None
    schema_definition: SchemaDefinition


class DatasetOut(BaseModel):
    id: uuid.UUID
    name: str
    description: str | None
    schema_definition: SchemaDefinition
    created_at: datetime

    model_config = {"from_attributes": True}


class DatasetSummaryOut(BaseModel):
    id: uuid.UUID
    name: str
    description: str | None
    last_uploaded_at: datetime | None
    last_uploaded_by: str | None

    model_config = {"from_attributes": True}

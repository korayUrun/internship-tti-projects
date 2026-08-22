from dataclasses import dataclass, field
from io import BytesIO

import pandas as pd

from app.schemas.dataset import ColumnDefinition, ColumnType, SchemaDefinition
from app.schemas.upload import ValidationError


@dataclass
class ParseResult:
    """rows: geçerli satırlar (DB'ye yazılacak). errors: atlanan satırların/hataların listesi.

    rejected=True yalnızca dosya yapısal olarak okunamaz durumdaysa (zorunlu bir kolon
    Excel'de hiç yoksa) set edilir — bu durumda tüm dosya reddedilir çünkü o kolon için
    satır bazlı bir değerlendirme yapmak mümkün değildir. Aksi halde hatalı satırlar
    atlanır, geçerli satırlar yazılır (kısmi kabul).
    """

    rows: list[dict] = field(default_factory=list)
    errors: list[ValidationError] = field(default_factory=list)
    rejected: bool = False


def _coerce_value(raw_value, column: ColumnDefinition, row_number: int, errors: list[ValidationError]) -> tuple[object, bool]:
    """Değeri dönüştürür. (değer, satır_geçerli_mi) döner.

    `required`, hücrenin boş olamayacağı anlamına gelmez — hücre boşsa `null` yazılır.
    Sadece kolonun Excel'de bir başlık olarak bulunması zorunlu olur (bkz. parse_excel
    içindeki missing_headers kontrolü).
    """
    if pd.isna(raw_value) or (isinstance(raw_value, str) and raw_value.strip() == ""):
        return None, True

    if column.type == ColumnType.NUMBER:
        try:
            return float(raw_value), True
        except (TypeError, ValueError):
            errors.append(
                ValidationError(row=row_number, field=column.field, message=f"'{raw_value}' sayısal bir değer değil")
            )
            return None, False

    if column.type == ColumnType.BOOLEAN:
        if isinstance(raw_value, bool):
            return raw_value, True
        text = str(raw_value).strip().lower()
        if text in {"true", "1", "evet", "yes"}:
            return True, True
        if text in {"false", "0", "hayır", "no"}:
            return False, True
        errors.append(
            ValidationError(row=row_number, field=column.field, message=f"'{raw_value}' boolean bir değer değil")
        )
        return None, False

    return str(raw_value).strip(), True


def parse_excel(file_bytes: bytes, schema: SchemaDefinition) -> ParseResult:
    """Excel'i şemaya göre okur. Şemada tanımlı olmayan kolonlar yok sayılır.

    Satır bazlı doğrulama yapılır: bir satırda hata varsa sadece o satır atlanır ve
    hatası raporlanır, diğer geçerli satırlar yine de veritabanına yazılır.
    """
    dataframe = pd.read_excel(BytesIO(file_bytes), dtype=object)
    dataframe.columns = [str(c).strip() for c in dataframe.columns]

    missing_headers = [
        column.excel_header
        for column in schema.columns
        if column.required and column.excel_header not in dataframe.columns
    ]
    if missing_headers:
        errors = [
            ValidationError(row=0, field=header, message="Zorunlu kolon Excel'de bulunamadı")
            for header in missing_headers
        ]
        return ParseResult(rows=[], errors=errors, rejected=True)

    rows: list[dict] = []
    errors: list[ValidationError] = []
    for position, raw_row in enumerate(dataframe.to_dict(orient="records")):
        row_number = position + 2  # +1 header satırı, +1 1-index
        row_data = {}
        row_is_valid = True
        for column in schema.columns:
            raw_value = raw_row.get(column.excel_header)
            value, is_valid = _coerce_value(raw_value, column, row_number, errors)
            row_data[column.field] = value
            row_is_valid = row_is_valid and is_valid
        if row_is_valid:
            rows.append(row_data)

    return ParseResult(rows=rows, errors=errors)


def group_rows(rows: list[dict], schema: SchemaDefinition) -> list[dict]:
    """`schema.group_by` ayarlıysa düz satırları grupla, parent + nested item listesi üretir.

    Örn. group_by="cable_id", item_fields=["nosid","supplier_id"] için aynı cable_id'ye
    sahip satırlar tek bir kayıtta birleşir; o kaydın "items" alanı altında her satırın
    nosid/supplier_id çifti bir liste elemanı olur. group_by tanımlı değilse satırlar
    değiştirilmeden döner.
    """
    if not schema.group_by:
        return rows

    grouped: dict[object, dict] = {}
    order: list[object] = []
    for row in rows:
        key = row.get(schema.group_by)
        if key not in grouped:
            parent = {k: v for k, v in row.items() if k not in schema.item_fields}
            parent[schema.nested_field] = []
            grouped[key] = parent
            order.append(key)
        item = {field_name: row.get(field_name) for field_name in schema.item_fields}
        grouped[key][schema.nested_field].append(item)

    return [grouped[key] for key in order]


def row_count_dropped_significantly(previous_count: int, new_count: int, warning_ratio: float) -> bool:
    if previous_count == 0:
        return False
    dropped_ratio = (previous_count - new_count) / previous_count
    return dropped_ratio >= warning_ratio

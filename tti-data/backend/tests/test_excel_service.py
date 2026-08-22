from io import BytesIO

import openpyxl

from app.schemas.dataset import ColumnDefinition, ColumnType, SchemaDefinition
from app.services.excel_service import group_rows, parse_excel, row_count_dropped_significantly

SCHEMA = SchemaDefinition(
    columns=[
        ColumnDefinition(excel_header="Kablo No", field="kablo_no", type=ColumnType.STRING, required=True),
        ColumnDefinition(excel_header="Uzunluk", field="uzunluk_m", type=ColumnType.NUMBER, required=True),
        ColumnDefinition(excel_header="Aktif", field="aktif", type=ColumnType.BOOLEAN, required=False),
    ]
)


def _build_excel(rows: list[list], headers: list[str]) -> bytes:
    workbook = openpyxl.Workbook()
    sheet = workbook.active
    sheet.append(headers)
    for row in rows:
        sheet.append(row)
    buffer = BytesIO()
    workbook.save(buffer)
    return buffer.getvalue()


def test_parse_excel_ignores_extra_columns_and_reads_expected_ones():
    file_bytes = _build_excel(
        rows=[["K-1", 12.5, "evet", "gereksiz-deger"]],
        headers=["Kablo No", "Uzunluk", "Aktif", "Kullanılmayan Kolon"],
    )

    result = parse_excel(file_bytes, SCHEMA)

    assert not result.rejected
    assert not result.errors
    assert result.rows == [{"kablo_no": "K-1", "uzunluk_m": 12.5, "aktif": True}]


def test_parse_excel_writes_null_for_empty_required_value_without_error():
    file_bytes = _build_excel(
        rows=[["", 12.5, "evet"], ["K-2", 20, "evet"]],
        headers=["Kablo No", "Uzunluk", "Aktif"],
    )

    result = parse_excel(file_bytes, SCHEMA)

    assert not result.rejected
    assert not result.errors
    assert result.rows == [
        {"kablo_no": None, "uzunluk_m": 12.5, "aktif": True},
        {"kablo_no": "K-2", "uzunluk_m": 20.0, "aktif": True},
    ]


def test_parse_excel_skips_row_with_invalid_number_but_keeps_others():
    file_bytes = _build_excel(
        rows=[["K-1", "abc", "evet"], ["K-2", 20, "evet"]],
        headers=["Kablo No", "Uzunluk", "Aktif"],
    )

    result = parse_excel(file_bytes, SCHEMA)

    assert not result.rejected
    assert result.rows == [{"kablo_no": "K-2", "uzunluk_m": 20.0, "aktif": True}]
    assert any(e.field == "uzunluk_m" and e.row == 2 for e in result.errors)


def test_parse_excel_rejects_whole_file_when_required_column_missing():
    file_bytes = _build_excel(rows=[["12.5"]], headers=["Uzunluk"])

    result = parse_excel(file_bytes, SCHEMA)

    assert result.rejected
    assert result.rows == []
    assert any(e.field == "Kablo No" for e in result.errors)


def test_group_rows_nests_item_fields_under_shared_key():
    schema = SchemaDefinition(
        columns=[
            ColumnDefinition(excel_header="Cable ID", field="cable_id", type=ColumnType.STRING, required=True),
            ColumnDefinition(excel_header="Capacity", field="capacity", type=ColumnType.NUMBER, required=True),
            ColumnDefinition(excel_header="Nosid", field="nosid", type=ColumnType.STRING, required=True),
            ColumnDefinition(excel_header="Supplier", field="supplier_id", type=ColumnType.STRING, required=True),
        ],
        group_by="cable_id",
        nested_field="items",
        item_fields=["nosid", "supplier_id"],
    )
    rows = [
        {"cable_id": "C-1", "capacity": 72.0, "nosid": "df-1", "supplier_id": "S-1"},
        {"cable_id": "C-1", "capacity": 72.0, "nosid": "df-2", "supplier_id": "S-2"},
        {"cable_id": "C-2", "capacity": 24.0, "nosid": "df-3", "supplier_id": "S-3"},
    ]

    grouped = group_rows(rows, schema)

    assert grouped == [
        {
            "cable_id": "C-1",
            "capacity": 72.0,
            "items": [
                {"nosid": "df-1", "supplier_id": "S-1"},
                {"nosid": "df-2", "supplier_id": "S-2"},
            ],
        },
        {
            "cable_id": "C-2",
            "capacity": 24.0,
            "items": [{"nosid": "df-3", "supplier_id": "S-3"}],
        },
    ]


def test_group_rows_returns_unchanged_when_group_by_not_set():
    schema = SchemaDefinition(columns=[ColumnDefinition(excel_header="A", field="a")])
    rows = [{"a": "1"}, {"a": "2"}]

    assert group_rows(rows, schema) == rows


def test_row_count_dropped_significantly():
    assert row_count_dropped_significantly(previous_count=100, new_count=40, warning_ratio=0.5)
    assert not row_count_dropped_significantly(previous_count=100, new_count=60, warning_ratio=0.5)
    assert not row_count_dropped_significantly(previous_count=0, new_count=0, warning_ratio=0.5)

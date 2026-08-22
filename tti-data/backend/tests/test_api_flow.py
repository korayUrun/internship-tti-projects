import uuid
from io import BytesIO

import openpyxl


def _build_excel(rows: list[list], headers: list[str]) -> bytes:
    workbook = openpyxl.Workbook()
    sheet = workbook.active
    sheet.append(headers)
    for row in rows:
        sheet.append(row)
    buffer = BytesIO()
    workbook.save(buffer)
    return buffer.getvalue()


SCHEMA_DEFINITION = {
    "columns": [
        {"excel_header": "Kablo No", "field": "kablo_no", "type": "string", "required": True},
        {"excel_header": "Uzunluk", "field": "uzunluk_m", "type": "number", "required": True},
    ]
}


def _auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


def test_full_upload_read_rollback_flow(client, admin_token):
    create_response = client.post(
        "/admin/datasets",
        json={"name": "kablo_bilgileri", "description": "Kablo listesi", "schema_definition": SCHEMA_DEFINITION},
        headers=_auth_headers(admin_token),
    )
    assert create_response.status_code == 201, create_response.text

    api_key = "test-api-key"
    from app import database
    from app.models.permission import DatasetPermission

    db = database.SessionLocal()
    dataset_id = uuid.UUID(create_response.json()["id"])
    db.add(DatasetPermission(api_key=api_key, app_name="tablet-app", dataset_id=dataset_id))
    db.commit()
    db.close()

    first_excel = _build_excel(rows=[["K-1", 10], ["K-2", 20]], headers=["Kablo No", "Uzunluk"])
    upload_response = client.post(
        "/admin/datasets/kablo_bilgileri/upload",
        files={"file": ("kablo.xlsx", first_excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
        headers=_auth_headers(admin_token),
    )
    assert upload_response.status_code == 200, upload_response.text
    assert upload_response.json()["row_count"] == 2
    first_upload_id = upload_response.json()["upload_id"]

    data_response = client.get("/api/datasets/kablo_bilgileri/data", headers={"X-API-Key": api_key})
    assert data_response.status_code == 200
    assert sorted(row["kablo_no"] for row in data_response.json()) == ["K-1", "K-2"]

    second_excel = _build_excel(rows=[["K-3", 30]], headers=["Kablo No", "Uzunluk"])
    second_upload_response = client.post(
        "/admin/datasets/kablo_bilgileri/upload?force=true",
        files={"file": ("kablo2.xlsx", second_excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
        headers=_auth_headers(admin_token),
    )
    assert second_upload_response.status_code == 200, second_upload_response.text
    assert second_upload_response.json()["row_count"] == 1

    data_after_second_upload = client.get("/api/datasets/kablo_bilgileri/data", headers={"X-API-Key": api_key})
    assert [row["kablo_no"] for row in data_after_second_upload.json()] == ["K-3"]

    history_response = client.get("/admin/datasets/kablo_bilgileri/history", headers=_auth_headers(admin_token))
    assert len(history_response.json()) == 2

    rollback_response = client.post(
        f"/admin/datasets/kablo_bilgileri/rollback/{first_upload_id}", headers=_auth_headers(admin_token)
    )
    assert rollback_response.status_code == 200, rollback_response.text
    assert rollback_response.json()["row_count"] == 2

    data_after_rollback = client.get("/api/datasets/kablo_bilgileri/data", headers={"X-API-Key": api_key})
    assert sorted(row["kablo_no"] for row in data_after_rollback.json()) == ["K-1", "K-2"]


def test_invalid_excel_is_rejected_without_writing_data(client, admin_token):
    client.post(
        "/admin/datasets",
        json={"name": "personel", "description": None, "schema_definition": SCHEMA_DEFINITION},
        headers=_auth_headers(admin_token),
    )

    # Kablo No boş olsa da hata sayılmaz (null yazılır); tek geçersiz alan Uzunluk'un
    # sayısal olmaması. Tek satır olduğu için geçerli satır kalmaz, upload reddedilir.
    bad_excel = _build_excel(rows=[["", "not-a-number"]], headers=["Kablo No", "Uzunluk"])
    response = client.post(
        "/admin/datasets/personel/upload",
        files={"file": ("bad.xlsx", bad_excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
        headers=_auth_headers(admin_token),
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "failed"
    assert len(body["validation_errors"]) == 1
    assert body["validation_errors"][0]["field"] == "uzunluk_m"


def test_upload_partially_accepts_valid_rows_and_reports_invalid_ones(client, admin_token):
    create_response = client.post(
        "/admin/datasets",
        json={"name": "kismi_kabul", "description": None, "schema_definition": SCHEMA_DEFINITION},
        headers=_auth_headers(admin_token),
    )
    dataset_id = uuid.UUID(create_response.json()["id"])

    mixed_excel = _build_excel(
        rows=[["K-1", 10], ["", "not-a-number"], ["K-3", 30]],
        headers=["Kablo No", "Uzunluk"],
    )
    response = client.post(
        "/admin/datasets/kismi_kabul/upload",
        files={"file": ("mixed.xlsx", mixed_excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
        headers=_auth_headers(admin_token),
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["status"] == "success"
    assert body["row_count"] == 2
    assert len(body["validation_errors"]) == 1
    assert body["validation_errors"][0]["field"] == "uzunluk_m"

    api_key = "kismi-kabul-key"
    from app import database
    from app.models.permission import DatasetPermission

    db = database.SessionLocal()
    db.add(DatasetPermission(api_key=api_key, app_name="test-app", dataset_id=dataset_id))
    db.commit()
    db.close()

    data_response = client.get("/api/datasets/kismi_kabul/data", headers={"X-API-Key": api_key})
    assert sorted(row["kablo_no"] for row in data_response.json()) == ["K-1", "K-3"]


def test_delete_dataset_removes_it_and_its_data(client, admin_token):
    create_response = client.post(
        "/admin/datasets",
        json={"name": "silinecek", "description": None, "schema_definition": SCHEMA_DEFINITION},
        headers=_auth_headers(admin_token),
    )
    assert create_response.status_code == 201

    excel = _build_excel(rows=[["K-1", 10]], headers=["Kablo No", "Uzunluk"])
    upload_response = client.post(
        "/admin/datasets/silinecek/upload",
        files={"file": ("k.xlsx", excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
        headers=_auth_headers(admin_token),
    )
    assert upload_response.status_code == 200, upload_response.text

    delete_response = client.delete("/admin/datasets/silinecek", headers=_auth_headers(admin_token))
    assert delete_response.status_code == 204

    names = [d["name"] for d in client.get("/admin/datasets", headers=_auth_headers(admin_token)).json()]
    assert "silinecek" not in names

    history_after_delete = client.get("/admin/datasets/silinecek/history", headers=_auth_headers(admin_token))
    assert history_after_delete.status_code == 404


def test_grouped_dataset_enriches_from_lookup_dataset(client, admin_token):
    # cable_data: capacity/addressA/addressB burada tutulur, join anahtarı "nosid".
    cable_schema = {
        "columns": [
            {"excel_header": "nosid", "field": "nosid", "type": "string", "required": True},
            {"excel_header": "capacity", "field": "capacity", "type": "number", "required": True},
            {"excel_header": "addressA", "field": "addressA", "type": "string", "required": True},
            {"excel_header": "addressB", "field": "addressB", "type": "string", "required": True},
        ]
    }
    client.post(
        "/admin/datasets",
        json={"name": "cable_data", "description": None, "schema_definition": cable_schema},
        headers=_auth_headers(admin_token),
    )
    cable_excel = _build_excel(
        rows=[["mtc06591b.cable*drn-kkl", 72, "Arpac", "Yenice"]],
        headers=["nosid", "capacity", "addressA", "addressB"],
    )
    cable_upload = client.post(
        "/admin/datasets/cable_data/upload",
        files={"file": ("cable.xlsx", cable_excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
        headers=_auth_headers(admin_token),
    )
    assert cable_upload.status_code == 200, cable_upload.text
    assert cable_upload.json()["row_count"] == 1

    # df_parent_data: kendi Excel'inde sadece cable_id/nosid/supplier_id var;
    # capacity/addressA/addressB cable_data'dan lookup ile gelmeli.
    df_parent_schema = {
        "columns": [
            {"excel_header": "%CI_ParentServiceKey", "field": "cable_id", "type": "string", "required": True},
            {"excel_header": "nosid", "field": "nosid", "type": "string", "required": True},
            {"excel_header": "supplier_id", "field": "supplier_id", "type": "string", "required": True},
        ],
        "group_by": "cable_id",
        "nested_field": "items",
        "item_fields": ["nosid", "supplier_id"],
        "lookup_dataset": "cable_data",
        "lookup_key": "nosid",
        "lookup_fields": ["capacity", "addressA", "addressB"],
    }
    df_parent_create = client.post(
        "/admin/datasets",
        json={"name": "df_parent_data", "description": None, "schema_definition": df_parent_schema},
        headers=_auth_headers(admin_token),
    )
    df_parent_id = uuid.UUID(df_parent_create.json()["id"])
    df_excel = _build_excel(
        rows=[
            ["mtc06591b.cable*drn-kkl", "etu10914a.df*drn-kkl", "DF0003VE"],
            ["mtc06591b.cable*drn-kkl", "mtc30484a.df*drn-kkl", "Arpac 88-01"],
        ],
        headers=["%CI_ParentServiceKey", "nosid", "supplier_id"],
    )
    df_upload = client.post(
        "/admin/datasets/df_parent_data/upload",
        files={"file": ("df.xlsx", df_excel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
        headers=_auth_headers(admin_token),
    )
    assert df_upload.status_code == 200, df_upload.text
    assert df_upload.json()["row_count"] == 1  # tek cable_id'ye gruplanmış

    api_key = "df-parent-key"
    from app import database
    from app.models.permission import DatasetPermission

    db = database.SessionLocal()
    db.add(DatasetPermission(api_key=api_key, app_name="test-app", dataset_id=df_parent_id))
    db.commit()
    db.close()

    data_response = client.get("/api/datasets/df_parent_data/data", headers={"X-API-Key": api_key})
    assert data_response.status_code == 200
    records = data_response.json()
    assert len(records) == 1
    record = records[0]
    assert record["cable_id"] == "mtc06591b.cable*drn-kkl"
    assert record["capacity"] == 72.0
    assert record["addressA"] == "Arpac"
    assert record["addressB"] == "Yenice"
    assert sorted(item["nosid"] for item in record["items"]) == [
        "etu10914a.df*drn-kkl",
        "mtc30484a.df*drn-kkl",
    ]


def test_unauthenticated_requests_are_rejected(client):
    assert client.get("/admin/datasets").status_code == 401
    assert client.get("/api/datasets/kablo_bilgileri/data").status_code == 401

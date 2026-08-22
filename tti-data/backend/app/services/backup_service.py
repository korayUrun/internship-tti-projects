import json
import shutil
import uuid
from pathlib import Path

from app.config import settings


def _dataset_dir(dataset_name: str) -> Path:
    path = Path(settings.yukleme_kok) / dataset_name
    path.mkdir(parents=True, exist_ok=True)
    return path


def save_original_file(dataset_name: str, upload_id: uuid.UUID, file_bytes: bytes) -> str:
    """Yüklenen orijinal Excel dosyasını diske kaydeder (indirme/denetim amaçlı)."""
    path = _dataset_dir(dataset_name) / f"{upload_id}.xlsx"
    path.write_bytes(file_bytes)
    return str(path)


def save_records_snapshot(dataset_name: str, upload_id: uuid.UUID, rows: list[dict]) -> str:
    """Bu upload sonrası aktif hale gelen satırların JSON anlık görüntüsünü kaydeder.

    Rollback bu dosyadan okunur: "bu versiyona dön" = bu upload'un yazdığı veriyi
    tekrar aktif hale getir.
    """
    path = _dataset_dir(dataset_name) / f"{upload_id}.json"
    path.write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
    return str(path)


def load_records_snapshot(backup_file_path: str) -> list[dict]:
    return json.loads(Path(backup_file_path).read_text(encoding="utf-8"))


def delete_dataset_backups(dataset_name: str) -> None:
    """Dataset silinirken diskteki backup/upload dosyalarını da temizler."""
    shutil.rmtree(Path(settings.yukleme_kok) / dataset_name, ignore_errors=True)

#!/usr/bin/env python3
"""backend/backups/ altındaki yedeklerden `schema_definition` TASLAĞI üretir.

NEDEN VAR: dataset'lerin şema tanımı yalnızca veritabanında yaşar. Sunucuya temiz bir
veritabanıyla geçiliyorsa ve elde eski veritabanının dump'ı YOKSA, 21 + 11 + 11 alanlık
tanımları elle yeniden yazmak tüm göçün en hataya açık adımıdır. Bu betik, her yüklemede
diske yazılan `.xlsx` (Excel başlıkları) ve `.json` (kaydedilen alan adları) çiftinden
tanımı geri çıkarır.

⚠️ ÇIKTI BİR TASLAKTIR, OLDUĞU GİBİ KULLANILMAZ. Geri getirilemeyen üç şey var ve betik
bunları `TODO` olarak işaretler:
  · `required` bayrakları — JSON'da izi yok (hepsi false varsayılır)
  · gruplama (`group_by`, `nested_field`, `item_fields`) — yalnızca sezilir
  · lookup ayarı (`lookup_dataset`, `lookup_key`, `lookup_fields`) — yalnızca sezilir

Kullanım:
    python3 deploy/sema-taslagi-uret.py                 # backend/backups → stdout
    python3 deploy/sema-taslagi-uret.py --yol <klasor>
"""
import argparse
import json
import sys
from pathlib import Path

try:
    import openpyxl
except ImportError:
    sys.exit("openpyxl gerekli:  pip install openpyxl")


def tip_bul(degerler: list) -> str:
    """Alanın tipini kaydedilmiş değerlerden çıkarır. null'lar yok sayılır."""
    dolu = [d for d in degerler if d is not None]
    if not dolu:
        return "string"
    if all(isinstance(d, bool) for d in dolu):
        return "boolean"
    # ⚠️ bool, Python'da int'in alt sınıfıdır — boolean kontrolü ÖNCE gelmeli.
    if all(isinstance(d, (int, float)) and not isinstance(d, bool) for d in dolu):
        return "number"
    return "string"


def dataset_isle(klasor: Path) -> dict | None:
    xlsx = sorted(klasor.glob("*.xlsx"))
    jsonlar = sorted(klasor.glob("*.json"))
    if not xlsx or not jsonlar:
        return None

    ws = openpyxl.load_workbook(xlsx[-1], read_only=True).active
    basliklar = [h.value for h in next(ws.iter_rows(min_row=1, max_row=1))]

    kayitlar = json.loads(jsonlar[-1].read_text(encoding="utf-8"))
    if not kayitlar:
        return None
    alanlar = list(kayitlar[0].keys())

    # Gruplama sezgisi: kayıtlarda liste tutan bir alan varsa o nested_field'dir.
    nested = next((a for a in alanlar if isinstance(kayitlar[0].get(a), list)), None)
    duz_alanlar = [a for a in alanlar if a != nested]

    kolonlar, uyarilar = [], []
    for alan in duz_alanlar:
        if alan in basliklar:
            baslik = alan
        else:
            baslik = f"TODO-EXCEL-BASLIGI({alan})"
            uyarilar.append(alan)
        kolonlar.append({
            "excel_header": baslik,
            "field": alan,
            "type": tip_bul([k.get(alan) for k in kayitlar[:500]]),
            "required": False,          # TODO: geri getirilemez, elle doğrulayın
        })

    sema: dict = {"columns": kolonlar}
    notlar = []

    # Eşleşmeyen alan sayısı, Excel başlığı sayısıyla aynı sırada mı? (workers_data durumu:
    # Türkçe başlıklar → İngilizce alan adları, sıra korunmuş.)
    if uyarilar and len(uyarilar) == len(duz_alanlar) and len(basliklar) >= len(duz_alanlar):
        notlar.append(
            "Hiçbir alan adı Excel başlığıyla eşleşmedi; başlıklar SIRAYLA eşlendi. "
            "Bu bir TAHMİNDİR — mutlaka gözle doğrulayın."
        )
        for i, kolon in enumerate(kolonlar):
            kolon["excel_header"] = basliklar[i]

    if nested:
        ic_alanlar = sorted({k for kayit in kayitlar[:500] for oge in kayit.get(nested) or [] for k in oge})
        # Grup anahtarı: nested olmayan ve Excel'de KARŞILIĞI OLMAYAN alanlar lookup adayıdır.
        grup_adaylari = [k["field"] for k in kolonlar if k["excel_header"] not in basliklar]
        sema.update({
            "group_by": f"TODO-GRUP-ANAHTARI({duz_alanlar[0]})",
            "nested_field": nested,
            "item_fields": ic_alanlar,
        })
        notlar.append(
            f"GRUPLAMA sezildi: '{nested}' alanı liste tutuyor. `group_by` hangi Excel kolonundan "
            f"geldiği kayıtlarda YAZMAZ — elle belirleyin. Aday alanlar: {grup_adaylari or duz_alanlar[:3]}"
        )
        if grup_adaylari:
            notlar.append(
                f"LOOKUP şüphesi: {grup_adaylari} alanları Excel'de YOK ama kayıtta VAR — "
                "başka bir dataset'ten çekilmiş olmalı. `lookup_dataset`/`lookup_key`/"
                "`lookup_fields` elle doldurulmalı."
            )
    elif uyarilar:
        notlar.append(f"Excel'de karşılığı bulunamayan alanlar: {uyarilar}")

    return {
        "dataset": klasor.name,
        "kayit_sayisi": len(kayitlar),
        "excel_kolon_sayisi": len(basliklar),
        "schema_definition": sema,
        "NOTLAR": notlar or ["Excel başlıklarıyla birebir eşleşti — yine de gözden geçirin."],
    }


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--yol", default="backend/backups", help="yedek kökü (varsayılan: backend/backups)")
    args = ap.parse_args()

    kok = Path(args.yol)
    if not kok.is_dir():
        sys.exit(f"klasör yok: {kok}")

    sonuc = [s for d in sorted(kok.iterdir()) if d.is_dir() and (s := dataset_isle(d))]
    if not sonuc:
        sys.exit(f"{kok} altında .xlsx + .json çifti bulunamadı")
    print(json.dumps(sonuc, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

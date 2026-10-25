#!/usr/bin/env bash
# TTI Saha Hakediş — TEMİZ BAŞLANGIÇ (proje/belge/sözleşme/kablo verisini siler)
#
# ⚠️ GERİ ALINAMAZ. Çalıştırmadan önce `bash ../tti-sunucu/yedek.sh` ZORUNLU.
#
# Ne SİLİNİR:
#   · Projeler ve belgeler  — is_kaydi, belge, belge_satir, ek, durum_kaydi + sayaçlar
#   · Sözleşmeler           — sozlesme, poz, fiyat_versiyonu, poz_fiyat
#   · Kablo envanteri       — kable, kable_surum, kable_surum_satiri
#   · SP No / belge no / mtc sayaçları — numaralar 001'den başlasın diye
#
# Ne KALIR (bilinçli):
#   · bolge, kapsam   — referans veri; silinirse uygulama açılmaz (EnumSenkronDogrulayici)
#   · kullanici       — silinirse KİMSE giremez. Kullanıcı çıkarmanın yolu arayüzden pasife almaktır.
#
# ⚠️ `TRUNCATE ... CASCADE` KULLANILMAZ (CLAUDE.md §13): cascade zinciri
#    `is_kaydi` → `fiyat_versiyonu` → `poz_fiyat` üzerinden ilerler ve niyet edilenden fazlasını siler.
#    Silme sırası aşağıda ELLE yazılıdır; yabancı anahtar bağımlılıklarının tersinden gider.
#
# Kullanım:
#   sunucuda:  ONAY=EVET DOCKER="sudo docker" bash deploy/temiz-baslangic.sh
#   yerelde :  ONAY=EVET bash deploy/temiz-baslangic.sh
#   dosyalar dahil:  ONAY=EVET DOSYALAR=EVET ARSIV_KOK=/srv/veri/tti-hakedis/arsiv \
#                    YUKLEME_KOK=/srv/veri/tti-hakedis/yukleme DOCKER="sudo docker" bash ...

set -euo pipefail

DOCKER="${DOCKER:-docker}"
DB_KAP="${DB_KAP:-tti-db}"
DB_ADI="${DB_ADI:-tti}"
DB_KULLANICI="${DB_KULLANICI:-tti_hakedis}"

kirmizi() { printf '\033[31m%s\033[0m\n' "$1"; }
yesil()   { printf '\033[32m%s\033[0m\n' "$1"; }
baslik()  { printf '\n\033[1m%s\033[0m\n' "$1"; }

pg() {
  $DOCKER exec -i -e PGOPTIONS=--search_path=hakedis "$DB_KAP" \
    psql -U "$DB_KULLANICI" -d "$DB_ADI" -tAc "$1"
}

# Arşiv ve yükleme klasörleri `1000:1000` sahipli; sunucuda `deploy-user` bunlara doğrudan bakamaz.
# Zaten root isek sudo çağırmayız (sudo kurulu olmayabilir).
ayricalikli() {
  if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo "$@"; fi
}

# --- Onay kapısı ------------------------------------------------------------------------------
if [ "${ONAY:-}" != "EVET" ]; then
  kirmizi "DURDU: bu betik projeleri, belgeleri, sözleşmeleri ve kablo envanterini SİLER."
  echo "Yedek aldıysanız ve devam etmek istiyorsanız:"
  echo "    ONAY=EVET DOCKER=\"sudo docker\" bash deploy/temiz-baslangic.sh"
  exit 1
fi

if ! $DOCKER exec "$DB_KAP" true 2>/dev/null; then
  kirmizi "Veritabanı konteynerine ulaşılamadı: $DB_KAP"
  echo "Sunucuda docker sudo ister: DOCKER=\"sudo docker\" ekleyin."
  exit 2
fi

# --- Öncesi -----------------------------------------------------------------------------------
sayimlar() {
  pg "select 'proje', count(*) from is_kaydi
      union all select 'belge', count(*) from belge
      union all select 'sözleşme', count(*) from sozlesme
      union all select 'poz', count(*) from poz
      union all select 'kablo', count(*) from kable
      union all select 'kullanıcı (KORUNUR)', count(*) from kullanici
      union all select 'bölge (KORUNUR)', count(*) from bolge;" | sed 's/|/: /'
}

baslik "Silme ÖNCESİ"
sayimlar

# --- Silme ------------------------------------------------------------------------------------
# Tek transaction: yarıda kalan bir temizlik, belgesi olmayan projeler gibi tutarsız bir hâl bırakır.
baslik "Siliniyor…"
$DOCKER exec -i -e PGOPTIONS=--search_path=hakedis "$DB_KAP" \
  psql -U "$DB_KULLANICI" -d "$DB_ADI" -v ON_ERROR_STOP=1 -q <<'SQL'
begin;

-- 1) Belgeye bağlı yapraklar (belge silinmeden bunlar gitmeli)
delete from durum_kaydi;
delete from ek;
delete from belge_satir;

-- 2) Belgeler ve projeler. belge.fiyat_versiyonu_id bu yüzden fiyat_versiyonu'ndan ÖNCE,
--    is_kaydi.kable_id bu yüzden kable'dan ÖNCE siliniyor.
delete from belge;
delete from is_kaydi;

-- 3) Numara sayaçları — yeni başlangıçta SP No ve belge no 001'den başlasın.
delete from belge_sayac;
delete from is_sayac;
delete from mtc_sayac;

-- 4) Kablo envanteri: izdüşüm ve sürüm satırları
delete from kable_surum_satiri;
delete from kable_surum;
delete from kable;

-- 5) Sözleşmeler: fiyatlar → sürüm → pozlar → sözleşme
delete from poz_fiyat;
delete from fiyat_versiyonu;
delete from poz;
delete from sozlesme;

commit;
SQL

baslik "Silme SONRASI"
sayimlar

# --- Dosyalar (opsiyonel) ---------------------------------------------------------------------
# Veritabanı boşaldığında arşivdeki PDF'ler ve yüklenen EK dosyaları ARTIK HİÇBİR KAYDA BAĞLI DEĞİL.
# Bırakılırlarsa "temiz başlangıç" diskte yarım kalır; silinirlerse geri dönüşü yedek dışında yoktur.
if [ "${DOSYALAR:-}" = "EVET" ]; then
  baslik "Dosyalar siliniyor"
  for kok in "${ARSIV_KOK:-}" "${YUKLEME_KOK:-}"; do
    if [ -z "$kok" ]; then
      kirmizi "ARSIV_KOK / YUKLEME_KOK verilmedi — dosya adımı atlandı."
      continue
    fi
    # ⚠️ Test AYRICALIKLI yapılır. Düz `[ -d ]` bu klasörlerde `deploy-user` olarak başarısız olur
    # (sahiplik 1000:1000 / root) ve betik bunu "klasör yok" diye yorumlayıp SESSİZCE atlar —
    # kullanıcı temizlendiğini sanır, dosyalar diskte kalır. Bir kez yaşandı (17 Ağustos 2026).
    if ! ayricalikli test -e "$kok"; then
      kirmizi "Klasör gerçekten yok, atlandı: $kok"
      continue
    fi
    if ! ayricalikli test -d "$kok"; then
      kirmizi "Klasör değil, atlandı: $kok"
      continue
    fi
    # Klasörün KENDİSİ silinmez, yalnızca içi boşaltılır: bind mount hedefi kaybolursa
    # konteyner yazamaz hâle gelir ve hata ancak ilk PDF üretiminde çıkar.
    ayricalikli find "$kok" -mindepth 1 -delete
    yesil "boşaltıldı: $kok ($(ayricalikli find "$kok" -mindepth 1 | wc -l | tr -d ' ') dosya kaldı)"
  done
else
  baslik "Dosyalar"
  echo "  · Atlandı. Arşiv ve yükleme klasörleri OLDUĞU GİBİ duruyor (artık kayıtsız dosyalar)."
  echo "  · Onları da temizlemek için: DOSYALAR=EVET ARSIV_KOK=... YUKLEME_KOK=... ekleyin."
fi

baslik "Bitti"
echo "Sıradaki iki adım ZORUNLU — ikisi yapılmadan proje açılamaz:"
echo "  1. Yönetici → Sözleşmeler → sözleşme .xlsx yükle"
echo "  2. Yönetici → Kablo Envanteri → cable_data_*.xlsx yükle → Fark → Yürürlüğe Al"

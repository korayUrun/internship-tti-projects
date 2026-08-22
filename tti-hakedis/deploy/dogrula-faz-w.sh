#!/bin/bash
# Faz W doğrulaması — "Yeni Tesis": Kablo ID sistemce (mtc…) atanır, uçlar zorunlu (12 Ağustos 2026).
#
#   SIFIRLA=1 bash deploy/dogrula-faz-w.sh   → iş/belge tablolarını temizle, sonra koştur
#   bash deploy/dogrula-faz-w.sh             → mevcut verinin üzerine koştur
#
# ⚠️ Betik GERÇEK proje, belge ve çıktı dosyası oluşturur; geliştirme veritabanı için yazıldı.
#
# Kapsam:
#   W1 — kablo zorunluluğu kapsama bağlı ve karar tek yerde (`Scope.kableZorunluMu`)
#   W2 — şema: üç sütun nullable, kapsam koşullu CHECK
#   W3 — Yeni Tesis projesi uçtan uca çalışıyor (uçlar zorunlu, mtc atanır, liste, belge, PDF, Excel)
#   W4 — uç segmentleri sunucuda `KonumYolu` ile birleşiyor; istekteki kableNosid yok sayılır (mtc)

set -uo pipefail
KOK="${KOK:-http://localhost:8081}"
KAYNAK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$KAYNAK/dogrula.sh"

DB_KAP="${DB_KAP:-tti-db}"
DB_ADI="${DB_ADI:-tti}"
DB_KULLANICI="${DB_KULLANICI:-tti_hakedis}"
SOZLESME="2026-guvenay-rota-1"
POZ="r5"
GECICI="${GECICI:-$(mktemp -d)}"

pg() {
  docker exec -i -e PGOPTIONS=--search_path=hakedis "$DB_KAP" psql -U "$DB_KULLANICI" -d "$DB_ADI" -tAc "$1" \
    | grep -v -E '^(INSERT|UPDATE|DELETE|SELECT|CREATE|DROP|ALTER) ' || true
}
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null || echo YOK; }
alan() { printf '%s' "$SON_GOVDE" | json "d['$1']"; }

GECEN=0; DUSEN=0
onay() {
  if [ "$2" = "$3" ]; then
    printf '  ✔ %-58s %s\n' "$1" "$3"; GECEN=$((GECEN+1))
  else
    printf '  ✘ %-58s beklenen=%s  gerçek=%s\n' "$1" "$2" "$3"; DUSEN=$((DUSEN+1))
  fi
}
not() { printf '  ℹ %s\n' "$1"; }

kimlik() {
  local deger
  deger=$(alan "$1")
  if [ "$deger" = "YOK" ]; then
    printf '  ✘ beklenmeyen yanıt (kod %s): %s\n' "$SON_KOD" "$SON_GOVDE" >&2
    exit 1
  fi
  printf '%s' "$deger"
}

indir() {
  SON_KOD=$(curl -s -b "$1" -o "$3" -w '%{http_code}' "$KOK$2")
}

yukle() {
  local jar="$1" yol="$2" dosya="$3" token yanit
  token=$(awk '/XSRF-TOKEN/{print $7}' "$jar")
  yanit=$(curl -s -b "$jar" -w $'\n%{http_code}' -X POST -H "X-XSRF-TOKEN: $token" -F "dosya=@$dosya" "$KOK$yol")
  SON_KOD=$(printf '%s' "$yanit" | tail -1)
  SON_GOVDE=$(printf '%s' "$yanit" | sed '$d')
}

ek1_yukle() {
  local kml="$GECICI/guzergah.kml"
  printf '<?xml version="1.0"?><kml/>' > "$kml"
  yukle "$1" "/api/belgeler/$2/ekler?tur=EK1_ROTA" "$kml"
}

# --- Sıfırlama --------------------------------------------------------------------------------
if [ "${SIFIRLA:-0}" = "1" ]; then
  echo "=== Sıfırlama ==="
  # ⚠️ `TRUNCATE ... CASCADE` KULLANILMAZ: cascade `is_kaydi` → `fiyat_versiyonu` → `poz_fiyat`
  # zincirini izleyip referans fiyat listesini de siler. Silme sırası elle yazılır.
  pg "delete from durum_kaydi;" > /dev/null
  pg "delete from ek;" > /dev/null
  pg "delete from belge_satir;" > /dev/null
  pg "delete from belge;" > /dev/null
  pg "delete from belge_sayac;" > /dev/null
  pg "delete from is_kaydi;" > /dev/null
  pg "delete from is_sayac;" > /dev/null
  onay "sıfırlama referans fiyatlara dokunmadı" "True" \
    "$([ "$(pg "select count(*) from poz_fiyat;")" -gt 100 ] && echo True || echo False)"
fi

JAR=$(giris izmir TTI666666)
JAR_YON=$(giris yonetici TTI000000)
NOSID=$(curl -s -b "$JAR" "$KOK/api/kableler?ara=mtc&limit=1" | sed -n 's/.*"nosid":"\([^"]*\)".*/\1/p')

# ==============================================================================================
echo "=== W2: şema ==="
# ==============================================================================================
for sutun in kable_id kable_nosid adres_a; do
  onay "is_kaydi.$sutun nullable" "YES" \
    "$(pg "select is_nullable from information_schema.columns where table_name='is_kaydi' and column_name='$sutun';")"
done
onay "kapsam koşullu CHECK var" "1" \
  "$(pg "select count(*) from pg_constraint where conname='is_kaydi_kable_zorunlu';")"
# CHECK gömülü kapsam kimliği taşıyor; seed kaymışsa kısıt yanlış kapsamı muaf tutardı.
onay "kapsam id 4 hâlâ YENI_TESIS" "YENI_TESIS" "$(pg "select kod from kapsam where id = 4;")"

# ==============================================================================================
echo "=== W1: kablo zorunluluğu kapsama bağlı ==="
# ==============================================================================================
KAPSAMLAR=$(curl -s -b "$JAR" "$KOK/api/kapsamlar")
onay "YENI_TESIS kablo zorunlu değil" "False" \
  "$(printf '%s' "$KAPSAMLAR" | json "[k['kableZorunlu'] for k in d if k['kod']=='YENI_TESIS'][0]")"
onay "diğer dört kapsamda zorunlu" "4" \
  "$(printf '%s' "$KAPSAMLAR" | json "sum(1 for k in d if k['kableZorunlu'])")"

# Muafiyet kapsama özel: kablosuz Deplase reddedilmeli.
cagir "$JAR" POST /api/isler '{"kapsamKodu":"DEPLASE_IYILESTIRME","route":"R-W","ekAraligi":"EK1-EK2"}'
onay "kablosuz Deplase reddedildi (400)" "400" "$SON_KOD"
onay "hata KableID'yi adıyla söylüyor" "1" "$(printf '%s' "$SON_GOVDE" | grep -c 'KableID')"

# Veritabanı da aynı kuralı koruyor: servis katmanı atlansa bile kısıt durduruyor.
KISIT_HATASI=$(docker exec -i -e PGOPTIONS=--search_path=hakedis "$DB_KAP" psql -U "$DB_KULLANICI" -d "$DB_ADI" -tAc \
  "insert into is_kaydi (bolge_id, kapsam_id, sp_no, route, ek_araligi, olusturan_id, yil)
   values (6, 1, 'KISIT-DENEME', 'r', 'e', 1, 2026);" 2>&1 | grep -c 'is_kaydi_kable_zorunlu')
onay "DB kısıtı kablosuz Deplase'yi reddediyor" "1" "$KISIT_HATASI"

# ==============================================================================================
echo "=== W3: Yeni Tesis projesi uçtan uca (uçlar zorunlu, mtc atanır) ==="
# ==============================================================================================
# Uçsuz Yeni Tesis reddedilir: uçlar (ülke/şehir/adres) artık zorunlu (12 Ağustos 2026).
cagir "$JAR" POST /api/isler '{"kapsamKodu":"YENI_TESIS","route":"R-YT0","ekAraligi":"EK0"}'
onay "uçsuz Yeni Tesis reddedildi (400)" "400" "$SON_KOD"

# Geçerli Yeni Tesis: iki uçta da ülke/şehir/adres dolu. Kablo ID'yi sistem üretir (mtc…).
cagir "$JAR" POST /api/isler '{"kapsamKodu":"YENI_TESIS","route":"R-YT","ekAraligi":"EK1-EK2",
  "ucA":{"segmentler":{"country":"Turkiye","city":"Izmir","address":"Konak POP"}},
  "ucB":{"segmentler":{"country":"Turkiye","city":"Izmir","address":"Bornova ODF"}}}'
onay "Yeni Tesis açıldı (201)" "201" "$SON_KOD"
IS_ID=$(kimlik id)
onay "Kablo ID mtc##### atandı" "1" "$(printf '%s' "$(alan kableNosid)" | grep -cE '^mtc[0-9]+$')"
onay "adresA dolduruldu" "1" "$([ -n "$(alan adresA)" ] && [ "$(alan adresA)" != "None" ] && echo 1 || echo 0)"
# mtc kimliği envantere EKLENMEZ → "aynı kablo üzerinde başka proje var" sorusunun konusu yok.
onay "AYNI_KABLE_VAR uyarısı üretilmedi" "0" "$(printf '%s' "$SON_GOVDE" | grep -c 'AYNI_KABLE_VAR')"

cagir "$JAR" GET "/api/isler/$IS_ID"
onay "kablosuz proje listeden okunabiliyor" "200" "$SON_KOD"

cagir "$JAR" POST "/api/isler/$IS_ID/belgeler" "$(printf '{"tur":"FIZIBILITE","sozlesmeKodu":"%s"}' "$SOZLESME")"
onay "kablosuz projede belge açıldı" "201" "$SON_KOD"
BELGE_ID=$(kimlik id)
cagir "$JAR" PUT "/api/belgeler/$BELGE_ID/satirlar" "$(printf '{"satirlar":[{"pozId":"%s","miktar":"2"}]}' "$POZ")"
ek1_yukle "$JAR" "$BELGE_ID"
cagir "$JAR" POST "/api/belgeler/$BELGE_ID/gonder"
onay "kablosuz projenin fizibilitesi gönderildi" "200" "$SON_KOD"

# Fazın asıl riski: çıktı üretimi kablo alanları null'ken düşüyor mu?
indir "$JAR" "/api/belgeler/$BELGE_ID/pdf?varyant=FIYATLI" "$GECICI/kablosuz.pdf"
onay "PDF üretildi" "200" "$SON_KOD"
onay "PDF gerçek bir PDF" "True" \
  "$([ "$(head -c 4 "$GECICI/kablosuz.pdf")" = "%PDF" ] && echo True || echo False)"
indir "$JAR" "/api/belgeler/$BELGE_ID/excel?varyant=FIYATLI" "$GECICI/kablosuz.xlsx"
onay "Excel üretildi" "200" "$SON_KOD"
indir "$JAR" "/api/isler/liste.xlsx" "$GECICI/liste.xlsx"
onay "proje listesi Excel'i üretildi" "200" "$SON_KOD"

# ==============================================================================================
echo "=== W4: uç segmentleri sunucuda birleşiyor ==="
# ==============================================================================================
cagir "$JAR" POST /api/isler '{"kapsamKodu":"YENI_TESIS","route":"R-YT2","ekAraligi":"EK3-EK4",
  "ucA":{"segmentler":{"country":"Turkiye","city":"Izmir","address":"Konak POP","building":"AND Plaza","floor":"Kat 1"}},
  "ucB":{"segmentler":{"country":"Turkiye","city":"Izmir","address":"Bornova ODF"}}}'
onay "uç segmentleriyle proje açıldı" "201" "$SON_KOD"
# Bina ve kat TEK segmentte (`>`), diğerleri `>>` ile — kural `KonumYolu`'nda, istemcide değil.
onay "Uç A konum yolu sunucuda kuruldu" "Turkiye >> Izmir >> Konak POP >> AND Plaza > Kat 1" "$(alan adresA)"
onay "Uç B konum yolu kuruldu" "Turkiye >> Izmir >> Bornova ODF" "$(alan adresB)"
onay "Kablo ID mtc##### atandı" "1" "$(printf '%s' "$(alan kableNosid)" | grep -cE '^mtc[0-9]+$')"

# Yeni Tesis'te istekte gelen kableNosid YOK SAYILIR: Kablo ID'yi sistem üretir (mtc…), envantere
# eklenmez. Uçlar (iki uç da) zorunlu — elle girilen uçlar korunur.
cagir "$JAR" POST /api/isler '{"kapsamKodu":"YENI_TESIS","kableNosid":"ELLE-YAZILDI","route":"R-YT3","ekAraligi":"EK5-EK6",
  "ucA":{"segmentler":{"country":"Turkiye","city":"Bursa","address":"Yeni Tesis Sahasi"}},
  "ucB":{"segmentler":{"country":"Turkiye","city":"Bursa","address":"Bursa ODF"}}}'
onay "Yeni Tesis açıldı (201)" "201" "$SON_KOD"
IS_SERBEST=$(kimlik id)
onay "istekteki kableNosid yok sayıldı, mtc atandı" "1" "$(printf '%s' "$(alan kableNosid)" | grep -cE '^mtc[0-9]+$')"
onay "elle girilen uç korundu" "Turkiye >> Bursa >> Yeni Tesis Sahasi" "$(alan adresA)"
onay "envanterle bağ kurulmadı (kable_id boş, nosid mtc)" "1" \
  "$(pg "select count(*) from is_kaydi where id = $IS_SERBEST and kable_id is null and kable_nosid ~ '^mtc[0-9]+\$';")"

# İki uçtan biri eksikse (yalnızca Uç A dolu) Yeni Tesis reddedilir: her iki uç zorunlu.
cagir "$JAR" POST /api/isler '{"kapsamKodu":"YENI_TESIS","route":"R-YT4","ekAraligi":"EK",
  "ucA":{"segmentler":{"country":"Turkiye","city":"Bursa","address":"Tek Uc"}}}'
onay "tek uçlu Yeni Tesis reddedildi (400)" "400" "$SON_KOD"

# Zorunlu kapsamda envanterde olmayan kimlik hâlâ reddedilmeli: muafiyet kapsama özel.
cagir "$JAR" POST /api/isler '{"kapsamKodu":"DEPLASE_IYILESTIRME","kableNosid":"Bornova YT-1","route":"R-D2","ekAraligi":"EK9"}'
onay "Deplase'de olmayan kimlik reddedildi (404)" "404" "$SON_KOD"

# Diğer kapsamlar bugünkü gibi çalışmaya devam ediyor: envanterden seçim, uçlar oradan.
cagir "$JAR" POST /api/isler "$(printf '{"kapsamKodu":"DEPLASE_IYILESTIRME","kableNosid":"%s","route":"R-D","ekAraligi":"EK7-EK8",
  "ucA":{"segmentler":{"country":"YOKSAYILMALI"}}}' "$NOSID")"
onay "kablolu Deplase hâlâ açılıyor" "201" "$SON_KOD"
onay "adres envanterden geldi" "0" "$(printf '%s' "$SON_GOVDE" | grep -c 'YOKSAYILMALI')"

not "kablosuz proje sayısı: $(pg "select count(*) from is_kaydi where kable_id is null;")"

# ==============================================================================================
printf '\n=== Özet: %d geçti, %d düştü ===\n' "$GECEN" "$DUSEN"
[ "$DUSEN" -eq 0 ]

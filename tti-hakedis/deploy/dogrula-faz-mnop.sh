#!/bin/bash
# Faz M–P doğrulaması — sadeleştirme turunun tamamı, tek betikte.
#
#   SIFIRLA=1 bash deploy/dogrula-faz-mnop.sh   → iş/belge tablolarını temizle, sonra koştur
#   bash deploy/dogrula-faz-mnop.sh             → mevcut verinin üzerine koştur
#
# ⚠️ Betik GERÇEK iş, belge ve çıktı dosyası oluşturur; geliştirme veritabanı için yazıldı.
#
# **Neden faz başına ayrı betik değil:** M, N, O ve P tek bir kopuk değişiklik değil, birbirine
# bağlı bir bütün — sözleşme belgeye taşınmadan durum makinesi çalışmıyor, durum makinesi olmadan
# aşama kapısı denetlenemiyor. Dört ayrı betik aynı 80 satırlık hazırlık ve sıfırlama bloğunu dört
# kez taşırdı.
#
# Kapsam:
#   M — onay akışı ve imza kalktı, bölge sıra kuralı kalktı, "son güncelleyen" izi
#   N — sözleşme işten belgeye taşındı, işe özel pazarlık fiyatı kalktı
#   O — iş durum makinesi (Açık/İşleniyor/Kapandı/İptal), otomatik SP No, serbest kable araması
#   P — 2 hane yuvarlama, miktar Excel'i, üç belgede zorunlu güzergâh dosyası
#   + iş listesi Excel çıktısı (aynı hazırlığı kullandığı için ayrı betik açılmadı)

set -uo pipefail
KOK="${KOK:-http://localhost:8081}"
KAYNAK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$KAYNAK/dogrula.sh"

DB_KAP="${DB_KAP:-tti-db}"
DB_ADI="${DB_ADI:-tti}"
DB_KULLANICI="${DB_KULLANICI:-tti_hakedis}"
SOZLESME="2026-guvenay-rota-1"
SOZLESME_2="2026guvenay-rota-2"
KAPSAM="DEPLASE_IYILESTIRME"
POZ="r5"
BOLGE_DOSYA_KODU="IZMIR"
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
esler() {
  if [[ "$3" =~ $2 ]]; then
    printf '  ✔ %-58s %s\n' "$1" "$3"; GECEN=$((GECEN+1))
  else
    printf '  ✘ %-58s kalıp=%s  gerçek=%s\n' "$1" "$2" "$3"; DUSEN=$((DUSEN+1))
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
  local jar="$1" yol="$2" hedef="$3" basliklar="$GECICI/basliklar.txt"
  SON_KOD=$(curl -s -b "$jar" -D "$basliklar" -o "$hedef" -w '%{http_code}' "$KOK$yol")
  SON_DOSYA_ADI=$(grep -i '^content-disposition' "$basliklar" | sed -n 's/.*filename="\{0,1\}\([^";]*\).*/\1/p' | tr -d '\r')
}

yukle() {
  local jar="$1" yol="$2" dosya="$3" token yanit
  token=$(awk '/XSRF-TOKEN/{print $7}' "$jar")
  yanit=$(curl -s -b "$jar" -w $'\n%{http_code}' -X POST -H "X-XSRF-TOKEN: $token" \
    -F "dosya=@$dosya" "$KOK$yol")
  SON_KOD=$(printf '%s' "$yanit" | tail -1)
  SON_GOVDE=$(printf '%s' "$yanit" | sed '$d')
}

is_govdesi() {
  printf '{"kapsamKodu":"%s","kableNosid":"%s","route":"R-MNOP","ekAraligi":"EK1-EK9"}' "$KAPSAM" "$1"
}
belge_govdesi() { printf '{"tur":"%s","sozlesmeKodu":"%s"}' "$1" "${2:-$SOZLESME}"; }
miktar_govdesi() { printf '{"satirlar":[{"pozId":"%s","miktar":"%s"}]}' "$POZ" "$1"; }

# Güzergâh dosyası artık ÜÇ belgede de zorunlu; her gönderimden önce yüklenmeli.
ek1_yukle() {
  local jar="$1" belgeId="$2" kml="$GECICI/guzergah.kml"
  printf '<?xml version="1.0"?><kml/>' > "$kml"
  yukle "$jar" "/api/belgeler/$belgeId/ekler?tur=EK1_ROTA" "$kml"
}

# --- Sıfırlama --------------------------------------------------------------------------------
if [ "${SIFIRLA:-0}" = "1" ]; then
  echo "=== Sıfırlama ==="
  # ⚠️ `TRUNCATE ... CASCADE` KULLANILMAZ: cascade zinciri `is_kaydi` → `fiyat_versiyonu` →
  # `poz_fiyat` üzerinden referans fiyat listesini de siler. Silme sırası elle yazılır.
  # `belge_imza` ve işe özel `fiyat_versiyonu` satırları artık YOK (V9/V10).
  pg "delete from ek;" > /dev/null
  pg "delete from belge_satir;" > /dev/null
  pg "delete from belge;" > /dev/null
  pg "delete from belge_sayac;" > /dev/null
  pg "delete from is_kaydi;" > /dev/null
  pg "delete from is_sayac;" > /dev/null
  # Sabit sayı değil "boşalmadı" kontrolü: geliştirme veritabanına sonradan sözleşme yüklenmiş olabilir.
  onay "sıfırlama referans fiyatlara dokunmadı" "True" \
    "$([ "$(pg "select count(*) from poz_fiyat;")" -gt 100 ] && echo True || echo False)"
fi

JAR=$(giris izmir TTI666666)
JAR_YON=$(giris yonetici TTI000000)
JAR_ANK=$(giris ankara TTI444444)

# ==============================================================================================
echo "=== Faz M: onay akışı ve imza kalktı ==="
# ==============================================================================================
# `belge.durum` bir PG enum değil, check kısıtlı text sütunu; kısıt metni okunuyor.
BELGE_DURUM_KISITI=$(pg "select pg_get_constraintdef(oid) from pg_constraint where conname = 'belge_durum_check';")
# Faz V: `ONAY_BEKLIYOR` geri geldi (keşif/hakediş için); `ONAYLANDI` gelmedi — onaylanmış belge
# `TAMAMLANDI`'dır ve ayrı bir değere ihtiyaç yok.
onay "kısıtta ONAY_BEKLIYOR var (Faz V)" "1" "$(grep -c ONAY_BEKLIYOR <<< "$BELGE_DURUM_KISITI")"
onay "kısıtta ONAYLANDI yok" "0" "$(grep -c 'ONAYLANDI' <<< "${BELGE_DURUM_KISITI//ONAY_BEKLIYOR/}")"
onay "kısıtta TAMAMLANDI var" "1" "$(grep -c TAMAMLANDI <<< "$BELGE_DURUM_KISITI")"
onay "onaylayan_id sütunu düştü" "0" \
  "$(pg "select count(*) from information_schema.columns where table_name='belge' and column_name='onaylayan_id';")"
onay "belge_imza tablosu düştü" "0" \
  "$(pg "select count(*) from information_schema.tables where table_name='belge_imza';")"
onay "bölge sıra indeksi düştü" "0" \
  "$(pg "select count(*) from pg_indexes where indexname='belge_acik_tur_ux';")"
onay "guncelleme sütunu NOT NULL" "NO" \
  "$(pg "select is_nullable from information_schema.columns where table_name='belge' and column_name='guncelleme';")"

SON_KOD=$(durum "$JAR" "/api/belgeler/1/imza")
onay "imza ucu kaldırıldı (404)" "404" "$SON_KOD"

# ==============================================================================================
echo "=== Faz N: sözleşme belgeye taşındı ==="
# ==============================================================================================
onay "is_kaydi.sozlesme_id düştü" "0" \
  "$(pg "select count(*) from information_schema.columns where table_name='is_kaydi' and column_name='sozlesme_id';")"
onay "fiyat_versiyonu.is_id düştü" "0" \
  "$(pg "select count(*) from information_schema.columns where table_name='fiyat_versiyonu' and column_name='is_id';")"
# ⚠️ `drop column is_id` bu indeksi sessizce düşürdü; V10 yeniden kurdu. Kurmasaydı bir sözleşme iki
# YAYINDA sürüme sahip olabilir ve `referansFiyatVersiyonuId` `optional()` üzerinde patlardı.
onay "yayın tekilliği indeksi yeniden kuruldu" "1" \
  "$(pg "select count(*) from pg_indexes where indexname='fiyat_versiyonu_yayin_ux';")"
# ARSIV sürümleri meşru olarak duruyor; tekil olması gereken YAYINDA sürümüdür.
onay "sözleşme başına tek YAYINDA sürüm" "0" \
  "$(pg "select count(*) from (select sozlesme_id from fiyat_versiyonu where durum = 'YAYINDA' group by sozlesme_id having count(*) <> 1) t;")"

# ==============================================================================================
echo "=== Faz O: SP No üretimi ve bölge ==="
# ==============================================================================================
# Bölge kısıtı kalktı: İzmir sorumlusu Ankara'nın kablosuyla iş açabilir, ama iş KENDİ bölgesine yazılır.
ANK_NOSID=$(curl -s -b "$JAR_ANK" "$KOK/api/kableler?ara=mtc&limit=1" | sed -n 's/.*"nosid":"\([^"]*\)".*/\1/p')
cagir "$JAR" POST /api/isler "$(is_govdesi "$ANK_NOSID")"
onay "başka bölgenin kablosuyla iş açılabiliyor" "201" "$SON_KOD"
IS_ID=$(kimlik id)
onay "iş, işi AÇANIN bölgesine yazıldı" "IZMIR" "$(alan bolgeKodu)"
SP_1=$(alan spNo)
esler "SP No üretildi" "^[0-9]{4}-${BOLGE_DOSYA_KODU}-[0-9]{3}$" "$SP_1"
onay "yeni işin durumu yok (fizibilite bekleniyor)" "None" "$(alan durum)"
onay "durum metni sunucudan geliyor" "Fizibilite Bekleniyor" "$(alan durumMetni)"

cagir "$JAR" POST /api/isler "$(is_govdesi "$ANK_NOSID")"
IS_ID_2=$(kimlik id)
SP_2=$(alan spNo)
onay "SP No ardışık" "1" "$(python3 -c "print(int('${SP_2##*-}') - int('${SP_1##*-}'))")"

# Yönetici iş açamaz: bölgesi yok, dolayısıyla işin hangi bölgeye yazılacağı belirsiz.
cagir "$JAR_YON" POST /api/isler "$(is_govdesi "$ANK_NOSID")"
onay "yönetici iş açamaz (409)" "409" "$SON_KOD"

# ==============================================================================================
echo "=== Faz O: aşama kapısı iş durumuna bağlı ==="
# ==============================================================================================
cagir "$JAR" POST "/api/isler/$IS_ID/belgeler" "$(belge_govdesi FIZIBILITE)"
onay "sözleşmeyle fizibilite açıldı" "201" "$SON_KOD"
FIZ_ID=$(kimlik id)
onay "belge kendi sözleşmesini taşıyor" "$SOZLESME" "$(alan sozlesmeKodu)"
onay "fizibilite açılınca iş 'Açık' oldu" "ACIK" "$(cagir "$JAR" GET "/api/isler/$IS_ID"; alan durum)"

# Temiz bir işte denenir: aynı işte ikinci fizibilite aşama kuralına (409) takılır ve sözleşme
# kontrolüne hiç gelinmez.
cagir "$JAR" POST /api/isler "$(is_govdesi "$ANK_NOSID")"
IS_ID_TEMIZ=$(kimlik id)
cagir "$JAR" POST "/api/isler/$IS_ID_TEMIZ/belgeler" '{"tur":"FIZIBILITE"}'
onay "sözleşmesiz belge açılamaz (400)" "400" "$SON_KOD"

cagir "$JAR" POST "/api/isler/$IS_ID/belgeler" "$(belge_govdesi KESIF)"
onay "iş işleme alınmadan keşif açılamaz (409)" "409" "$SON_KOD"

cagir "$JAR" POST "/api/isler/$IS_ID/durum" '{"durum":"ISLENIYOR","gerekce":"islemeal"}'
onay "bölge sorumlusu durum değiştiremez (403)" "403" "$SON_KOD"

cagir "$JAR_YON" POST "/api/isler/$IS_ID/durum" '{"durum":"ISLENIYOR","gerekce":"islemeal"}'
onay "yönetici işi işleme aldı" "200" "$SON_KOD"
onay "iş durumu İşleniyor" "ISLENIYOR" "$(alan durum)"

cagir "$JAR" PUT "/api/belgeler/$FIZ_ID/satirlar" "$(miktar_govdesi 5)"
onay "İşleniyor'da fizibilite kilitlendi (409)" "409" "$SON_KOD"

# ==============================================================================================
echo "=== Faz M: bölge sıra kuralı kalktı ==="
# ==============================================================================================
# İkinci iş de aynı anda keşfe geçebilmeli: eskiden bölgede aynı türden tek açık belge olabiliyordu.
# Aşama sırası (fizibilite kapanmadan keşif yok) KORUNUYOR; kalkan yalnızca BÖLGE sırasıydı.
cagir "$JAR" POST "/api/isler/$IS_ID_2/belgeler" "$(belge_govdesi FIZIBILITE)"
FIZ_ID_2=$(kimlik id)
cagir "$JAR" PUT "/api/belgeler/$FIZ_ID_2/satirlar" "$(miktar_govdesi 1)"
ek1_yukle "$JAR" "$FIZ_ID_2"
cagir "$JAR" POST "/api/belgeler/$FIZ_ID_2/gonder"
onay "ikinci işin fizibilitesi gönderildi" "200" "$SON_KOD"
cagir "$JAR_YON" POST "/api/isler/$IS_ID_2/durum" '{"durum":"ISLENIYOR","gerekce":"islemeal"}'

# Birinci işin fizibilitesi de kapanmalı; "İşleniyor"da kilitli olduğu için satır yazılamıyor,
# miktar İşleme Al'dan ÖNCE girilmişti mi? Hayır — bu yüzden burada yeni bir fizibilite açılamaz.
# Bilinçli: birinci iş bu betikte fizibilitesi taslak kilitli kalan örnek olarak duruyor.
cagir "$JAR" POST "/api/isler/$IS_ID_2/belgeler" "$(belge_govdesi KESIF)"
KESIF_ID=$(kimlik id)
# İkinci bir işte de eşzamanlı keşif: eskiden bölgede aynı türden tek açık belge olabiliyordu.
cagir "$JAR" POST /api/isler "$(is_govdesi "$ANK_NOSID")"
IS_ID_4=$(kimlik id)
cagir "$JAR" POST "/api/isler/$IS_ID_4/belgeler" "$(belge_govdesi FIZIBILITE)"
FIZ_ID_4=$(kimlik id)
cagir "$JAR" PUT "/api/belgeler/$FIZ_ID_4/satirlar" "$(miktar_govdesi 1)"
ek1_yukle "$JAR" "$FIZ_ID_4"
cagir "$JAR" POST "/api/belgeler/$FIZ_ID_4/gonder"
cagir "$JAR_YON" POST "/api/isler/$IS_ID_4/durum" '{"durum":"ISLENIYOR","gerekce":"islemeal"}'
cagir "$JAR" POST "/api/isler/$IS_ID_4/belgeler" "$(belge_govdesi KESIF)"
onay "aynı bölgede iki keşif aynı anda açık" "201" "$SON_KOD"
KESIF_ID_2=$(kimlik id)
not "iki açık keşif: #$KESIF_ID ve #$KESIF_ID_2"

# ==============================================================================================
echo "=== Faz N: her belge kendi sözleşmesini seçer ==="
# ==============================================================================================
cagir "$JAR" PUT "/api/belgeler/$KESIF_ID/sozlesme" "$(printf '{"sozlesmeKodu":"%s"}' "$SOZLESME_2")"
onay "keşif farklı sözleşmeye geçebildi" "200" "$SON_KOD"
onay "belgenin sözleşmesi değişti" "$SOZLESME_2" "$(alan sozlesmeKodu)"
cagir "$JAR" PUT "/api/belgeler/$KESIF_ID/sozlesme" "$(printf '{"sozlesmeKodu":"%s"}' "$SOZLESME")"
onay "geri alındı" "$SOZLESME" "$(alan sozlesmeKodu)"

# ==============================================================================================
echo "=== Faz P: 2 hane yuvarlama ==="
# ==============================================================================================
cagir "$JAR" PUT "/api/belgeler/$KESIF_ID/satirlar" "$(miktar_govdesi 2)"
SATIR=$(printf '%s' "$SON_GOVDE" | python3 -c "import sys,json;d=json.load(sys.stdin);print(json.dumps(d['satirlar'][0]))")
# Sabit fiyat yazılmıyor: sözleşmenin yayındaki sürümü geliştirme veritabanında değişmiş olabilir.
# Kilitlenen şey **değişmezin kendisi**, tek bir sayı değil.
onay "birim fiyat en fazla 2 ondalık" "True" \
  "$(printf '%s' "$SATIR" | python3 -c "
import sys, json
from decimal import Decimal
d = json.load(sys.stdin)
print(-Decimal(d['birimFiyat']).as_tuple().exponent <= 2)
")"
onay "gösterimdeki sayı, çivilenen fiyatın aynısı" "True" \
  "$(printf '%s' "$SATIR" | python3 -c "
import sys, json
from decimal import Decimal
d = json.load(sys.stdin)
gosterim = d['birimFiyatGosterim'].replace(' ₺', '').replace('.', '').replace(',', '.')
print(Decimal(gosterim) == Decimal(d['birimFiyat']))
")"
# Asıl kilit bu: gösterilen fiyat × miktar, gösterilen tutara TAM eşit olmalı. 30 Temmuz kurgusunda
# fiyat ham, tutar yuvarlıydı ve kullanıcı hesabı hatalı sanıyordu.
onay "fiyat × miktar = tutar (tam)" "True" \
  "$(printf '%s' "$SATIR" | python3 -c "
import sys, json
from decimal import Decimal
d = json.load(sys.stdin)
print(Decimal(d['birimFiyat']) * Decimal(d['miktar']) == Decimal(d['tutar']))
")"
# İddia isme değil **oturumdaki kullanıcıya** bağlı: ad soyad gerçek personel adı olduğu için
# (V12) değişebilir, ama "izi bırakan kişi belgeyi güncelleyendir" kuralı değişmez.
SON_GUNCELLEYEN=$(alan sonGuncelleyenAdSoyad)
BEKLENEN_AD=$(curl -s -b "$JAR" "$KOK/api/auth/ben" | json "d['adSoyad']")
onay "son güncelleyen izi oturumdaki kullanıcı" "$BEKLENEN_AD" "$SON_GUNCELLEYEN"

# ==============================================================================================
echo "=== Faz P: miktar Excel'i ==="
# ==============================================================================================
SABLON="$GECICI/miktar.xlsx"
indir "$JAR" "/api/belgeler/$KESIF_ID/miktar-sablonu.xlsx" "$SABLON"
onay "miktar şablonu indi" "200" "$SON_KOD"
esler "dosya adı sunucudan geldi" "MIKTAR_SABLONU\.xlsx$" "$SON_DOSYA_ADI"
onay "şablonda POZ_KIMLIK sütunu var" "1" \
  "$(unzip -p "$SABLON" 'xl/sharedStrings.xml' | grep -c 'POZ_KIMLIK')"
onay "şablonda Miktar sütunu var" "1" \
  "$(unzip -p "$SABLON" 'xl/sharedStrings.xml' | grep -c '>Miktar<')"

# Şablon indiği gibi geri yüklenirse mevcut miktarlar korunmalı (şablon dolu iniyor).
yukle "$JAR" "/api/belgeler/$KESIF_ID/miktar-yukleme" "$SABLON"
onay "doldurulmuş şablon yüklendi" "200" "$SON_KOD"
onay "mevcut miktar korundu" "1" \
  "$(printf '%s' "$SON_GOVDE" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['satirlar']))")"

# Bozuk dosya: hiçbir satır yazılmamalı.
printf 'bu bir xlsx degil' > "$GECICI/bozuk.xlsx"
yukle "$JAR" "/api/belgeler/$KESIF_ID/miktar-yukleme" "$GECICI/bozuk.xlsx"
onay "bozuk dosya reddedildi (400)" "400" "$SON_KOD"
onay "reddedilen yükleme satırlara dokunmadı" "1" \
  "$(pg "select count(*) from belge_satir where belge_id = $KESIF_ID;")"

# ==============================================================================================
echo "=== Faz P: güzergâh dosyası üç belgede de zorunlu ==="
# ==============================================================================================
cagir "$JAR" POST "/api/belgeler/$KESIF_ID/gonder"
onay "EK1'siz keşif gönderilemez (409)" "409" "$SON_KOD"
ek1_yukle "$JAR" "$KESIF_ID"
onay "keşife EK1 yüklenebildi (eskiden yalnızca hakediş)" "201" "$SON_KOD"
cagir "$JAR" POST "/api/belgeler/$KESIF_ID/gonder"
onay "EK1 sonrası keşif gönderildi" "200" "$SON_KOD"
# Faz V: keşif gönderilince kapanmıyor, yönetici onayına düşüyor.
onay "keşif onay bekliyor (Faz V)" "ONAY_BEKLIYOR" "$(alan durum)"
cagir "$JAR_YON" POST "/api/belgeler/$KESIF_ID/onayla"
onay "yönetici keşfi onayladı" "200" "$SON_KOD"
onay "keşif tamamlandı" "TAMAMLANDI" "$(alan durum)"

# Fizibilite de EK1 istiyor — ikinci işin fizibilitesi hâlâ taslak değil, kilitli; üçüncü iş açalım.
cagir "$JAR" POST /api/isler "$(is_govdesi "$ANK_NOSID")"
IS_ID_3=$(kimlik id)
cagir "$JAR" POST "/api/isler/$IS_ID_3/belgeler" "$(belge_govdesi FIZIBILITE)"
FIZ_ID_3=$(kimlik id)
cagir "$JAR" PUT "/api/belgeler/$FIZ_ID_3/satirlar" "$(miktar_govdesi 1)"
cagir "$JAR" POST "/api/belgeler/$FIZ_ID_3/gonder"
onay "EK1'siz fizibilite gönderilemez (409)" "409" "$SON_KOD"
ek1_yukle "$JAR" "$FIZ_ID_3"
cagir "$JAR" POST "/api/belgeler/$FIZ_ID_3/gonder"
onay "EK1 sonrası fizibilite gönderildi" "200" "$SON_KOD"
onay "fizibilite de TAMAMLANDI (onay yok)" "TAMAMLANDI" "$(alan durum)"

# ==============================================================================================
echo "=== Faz O: hakediş gönderilince iş kapanır ==="
# ==============================================================================================
cagir "$JAR" POST "/api/isler/$IS_ID_2/belgeler" "$(belge_govdesi HAKEDIS)"
onay "keşif kapanınca hakediş açıldı" "201" "$SON_KOD"
HAK_ID=$(kimlik id)
cagir "$JAR" PUT "/api/belgeler/$HAK_ID/satirlar" "$(miktar_govdesi 3)"
ek1_yukle "$JAR" "$HAK_ID"
cagir "$JAR" POST "/api/belgeler/$HAK_ID/gonder"
onay "hakediş gönderildi (imza istenmedi)" "200" "$SON_KOD"

# Faz V: kapanış GÖNDERİMDE değil ONAYDA olur.
cagir "$JAR" GET "/api/isler/$IS_ID_2"
onay "hakediş gönderimi işi kapatmıyor" "ISLENIYOR" "$(alan durum)"
cagir "$JAR_YON" POST "/api/belgeler/$HAK_ID/onayla"
onay "yönetici hakedişi onayladı" "200" "$SON_KOD"

cagir "$JAR" GET "/api/isler/$IS_ID_2"
onay "onaydan sonra iş kapandı" "KAPANDI" "$(alan durum)"

cagir "$JAR" POST "/api/isler/$IS_ID_2/belgeler" "$(belge_govdesi KESIF)"
onay "kapanmış işe yeni belge açılamaz (409)" "409" "$SON_KOD"

# Faz V: yönetici artık her durumdan her duruma geçebiliyor; kapanmış iş de iptal edilebilir.
cagir "$JAR_YON" POST "/api/isler/$IS_ID_2/durum" '{"durum":"IPTAL","gerekce":"deneme"}'
onay "kapanmış iş de iptal edilebiliyor (Faz V)" "200" "$SON_KOD"

cagir "$JAR_YON" POST "/api/isler/$IS_ID_4/durum" '{"durum":"IPTAL","gerekce":"sahada düştü"}'
onay "İşleniyor durumundaki iş iptal edilebiliyor" "200" "$SON_KOD"
cagir "$JAR_YON" POST "/api/isler/$IS_ID/durum" '{"durum":"IPTAL"}'
onay "gerekçesiz iptal reddedildi (400)" "400" "$SON_KOD"

# ==============================================================================================
echo "=== Faz P: çıktıda 2 hane ==="
# ==============================================================================================
PDF="$GECICI/hakedis.pdf"
indir "$JAR" "/api/belgeler/$HAK_ID/pdf?varyant=FIYATLI" "$PDF"
onay "PDF indi" "200" "$SON_KOD"
onay "PDF üretildi (baytlar)" "1" "$([ -s "$PDF" ] && echo 1 || echo 0)"

XLSX="$GECICI/hakedis.xlsx"
indir "$JAR" "/api/belgeler/$HAK_ID/excel?varyant=FIYATLI" "$XLSX"
onay "Excel indi" "200" "$SON_KOD"
# Hücre değeri ham değil yuvarlı olmalı: Excel formatı 2 hane gösterse de değer 14 ondalık kalsaydı
# dosyayı açan kişi hücreye tıkladığında farklı bir sayı görürdü.
onay "Excel'de ham 14 ondalıklı fiyat yok" "0" \
  "$(unzip -p "$XLSX" 'xl/worksheets/sheet1.xml' | grep -c '648.35852723050107')"

# ==============================================================================================
echo "=== Faz N/P: sözleşme yönetimi iki eyleme indi ==="
# ==============================================================================================
FORMAT="$GECICI/format.xlsx"
indir "$JAR_YON" "/api/sozlesmeler/format.xlsx" "$FORMAT"
onay "sözleşme formatı indi" "200" "$SON_KOD"
onay "formatta ISC_POZNO başlığı var" "1" \
  "$(unzip -p "$FORMAT" 'xl/sharedStrings.xml' | grep -c 'ISC_POZNO')"
SON_KOD=$(durum "$JAR" "/api/sozlesmeler/format.xlsx")
onay "bölge sorumlusu formatı indiremez (403)" "403" "$SON_KOD"

SON_KOD=$(durum "$JAR_YON" "/api/sozlesmeler/$SOZLESME/fiyatlar")
onay "fiyat yönetimi ucu kaldırıldı (404)" "404" "$SON_KOD"

# ==============================================================================================
echo "=== Excel sütun düzeni ve belge aynası (eski dogrula-faz-g.sh'den devralındı) ==="
# ==============================================================================================
# fastexcel ASCII dışı harfleri `&#x15f;` gibi yazar; ham XML'de "Hakediş" aramak DAİMA boş döner ve
# kontrol sessizce "geçmiş" görünür. Bu yüzden kaçışlar çözülerek aranıyor.
dizeler() {
  unzip -p "$1" 'xl/sharedStrings.xml' 2>/dev/null | python3 -c "
import re, sys
print(re.sub(r'&#x([0-9a-fA-F]+);', lambda e: chr(int(e.group(1), 16)), sys.stdin.read()))
"
}
SAYFA=$(dizeler "$XLSX")
# Hakediş kaynağın 8 sütununu kullanır: keşif sol çifti, hakediş sağ çifti doldurur.
onay "hakediş kaynağın 4 miktar/tutar başlığını taşıyor" "4" \
  "$(printf '%s' "$SAYFA" | grep -o -E '1\. Keşif Talep|Saha Uygulama|1\.Keşif Tutar|Kesin Hakediş Tutar' | sort -u | wc -l | tr -d ' ')"
onay "Excel sözleşmenin aynası (girilmemiş kalemler de var)" "True" \
  "$([ "$(unzip -p "$XLSX" 'xl/worksheets/sheet1.xml' | grep -o '<row ' | wc -l)" -gt 90 ] && echo True || echo False)"

ARSIV_HOST="$(grep -E '^ARSIV_KOK=' "$KAYNAK/.env" 2>/dev/null | cut -d= -f2-)"
ARSIV_HOST="${ARSIV_HOST:-./arsiv}"
case "$ARSIV_HOST" in /*) : ;; *) ARSIV_HOST="$KAYNAK/${ARSIV_HOST#./}" ;; esac
HAK_NO=$(pg "select belge_no from belge where id = $HAK_ID;")
ARSIV_DIZIN="$ARSIV_HOST/YedekTTI/$BOLGE_DOSYA_KODU/Hakedis"
# Arşiv kopyası asenkron yazılıyor; kısa süre beklenir.
for _ in $(seq 1 20); do [ -f "$ARSIV_DIZIN/TTI_${HAK_NO}_FIYATLI.pdf" ] && break; sleep 0.5; done
onay "gönderilen belge indirilmeden arşive düştü" "True" \
  "$([ -f "$ARSIV_DIZIN/TTI_${HAK_NO}_FIYATLI.pdf" ] && echo True || echo False)"
onay "arşivde Excel de var" "True" \
  "$([ -f "$ARSIV_DIZIN/TTI_${HAK_NO}_FIYATLI.xlsx" ] && echo True || echo False)"
# Otomatik yazılan yalnızca FİYATLI kopyadır (CLAUDE.md §7/6).
onay "otomatik arşivde TEKLIF kopyası yok" "False" \
  "$([ -f "$ARSIV_DIZIN/TTI_${HAK_NO}_TEKLIF.pdf" ] && echo True || echo False)"

# ==============================================================================================
echo "=== Sözleşme yükleme (eski dogrula-faz-l.sh'den devralındı) ==="
# ==============================================================================================
SON_KOD=$(durum "$JAR" "/api/sozlesmeler/yonetim")
onay "bölge sorumlusu yönetim listesini göremiyor (403)" "403" "$SON_KOD"
onay "bölge sorumlusu sözleşme listesini görebiliyor" "200" "$(durum "$JAR" "/api/sozlesmeler")"
onay "bölge sorumlusu poz listesini görebiliyor" "200" "$(durum "$JAR" "/api/sozlesmeler/$SOZLESME/pozlar")"

# İndirilen format, yükleme önizlemesinden geçebilmeli: şablon ↔ parser sözleşmesinin uçtan uca kanıtı.
ONCEKI_SOZLESME=$(pg "select count(*) from sozlesme;")
yukle "$JAR_YON" "/api/sozlesmeler/onizleme" "$FORMAT"
onay "indirilen format önizlemeden geçti" "200" "$SON_KOD"
onay "önizleme hiçbir şey yazmadı" "$ONCEKI_SOZLESME" "$(pg "select count(*) from sozlesme;")"

printf 'bu bir xlsx degil' > "$GECICI/bozuk-sozlesme.xlsx"
yukle "$JAR_YON" "/api/sozlesmeler/onizleme" "$GECICI/bozuk-sozlesme.xlsx"
onay "bozuk sözleşme dosyası → 400 (500 değil)" "400" "$SON_KOD"

# ==============================================================================================
echo "=== Proje listesi Excel'i ==="
# ==============================================================================================
# `liste.xlsx` ile `/{id}` aynı yolda yarışıyor: Spring sabit yolu tercih ediyor, ama bu bir
# çerçeve davranışı ve sürümle değişirse hata "iş bulunamadı" diye görünürdü.
LISTE="$GECICI/isler.xlsx"
indir "$JAR" "/api/isler/liste.xlsx" "$LISTE"
onay "bölge sorumlusu listeyi indirebiliyor" "200" "$SON_KOD"
esler "dosya adı bölge ve zaman taşıyor" '^TTI_PROJELER_[A-Z_-]+_[0-9]{8}_[0-9]{4}\.xlsx$' "$SON_DOSYA_ADI"
onay "inen dosya gerçek bir xlsx (zip)" "True" \
  "$(python3 -c "import zipfile,sys;print(zipfile.is_zipfile(sys.argv[1]))" "$LISTE")"
onay "id yolu hâlâ çalışıyor" "200" "$(durum "$JAR_YON" "/api/isler/$IS_ID")"

# Tutar ve tarih hücreleri SAYI tipinde olmalı: metne düşerlerse Excel'de ne toplanır ne sıralanır
# ve dosya yine "çalışıyor" görünür — indirmenin ana sebebi tam da bu iki işlem.
LISTE_OZET=$(python3 - "$LISTE" <<'PY'
import sys, zipfile
from xml.etree import ElementTree as ET
NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
z = zipfile.ZipFile(sys.argv[1])
metin = [''.join(t.text or '' for t in si.iter(NS + 't')) for si in ET.fromstring(z.read('xl/sharedStrings.xml'))]
sayfa = ET.fromstring(z.read('xl/worksheets/sheet1.xml'))
stiller = ET.fromstring(z.read('xl/styles.xml'))
bicimler = {n.get('numFmtId'): n.get('formatCode') for n in stiller.iter(NS + 'numFmt')}
xf = [x.get('numFmtId') for x in stiller.find(NS + 'cellXfs')]

satirlar = list(sayfa.iter(NS + 'row'))
baslik = [metin[int(c.find(NS + 'v').text)] for c in satirlar[0]]

para = tarih = 0
for satir in satirlar[1:]:
    for hucre in satir:
        if hucre.find(NS + 'v') is None or hucre.get('t') == 's':
            continue
        bicim = bicimler.get(xf[int(hucre.get('s') or 0)], '')
        if bicim == '#,##0.00':
            para += 1
        elif bicim == 'dd.MM.yyyy HH:mm':
            tarih += 1

print(len(baslik), sum(1 for b in baslik if b.endswith('Toplam')), para > 0, tarih > 0)
PY
)
onay "24 sütun (8 kimlik + 3×5 aşama + gerekçe)" "24" "$(printf '%s' "$LISTE_OZET" | cut -d' ' -f1)"
onay "üç aşamanın da toplam sütunu var" "3" "$(printf '%s' "$LISTE_OZET" | cut -d' ' -f2)"
onay "tutarlar sayı hücresi (#,##0.00)" "True" "$(printf '%s' "$LISTE_OZET" | cut -d' ' -f3)"
onay "tarihler sayı hücresi (metin değil)" "True" "$(printf '%s' "$LISTE_OZET" | cut -d' ' -f4)"

echo
printf 'Geçen: %s   Düşen: %s\n' "$GECEN" "$DUSEN"
[ "$DUSEN" -eq 0 ]

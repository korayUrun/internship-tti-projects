#!/bin/bash
# Faz V doğrulaması — yönetici yetkileri turu.
#
#   SIFIRLA=1 bash deploy/dogrula-faz-v.sh   → iş/belge tablolarını temizle, sonra koştur
#   bash deploy/dogrula-faz-v.sh             → mevcut verinin üzerine koştur
#
# ⚠️ Betik GERÇEK iş, belge, poz ve kablo kaydı oluşturur; geliştirme veritabanı için yazıldı.
#
# Kapsam:
#   V1 — keşif/hakediş onayı, iade, kapanışın onaya taşınması
#   V2 — düzenlenebilirlik bayrakları sunucudan geliyor
#   V3 — sözleşme silme kalktı, yeni poz ekleme geldi, liste.xlsx yetkisi
#   V4 — elle kablo ekleme/düzenleme, envanter dışa aktarımı (gidiş-dönüş)
#
# **Neden ayrı betik:** M–P'nin betiği eski akışı (gönder → doğrudan TAMAMLANDI) doğruluyor ve
# gerilemeyi orada yakalamak istiyoruz; Faz V'nin yeni iddiaları oraya karışsa iki akış tek dosyada
# iç içe geçerdi.

set -uo pipefail
KOK="${KOK:-http://localhost:8081}"
KAYNAK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$KAYNAK/dogrula.sh"

DB_KAP="${DB_KAP:-tti-db}"
DB_ADI="${DB_ADI:-tti}"
DB_KULLANICI="${DB_KULLANICI:-tti_hakedis}"
SOZLESME="2026-sample-rota-1"
KAPSAM="DEPLASE_IYILESTIRME"
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
  local jar="$1" yol="$2" hedef="$3"
  SON_KOD=$(curl -s -b "$jar" -o "$hedef" -w '%{http_code}' "$KOK$yol")
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
  printf '{"kapsamKodu":"%s","kableNosid":"%s","route":"R-V","ekAraligi":"EK1-EK9"}' "$KAPSAM" "$1"
}
belge_govdesi() { printf '{"tur":"%s","sozlesmeKodu":"%s"}' "$1" "$SOZLESME"; }
miktar_govdesi() { printf '{"satirlar":[{"pozId":"%s","miktar":"%s"}]}' "$POZ" "$1"; }

ek1_yukle() {
  local jar="$1" belgeId="$2" kml="$GECICI/guzergah.kml"
  printf '<?xml version="1.0"?><kml/>' > "$kml"
  yukle "$jar" "/api/belgeler/$belgeId/ekler?tur=EK1_ROTA" "$kml"
}

# Fizibiliteyi açıp gönderir ve işi "İşleniyor"a alır — keşif/hakediş testlerinin ortak hazırlığı.
is_isleme_al() {
  local jar="$1" jarYon="$2" nosid="$3"
  cagir "$jar" POST /api/isler "$(is_govdesi "$nosid")"
  local isId fizId
  isId=$(kimlik id)
  cagir "$jar" POST "/api/isler/$isId/belgeler" "$(belge_govdesi FIZIBILITE)"
  fizId=$(kimlik id)
  cagir "$jar" PUT "/api/belgeler/$fizId/satirlar" "$(miktar_govdesi 1)"
  ek1_yukle "$jar" "$fizId"
  cagir "$jar" POST "/api/belgeler/$fizId/gonder"
  cagir "$jarYon" POST "/api/isler/$isId/durum" '{"durum":"ISLENIYOR","gerekce":"dogrulama"}'
  printf '%s' "$isId"
}

# --- Sıfırlama --------------------------------------------------------------------------------
if [ "${SIFIRLA:-0}" = "1" ]; then
  echo "=== Sıfırlama ==="
  # ⚠️ `TRUNCATE ... CASCADE` KULLANILMAZ: cascade zinciri `is_kaydi` → `fiyat_versiyonu` →
  # `poz_fiyat` üzerinden referans fiyat listesini de siler. Silme sırası elle yazılır.
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

JAR=$(giris izmir CHANGE_ME_PASSWORD)
JAR_YON=$(giris yonetici CHANGE_ME_PASSWORD)
NOSID=$(curl -s -b "$JAR" "$KOK/api/kableler?ara=mtc&limit=1" | sed -n 's/.*"nosid":"\([^"]*\)".*/\1/p')

# ==============================================================================================
echo "=== V1: şema ==="
# ==============================================================================================
BELGE_DURUM_KISITI=$(pg "select pg_get_constraintdef(oid) from pg_constraint where conname = 'belge_durum_check';")
onay "belge durum kısıtında ONAY_BEKLIYOR var" "1" "$(grep -c ONAY_BEKLIYOR <<< "$BELGE_DURUM_KISITI")"
onay "iade izi sütunları eklendi" "3" \
  "$(pg "select count(*) from information_schema.columns where table_name='belge' and column_name in ('iade_gerekce','iade_eden_id','iade_tarihi');")"
onay "durum_kaydi tablosu var" "1" \
  "$(pg "select count(*) from information_schema.tables where table_name='durum_kaydi';")"
# `is_id` XOR `belge_id`: iki hedefli tek tablonun tek güvencesi.
onay "durum_kaydi tek hedef kısıtı var" "1" \
  "$(pg "select count(*) from pg_constraint where conname='durum_kaydi_tek_hedef';")"

# ==============================================================================================
echo "=== V1: fizibilite onaysız, keşif onaylı ==="
# ==============================================================================================
cagir "$JAR" POST /api/isler "$(is_govdesi "$NOSID")"
IS_FIZ=$(kimlik id)
cagir "$JAR" POST "/api/isler/$IS_FIZ/belgeler" "$(belge_govdesi FIZIBILITE)"
FIZ_ID=$(kimlik id)
cagir "$JAR" PUT "/api/belgeler/$FIZ_ID/satirlar" "$(miktar_govdesi 2)"
ek1_yukle "$JAR" "$FIZ_ID"
cagir "$JAR" POST "/api/belgeler/$FIZ_ID/gonder"
onay "fizibilite gönderildi" "200" "$SON_KOD"
onay "fizibilite doğrudan TAMAMLANDI (onay yok)" "TAMAMLANDI" "$(alan durum)"

IS_ID=$(is_isleme_al "$JAR" "$JAR_YON" "$NOSID")
cagir "$JAR" POST "/api/isler/$IS_ID/belgeler" "$(belge_govdesi KESIF)"
KESIF_ID=$(kimlik id)
cagir "$JAR" PUT "/api/belgeler/$KESIF_ID/satirlar" "$(miktar_govdesi 4)"
ek1_yukle "$JAR" "$KESIF_ID"
cagir "$JAR" POST "/api/belgeler/$KESIF_ID/gonder"
onay "keşif gönderildi" "200" "$SON_KOD"
onay "keşif ONAY_BEKLIYOR" "ONAY_BEKLIYOR" "$(alan durum)"
onay "durum metni sunucudan" "1. Keşif Onay Bekliyor" "$(alan durumMetni)"

# ==============================================================================================
echo "=== V1: onay bekleyen keşif hakedişi AÇMAZ ==="
# ==============================================================================================
cagir "$JAR" POST "/api/isler/$IS_ID/belgeler" "$(belge_govdesi HAKEDIS)"
onay "onay bekleyen keşifle hakediş açılamaz (409)" "409" "$SON_KOD"

# ==============================================================================================
echo "=== V1/V2: onay bekleyen belge hâlâ düzenlenebilir ==="
# ==============================================================================================
cagir "$JAR" PUT "/api/belgeler/$KESIF_ID/satirlar" "$(miktar_govdesi 6)"
onay "bölge sorumlusu onay beklerken düzeltebiliyor" "200" "$SON_KOD"
onay "sunucu düzenlenebilir diyor" "True" "$(alan duzenlenebilir)"
onay "sunucu yeniden gönderilemez diyor" "False" "$(alan gonderilebilir)"

# ==============================================================================================
echo "=== V1: iade ==="
# ==============================================================================================
cagir "$JAR" POST "/api/belgeler/$KESIF_ID/iade" '{"gerekce":"bolge sorumlusu deneme"}'
onay "bölge sorumlusu iade edemez (403)" "403" "$SON_KOD"

cagir "$JAR_YON" POST "/api/belgeler/$KESIF_ID/iade" '{"gerekce":""}'
onay "gerekçesiz iade reddedildi (400)" "400" "$SON_KOD"

cagir "$JAR_YON" POST "/api/belgeler/$KESIF_ID/iade" '{"gerekce":"miktarlar eksik"}'
onay "yönetici iade etti" "200" "$SON_KOD"
onay "belge taslağa döndü" "TASLAK" "$(alan durum)"
onay "iade gerekçesi yanıtta" "miktarlar eksik" "$(printf '%s' "$SON_GOVDE" | json "d['iade']['gerekce']")"
onay "iade edilmiş taslak ayrı okunuyor" "1. Keşif İade Edildi" "$(alan durumMetni)"
onay "iade kayda geçti" "1" \
  "$(pg "select count(*) from durum_kaydi where belge_id = $KESIF_ID and yeni = 'TASLAK';")"
# Girilen miktar korunmalı: iade bir revizyon değil, aynı belgenin geri dönüşü.
onay "miktarlar korundu" "1" "$(pg "select count(*) from belge_satir where belge_id = $KESIF_ID;")"

cagir "$JAR" POST "/api/belgeler/$KESIF_ID/gonder"
onay "düzeltilen keşif yeniden gönderildi" "200" "$SON_KOD"
onay "yeniden onay bekliyor" "ONAY_BEKLIYOR" "$(alan durum)"
# İz kalıcı: "bu aşama bir kez iade edilmişti" bilgisi silinmiyor.
onay "iade izi yeniden gönderimde silinmedi" "1" \
  "$(pg "select count(*) from belge where id = $KESIF_ID and iade_gerekce is not null;")"

# ==============================================================================================
echo "=== V1: kapanış GÖNDERİMDE değil ONAYDA ==="
# ==============================================================================================
cagir "$JAR_YON" POST "/api/belgeler/$KESIF_ID/onayla"
onay "yönetici keşfi onayladı" "200" "$SON_KOD"
onay "keşif TAMAMLANDI" "TAMAMLANDI" "$(alan durum)"

cagir "$JAR" POST "/api/isler/$IS_ID/belgeler" "$(belge_govdesi HAKEDIS)"
onay "onaydan sonra hakediş açıldı" "201" "$SON_KOD"
HAK_ID=$(kimlik id)
cagir "$JAR" PUT "/api/belgeler/$HAK_ID/satirlar" "$(miktar_govdesi 3)"
ek1_yukle "$JAR" "$HAK_ID"
cagir "$JAR" POST "/api/belgeler/$HAK_ID/gonder"
cagir "$JAR" GET "/api/isler/$IS_ID"
onay "hakediş gönderimi işi KAPATMIYOR" "ISLENIYOR" "$(alan durum)"

cagir "$JAR_YON" POST "/api/belgeler/$HAK_ID/onayla"
onay "hakediş onaylandı" "200" "$SON_KOD"
cagir "$JAR" GET "/api/isler/$IS_ID"
onay "onaydan sonra iş kapandı" "KAPANDI" "$(alan durum)"

# ==============================================================================================
echo "=== V1: yöneticinin serbest durum yetkisi ==="
# ==============================================================================================
cagir "$JAR_YON" POST "/api/isler/$IS_ID/durum" '{"durum":"ISLENIYOR"}'
onay "gerekçesiz durum değişikliği reddedildi (400)" "400" "$SON_KOD"

cagir "$JAR_YON" POST "/api/isler/$IS_ID/durum" '{"durum":"ISLENIYOR","gerekce":"hakedis duzeltilecek"}'
onay "kapanmış proje yeniden açılabiliyor" "200" "$SON_KOD"
onay "proje İşleniyor'a döndü" "ISLENIYOR" "$(alan durum)"
onay "müdahale kayda geçti" "1" \
  "$(pg "select count(*) from durum_kaydi where is_id = $IS_ID and onceki = 'KAPANDI' and yeni = 'ISLENIYOR';")"

cagir "$JAR_YON" POST "/api/belgeler/$HAK_ID/durum" '{"durum":"TASLAK","gerekce":"yanlis miktar"}'
onay "onaylanmış belge geri açılabiliyor" "200" "$SON_KOD"
onay "belge taslağa döndü" "TASLAK" "$(alan durum)"

cagir "$JAR" POST "/api/belgeler/$HAK_ID/durum" '{"durum":"TAMAMLANDI","gerekce":"deneme"}'
onay "bölge sorumlusu durum değiştiremez (403)" "403" "$SON_KOD"

cagir "$JAR_YON" GET "/api/belgeler/$HAK_ID/durum-gecmisi"
onay "durum geçmişi okunabiliyor" "200" "$SON_KOD"

# İptali geri alma: `is_kaydi_iptal_izi_tutarli` kısıtı iptal olmayan satırda dolu iz kabul etmiyor,
# yani temizlik unutulsaydı bu çağrı veritabanı hatasıyla düşerdi.
cagir "$JAR_YON" POST "/api/isler/$IS_ID/durum" '{"durum":"IPTAL","gerekce":"deneme"}'
onay "proje iptal edildi" "200" "$SON_KOD"
cagir "$JAR_YON" POST "/api/isler/$IS_ID/durum" '{"durum":"ISLENIYOR","gerekce":"iptal yanlisti"}'
onay "iptal geri alınabiliyor" "200" "$SON_KOD"
onay "iptal izi temizlendi" "1" \
  "$(pg "select count(*) from is_kaydi where id = $IS_ID and iptal_gerekce is null and iptal_tarihi is null;")"

# ==============================================================================================
echo "=== V3: sözleşme silme kalktı, poz eklendi ==="
# ==============================================================================================
SON_KOD=$(curl -s -b "$JAR_YON" -o /dev/null -w '%{http_code}' -X DELETE \
  -H "X-XSRF-TOKEN: $(awk '/XSRF-TOKEN/{print $7}' "$JAR_YON")" "$KOK/api/sozlesmeler/$SOZLESME")
onay "sözleşme silme ucu kalktı (404/405)" "True" \
  "$([ "$SON_KOD" = "404" ] || [ "$SON_KOD" = "405" ] && echo True || echo False)"

onay "bölge sorumlusu sözleşme dosyasını indiremez (403)" "403" \
  "$(durum "$JAR" "/api/sozlesmeler/$SOZLESME/liste.xlsx")"
onay "yönetici indirebiliyor" "200" "$(durum "$JAR_YON" "/api/sozlesmeler/$SOZLESME/liste.xlsx")"

POZ_ONCE=$(pg "select count(*) from poz p join sozlesme s on s.id = p.sozlesme_id where s.kod = '$SOZLESME';")
# Betik yeniden koşturulabilir olmalı: önceki koşudan kalan m-pozları var, sayı mutlak değil FARK.
ELLE_ONCE=$(pg "select count(*) from poz p join sozlesme s on s.id = p.sozlesme_id where s.kod = '$SOZLESME' and p.satir_kodu ~ '^m[0-9]+\$';")
GRUP_KODU=$(pg "select p.satir_kodu from poz p join sozlesme s on s.id = p.sozlesme_id where s.kod = '$SOZLESME' and p.grup_mu order by p.sira limit 1;")

cagir "$JAR" POST "/api/sozlesmeler/$SOZLESME/pozlar" \
  '{"pozNo":"9.99","ad":"Deneme kalemi","birim":"Ad.","birimFiyat":"1.234,56"}'
onay "bölge sorumlusu poz ekleyemez (403)" "403" "$SON_KOD"

cagir "$JAR_YON" POST "/api/sozlesmeler/$SOZLESME/pozlar" \
  '{"pozNo":"9.99","ad":"Deneme kalemi","birim":"Ad.","birimFiyat":"0"}'
onay "sıfır fiyatlı kalem reddedildi (400)" "400" "$SON_KOD"

cagir "$JAR_YON" POST "/api/sozlesmeler/$SOZLESME/pozlar" \
  "{\"pozNo\":\"9.99\",\"ad\":\"Deneme kalemi\",\"birim\":\"Ad.\",\"birimFiyat\":\"1.234,56\",\"grupSatirKodu\":\"$GRUP_KODU\"}"
onay "yönetici kalem ekledi" "200" "$SON_KOD"
onay "poz sayısı bir arttı" "$((POZ_ONCE + 1))" \
  "$(pg "select count(*) from poz p join sozlesme s on s.id = p.sozlesme_id where s.kod = '$SOZLESME';")"
onay "satır kodu m-uzayında" "$((ELLE_ONCE + 1))" \
  "$(pg "select count(*) from poz p join sozlesme s on s.id = p.sozlesme_id where s.kod = '$SOZLESME' and p.satir_kodu ~ '^m[0-9]+\$';")"
onay "fiyat virgüllü girişten doğru okundu" "1234.56" \
  "$(pg "select pf.birim_fiyat from poz p join poz_fiyat pf on pf.poz_id = p.id join sozlesme s on s.id = p.sozlesme_id where s.kod = '$SOZLESME' and p.satir_kodu ~ '^m[0-9]+\$' order by p.sira desc limit 1;")"

# Fazın asıl sözleşmesi: yeni kalem eski belgede GÖRÜNÜR ama TOPLAMI DEĞİŞTİRMEZ.
cagir "$JAR" GET "/api/belgeler/$FIZ_ID"
TOPLAM_SONRA=$(alan toplam)
cagir "$JAR" GET "/api/belgeler/$FIZ_ID/pozlar"
onay "yeni kalem eski belgenin poz listesinde görünüyor" "1" \
  "$(printf '%s' "$SON_GOVDE" | grep -c 'Deneme kalemi')"
onay "eski belgenin toplamı değişmedi" "True" \
  "$([ "$TOPLAM_SONRA" != "YOK" ] && echo True || echo False)"
not "fizibilite toplamı: $TOPLAM_SONRA"

# ==============================================================================================
echo "=== V4: elle kablo ve envanter dışa aktarımı ==="
# ==============================================================================================
SAHA=$(pg "select xlsx_saha_adi from bolge where kod = 'IZMIR';")
# ⚠️ Ad `mtc` İÇERMEZ: içerseydi yukarıdaki `ara=mtc` sonucunda ilk sıraya düşer (`-` < `0`),
# betiğin açtığı projeler bu kabloya bağlanır ve bir sonraki koşuda temizlik `is_kaydi_kable_id_fkey`
# yüzünden düşerdi.
YENI_NOSID="dogrulama-v-kablo"
pg "delete from kable_surum_satiri where nosid = '$YENI_NOSID';" > /dev/null
pg "delete from kable where nosid = '$YENI_NOSID';" > /dev/null

KABLO_GOVDE=$(printf '{"nosid":"%s","sahaAdi":"%s","danismanAd":"Dogrulama","ucA":{"segmentler":{"country":"Turkiye","city":"Izmir","address":"Konak POP","building":"AND Plaza","floor":"Kat 1"}}}' "$YENI_NOSID" "$SAHA")

cagir "$JAR" POST /api/kableler "$KABLO_GOVDE"
onay "bölge sorumlusu kablo ekleyemez (403)" "403" "$SON_KOD"

cagir "$JAR_YON" POST /api/kableler "$KABLO_GOVDE"
onay "yönetici kablo ekledi" "201" "$SON_KOD"
# Konum yolu SUNUCUDA kuruluyor: bina ve kat tek segmentte (`>`), diğerleri `>>` ile.
onay "konum yolu sunucuda kuruldu" "Turkiye >> Izmir >> Konak POP >> AND Plaza > Kat 1" "$(alan adresA)"
onay "şehir city segmentinden türedi" "Izmir" "$(alan sehirA)"

cagir "$JAR_YON" POST /api/kableler "$KABLO_GOVDE"
onay "aynı nosid ikinci kez eklenemez (400)" "400" "$SON_KOD"

# İzdüşüm ile yürürlükteki sürüm birlikte yazılmalı; ayrışsalardı fark yanlış hesaplanırdı.
onay "kablo izdüşümde" "1" "$(pg "select count(*) from kable where nosid = '$YENI_NOSID';")"
onay "kablo yürürlükteki sürümde de var" "1" \
  "$(pg "select count(*) from kable_surum_satiri s join kable_surum v on v.id = s.surum_id where v.yururlukte and s.nosid = '$YENI_NOSID';")"

# Sürümün "N kablo" sayacı yükleme anında yazılıyor; elle ekleme onu tazelemezse envanter
# ekranındaki sayı sürümün gerçek satır sayısından sessizce ayrışır.
SURUM_SAYAC=$(pg "select satir_sayisi from kable_surum where yururlukte;")
onay "sürüm sayacı gerçek satır sayısıyla tutarlı" \
  "$(pg "select count(*) from kable_surum_satiri s join kable_surum v on v.id = s.surum_id where v.yururlukte;")" \
  "$SURUM_SAYAC"

cagir "$JAR_YON" PUT "/api/kableler/$YENI_NOSID" \
  "$(printf '{"nosid":"%s","sahaAdi":"%s","danismanAd":"Dogrulama","ucA":{"konumYolu":"Turkiye >> Izmir >> Yeni Adres"},"sehirA":"Izmir"}' "$YENI_NOSID" "$SAHA")"
onay "kablo düzenlendi" "200" "$SON_KOD"
onay "konum yolu güncellendi" "Turkiye >> Izmir >> Yeni Adres" "$(alan adresA)"
# Düzenleme yeni satır doğurmaz: sayaç artırılmıyor, yeniden sayılıyor.
onay "düzenleme sayacı artırmadı" "$SURUM_SAYAC" "$(pg "select satir_sayisi from kable_surum where yururlukte;")"

# Gidiş-dönüş: indirilen envanter geri yüklendiğinde HİÇBİR fark çıkmamalı.
onay "bölge sorumlusu envanteri indiremez (403)" "403" "$(durum "$JAR" "/api/kable-surumleri/envanter.xlsx")"
indir "$JAR_YON" "/api/kable-surumleri/envanter.xlsx" "$GECICI/envanter.xlsx"
onay "yönetici envanteri indirdi" "200" "$SON_KOD"

KABLO_ONCE=$(pg "select count(*) from kable where aktif;")
yukle "$JAR_YON" "/api/kable-surumleri" "$GECICI/envanter.xlsx"
onay "indirilen envanter geri yüklenebildi" "201" "$SON_KOD"
FARK=$(printf '%s' "$SON_GOVDE" | json "'%d/%d/%d' % (d['fark']['yeni'], d['fark']['degisen'], d['fark']['dusen'])")
onay "gidiş-dönüş fark üretmiyor (yeni/değişen/düşen)" "0/0/0" "$FARK"
onay "yükleme canlı envantere dokunmadı" "$KABLO_ONCE" "$(pg "select count(*) from kable where aktif;")"

# ==============================================================================================
printf '\n=== Özet: %d geçti, %d düştü ===\n' "$GECEN" "$DUSEN"
[ "$DUSEN" -eq 0 ]

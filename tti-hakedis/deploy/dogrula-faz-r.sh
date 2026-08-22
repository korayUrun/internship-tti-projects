#!/bin/bash
# Faz R/S doğrulaması — web ve mobil arayüz güvenceleri.
#
#   bash deploy/dogrula-faz-r.sh
#
# Sunucu gerektirmez: kontroller **kaynak kod** üzerinde yapılır. Silinen ekranların bir yerde
# yaşamaya devam etmesi ve web'de para aritmetiği yapılması derleme hatası vermiyor; bu betik o iki
# sessiz kırılmayı kapatıyor (eski `dogrula-faz-h.sh`/`-i.sh`'nin devamı).

set -uo pipefail
KAYNAK="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GECEN=0; DUSEN=0
onay() {
  if [ "$2" = "$3" ]; then
    printf '  ✔ %-58s %s\n' "$1" "$3"; GECEN=$((GECEN+1))
  else
    printf '  ✘ %-58s beklenen=%s  gerçek=%s\n' "$1" "$2" "$3"; DUSEN=$((DUSEN+1))
  fi
}

# yoksa <açıklama> <kalıp> <dizin...>
yoksa() {
  local aciklama="$1" kalip="$2"; shift 2
  onay "$aciklama" "0" "$(grep -rlE "$kalip" "$@" 2>/dev/null | wc -l | tr -d ' ')"
}

# varsa <açıklama> <dosya>
varsa() {
  onay "$1" "1" "$([ -e "$2" ] && echo 1 || echo 0)"
}

echo "=== Silinen ekranlar geri gelmemiş ==="
for dosya in \
  "web/src/ekran/IsDetay.tsx" \
  "web/src/ekran/SozlesmeDetay.tsx" \
  "web/src/ekran/ImzaTuvali.tsx" \
  "web/src/ekran/OnayPaneli.tsx" \
  "app/src/main/kotlin/tr/com/tti/hakedis/feature/document/SignaturePad.kt" \
  "app/src/main/kotlin/tr/com/tti/hakedis/feature/workorder/WorkOrderDetailScreen.kt" \
  "server/src/main/kotlin/tr/com/tti/hakedis/server/ek/ImzaDenetleyici.kt" \
  "server/src/main/kotlin/tr/com/tti/hakedis/server/sozlesme/FiyatListesiServisi.kt" \
  "server/src/main/kotlin/tr/com/tti/hakedis/server/sozlesme/ReferansFiyatServisi.kt"; do
  onay "silinmiş: ${dosya##*/}" "0" "$([ -e "$KAYNAK/$dosya" ] && echo 1 || echo 0)"
done

echo "=== Yeni ekranlar yerinde ==="
varsa "web: satır ekranı sözleşme seçicisi" "$KAYNAK/web/src/ekran/SozlesmeSecici.tsx"
varsa "web: belge sözleşme kartı" "$KAYNAK/web/src/ekran/SozlesmeKarti.tsx"
varsa "web: miktar Excel bölümü" "$KAYNAK/web/src/ekran/MiktarExcel.tsx"
varsa "web: iş durum paneli" "$KAYNAK/web/src/ekran/DurumPaneli.tsx"
varsa "sunucu: miktar servisi" "$KAYNAK/server/src/main/kotlin/tr/com/tti/hakedis/server/belge/MiktarServisi.kt"
varsa "sunucu: sözleşme format şablonu" "$KAYNAK/server/src/main/kotlin/tr/com/tti/hakedis/server/sozlesme/SozlesmeSablonu.kt"
varsa "sunucu: SP No üreteci" "$KAYNAK/server/src/main/kotlin/tr/com/tti/hakedis/server/isler/IsNumaraUretici.kt"
varsa "domain: iş durumu" "$KAYNAK/core/domain/src/main/kotlin/tr/com/tti/hakedis/domain/workorder/WorkOrderStatus.kt"

echo "=== Kaldırılan kavramlar kaynakta kalmamış ==="
yoksa "web'de onay kuyruğu rotası yok" "'kuyruk'" "$KAYNAK/web/src"
# `ONAY_BEKLIYOR` Faz V'de geri geldi (keşif/hakediş onayı). `ONAYLANDI` gelmedi ve gelmemeli:
# onaylanmış belge `TAMAMLANDI`'dır; ikinci bir değer iki durumu eşanlamlı yapar ve hangisinin
# yazıldığı sorguya bağlı hâle gelirdi.
yoksa "web'de ONAYLANDI yok (ONAY_BEKLIYOR meşru)" "\\bONAYLANDI\\b" "$KAYNAK/web/src"
yoksa "web'de imza ucu çağrısı yok" "imza" "$KAYNAK/web/src"
# Çağrıyı arıyoruz, sözcüğü değil: `MoneyFormatter` KDoc'unda ~~moneyExact~~ kaydı bilinçli duruyor.
yoksa "sunucuda moneyExact çağrısı yok" "moneyExact\(" "$KAYNAK/server/src/main" "$KAYNAK/core/domain/src/main"
yoksa "domainde bölge sıra kuralı yok" "BekleyenIs|bolgeSirasinaTabiMi" "$KAYNAK/core/domain/src/main"

echo "=== Para aritmetiği yalnızca sunucuda (CLAUDE.md §13) ==="
# `toFixed` / `parseFloat` / `Number(` web'de para alanına dokunmanın en olası yolları.
# `\.toFixed(` — sözcüğün kendisi `bilesen.tsx` yorumunda kuralı anlatmak için geçiyor.
yoksa "web'de toFixed çağrısı yok" "\.toFixed\(" "$KAYNAK/web/src"
yoksa "web'de parseFloat yok" "parseFloat" "$KAYNAK/web/src"

echo "=== Yuvarlama tüm fiyat okumalarında aynı (Faz P) ==="
# Dört yer: sözleşme poz listesi, belge poz listesi, satır yazımı, çıktı sorgusu. Biri ayrışırsa
# ekranda girilebilir görünen satır başka fiyatla yazılır ve HİÇBİR hata çıkmaz.
onay "round(...,2) dört fiyat okumasında da var" "4" \
  "$(grep -rE 'round\(.*birim_fiyat.*, 2\)|round\(coalesce\(.*birim_fiyat.*\), 2\)' "$KAYNAK/server/src/main" | wc -l | tr -d ' ')"

echo "=== Web derlemesi ==="
if command -v npm > /dev/null 2>&1; then
  if (cd "$KAYNAK/web" && npm run build > /tmp/tti-web-build.log 2>&1); then
    onay "npm run build" "0" "0"
  else
    onay "npm run build" "0" "1"
    tail -20 /tmp/tti-web-build.log
  fi
else
  printf '  ℹ npm yok, web derlemesi atlandı\n'
fi

echo
printf 'Geçen: %s   Düşen: %s\n' "$GECEN" "$DUSEN"
[ "$DUSEN" -eq 0 ]

#!/bin/bash
# Kurulum hazırlığı — macOS ve Linux.
#
#   bash deploy/kur.sh
#
# Ne yapar:
#   1. Belge arşivi ve yükleme klasörlerini işletim sistemine uygun yerde oluşturur
#   2. `deploy/.env` yoksa `.env.example`'dan üretir
#   3. `.env` içindeki BOŞ ya da EKSİK anahtarları doldurur
#
# ⚠️ Var olan bir `.env`'i **ezmez**. Dolu bir değeri değiştirmek, çalışan bir kurulumun arşivini
#    sessizce başka bir klasöre taşımak demek olurdu; eski dosyalar orada kalır ve kimse fark etmez.
#    Değeri değiştirmek isteyen `.env`'i elle düzenler.

set -euo pipefail
KAYNAK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DOSYASI="$KAYNAK/.env"

# Hedef klasörler. macOS'ta ana dizin altı: `/srv` yazılabilir değil ve Docker Desktop paylaşılan
# klasör listesine ana dizini zaten dahil ediyor. Linux'ta sunucu kurulumu beklendiği için /srv,
# yazma yetkisi yoksa ana dizine düşülür.
if [ "$(uname -s)" = "Darwin" ]; then
  CATI="$HOME/TTI-Hakedis"
elif [ -w /srv ] || [ "$(id -u)" = "0" ]; then
  CATI="/srv/veri/tti-hakedis"
else
  CATI="$HOME/TTI-Hakedis"
  echo "ℹ /srv yazılabilir değil; klasörler ana dizine kuruluyor."
fi

ARSIV="$CATI/arsiv"
YUKLEME="$CATI/yukleme"

mkdir -p "$ARSIV" "$YUKLEME"
echo "✔ Klasörler hazır:"
echo "    arşiv   : $ARSIV"
echo "    yükleme : $YUKLEME"

if [ ! -f "$ENV_DOSYASI" ]; then
  cp "$KAYNAK/.env.example" "$ENV_DOSYASI"
  echo "✔ .env oluşturuldu (.env.example'dan)."
fi

# anahtar_yaz <ANAHTAR> <DEĞER> — yalnızca anahtar yoksa ya da değeri boşsa yazar.
anahtar_yaz() {
  local anahtar="$1" deger="$2" mevcut
  mevcut=$(grep -E "^${anahtar}=" "$ENV_DOSYASI" | tail -1 | cut -d= -f2- || true)

  if [ -n "$mevcut" ]; then
    echo "· $anahtar zaten dolu, DOKUNULMADI: $mevcut"
    # Göreli yol, compose dosyasının bulunduğu klasöre (deploy/) göre çözülür. `.env.example`'dan
    # gelen `./arsiv` değeri bu yüzden `deploy/arsiv`'a düşer ve kullanıcı belgeleri ana dizinde
    # arayıp bulamaz. Değeri değiştirmiyoruz ama nereye baktığını açıkça söylüyoruz.
    case "$mevcut" in
      .*) echo "  ⚠ Göreli yol: gerçek konum $KAYNAK/${mevcut#./}" ;;
    esac
    echo "  Değiştirmek isterseniz: deploy/.env düzenleyin, ESKİ klasörün içeriğini yeni yere taşıyın,"
    echo "  sonra 'docker compose up -d server' ile konteyneri yeniden oluşturun."
    return
  fi
  if grep -qE "^${anahtar}=" "$ENV_DOSYASI"; then
    # Boş anahtarı yerinde doldur. `sed -i ''` macOS (BSD) sözdizimi; Linux'ta `-i` tek başına
    # yeter, bu yüzden geçici dosya üzerinden gidiyoruz — iki platformda da aynı davranış.
    local gecici
    gecici=$(mktemp)
    sed "s|^${anahtar}=.*|${anahtar}=${deger}|" "$ENV_DOSYASI" > "$gecici"
    mv "$gecici" "$ENV_DOSYASI"
  else
    printf '%s=%s\n' "$anahtar" "$deger" >> "$ENV_DOSYASI"
  fi
  echo "✔ $anahtar=$deger"
}

anahtar_yaz ARSIV_KOK "$ARSIV"
anahtar_yaz YUKLEME_KOK "$YUKLEME"

echo
echo "Sıradaki adım:"
echo "  1. deploy/.env içindeki DB_PAROLA'yı değiştirin (varsayılan bırakılmamalı)"
echo "  2. ./gradlew :server:bootJar"
echo "  3. cd deploy && docker compose up -d --build"
echo
echo "Belgeler şuraya yazılacak: $ARSIV/YedekTTI/{BÖLGE}/{On_Fizibilite|1_Kesif|Hakedis}/"

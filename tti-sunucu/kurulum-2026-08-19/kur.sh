#!/usr/bin/env bash
# TTI Merkezi Veri (tti-data) — sunucu kurulumunun ELLE YAPILAN kısmı.
#
# Bu betik yalnızca hataya açık üç adımı yapar: veri kökü, .env'e ekleme, veritabanı provizyonu.
# İmaj derleme ve konteyner kaldırma KASITLI OLARAK DIŞARIDA — o iki komutun görünür olması
# gerekiyor (çıplak `docker compose build` diğer projelerin sürüm etiketlerini ezer).
#
# Kullanım (sunucuda):   cd /srv/kod/tti-sunucu && sudo bash kurulum-2026-08-19/kur.sh
#
# Tekrar çalıştırılabilir: yapılmış adımları atlar, hiçbir şeyi iki kez yapmaz.

set -uo pipefail

BETIK_DIZINI="$(cd "$(dirname "$0")" && pwd)"
SUNUCU_DIZINI="$(dirname "$BETIK_DIZINI")"
ENV_DOSYASI="${ENV_DOSYASI:-$SUNUCU_DIZINI/.env}"
BLOK="$BETIK_DIZINI/env-eklenecek-blok.txt"
SQL="$BETIK_DIZINI/03-data-HAZIR.sql"
VERI_KOKU="${VERI_KOKU:-/srv/veri/tti-data/yukleme}"
DB_KAPSAYICI="${DB_KAPSAYICI:-tti-db}"
DB_BAKIM="${DB_BAKIM:-tti_hakedis}"

hata_sayisi=0
basarili() { printf '\033[32m  ✓\033[0m %s\n' "$1"; }
basarisiz() { printf '\033[31m  ✗\033[0m %s\n' "$1"; hata_sayisi=$((hata_sayisi + 1)); }
atlandi()  { printf '\033[90m  ·\033[0m %s\n' "$1"; }
baslik()   { printf '\n\033[1m%s\033[0m\n' "$1"; }

# --- 0. Ön koşullar -------------------------------------------------------------------------
baslik "0. Ön koşullar"
for d in "$ENV_DOSYASI" "$BLOK" "$SQL"; do
  [ -f "$d" ] || { basarisiz "dosya yok: $d"; }
done
[ "$hata_sayisi" -eq 0 ] || { printf '\n\033[31mDurduruldu.\033[0m\n'; exit 1; }
basarili "gerekli dosyalar yerinde"

if ! docker inspect "$DB_KAPSAYICI" >/dev/null 2>&1; then
  basarisiz "$DB_KAPSAYICI çalışmıyor — önce 'docker compose up -d db'"
  exit 1
fi
basarili "$DB_KAPSAYICI ayakta"

# ⚠️ Yedek almadan devam edilmez. Bu betik veritabanına YAZAR.
baslik "0b. Yedek kontrolü"
printf '  Bu betik veritabanına rol ve veritabanı EKLER. `bash yedek.sh` çalıştırıldı mı? [evet/hayir] '
read -r cevap
case "$cevap" in
  evet|EVET|e|E) basarili "onaylandı" ;;
  *) printf '\n\033[31mÖnce yedek alın:  sudo bash yedek.sh\033[0m\n'; exit 1 ;;
esac

# --- 1. Veri kökü ---------------------------------------------------------------------------
baslik "1. Veri kökü (yüklenen Excel'ler + rollback anlık görüntüleri)"
if [ -d "$VERI_KOKU" ]; then
  atlandi "$VERI_KOKU zaten var"
else
  mkdir -p "$VERI_KOKU" && basarili "oluşturuldu: $VERI_KOKU" || basarisiz "oluşturulamadı"
fi
# Konteyner 1000:1000 ile çalışıyor; sahiplik yanlışsa uygulama açılır, sağlık kontrolü geçer
# ve hata ancak İLK EXCEL YÜKLEMESİNDE çıkar.
#
# ⚠️ Kontrol chown'un çıkış koduna DEĞİL, oluşan SAHİPLİĞE bakar: dizin zaten doğruysa
# chown'un başarısız olması sorun değildir; asıl soru "şu an doğru mu".
chown -R 1000:1000 "$(dirname "$VERI_KOKU")" 2>/dev/null || true
sahip="$(stat -c '%u:%g' "$VERI_KOKU" 2>/dev/null || stat -f '%u:%g' "$VERI_KOKU" 2>/dev/null)"
if [ "$sahip" = "1000:1000" ]; then
  basarili "sahiplik 1000:1000"
else
  basarisiz "sahiplik $sahip — 1000:1000 olmalı (sudo ile mi çalıştırdınız?)"
fi

# --- 2. .env'e ekleme -----------------------------------------------------------------------
baslik "2. .env ayarları"
if grep -q '^DATA_DB_PAROLA=' "$ENV_DOSYASI" 2>/dev/null; then
  atlandi "DATA_* satırları .env'de zaten var — eklenmedi"
else
  cp -p "$ENV_DOSYASI" "$ENV_DOSYASI.oncesi-$(date +%F)" \
    && basarili "mevcut .env yedeklendi: $(basename "$ENV_DOSYASI").oncesi-$(date +%F)"
  # ⚠️ EKLEMELİ (>>). Mevcut satırlara — özellikle SURUM ve SURUM_RAPOR'a — dokunulmaz.
  printf '\n' >> "$ENV_DOSYASI"
  cat "$BLOK" >> "$ENV_DOSYASI"
  chmod 600 "$ENV_DOSYASI"
  basarili "DATA_* satırları .env sonuna eklendi"
fi

for v in SURUM SURUM_RAPOR SURUM_DATA; do
  d="$(grep -E "^$v=" "$ENV_DOSYASI" | head -1 | cut -d= -f2-)"
  [ -n "$d" ] && basarili "$v=$d" || basarisiz "$v .env'de YOK"
done

# --- 3. Parola tutarlılığı ------------------------------------------------------------------
# En sık yapılan hata: .env ile provizyon SQL'indeki parolanın farklı olması. Uygulama açılır,
# sağlık kontrolü düşer, log'da yalnızca "password authentication failed" görünür.
baslik "3. Parola tutarlılığı (.env ↔ provizyon SQL)"
env_parola="$(grep -E '^DATA_DB_PAROLA=' "$ENV_DOSYASI" | head -1 | cut -d= -f2-)"
sql_parola="$(grep -oE "create user data with password '[^']*'" "$SQL" | sed "s/.*'\(.*\)'/\1/")"
if [ -n "$env_parola" ] && [ "$env_parola" = "$sql_parola" ]; then
  basarili "eşleşiyor (${#env_parola} karakter)"
else
  basarisiz "EŞLEŞMİYOR — uygulama veritabanına bağlanamaz. .env ve $SQL'i elle eşitleyin."
  exit 1
fi

# --- 4. Veritabanı provizyonu ---------------------------------------------------------------
baslik "4. Veritabanı provizyonu"
if docker exec "$DB_KAPSAYICI" psql -U "$DB_BAKIM" -d tti -tAc \
     "select 1 from pg_database where datname='data'" 2>/dev/null | grep -q 1; then
  atlandi "'data' veritabanı zaten var — provizyon atlandı"
else
  if docker exec -i "$DB_KAPSAYICI" psql -U "$DB_BAKIM" -d tti -v ON_ERROR_STOP=1 < "$SQL"; then
    basarili "'data' veritabanı ve ayrıcalıksız rol oluşturuldu"
  else
    basarisiz "provizyon başarısız"; exit 1
  fi
fi

# --- 5. Sınır kontrolü ----------------------------------------------------------------------
baslik "5. Projeler arası sınır"
s="$(docker exec "$DB_KAPSAYICI" psql -U "$DB_BAKIM" -d tti -tAc "select rolsuper from pg_roles where rolname='data'" 2>/dev/null | tr -d ' ')"
[ "$s" = "f" ] && basarili "'data' rolü superuser değil" || basarisiz "'data' rolü SUPERUSER — sınır yok"
for komsu in tti rapor; do
  y="$(docker exec "$DB_KAPSAYICI" psql -U "$DB_BAKIM" -d tti -tAc "select has_database_privilege('data','$komsu','connect')" 2>/dev/null | tr -d ' ')"
  [ "$y" = "f" ] && basarili "'data' → $komsu veritabanına bağlanamıyor" \
                 || basarisiz "'data' → $komsu veritabanına BAĞLANABİLİYOR"
done

# --- Sonuç ----------------------------------------------------------------------------------
baslik "Sonuç"
if [ "$hata_sayisi" -ne 0 ]; then
  printf '\033[31m%d kontrol başarısız — devam etmeyin.\033[0m\n' "$hata_sayisi"
  exit 1
fi
cat <<'SONRAKI'
  Hazırlık tamam. Sırada iki komut var — bilerek otomatikleştirilmedi:

    sudo docker compose build tti-data-server tti-data-panel
    sudo docker compose up -d tti-data-server tti-data-panel
    sudo docker compose up -d proxy

  ⚠️ Servis adlarını MUTLAKA yazın. Çıplak `docker compose build`, hakediş ve rapor
     imajlarını da yeniden derler ve SURUM etiketlerini ezer — geri dönüş imkânı kalmaz.
  ⚠️ Son satır proxy'yi yeniden başlatır: hakediş/rapor için 1-2 saniye kesinti.

  Sonra doğrulama:
    sudo docker ps --filter name=tti-data          # ikisi de (healthy), ~90 sn sürebilir
    curl -s http://localhost:8083/api/saglik
    sudo bash dogrula.sh
    sudo bash yedek.sh && sudo bash yedek.sh dogrula

  Bittiğinde bu klasörü silin — içinde parolalar var:
    sudo rm -rf kurulum-2026-08-19
SONRAKI
